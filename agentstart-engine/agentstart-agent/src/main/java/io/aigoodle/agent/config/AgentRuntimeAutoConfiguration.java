package io.aigoodle.agent.config;

import io.aigoodle.agent.context.AgentContextCompactor;
import io.aigoodle.agent.context.AgentContextEngine;
import io.aigoodle.agent.context.DefaultAgentContextEngine;
import io.aigoodle.agent.context.ExtractiveAgentContextCompactor;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.hitl.AutoApproveGate;
import io.aigoodle.agent.mapper.AgentRunEventMapper;
import io.aigoodle.agent.mapper.AgentRunMapper;
import io.aigoodle.agent.mapper.AgentVersionMapper;
import io.aigoodle.agent.mapper.AppAnnotationMapper;
import io.aigoodle.agent.mapper.AppAnnotationSettingMapper;
import io.aigoodle.agent.mapper.AppApiTokenMapper;
import io.aigoodle.agent.mapper.AppConversationMapper;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.mapper.AppModelConfigMapper;
import io.aigoodle.agent.mapper.AppSiteMapper;
import io.aigoodle.agent.mapper.TagBindingMapper;
import io.aigoodle.agent.mapper.TagMapper;
import io.aigoodle.agent.runtime.AgentRunObserver;
import io.aigoodle.agent.runtime.AgentRunStore;
import io.aigoodle.agent.runtime.AgentRunToolExecutionListener;
import io.aigoodle.agent.runtime.AgentRuntimeExtension;
import io.aigoodle.agent.runtime.AgentRuntimeInterceptor;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.agent.runtime.JdbcAgentRunStore;
import io.aigoodle.agent.service.AgentExecutionService;
import io.aigoodle.agent.service.AgentService;
import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.agent.service.AppAnnotationService;
import io.aigoodle.agent.service.AppAnnotationSettingService;
import io.aigoodle.agent.service.AppApiTokenService;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.agent.service.AppDatasetService;
import io.aigoodle.agent.service.AppMetricsService;
import io.aigoodle.agent.service.AppModelConfigService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.agent.service.AppSiteService;
import io.aigoodle.agent.service.TagService;
import io.aigoodle.agent.strategy.AgentStrategy;
import io.aigoodle.agent.strategy.AgentStrategyRegistry;
import io.aigoodle.agent.strategy.FunctionCallingStrategy;
import io.aigoodle.agent.strategy.PlanExecuteStrategy;
import io.aigoodle.agent.strategy.ReActStrategy;
import io.aigoodle.knowledge.service.DatasetService;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.config.GoodleMemoryAutoConfiguration;
import io.aigoodle.model.config.GoodleModelAutoConfiguration;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.config.GoodleToolsAutoConfiguration;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import io.aigoodle.tool.execution.ToolExecutionListener;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for the enterprise agent runtime: strategies, memory, the
 * approval gate and the {@link AgentService}.
 */
@AutoConfiguration(
        after = {
            GoodleModelAutoConfiguration.class,
            GoodleToolsAutoConfiguration.class,
            GoodleMemoryAutoConfiguration.class
        })
@EnableConfigurationProperties(AgentProperties.class)
@MapperScan("io.aigoodle.agent.mapper")
public class AgentRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public io.aigoodle.common.context.DepartmentHierarchyProvider departmentHierarchyProvider() {
        return (tenantId, departmentId) -> java.util.Set.of();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.aigoodle.agent.service.AppPermissionService appPermissionService(
            AppMapper apps,
            io.aigoodle.agent.mapper.AppPermissionMapper permissions,
            io.aigoodle.common.context.DepartmentHierarchyProvider departments) {
        return new io.aigoodle.agent.service.AppPermissionService(apps, permissions, departments);
    }

    @Bean
    public ReActStrategy reActStrategy() {
        return new ReActStrategy();
    }

    @Bean
    public FunctionCallingStrategy functionCallingStrategy() {
        return new FunctionCallingStrategy();
    }

    @Bean
    public PlanExecuteStrategy planExecuteStrategy() {
        return new PlanExecuteStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentStrategyRegistry agentStrategyRegistry(List<AgentStrategy> strategies) {
        return new AgentStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalGate approvalGate() {
        return new AutoApproveGate();
    }

    @Bean
    @ConditionalOnMissingBean
    public AppModelConfigService appModelConfigService(AppModelConfigMapper mapper) {
        return new AppModelConfigService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRunStore agentRunStore(AgentRunMapper runMapper, AgentRunEventMapper eventMapper) {
        return new JdbcAgentRunStore(runMapper, eventMapper);
    }

    @Bean
    @ConditionalOnMissingBean(io.aigoodle.agent.service.AgentToolCatalog.class)
    public io.aigoodle.agent.service.AgentToolCatalog agentToolCatalog(
            AppMapper mapper, AppModelConfigService configs, ToolRegistry tools) {
        return new io.aigoodle.agent.service.AgentToolResolver(mapper, configs, tools);
    }

    @Bean
    public ToolExecutionListener agentRunToolExecutionListener(AgentRunStore runStore) {
        return new AgentRunToolExecutionListener(runStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentContextCompactor agentContextCompactor() {
        return new ExtractiveAgentContextCompactor();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentContextEngine agentContextEngine(
            MemoryManager memory, AgentProperties properties, AgentContextCompactor compactor) {
        return new DefaultAgentContextEngine(memory, properties, compactor);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentService agentService(
            AppMapper appMapper,
            AppModelConfigService modelConfigService,
            ModelService modelService,
            ToolRegistry toolRegistry,
            AgentStrategyRegistry strategyRegistry,
            MemoryManager memory,
            ApprovalGate approvalGate,
            AgentRunStore runStore,
            ToolExecutionGateway toolExecutionGateway,
            AgentContextEngine contextEngine,
            List<AgentRunObserver> runObservers,
            SkillResolver skillResolver) {
        return new AgentService(
                appMapper,
                modelConfigService,
                modelService,
                toolRegistry,
                strategyRegistry,
                memory,
                approvalGate,
                runStore,
                toolExecutionGateway,
                contextEngine,
                runObservers,
                skillResolver);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public AgentRuntimeRegistry agentRuntimeRegistry(
            AgentService nativeRuntime,
            List<AgentRuntimeExtension> extensions,
            List<AgentRuntimeInterceptor> interceptors) {
        return new AgentRuntimeRegistry(nativeRuntime, extensions, interceptors);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppService appService(AgentService agentService) {
        return new AppService(agentService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentVersionService agentVersionService(
            AgentVersionMapper versions,
            AppMapper apps,
            AppModelConfigService modelConfigs,
            AgentRuntimeRegistry runtimes) {
        return new AgentVersionService(versions, apps, modelConfigs, runtimes);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentExecutionService agentExecutionService(
            AgentService drafts, AgentVersionService versions, AgentRuntimeRegistry runtimes) {
        return new AgentExecutionService(drafts, versions, runtimes);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppAnnotationService appAnnotationService(AppAnnotationMapper mapper) {
        return new AppAnnotationService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppMetricsService appMetricsService(MemoryManager memoryManager, AppMapper appMapper) {
        return new AppMetricsService(memoryManager, appMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppConversationService appConversationService(
            AppConversationMapper conversationMapper, MemoryManager memoryManager) {
        return new AppConversationService(conversationMapper, memoryManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppApiTokenService appApiTokenService(AppApiTokenMapper mapper) {
        return new AppApiTokenService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppAnnotationSettingService appAnnotationSettingService(AppAnnotationSettingMapper mapper) {
        return new AppAnnotationSettingService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AppSiteService appSiteService(AppSiteMapper mapper) {
        return new AppSiteService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TagService tagService(
            TagMapper tagMapper,
            TagBindingMapper bindingMapper,
            AppMapper appMapper,
            org.springframework.beans.factory.ObjectProvider<io.aigoodle.agent.service.DatasetOwnershipResolver>
                    datasets) {
        return new TagService(tagMapper, bindingMapper, appMapper, datasets.getIfAvailable());
    }

    /**
     * Knowledge-base attachment on apps. Wired only when the knowledge module
     * is on the classpath — the agent module stays independent otherwise.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DatasetService.class)
    static class AppDatasetConfiguration {

        @Bean
        @ConditionalOnMissingBean(io.aigoodle.agent.service.DatasetOwnershipResolver.class)
        io.aigoodle.agent.service.DatasetOwnershipResolver datasetOwnershipResolver(DatasetService datasets) {
            return datasets::require;
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(DatasetService.class)
        public AppDatasetService appDatasetService(
                AppMapper appMapper, AppModelConfigService modelConfigService, DatasetService datasetService) {
            return new AppDatasetService(appMapper, modelConfigService, datasetService);
        }
    }
}
