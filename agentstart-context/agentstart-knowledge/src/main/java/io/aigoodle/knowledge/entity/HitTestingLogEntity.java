package io.aigoodle.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One row per hit-test invocation — records the query, the retrieval params, the
 * results JSON (top-N segments + scores), and how long the call took. Powers the
 * "recent queries" panel of the dataset detail page so users can eyeball retrieval
 * quality drift after they change chunk rules or swap embedding models.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_dataset_query")
public class HitTestingLogEntity extends BaseEntity {

    private String datasetId;

    private String query;

    private String method;

    private Integer topK;

    /** JSON array of retrieved segments (id, position, score, content preview). */
    private String resultsJson;

    private Integer hitCount;

    private Integer latencyMs;
}
