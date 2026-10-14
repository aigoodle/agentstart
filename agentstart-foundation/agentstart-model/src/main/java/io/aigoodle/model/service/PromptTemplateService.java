package io.aigoodle.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.entity.PromptTemplateEntity;
import io.aigoodle.model.mapper.PromptTemplateMapper;
import java.util.List;
import java.util.Map;

/**
 * CRUD for reusable prompt templates plus a lightweight {@link #render(String, Map)}
 * helper — the same {@code {{#name#}}} placeholder syntax used across the workflow
 * engine, so templates round-trip cleanly.
 */
public class PromptTemplateService {

    private static final String DEFAULT_TENANT = "default";

    private final PromptTemplateMapper mapper;
    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    public PromptTemplateService(PromptTemplateMapper mapper) {
        this.mapper = mapper;
    }

    public PromptTemplateEntity create(PromptTemplateDraft draft) {
        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setTenantId(defaultTenant(draft.tenantId()));
        template.setName(draft.name());
        template.setCategory(draft.category());
        template.setDescription(draft.description());
        template.setContent(draft.content());
        template.setTagsJson(tagsJson(draft.tags()));
        mapper.insert(template);
        return template;
    }

    /** @deprecated use {@link #create(PromptTemplateDraft)} to avoid positional arguments. */
    @Deprecated
    public PromptTemplateEntity create(
            String tenantId, String name, String category, String description, String content, List<String> tags) {
        return create(new PromptTemplateDraft(tenantId, name, category, description, content, tags));
    }

    public PromptTemplateEntity update(String id, PromptTemplatePatch patch) {
        PromptTemplateEntity template = require(id);
        if (patch.name() != null) template.setName(patch.name());
        if (patch.category() != null) template.setCategory(patch.category());
        if (patch.description() != null) template.setDescription(patch.description());
        if (patch.content() != null) template.setContent(patch.content());
        if (patch.tags() != null) template.setTagsJson(JsonUtils.toJson(patch.tags()));
        mapper.updateById(template);
        return template;
    }

    public PromptTemplateEntity update(String tenantId, String id, PromptTemplatePatch patch) {
        PromptTemplateEntity template = require(tenantId, id);
        if (patch.name() != null) template.setName(patch.name());
        if (patch.category() != null) template.setCategory(patch.category());
        if (patch.description() != null) template.setDescription(patch.description());
        if (patch.content() != null) template.setContent(patch.content());
        if (patch.tags() != null) template.setTagsJson(JsonUtils.toJson(patch.tags()));
        mapper.update(
                template,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PromptTemplateEntity>()
                        .eq(PromptTemplateEntity::getTenantId, template.getTenantId())
                        .eq(PromptTemplateEntity::getId, template.getId()));
        return template;
    }

    /** @deprecated use {@link #update(String, PromptTemplatePatch)}. */
    @Deprecated
    public PromptTemplateEntity update(
            String id, String name, String category, String description, String content, List<String> tags) {
        return update(id, new PromptTemplatePatch(name, category, description, content, tags));
    }

    public void delete(String id) {
        mapper.deleteById(id);
    }

    public void delete(String tenantId, String id) {
        require(tenantId, id);
        mapper.delete(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTenantId, defaultTenant(tenantId))
                .eq(PromptTemplateEntity::getId, id));
    }

    public PromptTemplateEntity require(String id) {
        PromptTemplateEntity template = mapper.selectById(id);
        if (template == null) {
            throw new PlatformException("prompt_template_not_found", "Prompt template not found: " + id, null);
        }
        return template;
    }

    public PromptTemplateEntity require(String tenantId, String id) {
        PromptTemplateEntity template = mapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTenantId, defaultTenant(tenantId))
                .eq(PromptTemplateEntity::getId, id)
                .last("LIMIT 1"));
        if (template == null) {
            throw new PlatformException("prompt_template_not_found", "Prompt template not found", null);
        }
        return template;
    }

    public PromptTemplateEntity get(String id) {
        return mapper.selectById(id);
    }

    public PromptTemplateEntity get(String tenantId, String id) {
        return mapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTenantId, defaultTenant(tenantId))
                .eq(PromptTemplateEntity::getId, id));
    }

    public List<PromptTemplateEntity> list(String tenantId, String category) {
        LambdaQueryWrapper<PromptTemplateEntity> query = new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTenantId, defaultTenant(tenantId))
                .orderByDesc(PromptTemplateEntity::getUpdatedAt);
        if (category != null && !category.isBlank()) {
            query.eq(PromptTemplateEntity::getCategory, category);
        }
        return mapper.selectList(query);
    }

    public long count(String tenantId) {
        return mapper.selectCount(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTenantId, defaultTenant(tenantId)));
    }

    /**
     * Seed a small set of starter templates when the table is empty for {@code tenantId}
     * — a nudge for first-time users so the Prompt library page isn't a wall of empty
     * state on first boot. Safe to call repeatedly; only writes when the table is empty.
     */
    public void seedStartersIfEmpty(String tenantId) {
        String resolvedTenantId = defaultTenant(tenantId);
        if (count(resolvedTenantId) > 0) {
            return;
        }
        StarterPromptTemplates.forTenant(resolvedTenantId).forEach(this::create);
    }

    /** Extract the referenced variable names — for previewing what a template needs. */
    public List<String> variablesOf(String content) {
        return renderer.referencedVariables(content);
    }

    /** Render a template against a variable map. Missing keys resolve to empty. */
    public String render(String content, Map<String, Object> variables) {
        return renderer.render(content, variables);
    }

    private static String defaultTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId;
    }

    private static String tagsJson(List<String> tags) {
        return tags == null ? "[]" : JsonUtils.toJson(tags);
    }
}
