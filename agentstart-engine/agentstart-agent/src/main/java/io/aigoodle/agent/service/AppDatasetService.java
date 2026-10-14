package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.service.DatasetService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages knowledge bases attached to an application model configuration.
 *
 * <p>Every read and write passes through the same ownership resolver, so stale or
 * manually edited sidecars cannot expose datasets from another tenant.</p>
 */
public class AppDatasetService {

    private final AppMapper appMapper;
    private final AppModelConfigService modelConfigService;
    private final OwnedDatasetResolver datasetResolver;

    public AppDatasetService(
            AppMapper appMapper, AppModelConfigService modelConfigService, DatasetService datasetService) {
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
        this.datasetResolver = new OwnedDatasetResolver(datasetService);
    }

    /** Return hydrated summaries for the datasets currently attached to an app. */
    public List<AttachedDatasetView> list(String appId) {
        AppEntity application = requireApplication(appId);
        return hydrate(attachedDatasetIds(application), application.getTenantId());
    }

    public List<AttachedDatasetView> list(String tenantId, String appId) {
        AppEntity application = requireApplication(tenantId, appId);
        return hydrate(attachedDatasetIds(application), application.getTenantId());
    }

    /** Add datasets while retaining the existing order and removing duplicates. */
    @Transactional
    public List<AttachedDatasetView> attach(String appId, List<String> datasetIds) {
        AppEntity application = requireApplication(appId);
        Set<String> combinedIds = new LinkedHashSet<>(attachedDatasetIds(application));
        combinedIds.addAll(validateDatasetIds(datasetIds, application.getTenantId()));
        persistAttachedIds(application, combinedIds);
        return hydrate(combinedIds, application.getTenantId());
    }

    @Transactional
    public List<AttachedDatasetView> attach(String tenantId, String appId, List<String> datasetIds) {
        AppEntity application = requireApplication(tenantId, appId);
        Set<String> combinedIds = new LinkedHashSet<>(attachedDatasetIds(application));
        combinedIds.addAll(validateDatasetIds(datasetIds, application.getTenantId()));
        persistAttachedIds(application, combinedIds);
        return hydrate(combinedIds, application.getTenantId());
    }

    /** Remove one dataset id. Missing attachments are treated as an idempotent no-op. */
    @Transactional
    public List<AttachedDatasetView> detach(String appId, String datasetId) {
        AppEntity application = requireApplication(appId);
        Set<String> remainingIds = new LinkedHashSet<>(attachedDatasetIds(application));
        remainingIds.remove(datasetId);
        persistAttachedIds(application, remainingIds);
        return hydrate(remainingIds, application.getTenantId());
    }

    @Transactional
    public List<AttachedDatasetView> detach(String tenantId, String appId, String datasetId) {
        AppEntity application = requireApplication(tenantId, appId);
        Set<String> remainingIds = new LinkedHashSet<>(attachedDatasetIds(application));
        remainingIds.remove(datasetId);
        persistAttachedIds(application, remainingIds);
        return hydrate(remainingIds, application.getTenantId());
    }

    /** Replace the complete attachment set in the supplied order. */
    @Transactional
    public List<AttachedDatasetView> replace(String appId, List<String> datasetIds) {
        AppEntity application = requireApplication(appId);
        Set<String> replacementIds = validateDatasetIds(datasetIds, application.getTenantId());
        persistAttachedIds(application, replacementIds);
        return hydrate(replacementIds, application.getTenantId());
    }

    @Transactional
    public List<AttachedDatasetView> replace(String tenantId, String appId, List<String> datasetIds) {
        AppEntity application = requireApplication(tenantId, appId);
        Set<String> replacementIds = validateDatasetIds(datasetIds, application.getTenantId());
        persistAttachedIds(application, replacementIds);
        return hydrate(replacementIds, application.getTenantId());
    }

    private Set<String> validateDatasetIds(List<String> datasetIds, String tenantId) {
        Set<String> validatedIds = new LinkedHashSet<>();
        for (String datasetId : datasetIds == null ? List.<String>of() : datasetIds) {
            if (datasetId == null || datasetId.isBlank()) {
                continue;
            }
            datasetResolver.requireOwned(datasetId, tenantId);
            validatedIds.add(datasetId);
        }
        return validatedIds;
    }

    private List<AttachedDatasetView> hydrate(Iterable<String> datasetIds, String tenantId) {
        List<AttachedDatasetView> datasets = new ArrayList<>();
        for (String datasetId : datasetIds) {
            datasets.add(AttachedDatasetView.from(datasetResolver.requireOwned(datasetId, tenantId)));
        }
        return datasets;
    }

    private List<String> attachedDatasetIds(AppEntity application) {
        AppModelConfigEntity configuration =
                modelConfigService.findByAppId(application.getTenantId(), application.getId());
        return configuration == null ? List.of() : JsonUtils.parseList(configuration.getDatasetIdsJson(), String.class);
    }

    private void persistAttachedIds(AppEntity application, Iterable<String> datasetIds) {
        List<String> orderedIds = new ArrayList<>();
        datasetIds.forEach(orderedIds::add);
        AppModelConfigEntity patch = new AppModelConfigEntity();
        patch.setDatasetIdsJson(JsonUtils.toJson(orderedIds));
        modelConfigService.upsert(
                new AppModelConfigRegistration(application.getId(), application.getTenantId(), patch));
    }

    private AppEntity requireApplication(String appId) {
        return requireApplication(UserContextHolder.currentTenantId(), appId);
    }

    private AppEntity requireApplication(String tenantId, String appId) {
        AppEntity application = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId == null ? "default" : tenantId)
                .eq(AppEntity::getId, appId)
                .last("LIMIT 1"));
        if (application == null) {
            throw new PlatformException("app_not_found", "Application not found", null);
        }
        return application;
    }
}
