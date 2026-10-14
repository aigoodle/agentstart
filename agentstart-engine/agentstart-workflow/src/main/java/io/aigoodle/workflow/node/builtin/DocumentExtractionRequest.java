package io.aigoodle.workflow.node.builtin;

import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Normalized document content, filename and reader selection for extraction. */
record DocumentExtractionRequest(byte[] content, String filename, String readerName, String contentReference) {

    private static final String DEFAULT_CONTENT_REFERENCE = "sys.fileContent";
    private static final String DEFAULT_FILENAME_REFERENCE = "sys.filename";
    private static final String INLINE_FILENAME = "inline.txt";

    static DocumentExtractionRequest from(NodeDef node, ExecutionContext context) {
        String contentReference = node.getString("contentRef", DEFAULT_CONTENT_REFERENCE);
        String filenameReference = node.getString("filenameRef", DEFAULT_FILENAME_REFERENCE);
        String filename = text(context.getPool().get(filenameReference));
        String inlineTemplate = node.getString("text");

        byte[] content;
        if (inlineTemplate != null && !inlineTemplate.isBlank()) {
            String renderedText = VariableResolver.render(inlineTemplate, context.getPool());
            content = renderedText.getBytes(StandardCharsets.UTF_8);
            filename = filename == null ? INLINE_FILENAME : filename;
        } else {
            content = bytesFrom(context.getPool().get(contentReference));
        }
        return new DocumentExtractionRequest(
                content, filename, trimmedText(node.getString("readerName")), contentReference);
    }

    boolean hasContent() {
        return content != null;
    }

    String extractWith(DocumentExtractor documentExtractor) {
        return readerName == null
                ? documentExtractor.extractFile(content, filename)
                : documentExtractor.extract(readerName, content, filename);
    }

    private static byte[] bytesFrom(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof byte[] bytes) {
            return bytes;
        }
        if (content instanceof String text) {
            return decodeBase64OrUtf8(text);
        }
        return String.valueOf(content).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeBase64OrUtf8(String content) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException notBase64Encoded) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String trimmedText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
