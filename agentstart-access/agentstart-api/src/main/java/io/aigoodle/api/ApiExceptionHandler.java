package io.aigoodle.api;

import io.aigoodle.common.exception.PlatformException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Shared wire-level errors; domain-specific web advice takes precedence when installed. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<?>> platform(PlatformException exception) {
        ApiErrorCode code = ApiErrorCode.fromSlug(exception.getCode());
        return response(code == null ? ApiErrorCode.BAD_REQUEST : code, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> invalid(IllegalArgumentException exception) {
        return response(ApiErrorCode.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<?>> forbidden(SecurityException exception) {
        return response(ApiErrorCode.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> status(ResponseStatusException exception) {
        ApiErrorCode code =
                switch (exception.getStatusCode().value()) {
                    case 401 -> ApiErrorCode.UNAUTHORIZED;
                    case 403 -> ApiErrorCode.FORBIDDEN;
                    case 404 -> ApiErrorCode.NOT_FOUND;
                    case 409 -> ApiErrorCode.CONFLICT;
                    case 413 -> ApiErrorCode.UPLOAD_TOO_LARGE;
                    case 429 -> ApiErrorCode.RATE_LIMITED;
                    default -> exception.getStatusCode().is5xxServerError()
                            ? ApiErrorCode.SERVER_ERROR
                            : ApiErrorCode.BAD_REQUEST;
                };
        return ResponseEntity.status(exception.getStatusCode()).body(body(code, exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> unexpected(Exception exception) {
        return response(ApiErrorCode.SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<ApiResponse<?>> response(ApiErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(body(code, message));
    }

    private static ApiResponse<?> body(ApiErrorCode code, String message) {
        return ApiResponse.error(code, message == null ? code.getDefaultMessage() : message)
                .withTraceId(UUID.randomUUID().toString().replace("-", ""));
    }
}
