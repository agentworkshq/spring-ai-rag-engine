package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;

public class IngestionService {

    private final SourceRegistry sources;
    private final IngestionPipeline pipeline;

    public IngestionService(SourceRegistry sources, IngestionPipeline pipeline) {
        this.sources = sources;
        this.pipeline = pipeline;
    }

    public IngestionResult ingest(String sourceName, String tenantId) {
        IngestionRequest request = new IngestionRequest(sourceName, tenantId);
        RagSource source = sources.find(request.sourceName())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.RAG_E018, request.sourceName()));
        return pipeline.ingest(source, request.tenantId());
    }
}
