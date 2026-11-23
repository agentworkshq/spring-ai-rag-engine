package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.BaseException;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult.Outcome;
import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final Supplier<VectorStore> vectorStores;
    private final DocumentLoader loader;
    private final ChunkingStrategy chunker;
    private final DocumentRegistry registry;
    private final IngestionJobStore jobs;
    private final String chunkingSignature;
    private final int batchSize;
    private final boolean allowEmptySource;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public IngestionPipeline(Supplier<VectorStore> vectorStores, DocumentLoader loader, ChunkingStrategy chunker,
                             DocumentRegistry registry, IngestionJobStore jobs, String chunkingSignature,
                             int batchSize, boolean allowEmptySource) {
        this.vectorStores = vectorStores;
        this.loader = loader;
        this.chunker = chunker;
        this.registry = registry;
        this.jobs = jobs;
        this.chunkingSignature = chunkingSignature;
        this.batchSize = batchSize;
        this.allowEmptySource = allowEmptySource;
    }

    public IngestionResult ingest(RagSource source, String tenantId) {
        VectorStore store = store();
        return locked(source.name(), tenantId, () -> {
            Long jobId = null;
            try {
                jobId = jobs.start(source.name()).id();
                return run(source, tenantId, jobId, store);
            } catch (RuntimeException e) {
                log.error("Ingestion of source {} for tenant {} failed", source.name(), tenantId, e);
                if (jobId != null) {
                    jobs.fail(jobId, shortMessage(e));
                }
                if (e instanceof BaseException) {
                    throw e;
                }
                throw new ServiceException(e, ErrorMessages.RAG_E006);
            }
        });
    }

    public DocumentIngestResult ingestDocument(String sourceName, String tenantId, SourceItem item) {
        VectorStore store = store();
        return locked(sourceName, tenantId, () -> process(store, sourceName, tenantId, item,
                registry.find(sourceName, item.externalId(), tenantId).orElse(null)));
    }

    public void deleteDocument(String sourceName, String tenantId, String externalId) {
        VectorStore store = store();
        locked(sourceName, tenantId, () -> {
            if (registry.find(sourceName, externalId, tenantId).isEmpty()) {
                throw new ResourceNotFoundException(ErrorMessages.RAG_E002, externalId);
            }
            deleteChunks(store, documentId(sourceName, externalId), sourceName, tenantId);
            registry.delete(sourceName, externalId, tenantId);
            return null;
        });
    }

    public List<SourceDocumentRecord> listDocuments(String sourceName, String tenantId) {
        return registry.findBySource(sourceName, tenantId);
    }

    private VectorStore store() {
        VectorStore store = vectorStores.get();
        if (store == null) {
            throw new ServiceException(ErrorMessages.RAG_E019);
        }
        return store;
    }

    private <T> T locked(String sourceName, String tenantId, Supplier<T> action) {
        String lockKey = sourceName + "\u0000" + tenantId;
        if (!running.add(lockKey)) {
            throw new ServiceException(ErrorMessages.RAG_E016, sourceName, tenantId);
        }
        try {
            return action.get();
        } finally {
            running.remove(lockKey);
        }
    }

    private IngestionResult run(RagSource source, String tenantId, Long jobId, VectorStore store) {
        String sourceName = source.name();
        List<SourceItem> items;
        try (Stream<SourceItem> scan = source.scan()) {
            items = scan.toList();
        }
        Map<String, SourceDocumentRecord> known = new HashMap<>();
        registry.findBySource(sourceName, tenantId).forEach(r -> known.put(r.externalId(), r));
        if (items.isEmpty() && !known.isEmpty() && !allowEmptySource) {
            throw new ServiceException(ErrorMessages.RAG_E017, sourceName);
        }

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int deleted = 0;
        int failed = 0;
        Set<String> seen = new HashSet<>();
        for (SourceItem item : items) {
            seen.add(item.externalId());
            try {
                switch (process(store, sourceName, tenantId, item, known.get(item.externalId())).outcome()) {
                    case ADDED -> added++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Ingestion of {} in source {} failed: {}", item.externalId(), sourceName, e.toString());
            }
        }

        for (SourceDocumentRecord record : known.values()) {
            if (seen.contains(record.externalId())) {
                continue;
            }
            try {
                deleteChunks(store, documentId(sourceName, record.externalId()), sourceName, tenantId);
                registry.delete(sourceName, record.externalId(), tenantId);
                deleted++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Removal of {} in source {} failed: {}", record.externalId(), sourceName, e.toString());
            }
        }

        String message = failed > 0 ? failed + " documents failed" : null;
        jobs.finish(jobId, added, updated, deleted, skipped, message);
        return new IngestionResult(added, updated, deleted, skipped, failed, jobId);
    }

    private DocumentIngestResult process(VectorStore store, String sourceName, String tenantId, SourceItem item,
                                         SourceDocumentRecord existing) {
        String fileHash = ContentHasher.fileHash(item);
        String effectiveHash = ContentHasher.effectiveHash(fileHash, chunkingSignature);
        String documentId = documentId(sourceName, item.externalId());
        if (existing != null && existing.contentHash().equals(effectiveHash)) {
            return new DocumentIngestResult(item.externalId(), documentId, Outcome.SKIPPED, existing.chunkCount());
        }

        List<Document> chunks = enrich(chunker.split(loader.load(item)), sourceName, tenantId, documentId, fileHash);
        try {
            // always cleared first so a partly failed earlier run can never leave duplicates behind
            deleteChunks(store, documentId, sourceName, tenantId);
            for (int i = 0; i < chunks.size(); i += batchSize) {
                store.add(chunks.subList(i, Math.min(chunks.size(), i + batchSize)));
            }
            registry.save(new SourceDocumentRecord(sourceName, item.externalId(), tenantId, effectiveHash,
                    chunks.size()));
        } catch (RuntimeException e) {
            try {
                deleteChunks(store, documentId, sourceName, tenantId);
            } catch (RuntimeException cleanup) {
                log.warn("Cleanup of {} in source {} failed: {}", item.externalId(), sourceName, cleanup.toString());
            }
            registry.delete(sourceName, item.externalId(), tenantId);
            throw e;
        }
        return new DocumentIngestResult(item.externalId(), documentId,
                existing == null ? Outcome.ADDED : Outcome.UPDATED, chunks.size());
    }

    private List<Document> enrich(List<Document> chunks, String sourceName, String tenantId, String documentId,
                                  String fileHash) {
        List<Document> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put(ChunkMetadata.SOURCE_NAME, sourceName);
            metadata.put(ChunkMetadata.DOCUMENT_ID, documentId);
            metadata.put(ChunkMetadata.CHUNK_INDEX, i);
            metadata.put(ChunkMetadata.TENANT_ID, tenantId);
            metadata.put(ChunkMetadata.CONTENT_HASH, fileHash);
            // the tenant is part of the id so two tenants never overwrite each other's chunks
            String id = uuid(documentId + "\n" + tenantId + "\n" + i);
            result.add(Document.builder().id(id).text(chunk.getText()).metadata(metadata).build());
        }
        return result;
    }

    private void deleteChunks(VectorStore store, String documentId, String sourceName, String tenantId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        store.delete(b.and(
                b.and(b.eq(ChunkMetadata.DOCUMENT_ID, documentId), b.eq(ChunkMetadata.SOURCE_NAME, sourceName)),
                b.eq(ChunkMetadata.TENANT_ID, tenantId)).build());
    }

    private static String documentId(String sourceName, String externalId) {
        return uuid(sourceName + "\n" + externalId);
    }

    private static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String shortMessage(RuntimeException e) {
        if (e instanceof BaseException) {
            return e.getMessage();
        }
        return "Ingestion failed: " + e.getClass().getSimpleName();
    }
}
