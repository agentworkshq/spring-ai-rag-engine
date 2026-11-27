package dev.agentworkshq.rag.web.exception;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.BaseException;
import dev.agentworkshq.rag.core.text.Texts;
import dev.agentworkshq.rag.web.controller.RagControllers;
import dev.agentworkshq.rag.web.dto.ApiError;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackageClasses = RagControllers.class)
public class RagExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RagExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBase(BaseException ex, HttpServletRequest req) {
        ErrorMessages em = ex.getErrorMessage();
        if (em.getHttpStatus() >= 500) {
            logServerError(em, ex, req.getRequestURI());
        } else {
            logClientError(em, ex, req.getRequestURI());
        }
        return ResponseEntity.status(em.getHttpStatus())
                .body(ApiResponse.error(em.name(), ex.getClientMessage(), echo(req.getRequestURI())));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraints(ConstraintViolationException ex,
                                                               HttpServletRequest req) {
        ErrorMessages em = ex.getConstraintViolations().stream().findFirst()
                .map(ConstraintViolation::getMessageTemplate)
                .flatMap(RagExceptionHandler::toErrorMessage)
                .orElse(ErrorMessages.RAG_E005);
        logClientError(em, ex, req.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(em.name(), em.getMessage(), echo(req.getRequestURI())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        ErrorMessages em = ErrorMessages.RAG_E007;
        logServerError(em, ex, req.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(em.name(), em.getMessage(), echo(req.getRequestURI())));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ErrorMessages em = ErrorMessages.RAG_E005;
        List<ApiError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError(f.getField(), f.getDefaultMessage()))
                .toList();
        log.warn("{}: {} field error(s)", em.name(), fields.size());
        log.debug("{} on {}", em.name(), path(request), ex);
        return new ResponseEntity<>(ApiResponse.error(em.name(), em.getMessage(), fields, path(request)),
                headers, HttpStatus.BAD_REQUEST);
    }


    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
                                                                     HttpHeaders headers, HttpStatusCode status,
                                                                     WebRequest request) {
        ErrorMessages em = ErrorMessages.RAG_E026;
        logClientError(em, ex, path(request));
        return new ResponseEntity<>(ApiResponse.error(em.name(), em.getMessage(), path(request)), headers,
                HttpStatus.valueOf(em.getHttpStatus()));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        ErrorMessages em = ErrorMessages.RAG_E025;
        logClientError(em, ex, path(request));
        return new ResponseEntity<>(ApiResponse.error(em.name(), em.getMessage(), path(request)), headers,
                HttpStatus.valueOf(em.getHttpStatus()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ErrorMessages em = forStatus(status);
        if (status.is5xxServerError()) {
            logServerError(em, ex, path(request));
        } else {
            logClientError(em, ex, path(request));
        }
        return new ResponseEntity<>(ApiResponse.error(em.name(), em.getMessage(), path(request)), headers, status);
    }

    private static ErrorMessages forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorMessages.RAG_E001;
            default -> status.is5xxServerError() ? ErrorMessages.RAG_E007 : ErrorMessages.RAG_E008;
        };
    }

    private static void logServerError(ErrorMessages em, Exception ex, String uri) {
        log.error("{}: {}", em.name(), ex.getClass().getName());
        log.debug("{} on {}", em.name(), uri, ex);
    }

    private static void logClientError(ErrorMessages em, Exception ex, String uri) {
        log.warn("{}: {}", em.name(), ex.getClass().getName());
        log.debug("{} on {}", em.name(), uri, ex);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? echo(servlet.getRequest().getRequestURI()) : null;
    }

    // a path can carry a file name or a source name, so each segment is cleaned and cut like any echoed value
    private static String echo(String uri) {
        return Arrays.stream(uri.split("/", -1))
                .map(segment -> Texts.clean(segment, Texts.MAX_ECHO_LENGTH))
                .collect(Collectors.joining("/"));
    }

    private static Optional<ErrorMessages> toErrorMessage(String name) {
        try {
            return Optional.of(ErrorMessages.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
