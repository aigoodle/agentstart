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
 * General-purpose recursive separator + token-size chunking with overlap.
 */
public class NaiveChunker implements Chunker {

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.NAIVE;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        TextSplitSettings splitSettings =
                new TextSplitSettings(rule.getSeparators(), rule.getChunkTokens(), rule.getOverlapTokens());
        List<String> pieces = RecursiveSplitter.split(text, splitSettings);
        List<Chunk> chunks = new ArrayList<>();
        int position = 0;
        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }
            chunks.add(new Chunk(piece, position++, new HashMap<>(baseMetadata)));
        }
        return chunks;
    }
}
