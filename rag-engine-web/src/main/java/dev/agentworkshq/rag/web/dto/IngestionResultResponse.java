package dev.agentworkshq.rag.web.dto;

import dev.agentworkshq.rag.core.ingest.IngestionResult;

public record IngestionResultResponse(int added, int updated, int deleted, int skipped, int failed, Long jobId) {

    public static IngestionResultResponse from(IngestionResult result) {
        return new IngestionResultResponse(result.added(), result.updated(), result.deleted(), result.skipped(),
                result.failed(), result.jobId());
    }
}
