package dev.agentworkshq.rag.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class FakeEmbeddingModel implements EmbeddingModel {

    private static final List<String> KEYWORDS = List.of("apple", "banana", "cherry");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> result = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            result.add(new Embedding(vector(inputs.get(i)), i));
        }
        return new EmbeddingResponse(result);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return 8;
    }

    private static float[] vector(String text) {
        if (text.contains("BOOM")) {
            throw new IllegalStateException("embedding failed");
        }
        float[] v = new float[8];
        String lower = text.toLowerCase();
        boolean keyword = false;
        for (int i = 0; i < KEYWORDS.size(); i++) {
            if (lower.contains(KEYWORDS.get(i))) {
                v[i] = 1;
                keyword = true;
            }
        }
        if (keyword) {
            return v;
        }
        for (int i = 0; i < text.length(); i++) {
            v[i % 8] += text.charAt(i);
        }
        v[0] += 1;
        return v;
    }
}
