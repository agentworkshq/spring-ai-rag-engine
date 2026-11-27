package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.FakeEmbeddingModel;
import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class IngestionPipelineTest {

    private static final String SENTENCES = "The quick brown fox jumps over the lazy dog. ";

    @TempDir
    Path root;

    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final InMemoryIngestionJobStore jobs = new InMemoryIngestionJobStore();
    private RecordingStore store;
    private FilesystemSource source;

    @BeforeEach
    void setUp() {
        store = new RecordingStore(SimpleVectorStore.builder(new FakeEmbeddingModel()).build());
        source = new FilesystemSource("docs", root, List.of());
    }

    private IngestionPipeline pipeline(int chunkSize, int batchSize, boolean allowEmpty) {
        return new IngestionPipeline(() -> store, new DefaultDocumentLoader(1024 * 1024, 10),
                new RecursiveChunker(chunkSize, 10), registry, jobs, "RECURSIVE:" + chunkSize + ":10", batchSize,
                allowEmpty, 0.5, 1024 * 1024);
    }

    private IngestionPipeline pipeline() {
        return pipeline(100, 50, false);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(root.resolve(name), content);
    }

    private List<Document> stored(String tenant) {
        return store.search(new FilterExpressionBuilder().eq(ChunkMetadata.TENANT_ID, tenant).build());
    }

    private List<Document> stored(String tenant, String filename) {
        return stored(tenant).stream().filter(d -> filename.equals(d.getMetadata().get(ChunkMetadata.FILENAME)))
                .toList();
    }

    private IngestionJobRecord job(IngestionResult result) {
        return jobs.findById(result.jobId()).orElseThrow();
    }

    @Test
    void newFileIsAddedWithMetadata() throws IOException {
        write("a.txt", SENTENCES.repeat(10));

        IngestionResult result = pipeline().ingest(source, "t1");

        assertThat(result).extracting(IngestionResult::added, IngestionResult::updated, IngestionResult::deleted,
                IngestionResult::skipped, IngestionResult::failed).containsExactly(1, 0, 0, 0, 0);
        List<Document> chunks = stored("t1");
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.getMetadata())
                .containsEntry(ChunkMetadata.SOURCE_NAME, "docs")
                .containsEntry(ChunkMetadata.FILENAME, "a.txt")
                .containsEntry(ChunkMetadata.TENANT_ID, "t1")
                .containsKeys(ChunkMetadata.DOCUMENT_ID, ChunkMetadata.CHUNK_INDEX, ChunkMetadata.CONTENT_HASH));
        assertThat(registry.find("docs", "a.txt", "t1")).isPresent();
        IngestionJobRecord job = job(result);
        assertThat(job.status()).isEqualTo(IngestionStatus.DONE);
        assertThat(job.added()).isEqualTo(1);
        assertThat(job.errorMessage()).isNull();
    }

    @Test
    void unchangedFileIsSkipped() throws IOException {
        write("a.txt", SENTENCES.repeat(10));
        pipeline().ingest(source, "t1");
        int chunkCount = stored("t1").size();

        IngestionResult result = pipeline().ingest(source, "t1");

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.added() + result.updated() + result.deleted()).isZero();
        assertThat(stored("t1")).hasSize(chunkCount);
        assertThat(job(result).skipped()).isEqualTo(1);
    }

    @Test
    void changedFileIsReingestedAndOldChunksAreGone() throws IOException {
        write("a.txt", "Alpha content. ".repeat(20));
        pipeline().ingest(source, "t1");
        write("a.txt", "Omega content. ".repeat(20));

        IngestionResult result = pipeline().ingest(source, "t1");

        assertThat(result.updated()).isEqualTo(1);
        assertThat(stored("t1")).isNotEmpty().allSatisfy(c -> {
            assertThat(c.getText()).contains("Omega");
            assertThat(c.getText()).doesNotContain("Alpha");
        });
    }

    @Test
    void removedFileIsDeletedFromStoreAndRegistry() throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        write("b.txt", SENTENCES.repeat(5));
        pipeline().ingest(source, "t1");
        Files.delete(root.resolve("a.txt"));

        IngestionResult result = pipeline().ingest(source, "t1");

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(stored("t1", "a.txt")).isEmpty();
        assertThat(stored("t1", "b.txt")).isNotEmpty();
        assertThat(registry.find("docs", "a.txt", "t1")).isEmpty();
        assertThat(job(result).deleted()).isEqualTo(1);
    }

    @Test
    void tenantsAreIsolated() throws IOException {
        write("a.txt", "Alpha content. ".repeat(20));
        pipeline().ingest(source, "A");
        pipeline().ingest(source, "B");
        List<String> tenantBBefore = stored("B").stream().map(Document::getId).sorted().toList();
        assertThat(tenantBBefore).isNotEmpty();
        assertThat(stored("A")).hasSameSizeAs(stored("B"));

        write("a.txt", "Omega content. ".repeat(20));
        pipeline().ingest(source, "A");
        assertThat(stored("B").stream().map(Document::getId).sorted().toList()).isEqualTo(tenantBBefore);
        assertThat(stored("B")).allSatisfy(c -> assertThat(c.getText()).contains("Alpha"));
        assertThat(stored("A")).allSatisfy(c -> assertThat(c.getText()).contains("Omega"));

        Files.delete(root.resolve("a.txt"));
        pipeline(100, 50, true).ingest(source, "A");
        assertThat(stored("A")).isEmpty();
        assertThat(stored("B").stream().map(Document::getId).sorted().toList()).isEqualTo(tenantBBefore);
        assertThat(registry.find("docs", "a.txt", "B")).isPresent();
    }

    @Test
    void everyDeleteFilterCarriesTheTenant() throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        pipeline().ingest(source, "A");
        write("a.txt", SENTENCES.repeat(6));
        pipeline().ingest(source, "A");
        Files.delete(root.resolve("a.txt"));
        pipeline(100, 50, true).ingest(source, "A");

        assertThat(store.deleteFilters).isNotEmpty().allSatisfy(f -> assertThat(f).contains(ChunkMetadata.TENANT_ID));
    }

    @Test
    void changingChunkSizeCausesReingestion() throws IOException {
        write("a.txt", SENTENCES.repeat(20));
        pipeline(100, 50, false).ingest(source, "t1");

        IngestionResult result = pipeline(200, 50, false).ingest(source, "t1");

        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void failingDocumentDoesNotStopTheOthers() throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        write("b.txt", "BOOM secret text");
        write("c.txt", SENTENCES.repeat(5));

        IngestionResult result = pipeline().ingest(source, "t1");

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(registry.find("docs", "b.txt", "t1")).hasValueSatisfying(r ->
                assertThat(r.contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH));
        IngestionJobRecord job = job(result);
        assertThat(job.status()).isEqualTo(IngestionStatus.DONE);
        assertThat(job.errorMessage()).isEqualTo("1 documents failed").doesNotContain("BOOM");
    }

    @Test
    void failedUpdateIsReingestedOnTheNextRun() throws IOException {
        write("a.txt", "Alpha content. ".repeat(20));
        pipeline().ingest(source, "t1");
        write("a.txt", "BOOM content. ".repeat(20));

        IngestionResult failed = pipeline().ingest(source, "t1");

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(stored("t1")).isNotEmpty().allSatisfy(c -> assertThat(c.getText()).contains("Alpha"));
        assertThat(registry.find("docs", "a.txt", "t1")).hasValueSatisfying(r ->
                assertThat(r.contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH));

        write("a.txt", "Omega content. ".repeat(20));
        IngestionResult retry = pipeline().ingest(source, "t1");

        assertThat(retry.updated()).isEqualTo(1);
        assertThat(stored("t1")).isNotEmpty().allSatisfy(c -> assertThat(c.getText()).contains("Omega"));
    }

    @Test
    void emptyScanWithRegisteredDocumentsDeletesNothingAndFailsTheJob() throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        pipeline().ingest(source, "t1");
        int chunkCount = stored("t1").size();
        Files.delete(root.resolve("a.txt"));

        assertThatThrownBy(() -> pipeline().ingest(source, "t1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage()).isEqualTo(ErrorMessages.RAG_E017));

        assertThat(stored("t1")).hasSize(chunkCount);
        assertThat(registry.find("docs", "a.txt", "t1")).isPresent();
        IngestionJobRecord latest = jobs.findRecent("docs", 1).get(0);
        assertThat(latest.status()).isEqualTo(IngestionStatus.FAILED);
    }

    @Test
    void emptyScanDeletesWhenAllowed() throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        pipeline().ingest(source, "t1");
        Files.delete(root.resolve("a.txt"));

        IngestionResult result = pipeline(100, 50, true).ingest(source, "t1");

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(stored("t1")).isEmpty();
        assertThat(registry.findBySource("docs", "t1")).isEmpty();
    }

    @Test
    void secondConcurrentRunForSameSourceAndTenantIsRejected() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RagSource blocking = new RagSource() {
            @Override
            public String name() {
                return "docs";
            }

            @Override
            public Stream<SourceItem> scan() {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Stream.empty();
            }
        };
        IngestionPipeline pipeline = pipeline();
        CompletableFuture<IngestionResult> first = CompletableFuture.supplyAsync(() -> pipeline.ingest(blocking, "t1"));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> pipeline.ingest(blocking, "t1"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage()).isEqualTo(ErrorMessages.RAG_E016));
        assertThat(pipeline.ingest(source, "t2")).isNotNull();

        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(pipeline.ingest(blocking, "t1")).isNotNull();
    }

    @Test
    void batchSizeIsRespected() throws IOException {
        write("a.txt", SENTENCES.repeat(30));

        pipeline(100, 3, false).ingest(source, "t1");

        assertThat(store.batchSizes).hasSizeGreaterThan(1).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(3));
        assertThat(store.batchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(stored("t1").size());
    }

    @Test
    void fatalErrorFailsTheJobWithShortMessage() {
        RagSource broken = new RagSource() {
            @Override
            public String name() {
                return "docs";
            }

            @Override
            public Stream<SourceItem> scan() {
                throw new IllegalStateException("secret file contents");
            }
        };

        assertThatThrownBy(() -> pipeline().ingest(broken, "t1")).isInstanceOf(ServiceException.class);

        IngestionJobRecord job = jobs.findRecent("docs", 1).get(0);
        assertThat(job.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(job.errorMessage()).isEqualTo("Ingestion failed: IllegalStateException")
                .doesNotContain("secret").doesNotContain("\n").doesNotContain(" at ");
    }

    @Test
    void scanFailingAfterSomeItemsDeletesNothing() throws IOException {
        assertFailedScanDeletesNothing(new IllegalStateException("scan broke"));
    }

    @Test
    void uncheckedIoFailureDuringTheScanDeletesNothing() throws IOException {
        assertFailedScanDeletesNothing(new UncheckedIOException(new IOException("listing broke")));
    }

    private void assertFailedScanDeletesNothing(RuntimeException failure) throws IOException {
        write("a.txt", SENTENCES.repeat(5));
        write("b.txt", SENTENCES.repeat(5));
        pipeline().ingest(source, "t1");
        List<String> chunksBefore = stored("t1").stream().map(Document::getId).sorted().toList();
        List<SourceDocumentRecord> registryBefore = registry.findBySource("docs", "t1");
        store.deleteFilters.clear();
        store.batchSizes.clear();
        SourceItem first = source.scan().findFirst().orElseThrow();
        AtomicInteger produced = new AtomicInteger();
        RagSource broken = new RagSource() {
            @Override
            public String name() {
                return "docs";
            }

            @Override
            public Stream<SourceItem> scan() {
                return Stream.concat(Stream.of(first).peek(item -> produced.incrementAndGet()),
                        Stream.generate(() -> {
                            throw failure;
                        }));
            }
        };

        assertThatThrownBy(() -> pipeline().ingest(broken, "t1"))
                .isInstanceOf(ServiceException.class)
                .hasCause(failure);

        assertThat(produced).hasValue(1);
        assertThat(store.deleteFilters).isEmpty();
        assertThat(store.batchSizes).isEmpty();
        assertThat(stored("t1").stream().map(Document::getId).sorted().toList()).isEqualTo(chunksBefore);
        assertThat(registry.findBySource("docs", "t1")).isEqualTo(registryBefore);
        assertThat(jobs.findRecent("docs", 1).get(0).status()).isEqualTo(IngestionStatus.FAILED);
    }

    private static class RecordingStore implements VectorStore {

        final List<Integer> batchSizes = new ArrayList<>();
        final List<String> deleteFilters = new ArrayList<>();
        private final SimpleVectorStore delegate;

        RecordingStore(SimpleVectorStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(List<Document> documents) {
            batchSizes.add(documents.size());
            delegate.add(documents);
        }

        @Override
        public void delete(List<String> idList) {
            delegate.delete(idList);
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            deleteFilters.add(filterExpression.toString());
            delegate.delete(filterExpression);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return delegate.similaritySearch(request);
        }

        List<Document> search(Filter.Expression filter) {
            return delegate.similaritySearch(SearchRequest.builder().query("fox").topK(100000)
                    .similarityThresholdAll().filterExpression(filter).build());
        }
    }
}
