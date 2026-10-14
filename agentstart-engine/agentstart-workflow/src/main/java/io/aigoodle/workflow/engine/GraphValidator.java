package io.aigoodle.workflow.engine;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Publication and runtime safety checks for executable workflow DAGs. */
public final class GraphValidator {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{#([^.\\s#}]+)\\.[^#}]+#}}");
    private static final int MAX_NESTED_GRAPH_DEPTH = 8;
    private static final long MAX_NODE_TIMEOUT_MILLIS = Duration.ofHours(24).toMillis();
    private static final long MAX_NODE_ATTEMPTS = 20;

    public void validate(WorkflowGraph graph) {
        validate(graph, 0, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void validate(WorkflowGraph graph, int depth, Set<WorkflowGraph> visiting) {
        require(graph != null, "graph_required", "Workflow graph is required");
        require(
                depth <= MAX_NESTED_GRAPH_DEPTH,
                "nested_graph_too_deep",
                "Nested workflow graph depth exceeds " + MAX_NESTED_GRAPH_DEPTH);
        require(visiting.add(graph), "nested_graph_cycle", "Nested workflow graph contains itself");

        List<NodeDef> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<EdgeDef> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        Map<String, NodeDef> byId = new HashMap<>();
        for (NodeDef node : nodes) {
            require(node != null && hasText(node.getId()), "node_id_required", "Every node must have an id");
            require(node.getType() != null, "node_type_required", "Node " + node.getId() + " has no type");
            require(
                    depth == 0
                            || (node.getType() != NodeType.VIDEO_GENERATION
                                    && !(node.getType() == NodeType.CONNECTOR
                                            && "media.video".equals(node.getString("connectorId")))),
                    "nested_video_not_supported",
                    "Video generation requires a top-level workflow node; nested iteration does not persist asynchronous waits");
            require(
                    byId.putIfAbsent(node.getId(), node) == null,
                    "duplicate_node_id",
                    "Duplicate node id: " + node.getId());
        }
        List<NodeDef> starts =
                nodes.stream().filter(n -> n.getType() == NodeType.START).toList();
        List<NodeDef> ends =
                nodes.stream().filter(n -> n.getType() == NodeType.END).toList();
        require(starts.size() == 1, "invalid_start_count", "Workflow must contain exactly one START node");
        nodes.forEach(GraphValidator::validateNodeConfig);

        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        for (String id : byId.keySet()) {
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
        }
        Set<String> edgeKeys = new HashSet<>();
        for (EdgeDef edge : edges) {
            require(
                    edge != null && byId.containsKey(edge.getSource()),
                    "edge_source_not_found",
                    "Edge source does not exist: " + (edge == null ? null : edge.getSource()));
            require(
                    byId.containsKey(edge.getTarget()),
                    "edge_target_not_found",
                    "Edge target does not exist: " + edge.getTarget());
            require(!edge.getSource().equals(edge.getTarget()), "graph_cycle", "Self-loop at node " + edge.getSource());
            String edgeKey = edge.getSource() + "\u0000" + edge.getTarget() + "\u0000" + edge.getSourceHandle();
            require(
                    edgeKeys.add(edgeKey),
                    "duplicate_edge",
                    "Duplicate edge " + edge.getSource() + " -> " + edge.getTarget());
            validateHandle(byId.get(edge.getSource()), edge);
            outgoing.get(edge.getSource()).add(edge.getTarget());
            incoming.get(edge.getTarget()).add(edge.getSource());
        }
        require(
                incoming.get(starts.getFirst().getId()).isEmpty(),
                "start_has_incoming",
                "START node cannot have incoming edges");
        for (NodeDef end : ends) {
            require(outgoing.get(end.getId()).isEmpty(), "end_has_outgoing", "END node cannot have outgoing edges");
        }
        boolean hasTerminalAnswer = nodes.stream()
                .anyMatch(node -> node.getType() == NodeType.ANSWER
                        && outgoing.get(node.getId()).isEmpty());
        require(
                !ends.isEmpty() || hasTerminalAnswer,
                "invalid_terminal_count",
                "Workflow must contain an END node or a terminal ANSWER node");

        Set<String> reachable = reachable(starts.getFirst().getId(), outgoing);
        require(
                reachable.size() == nodes.size(),
                "unreachable_node",
                "Nodes unreachable from START: " + difference(byId.keySet(), reachable));
        ensureAcyclic(byId.keySet(), outgoing, incoming);
        validateVariableReferences(nodes, incoming);
        validateNestedGraphs(nodes, depth, visiting);
        visiting.remove(graph);
    }

    private static void validateHandle(NodeDef source, EdgeDef edge) {
        String handle = edge.getSourceHandle();
        if (source.getType() == NodeType.IF_ELSE || source.getType() == NodeType.QUESTION_CLASSIFIER) {
            require(
                    hasText(handle),
                    "branch_handle_required",
                    "Branch edge from " + source.getId() + " requires sourceHandle");
            Set<String> valid = validHandles(source);
            require(
                    valid.contains(handle),
                    "invalid_branch_handle",
                    "Branch edge from " + source.getId() + " uses unknown handle " + handle + "; expected one of "
                            + valid);
        } else {
            require(
                    !hasText(handle),
                    "unexpected_branch_handle",
                    "Node " + source.getId() + " does not produce branch handles");
        }
    }

    private static Set<String> validHandles(NodeDef source) {
        Set<String> handles = new HashSet<>();
        if (source.getType() == NodeType.IF_ELSE) {
            List<Map<String, Object>> cases = source.getMapList("cases");
            if (cases.isEmpty()) return Set.of("true", "false");
            for (Map<String, Object> candidate : cases) {
                Object id = candidate.get("id") == null ? candidate.get("caseId") : candidate.get("id");
                if (id != null && hasText(String.valueOf(id))) handles.add(String.valueOf(id));
            }
            handles.add("false");
        } else {
            for (Map<String, Object> category : source.getMapList("classes")) {
                Object id = category.get("id");
                if (id != null && hasText(String.valueOf(id))) handles.add(String.valueOf(id));
            }
        }
        require(
                !handles.isEmpty(),
                "branch_config_required",
                "Branch node " + source.getId() + " must define at least one selectable handle");
        return handles;
    }

    /** Minimal built-in schemas for settings that affect scheduler correctness. */
    private static void validateNodeConfig(NodeDef node) {
        Object timeout = node.get("timeoutMillis");
        require(
                timeout == null || positiveLong(timeout),
                "invalid_node_timeout",
                "Node " + node.getId() + " timeoutMillis must be a positive integer");
        require(
                timeout == null || parsedLong(timeout) <= MAX_NODE_TIMEOUT_MILLIS,
                "invalid_node_timeout",
                "Node " + node.getId() + " timeoutMillis exceeds 24 hours");
        Object retries = node.get("maxAttempts");
        require(
                retries == null || positiveLong(retries),
                "invalid_retry_policy",
                "Node " + node.getId() + " maxAttempts must be a positive integer");
        require(
                retries == null || parsedLong(retries) <= MAX_NODE_ATTEMPTS,
                "invalid_retry_policy",
                "Node " + node.getId() + " maxAttempts exceeds " + MAX_NODE_ATTEMPTS);
        Object initialBackoff = node.get("retryBackoffMillis");
        require(
                initialBackoff == null || nonNegativeLong(initialBackoff),
                "invalid_retry_policy",
                "Node " + node.getId() + " retryBackoffMillis must be a non-negative integer");
        Object maximumBackoff = node.get("maxRetryBackoffMillis");
        require(
                maximumBackoff == null || nonNegativeLong(maximumBackoff),
                "invalid_retry_policy",
                "Node " + node.getId() + " maxRetryBackoffMillis must be a non-negative integer");
        Object multiplier = node.get("retryBackoffMultiplier");
        require(
                multiplier == null
                        || multiplier instanceof Number number
                                && Double.isFinite(number.doubleValue())
                                && number.doubleValue() >= 1.0
                                && number.doubleValue() <= 10.0,
                "invalid_retry_policy",
                "Node " + node.getId() + " retryBackoffMultiplier must be between 1 and 10");
        Object legacyHttpRetries = node.get("maxRetries");
        require(
                legacyHttpRetries == null || nonNegativeLong(legacyHttpRetries),
                "invalid_retry_policy",
                "Node " + node.getId() + " maxRetries must be a non-negative integer");
        require(
                retries == null || legacyHttpRetries == null,
                "ambiguous_retry_policy",
                "Node " + node.getId() + " cannot configure both maxAttempts and maxRetries");
        if (node.getType() == NodeType.HTTP_REQUEST
                && legacyHttpRetries instanceof Number count
                && count.longValue() > 0) {
            String method = node.getString("method", "GET").toUpperCase(java.util.Locale.ROOT);
            boolean inherentlyIdempotent = method.equals("GET")
                    || method.equals("HEAD")
                    || method.equals("OPTIONS")
                    || method.equals("PUT")
                    || method.equals("DELETE");
            require(
                    inherentlyIdempotent || hasText(node.getString("idempotencyKey")),
                    "unsafe_http_retry",
                    "HTTP node " + node.getId() + " requires idempotencyKey before retrying " + method);
        }

        switch (node.getType()) {
            case VIDEO_GENERATION -> {
                Object selection = node.get("model");
                require(
                        selection instanceof Map<?, ?> model
                                && (model.get("modelId") instanceof String id && hasText(id)
                                        || model.get("providerName") instanceof String provider
                                                && hasText(provider)
                                                && model.get("modelName") instanceof String name
                                                && hasText(name)),
                        "video_model_required",
                        "Video generation node " + node.getId() + " requires a configured video model");
                require(
                        hasText(node.getString("prompt")),
                        "video_prompt_required",
                        "Video generation node " + node.getId() + " requires a prompt");
                require(
                        node.get("parameters") == null || node.get("parameters") instanceof Map<?, ?>,
                        "invalid_video_parameters",
                        "Video parameters must be an object");
            }
            case HUMAN_INPUT, APPROVAL -> {
                Object schema = node.get("inputSchema");
                require(
                        schema == null || schema instanceof Map<?, ?>,
                        "invalid_input_schema",
                        "Node " + node.getId() + " inputSchema must be an object");
                Object timeoutSeconds = node.get("timeoutSeconds");
                require(
                        timeoutSeconds == null || positiveLong(timeoutSeconds),
                        "invalid_wait_timeout",
                        "Node " + node.getId() + " timeoutSeconds must be positive");
                String strategy = node.getString("timeoutStrategy", "TIMEOUT").toUpperCase(java.util.Locale.ROOT);
                require(
                        strategy.equals("TIMEOUT") || strategy.equals("ESCALATE"),
                        "invalid_timeout_strategy",
                        "Node " + node.getId() + " timeoutStrategy must be TIMEOUT or ESCALATE");
                if (strategy.equals("ESCALATE")) {
                    require(
                            node.getType() == NodeType.APPROVAL,
                            "invalid_timeout_strategy",
                            "Only APPROVAL nodes support timeout escalation");
                    require(
                            hasText(node.getString("escalationCorrelationKey")),
                            "escalation_target_required",
                            "APPROVAL node " + node.getId() + " requires escalationCorrelationKey");
                }
            }
            case WAIT_EVENT -> require(
                    hasText(node.getString("correlationKey")),
                    "correlation_key_required",
                    "WAIT_EVENT node " + node.getId() + " requires correlationKey");
            case SLEEP_UNTIL -> {
                boolean hasUntil = hasText(node.getString("until"));
                Object delay = node.get("delayMillis");
                require(
                        hasUntil ^ delay != null,
                        "invalid_sleep_config",
                        "SLEEP_UNTIL node " + node.getId() + " requires exactly one of until or delayMillis");
                if (delay != null)
                    require(
                            positiveLong(delay),
                            "invalid_sleep_delay",
                            "SLEEP_UNTIL node " + node.getId() + " delayMillis must be positive");
                if (hasUntil) {
                    try {
                        java.time.Instant.parse(node.getString("until"));
                    } catch (java.time.format.DateTimeParseException exception) {
                        require(
                                false,
                                "invalid_sleep_until",
                                "SLEEP_UNTIL node " + node.getId() + " until must be ISO-8601");
                    }
                }
            }
            default -> {}
        }
    }

    private static boolean nonNegativeLong(Object value) {
        if (!(value instanceof Number number)) return false;
        long converted = number.longValue();
        return converted >= 0 && number.doubleValue() == converted;
    }

    private static boolean positiveLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value)) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static long parsedLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void ensureAcyclic(
            Collection<String> ids, Map<String, List<String>> outgoing, Map<String, List<String>> incoming) {
        Map<String, Integer> degree = new HashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String id : ids) {
            int value = incoming.get(id).size();
            degree.put(id, value);
            if (value == 0) ready.add(id);
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String target : outgoing.get(id)) {
                if (degree.compute(target, (key, value) -> value - 1) == 0) ready.add(target);
            }
        }
        require(visited == ids.size(), "graph_cycle", "Workflow contains a cycle; use ITERATION for loops");
    }

    private static void validateVariableReferences(List<NodeDef> nodes, Map<String, List<String>> incoming) {
        Map<String, Set<String>> ancestors = new HashMap<>();
        for (NodeDef node : nodes) ancestors.put(node.getId(), ancestors(node.getId(), incoming, new HashSet<>()));
        for (NodeDef node : nodes) {
            for (String reference : references(node.getData())) {
                if (reference.equals("sys") || reference.equals("var") || reference.equals(node.getId())) continue;
                require(
                        ancestors.get(node.getId()).contains(reference),
                        "invalid_variable_reference",
                        "Node " + node.getId() + " references non-upstream node " + reference);
            }
        }
    }

    private static Set<String> ancestors(String id, Map<String, List<String>> incoming, Set<String> found) {
        for (String parent : incoming.getOrDefault(id, List.of())) {
            if (found.add(parent)) ancestors(parent, incoming, found);
        }
        return found;
    }

    private void validateNestedGraphs(List<NodeDef> nodes, int depth, Set<WorkflowGraph> visiting) {
        for (NodeDef node : nodes) {
            if (node.getType() == NodeType.ITERATION) {
                Object nested = node.get("subGraph");
                require(
                        nested != null,
                        "iteration_graph_required",
                        "ITERATION node " + node.getId() + " requires subGraph");
                WorkflowGraph child;
                try {
                    child = nested instanceof WorkflowGraph graph
                            ? graph
                            : io.aigoodle.common.util.JsonUtils.parse(
                                    io.aigoodle.common.util.JsonUtils.toJson(nested), WorkflowGraph.class);
                } catch (RuntimeException exception) {
                    throw new PlatformException(
                            "invalid_iteration_graph",
                            "ITERATION node " + node.getId() + " has an invalid subGraph",
                            exception);
                }
                validate(child, depth + 1, visiting);
            }
        }
    }

    private static Set<String> references(Object value) {
        Set<String> result = new HashSet<>();
        collectReferences(value, result);
        return result;
    }

    private static void collectReferences(Object value, Set<String> result) {
        if (value instanceof String text) {
            Matcher matcher = VARIABLE.matcher(text);
            while (matcher.find()) result.add(matcher.group(1));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectReferences(item, result));
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collectReferences(item, result));
        }
    }

    private static Set<String> reachable(String start, Map<String, List<String>> outgoing) {
        Set<String> result = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (result.add(id)) queue.addAll(outgoing.getOrDefault(id, List.of()));
        }
        return result;
    }

    private static Set<String> difference(Collection<String> all, Set<String> included) {
        Set<String> result = new HashSet<>(all);
        result.removeAll(included);
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) throw new PlatformException(code, message, null);
    }
}
