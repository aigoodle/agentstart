package io.aigoodle.knowledge.reader;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** XLS/XLSX parser emitting one heading and one Markdown table per worksheet. */
public class SpreadsheetDocumentReader implements DocumentReader {
    public static final String NAME = "spreadsheet";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null) return false;
        String f = filename.toLowerCase();
        return f.endsWith(".xlsx") || f.endsWith(".xls");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return parse(bytes, filename).text();
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String filename) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            List<DocumentBlock> blocks = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            int sheetIndex = 0;
            for (Sheet sheet : workbook) {
                String heading = sheet.getSheetName();
                ParserSupport.add(
                        blocks, BlockType.HEADING, heading, sheetIndex + 1, 1, heading, Map.of("sheet", heading));
                List<List<String>> rows = new ArrayList<>();
                int maxColumns = 0;
                for (Row row : sheet) maxColumns = Math.max(maxColumns, row.getLastCellNum());
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < maxColumns; c++) cells.add(formatter.formatCellValue(row.getCell(c)));
                    if (cells.stream().anyMatch(v -> !v.isBlank())) rows.add(cells);
                }
                ParserSupport.add(
                        blocks,
                        BlockType.TABLE,
                        ParserSupport.markdownTable(rows),
                        sheetIndex + 1,
                        null,
                        heading,
                        Map.of("sheet", heading, "rows", rows.size(), "columns", maxColumns));
                sheetIndex++;
            }
            return ParsedDocument.builder()
                    .filename(filename)
                    .parser(NAME)
                    .mediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .blocks(blocks)
                    .metadata(Map.of("sheetCount", workbook.getNumberOfSheets()))
                    .build();
        } catch (Exception e) {
            throw new PlatformException("extract_failed", "Failed to parse spreadsheet " + filename, e);
        }
    }
}
