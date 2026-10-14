package io.aigoodle.web.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the REST web layer.
 * <p>
 * Defaults are frontend-friendly for local development, with CORS open to all
 * origins. Applications that embed agentstart-web in a secured deployment can
 * lock these down via {@code spring-agent.web.*}.
 */
@Data
@ConfigurationProperties(prefix = "spring-agent.web")
public class GoodleWebProperties {

    /** Common prefix for web and completion endpoints; blank mounts them at the root. */
    private String basePath = "/agent-start";

    /** Allowed origins for CORS; {@code *} = any (default, since no login is expected). */
    private List<String> allowedOrigins = List.of("*");

    /**
     * How large a multipart upload can be (default 50 MiB). Configure the servlet
     * limits via {@code spring.servlet.multipart.max-file-size} in application.yml
     * so the value ends up in {@code MultipartResolver}.
     */
    private String maxUploadSize = "50MB";
}
