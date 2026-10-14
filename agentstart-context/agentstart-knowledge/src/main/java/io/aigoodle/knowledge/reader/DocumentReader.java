package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import io.aigoodle.knowledge.reader.model.ParsedDocument;
import java.util.List;

/**
 * SPI that turns a raw byte payload into extracted text. Third parties can publish
 * a new reader (e.g. for a proprietary file format) as a Spring bean and it will be
 * picked up by {@link DocumentReaderRegistry} automatically.
 * <p>
 * The name is matched against the incoming source type / file extension by
 * {@link DocumentReaderRegistry#pick(String)}.
 */
public interface DocumentReader {

    /**
     * Unique reader name, also used as the source type when calling
     * {@link io.aigoodle.knowledge.service.KnowledgeService}
     * (e.g. {@code text}, {@code markdown}, {@code pdf}, {@code html}, {@code tika}).
     */
    String getName();

    /** True if this reader can handle the given filename (typically by extension). */
    boolean supports(String filename);

    /** Extract plain text from the payload. */
    String read(byte[] bytes, String filename);

    /**
     * Structure-preserving parse entrypoint. Existing third-party readers remain
     * source-compatible: their plain text is represented as one paragraph block.
     */
    default ParsedDocument parse(byte[] bytes, String filename) {
        String text = read(bytes, filename);
        List<DocumentBlock> blocks = text == null || text.isBlank()
                ? List.of()
                : List.of(DocumentBlock.builder()
                        .index(0)
                        .type(BlockType.PARAGRAPH)
                        .text(text)
                        .build());
        return ParsedDocument.builder()
                .filename(filename)
                .parser(getName())
                .blocks(blocks)
                .build();
    }
}
