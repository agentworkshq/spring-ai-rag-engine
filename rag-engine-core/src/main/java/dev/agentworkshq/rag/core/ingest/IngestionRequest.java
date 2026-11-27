package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.text.Texts;

public record IngestionRequest(String sourceName, String tenantId) {

    public IngestionRequest {
        requireTenant(tenantId);
    }

    static void requireTenant(String tenantId) {
        if (!Texts.isPlain(tenantId, Texts.MAX_ID_LENGTH)) {
            throw new ServiceException(ErrorMessages.RAG_E031, Texts.MAX_ID_LENGTH);
        }
    }

    @Override
    public String toString() {
        return "IngestionRequest[]";
    }
}
