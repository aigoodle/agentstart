package io.aigoodle.knowledge.config;

import io.aigoodle.knowledge.enums.ChunkingTemplate;
import java.util.List;
import lombok.Data;

/**
 * How a document is cleaned and split into chunks.
 */
@Data
public class ProcessRule {

    /** Dify's parent-child parent-context mode: paragraph slice vs the whole document. */
    public enum ParentMode {
        /** Split the document into paragraph-sized parent blocks (default). */
        PARAGRAPH,
        /** Treat the whole document as a single parent (max context). */
        FULL_DOC
    }

    private ChunkingTemplate template = ChunkingTemplate.NAIVE;

    /** Target chunk size in tokens (approximate; see {@code TokenCounter}). */
    private int chunkTokens = 256;

    /** Overlap in tokens between adjacent chunks (NAIVE template). */
    private int overlapTokens = 50;

    /** Keep Markdown tables and fenced code blocks intact when structure-aware chunking is used. */
    private boolean protectStructuredBlocks = true;

    /** Prefix the active heading path to chunk text so embeddings retain section context. */
    private boolean includeHeadingContext = true;

    /** For PARENT_CHILD: parent chunk target size in tokens (only used when {@link #parentMode} = PARAGRAPH). */
    private int parentChunkTokens = 1024;

    /** For PARENT_CHILD: whether the parent is a paragraph slice or the full document. */
    private ParentMode parentMode = ParentMode.PARAGRAPH;

    /** Ordered separators tried by the recursive splitter, largest semantic unit first. */
    private List<String> separators = List.of("\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", "；", "; ", " ");

    /** Text cleanup toggles. */
    private boolean removeExtraWhitespace = true;

    private boolean removeUrlsEmails = false;

    public static ProcessRule naive() {
        return new ProcessRule();
    }
}
