package io.aigoodle.trigger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import io.aigoodle.trigger.api.TriggerType;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A persisted trigger: a source ({@link TriggerType}) that fires a target (a workflow,
 * by default) with a mapped payload.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_triggers")
public class TriggerEntity extends BaseEntity {

    private String name;

    /** User that created this durable trigger; restored for scheduled execution. */
    private String userId;

    private TriggerType type;

    private Boolean enabled;

    /** Dispatch target type, resolved by a {@code TriggerDispatcher} (e.g. {@code workflow}). */
    private String targetType;

    private String targetId;

    /**
     * Type-specific config JSON: webhook {@code {path,token}}, cron {@code {expression}},
     * event {@code {eventName}}.
     */
    private String configJson;

    /** Persisted scheduling cursor used by every application node. */
    private LocalDateTime nextFireAt;

    private LocalDateTime lastFireAt;

    /** Short database lease that guarantees one cluster node owns a due firing. */
    private LocalDateTime lockUntil;

    private String lockOwner;

    private Long fireCount;
}
