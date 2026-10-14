package io.aigoodle.connector;

public class ConnectorException extends RuntimeException {
    private final String code;

    public ConnectorException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ConnectorException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
