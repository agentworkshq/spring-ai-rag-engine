package dev.agentworkshq.rag.core.store;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

public class InMemoryIngestionJobStore implements IngestionJobStore {

    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, IngestionJobRecord> jobs = new ConcurrentHashMap<>();

    @Override
    public IngestionJobRecord start(String sourceName) {
        Long id = ids.incrementAndGet();
        IngestionJobRecord job = new IngestionJobRecord(id, sourceName, IngestionStatus.RUNNING,
                Instant.now(), null, 0, 0, 0, 0, null);
        jobs.put(id, job);
        return job;
    }

    @Override
    public IngestionJobRecord finish(Long id, int added, int updated, int deleted, int skipped,
                                   String errorMessage) {
        return update(id, j -> new IngestionJobRecord(j.id(), j.sourceName(), IngestionStatus.DONE,
                j.startedAt(), Instant.now(), added, updated, deleted, skipped, errorMessage));
    }

    @Override
    public IngestionJobRecord fail(Long id, String errorMessage) {
        return update(id, j -> new IngestionJobRecord(j.id(), j.sourceName(), IngestionStatus.FAILED,
                j.startedAt(), Instant.now(), j.added(), j.updated(), j.deleted(), j.skipped(), errorMessage));
    }

    @Override
    public Optional<IngestionJobRecord> findById(Long id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<IngestionJobRecord> findRecent(String sourceName, int limit) {
        return jobs.values().stream()
                .filter(j -> j.sourceName().equals(sourceName))
                .sorted(Comparator.comparing(IngestionJobRecord::startedAt).thenComparing(IngestionJobRecord::id)
                        .reversed())
                .limit(limit)
                .toList();
    }

    private IngestionJobRecord update(Long id, UnaryOperator<IngestionJobRecord> change) {
        IngestionJobRecord updated = jobs.computeIfPresent(id, (key, job) -> change.apply(job));
        if (updated == null) {
            throw new ResourceNotFoundException(ErrorMessages.RAG_E001);
        }
        return updated;
    }
}
