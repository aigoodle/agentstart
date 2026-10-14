package io.aigoodle.rag.spi;

import io.aigoodle.rag.api.PlannedQuery;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RetrievalCandidate;
import java.util.List;

public interface Retriever {
    String getName();

    RagPolicy.Channel getChannel();

    default boolean available(RagRequest request) {
        return true;
    }

    List<RetrievalCandidate> retrieve(PlannedQuery query, RagRequest request);
}
