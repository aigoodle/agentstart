package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppAnnotationEntity;
import io.aigoodle.agent.mapper.AppAnnotationMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the curated question-and-answer pairs owned by an application.
 * Application ownership is part of every mutation so a route scoped to one
 * application cannot accidentally modify another application's annotation.
 */
public class AppAnnotationService {

    private final AppAnnotationMapper annotationMapper;

    public AppAnnotationService(AppAnnotationMapper annotationMapper) {
        this.annotationMapper = annotationMapper;
    }

    public List<AppAnnotationEntity> list(String appId) {
        return list(UserContextHolder.currentTenantId(), appId);
    }

    public List<AppAnnotationEntity> list(String tenantId, String appId) {
        return annotationMapper.selectList(new LambdaQueryWrapper<AppAnnotationEntity>()
                .eq(AppAnnotationEntity::getTenantId, normalizedTenant(tenantId))
                .eq(AppAnnotationEntity::getAppId, appId)
                .orderByDesc(AppAnnotationEntity::getUpdatedAt));
    }

    public AppAnnotationEntity require(String appId, String annotationId) {
        return require(UserContextHolder.currentTenantId(), appId, annotationId);
    }

    public AppAnnotationEntity require(String tenantId, String appId, String annotationId) {
        AppAnnotationEntity annotation = annotationMapper.selectOne(new LambdaQueryWrapper<AppAnnotationEntity>()
                .eq(AppAnnotationEntity::getTenantId, normalizedTenant(tenantId))
                .eq(AppAnnotationEntity::getId, annotationId)
                .eq(AppAnnotationEntity::getAppId, appId)
                .last("LIMIT 1"));
        if (annotation == null)
            throw new PlatformException("annotation_not_found", "Annotation not found: " + annotationId, null);
        return annotation;
    }

    @Transactional
    public AppAnnotationEntity create(AppAnnotationEntity annotation) {
        if (annotation.getHitCount() == null) {
            annotation.setHitCount(0);
        }
        if (annotation.getEnabled() == null) {
            annotation.setEnabled(true);
        }
        annotationMapper.insert(annotation);
        return annotation;
    }

    @Transactional
    public AppAnnotationEntity create(String tenantId, String appId, AppAnnotationEntity annotation) {
        annotation.setId(null);
        annotation.setTenantId(normalizedTenant(tenantId));
        annotation.setAppId(appId);
        return create(annotation);
    }

    @Transactional
    public AppAnnotationEntity update(String appId, String annotationId, AppAnnotationEntity updates) {
        return update(UserContextHolder.currentTenantId(), appId, annotationId, updates);
    }

    @Transactional
    public AppAnnotationEntity update(String tenantId, String appId, String annotationId, AppAnnotationEntity updates) {
        AppAnnotationEntity annotation = require(tenantId, appId, annotationId);
        if (updates.getQuestion() != null) annotation.setQuestion(updates.getQuestion());
        if (updates.getContent() != null) annotation.setContent(updates.getContent());
        if (updates.getEnabled() != null) annotation.setEnabled(updates.getEnabled());
        annotationMapper.update(
                annotation,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppAnnotationEntity>()
                        .eq(AppAnnotationEntity::getTenantId, annotation.getTenantId())
                        .eq(AppAnnotationEntity::getAppId, appId)
                        .eq(AppAnnotationEntity::getId, annotationId));
        return annotation;
    }

    @Transactional
    public void delete(String appId, String annotationId) {
        delete(UserContextHolder.currentTenantId(), appId, annotationId);
    }

    @Transactional
    public void delete(String tenantId, String appId, String annotationId) {
        AppAnnotationEntity annotation = require(tenantId, appId, annotationId);
        annotationMapper.delete(new LambdaQueryWrapper<AppAnnotationEntity>()
                .eq(AppAnnotationEntity::getTenantId, annotation.getTenantId())
                .eq(AppAnnotationEntity::getAppId, appId)
                .eq(AppAnnotationEntity::getId, annotationId));
    }

    /** Increments the number of times an annotation has served a response. */
    @Transactional
    public void recordHit(String appId, String annotationId) {
        recordHit(UserContextHolder.currentTenantId(), appId, annotationId);
    }

    @Transactional
    public void recordHit(String tenantId, String appId, String annotationId) {
        AppAnnotationEntity annotation = require(tenantId, appId, annotationId);
        annotation.setHitCount((annotation.getHitCount() == null ? 0 : annotation.getHitCount()) + 1);
        annotationMapper.update(
                annotation,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppAnnotationEntity>()
                        .eq(AppAnnotationEntity::getTenantId, annotation.getTenantId())
                        .eq(AppAnnotationEntity::getAppId, appId)
                        .eq(AppAnnotationEntity::getId, annotationId));
    }

    private static String normalizedTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }
}
