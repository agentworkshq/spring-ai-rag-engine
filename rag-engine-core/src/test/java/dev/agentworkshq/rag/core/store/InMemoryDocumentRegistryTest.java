package dev.agentworkshq.rag.core.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryDocumentRegistryTest {

    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();

    @Test
    void savesFindsAndReplaces() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 3));
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h2", 5));

        assertThat(registry.find("docs", "a.md", "default")).hasValueSatisfying(d -> {
            assertThat(d.contentHash()).isEqualTo("h2");
            assertThat(d.chunkCount()).isEqualTo(5);
        });
    }

    @Test
    void listsBySourceAndTenantOnly() {
        registry.save(new SourceDocumentRecord("docs", "b.md", "default", "h", 1));
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h", 1));
        registry.save(new SourceDocumentRecord("docs", "c.md", "other", "h", 1));
        registry.save(new SourceDocumentRecord("wiki", "d.md", "default", "h", 1));

        assertThat(registry.findBySource("docs", "default"))
                .extracting(SourceDocumentRecord::externalId)
                .containsExactly("a.md", "b.md");
    }

    @Test
    void deleteRemovesAndAllowsSavingAgain() {
        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h1", 1));
        registry.delete("docs", "a.md", "default");
        assertThat(registry.find("docs", "a.md", "default")).isEmpty();

        registry.save(new SourceDocumentRecord("docs", "a.md", "default", "h2", 1));
        assertThat(registry.find("docs", "a.md", "default")).isPresent();
    }
}
