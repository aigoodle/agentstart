package io.aigoodle.channel;

public class ChannelException extends RuntimeException {
    private final String code;

    public ChannelException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ChannelException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
