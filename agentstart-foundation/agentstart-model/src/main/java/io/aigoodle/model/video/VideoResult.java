package io.aigoodle.model.video;

import java.util.Map;

public record VideoResult(Status status, String videoUrl, String errorCode, Map<String, Object> usage) {
    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        EXPIRED
    }

    public VideoResult {
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }

    public boolean terminal() {
        return status != Status.QUEUED && status != Status.RUNNING;
    }
}
