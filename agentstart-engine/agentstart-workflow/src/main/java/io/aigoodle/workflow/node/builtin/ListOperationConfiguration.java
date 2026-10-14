package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Normalized operation and parameters for a list-operator workflow node. */
record ListOperationConfiguration(
        Operation operation, String field, String expectedValue, SortDirection sortDirection, int limit) {

    static ListOperationConfiguration from(NodeDef node) {
        return new ListOperationConfiguration(
                Operation.from(node.getString("operation", "limit")),
                node.getString("field"),
                node.getString("value"),
                SortDirection.from(node.getString("order", "asc")),
                node.getInt("size", 10));
    }

    List<Object> apply(List<?> inputItems) {
        List<Object> items = new ArrayList<>(inputItems);
        return switch (operation) {
            case FILTER -> filter(items);
            case SORT -> sort(items);
            case LIMIT -> limit(items);
            case DISTINCT -> distinct(items);
            case PASSTHROUGH -> items;
        };
    }

    private List<Object> filter(List<Object> items) {
        if (field == null || expectedValue == null) {
            return items;
        }
        List<Object> matchingItems = new ArrayList<>();
        for (Object item : items) {
            Object candidateValue = item instanceof Map<?, ?> fields ? fields.get(field) : item;
            if (candidateValue != null && expectedValue.equals(String.valueOf(candidateValue))) {
                matchingItems.add(item);
            }
        }
        return matchingItems;
    }

    private List<Object> sort(List<Object> items) {
        Comparator<Object> comparator =
                (leftItem, rightItem) -> compare(sortableValue(leftItem), sortableValue(rightItem));
        items.sort(sortDirection == SortDirection.DESCENDING ? comparator.reversed() : comparator);
        return items;
    }

    private List<Object> limit(List<Object> items) {
        if (limit <= 0 || limit >= items.size()) {
            return items;
        }
        return new ArrayList<>(items.subList(0, limit));
    }

    private static List<Object> distinct(List<Object> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    private Object sortableValue(Object item) {
        return field != null && item instanceof Map<?, ?> fields ? fields.get(field) : item;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left instanceof Comparable comparable && left.getClass().equals(right.getClass())) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    enum Operation {
        FILTER,
        SORT,
        LIMIT,
        DISTINCT,
        PASSTHROUGH;

        static Operation from(String configuredOperation) {
            if (configuredOperation == null) {
                return LIMIT;
            }
            return switch (configuredOperation.trim().toLowerCase(Locale.ROOT)) {
                case "filter" -> FILTER;
                case "sort" -> SORT;
                case "limit" -> LIMIT;
                case "distinct" -> DISTINCT;
                default -> PASSTHROUGH;
            };
        }
    }

    enum SortDirection {
        ASCENDING,
        DESCENDING;

        static SortDirection from(String configuredOrder) {
            return "desc".equalsIgnoreCase(configuredOrder) ? DESCENDING : ASCENDING;
        }
    }
}
