package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CSV/TSV parser with quoted-field handling, represented as a searchable table block. */
public class CsvDocumentReader implements DocumentReader {
    public static final String NAME = "csv";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null) return false;
        String f = filename.toLowerCase();
        return f.endsWith(".csv") || f.endsWith(".tsv");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return parse(bytes, filename).text();
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String filename) {
        String text = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        char delimiter = filename != null && filename.toLowerCase().endsWith(".tsv") ? '\t' : ',';
        List<List<String>> rows = parseRows(text, delimiter);
        DocumentBlock table = DocumentBlock.builder()
                .index(0)
                .type(BlockType.TABLE)
                .text(ParserSupport.markdownTable(rows))
                .metadata(Map.of("rows", rows.size(), "delimiter", String.valueOf(delimiter)))
                .build();
        return ParsedDocument.builder()
                .filename(filename)
                .parser(NAME)
                .mediaType("text/csv")
                .blocks(rows.isEmpty() ? List.of() : List.of(table))
                .build();
    }

    static List<List<String>> parseRows(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else field.append(c);
        }
        row.add(field.toString());
        if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row);
        return rows;
    }
}
