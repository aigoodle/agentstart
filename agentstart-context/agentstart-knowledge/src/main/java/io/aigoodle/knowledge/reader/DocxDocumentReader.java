package io.aigoodle.knowledge.reader;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/** DOCX parser preserving paragraph order, heading hierarchy, lists and tables. */
public class DocxDocumentReader implements DocumentReader {
    public static final String NAME = "docx";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".docx");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return parse(bytes, filename).text();
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String filename) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> headings = new ArrayList<>();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    int level = headingLevel(paragraph.getStyle());
                    if (level > 0) {
                        String path = ParserSupport.updateHeading(headings, level, text);
                        ParserSupport.add(
                                blocks,
                                BlockType.HEADING,
                                text,
                                null,
                                level,
                                path,
                                Map.of("style", String.valueOf(paragraph.getStyle())));
                    } else {
                        BlockType type = paragraph.getNumID() == null ? BlockType.PARAGRAPH : BlockType.LIST_ITEM;
                        ParserSupport.add(
                                blocks, type, text, null, null, ParserSupport.headingPath(headings), Map.of());
                    }
                } else if (element instanceof XWPFTable table) {
                    List<List<String>> rows = table.getRows().stream()
                            .map(row -> row.getTableCells().stream()
                                    .map(cell -> cell.getText().strip())
                                    .toList())
                            .toList();
                    ParserSupport.add(
                            blocks,
                            BlockType.TABLE,
                            ParserSupport.markdownTable(rows),
                            null,
                            null,
                            ParserSupport.headingPath(headings),
                            Map.of("rows", rows.size()));
                }
            }
            String title = doc.getProperties().getCoreProperties().getTitle();
            return ParsedDocument.builder()
                    .filename(filename)
                    .parser(NAME)
                    .mediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .title(title)
                    .blocks(blocks)
                    .metadata(Map.of("blockCount", blocks.size()))
                    .build();
        } catch (Exception e) {
            throw new PlatformException("extract_failed", "Failed to parse DOCX " + filename, e);
        }
    }

    private static int headingLevel(String style) {
        if (style == null) return 0;
        String normalized = style.toLowerCase().replace(" ", "");
        if (!normalized.startsWith("heading") && !normalized.startsWith("标题")) return 0;
        String digits = normalized.replaceAll("\\D", "");
        return digits.isEmpty() ? 1 : Math.max(1, Math.min(6, Integer.parseInt(digits)));
    }
}
