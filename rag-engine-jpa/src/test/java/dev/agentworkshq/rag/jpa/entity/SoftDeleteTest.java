package dev.agentworkshq.rag.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.store.IngestionStatus;
import dev.agentworkshq.rag.jpa.repository.IngestionJobRepository;
import dev.agentworkshq.rag.jpa.testapp.TestApplication;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class SoftDeleteTest {

    @Autowired
    private IngestionJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    private IngestionJob newJob(String sourceName) {
        IngestionJob job = new IngestionJob();
        job.setSourceName(sourceName);
        job.setStatus(IngestionStatus.RUNNING);
        job.setStartedAt(Instant.now());
        return job;
    }

    @Test
    void softDeletedJobsAreHiddenFromQueries() {
        IngestionJob kept = repository.save(newJob("docs"));
        IngestionJob removed = repository.save(newJob("docs"));
        removed.setDeleted(true);
        repository.saveAndFlush(removed);
        entityManager.clear();

        assertThat(repository.findAll()).extracting(IngestionJob::getId).containsExactly(kept.getId());
        assertThat(repository.findById(removed.getId())).isEmpty();
        assertThat(repository.findBySourceNameOrderByCreatedOnDescIdDesc("docs", PageRequest.of(0, 10)))
                .extracting(IngestionJob::getId).containsExactly(kept.getId());
    }
}
