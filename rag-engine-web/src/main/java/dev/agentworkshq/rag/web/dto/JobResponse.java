package dev.agentworkshq.rag.web.dto;

import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import java.time.Instant;

public record JobResponse(Long id, String sourceName, String status, Instant startedAt, Instant finishedAt,
                          int added, int updated, int deleted, int skipped, String errorMessage) {

    public static JobResponse from(IngestionJobRecord job) {
        return new JobResponse(job.id(), job.sourceName(), job.status().name(), job.startedAt(), job.finishedAt(),
                job.added(), job.updated(), job.deleted(), job.skipped(), job.errorMessage());
    }
}
