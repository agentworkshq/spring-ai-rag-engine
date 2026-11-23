package dev.agentworkshq.rag.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RagEnginePropertiesTest {

    private static RagEngineProperties bind(Map<String, String> values) {
        Map<String, String> all = new java.util.HashMap<>(values);
        all.putIfAbsent("rag-engine.enabled", "true");
        return new Binder(new MapConfigurationPropertySource(all)).bind("rag-engine", RagEngineProperties.class).get();
    }

    @Test
    void defaultsApply() {
        RagEngineProperties props = bind(Map.of("rag-engine.enabled", "true"));

        assertThat(props.chunking().strategy()).isEqualTo(RagEngineProperties.Chunking.Strategy.RECURSIVE);
        assertThat(props.chunking().chunkSize()).isEqualTo(800);
        assertThat(props.chunking().overlap()).isEqualTo(100);
        assertThat(props.limits().maxFileSize().toBytes()).isEqualTo(20L * 1024 * 1024);
        assertThat(props.limits().maxPages()).isEqualTo(500);
        assertThat(props.ingestion().batchSize()).isEqualTo(50);
        assertThat(props.sources()).isEmpty();
    }

    @Test
    void sourcesBind() {
        RagEngineProperties props = bind(Map.of(
                "rag-engine.sources[0].name", "docs",
                "rag-engine.sources[0].type", "filesystem",
                "rag-engine.sources[0].path", "/data/docs",
                "rag-engine.sources[0].include[0]", "**/*.md"));

        assertThat(props.sources()).hasSize(1);
        assertThat(props.sources().get(0).name()).isEqualTo("docs");
        assertThat(props.sources().get(0).type()).isEqualTo(RagEngineProperties.Source.Type.FILESYSTEM);
        assertThat(props.sources().get(0).include()).containsExactly("**/*.md");
    }

    @Test
    void overlapNotSmallerThanChunkSizeFails() {
        assertThatThrownBy(() -> new RagEngineProperties.Chunking(RagEngineProperties.Chunking.Strategy.RECURSIVE, 100, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
        assertThatThrownBy(() -> bind(Map.of(
                "rag-engine.chunking.chunk-size", "100",
                "rag-engine.chunking.overlap", "150")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveSizesFail() {
        assertThatThrownBy(() -> new RagEngineProperties.Chunking(RagEngineProperties.Chunking.Strategy.FIXED, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagEngineProperties.Limits(org.springframework.util.unit.DataSize.ofBytes(0), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagEngineProperties.Limits(org.springframework.util.unit.DataSize.ofMegabytes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagEngineProperties.Ingestion(0, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
