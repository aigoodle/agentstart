package io.aigoodle.agent.strategy;

import io.aigoodle.common.util.JsonUtils;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses a model-produced JSON plan and supplies a safe single-step fallback. */
final class PlanParser {

    private static final Logger logger = LoggerFactory.getLogger(PlanParser.class);
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    List<String> parse(String modelOutput, String fallbackTask) {
        if (modelOutput != null) {
            Matcher jsonArray = JSON_ARRAY.matcher(modelOutput);
            if (jsonArray.find()) {
                try {
                    List<String> steps = JsonUtils.parseList(jsonArray.group(), String.class);
                    if (steps != null && !steps.isEmpty()) {
                        return steps;
                    }
                } catch (Exception exception) {
                    logger.debug("Plan JSON parse failed; using the task as one step: {}", exception.getMessage());
                }
            }
        }
        return List.of(fallbackTask);
    }
}
