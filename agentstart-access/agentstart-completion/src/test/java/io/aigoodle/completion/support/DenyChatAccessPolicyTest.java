package io.aigoodle.completion.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.exception.PlatformException;
import org.junit.jupiter.api.Test;

class DenyChatAccessPolicyTest {

    private final ChatAccessPolicy policy = new DenyChatAccessPolicy();

    @Test
    void rejectsUnconfiguredDebugAccess() {
        assertThatThrownBy(() -> policy.authorizeDebug("app-1", null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("ChatAccessPolicy");
    }
}
