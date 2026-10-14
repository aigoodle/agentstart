package io.aigoodle.rag.spi;

import io.aigoodle.rag.api.Evidence;
import io.aigoodle.rag.api.RagPolicy;
import io.aigoodle.rag.api.RagRequest;
import java.util.List;

public interface ContextProcessor {
    int getOrder();

    List<Evidence> process(RagRequest request, List<Evidence> evidence, RagPolicy.Context policy);
}
