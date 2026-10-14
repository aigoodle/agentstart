package io.aigoodle.knowledge.chunk.template;

import io.aigoodle.knowledge.chunk.Chunk;
import io.aigoodle.knowledge.chunk.Chunker;
import io.aigoodle.knowledge.chunk.RecursiveSplitter;
import io.aigoodle.knowledge.chunk.TextSplitSettings;
import io.aigoodle.knowledge.chunk.TokenCounter;
import io.aigoodle.knowledge.config.ProcessRule;
import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure-first splitter inspired by RAGFlow's title chunker. It protects fenced
 * code and Markdown tables, tracks the heading hierarchy and only applies recursive
 * token splitting inside oversized semantic sections.
 */
public class StructureAwareChunker implements Chunker {

    @Override
    public ChunkingTemplate template() {
        return ChunkingTemplate.STRUCTURE_AWARE;
    }

    @Override
    public List<Chunk> chunk(String text, ProcessRule rule, Map<String, Object> baseMetadata) {
        List<Section> sections = parseSections(text == null ? "" : text, rule.isProtectStructuredBlocks());
        List<Chunk> chunks = new ArrayList<>();
        TextSplitSettings settings =
                new TextSplitSettings(rule.getSeparators(), rule.getChunkTokens(), rule.getOverlapTokens());
        int position = 0;
        for (Section section : sections) {
            if (section.body().isBlank()) {
                continue;
            }
            List<String> pieces =
                    section.protectedBlock() && TokenCounter.count(section.body()) <= rule.getChunkTokens()
                            ? List.of(section.body())
                            : RecursiveSplitter.split(section.body(), settings);
            for (String piece : pieces) {
                if (piece.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>(baseMetadata);
                if (!section.headingPath().isBlank()) {
                    metadata.put("heading", section.headingPath());
                }
                metadata.put("blockType", section.blockType());
                String content =
                        rule.isIncludeHeadingContext() && !section.headingPath().isBlank()
                                ? section.headingPath() + "\n" + piece.strip()
                                : piece.strip();
                chunks.add(new Chunk(content, position++, metadata));
            }
        }
        return chunks;
    }

    private static List<Section> parseSections(String text, boolean protectBlocks) {
        List<Section> sections = new ArrayList<>();
        String[] headings = new String[7];
        StringBuilder body = new StringBuilder();
        boolean fenced = false;
        boolean table = false;
        String blockType = "paragraph";
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            int level = headingLevel(line);
            boolean fenceLine =
                    line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~");
            boolean tableLine = protectBlocks && line.indexOf('|') >= 0 && !line.isBlank();
            if (!fenced && level > 0) {
                flush(sections, body, headingPath(headings), blockType, false);
                for (int i = level; i < headings.length; i++) headings[i] = null;
                headings[level] = line.replaceFirst("^#{1,6}\\s+", "").strip();
                blockType = "paragraph";
                table = false;
                continue;
            }
            if (fenceLine) {
                if (!fenced) {
                    flush(sections, body, headingPath(headings), blockType, table);
                    blockType = "code";
                }
                body.append(line).append('\n');
                fenced = !fenced;
                if (!fenced) {
                    flush(sections, body, headingPath(headings), blockType, protectBlocks);
                    blockType = "paragraph";
                }
                continue;
            }
            if (!fenced && tableLine != table && body.length() > 0) {
                flush(sections, body, headingPath(headings), blockType, table && protectBlocks);
            }
            table = !fenced && tableLine;
            blockType = fenced
                    ? "code"
                    : table ? "table" : line.stripLeading().matches("(?:[-*+] |\\d+[.)] ).*") ? "list" : "paragraph";
            body.append(line).append('\n');
            if (!fenced && !table && line.isBlank()) {
                flush(sections, body, headingPath(headings), blockType, false);
            }
        }
        flush(sections, body, headingPath(headings), blockType, (fenced || table) && protectBlocks);
        return sections;
    }

    private static void flush(
            List<Section> sections, StringBuilder body, String heading, String blockType, boolean protectedBlock) {
        String value = body.toString().strip();
        body.setLength(0);
        if (!value.isBlank()) sections.add(new Section(heading, value, blockType, protectedBlock));
    }

    private static int headingLevel(String line) {
        int i = 0;
        while (i < line.length() && i < 6 && line.charAt(i) == '#') i++;
        return i > 0 && i < line.length() && Character.isWhitespace(line.charAt(i)) ? i : 0;
    }

    private static String headingPath(String[] headings) {
        List<String> path = new ArrayList<>();
        for (int i = 1; i < headings.length; i++) if (headings[i] != null) path.add(headings[i]);
        return String.join(" > ", path);
    }

    private record Section(String headingPath, String body, String blockType, boolean protectedBlock) {}
}
