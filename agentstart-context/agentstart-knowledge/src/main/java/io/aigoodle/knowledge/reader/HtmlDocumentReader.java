package io.aigoodle.knowledge.reader;

import java.nio.charset.StandardCharsets;

/**
 * Strips HTML tags to plain text. Used for {@code .html} / {@code .htm}. For heavier
 * HTML processing (link extraction, CSS-aware traversal) prefer Tika via
 * {@link TikaDocumentReader}.
 */
public class HtmlDocumentReader implements DocumentReader {

    public static final String NAME = "html";

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
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    @Override
    public String read(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String html = new String(bytes, StandardCharsets.UTF_8);
        // Remove scripts and styles wholesale, then strip remaining tags.
        String cleaned = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    @Override
    public io.aigoodle.knowledge.reader.model.ParsedDocument parse(byte[] bytes, String filename) {
        // Tika's HTML parser normalizes malformed HTML to safe XHTML; reuse the
        // same semantic block conversion while retaining this format-specific name.
        var parsed = new TikaDocumentReader().parse(bytes, filename);
        parsed.setParser(NAME);
        return parsed;
    }
}
