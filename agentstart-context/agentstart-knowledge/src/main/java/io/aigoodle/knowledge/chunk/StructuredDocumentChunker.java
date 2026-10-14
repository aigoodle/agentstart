package io.aigoodle.knowledge.chunk;

import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts parsed semantic blocks into chunks without losing source attribution. */
public class StructuredDocumentChunker {
    private final ChunkerRegistry registry;

    public StructuredDocumentChunker(ChunkerRegistry registry) {
        this.registry = registry;
    }

    public List<Chunk> chunk(ParsedDocument document, ProcessRule rule, Map<String, Object> baseMetadata) {
        if (document == null) return List.of();
        if (rule.getTemplate() != ChunkingTemplate.STRUCTURE_AWARE) {
            return registry.get(rule.getTemplate())
                    .chunk(TextCleaner.clean(document.text(), rule), rule, documentMetadata(document, baseMetadata));
        }
        List<Chunk> chunks = new ArrayList<>();
        TextSplitSettings splitSettings =
                new TextSplitSettings(rule.getSeparators(), rule.getChunkTokens(), rule.getOverlapTokens());
        String activeHeading = "";
        for (DocumentBlock block : document.getBlocks()) {
            if (block.getType() == BlockType.HEADING || block.getType() == BlockType.TITLE) {
                activeHeading = block.getHeadingPath() == null ? block.getText() : block.getHeadingPath();
                continue;
            }
            String cleaned = TextCleaner.clean(block.getText(), rule);
            if (cleaned.isBlank()) continue;
            String heading =
                    block.getHeadingPath() == null || block.getHeadingPath().isBlank()
                            ? activeHeading
                            : block.getHeadingPath();
            boolean protectedBlock = rule.isProtectStructuredBlocks()
                    && (block.getType() == BlockType.TABLE || block.getType() == BlockType.CODE)
                    && TokenCounter.count(cleaned) <= rule.getChunkTokens();
            List<String> pieces = protectedBlock ? List.of(cleaned) : RecursiveSplitter.split(cleaned, splitSettings);
            for (String piece : pieces) {
                Map<String, Object> metadata = documentMetadata(document, baseMetadata);
                metadata.putAll(block.getMetadata());
                metadata.put("blockIndex", block.getIndex());
                metadata.put("blockType", block.getType().name().toLowerCase());
                if (block.getPage() != null) metadata.put("page", block.getPage());
                if (heading != null && !heading.isBlank()) metadata.put("heading", heading);
                String content = rule.isIncludeHeadingContext() && heading != null && !heading.isBlank()
                        ? heading + "\n" + piece
                        : piece;
                chunks.add(new Chunk(content, chunks.size(), metadata));
            }
        }
        return chunks;
    }

    private static Map<String, Object> documentMetadata(ParsedDocument document, Map<String, Object> baseMetadata) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.putAll(document.getMetadata());
        if (document.getParser() != null) metadata.put("parser", document.getParser());
        if (document.getMediaType() != null) metadata.put("mediaType", document.getMediaType());
        metadata.put("pageCount", document.pageCount());
        if (document.getTitle() != null) metadata.put("documentTitle", document.getTitle());
        metadata.entrySet().removeIf(entry -> entry.getValue() == null);
        return metadata;
    }
}
