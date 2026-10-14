package io.aigoodle.mcp.auth.exception;

public class McpAccessDeniedException extends RuntimeException {

    public McpAccessDeniedException(String message) {
        super(message);
    }
}
