package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public class AppException extends BaseException {

    public AppException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }

    public AppException(Throwable cause, ErrorMessages errorMessage, Object... args) {
        super(cause, errorMessage, args);
    }
}
