package dev.agentworkshq.rag.core.answer;

import java.util.List;

public record RagAnswer(String text, List<Citation> citations, boolean answered) {
}
