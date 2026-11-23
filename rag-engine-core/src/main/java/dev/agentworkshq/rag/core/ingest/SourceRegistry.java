package dev.agentworkshq.rag.core.ingest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SourceRegistry {

    private final Map<String, RagSource> sources = new LinkedHashMap<>();

    public SourceRegistry(List<RagSource> sources) {
        for (RagSource source : sources) {
            if (this.sources.putIfAbsent(source.name(), source) != null) {
                throw new IllegalArgumentException("Duplicate source name: " + source.name());
            }
        }
    }

    public Optional<RagSource> find(String name) {
        return Optional.ofNullable(sources.get(name));
    }
}
