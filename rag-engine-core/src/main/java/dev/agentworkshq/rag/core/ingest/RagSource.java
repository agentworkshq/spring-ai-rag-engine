package dev.agentworkshq.rag.core.ingest;

import java.util.stream.Stream;

public interface RagSource {

    String name();

    Stream<SourceItem> scan();
}
