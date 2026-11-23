package dev.agentworkshq.rag.core.ingest;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

public class FixedTokenChunker implements ChunkingStrategy {

    private final TokenTextSplitter splitter;

    public FixedTokenChunker(int chunkSize) {
        this.splitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
    }

    @Override
    public List<Document> split(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document parent : documents) {
            List<Document> pieces = splitter.apply(List.of(parent));
            int index = 0;
            for (Document piece : pieces) {
                String text = piece.getText();
                if (text != null && !text.isBlank()) {
                    result.add(Chunks.of(parent, index++, text));
                }
            }
        }
        return result;
    }
}
