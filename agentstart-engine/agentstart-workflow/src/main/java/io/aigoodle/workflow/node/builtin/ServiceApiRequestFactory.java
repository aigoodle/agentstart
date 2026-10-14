package io.aigoodle.workflow.node.builtin;

import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds a service API request from designer configuration and workflow variables. */
final class ServiceApiRequestFactory {

    private final String baseUrl;

    ServiceApiRequestFactory(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    PreparedServiceRequest create(NodeDef node, ExecutionContext context) {
        String configuredUrl = VariableResolver.render(node.getString("url", ""), context.getPool());
        if (configuredUrl == null || configuredUrl.isBlank()) {
            throw new InvalidServiceRequestException("Service URL is empty");
        }

        String absoluteUrl = resolveAbsoluteUrl(configuredUrl);
        String requestUrl = appendQueryString(absoluteUrl, node.getMapList("parameters"), context);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(Math.max(1, node.getInt("timeoutSeconds", 30))));
            applyHeaders(requestBuilder, node.getMapList("headers"), context);
            String method = node.getString("method", "GET").trim().toUpperCase(Locale.ROOT);
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            return new PreparedServiceRequest(requestBuilder.build(), requestUrl);
        } catch (IllegalArgumentException exception) {
            throw new InvalidServiceRequestException("Invalid service request for URL: " + requestUrl, exception);
        }
    }

    private String resolveAbsoluteUrl(String configuredUrl) {
        String lowercaseUrl = configuredUrl.toLowerCase(Locale.ROOT);
        if (lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://")) {
            return configuredUrl;
        }
        if (baseUrl.isEmpty()) {
            throw new InvalidServiceRequestException("Service URL '" + configuredUrl
                    + "' is not absolute and no agent-boot.http-base-url is configured");
        }
        String relativePath = configuredUrl.startsWith("/") ? configuredUrl.substring(1) : configuredUrl;
        return baseUrl + "/" + relativePath;
    }

    private static String appendQueryString(
            String url, List<Map<String, Object>> parameters, ExecutionContext context) {
        List<String> encodedParameters = new ArrayList<>(parameters.size());
        for (Map<String, Object> parameter : parameters) {
            String name = asString(parameter.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            String renderedValue = VariableResolver.render(asString(parameter.get("value")), context.getPool());
            encodedParameters.add(encode(name.trim()) + "=" + encode(renderedValue));
        }
        if (encodedParameters.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + String.join("&", encodedParameters);
    }

    private static void applyHeaders(
            HttpRequest.Builder requestBuilder, List<Map<String, Object>> headers, ExecutionContext context) {
        for (Map<String, Object> header : headers) {
            String name = asString(header.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = VariableResolver.render(asString(header.get("value")), context.getPool());
            requestBuilder.header(name.trim(), value == null ? "" : value);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().replaceFirst("/+$", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record PreparedServiceRequest(HttpRequest request, String url) {}

    static final class InvalidServiceRequestException extends IllegalArgumentException {

        InvalidServiceRequestException(String message) {
            super(message);
        }

        InvalidServiceRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
