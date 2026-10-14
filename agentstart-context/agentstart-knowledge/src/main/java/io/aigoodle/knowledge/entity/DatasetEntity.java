package io.aigoodle.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.knowledge.enums.IndexingTechnique;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A knowledge base: a named collection of documents indexed with one embedding
 * model and a fixed processing / retrieval configuration.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_dataset")
public class DatasetEntity extends BaseEntity {

    private String name;

    private String description;

    /** Model id (from the model module) used to embed chunks and queries. */
    private String embeddingModelId;

    private IndexingTechnique indexingTechnique;

    /** JSON of {@link io.aigoodle.knowledge.config.ProcessRule}. */
    private String processRuleJson;

    /** JSON of {@link io.aigoodle.knowledge.config.RetrievalConfig}. */
    private String retrievalConfigJson;

    /** Optional vector store binding name; null = in-memory default. */
    private String vectorStore;

    /** Currently queryable blue/green index; null preserves legacy unversioned data. */
    private String activeIndexVersionId;

    private Integer documentCount;

    private Integer segmentCount;
}
