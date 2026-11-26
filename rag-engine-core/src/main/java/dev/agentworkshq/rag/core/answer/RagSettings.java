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

    @Override
    public String toString() {
        return "RagSettings[topK=" + topK + ", maxTopK=" + maxTopK + ", similarityThreshold=" + similarityThreshold
                + ", queryRewrite=" + queryRewrite + ", citeSources=" + citeSources + ", noAnswerMessageLength="
                + (noAnswerMessage == null ? 0 : noAnswerMessage.length()) + ", maxContextChars=" + maxContextChars
                + ", maxQuestionLength=" + maxQuestionLength + "]";
    }
}
