package dev.agentworkshq.rag.core.ingest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;

final class Chunks {

    private Chunks() {
    }

    static Document of(Document parent, int index, String text) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        String id = UUID.nameUUIDFromBytes((parent.getId() + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
        return Document.builder().id(id).text(text).metadata(metadata).build();
    }
}
