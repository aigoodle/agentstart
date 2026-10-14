package io.aigoodle.rag.spi;

import io.aigoodle.rag.api.RagRequest;
import io.aigoodle.rag.api.RagResult;

public interface RagPipeline {
    RagResult retrieve(RagRequest request);
}
