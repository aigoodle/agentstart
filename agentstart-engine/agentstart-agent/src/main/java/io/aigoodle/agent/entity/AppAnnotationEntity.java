package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A curated question-and-answer pair owned by an application. When retrieval
 * matches {@link #question}, the application may return {@link #content}
 * directly instead of invoking an LLM.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_annotations")
public class AppAnnotationEntity extends BaseEntity {

    /** Identifier of the application that owns this annotation. */
    private String appId;

    /** User-facing question that this annotation recognizes. */
    private String question;

    /** Curated answer returned when the annotation matches. */
    private String content;

    /** Number of times this annotation has served a response. */
    private Integer hitCount;

    /** Whether this annotation is available for retrieval. */
    private Boolean enabled;
}
