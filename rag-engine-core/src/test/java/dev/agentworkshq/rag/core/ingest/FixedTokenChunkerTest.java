package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class FixedTokenChunkerTest {

    private static Document doc(String text) {
        return Document.builder().text(text).metadata(Map.of(ChunkMetadata.FILENAME, "a.txt")).build();
    }

    @Test
    void splitsLongTextAndKeepsMetadata() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(100);

        List<Document> chunks = new FixedTokenChunker(50).split(List.of(doc(text)));

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getText()).isNotBlank();
            assertThat(chunks.get(i).getMetadata())
                    .containsEntry(ChunkMetadata.FILENAME, "a.txt")
                    .containsEntry(ChunkMetadata.CHUNK_INDEX, i);
        }
    }

    @Test
    void shortTextIsOneChunk() {
        assertThat(new FixedTokenChunker(100).split(List.of(doc("short text")))).hasSize(1);
    }

    @Test
    void emptyInputGivesNoChunks() {
        assertThat(new FixedTokenChunker(100).split(List.of())).isEmpty();
    }

    @Test
    void outputIsDeterministic() {
        Document source = doc("The quick brown fox jumps over the lazy dog. ".repeat(100));
        FixedTokenChunker chunker = new FixedTokenChunker(50);

        List<String> first = chunker.split(List.of(source)).stream().map(Document::getId).toList();
        List<String> second = chunker.split(List.of(source)).stream().map(Document::getId).toList();

        assertThat(second).isEqualTo(first);
    }
}
