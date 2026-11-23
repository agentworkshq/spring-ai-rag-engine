package dev.agentworkshq.rag.core.ingest;

import java.util.List;
import org.springframework.ai.document.Document;

public interface DocumentLoader {

    List<Document> load(SourceItem item);
}
