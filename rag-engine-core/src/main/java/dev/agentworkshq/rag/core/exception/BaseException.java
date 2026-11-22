package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public abstract class BaseException extends RuntimeException {

    private final ErrorMessages errorMessage;

    protected BaseException(ErrorMessages errorMessage, Object... args) {
        super(args == null || args.length == 0 ? errorMessage.getMessage() : errorMessage.format(args));
        this.errorMessage = errorMessage;
    }

    public ErrorMessages getErrorMessage() {
        return errorMessage;
    }
}
