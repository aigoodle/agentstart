package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Many-to-many join between {@link TagEntity} and an app or dataset id. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_tag_bindings")
public class TagBindingEntity extends BaseEntity {

    /** FK to {@code tags.id}. */
    private String tagId;

    /** The tagged object's id — an app id or a dataset id, discriminated by {@code targetType}. */
    private String targetId;

    /** {@code app} / {@code knowledge}. */
    private String targetType;
}
