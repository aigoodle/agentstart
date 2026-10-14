package io.aigoodle.workflow.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * A node in a workflow definition: an id, a type and a free-form config map
 * ({@code data}) interpreted by the node's executor.
 */
@Data
public class NodeDef {

    private String id;
    private NodeType type;
    private String title;

    /** Executor-specific configuration. */
    private Map<String, Object> data = new HashMap<>();

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMapList(String key) {
        Object value = data.get(key);
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    public static NodeDef of(String id, NodeType type) {
        NodeDef node = new NodeDef();
        node.setId(id);
        node.setType(type);
        return node;
    }

    public NodeDef with(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
