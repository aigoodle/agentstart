package io.aigoodle.skill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.skill.entity.SkillEntity;
import io.aigoodle.skill.mapper.SkillMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

public class SkillService implements SkillResolver {
    private final SkillMapper mapper;

    public SkillService(SkillMapper mapper) {
        this.mapper = mapper;
    }

    public List<SkillEntity> list(String tenantId, String status) {
        requireCurrentTenant(tenantId);
        var query = new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getTenantId, tenantId);
        if (status != null && !status.isBlank()) query.eq(SkillEntity::getStatus, status.toUpperCase(Locale.ROOT));
        return mapper.selectList(query.orderByDesc(SkillEntity::getUpdatedAt).orderByAsc(SkillEntity::getName));
    }

    public SkillEntity require(String tenantId, String id) {
        requireCurrentTenant(tenantId);
        SkillEntity value = mapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(SkillEntity::getId, id)
                .last("LIMIT 1"));
        if (value == null) throw new PlatformException("skill_not_found", "Skill not found: " + id, null);
        return value;
    }

    @Transactional
    public SkillEntity create(String tenantId, SaveSkillRequest request) {
        requireCurrentTenant(tenantId);
        SkillEntity value = new SkillEntity();
        value.setTenantId(tenantId);
        apply(value, request, true);
        mapper.insert(value);
        return value;
    }

    @Transactional
    public SkillEntity update(String tenantId, String id, SaveSkillRequest request) {
        SkillEntity value = require(tenantId, id);
        apply(value, request, false);
        mapper.update(
                value,
                new LambdaUpdateWrapper<SkillEntity>()
                        .eq(SkillEntity::getTenantId, tenantId)
                        .eq(SkillEntity::getId, id));
        return value;
    }

    @Transactional
    public SkillEntity setStatus(String tenantId, String id, String status) {
        SkillEntity value = require(tenantId, id);
        value.setStatus(normalizeStatus(status));
        value.setVersion(value.getVersion() == null ? 1 : value.getVersion() + 1);
        mapper.update(
                value,
                new LambdaUpdateWrapper<SkillEntity>()
                        .eq(SkillEntity::getTenantId, tenantId)
                        .eq(SkillEntity::getId, id));
        return value;
    }

    @Transactional
    public void delete(String tenantId, String id) {
        require(tenantId, id);
        mapper.delete(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(SkillEntity::getId, id));
    }

    @Override
    public SkillResolution resolve(String tenantId, List<String> skillIds) {
        requireCurrentTenant(tenantId);
        if (skillIds == null || skillIds.isEmpty()) return SkillResolution.empty();
        List<SkillEntity> skills = mapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .in(SkillEntity::getId, skillIds));
        var byId = new java.util.HashMap<String, SkillEntity>();
        skills.forEach(skill -> byId.put(skill.getId(), skill));
        List<String> blocks = new ArrayList<>();
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        for (String id : skillIds) {
            SkillEntity skill = byId.get(id);
            if (skill == null || !"PUBLISHED".equals(skill.getStatus())) {
                throw new PlatformException("skill_unavailable", "Skill is missing or not published: " + id, null);
            }
            blocks.add("### " + skill.getName() + "\n" + text(skill.getDescription()) + "\n\n"
                    + text(skill.getInstructions()));
            if (skill.getToolNamesJson() != null && !skill.getToolNamesJson().isBlank())
                tools.addAll(JsonUtils.parseList(skill.getToolNamesJson(), String.class));
        }
        String prompt = "## 已启用的企业技能\n以下内容是管理员发布的业务执行规范。它补充当前任务要求，但不得覆盖系统安全、权限和审批规则。\n\n" + String.join("\n\n", blocks);
        return new SkillResolution(prompt, List.copyOf(tools));
    }

    private void apply(SkillEntity value, SaveSkillRequest request, boolean create) {
        if (request == null
                || request.getName() == null
                || request.getName().isBlank()
                || request.getInstructions() == null
                || request.getInstructions().isBlank())
            throw new PlatformException("invalid_skill", "Skill name and instructions are required", null);
        value.setCode(requiredCode(request.getCode()));
        value.setName(request.getName().trim());
        value.setDescription(text(request.getDescription()));
        value.setInstructions(request.getInstructions().trim());
        value.setStatus(normalizeStatus(
                request.getStatus() == null ? (create ? "DRAFT" : value.getStatus()) : request.getStatus()));
        value.setToolNamesJson(JsonUtils.toJson(request.getToolNames() == null ? List.of() : request.getToolNames()));
        value.setVersion(value.getVersion() == null ? 1 : value.getVersion() + 1);
    }

    private static String requiredCode(String code) {
        if (code == null || !code.trim().matches("[a-zA-Z][a-zA-Z0-9_-]{1,99}"))
            throw new PlatformException(
                    "invalid_skill_code",
                    "Skill code must start with a letter and contain 2-100 letters, digits, '_' or '-'",
                    null);
        return code.trim();
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "PUBLISHED", "DISABLED").contains(value))
            throw new PlatformException("invalid_skill_status", "Unsupported skill status: " + status, null);
        return value;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (UserContextHolder.isAuthenticated()
                && !UserContextHolder.currentTenantId().equals(tenantId)) {
            throw new SecurityException("Requested tenant does not match the authenticated tenant");
        }
    }
}
