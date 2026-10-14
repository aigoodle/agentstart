package io.aigoodle.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Data;

/**
 * Uniform envelope for every REST response. Success: {@code {code: 'ok', data}}.
 * Error: {@code {code: '<slug>', message, details?, traceId?}}.
 *
 * <p>{@code details} carries field-level or structured diagnostics (e.g. a map of
 * validation errors keyed by field name). {@code traceId} lets ops correlate a
 * client-visible error with server logs — set it via
 * {@link #withTraceId(String)} in the exception handler.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    public static final String OK_CODE = "ok";

    private String code;
    private String message;
    private T data;
    private Map<String, Object> details;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(OK_CODE);
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> error(ApiErrorCode code, String message) {
        return error(code.getCode(), message);
    }

    public ApiResponse<T> withDetails(Map<String, Object> details) {
        this.details = details;
        return this;
    }

    public ApiResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
