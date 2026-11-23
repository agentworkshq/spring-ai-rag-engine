package dev.agentworkshq.rag.core.ingest;

public record IngestionRequest(String sourceName, String tenantId) {

    public static final String DEFAULT_TENANT = "default";

    public IngestionRequest {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = DEFAULT_TENANT;
        }
    }
}
