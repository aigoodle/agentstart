package io.aigoodle.trigger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerInvocationRequestTest {

    @Test
    void namedFactoriesDescribeTheInvocationSource() {
        assertThat(TriggerInvocationRequest.manual("trigger-1", null).source()).isEqualTo("manual");
        assertThat(TriggerInvocationRequest.webhook("trigger-1", Map.of()).source())
                .isEqualTo("webhook");
        assertThat(TriggerInvocationRequest.event("trigger-1", Map.of()).source())
                .isEqualTo("event");
        assertThat(TriggerInvocationRequest.cron("trigger-1").source()).isEqualTo("cron");
    }

    @Test
    void takesAPayloadSnapshotBeforeAsyncExecution() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "order-1");

        TriggerInvocationRequest request = TriggerInvocationRequest.event("trigger-1", payload);
        payload.put("orderId", "order-2");

        assertThat(request.payload()).containsEntry("orderId", "order-1");
        assertThatThrownBy(() -> request.payload().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingTriggerIdentity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TriggerInvocationRequest.manual(" ", Map.of()))
                .withMessage("triggerId must not be blank");
    }
}
