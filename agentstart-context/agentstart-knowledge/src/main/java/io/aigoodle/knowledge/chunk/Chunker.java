package io.aigoodle.knowledge.chunk;

import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.List;
import java.util.Map;

/**
 * Splits a cleaned document text into {@link Chunk}s according to a {@link ProcessRule}.
 * Implementations are selected by {@link #template()}.
 */
public interface Chunker {

    ChunkingTemplate template();

    List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata);
}
