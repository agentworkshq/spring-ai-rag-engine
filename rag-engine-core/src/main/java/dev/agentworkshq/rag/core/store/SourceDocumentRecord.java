package dev.agentworkshq.rag.core.store;

public record SourceDocumentRecord(String sourceName, String externalId, String tenantId,
                                   String contentHash, int chunkCount) {

    @Override
    public String toString() {
        return "SourceDocumentRecord[chunkCount=" + chunkCount + "]";
    }
}
