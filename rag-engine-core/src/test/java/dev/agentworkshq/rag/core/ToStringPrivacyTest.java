package dev.agentworkshq.rag.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.answer.Citation;
import dev.agentworkshq.rag.core.answer.RagAnswer;
import dev.agentworkshq.rag.core.answer.RagContext;
import dev.agentworkshq.rag.core.answer.RagSettings;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult;
import dev.agentworkshq.rag.core.ingest.IngestionRequest;
import dev.agentworkshq.rag.core.ingest.SourceItem;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToStringPrivacyTest {

    private static final String MARKER = "SECRET-MARKER-TEXT";
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    private static Citation citation() {
        return new Citation(1, MARKER, MARKER, 2, MARKER, true);
    }

    private static void assertHidden(Object value) {
        assertThat(value.toString()).startsWith(value.getClass().getSimpleName() + "[").doesNotContain(MARKER);
    }

    @Test
    void citationHidesIdFileNameAndSnippet() {
        assertHidden(citation());
        assertThat(citation().toString()).contains("index=1", "page=2", "snippetLength=18", "used=true");
    }

    @Test
    void ragAnswer() {
        RagAnswer answer = new RagAnswer(MARKER, List.of(citation()), true);

        assertHidden(answer);
        assertThat(answer.toString()).contains("textLength=18", "citations=1", "answered=true");
        assertThat(new RagAnswer(null, null, false).toString()).contains("textLength=0", "citations=0");
    }

    @Test
    void ragContext() {
        RagContext context = new RagContext(MARKER, List.of(MARKER, "b"), 3);

        assertHidden(context);
        assertThat(context.toString()).isEqualTo("RagContext[sourceNames=2, topK=3]");
    }

    @Test
    void ragSettings() {
        assertHidden(new RagSettings(1, 2, 0.5, false, true, MARKER, 100, 200));
    }

    @Test
    void documentIngestResult() {
        DocumentIngestResult result =
                new DocumentIngestResult(MARKER, MARKER, DocumentIngestResult.Outcome.ADDED, 4);

        assertHidden(result);
        assertThat(result.toString()).contains("ADDED", "chunkCount=4");
    }

    @Test
    void ingestionRequest() {
        assertHidden(new IngestionRequest(MARKER, MARKER));
    }

    @Test
    void sourceItem() {
        SourceItem item = new SourceItem(MARKER, MARKER, MARKER, NOW, 7, InputStream::nullInputStream);

        assertHidden(item);
        assertThat(item.toString()).isEqualTo("SourceItem[size=7]");
    }

    @Test
    void ingestionJobRecord() {
        IngestionJobRecord job =
                new IngestionJobRecord(5L, MARKER, IngestionStatus.FAILED, NOW, NOW, 1, 2, 3, 4, MARKER);

        assertHidden(job);
        assertThat(job.toString()).contains("id=5", "FAILED", "errorMessageLength=18");
    }

    @Test
    void sourceDocumentRecord() {
        assertHidden(new SourceDocumentRecord(MARKER, MARKER, MARKER, MARKER, 2));
    }
}
