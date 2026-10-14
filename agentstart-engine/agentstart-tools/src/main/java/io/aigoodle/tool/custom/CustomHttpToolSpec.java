package io.aigoodle.tool.custom;

import java.util.LinkedHashMap;
import java.util.Map;

/** Durable definition created from the tool-management page. */
public class CustomHttpToolSpec {
    private String name;
    private String description;
    private String method = "POST";
    private String url;
    private String inputSchema = "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":true}";
    private Map<String, String> headers = new LinkedHashMap<>();
    private boolean enabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
