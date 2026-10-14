package io.aigoodle.trigger.dispatch;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Outcome of dispatching a trigger to its target.
 */
@Data
@Builder
public class DispatchResult {

    private boolean success;
    private String runId;
    private Map<String, Object> outputs;
    private String error;

    public static DispatchResult ok(String runId, Map<String, Object> outputs) {
        return DispatchResult.builder()
                .success(true)
                .runId(runId)
                .outputs(outputs)
                .build();
    }

    public static DispatchResult failed(String error) {
        return DispatchResult.builder().success(false).error(error).build();
    }
}
