package dev.agentworkshq.rag.jpa.store;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.jpa.entity.SourceDocument;
import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JpaDocumentRegistry implements DocumentRegistry {

    private final SourceDocumentRepository repository;

    public JpaDocumentRegistry(SourceDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SourceDocumentRecord> find(String sourceName, String externalId, String tenantId) {
        return repository.findBySourceNameAndExternalIdAndTenantId(sourceName, externalId, tenantId)
                .map(JpaDocumentRegistry::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceDocumentRecord> findBySource(String sourceName, String tenantId) {
        return repository.findBySourceNameAndTenantIdOrderByExternalId(sourceName, tenantId).stream()
                .map(JpaDocumentRegistry::toRecord)
                .toList();
    }

    @Override
    public void save(SourceDocumentRecord record) {
        SourceDocument entity = repository
                .findBySourceNameAndExternalIdAndTenantId(record.sourceName(), record.externalId(), record.tenantId())
                .orElseGet(SourceDocument::new);
        entity.setSourceName(record.sourceName());
        entity.setExternalId(record.externalId());
        entity.setTenantId(record.tenantId());
        entity.setContentHash(record.contentHash());
        entity.setChunkCount(record.chunkCount());
        repository.save(entity);
    }

    // Hard delete: a soft-deleted row would keep holding the unique key and block re-ingesting the document.
    @Override
    public void delete(String sourceName, String externalId, String tenantId) {
        repository.findBySourceNameAndExternalIdAndTenantId(sourceName, externalId, tenantId)
                .ifPresent(repository::delete);
    }

    private static SourceDocumentRecord toRecord(SourceDocument entity) {
        return new SourceDocumentRecord(entity.getSourceName(), entity.getExternalId(), entity.getTenantId(),
                entity.getContentHash(), entity.getChunkCount());
    }
}
