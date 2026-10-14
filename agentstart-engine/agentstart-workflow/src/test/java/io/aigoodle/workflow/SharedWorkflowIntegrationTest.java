package io.aigoodle.workflow;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.persistence.TenantSqlScope;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.mapper.WorkflowMapper;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = WorkflowTestApplication.class, properties = "spring-agent.tools.builtin=false")
class SharedWorkflowIntegrationTest {
    @org.springframework.test.context.bean.override.convention.TestBean(methodName = "resourceProbe")
    io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor templateTransformNodeExecutor;

    static io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor resourceProbe() {
        return new io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor() {
            @Override
            public io.aigoodle.workflow.node.NodeResult execute(
                    io.aigoodle.workflow.graph.NodeDef node, io.aigoodle.workflow.node.ExecutionContext context) {
                assertThat(context.getTenantId()).isEqualTo("consumer");
                assertThat(context.resourceTenant()).isEqualTo("owner");
                String resource = context.withResourceTenant(UserContextHolder::currentTenantId);
                assertThat(resource).isEqualTo("owner");
                assertThat(UserContextHolder.currentTenantId()).isEqualTo("consumer");
                return io.aigoodle.workflow.node.NodeResult.of("text", "owner resources; consumer data");
            }
        };
    }

    @org.springframework.test.context.bean.override.convention.TestBean(methodName = "offlineHttp")
    io.aigoodle.workflow.node.builtin.HttpRequestNodeExecutor httpRequestNodeExecutor;

    @org.springframework.test.context.bean.override.convention.TestBean(methodName = "offlineApi")
    io.aigoodle.workflow.node.builtin.ServiceApiNodeExecutor serviceApiNodeExecutor;

    static io.aigoodle.workflow.node.builtin.HttpRequestNodeExecutor offlineHttp() {
        var node = org.mockito.Mockito.mock(io.aigoodle.workflow.node.builtin.HttpRequestNodeExecutor.class);
        org.mockito.Mockito.when(node.type()).thenReturn(io.aigoodle.workflow.graph.NodeType.HTTP_REQUEST);
        return node;
    }

    static io.aigoodle.workflow.node.builtin.ServiceApiNodeExecutor offlineApi() {
        var node = org.mockito.Mockito.mock(io.aigoodle.workflow.node.builtin.ServiceApiNodeExecutor.class);
        org.mockito.Mockito.when(node.type()).thenReturn(io.aigoodle.workflow.graph.NodeType.SERVICE_API);
        return node;
    }

    @Autowired
    AppMapper apps;

    @Autowired
    WorkflowMapper workflows;

    @Autowired
    WorkflowService service;

    @Autowired
    io.aigoodle.workflow.mapper.WorkflowRunMapper runs;

    @Test
    void sharedPublishedGraphRunsWithConsumerTenantAndLeavesGuardEnabled() throws Exception {
        String id = UUID.randomUUID().toString();
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setTenantId("owner");
        app.setName("Shared");
        app.setMode("chatflow");
        app.setPublished(true);
        app.setVisibility("GLOBAL");
        app.setDataAccessMode("ALL");
        app.setWorkflowId(id);
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setId(id);
        workflow.setAppId(id);
        workflow.setTenantId("owner");
        workflow.setName("Shared");
        workflow.setMode("chatflow");
        workflow.setVersion("published-test");
        workflow.setGraph(
                new ObjectMapper()
                        .readTree(
                                """
                {"_resourceTenantId":"attacker","nodes":[{"id":"start","type":"START","data":{}},
                  {"id":"wait","type":"HUMAN_INPUT","data":{}},
                  {"id":"probe","type":"TEMPLATE_TRANSFORM","data":{}},
                  {"id":"answer","type":"ANSWER","data":{"answer":"{{#probe.text#}}"}}],
                 "edges":[{"source":"start","target":"wait"},{"source":"wait","target":"probe"},
                          {"source":"probe","target":"answer"}]}
                """));
        UserContextHolder.runAs(
                CurrentUser.builder().tenantId("owner").userId("owner-user").build(), () -> {
                    apps.insert(app);
                    workflows.insert(workflow);
                });
        var caller = CurrentUser.builder()
                .tenantId("consumer")
                .userId("consumer-user")
                .build();
        UserContextHolder.runAs(caller, () -> {
            var waiting = service.runSharedPublished(
                    id,
                    "owner",
                    Map.of("_memory_tenant_id", "owner"),
                    UUID.randomUUID().toString(),
                    null,
                    null);
            assertThat(waiting.getStatus()).isEqualTo(io.aigoodle.workflow.engine.WorkflowRunStatus.WAITING);
            var result = service.signal(
                            "consumer",
                            waiting.getRunId(),
                            waiting.getWaitRequest().resumeToken(),
                            UUID.randomUUID().toString(),
                            Map.of())
                    .runResult();
            assertThat(result.isSuccess()).as(result.getError()).isTrue();
            assertThat(UserContextHolder.get()).isSameAs(caller);
            assertThat(TenantSqlScope.isBypassed()).isFalse();
            var run = runs.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            io.aigoodle.workflow.entity.WorkflowRunEntity>()
                    .eq(io.aigoodle.workflow.entity.WorkflowRunEntity::getTenantId, "consumer")
                    .eq(io.aigoodle.workflow.entity.WorkflowRunEntity::getId, result.getRunId()));
            assertThat(run).isNotNull();
            assertThat(run.getInputsJson()).contains("consumer").doesNotContain("\"_memory_tenant_id\":\"owner\"");
            assertThatThrownBy(() -> service.require("owner", id)).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service.runSharedPublished("missing", "owner", Map.of(), "c", null, null))
                    .hasMessageContaining("不可访问");
        });
    }
}
