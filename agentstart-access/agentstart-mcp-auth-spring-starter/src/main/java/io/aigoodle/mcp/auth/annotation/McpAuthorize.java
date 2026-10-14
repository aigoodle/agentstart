package io.aigoodle.mcp.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Protects an MCP tool class or method and optionally requires roles/scopes. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface McpAuthorize {

    String[] roles() default {};

    String[] scopes() default {};

    Match roleMatch() default Match.ALL;

    Match scopeMatch() default Match.ALL;

    enum Match {
        ALL,
        ANY
    }
}
