package io.aigoodle.workflow.config;

import io.aigoodle.common.trigger.ScheduledTaskGateway;
import io.aigoodle.knowledge.reader.DocumentExtractor;
import io.aigoodle.knowledge.service.KnowledgeService;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.model.service.PromptTemplateService;
import io.aigoodle.workflow.engine.NodeExecutorRegistry;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.mapper.HumanInteractionMapper;
import io.aigoodle.workflow.mapper.WorkflowCheckpointMapper;
import io.aigoodle.workflow.mapper.WorkflowExecutionEventMapper;
import io.aigoodle.workflow.mapper.WorkflowMapper;
import io.aigoodle.workflow.mapper.WorkflowResumeSignalMapper;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import io.aigoodle.workflow.mapper.WorkflowRunNodeMapper;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.builtin.AgentNodeExecutor;
import io.aigoodle.workflow.node.builtin.AnswerNodeExecutor;
import io.aigoodle.workflow.node.builtin.ApprovalNodeExecutor;
import io.aigoodle.workflow.node.builtin.CodeNodeExecutor;
import io.aigoodle.workflow.node.builtin.DocumentExtractorNodeExecutor;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.HttpRequestNodeExecutor;
import io.aigoodle.workflow.node.builtin.HumanInputNodeExecutor;
import io.aigoodle.workflow.node.builtin.IfElseNodeExecutor;
import io.aigoodle.workflow.node.builtin.IterationNodeExecutor;
import io.aigoodle.workflow.node.builtin.KnowledgeRetrievalNodeExecutor;
import io.aigoodle.workflow.node.builtin.ListOperatorNodeExecutor;
import io.aigoodle.workflow.node.builtin.LlmNodeExecutor;
import io.aigoodle.workflow.node.builtin.ParameterExtractorNodeExecutor;
import io.aigoodle.workflow.node.builtin.QuestionClassifierNodeExecutor;
import io.aigoodle.workflow.node.builtin.ScheduleTriggerNodeExecutor;
import io.aigoodle.workflow.node.builtin.ServiceApiNodeExecutor;
import io.aigoodle.workflow.node.builtin.SleepUntilNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor;
import io.aigoodle.workflow.node.builtin.VariableAggregatorNodeExecutor;
import io.aigoodle.workflow.node.builtin.VariableAssignerNodeExecutor;
import io.aigoodle.workflow.node.builtin.WaitEventNodeExecutor;
import io.aigoodle.workflow.service.HumanInteractionService;
import io.aigoodle.workflow.service.HumanInteractionStore;
import io.aigoodle.workflow.service.PersistentWorkflowRunner;
import io.aigoodle.workflow.service.WorkflowCheckpointStore;
import io.aigoodle.workflow.service.WorkflowRuntimeMetrics;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for the workflow module. Registers built-in node executors, the
 * DAG engine and the workflow service. Nodes that need optional modules
 * (knowledge / tools / JEXL) are only wired when those modules are on the classpath.
 */
@AutoConfiguration(
        after = GoodleModelAutoConfiguration.class,
        afterName = {
            "io.aigoodle.connector.config.GoodleConnectorAutoConfiguration",
            "io.aigoodle.channel.config.GoodleChannelAutoConfiguration"
        })
@EnableConfigurationProperties(GoodleWorkflowProperties.class)
@EnableScheduling
@MapperScan("io.aigoodle.workflow.mapper")
public class GoodleWorkflowAutoConfiguration {

    // ---- executors with no extra dependencies ----

    @Bean
    public StartNodeExecutor startNodeExecutor() {
        return new StartNodeExecutor();
    }

    @Bean
    public EndNodeExecutor endNodeExecutor() {
        return new EndNodeExecutor();
    }

    @Bean
    public AnswerNodeExecutor answerNodeExecutor() {
        return new AnswerNodeExecutor();
    }

    @Bean
    public TemplateTransformNodeExecutor templateTransformNodeExecutor() {
        return new TemplateTransformNodeExecutor();
    }

    @Bean
    public VariableAggregatorNodeExecutor variableAggregatorNodeExecutor() {
        return new VariableAggregatorNodeExecutor();
    }

    @Bean
    public VariableAssignerNodeExecutor variableAssignerNodeExecutor() {
        return new VariableAssignerNodeExecutor();
    }

    @Bean
    public IfElseNodeExecutor ifElseNodeExecutor() {
        return new IfElseNodeExecutor();
    }

    @Bean
    public HumanInputNodeExecutor humanInputNodeExecutor(ModelService modelService) {
        return new HumanInputNodeExecutor(modelService);
    }

    @Bean
    public ApprovalNodeExecutor approvalNodeExecutor() {
        return new ApprovalNodeExecutor();
    }

    @Bean
    public WaitEventNodeExecutor waitEventNodeExecutor() {
        return new WaitEventNodeExecutor();
    }

    @Bean
    public SleepUntilNodeExecutor sleepUntilNodeExecutor() {
        return new SleepUntilNodeExecutor();
    }

    @Bean
    public HttpRequestNodeExecutor httpRequestNodeExecutor(GoodleWorkflowProperties properties) {
        return new HttpRequestNodeExecutor(properties.getHttpBaseUrl());
    }

    @Bean
    public ServiceApiNodeExecutor serviceApiNodeExecutor(GoodleWorkflowProperties properties) {
        return new ServiceApiNodeExecutor(properties.getHttpBaseUrl());
    }

    @Bean
    public ListOperatorNodeExecutor listOperatorNodeExecutor() {
        return new ListOperatorNodeExecutor();
    }

    // ---- iteration: needs the engine but must not fetch it at bean-creation time ----

    @Bean
    public IterationNodeExecutor iterationNodeExecutor(ObjectProvider<WorkflowEngine> engine) {
        return new IterationNodeExecutor(engine::getObject);
    }

    // ---- executors needing the model module ----

    @Bean
    public LlmNodeExecutor llmNodeExecutor(
            ModelService modelService,
            ObjectProvider<PromptTemplateService> promptTemplateService,
            MemoryManager conversationMemory) {
        return new LlmNodeExecutor(modelService, promptTemplateService.getIfAvailable(), conversationMemory);
    }

    @Bean
    public QuestionClassifierNodeExecutor questionClassifierNodeExecutor(
            ModelService modelService,
            ObjectProvider<PromptTemplateService> promptTemplateService,
            MemoryManager memoryManager) {
        return new QuestionClassifierNodeExecutor(modelService, promptTemplateService.getIfAvailable(), memoryManager);
    }

    @Bean
    public ParameterExtractorNodeExecutor parameterExtractorNodeExecutor(
            ModelService modelService, MemoryManager memoryManager) {
        return new ParameterExtractorNodeExecutor(modelService, memoryManager);
    }

    @Bean
    public ScheduleTriggerNodeExecutor scheduleTriggerNodeExecutor(
            ObjectProvider<ScheduledTaskGateway> scheduledTaskGateway, ObjectProvider<ModelService> modelService) {
        return new ScheduleTriggerNodeExecutor(scheduledTaskGateway, modelService);
    }

    @Bean
    public AgentNodeExecutor agentNodeExecutor(
            io.aigoodle.agent.runtime.AgentRuntime agentRuntime, ModelService modelService) {
        return new AgentNodeExecutor(agentRuntime, modelService);
    }

    // ---- optional: knowledge-based nodes ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KnowledgeService.class)
    static class KnowledgeNodeConfiguration {
        @Bean
        @ConditionalOnBean(KnowledgeService.class)
        public KnowledgeRetrievalNodeExecutor knowledgeRetrievalNodeExecutor(KnowledgeService knowledgeService) {
            return new KnowledgeRetrievalNodeExecutor(knowledgeService);
        }

        @Bean
        @ConditionalOnBean(DocumentExtractor.class)
        public DocumentExtractorNodeExecutor documentExtractorNodeExecutor(DocumentExtractor documentExtractor) {
            return new DocumentExtractorNodeExecutor(documentExtractor);
        }
    }

    // ---- optional: tool node (needs the tools module) ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.aigoodle.tool.ToolRegistry.class)
    static class ToolNodeConfiguration {
        @Bean
        @ConditionalOnBean(io.aigoodle.tool.ToolRegistry.class)
        public io.aigoodle.workflow.node.builtin.ToolNodeExecutor toolNodeExecutor(
                io.aigoodle.tool.ToolRegistry toolRegistry,
                io.aigoodle.tool.execution.ToolExecutionGateway executionGateway) {
            return new io.aigoodle.workflow.node.builtin.ToolNodeExecutor(toolRegistry, executionGateway);
        }
    }

    // ---- optional: provider-neutral connector node ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.aigoodle.connector.execution.ConnectorExecutionGateway")
    static class ConnectorNodeConfiguration {
        @Bean
        @ConditionalOnBean(type = "io.aigoodle.connector.execution.ConnectorExecutionGateway")
        public io.aigoodle.workflow.node.builtin.VideoGenerationNodeExecutor videoGenerationNodeExecutor(
                io.aigoodle.connector.execution.ConnectorExecutionGateway gateway) {
            return new io.aigoodle.workflow.node.builtin.VideoGenerationNodeExecutor(gateway);
        }

        @Bean
        @ConditionalOnBean(type = "io.aigoodle.connector.execution.ConnectorExecutionGateway")
        public io.aigoodle.workflow.node.builtin.ConnectorNodeExecutor connectorNodeExecutor(
                io.aigoodle.connector.execution.ConnectorExecutionGateway gateway,
                io.aigoodle.channel.ChannelConnectionService channelConnections,
                io.aigoodle.channel.ChannelRuntimeRegistry channelRuntimes) {
            return new io.aigoodle.workflow.node.builtin.ConnectorNodeExecutor(
                    gateway, channelConnections, channelRuntimes);
        }
    }

    // ---- optional: CODE node (needs Apache Commons JEXL 3 on the classpath) ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.commons.jexl3.JexlEngine")
    static class CodeNodeConfiguration {
        @Bean
        public CodeNodeExecutor codeNodeExecutor() {
            return new CodeNodeExecutor();
        }
    }

    // ---- engine + service ----

    @Bean
    @ConditionalOnMissingBean
    public WorkflowCheckpointStore workflowCheckpointStore(
            WorkflowCheckpointMapper checkpointMapper,
            WorkflowRunNodeMapper nodeMapper,
            WorkflowExecutionEventMapper eventMapper,
            WorkflowResumeSignalMapper signalMapper) {
        return new WorkflowCheckpointStore(checkpointMapper, nodeMapper, eventMapper, signalMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanInteractionStore humanInteractionStore(
            HumanInteractionMapper mapper,
            ObjectProvider<io.aigoodle.workflow.interaction.HumanInteractionNotifier> notifier) {
        return new HumanInteractionStore(mapper, notifier.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public PersistentWorkflowRunner persistentWorkflowRunner(
            WorkflowEngine engine,
            WorkflowCheckpointStore checkpointStore,
            HumanInteractionStore humanInteractions,
            ObjectProvider<io.aigoodle.workflow.service.WorkflowRuntimeMetrics> metrics) {
        return new PersistentWorkflowRunner(
                engine,
                checkpointStore,
                "workflow-" + java.util.UUID.randomUUID(),
                java.time.Duration.ofSeconds(30),
                humanInteractions,
                metrics.getIfAvailable(() -> io.aigoodle.workflow.service.WorkflowRuntimeMetrics.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanInteractionService humanInteractionService(
            HumanInteractionStore store, PersistentWorkflowRunner runner, ObjectProvider<MemoryManager> memory) {
        return new HumanInteractionService(store, runner, memory.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public io.aigoodle.workflow.service.WorkflowWaitRecoveryService workflowWaitRecoveryService(
            WorkflowCheckpointStore store,
            PersistentWorkflowRunner runner,
            ObjectProvider<io.aigoodle.workflow.service.WorkflowRuntimeMetrics> metrics) {
        return new io.aigoodle.workflow.service.WorkflowWaitRecoveryService(
                store, runner, metrics.getIfAvailable(() -> io.aigoodle.workflow.service.WorkflowRuntimeMetrics.NOOP));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.micrometer.core.instrument.MeterRegistry.class)
    static class WorkflowMetricsConfiguration {
        @Bean
        @ConditionalOnBean(io.micrometer.core.instrument.MeterRegistry.class)
        @ConditionalOnMissingBean(WorkflowRuntimeMetrics.class)
        WorkflowRuntimeMetrics workflowRuntimeMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
            return new io.aigoodle.workflow.service.MicrometerWorkflowRuntimeMetrics(registry);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public NodeExecutorRegistry nodeExecutorRegistry(List<NodeExecutor> executors) {
        return new NodeExecutorRegistry(executors);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngine workflowEngine(NodeExecutorRegistry registry) {
        return new WorkflowEngine(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowService workflowService(
            WorkflowMapper workflowMapper,
            WorkflowRunMapper runMapper,
            WorkflowEngine engine,
            PersistentWorkflowRunner persistentRunner,
            ObjectProvider<io.aigoodle.agent.service.AppConversationService> conversations) {
        return new WorkflowService(workflowMapper, runMapper, engine, persistentRunner, conversations);
    }
}
