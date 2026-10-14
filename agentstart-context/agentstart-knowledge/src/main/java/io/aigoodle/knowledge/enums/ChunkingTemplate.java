package io.aigoodle.knowledge.enums;

/**
 * Document-type aware chunking strategies, inspired by RAGFlow's template chunkers.
 * Each value maps to a {@code Chunker} implementation.
 */
public enum ChunkingTemplate {

    /** Recursive separator + token-size splitting. The general-purpose default. */
    NAIVE,

    /** Small child chunks for precise recall, each linked to a larger parent chunk for context. */
    PARENT_CHILD,

    /** Detects question/answer pairs and keeps each pair as one chunk. */
    QA,

    /** Splits on Markdown headings, preserving the heading hierarchy as metadata. */
    MARKDOWN,

    /** Auto-detects headings, lists, tables and fenced code, preserving structural context. */
    STRUCTURE_AWARE,

    /** The whole document becomes a single chunk (good for very short docs). */
    ONE
}
