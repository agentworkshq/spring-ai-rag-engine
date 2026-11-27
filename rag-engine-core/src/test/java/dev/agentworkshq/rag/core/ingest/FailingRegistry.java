package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

class FailingRegistry implements DocumentRegistry {

    private final InMemoryDocumentRegistry delegate = new InMemoryDocumentRegistry();

    Predicate<SourceDocumentRecord> failSave = record -> false;
    boolean failDelete;

    @Override
    public Optional<SourceDocumentRecord> find(String sourceName, String externalId, String tenantId) {
        return delegate.find(sourceName, externalId, tenantId);
    }

    @Override
    public List<SourceDocumentRecord> findBySource(String sourceName, String tenantId) {
        return delegate.findBySource(sourceName, tenantId);
    }

    @Override
    public void save(SourceDocumentRecord record) {
        if (failSave.test(record)) {
            throw new IllegalStateException("registry failed");
        }
        delegate.save(record);
    }

    @Override
    public void delete(String sourceName, String externalId, String tenantId) {
        if (failDelete) {
            throw new IllegalStateException("registry failed");
        }
        delegate.delete(sourceName, externalId, tenantId);
    }

    void recover() {
        failSave = record -> false;
        failDelete = false;
    }
}
