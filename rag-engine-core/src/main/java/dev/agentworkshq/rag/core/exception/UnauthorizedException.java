package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }
}
