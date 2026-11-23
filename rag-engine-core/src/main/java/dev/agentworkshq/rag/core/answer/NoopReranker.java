package dev.agentworkshq.rag.core.answer;

import java.util.List;
import org.springframework.ai.document.Document;

public class NoopReranker implements Reranker {

    @Override
    public List<Document> rerank(String question, List<Document> documents) {
        return documents;
    }
}
