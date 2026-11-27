package dev.agentworkshq.rag.core.ingest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

class MapSource implements RagSource {

    final Map<String, String> files = new LinkedHashMap<>();

    @Override
    public String name() {
        return "docs";
    }

    @Override
    public Stream<SourceItem> scan() {
        return files.entrySet().stream().map(f -> item(f.getKey(), f.getValue()));
    }

    static SourceItem item(String name, String content) {
        return item(name, content.getBytes(StandardCharsets.UTF_8));
    }

    static SourceItem item(String name, byte[] bytes) {
        return new SourceItem(name, name, "text/plain", Instant.now(), bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }
}
