package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RecursiveChunkerTest {

    private static final String PROSE = ("The quick brown fox jumps over the lazy dog. "
            + "Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump!\n\n")
            .repeat(20);

    private static Document doc(String text) {
        return Document.builder().text(text).metadata(Map.of(ChunkMetadata.FILENAME, "a.txt", "custom", "x")).build();
    }

    @Test
    void everyChunkFitsChunkSizeAndIsNotEmpty() {
        List<Document> chunks = new RecursiveChunker(120, 20).split(List.of(doc(PROSE)));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.getText()).isNotBlank();
            assertThat(c.getText().length()).isLessThanOrEqualTo(120);
        });
    }

    @Test
    void textWithoutSeparatorsIsHardCut() {
        List<Document> chunks = new RecursiveChunker(50, 0).split(List.of(doc("x".repeat(260))));

        assertThat(chunks).hasSize(6);
        assertThat(chunks).allSatisfy(c -> assertThat(c.getText().length()).isLessThanOrEqualTo(50));
    }

    @Test
    void overlapRepeatsTheEndOfThePreviousChunk() {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma";
        List<Document> chunks = new RecursiveChunker(40, 12).split(List.of(doc(text)));

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String previousLastWord = lastWord(chunks.get(i - 1).getText());
            assertThat(chunks.get(i).getText()).contains(previousLastWord);
        }
    }

    @Test
    void zeroOverlapDoesNotRepeatText() {
        String text = "one two three four five six seven eight nine ten eleven twelve";
        List<Document> chunks = new RecursiveChunker(20, 0).split(List.of(doc(text)));

        String rejoined = String.join(" ", chunks.stream().map(Document::getText).toList());
        assertThat(rejoined).isEqualTo(text);
    }

    @Test
    void emptyInputGivesNoChunks() {
        RecursiveChunker chunker = new RecursiveChunker(100, 10);

        assertThat(chunker.split(List.of())).isEmpty();
        assertThat(chunker.split(List.of(doc("   \n\n  ")))).isEmpty();
    }

    @Test
    void outputIsDeterministic() {
        Document source = doc(PROSE);
        RecursiveChunker chunker = new RecursiveChunker(150, 30);

        List<Document> first = chunker.split(List.of(source));
        List<Document> second = chunker.split(List.of(source));

        assertThat(second).hasSameSizeAs(first);
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i).getId()).isEqualTo(first.get(i).getId());
            assertThat(second.get(i).getText()).isEqualTo(first.get(i).getText());
        }
    }

    @Test
    void metadataIsPreservedAndChunkIndexAdded() {
        List<Document> chunks = new RecursiveChunker(120, 20).split(List.of(doc(PROSE)));

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getMetadata())
                    .containsEntry(ChunkMetadata.FILENAME, "a.txt")
                    .containsEntry("custom", "x")
                    .containsEntry(ChunkMetadata.CHUNK_INDEX, i);
        }
    }

    @Test
    void chunkIndexRestartsForEachDocument() {
        List<Document> chunks = new RecursiveChunker(120, 20).split(List.of(doc(PROSE), doc(PROSE)));

        assertThat(chunks.stream().filter(c -> c.getMetadata().get(ChunkMetadata.CHUNK_INDEX).equals(0)).count())
                .isEqualTo(2);
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> new RecursiveChunker(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecursiveChunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String lastWord(String text) {
        return text.substring(text.lastIndexOf(' ') + 1);
    }
}
