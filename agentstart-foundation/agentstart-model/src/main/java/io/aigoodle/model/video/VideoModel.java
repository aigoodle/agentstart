package io.aigoodle.model.video;

import java.util.Map;

/** Vendor-neutral asynchronous API. Submission MUST NOT be automatically retried. */
public interface VideoModel {
    String submit(VideoRequest request);

    VideoResult query(String taskId);
    /** Return false if the vendor cannot cancel the current task; never claim cancellation on failure. */
    boolean cancel(String taskId);
    /** JSON Schema for this model's supported invocation parameters. */
    Map<String, Object> parameterSchema();

    default int maxPromptLength() {
        return 10000;
    }
}
