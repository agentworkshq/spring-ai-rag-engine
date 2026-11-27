package dev.agentworkshq.rag.core.answer;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.text.Texts;
import java.util.List;

public record RagContext(String tenantId, List<String> sourceNames, Integer topK) {

    public RagContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ServiceException(ErrorMessages.RAG_E021);
        }
        if (topK != null && topK <= 0) {
            throw new ServiceException(ErrorMessages.RAG_E008);
        }
        if (sourceNames != null && !sourceNames.stream().allMatch(n -> Texts.isPlain(n, Texts.MAX_ID_LENGTH))) {
            throw new ServiceException(ErrorMessages.RAG_E032, Texts.MAX_ID_LENGTH);
        }
        sourceNames = sourceNames == null ? List.of() : List.copyOf(sourceNames);
    }

    public static RagContext of(String tenantId) {
        return new RagContext(tenantId, null, null);
    }

    @Override
    public String toString() {
        return "RagContext[sourceNames=" + sourceNames.size() + ", topK=" + topK + "]";
    }
}
