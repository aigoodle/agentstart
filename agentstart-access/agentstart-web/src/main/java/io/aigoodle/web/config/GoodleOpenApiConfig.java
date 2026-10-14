package io.aigoodle.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * OpenAPI 3 auto-config for agentstart-web. Loaded only when springdoc is on
 * the classpath (springdoc-openapi-starter-webflux-ui is declared optional in
 * the library pom; the runnable agentstart-server always pulls it in).
 * <p>
 * External clients hit {@code /v3/api-docs} for the raw spec (auto-generatable
 * TS/Python/Java clients) or {@code /swagger-ui.html} for a browsable UI.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
public class GoodleOpenApiConfig {

    @Bean
    public OpenAPI springAgentOpenApi(GoodleWebProperties properties) {
        String serverUrl = GoodleWebAutoConfiguration.normalizedBasePath(properties.getBasePath());
        if (serverUrl.isEmpty()) {
            serverUrl = "/";
        }
        return new OpenAPI()
                .info(new Info()
                        .title("spring-agent-start REST API")
                        .description(
                                """
                                REST + SSE surface of spring-agent-start — model providers, knowledge base,
                                workflow engine, agent runtime, tools, observability, triggers.

                                All responses use the envelope shape {code, message, data}. Success is
                                {code: 'ok', data: …}; errors are {code: '<code>', message: '<msg>', details?}.
                                See ApiErrorCode for the full enum.""")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("spring-agent-start")
                                .url("https://github.com/agent-start/spring-agent-start"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url(serverUrl).description("Base URL")))
                .tags(List.of(
                        new Tag().name("System").description("Health + module discovery"),
                        new Tag()
                                .name("Model")
                                .description("Provider credentials + model registration + connection test"),
                        new Tag().name("Knowledge").description("Datasets · documents · segments · retrieval"),
                        new Tag().name("Workflow").description("DAG persistence + synchronous + SSE run"),
                        new Tag().name("Agent").description("Agent CRUD + one-shot chat + SSE chat"),
                        new Tag().name("Tools").description("Tool discovery + test invocation"),
                        new Tag().name("Observability").description("LLM call metrics"),
                        new Tag().name("Trigger").description("Webhook/cron/event triggers")))
                .components(new Components());
    }
}
