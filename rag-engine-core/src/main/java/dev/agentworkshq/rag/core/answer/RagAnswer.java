package dev.agentworkshq.rag.core.answer;

import java.util.List;

public record RagAnswer(String text, List<Citation> citations, boolean answered) {

    @Override
    public String toString() {
        return "RagAnswer[textLength=" + (text == null ? 0 : text.length()) + ", citations="
                + (citations == null ? 0 : citations.size()) + ", answered=" + answered + "]";
    }
}
