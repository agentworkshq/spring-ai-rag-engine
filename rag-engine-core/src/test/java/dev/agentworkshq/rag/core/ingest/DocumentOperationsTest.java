package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.FakeEmbeddingModel;
import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.BaseException;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult.Outcome;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class DocumentOperationsTest {

    private static final String SOURCE = "uploads";

    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final InMemoryIngestionJobStore jobs = new InMemoryIngestionJobStore();
    private SimpleVectorStore store;
    private IngestionPipeline pipeline;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(new FakeEmbeddingModel()).build();
        pipeline = new IngestionPipeline(() -> store, new DefaultDocumentLoader(1024 * 1024, 10),
                new RecursiveChunker(100, 10), registry, jobs, "RECURSIVE:100:10", 50, false, 0.5,
                1024 * 1024);
        service = new IngestionService(new SourceRegistry(List.of()), pipeline);
    }

    private static SourceItem item(String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SourceItem(name, name, "text/plain", Instant.now(), bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private List<Document> stored(String tenant, String filename) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return store.similaritySearch(SearchRequest.builder().query("fox").topK(100000).similarityThresholdAll()
                        .filterExpression(b.eq(ChunkMetadata.TENANT_ID, tenant).build()).build()).stream()
                .filter(d -> filename.equals(d.getMetadata().get(ChunkMetadata.FILENAME))).toList();
    }

    @Test
    void newDocumentIsAdded() {
        DocumentIngestResult result = service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));

        assertThat(result.outcome()).isEqualTo(Outcome.ADDED);
        assertThat(result.externalId()).isEqualTo("a.txt");
        assertThat(result.documentId()).isNotBlank();
        assertThat(result.chunkCount()).isGreaterThan(1);
        assertThat(stored("t1", "a.txt")).hasSize(result.chunkCount());
        assertThat(registry.find(SOURCE, "a.txt", "t1")).isPresent();
        assertThat(jobs.findRecent(SOURCE, 10)).isEmpty();
    }

    @Test
    void unchangedDocumentIsSkipped() {
        DocumentIngestResult first = service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));

        DocumentIngestResult second = service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));

        assertThat(second.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(second.chunkCount()).isEqualTo(first.chunkCount());
        assertThat(stored("t1", "a.txt")).hasSize(first.chunkCount());
    }

    @Test
    void changedDocumentIsUpdatedAndOldChunksAreGone() {
        service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));

        DocumentIngestResult result = service.ingestDocument(SOURCE, "t1", item("a.txt", "Omega content. ".repeat(20)));

        assertThat(result.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(stored("t1", "a.txt")).isNotEmpty().allSatisfy(c -> {
            assertThat(c.getText()).contains("Omega");
            assertThat(c.getText()).doesNotContain("Alpha");
        });
    }

    @Test
    void deleteRemovesChunksAndRegistryEntry() {
        service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));
        service.ingestDocument(SOURCE, "t1", item("b.txt", "Beta content. ".repeat(20)));

        service.deleteDocument(SOURCE, "t1", "a.txt");

        assertThat(stored("t1", "a.txt")).isEmpty();
        assertThat(registry.find(SOURCE, "a.txt", "t1")).isEmpty();
        assertThat(stored("t1", "b.txt")).isNotEmpty();
        assertThat(service.listDocuments(SOURCE, "t1")).extracting(r -> r.externalId()).containsExactly("b.txt");
    }

    @Test
    void deleteOfUnknownDocumentIsNotFound() {
        assertThatThrownBy(() -> service.deleteDocument(SOURCE, "t1", "nope.txt"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorMessage()).isEqualTo(ErrorMessages.RAG_E002));
    }

    @Test
    void singleDocumentCallsNeverDeleteOtherDocuments() {
        service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha content. ".repeat(20)));
        service.ingestDocument(SOURCE, "t1", item("b.txt", "Beta content. ".repeat(20)));

        service.ingestDocument(SOURCE, "t1", item("c.txt", "Gamma content. ".repeat(20)));
        service.ingestDocument(SOURCE, "t1", item("b.txt", "Beta changed. ".repeat(20)));

        assertThat(stored("t1", "a.txt")).isNotEmpty();
        assertThat(registry.findBySource(SOURCE, "t1")).extracting(r -> r.externalId())
                .containsExactly("a.txt", "b.txt", "c.txt");
    }

    @Test
    void tenantsAreIsolated() {
        service.ingestDocument(SOURCE, "A", item("a.txt", "Alpha content. ".repeat(20)));
        service.ingestDocument(SOURCE, "B", item("a.txt", "Alpha content. ".repeat(20)));
        int tenantB = stored("B", "a.txt").size();

        service.ingestDocument(SOURCE, "A", item("a.txt", "Omega content. ".repeat(20)));
        assertThat(stored("B", "a.txt")).hasSize(tenantB).allSatisfy(c -> assertThat(c.getText()).contains("Alpha"));

        service.deleteDocument(SOURCE, "A", "a.txt");
        assertThat(stored("A", "a.txt")).isEmpty();
        assertThat(stored("B", "a.txt")).hasSize(tenantB);
        assertThat(registry.find(SOURCE, "a.txt", "B")).isPresent();
        assertThatThrownBy(() -> service.deleteDocument(SOURCE, "A", "a.txt"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.listDocuments(SOURCE, "A")).isEmpty();
        assertThat(service.listDocuments(SOURCE, "B")).hasSize(1);
    }

    @Test
    void lockIsSharedWithFullIngest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RagSource blocking = new RagSource() {
            @Override
            public String name() {
                return SOURCE;
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
        CompletableFuture<IngestionResult> full = CompletableFuture.supplyAsync(() -> pipeline.ingest(blocking, "t1"));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha")))
                .satisfies(e -> assertThat(((BaseException) e).getErrorMessage()).isEqualTo(ErrorMessages.RAG_E016));
        assertThatThrownBy(() -> service.deleteDocument(SOURCE, "t1", "a.txt"))
                .satisfies(e -> assertThat(((BaseException) e).getErrorMessage()).isEqualTo(ErrorMessages.RAG_E016));
        assertThat(service.ingestDocument(SOURCE, "t2", item("a.txt", "Alpha")).outcome()).isEqualTo(Outcome.ADDED);

        release.countDown();
        full.get(10, TimeUnit.SECONDS);
        assertThat(service.ingestDocument(SOURCE, "t1", item("a.txt", "Alpha")).outcome()).isEqualTo(Outcome.ADDED);
    }

    @Test
    void uploadsIsAReservedSourceName() {
        RagSource reserved = new RagSource() {
            @Override
            public String name() {
                return "uploads";
            }

            @Override
            public Stream<SourceItem> scan() {
                return Stream.empty();
            }
        };

        assertThatThrownBy(() -> new SourceRegistry(List.of(reserved)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
    }
}
