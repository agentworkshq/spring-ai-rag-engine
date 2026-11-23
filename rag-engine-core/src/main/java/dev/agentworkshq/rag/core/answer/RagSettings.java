package dev.agentworkshq.rag.core.answer;

public record RagSettings(
        int topK,
        int maxTopK,
        double similarityThreshold,
        boolean queryRewrite,
        boolean citeSources,
        String noAnswerMessage,
        int maxContextChars,
        int maxQuestionLength) {
}
