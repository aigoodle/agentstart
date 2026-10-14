package io.aigoodle.workflow.node;

import io.aigoodle.common.exception.PlatformException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/** Converts thrown failures into stable, retry-aware node results. */
public final class NodeFailureClassifier {

    private NodeFailureClassifier() {}

    public static NodeResult classify(Throwable failure) {
        Throwable root = rootCause(failure);
        String message = safeMessage(root);
        if (root instanceof HttpTimeoutException || root instanceof TimeoutException) {
            return NodeResult.transientFailure("TIMEOUT", message);
        }
        if (root instanceof IOException) {
            return NodeResult.transientFailure("TRANSPORT_ERROR", message);
        }
        if (root instanceof IllegalArgumentException) {
            return NodeResult.permanentFailure("INVALID_ARGUMENT", message);
        }
        if (root instanceof SecurityException) {
            return NodeResult.permanentFailure("FORBIDDEN", message);
        }
        if (root instanceof PlatformException platform) {
            return NodeResult.permanentFailure(platform.getCode(), message);
        }
        return NodeResult.permanentFailure("UNEXPECTED_ERROR", message);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
