package io.aigoodle.knowledge.retrieve;

import java.util.List;

/** SPI for query normalization, rewriting and expansion before dense/sparse recall. */
public interface QueryTransformer {

    default int getOrder() {
        return 0;
    }

    List<String> transform(String query);
}
