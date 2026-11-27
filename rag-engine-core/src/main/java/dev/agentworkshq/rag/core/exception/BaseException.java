package dev.agentworkshq.rag.core.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.text.Texts;
import java.util.Arrays;

public abstract class BaseException extends RuntimeException {

    private final ErrorMessages errorMessage;

    protected BaseException(ErrorMessages errorMessage, Object... args) {
        this(null, errorMessage, args);
    }

    protected BaseException(Throwable cause, ErrorMessages errorMessage, Object... args) {
        super(message(errorMessage, args), cause);
        this.errorMessage = errorMessage;
    }

    public ErrorMessages getErrorMessage() {
        return errorMessage;
    }

    // a server error keeps its detail for the logs, so clients get a message without arguments
    public String getClientMessage() {
        return errorMessage.getHttpStatus() >= 500 ? errorMessage.getPublicMessage() : getMessage();
    }

    private static String message(ErrorMessages errorMessage, Object[] args) {
        if (args == null || args.length == 0) {
            return errorMessage.getMessage();
        }
        if (errorMessage.getHttpStatus() >= 500) {
            return errorMessage.format(args);
        }
        // client errors echo names that came from the caller
        return errorMessage.format(Arrays.stream(args)
                .map(a -> a instanceof Number ? a : Texts.clean(String.valueOf(a), Texts.MAX_ECHO_LENGTH))
                .toArray());
    }
}
