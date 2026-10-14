package io.aigoodle.workflow.node.builtin;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import io.aigoodle.workflow.node.NodeResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps retrieved knowledge segments to the stable workflow-node output shape. */
final class KnowledgeRetrievalResultMapper {

    private static final String CONTEXT_SEPARATOR = "\n\n---\n\n";

    private KnowledgeRetrievalResultMapper() {}

    static NodeResult map(List<RetrievedSegment> retrievedSegments) {
        List<RetrievedSegment> segments = retrievedSegments == null ? List.of() : retrievedSegments;
        String context =
                segments.stream().map(RetrievedSegment::contextText).collect(Collectors.joining(CONTEXT_SEPARATOR));
        List<Map<String, Object>> segmentViews = segments.stream()
                .map(KnowledgeRetrievalResultMapper::segmentView)
                .toList();
        return NodeResult.empty().output("result", context).output("segments", segmentViews);
    }

    private static Map<String, Object> segmentView(RetrievedSegment segment) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("content", segment.getContent());
        view.put("score", segment.getScore());
        view.put("documentId", segment.getDocumentId() == null ? "" : segment.getDocumentId());
        return view;
    }
}
