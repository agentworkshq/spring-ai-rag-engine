package dev.agentworkshq.rag.core.store;

import java.time.Instant;

public record IngestionJobRecord(Long id, String sourceName, IngestionStatus status, Instant startedAt,
                                 Instant finishedAt, int added, int updated, int deleted, int skipped,
                                 String errorMessage) {

    @Override
    public String toString() {
        return "IngestionJobRecord[id=" + id + ", status=" + status + ", added=" + added + ", updated=" + updated
                + ", deleted=" + deleted + ", skipped=" + skipped + ", errorMessageLength="
                + (errorMessage == null ? 0 : errorMessage.length()) + "]";
    }
}
