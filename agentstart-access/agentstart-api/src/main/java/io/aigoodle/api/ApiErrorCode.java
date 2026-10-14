package io.aigoodle.api;

import org.springframework.http.HttpStatus;

/**
 * Documented enum of every error slug the REST layer emits.
 *
 * <p>Consumers (frontend, external SDKs) can switch on {@link #getCode()} to
 * distinguish e.g. "missing model" from "network error" without parsing message
 * strings. Adding a new slug here is a breaking API change — bump the version
 * and mention it in CHANGELOG.md.
 */
public enum ApiErrorCode {

    // ------- 4xx: client errors
    BAD_REQUEST("bad_request", HttpStatus.BAD_REQUEST, "Malformed or unacceptable request"),
    VALIDATION_FAILED("validation_failed", HttpStatus.BAD_REQUEST, "One or more fields failed validation"),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Missing or invalid credentials"),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "Not allowed to access this resource"),
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND, "Resource does not exist"),
    CONFLICT("conflict", HttpStatus.CONFLICT, "Resource state conflict"),
    RUN_CONCURRENT_UPDATE("run_concurrent_update", HttpStatus.CONFLICT, "Agent run was modified concurrently"),
    INVALID_RUN_TRANSITION("invalid_run_transition", HttpStatus.CONFLICT, "Agent run state transition is invalid"),
    AGENT_RUN_NOT_WAITING("agent_run_not_waiting", HttpStatus.CONFLICT, "Agent run is no longer waiting for approval"),
    AGENT_RUN_NOT_PAUSED("agent_run_not_paused", HttpStatus.CONFLICT, "Agent run is no longer paused"),
    RATE_LIMITED("rate_limited", HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // ------- Business-domain errors (4xx-adjacent)
    MODEL_NOT_CONFIGURED(
            "model_not_configured", HttpStatus.PRECONDITION_FAILED, "No model configured for this operation"),
    EMBEDDING_MODEL_REQUIRED(
            "embedding_model_required",
            HttpStatus.PRECONDITION_FAILED,
            "Embedding model needed for HIGH_QUALITY indexing"),
    DATASET_NOT_FOUND("dataset_not_found", HttpStatus.NOT_FOUND, "Dataset not found"),
    DOCUMENT_NOT_FOUND("document_not_found", HttpStatus.NOT_FOUND, "Document not found"),
    SEGMENT_NOT_FOUND("segment_not_found", HttpStatus.NOT_FOUND, "Segment not found"),
    AGENT_NOT_FOUND("agent_not_found", HttpStatus.NOT_FOUND, "Agent not found"),
    WORKFLOW_NOT_FOUND("workflow_not_found", HttpStatus.NOT_FOUND, "Workflow not found"),
    MODEL_NOT_FOUND("model_not_found", HttpStatus.NOT_FOUND, "Model not found"),
    PROVIDER_NOT_FOUND("provider_not_found", HttpStatus.NOT_FOUND, "Provider not found"),
    TOOL_NOT_FOUND("tool_not_found", HttpStatus.NOT_FOUND, "Tool not found"),
    FILE_REQUIRED("file_required", HttpStatus.BAD_REQUEST, "An uploaded file is required"),
    UPLOAD_TOO_LARGE(
            "upload_too_large", HttpStatus.CONTENT_TOO_LARGE, "Uploaded file exceeds the configured size limit"),
    UPLOAD_READ_FAILED("upload_read_failed", HttpStatus.BAD_REQUEST, "Failed to read the uploaded file"),

    // ------- 5xx: server errors
    SERVER_ERROR("server_error", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error"),
    UPSTREAM_UNAVAILABLE(
            "upstream_unavailable", HttpStatus.BAD_GATEWAY, "Upstream (LLM / DB / vector store) is unreachable");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /** Map a raw slug (e.g. from {@code PlatformException.getCode()}) to an enum entry. */
    public static ApiErrorCode fromSlug(String slug) {
        if (slug == null) return SERVER_ERROR;
        for (ApiErrorCode c : values()) {
            if (c.code.equals(slug)) return c;
        }
        return null;
    }
}
