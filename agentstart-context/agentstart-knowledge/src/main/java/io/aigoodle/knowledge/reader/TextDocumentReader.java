package io.aigoodle.knowledge.reader;

import java.nio.charset.StandardCharsets;

/**
 * Reads a payload as UTF-8 text. Used for {@code .txt}, {@code .log}, {@code .csv}
 * and similar plain formats.
 */
public class TextDocumentReader implements DocumentReader {

    public static final String NAME = "text";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
}
