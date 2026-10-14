package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.ParsedDocument;

/**
 * Post-parser SPI for OCR, image captioning, layout recognition or metadata
 * extraction. Implementations should return the same document when not applicable.
 */
public interface DocumentEnricher {
    default int order() {
        return 0;
    }

    boolean supports(ParsedDocument document);

    ParsedDocument enrich(ParsedDocument document, byte[] sourceBytes);
}
