package dev.agentworkshq.rag.core.answer;

public record Citation(int index, String documentId, String filename, Integer page, String snippet, boolean used) {
}
