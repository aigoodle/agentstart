package io.aigoodle.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.config.RetrievalConfig;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.mapper.DatasetMapper;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for datasets (knowledge bases) and access to their parsed configuration.
 */
public class DatasetService {

    private final DatasetMapper datasetMapper;

    public DatasetService(DatasetMapper datasetMapper) {
        this.datasetMapper = datasetMapper;
    }

    @Transactional
    public DatasetEntity create(CreateDatasetRequest request) {
        DatasetEntity dataset = DatasetDefinitionFactory.create(request);
        datasetMapper.insert(dataset);
        return dataset;
    }

    public DatasetEntity get(String id) {
        return get(UserContextHolder.currentTenantId(), id);
    }

    public DatasetEntity get(String tenantId, String id) {
        return datasetMapper.selectOne(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getTenantId, effectiveTenant(tenantId))
                .eq(DatasetEntity::getId, id)
                .last("LIMIT 1"));
    }

    public DatasetEntity require(String id) {
        return require(UserContextHolder.currentTenantId(), id);
    }

    public DatasetEntity require(String tenantId, String id) {
        DatasetEntity dataset = datasetMapper.selectOne(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getTenantId, effectiveTenant(tenantId))
                .eq(DatasetEntity::getId, id)
                .last("LIMIT 1"));
        if (dataset == null) {
            throw new PlatformException("dataset_not_found", "Dataset not found", null);
        }
        return dataset;
    }

    public List<DatasetEntity> list(String tenantId) {
        return datasetMapper.selectList(
                new LambdaQueryWrapper<DatasetEntity>().eq(DatasetEntity::getTenantId, effectiveTenant(tenantId)));
    }

    @Transactional
    public void delete(String id) {
        delete(UserContextHolder.currentTenantId(), id);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        require(tenantId, id);
        datasetMapper.delete(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getTenantId, effectiveTenant(tenantId))
                .eq(DatasetEntity::getId, id));
    }

    /**
     * Edit dataset metadata + retrieval/chunk settings. Missing fields are left
     * untouched. This is the shape of Dify's dataset settings dialog: rename,
     * tweak topK, swap in a different embedding model, etc.
     */
    @Transactional
    public DatasetEntity update(String id, UpdateDatasetRequest patch) {
        return update(UserContextHolder.currentTenantId(), id, patch);
    }

    @Transactional
    public DatasetEntity update(String tenantId, String id, UpdateDatasetRequest patch) {
        DatasetEntity dataset = require(tenantId, id);
        DatasetPatchApplicator.apply(dataset, patch);
        updateOwned(dataset);
        return dataset;
    }

    public ProcessRule processRule(DatasetEntity dataset) {
        ProcessRule processRule = JsonUtils.parse(dataset.getProcessRuleJson(), ProcessRule.class);
        return processRule == null ? ProcessRule.naive() : processRule;
    }

    public RetrievalConfig retrievalConfig(DatasetEntity dataset) {
        RetrievalConfig retrievalConfig = JsonUtils.parse(dataset.getRetrievalConfigJson(), RetrievalConfig.class);
        return retrievalConfig == null ? RetrievalConfig.vector() : retrievalConfig;
    }

    public void applyCountChange(DatasetEntity dataset, DatasetCountChange change) {
        dataset.setDocumentCount(Math.max(0, valueOrZero(dataset.getDocumentCount()) + change.documents()));
        dataset.setSegmentCount(Math.max(0, valueOrZero(dataset.getSegmentCount()) + change.segments()));
        updateOwned(dataset);
    }

    /** @deprecated Use {@link #applyCountChange(DatasetEntity, DatasetCountChange)}. */
    @Deprecated(forRemoval = false)
    public void updateCounts(DatasetEntity dataset, int deltaDocuments, int deltaSegments) {
        applyCountChange(dataset, new DatasetCountChange(deltaDocuments, deltaSegments));
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void updateOwned(DatasetEntity dataset) {
        datasetMapper.update(
                dataset,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DatasetEntity>()
                        .eq(DatasetEntity::getTenantId, effectiveTenant(dataset.getTenantId()))
                        .eq(DatasetEntity::getId, dataset.getId()));
    }

    private static String effectiveTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }
}
