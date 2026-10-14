package io.aigoodle.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The standalone spring-agent-start server. Run this class to get a working REST +
 * SSE backend on {@code http://localhost:18090/api/v1} without embedding any of the
 * modules into your own app.
 * <p>
 * All functional modules ship their own {@code @AutoConfiguration}, so just having
 * their jars on the classpath is enough — this class has no imports beyond {@code
 * @SpringBootApplication}. Configuration lives in {@code application.yml}; swap to
 * Postgres via {@code --spring.profiles.active=postgres}.
 */
@SpringBootApplication
public class GoodleServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodleServerApplication.class, args);
    }
}
