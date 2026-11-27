package dev.agentworkshq.rag.jpa.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.jpa.entity.SourceDocument;
import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import dev.agentworkshq.rag.jpa.testapp.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApplication.class)
class JpaDocumentRegistryTest {

    @Autowired
    private DocumentRegistry registry;

    @Autowired
    private SourceDocumentRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void jpaRegistryReplacesInMemoryDefault() {
        assertThat(registry).isInstanceOf(JpaDocumentRegistry.class);
    }

    @Test
    void savesAndFinds() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));

        assertThat(registry.find("docs", "a.md", "default"))
                .contains(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));
        assertThat(registry.findBySource("docs", "default")).hasSize(1);
        assertThat(registry.find("docs", "a.md", "other")).isEmpty();
    }

    @Test
    void savingAgainReplacesHash() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h2", 5));

        assertThat(registry.find("docs", "a.md", "default")).hasValueSatisfying(d -> {
            assertThat(d.contentHash()).isEqualTo("h2");
            assertThat(d.chunkCount()).isEqualTo(5);
        });
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savingAcrossTransactionsUpdatesOnlyTheRowWithTheSameKey() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));
        registry.save(new SourceDocumentRecord("docs", "a.md", "other", "h1", 3));
        registry.save(new SourceDocumentRecord("web", "a.md", "default", "h1", 3));
        Long id = row("docs", "default").getId();

        registry.save(new SourceDocumentRecord("docs", "a.md", "default",
                SourceDocumentRecord.DIRTY_HASH, 7));

        assertThat(repository.count()).isEqualTo(3);
        assertThat(row("docs", "default").getId()).isEqualTo(id);
        assertThat(registry.find("docs", "a.md", "default")).contains(new SourceDocumentRecord("docs", "a.md",
                "default", SourceDocumentRecord.DIRTY_HASH, 7));
        assertThat(row("docs", "other").getContentHash()).isEqualTo("h1");
        assertThat(row("web", "default").getContentHash()).isEqualTo("h1");
    }

    private SourceDocument row(String sourceName, String tenantId) {
        return repository.findBySourceNameAndExternalIdAndTenantId(sourceName, "a.md", tenantId).orElseThrow();
    }

    @Test
    void deleteRemovesRowAndSameDocumentCanBeSavedAgain() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));
        registry.delete("docs", "a.md", "default");

        assertThat(registry.find("docs", "a.md", "default")).isEmpty();
        assertThat(repository.count()).isZero();

        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h3", 2));

        assertThat(registry.find("docs", "a.md", "default")).isPresent();
    }
}
