package dev.agentworkshq.rag.jpa.store;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.store.IngestionJobRecord;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.jpa.entity.IngestionJob;
import dev.agentworkshq.rag.jpa.repository.IngestionJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// writes commit on their own, so a job stays recorded when a host transaction rolls back
public class JpaIngestionJobStore implements IngestionJobStore {

    private static final int MAX_ERROR_LENGTH = 500;

    private final IngestionJobRepository repository;

    public JpaIngestionJobStore(IngestionJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionJobRecord start(String sourceName) {
        IngestionJob job = new IngestionJob();
        job.setSourceName(sourceName);
        job.setStatus(IngestionStatus.RUNNING);
        job.setStartedAt(Instant.now());
        return toRecord(repository.save(job));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionJobRecord finish(Long id, int added, int updated, int deleted, int skipped,
                                   String errorMessage) {
        IngestionJob job = load(id);
        job.setErrorMessage(truncate(errorMessage));
        job.setStatus(IngestionStatus.DONE);
        job.setFinishedAt(Instant.now());
        job.setAddedCount(added);
        job.setUpdatedCount(updated);
        job.setDeletedCount(deleted);
        job.setSkippedCount(skipped);
        return toRecord(repository.save(job));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestionJobRecord fail(Long id, String errorMessage) {
        IngestionJob job = load(id);
        job.setStatus(IngestionStatus.FAILED);
        job.setFinishedAt(Instant.now());
        job.setErrorMessage(truncate(errorMessage));
        return toRecord(repository.save(job));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngestionJobRecord> findById(Long id) {
        return repository.findById(id).map(JpaIngestionJobStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionJobRecord> findRecent(String sourceName, int limit) {
        return repository.findBySourceNameOrderByCreatedOnDescIdDesc(sourceName, PageRequest.of(0, limit)).stream()
                .map(JpaIngestionJobStore::toRecord)
                .toList();
    }

    private IngestionJob load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.RAG_E001));
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private static IngestionJobRecord toRecord(IngestionJob job) {
        return new IngestionJobRecord(job.getId(), job.getSourceName(), job.getStatus(), job.getStartedAt(),
                job.getFinishedAt(), job.getAddedCount(), job.getUpdatedCount(), job.getDeletedCount(),
                job.getSkippedCount(), job.getErrorMessage());
    }
}
