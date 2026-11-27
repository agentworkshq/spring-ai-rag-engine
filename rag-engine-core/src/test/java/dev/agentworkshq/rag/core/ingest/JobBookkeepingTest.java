package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class JobBookkeepingTest {

    private final FailingStore store = new FailingStore();
    private final MapSource source = new MapSource();
    private final FailingJobs jobs = new FailingJobs();
    private final IngestionPipeline pipeline = pipeline(store);

    private IngestionPipeline pipeline(FailingStore vectorStore) {
        return new IngestionPipeline(() -> vectorStore, new DefaultDocumentLoader(1024, 10),
                new RecursiveChunker(100, 10), new InMemoryDocumentRegistry(), jobs, "RECURSIVE:100:10", 50, false,
                0.5, 1024);
    }

    private IngestionJobRecord latest() {
        return jobs.findRecent("docs", 1).get(0);
    }

    private static RagSource failingWith(Error error) {
        return new RagSource() {
            @Override
            public String name() {
                return "docs";
            }

            @Override
            public Stream<SourceItem> scan() {
                throw error;
            }
        };
    }

    @Test
    void aFailingFinishStillReturnsTheResultAndMarksTheJobFailed() {
        source.files.put("a.txt", "content");
        jobs.failFinish = true;

        IngestionResult result = pipeline.ingest(source, "t1");

        assertThat(result.added()).isEqualTo(1);
        assertThat(latest().status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(latest().errorMessage())
                .isEqualTo("Result not recorded: added=1 updated=0 deleted=0 skipped=0 failed=0");
    }

    @Test
    void theResultSurvivesWhenTheJobCannotBeMarkedFailedEither() {
        source.files.put("a.txt", "content");
        jobs.failFinish = true;
        jobs.failFail = true;

        assertThat(pipeline.ingest(source, "t1").added()).isEqualTo(1);
    }

    @Test
    void anErrorDuringTheScanFailsTheJobAndIsRethrownUnchanged() {
        Error error = new AssertionError("scan broke");

        assertThatThrownBy(() -> pipeline.ingest(failingWith(error), "t1")).isSameAs(error);

        assertThat(latest().status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(latest().errorMessage()).isEqualTo("Ingestion failed: AssertionError");
    }

    @Test
    void anErrorFromTheStoreFailsTheJobAndIsRethrownUnchanged() {
        source.files.put("a.txt", "content");
        Error error = new StackOverflowError();
        FailingStore broken = new FailingStore() {
            @Override
            public void add(List<Document> documents) {
                throw error;
            }
        };

        assertThatThrownBy(() -> pipeline(broken).ingest(source, "t1")).isSameAs(error);

        assertThat(latest().status()).isEqualTo(IngestionStatus.FAILED);
    }

    @Test
    void anOriginalErrorIsNotHiddenWhenTheJobCannotBeMarkedFailed() {
        Error error = new AssertionError("scan broke");
        jobs.failFail = true;

        assertThatThrownBy(() -> pipeline.ingest(failingWith(error), "t1")).isSameAs(error);
    }

    @Test
    void theLockIsReleasedAfterAnError() {
        assertThatThrownBy(() -> pipeline.ingest(failingWith(new AssertionError()), "t1"))
                .isInstanceOf(AssertionError.class);

        assertThat(pipeline.ingest(source, "t1")).isNotNull();
    }

    private static class FailingJobs implements IngestionJobStore {

        private final InMemoryIngestionJobStore delegate = new InMemoryIngestionJobStore();
        boolean failFinish;
        boolean failFail;

        @Override
        public IngestionJobRecord start(String sourceName) {
            return delegate.start(sourceName);
        }

        @Override
        public IngestionJobRecord finish(Long id, int added, int updated, int deleted, int skipped,
                                         String errorMessage) {
            if (failFinish) {
                throw new IllegalStateException("job store failed");
            }
            return delegate.finish(id, added, updated, deleted, skipped, errorMessage);
        }

        @Override
        public IngestionJobRecord fail(Long id, String errorMessage) {
            if (failFail) {
                throw new IllegalStateException("job store failed again");
            }
            return delegate.fail(id, errorMessage);
        }

        @Override
        public Optional<IngestionJobRecord> findById(Long id) {
            return delegate.findById(id);
        }

        @Override
        public List<IngestionJobRecord> findRecent(String sourceName, int limit) {
            return delegate.findRecent(sourceName, limit);
        }
    }
}
