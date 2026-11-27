package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeleteGuardTest {

    private static final String SIX_OF_TEN =
            "Run would remove 6 of 10 known documents, more than max-delete-ratio allows, nothing was deleted";

    private final FailingStore store = new FailingStore();
    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final InMemoryIngestionJobStore jobs = new InMemoryIngestionJobStore();
    private final MapSource source = new MapSource();

    private IngestionPipeline pipeline(double ratio, boolean allowEmpty) {
        return new IngestionPipeline(() -> store, new DefaultDocumentLoader(1024, 10), new RecursiveChunker(100, 10),
                registry, jobs, "RECURSIVE:100:10", 50, allowEmpty, ratio, 1024);
    }

    private void ingest(int documents) {
        for (int i = 0; i < documents; i++) {
            source.files.put(i + ".txt", "content " + i);
        }
        pipeline(0.5, false).ingest(source, "t1");
    }

    private void remove(int documents) {
        for (int i = 0; i < documents; i++) {
            source.files.remove(i + ".txt");
        }
    }

    private void assertBlocked(IngestionPipeline pipeline, String message) {
        List<SourceDocumentRecord> before = registry.findBySource("docs", "t1");
        int writes = store.writes;

        assertThatThrownBy(() -> pipeline.ingest(source, "t1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage(message)
                .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage())
                        .isEqualTo(ErrorMessages.RAG_E035));

        assertThat(store.writes).isEqualTo(writes);
        assertThat(registry.findBySource("docs", "t1")).isEqualTo(before);
        IngestionJobRecord job = jobs.findRecent("docs", 1).get(0);
        assertThat(job.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(job.errorMessage()).isEqualTo(message);
    }

    @Test
    void removingSixOfTenFailsBeforeAnythingChanges() {
        ingest(10);
        remove(6);
        source.files.put("new.txt", "new content");

        assertBlocked(pipeline(0.5, false), SIX_OF_TEN);
    }

    @Test
    void removingFourOfTenPasses() {
        ingest(10);
        remove(4);

        assertThat(pipeline(0.5, false).ingest(source, "t1").deleted()).isEqualTo(4);
    }

    @Test
    void removingExactlyTheRatioPasses() {
        ingest(10);
        remove(5);

        assertThat(pipeline(0.5, false).ingest(source, "t1").deleted()).isEqualTo(5);
    }

    @Test
    void fewerThanTenKnownDocumentsAreNotGuarded() {
        ingest(9);
        remove(8);

        assertThat(pipeline(0.5, false).ingest(source, "t1").deleted()).isEqualTo(8);
    }

    @Test
    void aRatioOfOneDisablesTheGuard() {
        ingest(10);
        remove(10);

        assertThat(pipeline(1, true).ingest(source, "t1").deleted()).isEqualTo(10);
    }

    @Test
    void allowingAnEmptySourceDoesNotBypassTheGuard() {
        ingest(10);
        remove(10);

        assertBlocked(pipeline(0.5, true),
                "Run would remove 10 of 10 known documents, more than max-delete-ratio allows, nothing was deleted");
    }

    @Test
    void anotherTenantsDocumentsDoNotCount() {
        ingest(10);
        pipeline(0.5, false).ingest(source, "t2");
        remove(6);

        assertBlocked(pipeline(0.5, false), SIX_OF_TEN);
        assertThat(registry.findBySource("docs", "t2")).hasSize(10);
    }
}
