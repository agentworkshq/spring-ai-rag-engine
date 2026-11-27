package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.BaseException;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult.Outcome;
import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);
    // a ratio says little about a handful of documents
    private static final int DELETE_GUARD_MIN_DOCUMENTS = 10;

    private final Supplier<VectorStore> vectorStores;
    private final DocumentLoader loader;
    private final ChunkingStrategy chunker;
    private final DocumentRegistry registry;
    private final IngestionJobStore jobs;
    private final String chunkingSignature;
    private final int batchSize;
    private final boolean allowEmptySource;
    private final double maxDeleteRatio;
    private final long maxFileSize;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public IngestionPipeline(Supplier<VectorStore> vectorStores, DocumentLoader loader, ChunkingStrategy chunker,
                             DocumentRegistry registry, IngestionJobStore jobs, String chunkingSignature,
                             int batchSize, boolean allowEmptySource, double maxDeleteRatio, long maxFileSize) {
        this.vectorStores = vectorStores;
        this.loader = loader;
        this.chunker = chunker;
        this.registry = registry;
        this.jobs = jobs;
        this.chunkingSignature = chunkingSignature;
        this.batchSize = batchSize;
        this.allowEmptySource = allowEmptySource;
        this.maxDeleteRatio = maxDeleteRatio;
        this.maxFileSize = maxFileSize;
    }

    public IngestionResult ingest(RagSource source, String tenantId) {
        IngestionRequest.requireTenant(tenantId);
        VectorStore store = store();
        return locked(source.name(), tenantId, () -> {
            Long jobId = null;
            try {
                jobId = jobs.start(source.name()).id();
                return run(source, tenantId, jobId, store);
            } catch (RuntimeException e) {
                ErrorMessages code = e instanceof BaseException base ? base.getErrorMessage() : ErrorMessages.RAG_E006;
                log.error("Ingestion run failed: {} {}", code.name(), e.getClass().getName());
                log.debug("Ingestion of source {} for tenant {} failed", source.name(), tenantId, e);
                failJob(jobId, shortMessage(e));
                if (e instanceof BaseException) {
                    throw e;
                }
                throw new ServiceException(e, ErrorMessages.RAG_E006);
            } catch (Throwable t) {
                // only so that the job does not stay RUNNING
                failJob(jobId, shortMessage(t));
                throw t;
            }
        });
    }

    public DocumentIngestResult ingestDocument(String sourceName, String tenantId, SourceItem item) {
        IngestionRequest.requireTenant(tenantId);
        VectorStore store = store();
        return locked(sourceName, tenantId, () -> process(store, sourceName, tenantId, item,
                registry.find(sourceName, item.externalId(), tenantId).orElse(null)));
    }

    public void deleteDocument(String sourceName, String tenantId, String externalId) {
        IngestionRequest.requireTenant(tenantId);
        VectorStore store = store();
        locked(sourceName, tenantId, () -> {
            SourceDocumentRecord record = registry.find(sourceName, externalId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.RAG_E002, externalId));
            remove(store, record);
            return null;
        });
    }

    public List<SourceDocumentRecord> listDocuments(String sourceName, String tenantId) {
        IngestionRequest.requireTenant(tenantId);
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
        Set<String> listed = items.stream().map(SourceItem::externalId).collect(Collectors.toSet());
        List<SourceDocumentRecord> gone = known.values().stream()
                .filter(r -> !listed.contains(r.externalId()))
                .toList();
        if (known.size() >= DELETE_GUARD_MIN_DOCUMENTS && (double) gone.size() / known.size() > maxDeleteRatio) {
            throw new ServiceException(ErrorMessages.RAG_E035, gone.size(), known.size());
        }

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int deleted = 0;
        int failed = 0;
        for (SourceItem item : items) {
            try {
                switch (process(store, sourceName, tenantId, item, known.get(item.externalId())).outcome()) {
                    case ADDED -> added++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Ingestion of a document failed: {}", e.getClass().getName());
                log.debug("Ingestion of {} in source {} failed", item.externalId(), sourceName, e);
            }
        }

        for (SourceDocumentRecord record : gone) {
            try {
                remove(store, record);
                deleted++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Removal of a document failed: {}", e.getClass().getName());
                log.debug("Removal of {} in source {} failed", record.externalId(), sourceName, e);
            }
        }

        finishJob(jobId, added, updated, deleted, skipped, failed);
        return new IngestionResult(added, updated, deleted, skipped, failed, jobId);
    }

    private void finishJob(Long jobId, int added, int updated, int deleted, int skipped, int failed) {
        try {
            jobs.finish(jobId, added, updated, deleted, skipped, failed > 0 ? failed + " documents failed" : null);
        } catch (RuntimeException e) {
            // the changes are made, so the caller still gets the result
            log.error("Recording an ingestion result failed: {}", e.getClass().getName());
            log.debug("Recording the result of job {} failed", jobId, e);
            failJob(jobId, "Result not recorded: added=" + added + " updated=" + updated + " deleted=" + deleted
                    + " skipped=" + skipped + " failed=" + failed);
        }
    }

    private void failJob(Long jobId, String message) {
        if (jobId == null) {
            return;
        }
        try {
            jobs.fail(jobId, message);
        } catch (RuntimeException e) {
            log.error("Marking an ingestion job as failed did not work: {}", e.getClass().getName());
            log.debug("Marking job {} as failed did not work", jobId, e);
        }
    }

    private DocumentIngestResult process(VectorStore store, String sourceName, String tenantId, SourceItem item,
                                         SourceDocumentRecord existing) {
        // read once, so the hash and the chunks come from the same bytes
        byte[] bytes = ContentHasher.read(item, maxFileSize);
        String fileHash = ContentHasher.fileHash(bytes);
        String effectiveHash = ContentHasher.effectiveHash(fileHash, chunkingSignature);
        String documentId = documentId(sourceName, item.externalId());
        if (existing != null && existing.contentHash().equals(effectiveHash)) {
            return new DocumentIngestResult(item.externalId(), documentId, Outcome.SKIPPED, existing.chunkCount());
        }

        SourceItem snapshot = new SourceItem(item.externalId(), item.filename(), item.contentType(),
                item.lastModified(), bytes.length, () -> new ByteArrayInputStream(bytes));
        List<Document> chunks = enrich(chunker.split(loader.load(snapshot)), sourceName, tenantId, documentId,
                fileHash);
        int previousCount = existing == null ? 0 : existing.chunkCount();
        if (bytes.length > 0 && chunks.stream().allMatch(c -> c.getText().isBlank())) {
            markDirty(sourceName, tenantId, item.externalId(), previousCount);
            throw new ServiceException(ErrorMessages.RAG_E034);
        }

        // the highest number of chunk ids that can exist until this run has finished
        int upperBound = Math.max(previousCount, chunks.size());
        markDirty(sourceName, tenantId, item.externalId(), upperBound);
        for (int i = 0; i < chunks.size(); i += batchSize) {
            store.add(chunks.subList(i, Math.min(chunks.size(), i + batchSize)));
        }
        if (upperBound > chunks.size()) {
            store.delete(IntStream.range(chunks.size(), upperBound)
                    .mapToObj(i -> chunkId(documentId, tenantId, i))
                    .toList());
        }
        registry.save(new SourceDocumentRecord(sourceName, item.externalId(), tenantId, effectiveHash,
                chunks.size()));
        return new DocumentIngestResult(item.externalId(), documentId,
                existing == null ? Outcome.ADDED : Outcome.UPDATED, chunks.size());
    }

    private void remove(VectorStore store, SourceDocumentRecord record) {
        markDirty(record.sourceName(), record.tenantId(), record.externalId(), record.chunkCount());
        deleteChunks(store, documentId(record.sourceName(), record.externalId()), record.sourceName(),
                record.tenantId());
        registry.delete(record.sourceName(), record.externalId(), record.tenantId());
    }

    private void markDirty(String sourceName, String tenantId, String externalId, int chunkCount) {
        registry.save(new SourceDocumentRecord(sourceName, externalId, tenantId, SourceDocumentRecord.DIRTY_HASH,
                chunkCount));
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
            result.add(Document.builder().id(chunkId(documentId, tenantId, i)).text(chunk.getText())
                    .metadata(metadata).build());
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

    // the tenant is part of the id so two tenants never overwrite or delete each other's chunks
    private static String chunkId(String documentId, String tenantId, int index) {
        return uuid(documentId + "\n" + tenantId + "\n" + index);
    }

    private static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String shortMessage(Throwable e) {
        if (e instanceof BaseException base) {
            return base.getClientMessage();
        }
        return "Ingestion failed: " + e.getClass().getSimpleName();
    }
}
