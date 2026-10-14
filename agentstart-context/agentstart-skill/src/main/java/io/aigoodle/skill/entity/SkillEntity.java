package io.aigoodle.skill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_skills")
public class SkillEntity extends BaseEntity {
    private String code;
    private String name;
    private String description;
    private String instructions;
    /** DRAFT, PUBLISHED or DISABLED. */
    private String status;

    private String toolNamesJson;
    private Long version;
}
