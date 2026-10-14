package io.aigoodle.common.exception;

/**
 * Base unchecked exception for all spring-agent-start modules.
 */
public class PlatformException extends RuntimeException {

    private final String code;

    public PlatformException(String message) {
        this("agent_error", message, null);
    }

    public PlatformException(String message, Throwable cause) {
        this("agent_error", message, cause);
    }

    public PlatformException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PlatformException of(String message) {
        return new PlatformException(message);
    }
}
