package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentExtractionRequestTest {

    @Test
    void rendersInlineTextAndSuppliesReadableFilename() {
        ExecutionContext context = ExecutionContext.start(Map.of("name", "Alice"), null, null);
        NodeDef node = documentNode().with("text", "Hello {{#sys.name#}}");

        DocumentExtractionRequest request = DocumentExtractionRequest.from(node, context);

        assertThat(new String(request.content(), StandardCharsets.UTF_8)).isEqualTo("Hello Alice");
        assertThat(request.filename()).isEqualTo("inline.txt");
        assertThat(request.readerName()).isNull();
    }

    @Test
    void decodesBase64ContentFromVariablePool() {
        String encoded = Base64.getEncoder().encodeToString("document".getBytes(StandardCharsets.UTF_8));
        ExecutionContext context =
                ExecutionContext.start(Map.of("fileContent", encoded, "filename", "note.txt"), null, null);

        DocumentExtractionRequest request = DocumentExtractionRequest.from(documentNode(), context);

        assertThat(new String(request.content(), StandardCharsets.UTF_8)).isEqualTo("document");
        assertThat(request.filename()).isEqualTo("note.txt");
    }

    @Test
    void preservesPlainTextThatIsNotBase64() {
        ExecutionContext context = ExecutionContext.start(Map.of("fileContent", "plain text!"), null, null);

        DocumentExtractionRequest request = DocumentExtractionRequest.from(documentNode(), context);

        assertThat(new String(request.content(), StandardCharsets.UTF_8)).isEqualTo("plain text!");
    }

    @Test
    void explicitReaderIsNormalizedAndUsed() {
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        ExecutionContext context =
                ExecutionContext.start(Map.of("fileContent", content, "filename", "data.bin"), null, null);
        DocumentExtractionRequest request =
                DocumentExtractionRequest.from(documentNode().with("readerName", " custom-reader "), context);
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.extract("custom-reader", content, "data.bin")).thenReturn("extracted");

        assertThat(request.extractWith(extractor)).isEqualTo("extracted");
        verify(extractor).extract("custom-reader", content, "data.bin");
    }

    private static NodeDef documentNode() {
        return NodeDef.of("document", NodeType.DOCUMENT_EXTRACTOR);
    }
}
