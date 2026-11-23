package dev.agentworkshq.rag.core.answer;

import java.util.List;
import org.springframework.ai.document.Document;

public interface Reranker {

    List<Document> rerank(String question, List<Document> documents);
}
