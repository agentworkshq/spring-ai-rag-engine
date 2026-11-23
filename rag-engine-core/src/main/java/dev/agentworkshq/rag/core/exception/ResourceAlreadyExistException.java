package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public class ResourceAlreadyExistException extends BaseException {

    public ResourceAlreadyExistException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public ResourceAlreadyExistException(Throwable cause, ErrorMessages errorMessage, Object... args) {
        super(cause, errorMessage, args);
    }
}
