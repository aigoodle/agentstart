package io.aigoodle.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class McpInvocationHeadersTest {
    @Test
    void restoresPreviousCredentialAfterNestedScopeAndException() {
        assertThat(McpInvocationHeaders.transportContext().get("authorization")).isNull();
        try (var outer = McpInvocationHeaders.withAuthorization("Bearer outer")) {
            assertThatThrownBy(() -> {
                        try (var inner = McpInvocationHeaders.withAuthorization("Bearer inner")) {
                            assertThat(McpInvocationHeaders.transportContext().get("authorization"))
                                    .isEqualTo("Bearer inner");
                            throw new IllegalStateException("failed request");
                        }
                    })
                    .isInstanceOf(IllegalStateException.class);
            assertThat(McpInvocationHeaders.transportContext().get("authorization"))
                    .isEqualTo("Bearer outer");
        }
        assertThat(McpInvocationHeaders.transportContext().get("authorization")).isNull();
    }
}
