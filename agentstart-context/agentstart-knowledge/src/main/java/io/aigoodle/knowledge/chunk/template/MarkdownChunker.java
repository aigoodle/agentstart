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
 * Splits on Markdown headings, keeping the heading path (e.g. "H1 > H2") as
 * metadata and prepending it to each chunk for better retrieval context. Oversized
 * sections are further split with the recursive splitter.
 */
public class MarkdownChunker implements Chunker {

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.MARKDOWN;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        List<Chunk> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        String[] headingPath = new String[7]; // index = heading level 1..6
        StringBuilder body = new StringBuilder();
        int nextPosition = 0;

        for (String line : lines) {
            int level = headingLevel(line);
            if (level > 0) {
                nextPosition = appendSectionChunks(body, headingPath, rule, baseMetadata, chunks, nextPosition);
                for (int deeperLevel = level + 1; deeperLevel < headingPath.length; deeperLevel++) {
                    headingPath[deeperLevel] = null;
                }
                headingPath[level] = line.replaceFirst("^#{1,6}\\s*", "").strip();
            } else {
                body.append(line).append("\n");
            }
        }
        appendSectionChunks(body, headingPath, rule, baseMetadata, chunks, nextPosition);
        return chunks;
    }

    private int appendSectionChunks(
            StringBuilder sectionBody,
            String[] headingPath,
            ProcessRule rule,
            Map<String, Object> baseMetadata,
            List<Chunk> chunks,
            int nextPosition) {
        String sectionContent = sectionBody.toString().strip();
        sectionBody.setLength(0);
        if (sectionContent.isBlank()) {
            return nextPosition;
        }
        String heading = joinHeadingPath(headingPath);
        String headingPrefix = heading.isEmpty() ? "" : heading + "\n";
        TextSplitSettings splitSettings =
                new TextSplitSettings(rule.getSeparators(), rule.getChunkTokens(), rule.getOverlapTokens());
        for (String piece : RecursiveSplitter.split(sectionContent, splitSettings)) {
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            if (!heading.isEmpty()) {
                metadata.put("heading", heading);
            }
            chunks.add(new Chunk(headingPrefix + piece, nextPosition++, metadata));
        }
        return nextPosition;
    }

    private static int headingLevel(String line) {
        int headingMarkerCount = 0;
        while (headingMarkerCount < line.length() && line.charAt(headingMarkerCount) == '#') {
            headingMarkerCount++;
        }
        boolean validHeading = headingMarkerCount >= 1
                && headingMarkerCount <= 6
                && headingMarkerCount < line.length()
                && line.charAt(headingMarkerCount) == ' ';
        return validHeading ? headingMarkerCount : 0;
    }

    private static String joinHeadingPath(String[] headingPath) {
        StringBuilder joinedPath = new StringBuilder();
        for (int level = 1; level < headingPath.length; level++) {
            if (headingPath[level] != null && !headingPath[level].isBlank()) {
                if (joinedPath.length() > 0) {
                    joinedPath.append(" > ");
                }
                joinedPath.append(headingPath[level]);
            }
        }
        return joinedPath.toString();
    }
}
