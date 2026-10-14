package io.aigoodle.knowledge.chunk.template;

import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.Chunker;
import io.aigoodle.knowledge.chunk.RecursiveSplitter;
import io.aigoodle.knowledge.chunk.TextSplitSettings;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parent-child / small-to-big chunking: the document is split into large parent
 * blocks, each parent into small child chunks. Children are what gets embedded and
 * matched (precise recall) while the parent text is carried along to give the LLM
 * full context. Mirrors Dify's hierarchical mode.
 */
public class ParentChildChunker implements Chunker {

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.PARENT_CHILD;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        TextSplitSettings parentSettings =
                TextSplitSettings.withoutOverlap(rule.getSeparators(), rule.getParentChunkTokens());
        List<String> parentChunks = rule.getParentMode() == ProcessRule.ParentMode.FULL_DOC
                ? List.of(text)
                : RecursiveSplitter.split(text, parentSettings);
        TextSplitSettings childSettings =
                new TextSplitSettings(rule.getSeparators(), rule.getChunkTokens(), rule.getOverlapTokens());
        List<Chunk> childChunks = new ArrayList<>();
        int position = 0;
        int parentIndex = 0;
        for (String parentChunk : parentChunks) {
            List<String> childPieces = RecursiveSplitter.split(parentChunk, childSettings);
            for (String childPiece : childPieces) {
                if (childPiece.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>(baseMetadata);
                metadata.put("parentIndex", parentIndex);
                Chunk childChunk = new Chunk(childPiece, position++, metadata);
                childChunk.setParentContent(parentChunk);
                childChunks.add(childChunk);
            }
            parentIndex++;
        }
        return childChunks;
    }
}
