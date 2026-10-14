package io.aigoodle.knowledge.chunk;

import java.util.List;

/** Immutable token budget, overlap, and separator policy for recursive text splitting. */
public record TextSplitSettings(List<String> separators, int maximumTokens, int overlapTokens) {

    public TextSplitSettings {
        separators = separators == null ? List.of() : List.copyOf(separators);
        maximumTokens = Math.max(1, maximumTokens);
        overlapTokens = Math.clamp(overlapTokens, 0, maximumTokens);
    }

    public static TextSplitSettings withoutOverlap(List<String> separators, int maximumTokens) {
        return new TextSplitSettings(separators, maximumTokens, 0);
    }
}
