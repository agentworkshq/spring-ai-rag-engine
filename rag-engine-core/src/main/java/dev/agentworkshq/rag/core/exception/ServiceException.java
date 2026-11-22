package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;

public class ServiceException extends BaseException {

    public ServiceException(ErrorMessages errorMessage, Object... args) {
        super(errorMessage, args);
    }
}
