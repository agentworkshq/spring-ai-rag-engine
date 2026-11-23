package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceNotFoundException(Throwable cause, ErrorMessages errorMessage, Object... args) {
        super(cause, errorMessage, args);
    }
}
