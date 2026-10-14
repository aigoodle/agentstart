package io.aigoodle.tool.execution;

/** Stable failure type surfaced consistently across local and MCP tools. */
public class ToolExecutionException extends RuntimeException {
    private final String code;

    public ToolExecutionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ToolExecutionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
