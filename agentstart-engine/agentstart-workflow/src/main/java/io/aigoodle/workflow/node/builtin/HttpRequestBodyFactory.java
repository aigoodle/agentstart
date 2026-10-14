package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the HTTP payload described by a workflow node's body configuration. */
final class HttpRequestBodyFactory {

    HttpRequestBody create(NodeDef node, ExecutionContext context) {
        String bodyType = node.getString("bodyType", "NONE").trim().toUpperCase(Locale.ROOT);
        Object configuredBody = node.get("body");

        // Legacy workflows stored a bare string instead of a body-type map.
        if (configuredBody instanceof String rawBody) {
            String renderedBody = VariableResolver.render(rawBody, context.getPool());
            return HttpRequestBody.withoutContentType(publisherFor(renderedBody));
        }

        Map<?, ?> bodyByType = configuredBody instanceof Map<?, ?> bodyMap ? bodyMap : Map.of();

        return switch (bodyType) {
            case "NONE" -> HttpRequestBody.empty();
            case "JSON" -> textBody(bodyByType.get("JSON"), "application/json; charset=utf-8", context);
            case "RAW" -> textBody(bodyByType.get("RAW"), "text/plain; charset=utf-8", context);
            case "X_WWW_FORM_URLENCODED" -> HttpRequestBody.withContentType(
                    publisherFor(encodeForm(asMapList(bodyByType.get(bodyType)), context)),
                    "application/x-www-form-urlencoded; charset=utf-8");
            case "FORM_DATA" -> multipartBody(asMapList(bodyByType.get(bodyType)), context);
            case "BINARY" -> textBody(bodyByType.get("BINARY"), "application/octet-stream", context);
            default -> HttpRequestBody.failure("Unknown bodyType: " + bodyType);
        };
    }

    private static HttpRequestBody textBody(Object configuredContent, String contentType, ExecutionContext context) {
        String content = VariableResolver.render(asString(configuredContent), context.getPool());
        return HttpRequestBody.withContentType(publisherFor(content), contentType);
    }

    private static String encodeForm(List<Map<String, Object>> fields, ExecutionContext context) {
        List<String> encodedFields = new ArrayList<>(fields.size());
        for (Map<String, Object> field : fields) {
            String fieldName = asString(field.get("name"));
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            String fieldValue = VariableResolver.render(asString(field.get("value")), context.getPool());
            encodedFields.add(encode(fieldName.trim()) + "=" + encode(fieldValue));
        }
        return String.join("&", encodedFields);
    }

    private static HttpRequestBody multipartBody(List<Map<String, Object>> fields, ExecutionContext context) {
        String boundary = "----spring-agentstart-boundary-" + Long.toHexString(System.nanoTime());
        StringBuilder content = new StringBuilder();
        for (Map<String, Object> field : fields) {
            String fieldName = asString(field.get("name"));
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            if ("FILE".equalsIgnoreCase(asString(field.get("type")))) {
                return HttpRequestBody.failure(
                        "FORM_DATA file uploads are not supported yet for field '" + fieldName + "'");
            }
            String fieldValue = VariableResolver.render(asString(field.get("value")), context.getPool());
            content.append("--")
                    .append(boundary)
                    .append("\r\n")
                    .append("Content-Disposition: form-data; name=\"")
                    .append(fieldName)
                    .append("\"\r\n\r\n")
                    .append(fieldValue == null ? "" : fieldValue)
                    .append("\r\n");
        }
        content.append("--").append(boundary).append("--\r\n");
        return HttpRequestBody.withContentType(
                HttpRequest.BodyPublishers.ofByteArray(content.toString().getBytes(StandardCharsets.UTF_8)),
                "multipart/form-data; boundary=" + boundary);
    }

    private static BodyPublisher publisherFor(String content) {
        if (content == null || content.isEmpty()) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record HttpRequestBody(BodyPublisher publisher, String contentType, String error) {

        static HttpRequestBody empty() {
            return withoutContentType(HttpRequest.BodyPublishers.noBody());
        }

        static HttpRequestBody withoutContentType(BodyPublisher publisher) {
            return new HttpRequestBody(publisher, null, null);
        }

        static HttpRequestBody withContentType(BodyPublisher publisher, String contentType) {
            return new HttpRequestBody(publisher, contentType, null);
        }

        static HttpRequestBody failure(String error) {
            return new HttpRequestBody(null, null, error);
        }

        boolean failed() {
            return error != null;
        }
    }
}
