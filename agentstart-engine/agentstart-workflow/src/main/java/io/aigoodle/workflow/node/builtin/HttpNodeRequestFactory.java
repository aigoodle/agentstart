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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compiles designer HTTP-node configuration into an executable JDK request. */
final class HttpNodeRequestFactory {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final String DEFAULT_USER_AGENT = "spring-agentstart-workflow/1.0";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_COUNT = 10;

    private final HttpRequestBodyFactory bodyFactory;
    private final String baseUrl;

    HttpNodeRequestFactory(String baseUrl) {
        this(new HttpRequestBodyFactory(), baseUrl);
    }

    HttpNodeRequestFactory(HttpRequestBodyFactory bodyFactory, String baseUrl) {
        this.bodyFactory = bodyFactory;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    PreparedRequest prepare(NodeDef node, ExecutionContext context) {
        String method = normalizedMethod(node);
        if (!ALLOWED_METHODS.contains(method)) {
            return PreparedRequest.failure("Unsupported HTTP method: " + method);
        }

        String configuredUrl = VariableResolver.render(node.getString("url", ""), context.getPool());
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return PreparedRequest.failure("HTTP node URL is empty");
        }

        String resolvedUrl = resolveUrl(configuredUrl);
        if (resolvedUrl == null) {
            return PreparedRequest.failure("HTTP node URL '" + configuredUrl
                    + "' is not absolute and no agent-boot.http-base-url is configured");
        }
        String requestUrl = appendQueryParameters(resolvedUrl, node.getMapList("parameters"), context);

        URI requestUri;
        try {
            requestUri = URI.create(requestUrl);
        } catch (IllegalArgumentException invalidUrl) {
            return PreparedRequest.failure("Invalid URL: " + requestUrl);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(Math.max(1, node.getInt("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS))));
        Set<String> configuredHeaders = applyHeaders(requestBuilder, node.get("headers"), context);
        if (!method.equals("GET")
                && !method.equals("HEAD")
                && !method.equals("OPTIONS")
                && !configuredHeaders.contains("idempotency-key")) {
            String configuredKey = node.getString("idempotencyKey");
            String key = configuredKey == null || configuredKey.isBlank()
                    ? context.getRunId() + ":" + node.getId()
                    : VariableResolver.render(configuredKey, context.getPool());
            requestBuilder.header("Idempotency-Key", key);
            configuredHeaders.add("idempotency-key");
        }
        applyAuthorization(requestBuilder, node.get("authorization"), context, configuredHeaders);

        HttpRequestBodyFactory.HttpRequestBody requestBody = bodyFactory.create(node, context);
        if (requestBody.failed()) {
            return PreparedRequest.failure(requestBody.error());
        }
        applyDefaultHeaders(requestBuilder, configuredHeaders, requestBody.contentType());
        requestBuilder.method(method, requestBody.publisher());

        int retryCount = Math.min(MAX_RETRY_COUNT, Math.max(0, node.getInt("maxRetries", 0)));
        return PreparedRequest.success(requestBuilder.build(), requestUrl, retryCount);
    }

    private static String normalizedMethod(NodeDef node) {
        return node.getString("method", "GET").trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveUrl(String configuredUrl) {
        String lowerCaseUrl = configuredUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) {
            return configuredUrl;
        }
        if (baseUrl.isEmpty()) {
            return null;
        }
        String relativePath = configuredUrl.startsWith("/") ? configuredUrl.substring(1) : configuredUrl;
        return baseUrl + "/" + relativePath;
    }

    private static String appendQueryParameters(
            String url, List<Map<String, Object>> parameters, ExecutionContext context) {
        List<String> encodedParameters = new ArrayList<>(parameters.size());
        for (Map<String, Object> parameter : parameters) {
            String parameterName = asString(parameter.get("name"));
            if (parameterName == null || parameterName.isBlank()) {
                continue;
            }
            String parameterValue = VariableResolver.render(asString(parameter.get("value")), context.getPool());
            encodedParameters.add(encode(parameterName.trim()) + "=" + encode(parameterValue));
        }
        if (encodedParameters.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + String.join("&", encodedParameters);
    }

    private static Set<String> applyHeaders(
            HttpRequest.Builder requestBuilder, Object headers, ExecutionContext context) {
        Set<String> configuredHeaderNames = new HashSet<>();
        if (headers instanceof List<?> headerRows) {
            applyHeaderRows(requestBuilder, headerRows, context, configuredHeaderNames);
        } else if (headers instanceof Map<?, ?> headerMap) {
            applyHeaderMap(requestBuilder, headerMap, context, configuredHeaderNames);
        }
        return configuredHeaderNames;
    }

    private static void applyHeaderRows(
            HttpRequest.Builder requestBuilder,
            List<?> headerRows,
            ExecutionContext context,
            Set<String> configuredHeaderNames) {
        for (Object row : headerRows) {
            if (!(row instanceof Map<?, ?> header)) {
                continue;
            }
            addHeader(
                    requestBuilder,
                    configuredHeaderNames,
                    asString(header.get("name")),
                    asString(header.get("value")),
                    context);
        }
    }

    private static void applyHeaderMap(
            HttpRequest.Builder requestBuilder,
            Map<?, ?> headers,
            ExecutionContext context,
            Set<String> configuredHeaderNames) {
        headers.forEach((name, value) ->
                addHeader(requestBuilder, configuredHeaderNames, asString(name), asString(value), context));
    }

    private static void addHeader(
            HttpRequest.Builder requestBuilder,
            Set<String> configuredHeaderNames,
            String name,
            String configuredValue,
            ExecutionContext context) {
        if (name == null || name.isBlank()) {
            return;
        }
        String headerName = name.trim();
        String headerValue = VariableResolver.render(configuredValue, context.getPool());
        requestBuilder.header(headerName, headerValue == null ? "" : headerValue);
        configuredHeaderNames.add(headerName.toLowerCase(Locale.ROOT));
    }

    private static void applyAuthorization(
            HttpRequest.Builder requestBuilder,
            Object authorization,
            ExecutionContext context,
            Set<String> configuredHeaderNames) {
        if (!(authorization instanceof Map<?, ?> configuration)
                || !"api_key".equalsIgnoreCase(asString(configuration.get("auth_type")))) {
            return;
        }
        String configuredValue = asString(configuration.get("api_key_value"));
        String apiKey = VariableResolver.render(configuredValue, context.getPool());
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        String configuredHeaderName = asString(configuration.get("api_key_header"));
        String headerName = configuredHeaderName == null || configuredHeaderName.isBlank()
                ? "Authorization"
                : configuredHeaderName.trim();
        String headerValue = authorizationValue(asString(configuration.get("api_key_header_prefix")), apiKey);
        requestBuilder.header(headerName, headerValue);
        configuredHeaderNames.add(headerName.toLowerCase(Locale.ROOT));
    }

    private static String authorizationValue(String prefix, String apiKey) {
        if ("bearer".equalsIgnoreCase(prefix)) {
            return "Bearer " + apiKey;
        }
        if ("basic".equalsIgnoreCase(prefix)) {
            return "Basic " + apiKey;
        }
        return apiKey;
    }

    private static void applyDefaultHeaders(
            HttpRequest.Builder requestBuilder, Set<String> configuredHeaders, String contentType) {
        if (contentType != null && !containsHeader(configuredHeaders, "Content-Type")) {
            requestBuilder.header("Content-Type", contentType);
        }
        if (!containsHeader(configuredHeaders, "User-Agent")) {
            requestBuilder.header("User-Agent", DEFAULT_USER_AGENT);
        }
    }

    private static boolean containsHeader(Set<String> lowerCaseNames, String headerName) {
        return lowerCaseNames.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record PreparedRequest(HttpRequest request, String url, int retryCount, String error) {

        static PreparedRequest success(HttpRequest request, String url, int retryCount) {
            return new PreparedRequest(request, url, retryCount, null);
        }

        static PreparedRequest failure(String error) {
            return new PreparedRequest(null, null, 0, error);
        }

        boolean failed() {
            return error != null;
        }
    }
}
