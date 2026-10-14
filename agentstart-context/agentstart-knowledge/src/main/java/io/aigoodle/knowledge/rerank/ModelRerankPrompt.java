package io.aigoodle.knowledge.rerank;

import io.aigoodle.knowledge.retrieve.RetrievedSegment;
import java.util.List;

/** Renders the text protocol sent to an LLM-based relevance scorer. */
final class ModelRerankPrompt {

    static final String SYSTEM_MESSAGE = "You are a strict relevance scorer. Only output the requested lines.";
    private static final int MAX_PASSAGE_LENGTH = 600;

    private ModelRerankPrompt() {}

    static String render(String query, List<RetrievedSegment> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Query: ").append(query).append("\n\n");
        prompt.append("Score each passage from 0.0 (irrelevant) to 1.0 (highly relevant). ")
                .append("Reply with exactly one line per passage in the format `index: score`.\n\n");
        for (int index = 0; index < candidates.size(); index++) {
            prompt.append('[')
                    .append(index)
                    .append("] ")
                    .append(abbreviate(candidates.get(index).getContent()))
                    .append("\n\n");
        }
        return prompt.toString();
    }

    private static String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > MAX_PASSAGE_LENGTH ? content.substring(0, MAX_PASSAGE_LENGTH) + "…" : content;
    }
}
