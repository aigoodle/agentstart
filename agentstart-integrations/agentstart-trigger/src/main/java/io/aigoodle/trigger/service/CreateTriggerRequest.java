package io.aigoodle.trigger.service;

import io.aigoodle.trigger.api.TriggerType;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for creating a trigger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTriggerRequest {

    private String tenantId;
    private String userId;
    private String name;
    private TriggerType type;

    @Builder.Default
    private String targetType = "workflow";

    private String targetId;

    /** Type-specific config: webhook {@code path}/{@code token}, cron {@code expression}, event {@code eventName}. */
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();

    @Builder.Default
    private boolean enabled = true;
}
