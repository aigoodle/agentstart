package io.aigoodle.memory.extraction;

import io.aigoodle.memory.MemoryWrite;
import java.util.List;

/** SPI for extracting durable facts/preferences; implementations may use rules or an LLM. */
@FunctionalInterface
public interface MemoryExtractor {
    List<MemoryWrite> extract(MemoryExchange exchange);
}
