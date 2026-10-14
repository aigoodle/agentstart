package io.aigoodle.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the workflow module, bound from {@code agent-boot.*}.
 *
 * <p>Kept intentionally under the short {@code agent-boot} prefix (rather than
 * the module-local {@code spring-agent.workflow.*}) because it holds values —
 * currently just the HTTP base URL — that are shared across executors and are
 * naturally owned by the deploying application, not the workflow module in
 * isolation.</p>
 */
@ConfigurationProperties(prefix = "agent-boot")
public class GoodleWorkflowProperties {

    /**
     * Base URL prepended to any HTTP node URL that isn't absolute
     * ({@code http://} / {@code https://}). Leave blank to require absolute
     * URLs everywhere. Trailing slash is optional — the executor normalises
     * it.
     *
     * <p>Example: with {@code http://127.0.0.1:8001/} configured here, a node
     * that stores {@code /api/foo} sends to {@code http://127.0.0.1:8001/api/foo}.</p>
     */
    private String httpBaseUrl = "";

    public String getHttpBaseUrl() {
        return httpBaseUrl;
    }

    public void setHttpBaseUrl(String httpBaseUrl) {
        this.httpBaseUrl = httpBaseUrl;
    }
}
