package io.aigoodle.workflow.node.builtin;

import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;

/** Extracts text from inline or variable-backed document content. */
public class DocumentExtractorNodeExecutor implements NodeExecutor {

    private final DocumentExtractor documentExtractor;

    public DocumentExtractorNodeExecutor(DocumentExtractor documentExtractor) {
        this.documentExtractor = documentExtractor;
    }

    @Override
    public NodeType type() {
        return NodeType.DOCUMENT_EXTRACTOR;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        DocumentExtractionRequest request = DocumentExtractionRequest.from(node, context);
        if (!request.hasContent()) {
            return NodeResult.failure("Document extractor: no content found at '" + request.contentReference() + "'");
        }

        String extractedText = request.extractWith(documentExtractor);
        return NodeResult.empty()
                .output("text", extractedText)
                .output("filename", request.filename())
                .output("length", extractedText == null ? 0 : extractedText.length());
    }
}
