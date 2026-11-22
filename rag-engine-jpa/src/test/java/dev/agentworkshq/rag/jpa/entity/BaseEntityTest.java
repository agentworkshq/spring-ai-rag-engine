package dev.agentworkshq.rag.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import dev.agentworkshq.rag.jpa.testapp.TestApplication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestApplication.class)
@Transactional
class BaseEntityTest {

    @Autowired
    private SourceDocumentRepository repository;

    @Autowired
    private EntityManager entityManager;

    private SourceDocument newDocument(String externalId) {
        SourceDocument document = new SourceDocument();
        document.setSourceName("docs");
        document.setExternalId(externalId);
        document.setContentHash("h1");
        return document;
    }

    @Test
    void timestampsAreSetOnSave() {
        SourceDocument saved = repository.saveAndFlush(newDocument("a.md"));

        assertThat(saved.getCreatedOn()).isNotNull();
        assertThat(saved.getUpdatedOn()).isEqualTo(saved.getCreatedOn());
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getTenantId()).isEqualTo("default");
    }

    @Test
    void updatedOnChangesOnUpdateButCreatedOnDoesNot() throws InterruptedException {
        SourceDocument saved = repository.saveAndFlush(newDocument("a.md"));
        var createdOn = saved.getCreatedOn();
        var firstUpdate = saved.getUpdatedOn();
        Thread.sleep(5);

        saved.setContentHash("h2");
        repository.saveAndFlush(saved);

        assertThat(saved.getUpdatedOn()).isAfter(firstUpdate);
        assertThat(saved.getCreatedOn()).isEqualTo(createdOn);
    }

    @Test
    void equalsAndHashCodeWorkBeforeAndAfterSave() {
        SourceDocument first = newDocument("a.md");
        SourceDocument second = newDocument("b.md");
        int hashBefore = first.hashCode();

        assertThat(first.isNew()).isTrue();
        assertThat(first).isEqualTo(first).isNotEqualTo(second);

        repository.saveAndFlush(first);
        repository.saveAndFlush(second);
        entityManager.clear();

        SourceDocument reloaded = repository.findById(first.getId()).orElseThrow();
        assertThat(first.isNew()).isFalse();
        assertThat(first.hashCode()).isEqualTo(hashBefore);
        assertThat(reloaded).isEqualTo(first).hasSameHashCodeAs(first).isNotEqualTo(second);
    }
}
