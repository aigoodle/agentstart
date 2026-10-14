package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Passes Markdown through verbatim so the {@code MarkdownChunker} can use its
 * headings. Note: Spring AI's markdown reader eagerly parses to Document, dropping
 * headings — we keep the raw text instead.
 */
public class MarkdownDocumentReader implements DocumentReader {

    public static final String NAME = "markdown";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String filename) {
        String markdown = read(bytes, filename).replace("\r\n", "\n").replace('\r', '\n');
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        BlockType bodyType = BlockType.PARAGRAPH;
        boolean fenced = false;
        for (String line : markdown.split("\n", -1)) {
            String stripped = line.stripLeading();
            boolean fence = stripped.startsWith("```") || stripped.startsWith("~~~");
            if (fence) {
                flush(blocks, body, bodyType, ParserSupport.headingPath(headings));
                fenced = !fenced;
                bodyType = BlockType.CODE;
                body.append(line).append('\n');
                if (!fenced) {
                    flush(blocks, body, BlockType.CODE, ParserSupport.headingPath(headings));
                    bodyType = BlockType.PARAGRAPH;
                }
                continue;
            }
            if (!fenced && stripped.matches("#{1,6}\\s+.*")) {
                flush(blocks, body, bodyType, ParserSupport.headingPath(headings));
                int level = stripped.indexOf(' ');
                String title = stripped.substring(level + 1).strip();
                String path = ParserSupport.updateHeading(headings, level, title);
                ParserSupport.add(blocks, BlockType.HEADING, title, null, level, path, Map.of());
                continue;
            }
            BlockType nextType =
                    !fenced && stripped.matches("(?:[-*+] |\\d+[.)] ).*") ? BlockType.LIST_ITEM : BlockType.PARAGRAPH;
            if (!fenced && nextType != bodyType) flush(blocks, body, bodyType, ParserSupport.headingPath(headings));
            if (!fenced) bodyType = nextType;
            body.append(line).append('\n');
            if (!fenced && line.isBlank()) flush(blocks, body, bodyType, ParserSupport.headingPath(headings));
        }
        flush(blocks, body, bodyType, ParserSupport.headingPath(headings));
        return ParsedDocument.builder()
                .filename(filename)
                .parser(NAME)
                .mediaType("text/markdown")
                .blocks(blocks)
                .metadata(Map.of("blockCount", blocks.size()))
                .build();
    }

    private static void flush(List<DocumentBlock> blocks, StringBuilder body, BlockType type, String heading) {
        String text = body.toString().strip();
        body.setLength(0);
        ParserSupport.add(blocks, type, text, null, null, heading, Map.of());
    }
}
