package io.aigoodle.memory;

/** Lifecycle tier of a memory item. */
public enum MemoryTier {
    /** Turn-local, bounded and never persisted. */
    WORKING,
    /** Conversation history with a configurable time-to-live. */
    SHORT_TERM,
    /** Curated facts and experiences retained across conversations. */
    LONG_TERM
}
