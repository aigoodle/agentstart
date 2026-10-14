package io.aigoodle.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;

/**
 * Thin Jackson wrapper used across all modules so we never leak a checked
 * {@code JsonProcessingException} into business code.
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtils() {}

    public static ObjectMapper mapper() {
        return OBJECT_MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Failed to serialize " + value.getClass().getSimpleName() + " to JSON", exception);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return readValue(json, OBJECT_MAPPER.constructType(type), type.getSimpleName());
    }

    public static <T> T parse(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JavaType targetType = OBJECT_MAPPER.getTypeFactory().constructType(type);
        return readValue(json, targetType, targetType.toCanonical());
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return parse(json, new TypeReference<Map<String, Object>>() {});
    }

    public static <T> List<T> parseList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JavaType listType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
        return readValue(json, listType, "List<" + elementType.getSimpleName() + ">");
    }

    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse JSON into a tree", exception);
        }
    }

    public static <T> T convert(Object value, Class<T> type) {
        return OBJECT_MAPPER.convertValue(value, type);
    }

    private static <T> T readValue(String json, JavaType targetType, String targetDescription) {
        try {
            return OBJECT_MAPPER.readValue(json, targetType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse JSON into " + targetDescription, exception);
        }
    }
}
