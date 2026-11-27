package dev.agentworkshq.rag.core.store;

public record SourceDocumentRecord(String sourceName, String externalId, String tenantId,
                                   String contentHash, int chunkCount) {

    // marks a document whose chunks may not match any content, it is never equal to a SHA-256 hex string
    public static final String DIRTY_HASH = "!dirty";

    @Override
    public String toString() {
        return "SourceDocumentRecord[chunkCount=" + chunkCount + "]";
    }
}
