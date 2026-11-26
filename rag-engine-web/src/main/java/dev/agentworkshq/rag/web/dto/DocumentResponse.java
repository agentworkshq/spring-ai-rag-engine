package dev.agentworkshq.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.util.Arrays;

public record DocumentResponse(
        String externalId,
        String filename,
        @JsonInclude(JsonInclude.Include.NON_NULL) String outcome,
        int chunkCount) {

    public static DocumentResponse from(DocumentIngestResult result) {
        return new DocumentResponse(result.externalId(), result.externalId(), result.outcome().name(),
                result.chunkCount());
    }

    public static DocumentResponse from(SourceDocumentRecord record) {
        return new DocumentResponse(record.externalId(), record.externalId(), null, record.chunkCount());
    }

    @Override
    public String toString() {
        // the component is a plain string, so only a real outcome name is printed
        boolean known = Arrays.stream(DocumentIngestResult.Outcome.values()).anyMatch(o -> o.name().equals(outcome));
        return "DocumentResponse[outcome=" + (known ? outcome : null) + ", chunkCount=" + chunkCount + "]";
    }
}
