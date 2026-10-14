package io.aigoodle.knowledge.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.reader.model.BlockType;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class StructuredDocumentReaderTest {

    @Test
    void docxPreservesHeadingParagraphAndTable() throws Exception {
        byte[] bytes;
        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Installation");
            doc.createParagraph().createRun().setText("Run the application locally.");
            var table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Name");
            table.getRow(0).getCell(1).setText("Port");
            table.getRow(1).getCell(0).setText("API");
            table.getRow(1).getCell(1).setText("18090");
            doc.write(out);
            bytes = out.toByteArray();
        }
        var parsed = new DocxDocumentReader().parse(bytes, "guide.docx");
        assertThat(parsed.getBlocks())
                .extracting(b -> b.getType())
                .contains(BlockType.HEADING, BlockType.PARAGRAPH, BlockType.TABLE);
        assertThat(parsed.text()).contains("Installation", "| Name | Port |", "18090");
    }

    @Test
    void pdfPreservesPageNumbers() throws Exception {
        byte[] bytes;
        try (PDDocument pdf = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 1; i <= 2; i++) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Content on page " + i);
                    stream.endText();
                }
            }
            pdf.save(out);
            bytes = out.toByteArray();
        }
        var parsed = new PdfDocumentReader().parse(bytes, "pages.pdf");
        assertThat(parsed.pageCount()).isEqualTo(2);
        assertThat(parsed.getBlocks()).extracting(b -> b.getPage()).contains(1, 2);
    }

    @Test
    void spreadsheetEmitsSheetHeadingAndTable() throws Exception {
        byte[] bytes;
        try (Workbook book = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Products");
            sheet.createRow(0).createCell(0).setCellValue("Product");
            sheet.getRow(0).createCell(1).setCellValue("Price");
            sheet.createRow(1).createCell(0).setCellValue("Widget");
            sheet.getRow(1).createCell(1).setCellValue(12.5);
            book.write(out);
            bytes = out.toByteArray();
        }
        var parsed = new SpreadsheetDocumentReader().parse(bytes, "products.xlsx");
        assertThat(parsed.text()).contains("Products", "Widget", "12.5");
        assertThat(parsed.getBlocks()).extracting(b -> b.getType()).contains(BlockType.TABLE);
    }

    @Test
    void presentationPreservesSlidesAndTitles() throws Exception {
        byte[] bytes;
        try (XMLSlideShow show = new XMLSlideShow();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var master = show.getSlideMasters().get(0);
            var slide = show.createSlide(master.getLayout(SlideLayout.TITLE_AND_CONTENT));
            slide.getPlaceholder(0).setText("Architecture");
            slide.getPlaceholder(1).setText("Parser to chunks to retrieval");
            show.write(out);
            bytes = out.toByteArray();
        }
        var parsed = new PresentationDocumentReader().parse(bytes, "architecture.pptx");
        assertThat(parsed.pageCount()).isEqualTo(1);
        assertThat(parsed.text()).contains("Architecture", "retrieval");
    }

    @Test
    void csvHandlesQuotedDelimiters() {
        var parsed = new CsvDocumentReader().parse("name,note\nA,\"x,y\"".getBytes(), "data.csv");
        assertThat(parsed.text()).contains("x,y");
    }
}
