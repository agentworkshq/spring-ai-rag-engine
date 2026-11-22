package dev.agentworkshq.rag.core.store;

import java.time.Instant;

public record IngestionJobRecord(Long id, String sourceName, IngestionStatus status, Instant startedAt,
                                 Instant finishedAt, int added, int updated, int deleted, int skipped,
                                 String errorMessage) {
}
