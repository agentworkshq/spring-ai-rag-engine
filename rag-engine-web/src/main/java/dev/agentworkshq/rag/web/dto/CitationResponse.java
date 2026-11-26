package dev.agentworkshq.rag.web.dto;

import dev.agentworkshq.rag.core.answer.Citation;

public record CitationResponse(int index, String documentId, String filename, Integer page, String snippet,
                               boolean used) {

    public static CitationResponse from(Citation citation) {
        return new CitationResponse(citation.index(), citation.documentId(), citation.filename(), citation.page(),
                citation.snippet(), citation.used());
    }

    @Override
    public String toString() {
        return "CitationResponse[index=" + index + ", page=" + page + ", snippetLength="
                + (snippet == null ? 0 : snippet.length()) + ", used=" + used + "]";
    }
}
