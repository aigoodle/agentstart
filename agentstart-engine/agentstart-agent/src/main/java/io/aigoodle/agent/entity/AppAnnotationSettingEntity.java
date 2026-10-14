package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Retrieval settings shared by all annotations owned by one application. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_annotation_settings")
public class AppAnnotationSettingEntity extends BaseEntity {

    /** Identifier of the application that owns these settings. */
    private String appId;

    /** Minimum similarity score required to use an annotation, from 0.0 to 1.0. */
    private Float scoreThreshold;

    /** Embedding model used to compare questions. */
    private String embeddingModelId;

    /** Whether annotation retrieval is enabled for the application. */
    private Boolean enabled;
}
