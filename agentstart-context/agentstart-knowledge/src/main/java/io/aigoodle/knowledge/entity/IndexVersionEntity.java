package io.aigoodle.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.knowledge.enums.IndexVersionStatus;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable indexing configuration plus its blue/green lifecycle state. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_index_versions")
public class IndexVersionEntity extends BaseEntity {
    private String datasetId;
    private String version;
    private String embeddingModelVersion;
    private String chunkingRuleVersion;
    private String contentChecksum;
    private IndexVersionStatus status;
    private String errorMessage;
    private Integer documentCount;
    private Integer segmentCount;
}
