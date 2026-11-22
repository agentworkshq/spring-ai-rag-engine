package dev.agentworkshq.rag.core.store;

import java.util.List;
import java.util.Optional;

public interface DocumentRegistry {

    Optional<SourceDocumentRecord> find(String sourceName, String externalId, String tenantId);

    List<SourceDocumentRecord> findBySource(String sourceName, String tenantId);

    void save(SourceDocumentRecord record);

    void delete(String sourceName, String externalId, String tenantId);
}
