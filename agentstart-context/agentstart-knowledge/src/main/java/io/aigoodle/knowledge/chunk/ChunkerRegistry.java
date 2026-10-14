package io.aigoodle.knowledge.chunk;

import io.aigoodle.knowledge.chunk.template.MarkdownChunker;
import io.aigoodle.knowledge.chunk.template.NaiveChunker;
import io.aigoodle.knowledge.chunk.template.OneChunker;
import io.aigoodle.knowledge.chunk.template.ParentChildChunker;
import io.aigoodle.knowledge.chunk.template.QaChunker;
import io.aigoodle.knowledge.chunk.template.StructureAwareChunker;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link Chunker} for a {@link ChunkingTemplate}. Custom chunkers
 * (provided as Spring beans) override built-ins for the same template.
 */
public class ChunkerRegistry {

    private final Map<ChunkingTemplate, Chunker> chunkers = new EnumMap<>(ChunkingTemplate.class);

    public ChunkerRegistry(List<Chunker> custom) {
        chunkers.put(ChunkingTemplate.NAIVE, new NaiveChunker());
        chunkers.put(ChunkingTemplate.ONE, new OneChunker());
        chunkers.put(ChunkingTemplate.MARKDOWN, new MarkdownChunker());
        chunkers.put(ChunkingTemplate.QA, new QaChunker());
        chunkers.put(ChunkingTemplate.PARENT_CHILD, new ParentChildChunker());
        chunkers.put(ChunkingTemplate.STRUCTURE_AWARE, new StructureAwareChunker());
        if (custom != null) {
            for (Chunker c : custom) {
                chunkers.put(c.template(), c);
            }
        }
    }

    public Chunker get(ChunkingTemplate template) {
        return chunkers.getOrDefault(template, chunkers.get(ChunkingTemplate.NAIVE));
    }
}
