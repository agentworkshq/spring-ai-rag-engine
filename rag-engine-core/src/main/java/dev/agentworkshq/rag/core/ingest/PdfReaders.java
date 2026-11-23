package dev.agentworkshq.rag.core.ingest;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;

// Kept apart so DefaultDocumentLoader never links against optional classes
final class PdfReaders {

    private PdfReaders() {
    }

    static int pageCount(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return pdf.getNumberOfPages();
        }
    }

    static List<Document> read(byte[] bytes) {
        return new PagePdfDocumentReader(new ByteArrayResource(bytes)).get();
    }
}
