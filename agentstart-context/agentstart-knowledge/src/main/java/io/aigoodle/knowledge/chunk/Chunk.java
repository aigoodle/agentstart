package io.aigoodle.knowledge.chunk;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * A produced chunk before it is persisted/embedded. {@code parentContent} is set by
 * the parent-child template so the larger context can be stored alongside the small
 * child chunk that is actually matched.
 */
@Data
public class Chunk {

    private final String content;
    private final int position;
    private final Map<String, Object> metadata;
    private String parentContent;

    public Chunk(String content, int position) {
        this(content, position, new HashMap<>());
    }

    public Chunk(String content, int position, Map<String, Object> metadata) {
        this.content = content;
        this.position = position;
        this.metadata = metadata == null ? new HashMap<>() : metadata;
    }

    public int tokenCount() {
        return TokenCounter.count(content);
    }
}
