package io.aigoodle.web.config;

import io.aigoodle.web.common.GlobalExceptionHandler;
import io.aigoodle.web.support.ChannelAdministrationPolicy;
import io.aigoodle.web.support.ChannelOwnershipPolicy;
import io.aigoodle.web.support.ChannelRuntimeAdministrationPolicy;
import io.aigoodle.web.support.DefaultChannelAdministrationPolicy;
import io.aigoodle.web.support.DefaultChannelOwnershipPolicy;
import io.aigoodle.web.support.DefaultChannelRuntimeAdministrationPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-config for the REST web layer. Every controller lives under
 * {@code io.aigoodle.web.controller} and is picked up by component scan;
 * this class only wires the cross-cutting concerns (CORS, global exception handler,
 * properties). Runs on Spring MVC (servlet) — the reactive AI-chat SSE stack sits
 * in the sibling {@code agentstart-completion} module.
 * <p>
 * The auto-config works on <b>both</b> web stacks. Stack-specific beans live in the
 * nested {@link ServletSupport} / {@link ReactiveSupport} configuration classes, each
 * guarded by {@code @ConditionalOnClass}. A reactive host that excludes
 * {@code spring-boot-starter-web} (such as {@code agentstart-server}) therefore skips
 * the {@link WebMvcConfigurer} beans without ever loading a servlet-only class — and
 * still gets the equivalent path prefix + CORS setup from the {@link WebFluxConfigurer}
 * mirror. Keep stack-typed bean methods inside those nested classes: a servlet/reactive
 * type in a signature of the <em>outer</em> class would make bean-definition
 * introspection blow up with {@code NoClassDefFoundError} on the other stack.
 * <p>
 * Controllers here use class-level {@code @ConditionalOnBean(XxxService.class)} to
 * degrade gracefully when a runtime module is absent. That condition is evaluated
 * during component scan, so this autoconfig must be processed <em>after</em> the
 * runtime autoconfigs that register those service beans — otherwise the condition
 * sees no bean definition and the controller is silently dropped.
 * {@code afterName} pins the order without a compile-time module dependency
 * (all runtime modules are {@code optional}, so a hard class reference would break
 * consumers that opt out of, say, the workflow module).
 */
@AutoConfiguration(
        afterName = {
            "io.aigoodle.model.config.GoodleModelAutoConfiguration",
            "io.aigoodle.tool.config.GoodleToolsAutoConfiguration",
            "io.aigoodle.connector.config.GoodleConnectorAutoConfiguration",
            "io.aigoodle.channel.config.GoodleChannelAutoConfiguration",
            "io.aigoodle.plugin.config.PluginAutoConfiguration",
            "io.aigoodle.channel.nativebot.NativeChannelAutoConfiguration",
            "io.aigoodle.agent.config.AgentRuntimeAutoConfiguration",
            "io.aigoodle.knowledge.config.GoodleKnowledgeAutoConfiguration",
            "io.aigoodle.workflow.config.GoodleWorkflowAutoConfiguration",
            "io.aigoodle.trigger.config.GoodleTriggerAutoConfiguration",
            "io.aigoodle.observability.config.GoodleObservabilityAutoConfiguration"
        })
@EnableConfigurationProperties(GoodleWebProperties.class)
@ComponentScan(basePackages = {"io.aigoodle.web.controller", "io.aigoodle.web.service", "io.aigoodle.web.support"})
public class GoodleWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChannelAdministrationPolicy.class)
    public ChannelAdministrationPolicy channelAdministrationPolicy() {
        return new DefaultChannelAdministrationPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(ChannelOwnershipPolicy.class)
    public ChannelOwnershipPolicy channelOwnershipPolicy() {
        return new DefaultChannelOwnershipPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(ChannelRuntimeAdministrationPolicy.class)
    public ChannelRuntimeAdministrationPolicy channelRuntimeAdministrationPolicy() {
        return new DefaultChannelRuntimeAdministrationPolicy();
    }

    /**
     * Fixed URL prefix stamped in front of every controller under
     * {@code io.aigoodle.web.controller}. The starter is designed to
     * be embedded in third-party apps; a namespace-avoidance prefix keeps our
     * paths from colliding with the host's own routes. Hard-coded — the value
     * only exists to be unique, not to be tuned.
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler springAgentGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Resolves the configured allowed CORS origins, defaulting to a single
     * wildcard. Shared by the servlet and reactive CORS configurers so both
     * stacks stay behaviourally identical.
     */
    static String[] allowedOriginsOf(GoodleWebProperties properties) {
        return properties.getAllowedOrigins() == null
                        || properties.getAllowedOrigins().isEmpty()
                ? new String[] {"*"}
                : properties.getAllowedOrigins().toArray(new String[0]);
    }

    /**
     * Servlet-stack wiring: path prefix + CORS via {@link WebMvcConfigurer}.
     * Skipped entirely on reactive hosts where spring-webmvc is absent.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class ServletSupport {

        @Bean
        @ConditionalOnMissingBean(name = "springAgentPathPrefixConfigurer")
        public WebMvcConfigurer springAgentPathPrefixConfigurer(GoodleWebProperties properties) {
            return new WebMvcConfigurer() {
                @Override
                public void configurePathMatch(PathMatchConfigurer configurer) {
                    String basePath = normalizedBasePath(properties.getBasePath());
                    if (!basePath.isEmpty()) {
                        configurer.addPathPrefix(basePath, GoodleWebAutoConfiguration::isAgentStartController);
                    }
                }
            };
        }

        @Bean
        @ConditionalOnMissingBean(name = "springAgentCorsConfigurer")
        public WebMvcConfigurer springAgentCorsConfigurer(GoodleWebProperties properties) {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    String[] origins = allowedOriginsOf(properties);
                    boolean wildcard = origins.length == 1 && "*".equals(origins[0]);
                    var mapping = registry.addMapping("/**")
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                            .allowedHeaders("*")
                            .exposedHeaders("*")
                            .maxAge(3600);
                    if (wildcard) {
                        // allowCredentials + "*" is illegal at the Spring layer; drop credentials for wildcards.
                        mapping.allowedOriginPatterns("*").allowCredentials(false);
                    } else {
                        mapping.allowedOrigins(origins).allowCredentials(true);
                    }
                }
            };
        }
    }

    /**
     * Reactive-stack mirror of {@link ServletSupport}: the same {@code /agent-start}
     * path prefix and CORS policy via {@link WebFluxConfigurer}, so generic
     * {@code @RestController} methods from this module behave identically when the
     * host runs WebFlux/Netty with spring-boot-starter-web excluded (e.g.
     * {@code agentstart-server}). Skipped when spring-webflux is absent.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebFluxConfigurer.class)
    static class ReactiveSupport {

        @Bean
        @ConditionalOnMissingBean(name = "springAgentReactivePathPrefixConfigurer")
        public WebFluxConfigurer springAgentReactivePathPrefixConfigurer(GoodleWebProperties properties) {
            return new WebFluxConfigurer() {
                @Override
                public void configurePathMatching(
                        org.springframework.web.reactive.config.PathMatchConfigurer configurer) {
                    String basePath = normalizedBasePath(properties.getBasePath());
                    if (!basePath.isEmpty()) {
                        configurer.addPathPrefix(basePath, GoodleWebAutoConfiguration::isAgentStartController);
                    }
                }
            };
        }

        @Bean
        @ConditionalOnMissingBean(name = "springAgentReactiveCorsConfigurer")
        public WebFluxConfigurer springAgentReactiveCorsConfigurer(GoodleWebProperties properties) {
            return new WebFluxConfigurer() {
                @Override
                public void addCorsMappings(org.springframework.web.reactive.config.CorsRegistry registry) {
                    String[] origins = allowedOriginsOf(properties);
                    boolean wildcard = origins.length == 1 && "*".equals(origins[0]);
                    var mapping = registry.addMapping("/**")
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                            .allowedHeaders("*")
                            .exposedHeaders("*")
                            .maxAge(3600);
                    if (wildcard) {
                        mapping.allowedOriginPatterns("*").allowCredentials(false);
                    } else {
                        mapping.allowedOrigins(origins).allowCredentials(true);
                    }
                }
            };
        }
    }

    /**
     * Controllers contributed by connector starters are part of the same public API namespace as
     * the controllers declared in agentstart-web. Keeping this predicate in one place prevents a
     * callback URL advertised by the UI from differing from the route actually registered by the
     * host application.
     */
    private static boolean isAgentStartController(Class<?> type) {
        String packageName = type.getPackageName();
        return packageName.startsWith("io.aigoodle.web.controller") || packageName.startsWith("io.aigoodle.channel");
    }

    static String normalizedBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath.trim())) {
            return "";
        }
        String normalized = basePath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
