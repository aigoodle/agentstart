package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.agent.config.AgentProperties;
import io.aigoodle.agent.context.AgentContextEngine;
import io.aigoodle.agent.context.AgentContextRequest;
import io.aigoodle.agent.context.DefaultAgentContextEngine;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.entity.AppMode;
import io.aigoodle.agent.entity.AppModelConfigEntity;
import io.aigoodle.agent.hitl.ApprovalGate;
import io.aigoodle.agent.mapper.AppMapper;
import io.aigoodle.agent.runtime.AgentResumeCommand;
import io.aigoodle.agent.runtime.AgentRunEvent;
import io.aigoodle.agent.runtime.AgentRunObservation;
import io.aigoodle.agent.runtime.AgentRunObserver;
import io.aigoodle.agent.runtime.AgentRunSnapshot;
import io.aigoodle.agent.runtime.AgentRunStatus;
import io.aigoodle.agent.runtime.AgentRunStore;
import io.aigoodle.agent.runtime.AgentRuntime;
import io.aigoodle.agent.runtime.InMemoryAgentRunStore;
import io.aigoodle.agent.strategy.AgentRunContext;
import io.aigoodle.agent.strategy.AgentRunInterruptedException;
import io.aigoodle.agent.strategy.AgentStrategyRegistry;
import io.aigoodle.agent.strategy.ResumableAgentStrategy;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.skill.service.SkillResolution;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates agent persistence, runtime configuration and strategy execution. */
public class AgentService implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String DEFAULT_TENANT_ID = "default";
    private static final int MAX_HISTORY_SIZE = 500;

    private final AppMapper appMapper;
    private AppPermissionService appPermissions;

    /** Spring 注入；保留已有手动构造运行时的兼容性。 */
    @org.springframework.beans.factory.annotation.Autowired
    public void setAppPermissions(AppPermissionService appPermissions) {
        this.appPermissions = appPermissions;
    }

    public void requireWritable(AppEntity app) {
        if (appPermissions != null) appPermissions.requireWrite(app);
    }

    private List<AppEntity> readable(List<AppEntity> apps) {
        return appPermissions == null ? apps : appPermissions.filterReadable(apps);
    }

    private final AppModelConfigService modelConfigService;
    private final ModelService modelService;
    private final AgentStrategyRegistry strategyRegistry;
    private final MemoryManager memory;
    private final ApprovalGate approvalGate;
    private final AppCatalogUpdater catalogUpdater;
    private final AgentDefinitionFactory definitionFactory;
    private final AgentToolResolver toolResolver;
    private final AgentRunStore runStore;
    private final ToolExecutionGateway toolExecutionGateway;
    private final AgentContextEngine contextEngine;
    private final SkillResolver skillResolver;
    private final List<AgentRunObserver> runObservers;

    private record ActiveRunKey(String tenantId, String runId) {}

    private final ConcurrentHashMap<ActiveRunKey, Thread> activeRuns = new ConcurrentHashMap<>();

    public AgentService(
            AppMapper appMapper,
            AppModelConfigService modelConfigService,
            ModelService modelService,
            ToolRegistry toolRegistry,
            AgentStrategyRegistry strategyRegistry,
            MemoryManager memory,
            ApprovalGate approvalGate) {
        this(
                appMapper,
                modelConfigService,
                modelService,
                toolRegistry,
                strategyRegistry,
                memory,
                approvalGate,
                new InMemoryAgentRunStore(),
                ToolExecutionGateway.direct(),
                new DefaultAgentContextEngine(memory, new AgentProperties()),
                List.of(),
                SkillResolver.none());
    }

    public AgentService(
            AppMapper appMapper,
            AppModelConfigService modelConfigService,
            ModelService modelService,
            ToolRegistry toolRegistry,
            AgentStrategyRegistry strategyRegistry,
            MemoryManager memory,
            ApprovalGate approvalGate,
            AgentRunStore runStore) {
        this(
                appMapper,
                modelConfigService,
                modelService,
                toolRegistry,
                strategyRegistry,
                memory,
                approvalGate,
                runStore,
                ToolExecutionGateway.direct(),
                new DefaultAgentContextEngine(memory, new AgentProperties()),
                List.of(),
                SkillResolver.none());
    }

    public AgentService(
            AppMapper appMapper,
            AppModelConfigService modelConfigService,
            ModelService modelService,
            ToolRegistry toolRegistry,
            AgentStrategyRegistry strategyRegistry,
            MemoryManager memory,
            ApprovalGate approvalGate,
            AgentRunStore runStore,
            ToolExecutionGateway toolExecutionGateway) {
        this(
                appMapper,
                modelConfigService,
                modelService,
                toolRegistry,
                strategyRegistry,
                memory,
                approvalGate,
                runStore,
                toolExecutionGateway,
                new DefaultAgentContextEngine(memory, new AgentProperties()),
                List.of(),
                SkillResolver.none());
    }

    public AgentService(
            AppMapper appMapper,
            AppModelConfigService modelConfigService,
            ModelService modelService,
            ToolRegistry toolRegistry,
            AgentStrategyRegistry strategyRegistry,
            MemoryManager memory,
            ApprovalGate approvalGate,
            AgentRunStore runStore,
            ToolExecutionGateway toolExecutionGateway,
            AgentContextEngine contextEngine) {
        this(
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
                List.of(),
                SkillResolver.none());
    }

    public AgentService(
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
            List<AgentRunObserver> runObservers) {
        this(
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
                SkillResolver.none());
    }

    public AgentService(
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
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
        this.modelService = modelService;
        this.strategyRegistry = strategyRegistry;
        this.memory = memory;
        this.approvalGate = approvalGate;
        this.runStore = runStore;
        this.toolExecutionGateway = toolExecutionGateway;
        this.contextEngine = contextEngine;
        this.runObservers = runObservers == null ? List.of() : List.copyOf(runObservers);
        this.skillResolver = skillResolver == null ? SkillResolver.none() : skillResolver;
        this.catalogUpdater = new AppCatalogUpdater();
        this.definitionFactory = new AgentDefinitionFactory(modelConfigService);
        this.toolResolver = new AgentToolResolver(appMapper, modelConfigService, toolRegistry);
    }

    @Transactional
    public AppEntity create(SaveAppRequest request) {
        AppEntity agent = new AppEntity();
        agent.setTenantId(valueOrDefault(request.getTenantId(), DEFAULT_TENANT_ID));
        catalogUpdater.applyRequest(request, agent);
        agent.setDataAccessMode("ALL");
        appMapper.insert(agent);
        saveModelConfig(agent, request);
        return agent;
    }

    public AppEntity require(String agentId) {
        return require(UserContextHolder.currentTenantId(), agentId);
    }

    public AppEntity require(String tenantId, String agentId) {
        AppEntity agent = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, valueOrDefault(tenantId, DEFAULT_TENANT_ID))
                .eq(AppEntity::getId, agentId)
                .last("LIMIT 1"));
        if (agent == null) throw new PlatformException("app_not_found", "Application not found", null);
        if (appPermissions != null) appPermissions.requireRead(agent);
        return agent;
    }

    public List<AppEntity> list(String tenantId) {
        String effectiveTenant = valueOrDefault(tenantId, DEFAULT_TENANT_ID);
        return readable(appMapper.selectList(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, effectiveTenant)
                .orderByDesc(AppEntity::getCreatedAt)
                .orderByDesc(AppEntity::getId)));
    }

    /** Published workflow-mode applications available to tenant-scoped selectors. */
    public List<AppEntity> listPublishedWorkflowApps(String tenantId) {
        String effectiveTenant = valueOrDefault(tenantId, DEFAULT_TENANT_ID);
        return readable(appMapper.selectList(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, effectiveTenant)
                .eq(AppEntity::getMode, "workflow")
                .eq(AppEntity::getPublished, true)
                .isNotNull(AppEntity::getWorkflowId)
                .ne(AppEntity::getWorkflowId, "")
                .orderByAsc(AppEntity::getName)
                .orderByDesc(AppEntity::getUpdatedAt)
                .orderByDesc(AppEntity::getId)));
    }

    /** Resolve an internal app by stable code, preferring a tenant-owned override. */
    public AppEntity requireVisibleByCode(String appCode, String executionTenantId, String rootTenantId) {
        String code = valueOrDefault(appCode, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (code.isEmpty()) {
            throw new PlatformException("app_code_required", "应用编码不能为空", null);
        }
        String tenant = valueOrDefault(executionTenantId, DEFAULT_TENANT_ID);
        AppEntity owned = findByTenantAndCode(tenant, code);
        if (owned != null) {
            return requireRunnable(owned);
        }
        String root = valueOrDefault(rootTenantId, "root");
        AppEntity shared = io.aigoodle.persistence.TenantSqlScope.bypass(
                () -> appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, root)
                        .eq(AppEntity::getAppCode, code)
                        .eq(AppEntity::getVisibility, "GLOBAL")
                        .last("LIMIT 1")));
        if (shared == null || !"GLOBAL".equalsIgnoreCase(shared.getVisibility())) {
            throw new PlatformException("app_not_found", "应用不存在或无权访问", null);
        }
        return requireRunnable(shared);
    }

    /** Chat-only lookup; ordinary require/update APIs remain tenant-owned. */
    public AppEntity requireForChat(String tenantId, String appId) {
        AppEntity owned = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
                .eq(AppEntity::getId, appId));
        if (owned != null) {
            if (appPermissions != null) appPermissions.requireRead(owned);
            return owned;
        }
        AppEntity shared = io.aigoodle.persistence.TenantSqlScope.bypass(
                () -> appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                        .eq(AppEntity::getId, appId)
                        .eq(AppEntity::getVisibility, "GLOBAL")
                        .eq(AppEntity::getPublished, true)));
        if (shared == null) throw new PlatformException("app_not_found", "应用不存在或无权访问", null);
        return requireRunnable(shared);
    }

    private AppEntity findByTenantAndCode(String tenantId, String appCode) {
        return appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
                .eq(AppEntity::getAppCode, appCode)
                .last("LIMIT 1"));
    }

    private AppEntity requireRunnable(AppEntity app) {
        if (appPermissions != null) appPermissions.requireRead(app);
        if (!Boolean.TRUE.equals(app.getPublished()) || "disabled".equalsIgnoreCase(app.getStatus())) {
            throw new PlatformException("app_unavailable", "应用尚未发布或已停用", null);
        }
        return app;
    }

    @Transactional
    public AppEntity update(String agentId, SaveAppRequest request) {
        return update(UserContextHolder.currentTenantId(), agentId, request);
    }

    @Transactional
    public AppEntity update(String tenantId, String agentId, SaveAppRequest request) {
        AppEntity agent = require(tenantId, agentId);
        requireWritable(agent);
        request.setTenantId(agent.getTenantId());
        catalogUpdater.applyRequest(request, agent);
        appMapper.update(
                agent,
                new LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, agent.getTenantId())
                        .eq(AppEntity::getId, agent.getId()));
        saveModelConfig(agent, request);
        return agent;
    }

    @Transactional
    public void delete(String agentId) {
        delete(UserContextHolder.currentTenantId(), agentId);
    }

    @Transactional
    public void delete(String tenantId, String agentId) {
        AppEntity owned = require(tenantId, agentId);
        requireWritable(owned);
        if (appPermissions != null) appPermissions.deleteForApp(owned.getTenantId(), owned.getId());
        modelConfigService.deleteByAppId(owned.getTenantId(), owned.getId());
        appMapper.delete(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, owned.getTenantId())
                .eq(AppEntity::getId, owned.getId()));
    }

    public AppModelConfigEntity getModelConfig(String appId) {
        return getModelConfig(UserContextHolder.currentTenantId(), appId);
    }

    public AppModelConfigEntity getModelConfig(String tenantId, String appId) {
        AppEntity owned = require(tenantId, appId);
        return modelConfigService.findByAppId(owned.getTenantId(), owned.getId());
    }

    public AppEntity enrich(AppEntity agent) {
        return definitionFactory.enrich(agent);
    }

    @Transactional
    public AppEntity bindWorkflowId(String appId, String workflowId) {
        return bindWorkflowId(UserContextHolder.currentTenantId(), appId, workflowId);
    }

    @Transactional
    public AppEntity bindWorkflowId(String tenantId, String appId, String workflowId) {
        AppEntity agent = require(tenantId, appId);
        requireWritable(agent);
        agent.setWorkflowId(workflowId);
        appMapper.update(
                agent,
                new LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, agent.getTenantId())
                        .eq(AppEntity::getId, agent.getId()));
        return agent;
    }

    @Transactional
    public AppEntity bindPublishedWorkflow(String appId, String workflowId) {
        return bindPublishedWorkflow(UserContextHolder.currentTenantId(), appId, workflowId);
    }

    @Transactional
    public AppEntity bindPublishedWorkflow(String tenantId, String appId, String workflowId) {
        AppEntity agent = require(tenantId, appId);
        requireWritable(agent);
        agent.setWorkflowId(workflowId);
        agent.setPublished(true);
        appMapper.update(
                agent,
                new LambdaUpdateWrapper<AppEntity>()
                        .eq(AppEntity::getTenantId, agent.getTenantId())
                        .eq(AppEntity::getId, agent.getId()));
        return agent;
    }

    public List<AgentMessage> history(String conversationId, int requestedSize) {
        int historySize = Math.min(MAX_HISTORY_SIZE, Math.max(1, requestedSize));
        return toAgentMessages(memory.history(DEFAULT_TENANT_ID, null, conversationId, historySize));
    }

    public List<AgentMessage> history(String tenantId, String appId, String conversationId, int requestedSize) {
        int historySize = Math.min(MAX_HISTORY_SIZE, Math.max(1, requestedSize));
        require(tenantId, appId);
        return toAgentMessages(
                memory.history(valueOrDefault(tenantId, DEFAULT_TENANT_ID), appId, conversationId, historySize));
    }

    public AgentDefinition toDefinition(AppEntity agent) {
        return definitionFactory.create(agent);
    }

    public AgentResponse run(String agentId, AgentRequest request) {
        return run(agentId, request, null, null);
    }

    /** Execute an Agent owned by the explicitly supplied trusted tenant. */
    public AgentResponse run(String tenantId, String agentId, AgentRequest request) {
        AppEntity application = require(tenantId, agentId);
        if (!AppMode.from(application.getMode()).isAgent()) {
            throw new PlatformException(
                    "app_mode_mismatch", "Only an application with mode 'agent' can use the Agent runtime.", null);
        }
        return runDefinition(toDefinition(application), request, null, null);
    }

    public AgentResponse run(String agentId, AgentRequest request, Consumer<AgentStep> stepListener) {
        return run(agentId, request, stepListener, null);
    }

    public AgentResponse run(
            String agentId, AgentRequest request, Consumer<AgentStep> stepListener, Consumer<String> tokenListener) {
        AppEntity application = require(agentId);
        if (!AppMode.from(application.getMode()).isAgent()) {
            throw new PlatformException(
                    "app_mode_mismatch", "Only an application with mode 'agent' can use the Agent runtime.", null);
        }
        return runDefinition(toDefinition(application), request, stepListener, tokenListener);
    }

    public AgentResponse runDefinition(AgentDefinition definition, AgentRequest request) {
        return run(definition, request, null, null);
    }

    public AgentResponse runDefinition(
            AgentDefinition definition, AgentRequest request, Consumer<AgentStep> stepListener) {
        return run(definition, request, stepListener, null);
    }

    public AgentResponse runDefinition(
            AgentDefinition definition,
            AgentRequest request,
            Consumer<AgentStep> stepListener,
            Consumer<String> tokenListener) {
        return run(definition, request, stepListener, tokenListener);
    }

    @Override
    public AgentResponse run(
            AgentDefinition definition,
            AgentRequest request,
            Consumer<AgentStep> stepListener,
            Consumer<String> tokenListener) {
        definition = applySkills(definition);
        String tenantId = valueOrDefault(definition.getTenantId(), DEFAULT_TENANT_ID);
        String conversationId = conversationIdOf(request);
        String runId = UUID.randomUUID().toString();
        runStore.create(runId, definition, request, conversationId);
        runStore.transition(tenantId, runId, AgentRunStatus.RUNNING, null, null);
        Instant observationStartedAt = Instant.now();
        notifyStarted(definition, conversationId, runId, false, observationStartedAt);
        ActiveRunKey activeRunKey = new ActiveRunKey(tenantId, runId);
        activeRuns.put(activeRunKey, Thread.currentThread());
        try (var metering = io.aigoodle.common.context.ModelExecutionScope.open(tenantId, meteringAppId(definition))) {
            AgentRunContext runContext = createRunContext(
                    definition,
                    request,
                    conversationId,
                    runId,
                    durableStepListener(tenantId, runId, stepListener),
                    tokenListener);

            log.info(
                    "Running agent '{}' run={} (strategy={}, tools={}) conversation={}",
                    definition.getName(),
                    runId,
                    definition.getStrategy(),
                    runContext.getTools().size(),
                    conversationId);
            AgentResponse response =
                    strategyRegistry.get(definition.getStrategy()).run(runContext);
            response.setRunId(runId);
            response.setConversationId(conversationId);
            rememberCompletedExchange(definition, request, response, conversationId);
            runStore.transition(tenantId, runId, statusOf(response), response, response.getError());
            notifyFinished(
                    definition,
                    conversationId,
                    runId,
                    false,
                    observationStartedAt,
                    statusOf(response),
                    response.getError());
            return response;
        } catch (RuntimeException exception) {
            terminateRun(tenantId, runId, exception);
            AgentRunStatus status =
                    runStore.find(tenantId, runId).map(AgentRunSnapshot::status).orElse(AgentRunStatus.FAILED);
            notifyFinished(
                    definition, conversationId, runId, false, observationStartedAt, status, exception.getMessage());
            throw exception;
        } finally {
            activeRuns.remove(activeRunKey);
        }
    }

    private AgentDefinition applySkills(AgentDefinition definition) {
        SkillResolution resolved = skillResolver.resolve(
                valueOrDefault(definition.getTenantId(), DEFAULT_TENANT_ID), definition.getSkillIds());
        if ((resolved.prompt() == null || resolved.prompt().isBlank())
                && resolved.toolNames().isEmpty()) return definition;
        String base = definition.getInstructions() == null
                ? ""
                : definition.getInstructions().trim();
        String instructions = resolved.prompt() == null || resolved.prompt().isBlank()
                ? base
                : (base.isBlank() ? resolved.prompt() : base + "\n\n" + resolved.prompt());
        java.util.LinkedHashSet<String> tools = new java.util.LinkedHashSet<>(definition.getToolNames());
        tools.addAll(resolved.toolNames());
        return definition.toBuilder()
                .instructions(instructions)
                .toolNames(List.copyOf(tools))
                .build();
    }

    @Override
    public Optional<AgentRunSnapshot> findRun(String runId) {
        return runStore.find(UserContextHolder.currentTenantId(), runId);
    }

    @Override
    public List<AgentRunEvent> runEvents(String runId, long afterSequence, int limit) {
        return runStore.events(UserContextHolder.currentTenantId(), runId, afterSequence, limit);
    }

    @Override
    public AgentResponse resume(String runId, AgentResumeCommand command) {
        String tenantId = UserContextHolder.currentTenantId();
        AgentRunSnapshot pausedRun = runStore.find(tenantId, runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
        if (pausedRun.status() != AgentRunStatus.WAITING_APPROVAL) {
            throw new PlatformException(
                    "agent_run_not_paused", "Agent run " + runId + " is not waiting for approval", null);
        }
        AgentDefinition definition = JsonUtils.parse(pausedRun.definitionJson(), AgentDefinition.class);
        AgentRequest request = JsonUtils.parse(pausedRun.requestJson(), AgentRequest.class);
        AgentResponse paused = JsonUtils.parse(pausedRun.responseJson(), AgentResponse.class);
        var strategy = strategyRegistry.get(definition.getStrategy());
        if (!(strategy instanceof ResumableAgentStrategy resumable)) {
            throw new PlatformException(
                    "strategy_not_resumable",
                    "Agent strategy " + definition.getStrategy() + " does not support checkpoints",
                    null);
        }

        runStore.transition(tenantId, runId, AgentRunStatus.RUNNING, null, null);
        Instant observationStartedAt = Instant.now();
        notifyStarted(definition, pausedRun.conversationId(), runId, true, observationStartedAt);
        ActiveRunKey activeRunKey = new ActiveRunKey(tenantId, runId);
        activeRuns.put(activeRunKey, Thread.currentThread());
        try (var metering = io.aigoodle.common.context.ModelExecutionScope.open(tenantId, meteringAppId(definition))) {
            AgentRunContext context = createRunContext(
                    definition,
                    request,
                    pausedRun.conversationId(),
                    runId,
                    durableStepListener(tenantId, runId, null),
                    null);
            AgentResponse response = resumable.resume(context, paused, command);
            response.setRunId(runId);
            response.setConversationId(pausedRun.conversationId());
            rememberCompletedExchange(definition, request, response, pausedRun.conversationId());
            runStore.transition(tenantId, runId, statusOf(response), response, response.getError());
            notifyFinished(
                    definition,
                    pausedRun.conversationId(),
                    runId,
                    true,
                    observationStartedAt,
                    statusOf(response),
                    response.getError());
            return response;
        } catch (RuntimeException exception) {
            terminateRun(tenantId, runId, exception);
            AgentRunStatus status =
                    runStore.find(tenantId, runId).map(AgentRunSnapshot::status).orElse(AgentRunStatus.FAILED);
            notifyFinished(
                    definition,
                    pausedRun.conversationId(),
                    runId,
                    true,
                    observationStartedAt,
                    status,
                    exception.getMessage());
            throw exception;
        } finally {
            activeRuns.remove(activeRunKey);
        }
    }

    @Override
    public AgentRunSnapshot cancel(String runId) {
        String tenantId = UserContextHolder.currentTenantId();
        AgentRunSnapshot run = runStore.find(tenantId, runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
        if (run.status().isTerminal()) {
            return run;
        }
        AgentRunSnapshot cancelled =
                runStore.transition(tenantId, runId, AgentRunStatus.CANCELLED, null, "Cancelled by caller");
        Thread executionThread = activeRuns.get(new ActiveRunKey(tenantId, runId));
        if (executionThread != null && executionThread != Thread.currentThread()) {
            executionThread.interrupt();
        }
        return cancelled;
    }

    private void terminateRun(String tenantId, String runId, RuntimeException exception) {
        AgentRunSnapshot current = runStore.find(tenantId, runId).orElse(null);
        if (current != null && !current.status().isTerminal()) {
            AgentRunStatus target = exception instanceof AgentRunInterruptedException interrupted
                    ? (interrupted.isTimedOut() ? AgentRunStatus.TIMED_OUT : AgentRunStatus.CANCELLED)
                    : AgentRunStatus.FAILED;
            runStore.transition(tenantId, runId, target, null, exception.getMessage());
        }
    }

    private static AgentRunStatus statusOf(AgentResponse response) {
        return switch (response.getStatus()) {
            case COMPLETED -> AgentRunStatus.COMPLETED;
            case AWAITING_APPROVAL -> AgentRunStatus.WAITING_APPROVAL;
            case FAILED -> AgentRunStatus.FAILED;
            case MAX_ITERATIONS -> AgentRunStatus.MAX_ITERATIONS;
        };
    }

    private static String meteringAppId(AgentDefinition definition) {
        var parent = io.aigoodle.common.context.ModelExecutionScope.current();
        return parent != null
                        && parent.appId() != null
                        && java.util.Objects.equals(parent.tenantId(), definition.getTenantId())
                ? parent.appId()
                : definition.getId();
    }

    private AgentRunContext createRunContext(
            AgentDefinition definition,
            AgentRequest request,
            String conversationId,
            String runId,
            Consumer<AgentStep> stepListener,
            Consumer<String> tokenListener) {
        List<AgentMessage> conversationHistory = contextEngine
                .assemble(new AgentContextRequest(
                        definition, memoryOwnerId(definition, request), conversationId, request.getQuery(), 0))
                .messages();
        return AgentRunContext.builder()
                .definition(definition)
                .query(request.getQuery())
                .conversationId(conversationId)
                .runId(runId)
                .requestVariables(request.getVariables() == null ? Map.of() : Map.copyOf(request.getVariables()))
                .deadline(deadlineOf(request))
                .active(() -> runStore.find(valueOrDefault(definition.getTenantId(), DEFAULT_TENANT_ID), runId)
                        .map(snapshot -> !snapshot.status().isTerminal())
                        .orElse(false))
                .history(conversationHistory)
                .chatClient(resolveChatClient(definition))
                .tools(toolResolver.resolve(
                        definition,
                        (agentId, delegatedRequest) -> run(definition.getTenantId(), agentId, delegatedRequest)))
                .approvalGate(approvalGate)
                .toolExecutionGateway(toolExecutionGateway)
                .stepListener(stepListener)
                .tokenListener(tokenListener)
                .build();
    }

    /** Persist first, then fan out to the best-effort caller stream. */
    private Consumer<AgentStep> durableStepListener(String tenantId, String runId, Consumer<AgentStep> caller) {
        return step -> {
            if (step == null) return;
            try {
                runStore.appendEvent(
                        tenantId,
                        runId,
                        "STEP_"
                                + (step.getKind() == null
                                        ? "UNKNOWN"
                                        : step.getKind().name()),
                        JsonUtils.toJson(step));
            } finally {
                if (caller != null) caller.accept(step);
            }
        };
    }

    private static Instant deadlineOf(AgentRequest request) {
        Long timeoutMillis = request.getTimeoutMillis();
        return timeoutMillis == null || timeoutMillis <= 0
                ? null
                : Instant.now().plusMillis(timeoutMillis);
    }

    private void notifyStarted(
            AgentDefinition definition, String conversationId, String runId, boolean resumed, Instant startedAt) {
        AgentRunObservation observation =
                observation(definition, conversationId, runId, resumed, AgentRunStatus.RUNNING, startedAt, null);
        runObservers.forEach(observer -> safely(() -> observer.onStarted(observation)));
    }

    private void notifyFinished(
            AgentDefinition definition,
            String conversationId,
            String runId,
            boolean resumed,
            Instant startedAt,
            AgentRunStatus status,
            String error) {
        AgentRunObservation observation =
                observation(definition, conversationId, runId, resumed, status, startedAt, error);
        runObservers.forEach(observer -> safely(() -> observer.onFinished(observation)));
    }

    private static AgentRunObservation observation(
            AgentDefinition definition,
            String conversationId,
            String runId,
            boolean resumed,
            AgentRunStatus status,
            Instant startedAt,
            String error) {
        return new AgentRunObservation(
                runId,
                definition.getTenantId(),
                definition.getId(),
                conversationId,
                definition.getStrategy(),
                resumed,
                status,
                startedAt,
                Duration.between(startedAt, Instant.now()),
                error);
    }

    private static void safely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException ignored) {
        }
    }

    private ChatClient resolveChatClient(AgentDefinition definition) {
        String provider = definition.getModelProvider();
        String modelName = definition.getModelName();
        if (provider == null || provider.isBlank() || modelName == null || modelName.isBlank()) {
            throw new PlatformException(
                    "model_not_configured",
                    "Agent '" + definition.getName() + "' has no model configured (provider + name required)",
                    null);
        }
        String resourceTenant = valueOrDefault(definition.getResourceTenantId(), definition.getTenantId());
        if (resourceTenant.equals(UserContextHolder.currentTenantId()))
            return modelService.getChatClient(resourceTenant, provider, modelName);
        return UserContextHolder.callAs(
                io.aigoodle.common.context.CurrentUser.builder()
                        .tenantId(resourceTenant)
                        .userId(UserContextHolder.currentUserId())
                        .build(),
                () -> modelService.getChatClient(resourceTenant, provider, modelName));
    }

    private void rememberCompletedExchange(
            AgentDefinition definition, AgentRequest request, AgentResponse response, String conversationId) {
        if (!definition.isMemoryEnabled() || response.getStatus() != AgentResponse.Status.COMPLETED) {
            return;
        }
        memory.rememberExchange(
                definition.getTenantId(),
                memoryOwnerId(definition, request),
                conversationId,
                request.getQuery(),
                response.getText());
    }

    private static String memoryOwnerId(AgentDefinition definition, AgentRequest request) {
        Object configured =
                request.getVariables() == null ? null : request.getVariables().get("memoryOwnerId");
        String value = configured == null ? null : String.valueOf(configured).trim();
        return value == null || value.isBlank() ? definition.getId() : value;
    }

    private static List<AgentMessage> toAgentMessages(List<MemoryItem> items) {
        return items.stream()
                .map(item -> new AgentMessage(toAgentRole(item.role()), item.content()))
                .toList();
    }

    private static AgentMessage.Role toAgentRole(MemoryRole role) {
        if (role == null) return AgentMessage.Role.USER;
        try {
            return AgentMessage.Role.valueOf(role.name());
        } catch (IllegalArgumentException nonConversationRole) {
            return AgentMessage.Role.SYSTEM;
        }
    }

    private void saveModelConfig(AppEntity agent, SaveAppRequest request) {
        AppModelConfigEntity modelConfig = AppModelConfigService.fromRequest(request);
        if (modelConfig != null) {
            modelConfigService.upsert(new AppModelConfigRegistration(agent.getId(), agent.getTenantId(), modelConfig));
        }
    }

    private static String conversationIdOf(AgentRequest request) {
        if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
            return request.getConversationId();
        }
        return UUID.randomUUID().toString();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
