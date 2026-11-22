package dev.agentworkshq.rag.jpa.repository;

import dev.agentworkshq.rag.jpa.entity.IngestionJob;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    List<IngestionJob> findBySourceNameOrderByCreatedOnDescIdDesc(String sourceName, Pageable pageable);
}
