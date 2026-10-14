package io.aigoodle.workflow.node.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalSupportTest {

    @Test
    void normalizesDatasetIdsAndRendersQuery() {
        NodeDef node = knowledgeNode()
                .with("datasetIds", List.of(" dataset-a ", "", "dataset-a", "dataset-b"))
                .with("query", "Question: {{#sys.question#}}")
                .with("topK", 8);
        ExecutionContext context = ExecutionContext.start(Map.of("question", "Why?"), null, null);

        KnowledgeRetrievalConfiguration configuration = KnowledgeRetrievalConfiguration.from(node, context);

        assertThat(configuration.datasetIds()).containsExactly("dataset-a", "dataset-b");
        assertThat(configuration.retrievalRequest().getQuery()).isEqualTo("Question: Why?");
        assertThat(configuration.retrievalRequest().getTopK()).isEqualTo(8);
    }

    @Test
    void acceptsLegacySingleDatasetId() {
        KnowledgeRetrievalConfiguration configuration = KnowledgeRetrievalConfiguration.from(
                knowledgeNode().with("datasetIds", " dataset-a "), ExecutionContext.start(Map.of(), null, null));

        assertThat(configuration.datasetIds()).containsExactly("dataset-a");
        assertThat(configuration.retrievalRequest().getTopK()).isEqualTo(5);
    }

    @Test
    void mapsParentContextAndStableSegmentViews() {
        RetrievedSegment first = RetrievedSegment.builder()
                .content("child one")
                .parentContent("parent context")
                .documentId("document-1")
                .score(0.9)
                .build();
        RetrievedSegment second =
                RetrievedSegment.builder().content("child two").score(0.7).build();

        NodeResult result = KnowledgeRetrievalResultMapper.map(List.of(first, second));

        assertThat(result.getOutputs().get("result")).isEqualTo("parent context\n\n---\n\nchild two");
        assertThat(segmentViews(result).get(0))
                .containsEntry("content", "child one")
                .containsEntry("score", 0.9)
                .containsEntry("documentId", "document-1");
        assertThat(segmentViews(result).get(1)).containsEntry("documentId", "");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> segmentViews(NodeResult result) {
        return (List<Map<String, Object>>) result.getOutputs().get("segments");
    }

    private static NodeDef knowledgeNode() {
        return NodeDef.of("knowledge", NodeType.KNOWLEDGE_RETRIEVAL);
    }
}
