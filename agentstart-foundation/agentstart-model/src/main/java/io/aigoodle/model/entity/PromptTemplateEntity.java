package io.aigoodle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A reusable prompt template. Content is a string with {@code {{#var#}}}
 * placeholders (same syntax as the workflow VariableResolver) so callers can render
 * it against their own variable map.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_prompt_template")
public class PromptTemplateEntity extends BaseEntity {

    private String name;

    /** Free-form grouping key (e.g. {@code "summarization"}, {@code "classifier"}). */
    private String category;

    private String description;

    private String content;

    /** JSON array of tags for filtering. */
    private String tagsJson;
}
