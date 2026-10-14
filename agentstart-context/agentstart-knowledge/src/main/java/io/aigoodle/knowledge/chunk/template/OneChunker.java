package io.aigoodle.knowledge.chunk.template;

import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.Chunker;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-document-as-one-chunk. Useful for short documents (FAQ answers, snippets).
 */
public class OneChunker implements Chunker {

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.ONE;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(new Chunk(text.strip(), 0, new HashMap<>(baseMetadata)));
    }
}
