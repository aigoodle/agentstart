package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.TagBindingEntity;
import io.aigoodle.agent.entity.TagEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.mapper.TagBindingMapper;
import io.aigoodle.agent.mapper.TagMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link TagEntity} + binding management. The tags are tenant-scoped
 * and typed ({@code app} vs {@code knowledge}) so app filters and dataset
 * filters share the same primitive without stepping on each other.
 */
public class TagService {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String DEFAULT_TARGET_TYPE = "app";

    private final TagMapper tagMapper;
    private final TagBindingMapper bindingMapper;
    private final AppMapper appMapper;
    private final DatasetOwnershipResolver datasetOwnershipResolver;

    public TagService(TagMapper tagMapper, TagBindingMapper bindingMapper) {
        this(tagMapper, bindingMapper, null, null);
    }

    public TagService(
            TagMapper tagMapper,
            TagBindingMapper bindingMapper,
            AppMapper appMapper,
            DatasetOwnershipResolver datasetOwnershipResolver) {
        this.tagMapper = tagMapper;
        this.bindingMapper = bindingMapper;
        this.appMapper = appMapper;
        this.datasetOwnershipResolver = datasetOwnershipResolver;
    }

    public List<TagEntity> list(String tenantId, String type) {
        return tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(type != null, TagEntity::getType, type)
                .orderByAsc(TagEntity::getName));
    }

    public TagEntity require(String tagId) {
        return require(UserContextHolder.currentTenantId(), tagId);
    }

    public TagEntity require(String tenantId, String tagId) {
        TagEntity tag = tagMapper.selectOne(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(TagEntity::getId, tagId)
                .last("LIMIT 1"));
        if (tag == null) throw new PlatformException("tag_not_found", "Tag not found", null);
        return tag;
    }

    @Transactional
    public TagEntity create(TagEntity tag) {
        tag.setType(valueOrDefault(tag.getType(), DEFAULT_TARGET_TYPE));
        tag.setTenantId(valueOrDefault(tag.getTenantId(), DEFAULT_TENANT_ID));
        tagMapper.insert(tag);
        return tag;
    }

    @Transactional
    public TagEntity rename(String tagId, String name) {
        return rename(UserContextHolder.currentTenantId(), tagId, name);
    }

    @Transactional
    public TagEntity rename(String tenantId, String tagId, String name) {
        TagEntity tag = require(tenantId, tagId);
        tag.setName(name);
        tagMapper.update(
                tag,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TagEntity>()
                        .eq(TagEntity::getTenantId, tag.getTenantId())
                        .eq(TagEntity::getId, tag.getId()));
        return tag;
    }

    @Transactional
    public void delete(String tagId) {
        delete(UserContextHolder.currentTenantId(), tagId);
    }

    @Transactional
    public void delete(String tenantId, String tagId) {
        TagEntity tag = require(tenantId, tagId);
        bindingMapper.delete(new LambdaQueryWrapper<TagBindingEntity>()
                .eq(TagBindingEntity::getTenantId, tag.getTenantId())
                .eq(TagBindingEntity::getTagId, tagId));
        tagMapper.delete(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getTenantId, tag.getTenantId())
                .eq(TagEntity::getId, tagId));
    }

    // ---------------------------------------------------------------- bindings

    public List<TagBindingEntity> bindings(String targetId, String targetType) {
        return bindings(UserContextHolder.currentTenantId(), targetId, targetType);
    }

    public List<TagBindingEntity> bindings(String tenantId, String targetId, String targetType) {
        requireOwnedTarget(tenantId, targetId, targetType);
        return bindingMapper.selectList(new LambdaQueryWrapper<TagBindingEntity>()
                .eq(TagBindingEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(TagBindingEntity::getTargetId, targetId)
                .eq(targetType != null, TagBindingEntity::getTargetType, targetType));
    }

    @Transactional
    public void bind(String tagId, String targetId, String targetType) {
        bind(UserContextHolder.currentTenantId(), tagId, targetId, targetType);
    }

    @Transactional
    public void bind(String tenantId, String tagId, String targetId, String targetType) {
        TagEntity tag = require(tenantId, tagId);
        String resolvedTargetType = valueOrDefault(targetType, DEFAULT_TARGET_TYPE);
        requireOwnedTarget(tenantId, targetId, resolvedTargetType);
        TagBindingEntity existing = bindingMapper.selectOne(new LambdaQueryWrapper<TagBindingEntity>()
                .eq(TagBindingEntity::getTenantId, tag.getTenantId())
                .eq(TagBindingEntity::getTagId, tagId)
                .eq(TagBindingEntity::getTargetId, targetId)
                .eq(TagBindingEntity::getTargetType, resolvedTargetType)
                .last("LIMIT 1"));
        if (existing != null) return;
        TagBindingEntity binding = new TagBindingEntity();
        binding.setTenantId(tag.getTenantId());
        binding.setTagId(tagId);
        binding.setTargetId(targetId);
        binding.setTargetType(resolvedTargetType);
        bindingMapper.insert(binding);
    }

    @Transactional
    public void unbind(String tagId, String targetId) {
        unbind(UserContextHolder.currentTenantId(), tagId, targetId);
    }

    @Transactional
    public void unbind(String tenantId, String tagId, String targetId) {
        TagEntity tag = require(tenantId, tagId);
        bindingMapper.delete(new LambdaQueryWrapper<TagBindingEntity>()
                .eq(TagBindingEntity::getTenantId, tag.getTenantId())
                .eq(TagBindingEntity::getTagId, tagId)
                .eq(TagBindingEntity::getTargetId, targetId));
    }

    private void requireOwnedTarget(String tenantId, String targetId, String targetType) {
        String resolvedTenant = valueOrDefault(tenantId, DEFAULT_TENANT_ID);
        String resolvedType = valueOrDefault(targetType, DEFAULT_TARGET_TYPE);
        if ("knowledge".equalsIgnoreCase(resolvedType) || "dataset".equalsIgnoreCase(resolvedType)) {
            if (datasetOwnershipResolver != null) {
                datasetOwnershipResolver.requireOwned(resolvedTenant, targetId);
            }
            return;
        }
        if (appMapper == null) return;
        AppEntity app = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, resolvedTenant)
                .eq(AppEntity::getId, targetId)
                .last("LIMIT 1"));
        if (app == null) throw new PlatformException("tag_target_not_found", "Tag target not found", null);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
