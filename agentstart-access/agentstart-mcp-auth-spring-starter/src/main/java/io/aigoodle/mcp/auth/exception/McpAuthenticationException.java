package io.aigoodle.mcp.auth.exception;

public class McpAuthenticationException extends RuntimeException {

    public McpAuthenticationException(String message) {
        super(message);
    }

    public McpAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
