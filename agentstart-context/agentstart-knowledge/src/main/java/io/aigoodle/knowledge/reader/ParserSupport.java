package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ParserSupport {
    private ParserSupport() {}

    static void add(
            List<DocumentBlock> blocks,
            BlockType type,
            String text,
            Integer page,
            Integer headingLevel,
            String headingPath,
            Map<String, Object> metadata) {
        if (text == null || text.isBlank()) return;
        blocks.add(DocumentBlock.builder()
                .index(blocks.size())
                .type(type)
                .text(text.strip())
                .page(page)
                .headingLevel(headingLevel)
                .headingPath(headingPath)
                .metadata(metadata == null ? Map.of() : metadata)
                .build());
    }

    static String updateHeading(List<String> headings, int level, String title) {
        while (headings.size() < level) headings.add(null);
        headings.set(level - 1, title);
        while (headings.size() > level) headings.remove(headings.size() - 1);
        return headingPath(headings);
    }

    static String headingPath(List<String> headings) {
        List<String> nonEmpty = new ArrayList<>();
        for (String heading : headings) if (heading != null && !heading.isBlank()) nonEmpty.add(heading);
        return String.join(" > ", nonEmpty);
    }

    static String markdownTable(List<List<String>> rows) {
        if (rows.isEmpty()) return "";
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns == 0) return "";
        StringBuilder out = new StringBuilder();
        appendRow(out, rows.get(0), columns);
        out.append('|');
        for (int i = 0; i < columns; i++) out.append(" --- |");
        out.append('\n');
        for (int i = 1; i < rows.size(); i++) appendRow(out, rows.get(i), columns);
        return out.toString().strip();
    }

    private static void appendRow(StringBuilder out, List<String> row, int columns) {
        out.append('|');
        for (int i = 0; i < columns; i++) {
            String cell = i < row.size() && row.get(i) != null ? row.get(i) : "";
            out.append(' ').append(cell.replace("|", "\\|").replace("\n", " ")).append(" |");
        }
        out.append('\n');
    }
}
