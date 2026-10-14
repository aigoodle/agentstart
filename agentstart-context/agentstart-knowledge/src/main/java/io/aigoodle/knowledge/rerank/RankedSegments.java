package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.Comparator;
import java.util.List;

/** Shared ordering and top-count policy for reranker implementations. */
final class RankedSegments {

    private RankedSegments() {}

    static List<RetrievedSegment> highestScoring(List<RetrievedSegment> segments, int topCount) {
        segments.sort(Comparator.comparingDouble(RetrievedSegment::getScore).reversed());
        return topCount > 0 && segments.size() > topCount ? segments.subList(0, topCount) : segments;
    }
}
