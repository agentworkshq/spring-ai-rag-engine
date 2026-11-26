package dev.agentworkshq.rag.core.answer;

public record Citation(int index, String documentId, String filename, Integer page, String snippet, boolean used) {

    @Override
    public String toString() {
        return "Citation[index=" + index + ", page=" + page + ", snippetLength="
                + (snippet == null ? 0 : snippet.length()) + ", used=" + used + "]";
    }
}
