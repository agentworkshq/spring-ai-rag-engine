package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult.Outcome;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class SingleReadIngestionTest {

    private final FailingStore store = new FailingStore();
    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final MapSource source = new MapSource();
    private final IngestionPipeline pipeline = new IngestionPipeline(() -> store,
            new DefaultDocumentLoader(1024 * 1024, 10), new RecursiveChunker(100, 10), registry,
            new InMemoryIngestionJobStore(), "RECURSIVE:100:10", 50, false, 0.5, 1024 * 1024);

    private DocumentIngestResult ingest(SourceItem item) {
        return pipeline.ingestDocument("docs", "t1", item);
    }

    private SourceDocumentRecord record(String name) {
        return registry.find("docs", name, "t1").orElseThrow();
    }

    private List<String> texts(String name) {
        return store.chunks("t1", name).stream().map(Document::getText).toList();
    }

    private static SourceItem changingOnSecondOpen(AtomicInteger opened) {
        return new SourceItem("a.txt", "a.txt", "text/plain", Instant.now(), 5, () -> new ByteArrayInputStream(
                (opened.incrementAndGet() == 1 ? "first" : "other").getBytes(StandardCharsets.UTF_8)));
    }

    private static String hashOf(String content) {
        return ContentHasher.effectiveHash(ContentHasher.fileHash(content.getBytes(StandardCharsets.UTF_8)),
                "RECURSIVE:100:10");
    }

    private void assertReadOnce(AtomicInteger opened) {
        assertThat(opened).hasValue(1);
        assertThat(texts("a.txt")).containsExactly("first");
        assertThat(record("a.txt").contentHash()).isEqualTo(hashOf("first"));
    }

    @Test
    void ingestDocumentReadsOnceSoHashAndChunksAgree() {
        AtomicInteger opened = new AtomicInteger();

        ingest(changingOnSecondOpen(opened));

        assertReadOnce(opened);
    }

    @Test
    void ingestReadsOnceSoHashAndChunksAgree() {
        AtomicInteger opened = new AtomicInteger();
        SourceItem item = changingOnSecondOpen(opened);
        RagSource changing = new RagSource() {
            @Override
            public String name() {
                return "docs";
            }

            @Override
            public Stream<SourceItem> scan() {
                return Stream.of(item);
            }
        };

        pipeline.ingest(changing, "t1");

        assertReadOnce(opened);
    }

    @Test
    void blankTextNeverReplacesExistingChunks() {
        ingest(MapSource.item("a.txt", "Alpha content."));

        assertNoText(MapSource.item("a.txt", " \n\t "));

        assertThat(texts("a.txt")).containsExactly("Alpha content.");
        assertThat(record("a.txt").contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH);
        assertThat(record("a.txt").chunkCount()).isEqualTo(1);
        assertThat(ingest(MapSource.item("a.txt", "Alpha content.")).outcome()).isEqualTo(Outcome.UPDATED);
    }

    @Test
    void aPdfWithoutTextNeverReplacesExistingChunks() throws IOException {
        ingest(MapSource.item("a.pdf", pdf("Alpha content")));
        List<String> before = texts("a.pdf");
        assertThat(before).hasSize(1);
        assertThat(before.get(0)).contains("Alpha");

        assertNoText(MapSource.item("a.pdf", pdf(null)));

        assertThat(texts("a.pdf")).isEqualTo(before);
        assertThat(record("a.pdf").contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH);
    }

    @Test
    void aNewDocumentWithoutTextIsCountedAsFailedAndStaysDirty() {
        source.files.put("blank.txt", "   ");

        IngestionResult result = pipeline.ingest(source, "t1");

        assertThat(result.failed()).isEqualTo(1);
        assertThat(registry.find("docs", "blank.txt", "t1")).hasValueSatisfying(r -> {
            assertThat(r.contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH);
            assertThat(r.chunkCount()).isZero();
        });
        assertThat(store.writes).isZero();
    }

    @Test
    void anEmptyFileIsAllowed() {
        DocumentIngestResult added = ingest(MapSource.item("empty.txt", ""));

        assertThat(added.outcome()).isEqualTo(Outcome.ADDED);
        assertThat(added.chunkCount()).isZero();
        assertThat(ingest(MapSource.item("empty.txt", "")).outcome()).isEqualTo(Outcome.SKIPPED);
    }

    @Test
    void anEmptiedFileRemovesTheOldChunks() {
        ingest(MapSource.item("a.txt", "Alpha content. ".repeat(20)));

        DocumentIngestResult result = ingest(MapSource.item("a.txt", ""));

        assertThat(result.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(texts("a.txt")).isEmpty();
        assertThat(record("a.txt").chunkCount()).isZero();
        assertThat(record("a.txt").contentHash()).isEqualTo(hashOf(""));
    }

    private void assertNoText(SourceItem item) {
        assertThatThrownBy(() -> ingest(item))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage())
                        .isEqualTo(ErrorMessages.RAG_E034));
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
