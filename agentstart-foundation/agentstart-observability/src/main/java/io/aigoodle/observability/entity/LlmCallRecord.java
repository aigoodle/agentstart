package io.aigoodle.observability.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One persisted LLM invocation: who, which model, how many tokens, how much it cost and
 * how long it took — the raw fact table for LLMOps dashboards.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_llm_calls")
public class LlmCallRecord extends BaseEntity {

    /** Application owning this invocation; null for historical or unscoped calls. */
    private String appId;

    private String provider;
    private String model;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /** Cost in micro-units of the configured currency (cost = micros / 1e6). */
    private Long costMicros;

    private Long latencyMs;

    private Boolean success;

    private String errorType;
}
