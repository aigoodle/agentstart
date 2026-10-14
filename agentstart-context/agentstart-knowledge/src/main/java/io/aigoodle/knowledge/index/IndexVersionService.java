package io.aigoodle.knowledge.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.knowledge.entity.DatasetEntity;
import io.aigoodle.knowledge.entity.IndexVersionEntity;
import io.aigoodle.knowledge.enums.IndexVersionStatus;
import io.aigoodle.knowledge.mapper.DatasetMapper;
import io.aigoodle.knowledge.mapper.IndexVersionMapper;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Owns blue/green index creation, atomic activation and rollback. */
public class IndexVersionService {
    private final IndexVersionMapper versionMapper;
    private final DatasetMapper datasetMapper;

    public IndexVersionService(IndexVersionMapper versionMapper, DatasetMapper datasetMapper) {
        this.versionMapper = versionMapper;
        this.datasetMapper = datasetMapper;
    }

    public IndexVersionEntity beginRebuild(
            String tenantId,
            String datasetId,
            String version,
            String embeddingModelVersion,
            String chunkingRuleVersion,
            String contentChecksum) {
        IndexVersionEntity entity = new IndexVersionEntity();
        entity.setTenantId(tenantId);
        entity.setDatasetId(datasetId);
        entity.setVersion(version);
        entity.setEmbeddingModelVersion(embeddingModelVersion);
        entity.setChunkingRuleVersion(chunkingRuleVersion);
        entity.setContentChecksum(contentChecksum);
        entity.setStatus(IndexVersionStatus.REBUILDING);
        entity.setDocumentCount(0);
        entity.setSegmentCount(0);
        versionMapper.insert(entity);
        return entity;
    }

    @Transactional
    public void activate(String tenantId, String datasetId, String versionId) {
        DatasetEntity dataset = datasetMapper.selectOne(new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getTenantId, tenantId)
                .eq(DatasetEntity::getId, datasetId)
                .last("FOR UPDATE"));
        if (dataset == null) throw new IllegalArgumentException("Dataset not found: " + datasetId);
        IndexVersionEntity target = require(tenantId, datasetId, versionId);
        if (target.getStatus() != IndexVersionStatus.REBUILDING && target.getStatus() != IndexVersionStatus.RETIRED) {
            throw new IllegalStateException("Only rebuilding or retired indexes can be activated");
        }
        versionMapper.update(
                null,
                new LambdaUpdateWrapper<IndexVersionEntity>()
                        .set(IndexVersionEntity::getStatus, IndexVersionStatus.RETIRED)
                        .eq(IndexVersionEntity::getTenantId, tenantId)
                        .eq(IndexVersionEntity::getDatasetId, datasetId)
                        .eq(IndexVersionEntity::getStatus, IndexVersionStatus.ACTIVE));
        versionMapper.update(
                null,
                new LambdaUpdateWrapper<IndexVersionEntity>()
                        .set(IndexVersionEntity::getStatus, IndexVersionStatus.ACTIVE)
                        .eq(IndexVersionEntity::getTenantId, tenantId)
                        .eq(IndexVersionEntity::getId, versionId));
        datasetMapper.update(
                null,
                new LambdaUpdateWrapper<DatasetEntity>()
                        .set(DatasetEntity::getActiveIndexVersionId, versionId)
                        .eq(DatasetEntity::getTenantId, tenantId)
                        .eq(DatasetEntity::getId, datasetId));
    }

    public void markFailed(String tenantId, String datasetId, String versionId, String error) {
        require(tenantId, datasetId, versionId);
        versionMapper.update(
                null,
                new LambdaUpdateWrapper<IndexVersionEntity>()
                        .set(IndexVersionEntity::getStatus, IndexVersionStatus.FAILED)
                        .set(IndexVersionEntity::getErrorMessage, error)
                        .eq(IndexVersionEntity::getTenantId, tenantId)
                        .eq(IndexVersionEntity::getId, versionId));
    }

    public List<IndexVersionEntity> list(String tenantId, String datasetId) {
        return versionMapper.selectList(new LambdaQueryWrapper<IndexVersionEntity>()
                .eq(IndexVersionEntity::getTenantId, tenantId)
                .eq(IndexVersionEntity::getDatasetId, datasetId)
                .orderByDesc(IndexVersionEntity::getCreatedAt));
    }

    private IndexVersionEntity require(String tenantId, String datasetId, String versionId) {
        IndexVersionEntity entity = versionMapper.selectOne(new LambdaQueryWrapper<IndexVersionEntity>()
                .eq(IndexVersionEntity::getTenantId, tenantId)
                .eq(IndexVersionEntity::getDatasetId, datasetId)
                .eq(IndexVersionEntity::getId, versionId)
                .last("LIMIT 1"));
        if (entity == null) throw new IllegalArgumentException("Index version not found: " + versionId);
        return entity;
    }
}
