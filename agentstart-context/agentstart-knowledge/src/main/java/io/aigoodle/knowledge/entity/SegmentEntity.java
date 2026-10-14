package io.aigoodle.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A single chunk: the unit that gets embedded, stored in the vector store and
 * returned by vector retrieval. Metadata is persisted for attribution and filtering.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_document_segments")
public class SegmentEntity extends BaseEntity {

    private String datasetId;

    private String documentId;

    /** Ordinal position of this chunk within its document. */
    private Integer position;

    private String content;

    private Integer tokenCount;

    /** JSON metadata (heading path, page, source name, custom fields). */
    private String metadataJson;

    /** Parent segment id for PARENT_CHILD chunking; null otherwise. */
    private String parentId;

    /** Id used as the vector store document id, so we can delete/update precisely. */
    private String vectorId;

    /** Index generation owning this segment; null denotes a legacy index. */
    private String indexVersionId;

    private Boolean enabled;

    /** Content hash for dedup / change detection. */
    private String hash;
}
