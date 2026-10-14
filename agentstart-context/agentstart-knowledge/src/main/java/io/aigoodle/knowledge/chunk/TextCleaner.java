package io.aigoodle.knowledge.chunk;

import io.aigoodle.knowledge.config.ProcessRule;
import java.util.regex.Pattern;

/**
 * Pre-processing cleanup applied before chunking, mirroring Dify's process rules.
 */
public final class TextCleaner {

    private static final Pattern REPEATED_HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private TextCleaner() {}

    public static String clean(String text, ProcessRule rule) {
        if (text == null) {
            return "";
        }
        String cleanedText = text.replace("\r\n", "\n").replace("\r", "\n");
        if (rule.isRemoveUrlsEmails()) {
            cleanedText = URL.matcher(cleanedText).replaceAll(" ");
            cleanedText = EMAIL_ADDRESS.matcher(cleanedText).replaceAll(" ");
        }
        if (rule.isRemoveExtraWhitespace()) {
            cleanedText = REPEATED_HORIZONTAL_WHITESPACE.matcher(cleanedText).replaceAll(" ");
            cleanedText = EXCESSIVE_NEWLINES.matcher(cleanedText).replaceAll("\n\n");
        }
        return cleanedText.strip();
    }
}
