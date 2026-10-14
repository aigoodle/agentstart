package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.node.NodeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A normalized set of fields requested by a parameter-extractor node. */
final class ExtractionParameterSet {

    private final List<ExtractionParameter> parameters;

    private ExtractionParameterSet(List<ExtractionParameter> parameters) {
        this.parameters = parameters;
    }

    static ExtractionParameterSet from(List<Map<String, Object>> configuredParameters) {
        List<ExtractionParameter> parameters = new ArrayList<>();
        for (Map<String, Object> configuredParameter : configuredParameters) {
            String name = text(configuredParameter.get("name"));
            if (name == null) {
                continue;
            }
            String type = text(configuredParameter.get("type"));
            parameters.add(new ExtractionParameter(
                    name, type == null ? "string" : type, text(configuredParameter.get("description"))));
        }
        return new ExtractionParameterSet(List.copyOf(parameters));
    }

    boolean isEmpty() {
        return parameters.isEmpty();
    }

    String schemaInstruction() {
        StringBuilder schema = new StringBuilder("{\n");
        for (int index = 0; index < parameters.size(); index++) {
            ExtractionParameter parameter = parameters.get(index);
            schema.append("  \"")
                    .append(parameter.name())
                    .append("\": \"")
                    .append(parameter.type())
                    .append('"');
            if (parameter.description() != null) {
                schema.append("  // ").append(parameter.description());
            }
            if (index < parameters.size() - 1) {
                schema.append(',');
            }
            schema.append('\n');
        }
        schema.append('}');
        return "Extract the fields described by the JSON schema. Reply with ONLY the JSON object, "
                + "no code fences, no prose. Use null when a field is missing.\nSchema:\n" + schema;
    }

    void writeExtractedValues(NodeResult result, Map<String, Object> extractedValues) {
        for (ExtractionParameter parameter : parameters) {
            Object value = extractedValues == null ? null : extractedValues.get(parameter.name());
            result.output(parameter.name(), value);
        }
    }

    void writeMissingValues(NodeResult result) {
        writeExtractedValues(result, null);
    }

    NodeResult resultFrom(String modelResponse) {
        Map<String, Object> extractedValues = JsonObjectResponseParser.parse(modelResponse);
        NodeResult result = NodeResult.empty();
        writeExtractedValues(result, extractedValues);
        return result.output("result", modelResponse);
    }

    NodeResult failedResult(Throwable extractionFailure) {
        NodeResult result = NodeResult.empty();
        writeMissingValues(result);
        return result.output("error", readableMessage(extractionFailure));
    }

    private static String readableMessage(Throwable failure) {
        if (failure == null) {
            return "Parameter extraction failed";
        }
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record ExtractionParameter(String name, String type, String description) {}
}
