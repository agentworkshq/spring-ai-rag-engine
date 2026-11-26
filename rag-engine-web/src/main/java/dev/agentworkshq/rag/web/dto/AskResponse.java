package dev.agentworkshq.rag.web.dto;

import dev.agentworkshq.rag.core.answer.RagAnswer;
import java.util.List;

public record AskResponse(String answer, boolean answered, List<CitationResponse> citations) {

    public static AskResponse from(RagAnswer answer) {
        return new AskResponse(answer.text(), answer.answered(),
                answer.citations().stream().map(CitationResponse::from).toList());
    }

    @Override
    public String toString() {
        return "AskResponse[answerLength=" + (answer == null ? 0 : answer.length()) + ", answered=" + answered
                + ", citations=" + (citations == null ? 0 : citations.size()) + "]";
    }
}
