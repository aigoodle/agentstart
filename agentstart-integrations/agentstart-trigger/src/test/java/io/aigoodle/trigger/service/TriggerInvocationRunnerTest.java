package io.aigoodle.trigger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.trigger.api.InvocationStatus;
import io.aigoodle.trigger.dispatch.DispatchResult;
import io.aigoodle.trigger.dispatch.TriggerDispatcher;
import io.aigoodle.trigger.dispatch.TriggerDispatcherRegistry;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.entity.TriggerInvocationEntity;
import io.aigoodle.trigger.mapper.TriggerInvocationMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TriggerInvocationRunnerTest {

    @Test
    void restoresPersistedUserAndTenantAroundBackgroundDispatch() {
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();
        TriggerDispatcher dispatcher = new TriggerDispatcher() {
            @Override
            public String targetType() {
                return "agent";
            }

            @Override
            public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
                userId.set(UserContextHolder.currentUserId());
                tenantId.set(UserContextHolder.currentTenantId());
                return DispatchResult.ok("run-1", Map.of());
            }
        };
        TriggerInvocationRunner runner = new TriggerInvocationRunner(
                mock(TriggerInvocationMapper.class), new TriggerDispatcherRegistry(List.of(dispatcher)));
        TriggerEntity trigger = trigger("trigger-1", "agent");
        trigger.setUserId("user-7");
        trigger.setTenantId("tenant-7");

        runner.execute(trigger, invocation("invocation-1"), Map.of());

        assertThat(userId.get()).isEqualTo("user-7");
        assertThat(tenantId.get()).isEqualTo("tenant-7");
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void channelDispatchCanRunAsConnectionOwnerWithoutChangingTenant() {
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();
        TriggerDispatcher dispatcher = new TriggerDispatcher() {
            @Override
            public String targetType() {
                return "workflow";
            }

            @Override
            public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
                userId.set(UserContextHolder.currentUserId());
                tenantId.set(UserContextHolder.currentTenantId());
                return DispatchResult.ok("run-1", Map.of());
            }
        };
        TriggerInvocationRunner runner = new TriggerInvocationRunner(
                mock(TriggerInvocationMapper.class), new TriggerDispatcherRegistry(List.of(dispatcher)));
        TriggerEntity trigger = trigger("trigger-1", "workflow");
        trigger.setUserId("publisher-1");
        trigger.setTenantId("tenant-1");

        runner.execute(trigger, invocation("invocation-1"), Map.of(), "account-owner-1");

        assertThat(userId.get()).isEqualTo("account-owner-1");
        assertThat(tenantId.get()).isEqualTo("tenant-1");
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void recordsDispatcherFailureDetails() {
        TriggerInvocationMapper invocationMapper = mock(TriggerInvocationMapper.class);
        TriggerDispatcher dispatcher = dispatcherReturning(DispatchResult.builder()
                .success(false)
                .runId("run-1")
                .outputs(Map.of("accepted", false))
                .error("target rejected request")
                .build());
        TriggerInvocationRunner runner =
                new TriggerInvocationRunner(invocationMapper, new TriggerDispatcherRegistry(List.of(dispatcher)));
        TriggerInvocationEntity invocation = invocation("invocation-1");

        runner.execute(trigger("trigger-1", "agent"), invocation, Map.of());

        assertThat(invocation.getStatus()).isEqualTo(InvocationStatus.FAILED);
        assertThat(invocation.getError()).isEqualTo("target rejected request");
        assertThat(invocation.getRunId()).isEqualTo("run-1");
        assertThat(invocation.getOutputsJson()).isEqualTo("{\"accepted\":false}");
        verify(invocationMapper, times(2)).update(eq(invocation), any());
    }

    @Test
    void recordsFailedStatusWhenDispatcherThrows() {
        TriggerInvocationMapper invocationMapper = mock(TriggerInvocationMapper.class);
        TriggerDispatcherRegistry registry = new TriggerDispatcherRegistry(List.of(failingDispatcher()));
        TriggerInvocationRunner runner = new TriggerInvocationRunner(invocationMapper, registry);
        TriggerEntity trigger = trigger("trigger-1", "agent");
        TriggerInvocationEntity invocation = invocation("invocation-1");

        DispatchResult result = runner.execute(trigger, invocation, Map.of("query", "hello"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("dispatcher unavailable");
        assertThat(invocation.getStatus()).isEqualTo(InvocationStatus.FAILED);
        assertThat(invocation.getError()).isEqualTo("dispatcher unavailable");
        verify(invocationMapper, times(2)).update(eq(invocation), any());
    }

    private static TriggerDispatcher failingDispatcher() {
        return new TriggerDispatcher() {
            @Override
            public String targetType() {
                return "agent";
            }

            @Override
            public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
                throw new IllegalStateException("dispatcher unavailable");
            }
        };
    }

    private static TriggerDispatcher dispatcherReturning(DispatchResult result) {
        return new TriggerDispatcher() {
            @Override
            public String targetType() {
                return "agent";
            }

            @Override
            public DispatchResult dispatch(String targetId, Map<String, Object> inputs, String conversationId) {
                return result;
            }
        };
    }

    private static TriggerEntity trigger(String id, String targetType) {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId(id);
        trigger.setTargetType(targetType);
        trigger.setTargetId("target-1");
        return trigger;
    }

    private static TriggerInvocationEntity invocation(String id) {
        TriggerInvocationEntity invocation = new TriggerInvocationEntity();
        invocation.setTenantId("default");
        invocation.setId(id);
        invocation.setStatus(InvocationStatus.PENDING);
        return invocation;
    }
}
