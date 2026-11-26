package dev.agentworkshq.rag.web.dto;

public record ApiError(String field, String message) {

    @Override
    public String toString() {
        return "ApiError[fieldLength=" + (field == null ? 0 : field.length()) + ", messageLength="
                + (message == null ? 0 : message.length()) + "]";
    }
}
