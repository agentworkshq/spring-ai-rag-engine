package dev.agentworkshq.rag.core.answer;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.util.List;

public record RagContext(String tenantId, List<String> sourceNames, Integer topK) {

    public RagContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ServiceException(ErrorMessages.RAG_E021);
        }
        if (topK != null && topK <= 0) {
            throw new ServiceException(ErrorMessages.RAG_E008);
        }
        sourceNames = sourceNames == null ? List.of() : List.copyOf(sourceNames);
    }

    public static RagContext of(String tenantId) {
        return new RagContext(tenantId, null, null);
    }
}
