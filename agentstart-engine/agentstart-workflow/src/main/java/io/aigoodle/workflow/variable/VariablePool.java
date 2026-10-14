package io.aigoodle.workflow.variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;

/**
 * The runtime variable store of a workflow execution. Holds system variables
 * (under the {@code sys} namespace) and the outputs of every executed node, keyed by
 * node id. Values are addressed by dotted path: {@code "sys.query"},
 * {@code "llm.text"}, {@code "http.body"}.
 * <p>
 * Backed by {@link ConcurrentHashMap} so worker threads running parallel nodes can
 * safely read while the engine's coordinator writes another node's outputs.
 */
public class VariablePool {

    public static final String SYS = "sys";

    /** namespace -> (key -> value); namespace is a node id or {@code sys}. */
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    private final Map<String, java.util.concurrent.ConcurrentSkipListMap<String, Object>> contributions =
            new ConcurrentHashMap<>();

    public void setSystem(String key, Object value) {
        put(SYS, key, value);
    }

    public void put(String namespace, String key, Object value) {
        if (value == null) {
            // ConcurrentHashMap forbids null values; get() on a missing key returns null,
            // so dropping the write preserves the observable behaviour of "read returns null".
            return;
        }
        store.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>()).put(key, value);
    }

    /**
     * Atomically combines a shared value using an explicit policy. APPEND always
     * produces a list; MERGE requires maps; REDUCER requires a supplied reducer.
     */
    public void merge(
            String namespace,
            String key,
            Object value,
            VariableMergeStrategy strategy,
            BinaryOperator<Object> reducer) {
        if (value == null) return;
        Map<String, Object> values = store.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>());
        values.compute(key, (ignored, existing) -> combine(existing, value, strategy, reducer, namespace, key));
    }

    /**
     * Deterministic parallel merge. Contributions are folded in writer-id order,
     * so completion timing cannot change APPEND, OVERWRITE, MERGE, or REDUCER results.
     */
    public void mergeFrom(
            String namespace,
            String key,
            String writerId,
            Object value,
            VariableMergeStrategy strategy,
            BinaryOperator<Object> reducer) {
        if (value == null || writerId == null || writerId.isBlank()) {
            throw new IllegalArgumentException("writerId and value are required for deterministic merge");
        }
        String contributionKey = namespace + "\u0000" + key;
        java.util.concurrent.ConcurrentSkipListMap<String, Object> writers = contributions.computeIfAbsent(
                contributionKey, ignored -> new java.util.concurrent.ConcurrentSkipListMap<>());
        writers.put(writerId, value);
        synchronized (writers) {
            Object combined = null;
            for (Object contribution : writers.values()) {
                combined = combine(combined, contribution, strategy, reducer, namespace, key);
            }
            store.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>())
                    .put(key, combined);
        }
    }

    private static Object combine(
            Object existing,
            Object incoming,
            VariableMergeStrategy strategy,
            BinaryOperator<Object> reducer,
            String namespace,
            String key) {
        if (existing == null || strategy == VariableMergeStrategy.OVERWRITE) return incoming;
        return switch (strategy) {
            case OVERWRITE -> incoming;
            case APPEND -> {
                List<Object> combined = new ArrayList<>();
                if (existing instanceof List<?> list) combined.addAll(list);
                else combined.add(existing);
                if (incoming instanceof List<?> list) combined.addAll(list);
                else combined.add(incoming);
                yield List.copyOf(combined);
            }
            case MERGE -> {
                if (!(existing instanceof Map<?, ?> left) || !(incoming instanceof Map<?, ?> right)) {
                    throw conflict(namespace, key, "MERGE requires map values");
                }
                Map<Object, Object> combined = new LinkedHashMap<>(left);
                right.forEach((mapKey, mapValue) -> {
                    if (combined.containsKey(mapKey) && !java.util.Objects.equals(combined.get(mapKey), mapValue)) {
                        throw conflict(namespace, key, "nested key conflict: " + mapKey);
                    }
                    combined.put(mapKey, mapValue);
                });
                yield Map.copyOf(combined);
            }
            case REJECT_ON_CONFLICT -> {
                if (!java.util.Objects.equals(existing, incoming)) throw conflict(namespace, key, "value conflict");
                yield existing;
            }
            case REDUCER -> {
                if (reducer == null) throw conflict(namespace, key, "REDUCER requires a reducer");
                yield java.util.Objects.requireNonNull(reducer.apply(existing, incoming), "Reducer returned null");
            }
        };
    }

    private static IllegalStateException conflict(String namespace, String key, String reason) {
        return new IllegalStateException("Variable merge failed for " + namespace + "." + key + ": " + reason);
    }

    public void putAll(String namespace, Map<String, Object> values) {
        if (values != null && !values.isEmpty()) {
            Map<String, Object> namespaceValues =
                    store.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>());
            values.forEach((key, value) -> {
                if (value != null) {
                    namespaceValues.put(key, value);
                }
            });
        }
    }

    public Map<String, Object> namespace(String namespace) {
        return store.getOrDefault(namespace, Map.of());
    }

    /** Resolve a dotted path like {@code "node.field"} or {@code "node.field.sub"}. */
    public Object get(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }
        Map<String, Object> namespaceValues = store.get(parts[0]);
        if (namespaceValues == null) {
            return null;
        }
        String rest = parts[1];
        if (namespaceValues.containsKey(rest)) {
            return namespaceValues.get(rest);
        }
        // descend into nested maps for "field.sub"
        String[] segments = rest.split("\\.");
        Object current = namespaceValues.get(segments[0]);
        for (int index = 1; index < segments.length && current instanceof Map<?, ?> nestedMap; index++) {
            current = nestedMap.get(segments[index]);
        }
        return current;
    }

    public String getString(String path) {
        Object value = get(path);
        return value == null ? null : String.valueOf(value);
    }

    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> copy = new java.util.LinkedHashMap<>();
        store.forEach((namespace, values) -> copy.put(namespace, new java.util.LinkedHashMap<>(values)));
        return copy;
    }

    public void restore(Map<String, ? extends Map<String, Object>> snapshot) {
        store.clear();
        contributions.clear();
        if (snapshot != null) snapshot.forEach(this::putAll);
    }
}
