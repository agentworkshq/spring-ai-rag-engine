package dev.agentworkshq.rag.core.constants;

public enum ErrorMessages {

    RAG_E001(404, "Resource not found"),
    RAG_E002(404, "Document with id %s not found"),
    RAG_E003(409, "Resource already exists"),
    RAG_E004(401, "Authentication required"),
    RAG_E005(400, "Validation failed"),
    RAG_E006(500, "Document ingestion failed"),
    RAG_E007(500, "An unexpected error occurred"),
    RAG_E008(400, "Invalid request"),
    RAG_E009(413, "File %s is %d bytes, exceeding the maximum of %d bytes"),
    RAG_E010(413, "PDF %s has %d pages, exceeding the maximum of %d pages"),
    RAG_E011(500, "Reader for %s is not on the classpath, add dependency %s", "A required document reader is missing"),
    RAG_E012(400, "Unsupported file type: %s"),
    RAG_E013(500, "Source root does not exist or is not a directory: %s", "A source is not available"),
    RAG_E014(400, "Include pattern must not contain '..': %s"),
    RAG_E015(500, "Failed to read %s", "Failed to read a document"),
    RAG_E016(409, "Ingestion is already running for source %s and tenant %s"),
    RAG_E017(409, "Source %s returned no items but has registered documents, nothing was deleted"),
    RAG_E018(404, "Source %s not found"),
    RAG_E019(500, "No VectorStore bean found, add a Spring AI vector store starter such as spring-ai-starter-vector-store-pgvector"),
    RAG_E020(500, "No ChatClient.Builder bean found, add a Spring AI chat model starter such as spring-ai-starter-model-openai"),
    RAG_E021(400, "Tenant id must not be blank"),
    RAG_E022(400, "Question must not be blank"),
    RAG_E023(400, "Question exceeds the maximum length of %d characters"),
    RAG_E024(403, "Access denied"),
    RAG_E025(413, "Uploaded file is too large"),
    RAG_E026(400, "A multipart part named \"file\" is required"),
    RAG_E027(400, "Invalid file name"),
    RAG_E028(400, "File content does not match its type"),
    RAG_E029(409, "Document limit of %d reached for this tenant"),
    RAG_E030(413, "File exceeds the maximum size of %d bytes"),
    RAG_E031(400, "Tenant id must not be blank, longer than %d characters or contain control characters"),
    RAG_E032(400, "Source names must not be blank, longer than %d characters or contain control characters"),
    RAG_E033(500, "A source could not be scanned completely");

    private final int httpStatus;
    private final String message;

    private final String publicMessage;

    ErrorMessages(int httpStatus, String message) {
        this(httpStatus, message, message);
    }

    ErrorMessages(int httpStatus, String message, String publicMessage) {
        this.httpStatus = httpStatus;
        this.message = message;
        this.publicMessage = publicMessage;
    }

    public String getPublicMessage() {
        return publicMessage;
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
