package dev.agentworkshq.rag.core.store;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDocumentRegistry implements DocumentRegistry {

    private record Key(String sourceName, String externalId, String tenantId) {
    }

    private final Map<Key, SourceDocumentRecord> documents = new ConcurrentHashMap<>();

    @Override
    public Optional<SourceDocumentRecord> find(String sourceName, String externalId, String tenantId) {
        return Optional.ofNullable(documents.get(new Key(sourceName, externalId, tenantId)));
    }

    @Override
    public List<SourceDocumentRecord> findBySource(String sourceName, String tenantId) {
        return documents.values().stream()
                .filter(d -> d.sourceName().equals(sourceName) && d.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(SourceDocumentRecord::externalId))
                .toList();
    }

    @Override
    public void save(SourceDocumentRecord record) {
        documents.put(new Key(record.sourceName(), record.externalId(), record.tenantId()), record);
    }

    @Override
    public void delete(String sourceName, String externalId, String tenantId) {
        documents.remove(new Key(sourceName, externalId, tenantId));
    }
}
