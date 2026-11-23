package dev.agentworkshq.rag.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "rag-engine")
public record RagEngineProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Chunking chunking,
        @DefaultValue Limits limits,
        @DefaultValue Ingestion ingestion,
        List<Source> sources) {

    public RagEngineProperties {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Chunking(
            @DefaultValue("RECURSIVE") Strategy strategy,
            @DefaultValue("800") int chunkSize,
            @DefaultValue("100") int overlap) {

        public Chunking {
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("rag-engine.chunking.chunk-size must be positive");
            }
            if (overlap < 0) {
                throw new IllegalArgumentException("rag-engine.chunking.overlap must not be negative");
            }
            if (overlap >= chunkSize) {
                throw new IllegalArgumentException("rag-engine.chunking.overlap must be smaller than chunk-size");
            }
        }

        public enum Strategy {
            FIXED, RECURSIVE
        }
    }

    public record Limits(
            @DefaultValue("20MB") DataSize maxFileSize,
            @DefaultValue("500") int maxPages) {

        public Limits {
            if (maxFileSize.toBytes() <= 0) {
                throw new IllegalArgumentException("rag-engine.limits.max-file-size must be positive");
            }
            if (maxPages <= 0) {
                throw new IllegalArgumentException("rag-engine.limits.max-pages must be positive");
            }
        }
    }

    public record Ingestion(@DefaultValue("50") int batchSize) {

        public Ingestion {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("rag-engine.ingestion.batch-size must be positive");
            }
        }
    }

    public record Source(
            String name,
            @DefaultValue("FILESYSTEM") Type type,
            String path,
            List<String> include) {

        public Source {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("rag-engine.sources[].name must not be blank");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("rag-engine.sources[].path must not be blank");
            }
            include = include == null ? List.of() : List.copyOf(include);
        }

        public enum Type {
            FILESYSTEM
        }
    }
}
