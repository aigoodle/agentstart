package io.aigoodle.knowledge.reader;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

/** PPTX parser preserving slide boundaries, titles, text boxes and tables. */
public class PresentationDocumentReader implements DocumentReader {
    public static final String NAME = "presentation";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pptx");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return parse(bytes, filename).text();
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String filename) {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            List<DocumentBlock> blocks = new ArrayList<>();
            for (int i = 0; i < show.getSlides().size(); i++) {
                int page = i + 1;
                var slide = show.getSlides().get(i);
                String title = slide.getTitle();
                String path = title == null || title.isBlank() ? "Slide " + page : title.strip();
                ParserSupport.add(blocks, BlockType.HEADING, path, page, 1, path, Map.of("slide", page));
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable table) {
                        List<List<String>> rows = table.getRows().stream()
                                .map(row -> row.getCells().stream()
                                        .map(cell -> cell.getText().strip())
                                        .toList())
                                .toList();
                        ParserSupport.add(
                                blocks,
                                BlockType.TABLE,
                                ParserSupport.markdownTable(rows),
                                page,
                                null,
                                path,
                                Map.of("slide", page, "rows", rows.size()));
                    } else if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (title != null && title.strip().equals(text == null ? "" : text.strip())) continue;
                        ParserSupport.add(blocks, BlockType.PARAGRAPH, text, page, null, path, Map.of("slide", page));
                    }
                }
            }
            return ParsedDocument.builder()
                    .filename(filename)
                    .parser(NAME)
                    .mediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                    .blocks(blocks)
                    .metadata(Map.of("slideCount", show.getSlides().size()))
                    .build();
        } catch (Exception e) {
            throw new PlatformException("extract_failed", "Failed to parse presentation " + filename, e);
        }
    }
}
