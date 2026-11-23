package dev.agentworkshq.rag.core.ingest;

public record IngestionResult(int added, int updated, int deleted, int skipped, int failed, Long jobId) {
}
