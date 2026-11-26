package dev.agentworkshq.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.agentworkshq.rag.core.constants.ErrorMessages;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record ApiResponse<T>(
        String status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object metadata,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ApiError> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, null, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data, PageMetadata metadata) {
        return new ApiResponse<>("success", message, null, data, metadata, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, null, path, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, List<ApiError> errors, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, errors, path, Instant.now());
    }

    @Override
    public String toString() {
        // status and errorCode are plain strings, so only the values this library produces are printed
        boolean knownStatus = "success".equals(status) || "error".equals(status);
        boolean knownCode = Arrays.stream(ErrorMessages.values()).anyMatch(e -> e.name().equals(errorCode));
        return "ApiResponse[status=" + (knownStatus ? status : null) + ", errorCode=" + (knownCode ? errorCode : null)
                + ", hasData=" + (data != null) + ", errors=" + (errors == null ? 0 : errors.size()) + "]";
    }
}
