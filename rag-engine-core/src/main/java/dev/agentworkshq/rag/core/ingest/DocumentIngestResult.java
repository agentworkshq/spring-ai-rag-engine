package dev.agentworkshq.rag.core.ingest;

public record DocumentIngestResult(String externalId, String documentId, Outcome outcome, int chunkCount) {

    public enum Outcome {
        ADDED, UPDATED, SKIPPED
    }
}
