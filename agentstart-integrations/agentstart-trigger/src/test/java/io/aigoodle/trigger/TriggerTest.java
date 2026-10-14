package io.aigoodle.trigger;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.trigger.ScheduledTaskGateway;
import io.aigoodle.trigger.api.InvocationStatus;
import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.cron.CronTriggerScheduler;
import io.aigoodle.trigger.dispatch.DispatchResult;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.entity.TriggerInvocationEntity;
import io.aigoodle.trigger.event.EventTriggerBus;
import io.aigoodle.trigger.mapper.TriggerInvocationMapper;
import io.aigoodle.trigger.mapper.TriggerMapper;
import io.aigoodle.trigger.service.CreateTriggerRequest;
import io.aigoodle.trigger.service.TriggerInvocationRequest;
import io.aigoodle.trigger.service.TriggerService;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.service.WorkflowService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TriggerTestApplication.class)
class TriggerTest {

    @BeforeEach
    void authenticateTenant() {
        UserContextHolder.set(
                CurrentUser.builder().tenantId("t").userId("trigger-test").build());
    }

    @AfterEach
    void clearIdentity() {
        UserContextHolder.clear();
    }

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private TriggerService triggerService;

    @Autowired
    private EventTriggerBus eventBus;

    @Autowired
    private CronTriggerScheduler cronScheduler;

    @Autowired
    private TriggerMapper triggerMapper;

    @Autowired
    private TriggerInvocationMapper triggerInvocationMapper;

    @Autowired
    private ScheduledTaskGateway scheduledTaskGateway;

    /** An echo workflow: Start -> Template("Echo: {{#sys.text#}}") -> End(answer). */
    private String echoWorkflow() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("tpl", NodeType.TEMPLATE_TRANSFORM)
                .with("template", "Echo: {{#sys.text#}}")
                .with("outputKey", "msg"));
        graph.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("answer", "{{#tpl.msg#}}")));
        graph.addEdge(EdgeDef.of("start", "tpl"));
        graph.addEdge(EdgeDef.of("tpl", "end"));
        WorkflowEntity workflow = workflowService.save(
                "app-trigger-" + java.util.UUID.randomUUID(),
                UserContextHolder.currentTenantId(),
                "echo",
                "workflow",
                graph);
        return workflow.getId();
    }

    private TriggerEntity trigger(TriggerType type, Map<String, Object> config) {
        return triggerService.create(CreateTriggerRequest.builder()
                .tenantId(UserContextHolder.currentTenantId())
                .name(type + "-trigger")
                .type(type)
                .targetType("workflow")
                .targetId(echoWorkflow())
                .config(config)
                .enabled(true)
                .build());
    }

    @Test
    void webhookFiresWorkflowSynchronously() {
        TriggerEntity trigger = trigger(TriggerType.WEBHOOK, Map.of("path", "hook1", "token", "s3cr3t"));
        assertTrue(triggerService.findWebhook("hook1").isPresent());

        DispatchResult result = triggerService.fireSynchronously(
                TriggerInvocationRequest.webhook(trigger.getId(), Map.of("text", "hi")));
        assertTrue(result.isSuccess(), result.getError());
        assertEquals("Echo: hi", result.getOutputs().get("answer"));

        List<TriggerInvocationEntity> invocations = triggerService.invocations(trigger.getId());
        assertEquals(1, invocations.size());
        assertEquals(InvocationStatus.COMPLETED, invocations.get(0).getStatus());
        assertNotNull(invocations.get(0).getRunId());
    }

    @Test
    void tenantScopedApiHidesForeignTrigger() {
        TriggerEntity trigger = trigger(TriggerType.MANUAL, Map.of());

        assertTenantMismatch(() -> triggerService.require("other-tenant", trigger.getId()));
        assertTenantMismatch(() -> triggerService.setEnabled("other-tenant", trigger.getId(), false));
        triggerService.fireSynchronously(
                "t", TriggerInvocationRequest.manual(trigger.getId(), Map.of("text", "guard")));
        String invocationId =
                triggerService.invocations("t", trigger.getId()).get(0).getId();
        assertTenantMismatch(() -> triggerService.invocation("other-tenant", invocationId));
        assertTenantMismatch(() -> triggerInvocationMapper.selectById(invocationId));
        assertTrue(triggerService.require("t", trigger.getId()).getEnabled());
    }

    @Test
    void eventTriggerFiresOnPublish() {
        trigger(TriggerType.EVENT, Map.of("eventName", "order.created"));
        trigger(TriggerType.EVENT, Map.of("eventName", "order.cancelled")); // should not fire

        int fired = eventBus.publishSync("order.created", Map.of("text", "order-42"));
        assertEquals(1, fired);
    }

    @Test
    void asyncFireCompletesEventually() {
        TriggerEntity trigger = trigger(TriggerType.MANUAL, Map.of());
        String invocationId = triggerService.fireAsynchronously(
                TriggerInvocationRequest.manual(trigger.getId(), Map.of("text", "async")));
        assertNotNull(invocationId);

        TriggerInvocationEntity invocation = await(
                () -> {
                    TriggerInvocationEntity candidate = triggerService.invocation(invocationId);
                    return candidate != null && candidate.getStatus() == InvocationStatus.COMPLETED ? candidate : null;
                },
                5000);
        assertNotNull(invocation, "async invocation should complete");
        assertEquals("{\"answer\":\"Echo: async\"}", invocation.getOutputsJson());
    }

    @Test
    void replayReusesOriginalPayload() {
        TriggerEntity trigger = trigger(TriggerType.MANUAL, Map.of());
        DispatchResult first = triggerService.fireSynchronously(
                TriggerInvocationRequest.manual(trigger.getId(), Map.of("text", "replay-me")));
        String originalInvocationId =
                triggerService.invocations(trigger.getId()).get(0).getId();

        TriggerInvocationEntity replayed = triggerService.replay(originalInvocationId);
        assertEquals(InvocationStatus.COMPLETED, replayed.getStatus());
        assertEquals(originalInvocationId, replayed.getReplayOf());
        assertEquals("Echo: replay-me", first.getOutputs().get("answer"));
        assertTrue(replayed.getOutputsJson().contains("Echo: replay-me"));
    }

    @Test
    void cronTriggerSchedulesAndFires() {
        TriggerEntity trigger = trigger(TriggerType.CRON, Map.of("expression", "*/1 * * * * *"));
        assertTrue(cronScheduler.isScheduled(trigger.getId()), "cron trigger should be scheduled on create");

        List<TriggerInvocationEntity> invocations = await(
                () -> {
                    List<TriggerInvocationEntity> candidates = triggerService.invocations(trigger.getId());
                    return candidates.isEmpty() ? null : candidates;
                },
                6000);
        assertNotNull(invocations, "cron trigger should fire within a few seconds");
        assertTrue(invocations.stream().anyMatch(invocation -> "cron".equals(invocation.getSource())));
    }

    @Test
    void databaseLeaseLetsOnlyOneClusterNodeClaimAnOccurrence() {
        TriggerEntity trigger = trigger(TriggerType.CRON, Map.of("scheduleType", "CRON", "expression", "0 0 8 * * *"));
        trigger.setNextFireAt(LocalDateTime.now().minusSeconds(1));
        updateOwned(trigger);

        List<TriggerEntity> nodeOne = triggerService.claimDueSchedules("node-one", Duration.ofMinutes(1), 10);
        List<TriggerEntity> nodeTwo = triggerService.claimDueSchedules("node-two", Duration.ofMinutes(1), 10);

        assertEquals(
                1,
                nodeOne.stream().filter(t -> t.getId().equals(trigger.getId())).count());
        assertTrue(nodeTwo.stream().noneMatch(t -> t.getId().equals(trigger.getId())));
    }

    @Test
    void oneTimeScheduleDisablesItselfWhenClaimed() {
        TriggerEntity trigger = trigger(
                TriggerType.CRON,
                Map.of(
                        "scheduleType",
                        "ONCE",
                        "runAt",
                        java.time.OffsetDateTime.now().plusMinutes(5).toString()));
        trigger.setNextFireAt(LocalDateTime.now().minusSeconds(1));
        updateOwned(trigger);

        List<TriggerEntity> claimed = triggerService.claimDueSchedules("node-one", Duration.ofMinutes(1), 10);
        TriggerEntity claimedTrigger = claimed.stream()
                .filter(t -> t.getId().equals(trigger.getId()))
                .findFirst()
                .orElseThrow();

        assertFalse(claimedTrigger.getEnabled());
        assertNull(claimedTrigger.getNextFireAt());
        assertEquals(1L, claimedTrigger.getFireCount());
    }

    @Test
    void publishedStartNodeConfigurationBecomesDurableSchedule() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START)
                .with("triggersEnabled", true)
                .with(
                        "triggers",
                        Map.of(
                                "type", "schedule",
                                "name", "school-report",
                                "scheduleType", "CRON",
                                "expression", "0 0 8 * * *",
                                "timeZone", "Asia/Shanghai",
                                "payloadJson", "{\"schoolId\":\"001\"}")));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "end"));
        WorkflowEntity workflow = workflowService.save(
                "app-scheduled-" + java.util.UUID.randomUUID(), "t", "school workflow", "workflow", graph);

        TriggerEntity created = triggerService.syncPublishedWorkflowSchedule(workflow, workflowService);

        assertNotNull(created);
        assertEquals(workflow.getId(), created.getTargetId());
        assertNotNull(created.getNextFireAt());
        assertEquals("001", ((Map<?, ?>) triggerService.config(created).get("data")).get("schoolId"));
    }

    @Test
    void publishedStartNodeConnectorBecomesDurableChannelMessageTrigger() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START)
                .with("triggersEnabled", true)
                .with(
                        "triggers",
                        Map.of(
                                "type", "connector",
                                "provider", "openclaw",
                                "channelId", "qqbot",
                                "channelName", "QQ Bot",
                                "connectionId", "connection-1",
                                "connectionName", "招生机器人",
                                "messageTypes", List.of("TEXT", "IMAGE"))));
        graph.addNode(NodeDef.of("end", NodeType.END)
                .with(
                        "outputs",
                        Map.of(
                                "tenantId", "{{#start.triggers.tenantId#}}",
                                "instanceName", "{{#start.triggers.instanceName#}}",
                                "message", "{{#start.message.content#}}")));
        graph.addEdge(EdgeDef.of("start", "end"));
        WorkflowEntity workflow = workflowService.save(
                "app-channel-" + java.util.UUID.randomUUID(), "t", "channel workflow", "workflow", graph);

        TriggerEntity created = triggerService.syncPublishedWorkflowSchedule(workflow, workflowService);

        assertNotNull(created);
        assertEquals(TriggerType.CHANNEL_MESSAGE, created.getType());
        assertEquals(workflow.getId(), created.getTargetId());
        assertEquals("connection-1", triggerService.config(created).get("connectionId"));
        assertEquals(List.of("TEXT", "IMAGE"), triggerService.config(created).get("messageTypes"));

        DispatchResult result = triggerService.fireSynchronouslyAs(
                "t",
                "account-owner-1",
                new TriggerInvocationRequest(
                        created.getId(),
                        Map.of(
                                "triggers", Map.of("tenantId", "t", "instanceName", "招生机器人"),
                                "message", Map.of("content", "你好")),
                        "channel_message",
                        "channel:t:connection-1:c1"));
        assertTrue(result.isSuccess());
        assertEquals("t", result.getOutputs().get("tenantId"));
        assertEquals("招生机器人", result.getOutputs().get("instanceName"));
        assertEquals("你好", result.getOutputs().get("message"));
    }

    @Test
    void userCanListAndDeleteOnlyOwnedWorkflowSchedules() {
        UserContextHolder.set(
                CurrentUser.builder().tenantId("tenant-owned").userId("user-a").build());
        ScheduledTaskGateway.ScheduledTaskResult owned =
                scheduledTaskGateway.createTask(new ScheduledTaskGateway.CreateScheduledTaskCommand(
                        "tenant-owned",
                        "user-a",
                        "A 的日报",
                        echoWorkflow(),
                        Map.of("scheduleType", "CRON", "expression", "0 0 8 * * *")));
        ScheduledTaskGateway.ScheduledTaskResult otherUser =
                scheduledTaskGateway.createTask(new ScheduledTaskGateway.CreateScheduledTaskCommand(
                        "tenant-owned",
                        "user-b",
                        "B 的日报",
                        echoWorkflow(),
                        Map.of("scheduleType", "CRON", "expression", "0 0 9 * * *")));

        assertEquals(
                List.of(owned.id()),
                scheduledTaskGateway.listUserTasks("tenant-owned", "user-a").stream()
                        .map(ScheduledTaskGateway.ScheduledTaskCandidate::id)
                        .toList());
        ScheduledTaskGateway.ScheduledTaskResult updated =
                scheduledTaskGateway.updateOwnedTask(new ScheduledTaskGateway.UpdateScheduledTaskCommand(
                        "tenant-owned",
                        "user-a",
                        owned.id(),
                        "A 的九点日报",
                        owned.targetWorkflowId(),
                        Map.of("scheduleType", "CRON", "expression", "0 0 9 * * *")));
        assertEquals("A 的九点日报", updated.name());
        assertEquals(
                "0 0 9 * * *",
                triggerService.config(triggerService.require(owned.id())).get("expression"));
        assertThrows(
                io.aigoodle.common.exception.PlatformException.class,
                () -> scheduledTaskGateway.updateOwnedTask(new ScheduledTaskGateway.UpdateScheduledTaskCommand(
                        "tenant-owned",
                        "user-a",
                        otherUser.id(),
                        "越权修改",
                        otherUser.targetWorkflowId(),
                        Map.of("scheduleType", "CRON", "expression", "0 0 10 * * *"))));
        List<ScheduledTaskGateway.DeletedScheduledTask> deleted =
                scheduledTaskGateway.deleteOwnedTasks("tenant-owned", "user-a", List.of(owned.id(), otherUser.id()));

        assertEquals(
                List.of(owned.id()),
                deleted.stream()
                        .map(ScheduledTaskGateway.DeletedScheduledTask::id)
                        .toList());
        assertNotNull(triggerService.require(otherUser.id()));
        assertThrows(io.aigoodle.common.exception.PlatformException.class, () -> triggerService.require(owned.id()));
    }

    private <T> T await(Supplier<T> condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            T value = condition.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private void updateOwned(TriggerEntity trigger) {
        triggerMapper.update(
                trigger,
                new LambdaUpdateWrapper<TriggerEntity>()
                        .eq(TriggerEntity::getTenantId, trigger.getTenantId())
                        .eq(TriggerEntity::getId, trigger.getId()));
    }

    private static void assertTenantMismatch(org.junit.jupiter.api.function.Executable action) {
        Throwable failure = assertThrows(RuntimeException.class, action);
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertInstanceOf(SecurityException.class, root);
    }
}
