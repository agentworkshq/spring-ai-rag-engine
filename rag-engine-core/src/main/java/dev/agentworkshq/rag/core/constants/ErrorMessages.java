package dev.agentworkshq.rag.core.constants;

public enum ErrorMessages {

    RAG_E001(404, "Resource not found"),
    RAG_E002(404, "Document with id %s not found"),
    RAG_E003(409, "Resource already exists"),
    RAG_E004(401, "Authentication required"),
    RAG_E005(400, "Validation failed"),
    RAG_E006(500, "Document ingestion failed"),
    RAG_E007(500, "An unexpected error occurred"),
    RAG_E008(400, "Invalid request");

    private final int httpStatus;
    private final String message;

    ErrorMessages(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public String format(Object... args) {
        return String.format(message, args);
    }
}
