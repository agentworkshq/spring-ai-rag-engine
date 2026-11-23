package dev.agentworkshq.rag.core.ingest;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

// Kept apart so DefaultDocumentLoader never links against optional classes
final class TikaReaders {

    private TikaReaders() {
    }

    static List<Document> read(byte[] bytes) {
        return new TikaDocumentReader(new ByteArrayResource(bytes)).get();
    }
}
