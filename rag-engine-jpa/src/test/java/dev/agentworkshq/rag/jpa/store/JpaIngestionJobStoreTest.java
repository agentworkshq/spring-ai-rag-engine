package dev.agentworkshq.rag.jpa.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.jpa.repository.IngestionJobRepository;
import dev.agentworkshq.rag.jpa.testapp.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApplication.class)
class JpaIngestionJobStoreTest {

    @Autowired
    private IngestionJobStore store;

    @Autowired
    private IngestionJobRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void jpaStoreReplacesInMemoryDefault() {
        assertThat(store).isInstanceOf(JpaIngestionJobStore.class);
    }

    @Test
    void startCreatesRunningJob() {
        IngestionJobRecord job = store.start("docs");

        assertThat(job.id()).isNotNull();
        assertThat(job.status()).isEqualTo(IngestionStatus.RUNNING);
        assertThat(job.startedAt()).isNotNull();
        assertThat(job.finishedAt()).isNull();
    }

    @Test
    void finishStoresCounts() {
        IngestionJobRecord job = store.start("docs");

        IngestionJobRecord done = store.finish(job.id(), 1, 2, 3, 4, null);

        assertThat(done.status()).isEqualTo(IngestionStatus.DONE);
        assertThat(done.added()).isEqualTo(1);
        assertThat(done.updated()).isEqualTo(2);
        assertThat(done.deleted()).isEqualTo(3);
        assertThat(done.skipped()).isEqualTo(4);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(store.findById(job.id())).hasValueSatisfying(found -> assertThat(found).usingRecursiveComparison()
                .ignoringFields("startedAt", "finishedAt").isEqualTo(done));
    }

    @Test
    void failStoresShortMessage() {
        IngestionJobRecord job = store.start("docs");

        IngestionJobRecord failed = store.fail(job.id(), "x".repeat(900));

        assertThat(failed.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(failed.errorMessage()).hasSize(500);
    }

    @Test
    void findRecentIsNewestFirstAndLimited() {
        IngestionJobRecord first = store.start("docs");
        IngestionJobRecord second = store.start("docs");
        store.start("wiki");

        assertThat(store.findRecent("docs", 10)).extracting(IngestionJobRecord::id)
                .containsExactly(second.id(), first.id());
        assertThat(store.findRecent("docs", 1)).hasSize(1);
    }
}
