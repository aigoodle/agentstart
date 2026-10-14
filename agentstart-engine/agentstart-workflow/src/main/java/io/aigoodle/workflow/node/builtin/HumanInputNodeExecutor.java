package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** Human wait node supporting fixed, AI-generated, and fixed-plus-AI form schemas. */
public final class HumanInputNodeExecutor extends AbstractWaitNodeExecutor {

    private static final Set<String> TYPES = Set.of("string", "number", "integer", "boolean", "array");
    private static final Set<String> COMPONENTS = Set.of("textarea", "select", "checkbox", "date-range");
    private static final String FORM_SYSTEM_PROMPT =
            """
            You generate a safe, concise user-input form from a business instruction.
            Return exactly one JSON object without Markdown or explanation.
            Required format:
            {"type":"object","title":"form title","description":"short guidance","x-submit-button-text":"Submit","properties":{"field_name":{"type":"string|number|integer|boolean|array","title":"field label","description":"placeholder","format":"date|date-time","enum":["value"],"x-enum-labels":["label"],"x-component":"textarea|select|checkbox|date-range"}},"required":["field_name"]}
            Rules: use stable English snake_case field names; create only fields needed for the task;
            maximum 12 fields; no nested objects; enum has at most 20 scalar values; never output HTML,
            scripts, remote components, executable code, or properties outside the demonstrated format.
            The application will validate and sanitize your JSON before displaying it.
            """;

    private final ModelService modelService;

    public HumanInputNodeExecutor() {
        this(null);
    }

    public HumanInputNodeExecutor(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public NodeType type() {
        return NodeType.HUMAN_INPUT;
    }

    @Override
    protected NodeResult resumed(NodeDef node, Object payload) {
        NodeResult result = super.resumed(node, payload);
        result.output("values", payload);
        return result;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        Object resumed = context.getPool().namespace(node.getId()).get("_resume");
        if (resumed != null) return resumed(node, resumed);
        String mode = node.getString("formMode", "FIXED").toUpperCase(Locale.ROOT);
        Map<String, Object> fixed = schema(node, context);
        Map<String, Object> effective = fixed;
        if (!"FIXED".equals(mode)) {
            try {
                Map<String, Object> generated = generate(node, context);
                effective = "HYBRID".equals(mode) ? merge(generated, fixed) : generated;
            } catch (RuntimeException failure) {
                if (fixedProperties(fixed).isEmpty()) {
                    return NodeResult.failure("Dynamic human-input form generation failed: " + failure.getMessage());
                }
                effective = fixed;
            }
        }
        Map<String, Object> interactionSchema = new LinkedHashMap<>(sanitize(effective));
        interactionSchema.put("x-interaction-id", UUID.randomUUID().toString());
        interactionSchema.put("x-access-token", UUID.randomUUID().toString());
        interactionSchema.put("x-presentation-mode", node.getString("presentationMode", "AUTO"));
        interactionSchema.put("x-delivery", delivery(node, context));
        WorkflowWaitRequest request = new WorkflowWaitRequest(
                type(),
                correlation(node, context),
                interactionSchema,
                expiresAt(node),
                null,
                UUID.randomUUID().toString());
        return NodeResult.waiting(request);
    }

    private static Map<String, Object> delivery(NodeDef node, ExecutionContext context) {
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "provider", render(node.get("channelProvider"), context));
        put(value, "channelId", render(node.get("channelId"), context));
        put(value, "connectionId", render(node.get("channelConnectionId"), context));
        put(value, "target", render(node.get("channelTarget"), context));
        put(value, "conversationId", render(node.get("channelConversationId"), context));
        put(
                value,
                "fallback",
                node.get("deliveryFallback") == null ? "WEB_LINK_THEN_TEXT" : node.get("deliveryFallback"));
        return value;
    }

    private static Object render(Object value, ExecutionContext context) {
        return value instanceof String text ? VariableResolver.render(text, context.getPool()) : value;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) target.put(key, value);
    }

    private Map<String, Object> generate(NodeDef node, ExecutionContext context) {
        if (modelService == null) throw new IllegalStateException("model service is unavailable");
        ChatClient client = NodeModelResolver.resolve(node, context, modelService);
        String configured = node.getString("generationPrompt", "{{#sys.query#}}");
        String businessPrompt = VariableResolver.render(configured, context.getPool());
        if (businessPrompt.isBlank()) throw new IllegalArgumentException("generation prompt is empty");
        ChatClient.ChatClientRequestSpec request =
                client.prompt().messages(new SystemMessage(FORM_SYSTEM_PROMPT), new UserMessage(businessPrompt));
        var options = NodeModelResolver.perNodeOptions(node);
        if (options != null) request = request.options(options.mutate());
        String response = request.call().content();
        return JsonUtils.parseMap(extractJson(response));
    }

    private static String extractJson(String text) {
        if (text == null) throw new IllegalArgumentException("model returned no content");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("model did not return a JSON object");
        return text.substring(start, end + 1);
    }

    private static Map<String, Object> merge(Map<String, Object> generated, Map<String, Object> fixed) {
        Map<String, Object> result = new LinkedHashMap<>(generated);
        fixed.forEach((key, value) -> {
            if (!"properties".equals(key) && !"required".equals(key)) result.put(key, value);
        });
        Map<String, Object> properties = new LinkedHashMap<>(fixedProperties(generated));
        properties.putAll(fixedProperties(fixed)); // configured fields always win
        result.put("properties", properties);
        LinkedHashSet<String> required = new LinkedHashSet<>(stringList(generated.get("required")));
        required.addAll(stringList(fixed.get("required")));
        if (!required.isEmpty()) result.put("required", new ArrayList<>(required));
        return result;
    }

    private static Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        copyText(source, result, "title", 100);
        copyText(source, result, "description", 500);
        copyText(source, result, "x-submit-button-text", 30);
        Map<String, Object> properties = new LinkedHashMap<>();
        fixedProperties(source).entrySet().stream().limit(12).forEach(entry -> {
            if (!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || !(entry.getValue() instanceof Map<?, ?> raw))
                return;
            Map<String, Object> field = new LinkedHashMap<>();
            String type = raw.get("type") == null ? "string" : String.valueOf(raw.get("type"));
            field.put("type", TYPES.contains(type) ? type : "string");
            copyText(raw, field, "title", 100);
            copyText(raw, field, "description", 300);
            String format = raw.get("format") == null ? null : String.valueOf(raw.get("format"));
            if ("date".equals(format) || "date-time".equals(format)) field.put("format", format);
            String component = raw.get("x-component") == null ? null : String.valueOf(raw.get("x-component"));
            if (component != null && COMPONENTS.contains(component)) field.put("x-component", component);
            if (raw.get("enum") instanceof List<?> values)
                field.put(
                        "enum",
                        values.stream()
                                .limit(20)
                                .filter(HumanInputNodeExecutor::scalar)
                                .toList());
            if (raw.get("x-enum-labels") instanceof List<?> labels)
                field.put(
                        "x-enum-labels",
                        labels.stream()
                                .limit(20)
                                .map(String::valueOf)
                                .map(v -> truncate(v, 100))
                                .toList());
            properties.put(entry.getKey(), field);
        });
        result.put("properties", properties);
        List<String> required = stringList(source.get("required")).stream()
                .filter(properties::containsKey)
                .distinct()
                .toList();
        if (!required.isEmpty()) result.put("required", required);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixedProperties(Map<String, Object> schema) {
        Object value = schema.get("properties");
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static List<String> stringList(Object value) {
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static boolean scalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static void copyText(Map<?, ?> from, Map<String, Object> to, String key, int max) {
        if (from.get(key) != null) to.put(key, truncate(String.valueOf(from.get(key)), max));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
