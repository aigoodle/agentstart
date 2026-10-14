package io.aigoodle.knowledge.reader.model;

/** Semantic block types emitted by format-aware document parsers. */
public enum BlockType {
    TITLE,
    HEADING,
    PARAGRAPH,
    LIST_ITEM,
    TABLE,
    CODE,
    IMAGE,
    PAGE_BREAK
}
