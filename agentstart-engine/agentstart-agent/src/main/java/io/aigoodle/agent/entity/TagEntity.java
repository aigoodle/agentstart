package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A tenant-scoped organisational tag applied to apps or knowledge bases
 * (Dify parity — the sidebar filter chips). Many-to-many bindings live in
 * {@link TagBindingEntity}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_tags")
public class TagEntity extends BaseEntity {

    /** {@code app} / {@code knowledge}. */
    private String type;

    /** Display name — unique per (tenant, type). */
    private String name;
}
