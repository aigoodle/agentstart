package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.util.List;

/**
 * Facade over {@link DocumentReaderRegistry}. Callers still get simple
 * {@code extractText / extractMarkdown / extractFile} methods but the actual work is
 * delegated to pluggable {@link DocumentReader} beans, so third parties can add
 * proprietary formats without touching this class.
 */
public class DocumentExtractor {

    private final DocumentReaderRegistry registry;
    private final List<DocumentEnricher> enrichers;

    public DocumentExtractor() {
        this(
                new DocumentReaderRegistry(List.of(
                        new TextDocumentReader(),
                        new MarkdownDocumentReader(),
                        new HtmlDocumentReader(),
                        new DocxDocumentReader(),
                        new PdfDocumentReader(),
                        new PresentationDocumentReader(),
                        new SpreadsheetDocumentReader(),
                        new CsvDocumentReader(),
                        new TikaDocumentReader())),
                List.of());
    }

    public DocumentExtractor(DocumentReaderRegistry registry) {
        this(registry, List.of());
    }

    public DocumentExtractor(DocumentReaderRegistry registry, List<DocumentEnricher> enrichers) {
        this.registry = registry;
        this.enrichers = enrichers == null
                ? List.of()
                : enrichers.stream()
                        .sorted(java.util.Comparator.comparingInt(DocumentEnricher::order))
                        .toList();
    }

    public String extractText(String text) {
        return text == null ? "" : text;
    }

    /** Markdown is kept verbatim so the {@code MarkdownChunker} can use its headings. */
    public String extractMarkdown(String markdown, String name) {
        return markdown == null ? "" : markdown;
    }

    /**
     * Extract text from an arbitrary payload. The reader is chosen by
     * {@link DocumentReaderRegistry#pick(String)} — extension first, Tika fallback.
     */
    public String extractFile(byte[] bytes, String filename) {
        return parseFile(bytes, filename).text();
    }

    public ParsedDocument parseFile(byte[] bytes, String filename) {
        DocumentReader reader = registry.pick(filename);
        if (reader == null) {
            String text = bytes == null ? "" : new String(bytes);
            return ParsedDocument.builder()
                    .filename(filename)
                    .parser("fallback")
                    .blocks(
                            text.isBlank()
                                    ? List.of()
                                    : List.of(DocumentBlock.builder()
                                            .index(0)
                                            .type(BlockType.PARAGRAPH)
                                            .text(text)
                                            .build()))
                    .build();
        }
        ParsedDocument parsed = reader.parse(bytes, filename);
        for (DocumentEnricher enricher : enrichers) {
            if (enricher.supports(parsed)) parsed = enricher.enrich(parsed, bytes);
        }
        return parsed;
    }

    /** Extract using an explicitly named reader — useful for URL/HTML sources etc. */
    public String extract(String readerName, byte[] bytes, String filename) {
        DocumentReader reader = registry.byName(readerName);
        if (reader == null) {
            return extractFile(bytes, filename);
        }
        return reader.read(bytes, filename);
    }

    public ParsedDocument parse(String readerName, byte[] bytes, String filename) {
        DocumentReader reader = registry.byName(readerName);
        return reader == null ? parseFile(bytes, filename) : reader.parse(bytes, filename);
    }

    public DocumentReaderRegistry registry() {
        return registry;
    }
}
