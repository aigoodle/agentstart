package io.aigoodle.common.context;

/** Identifies how the trusted request context was authenticated. */
public enum PrincipalType {
    USER,
    APP_API_KEY,
    CHAT_SESSION,
    ANONYMOUS
}
