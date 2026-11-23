package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class DefaultDocumentLoaderTest {

    private static SourceItem item(String filename, byte[] content) {
        return new SourceItem(filename, filename, "application/octet-stream", Instant.now(), content.length,
                () -> new ByteArrayInputStream(content));
    }

    private static SourceItem text(String filename, String content) {
        return item(filename, content.getBytes(StandardCharsets.UTF_8));
    }

    private static ErrorMessages code(Throwable t) {
        return ((ServiceException) t).getErrorMessage();
    }

    @Test
    void loadsPlainText() {
        List<Document> docs = new DefaultDocumentLoader(1024, 10).load(text("notes.txt", "hello world"));

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).isEqualTo("hello world");
        assertThat(docs.get(0).getMetadata()).containsEntry(ChunkMetadata.FILENAME, "notes.txt");
    }

    @Test
    void loadsMarkdown() {
        List<Document> docs = new DefaultDocumentLoader(1024, 10).load(text("README.MD", "# Title\n\nbody"));

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).contains("# Title");
    }

    @Test
    void blankFileGivesNoDocuments() {
        assertThat(new DefaultDocumentLoader(1024, 10).load(text("empty.txt", "  \n"))).isEmpty();
    }

    @Test
    void fileOverMaxSizeIsRejected() {
        DefaultDocumentLoader loader = new DefaultDocumentLoader(5, 10);

        assertThatThrownBy(() -> loader.load(text("big.txt", "0123456789")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E009));
    }

    @Test
    void understatedSizeIsStillBounded() {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        SourceItem lying = new SourceItem("big.txt", "big.txt", "text/plain", Instant.now(), 1,
                () -> new ByteArrayInputStream(content));

        assertThatThrownBy(() -> new DefaultDocumentLoader(5, 10).load(lying))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E009));
    }

    @Test
    void missingPdfReaderNamesTheDependency() {
        DefaultDocumentLoader loader = new DefaultDocumentLoader(1024, 10, false, true);

        assertThatThrownBy(() -> loader.load(item("a.pdf", new byte[] {1})))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E011))
                .hasMessageContaining("spring-ai-pdf-document-reader");
    }

    @Test
    void missingTikaReaderNamesTheDependency() {
        DefaultDocumentLoader loader = new DefaultDocumentLoader(1024, 10, true, false);

        assertThatThrownBy(() -> loader.load(item("a.docx", new byte[] {1})))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E011))
                .hasMessageContaining("spring-ai-tika-document-reader");
    }

    @Test
    void unsupportedExtensionIsRejected() {
        assertThatThrownBy(() -> new DefaultDocumentLoader(1024, 10).load(item("a.exe", new byte[] {1})))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E012));
    }

    @Test
    void pdfOverMaxPagesIsRejected() throws IOException {
        byte[] pdf = blankPdf(3);

        assertThatThrownBy(() -> new DefaultDocumentLoader(1024 * 1024, 2).load(item("a.pdf", pdf)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorMessages.RAG_E010));
    }

    private static byte[] blankPdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
