package io.aigoodle.connector.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.aigoodle.connector.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelIdentityExecutionPolicyTest {
    private final ChannelIdentityExecutionPolicy policy = new ChannelIdentityExecutionPolicy();

    @Test
    void deniesWritesFromUnverifiedChannelIdentity() {
        assertThat(policy.evaluate(action(ConnectorRiskLevel.WRITE), request(false))
                        .allowed())
                .isFalse();
    }

    @Test
    void permitsReadsAndVerifiedWrites() {
        assertThat(policy.evaluate(action(ConnectorRiskLevel.READ), request(false))
                        .allowed())
                .isTrue();
        assertThat(policy.evaluate(action(ConnectorRiskLevel.WRITE), request(true))
                        .allowed())
                .isTrue();
    }

    private static ConnectorActionDefinition action(ConnectorRiskLevel risk) {
        return new ConnectorActionDefinition("a", "a", "a", null, null, true, Duration.ofSeconds(1), risk, Map.of());
    }

    private static ConnectorExecutionRequest request(boolean verified) {
        return new ConnectorExecutionRequest(
                new ConnectorKey("p", "c"),
                "a",
                null,
                null,
                Map.of(),
                new ConnectorExecutionContext(
                        null,
                        "t",
                        "u",
                        null,
                        null,
                        null,
                        null,
                        Map.of("channelId", "qqbot", "externalIdentityVerified", verified)));
    }
}
