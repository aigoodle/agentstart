package io.aigoodle.knowledge.eval;

import java.util.List;

/** Named and versioned golden set so evaluation results remain reproducible. */
public record RetrievalEvaluationDataset(String name, String version, List<RetrievalEvaluationCase> cases) {
    public RetrievalEvaluationDataset {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
