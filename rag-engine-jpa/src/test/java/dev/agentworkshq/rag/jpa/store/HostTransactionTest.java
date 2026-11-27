package dev.agentworkshq.rag.jpa.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.jpa.repository.IngestionJobRepository;
import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import dev.agentworkshq.rag.jpa.testapp.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = TestApplication.class)
class HostTransactionTest {

    private static final SourceDocumentRecord DOCUMENT =
            new SourceDocumentRecord("docs", "a.md", "default", "h1", 3);

    @Autowired
    private DocumentRegistry registry;

    @Autowired
    private IngestionJobStore store;

    @Autowired
    private SourceDocumentRepository documents;

    @Autowired
    private IngestionJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate host;

    @BeforeEach
    void createHostTable() {
        host = new TransactionTemplate(transactionManager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS HOST_ROW (ID INT)");
    }

    @AfterEach
    void cleanUp() {
        documents.deleteAll();
        jobs.deleteAll();
        jdbc.execute("DROP TABLE HOST_ROW");
    }

    private void rolledBack(Runnable action) {
        host.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO HOST_ROW VALUES (1)");
            action.run();
            status.setRollbackOnly();
        });
        assertThat(hostRows()).isZero();
    }

    private int hostRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM HOST_ROW", Integer.class);
    }

    @Test
    void savedDocumentSurvivesRollback() {
        rolledBack(() -> registry.save(DOCUMENT));

        assertThat(registry.find("docs", "a.md", "default")).contains(DOCUMENT);
    }

    @Test
    void replacedDocumentSurvivesRollback() {
        registry.save(DOCUMENT);
        SourceDocumentRecord dirty = new SourceDocumentRecord("docs", "a.md", "default",
                SourceDocumentRecord.DIRTY_HASH, 7);

        rolledBack(() -> registry.save(dirty));

        assertThat(registry.find("docs", "a.md", "default")).contains(dirty);
    }

    @Test
    void deletedDocumentStaysDeletedAfterRollback() {
        registry.save(DOCUMENT);

        rolledBack(() -> registry.delete("docs", "a.md", "default"));

        assertThat(registry.find("docs", "a.md", "default")).isEmpty();
    }

    @Test
    void startedJobSurvivesRollback() {
        IngestionJobRecord[] started = new IngestionJobRecord[1];

        rolledBack(() -> started[0] = store.start("docs"));

        assertThat(store.findById(started[0].id())).hasValueSatisfying(
                job -> assertThat(job.status()).isEqualTo(IngestionStatus.RUNNING));
    }

    @Test
    void finishedJobSurvivesRollback() {
        Long id = store.start("docs").id();

        rolledBack(() -> store.finish(id, 1, 2, 3, 4, null));

        assertThat(store.findById(id)).hasValueSatisfying(job -> {
            assertThat(job.status()).isEqualTo(IngestionStatus.DONE);
            assertThat(job.added()).isEqualTo(1);
        });
    }

    @Test
    void failedJobSurvivesRollback() {
        Long id = store.start("docs").id();

        rolledBack(() -> store.fail(id, "boom"));

        assertThat(store.findById(id)).hasValueSatisfying(job -> {
            assertThat(job.status()).isEqualTo(IngestionStatus.FAILED);
            assertThat(job.errorMessage()).isEqualTo("boom");
        });
    }

    @Test
    void jobStartedAndFailedInOneRolledBackTransactionSurvives() {
        Long[] id = new Long[1];

        rolledBack(() -> {
            id[0] = store.start("docs").id();
            store.fail(id[0], "boom");
        });

        assertThat(store.findById(id[0])).hasValueSatisfying(
                job -> assertThat(job.status()).isEqualTo(IngestionStatus.FAILED));
    }

    @Test
    void failedSaveLeavesTheHostTransactionUsable() {
        SourceDocumentRecord tooLong = new SourceDocumentRecord("docs", "a.md", "default", "h".repeat(129), 1);

        host.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO HOST_ROW VALUES (1)");
            assertThatThrownBy(() -> registry.save(tooLong)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(status.isRollbackOnly()).isFalse();
            registry.save(DOCUMENT);
        });

        assertThat(hostRows()).isEqualTo(1);
        assertThat(registry.find("docs", "a.md", "default")).contains(DOCUMENT);
    }

    @Test
    void failedJobWriteLeavesTheHostTransactionUsable() {
        host.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO HOST_ROW VALUES (1)");
            assertThatThrownBy(() -> store.fail(-1L, "boom")).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> store.finish(-1L, 0, 0, 0, 0, null))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(status.isRollbackOnly()).isFalse();
        });

        assertThat(hostRows()).isEqualTo(1);
    }

    @Test
    void writesWorkWithoutAHostTransaction() {
        registry.save(DOCUMENT);
        assertThat(registry.find("docs", "a.md", "default")).contains(DOCUMENT);
        registry.delete("docs", "a.md", "default");
        assertThat(registry.find("docs", "a.md", "default")).isEmpty();

        Long done = store.start("docs").id();
        store.finish(done, 1, 0, 0, 0, null);
        Long failed = store.start("docs").id();
        store.fail(failed, "boom");

        assertThat(store.findById(done)).hasValueSatisfying(
                job -> assertThat(job.status()).isEqualTo(IngestionStatus.DONE));
        assertThat(store.findById(failed)).hasValueSatisfying(
                job -> assertThat(job.status()).isEqualTo(IngestionStatus.FAILED));
    }

    @Test
    void inMemoryStoresIgnoreTheHostTransaction() {
        DocumentRegistry memoryRegistry = new InMemoryDocumentRegistry();
        IngestionJobStore memoryStore = new InMemoryIngestionJobStore();
        Long[] id = new Long[1];

        rolledBack(() -> {
            memoryRegistry.save(DOCUMENT);
            id[0] = memoryStore.start("docs").id();
            memoryStore.fail(id[0], "boom");
        });

        assertThat(memoryRegistry.find("docs", "a.md", "default")).contains(DOCUMENT);
        assertThat(memoryStore.findById(id[0])).hasValueSatisfying(
                job -> assertThat(job.status()).isEqualTo(IngestionStatus.FAILED));
        assertThat(documents.count()).isZero();
        assertThat(jobs.count()).isZero();
    }
}
