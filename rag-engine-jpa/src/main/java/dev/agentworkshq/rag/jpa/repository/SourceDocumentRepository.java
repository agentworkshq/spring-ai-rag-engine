package dev.agentworkshq.rag.jpa.repository;

import dev.agentworkshq.rag.jpa.entity.SourceDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {

    Optional<SourceDocument> findBySourceNameAndExternalIdAndTenantId(String sourceName, String externalId,
                                                                      String tenantId);

    List<SourceDocument> findBySourceNameAndTenantIdOrderByExternalId(String sourceName, String tenantId);
}
