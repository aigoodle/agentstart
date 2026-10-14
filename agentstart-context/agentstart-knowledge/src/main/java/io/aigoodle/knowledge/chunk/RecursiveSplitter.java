package io.aigoodle.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-aware recursive text splitter (LangChain-style): try increasingly fine
 * separators until each piece fits {@code maxTokens}, hard-splitting only as a last
 * resort, then greedily merge adjacent pieces and add token overlap between chunks.
 */
public final class RecursiveSplitter {

    private RecursiveSplitter() {}

    public static List<String> split(String text, TextSplitSettings settings) {
        List<String> smallestFittingPieces = new ArrayList<>();
        splitRecursively(text, settings, 0, smallestFittingPieces);
        return mergeWithOverlap(smallestFittingPieces, settings);
    }

    /** @deprecated Use {@link #split(String, TextSplitSettings)}. */
    @Deprecated(forRemoval = false)
    public static List<String> split(String text, List<String> separators, int maximumTokens, int overlapTokens) {
        return split(text, new TextSplitSettings(separators, maximumTokens, overlapTokens));
    }

    private static void splitRecursively(
            String text, TextSplitSettings settings, int separatorIndex, List<String> output) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (TokenCounter.count(text) <= settings.maximumTokens()) {
            output.add(text.strip());
            return;
        }
        if (separatorIndex >= settings.separators().size()) {
            // No separators left: hard split on token budget by characters.
            output.addAll(hardSplit(text, settings.maximumTokens()));
            return;
        }
        String separator = settings.separators().get(separatorIndex);
        if (separator.isEmpty() || !text.contains(separator)) {
            splitRecursively(text, settings, separatorIndex + 1, output);
            return;
        }
        for (String part : text.split(java.util.regex.Pattern.quote(separator))) {
            if (part.isBlank()) {
                continue;
            }
            // Re-attach the separator so sentence punctuation is preserved.
            String separatedPiece = part + (isPunctuation(separator) ? separator : "");
            splitRecursively(separatedPiece, settings, separatorIndex + 1, output);
        }
    }

    private static List<String> hardSplit(String text, int maximumTokens) {
        List<String> pieces = new ArrayList<>();
        // Approx chars per chunk: 1 token ~ 1 CJK char or ~4 latin chars; use a safe lower bound.
        int approximateCharactersPerPiece = Math.max(1, maximumTokens);
        for (int start = 0; start < text.length(); start += approximateCharactersPerPiece) {
            int end = Math.min(text.length(), start + approximateCharactersPerPiece);
            pieces.add(text.substring(start, end).strip());
        }
        return pieces;
    }

    private static List<String> mergeWithOverlap(List<String> pieces, TextSplitSettings settings) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }
            if (currentChunk.length() == 0) {
                currentChunk.append(piece);
            } else if (TokenCounter.count(currentChunk + "\n" + piece) <= settings.maximumTokens()) {
                currentChunk.append("\n").append(piece);
            } else {
                chunks.add(currentChunk.toString());
                String overlap = settings.overlapTokens() > 0
                        ? trailingTokenSlice(currentChunk.toString(), settings.overlapTokens())
                        : "";
                currentChunk = new StringBuilder();
                if (!overlap.isBlank()) {
                    currentChunk.append(overlap).append("\n");
                }
                currentChunk.append(piece);
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
    }

    /** Return a trailing slice of {@code text} of roughly {@code overlapTokens} tokens. */
    private static String trailingTokenSlice(String text, int overlapTokens) {
        if (TokenCounter.count(text) <= overlapTokens) {
            return text;
        }
        int sliceStart = 0;
        // Walk back from the end accumulating until we hit the token budget.
        for (int index = text.length() - 1; index >= 0; index--) {
            if (TokenCounter.count(text.substring(index)) >= overlapTokens) {
                sliceStart = index;
                break;
            }
        }
        return text.substring(sliceStart).strip();
    }

    private static boolean isPunctuation(String separator) {
        String strippedSeparator = separator.strip();
        return strippedSeparator.length() == 1 && !Character.isLetterOrDigit(strippedSeparator.charAt(0));
    }
}
