package dev.agentworkshq.rag.web.dto;

import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import java.time.Instant;
import java.util.Arrays;

public record JobResponse(Long id, String sourceName, String status, Instant startedAt, Instant finishedAt,
                          int added, int updated, int deleted, int skipped, String errorMessage) {

    public static JobResponse from(IngestionJobRecord job) {
        return new JobResponse(job.id(), job.sourceName(), job.status().name(), job.startedAt(), job.finishedAt(),
                job.added(), job.updated(), job.deleted(), job.skipped(), job.errorMessage());
    }

    @Override
    public String toString() {
        // the component is a plain string, so only a real status name is printed
        boolean known = Arrays.stream(IngestionStatus.values()).anyMatch(s -> s.name().equals(status));
        return "JobResponse[id=" + id + ", status=" + (known ? status : null) + ", added=" + added + ", updated="
                + updated + ", deleted=" + deleted + ", skipped=" + skipped + ", errorMessageLength="
                + (errorMessage == null ? 0 : errorMessage.length()) + "]";
    }
}
