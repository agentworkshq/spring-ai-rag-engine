package dev.agentworkshq.rag.core.store;

import java.util.List;
import java.util.Optional;

public interface IngestionJobStore {

    IngestionJobRecord start(String sourceName);

    IngestionJobRecord finish(Long id, int added, int updated, int deleted, int skipped, String errorMessage);

    IngestionJobRecord fail(Long id, String errorMessage);

    Optional<IngestionJobRecord> findById(Long id);

    List<IngestionJobRecord> findRecent(String sourceName, int limit);
}
