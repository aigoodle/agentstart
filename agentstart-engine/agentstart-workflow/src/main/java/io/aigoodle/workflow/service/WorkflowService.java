package io.aigoodle.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.workflow.chat.ChatStreamSink;
import io.aigoodle.workflow.engine.WorkflowCompiler;
import io.aigoodle.workflow.engine.WorkflowEngine;
import io.aigoodle.workflow.engine.WorkflowRunResult;
import io.aigoodle.workflow.entity.WorkflowEntity;
import io.aigoodle.workflow.entity.WorkflowRunEntity;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.mapper.WorkflowMapper;
import io.aigoodle.workflow.mapper.WorkflowRunMapper;
import io.aigoodle.workflow.node.StepRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for workflow definitions, draft publication and execution.
 * Runtime details and observability persistence are delegated to focused collaborators.
 */
public class WorkflowService {

    private static final String DEFAULT_TENANT = "default";

    private io.aigoodle.agent.service.AppPermissionService appPermissions;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAppPermissions(io.aigoodle.agent.service.AppPermissionService appPermissions) {
        this.appPermissions = appPermissions;
    }

    private void checkApp(String tenantId, String appId, boolean write) {
        if (appPermissions != null)
            appPermissions.requireLinkedApp(defaultIfBlank(tenantId, DEFAULT_TENANT), appId, write);
    }

    private static void requireTenantAccess(String tenantId) {
        io.aigoodle.common.context.CurrentUser current = UserContextHolder.get();
        if (current != null
                && current.getTenantId() != null
                && !current.getTenantId().equals(defaultIfBlank(tenantId, DEFAULT_TENANT))) {
            throw new SecurityException("Cross-tenant workflow access is forbidden");
        }
    }

    private final WorkflowMapper workflowMapper;
    private final WorkflowEngine workflowEngine;
    private final WorkflowGraphCodec graphCodec;
    private final WorkflowRunStore runStore;
    private final WorkflowCompiler workflowCompiler;
    private final PersistentWorkflowRunner persistentRunner;
    private final ObjectProvider<AppConversationService> conversationServices;

    public WorkflowService(WorkflowMapper workflowMapper, WorkflowRunMapper runMapper, WorkflowEngine workflowEngine) {
        this(workflowMapper, runMapper, workflowEngine, null);
    }

    public WorkflowService(
            WorkflowMapper workflowMapper,
            WorkflowRunMapper runMapper,
            WorkflowEngine workflowEngine,
            PersistentWorkflowRunner persistentRunner) {
        this(workflowMapper, runMapper, workflowEngine, persistentRunner, null);
    }

    public WorkflowService(
            WorkflowMapper workflowMapper,
            WorkflowRunMapper runMapper,
            WorkflowEngine workflowEngine,
            PersistentWorkflowRunner persistentRunner,
            ObjectProvider<AppConversationService> conversationServices) {
        this.workflowMapper = workflowMapper;
        this.workflowEngine = workflowEngine;
        this.graphCodec = new WorkflowGraphCodec();
        this.runStore = new WorkflowRunStore(runMapper);
        this.workflowCompiler = new WorkflowCompiler();
        this.persistentRunner = persistentRunner;
        this.conversationServices = conversationServices;
    }

    public WorkflowEntity save(String appId, String tenantId, String name, String mode, WorkflowGraph graph) {
        return save(appId, tenantId, name, mode, graph == null ? null : graphCodec.write(graph));
    }

    /** Creates or updates the single draft row owned by an application. */
    @Transactional
    public WorkflowEntity save(String appId, String tenantId, String name, String mode, JsonNode graphDefinition) {
        return save(new WorkflowDraftDefinition(appId, tenantId, name, mode, graphDefinition));
    }

    /** Creates or updates the single draft row owned by an application. */
    @Transactional
    public WorkflowEntity save(WorkflowDraftDefinition definition) {
        requireTenantAccess(definition.tenantId());
        checkApp(definition.tenantId(), definition.applicationId(), true);
        requireAppId(definition.applicationId());
        String tenantId = defaultIfBlank(definition.tenantId(), DEFAULT_TENANT);
        WorkflowEntity draft = findDraft(tenantId, definition.applicationId());
        if (draft == null) {
            return insertDraft(definition);
        }

        WorkflowEntityFactory.updateDefinition(draft, definition.name(), definition.mode(), definition.graph());
        updateOwned(draft);
        return draft;
    }

    public WorkflowEntity require(String workflowId) {
        return require(UserContextHolder.currentTenantId(), workflowId);
    }

    public WorkflowEntity require(String tenantId, String workflowId) {
        requireTenantAccess(tenantId);
        WorkflowEntity workflow = workflowMapper.selectOne(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                .eq(WorkflowEntity::getId, workflowId)
                .last("LIMIT 1"));
        if (workflow == null) {
            throw new PlatformException("workflow_not_found", "Workflow not found", null);
        }
        checkApp(workflow.getTenantId(), workflow.getAppId(), false);
        return workflow;
    }

    public WorkflowGraph graphOf(WorkflowEntity workflow) {
        return graphCodec.read(workflow.getGraph());
    }

    public List<WorkflowEntity> list(String tenantId) {
        List<WorkflowEntity> rows = workflowMapper.selectList(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                .orderByDesc(WorkflowEntity::getUpdatedAt));
        if (appPermissions == null) return rows;
        java.util.Set<String> denied = appPermissions.unreadableAppIds(
                defaultIfBlank(tenantId, DEFAULT_TENANT),
                rows.stream()
                        .map(WorkflowEntity::getAppId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());
        return rows.stream()
                .filter(row -> row.getAppId() == null || !denied.contains(row.getAppId()))
                .toList();
    }

    public WorkflowEntity update(String workflowId, String name, String mode, WorkflowGraph graph) {
        return update(workflowId, name, mode, graph == null ? null : graphCodec.write(graph));
    }

    public WorkflowEntity update(String workflowId, String name, String mode, JsonNode graphDefinition) {
        return update(UserContextHolder.currentTenantId(), workflowId, name, mode, graphDefinition);
    }

    public WorkflowEntity update(
            String tenantId, String workflowId, String name, String mode, JsonNode graphDefinition) {
        WorkflowEntity workflow = require(tenantId, workflowId);
        checkApp(workflow.getTenantId(), workflow.getAppId(), true);
        WorkflowEntityFactory.updateDefinition(workflow, name, mode, graphDefinition);
        workflowMapper.update(
                workflow,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowEntity>()
                        .eq(WorkflowEntity::getTenantId, workflow.getTenantId())
                        .eq(WorkflowEntity::getId, workflow.getId()));
        return workflow;
    }

    public void delete(String workflowId) {
        delete(UserContextHolder.currentTenantId(), workflowId);
    }

    public void delete(String tenantId, String workflowId) {
        WorkflowEntity workflow = require(tenantId, workflowId);
        checkApp(workflow.getTenantId(), workflow.getAppId(), true);
        workflowMapper.delete(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                .eq(WorkflowEntity::getId, workflowId));
    }

    @Transactional
    public WorkflowEntity createDraft(
            String appId, String tenantId, String mode, String name, WorkflowGraph seedGraph) {
        JsonNode initialGraph = seedGraph == null ? graphCodec.emptyGraph() : graphCodec.write(seedGraph);
        return createDraft(new WorkflowDraftDefinition(appId, tenantId, name, mode, initialGraph));
    }

    @Transactional
    public WorkflowEntity createDraft(WorkflowDraftDefinition definition) {
        checkApp(definition.tenantId(), definition.applicationId(), true);
        WorkflowEntity existingDraft =
                findDraft(defaultIfBlank(definition.tenantId(), DEFAULT_TENANT), definition.applicationId());
        if (existingDraft != null) {
            return existingDraft;
        }
        JsonNode initialGraph = definition.graph() == null ? graphCodec.emptyGraph() : definition.graph();
        return insertDraft(new WorkflowDraftDefinition(
                definition.applicationId(),
                defaultIfBlank(definition.tenantId(), DEFAULT_TENANT),
                definition.name(),
                defaultIfBlank(definition.mode(), WorkflowEntityFactory.DEFAULT_MODE),
                initialGraph));
    }

    public WorkflowEntity findDraft(String appId) {
        return findDraft(UserContextHolder.currentTenantId(), appId);
    }

    public WorkflowEntity findDraft(String tenantId, String appId) {
        checkApp(tenantId, appId, false);
        WorkflowEntity workflow = workflowMapper.selectOne(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                .eq(WorkflowEntity::getId, appId)
                .last("LIMIT 1"));
        return workflow != null && WorkflowEntityFactory.DRAFT_VERSION.equals(workflow.getVersion()) ? workflow : null;
    }

    @Transactional
    public WorkflowEntity saveDraft(
            String appId,
            WorkflowGraph graph,
            String features,
            String environmentVariables,
            String conversationVariables) {
        return saveDraft(
                appId,
                graph == null ? null : graphCodec.write(graph),
                features,
                environmentVariables,
                conversationVariables);
    }

    @Transactional
    public WorkflowEntity saveDraft(
            String appId,
            JsonNode graphDefinition,
            String features,
            String environmentVariables,
            String conversationVariables) {
        return saveDraft(
                appId,
                new WorkflowDraftChanges(graphDefinition, features, environmentVariables, conversationVariables));
    }

    @Transactional
    public WorkflowEntity saveDraft(String appId, WorkflowDraftChanges changes) {
        checkApp(UserContextHolder.currentTenantId(), appId, true);
        requireAppId(appId);
        String tenantId = UserContextHolder.currentTenantId();
        WorkflowEntity draft = findDraft(tenantId, appId);
        boolean isNewDraft = draft == null;
        if (isNewDraft) {
            draft = WorkflowEntityFactory.draft(
                    new WorkflowDraftDefinition(appId, tenantId, null, null, changes.graph()));
        }
        WorkflowEntityFactory.updateDesignerState(draft, changes);
        if (isNewDraft) workflowMapper.insert(draft);
        else updateOwned(draft);
        return draft;
    }

    @Transactional
    public WorkflowEntity saveDraft(String tenantId, String appId, WorkflowDraftChanges changes) {
        checkApp(tenantId, appId, true);
        requireAppId(appId);
        String scopedTenant = defaultIfBlank(tenantId, DEFAULT_TENANT);
        WorkflowEntity draft = findDraft(scopedTenant, appId);
        if (draft == null) {
            throw new PlatformException("draft_not_found", "No draft workflow for app " + appId, null);
        }
        WorkflowEntityFactory.updateDesignerState(draft, changes);
        workflowMapper.update(
                draft,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowEntity>()
                        .eq(WorkflowEntity::getTenantId, scopedTenant)
                        .eq(WorkflowEntity::getId, appId));
        return draft;
    }

    /** Copies the mutable draft into a new, immutable published snapshot. */
    @Transactional
    public WorkflowEntity publishDraft(String appId, String markedName, String markedComment) {
        return publishDraft(appId, new WorkflowPublication(markedName, markedComment));
    }

    /** Copies the mutable draft into a new, immutable published snapshot. */
    @Transactional
    public WorkflowEntity publishDraft(String appId, WorkflowPublication publication) {
        checkApp(UserContextHolder.currentTenantId(), appId, true);
        WorkflowEntity draft = findDraft(appId);
        if (draft == null) {
            throw new PlatformException(
                    "draft_not_found", "No draft workflow for app " + appId + "; nothing to publish", null);
        }

        workflowCompiler.compile(graphOf(draft));
        WorkflowEntity snapshot = WorkflowEntityFactory.publishedSnapshot(
                draft, publication, LocalDateTime.now().toString());
        workflowMapper.insert(snapshot);
        return snapshot;
    }

    @Transactional
    public WorkflowEntity publishDraft(String tenantId, String appId, WorkflowPublication publication) {
        checkApp(tenantId, appId, true);
        WorkflowEntity draft = findDraft(tenantId, appId);
        if (draft == null) {
            throw new PlatformException("draft_not_found", "No draft workflow for app " + appId, null);
        }
        workflowCompiler.compile(graphOf(draft));
        WorkflowEntity snapshot = WorkflowEntityFactory.publishedSnapshot(
                draft, publication, LocalDateTime.now().toString());
        workflowMapper.insert(snapshot);
        return snapshot;
    }

    public List<WorkflowEntity> listByApp(String appId) {
        return listByApp(UserContextHolder.currentTenantId(), appId);
    }

    public List<WorkflowEntity> listByApp(String tenantId, String appId) {
        checkApp(tenantId, appId, false);
        return workflowMapper.selectList(new LambdaQueryWrapper<WorkflowEntity>()
                .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                .eq(WorkflowEntity::getAppId, appId)
                .orderByDesc(WorkflowEntity::getCreatedAt));
    }

    /** Replaces the mutable draft state with one immutable snapshot of the same app. */
    @Transactional
    public WorkflowEntity restorePublishedSnapshot(String appId, String snapshotId) {
        return restorePublishedSnapshot(UserContextHolder.currentTenantId(), appId, snapshotId);
    }

    @Transactional
    public WorkflowEntity restorePublishedSnapshot(String tenantId, String appId, String snapshotId) {
        checkApp(tenantId, appId, true);
        WorkflowEntity snapshot = require(tenantId, snapshotId);
        if (!appId.equals(snapshot.getAppId()) || !Boolean.TRUE.equals(snapshot.getPublished())) {
            throw new PlatformException(
                    "workflow_snapshot_invalid", "Workflow snapshot is not published for this application", null);
        }
        WorkflowEntity draft = findDraft(tenantId, appId);
        if (draft == null) throw new PlatformException("draft_not_found", "No draft workflow for app " + appId, null);
        WorkflowEntityFactory.updateDesignerState(
                draft,
                new WorkflowDraftChanges(
                        snapshot.getGraph(),
                        snapshot.getFeatures(),
                        snapshot.getEnvironmentVariables(),
                        snapshot.getConversationVariables()));
        workflowMapper.update(
                draft,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowEntity>()
                        .eq(WorkflowEntity::getTenantId, defaultIfBlank(tenantId, DEFAULT_TENANT))
                        .eq(WorkflowEntity::getId, appId));
        return draft;
    }

    public List<WorkflowRunEntity> runs(String workflowId, int limit) {
        return runs(UserContextHolder.currentTenantId(), workflowId, limit);
    }

    public List<WorkflowRunEntity> runs(String tenantId, String workflowId, int limit) {
        require(tenantId, workflowId);
        return runStore.findRecent(defaultIfBlank(tenantId, DEFAULT_TENANT), workflowId, limit);
    }

    public WorkflowRunResult run(String workflowId, Map<String, Object> inputs, String conversationId) {
        return executeStored(workflowId, inputs, conversationId, null, null, UserContextHolder.currentTenantId());
    }

    /** Executes only when the target belongs to the authenticated trigger tenant. */
    public WorkflowRunResult runForTenant(
            String workflowId, Map<String, Object> data, String conversationId, String tenantId) {
        return executeStored(workflowId, data, conversationId, null, null, tenantId);
    }

    public WorkflowRunResult runForTenant(
            String workflowId,
            Map<String, Object> data,
            String conversationId,
            String tenantId,
            ChatStreamSink chatSink) {
        return executeStored(workflowId, data, conversationId, null, chatSink, tenantId);
    }

    public WorkflowRunResult runForTenant(
            String workflowId,
            Map<String, Object> data,
            String conversationId,
            String tenantId,
            Consumer<StepRecord> stepListener) {
        return executeStored(workflowId, data, conversationId, stepListener, null, tenantId);
    }

    public WorkflowRunResult run(
            String workflowId, Map<String, Object> inputs, String conversationId, Consumer<StepRecord> stepListener) {
        return executeStored(
                workflowId, inputs, conversationId, stepListener, null, UserContextHolder.currentTenantId());
    }

    public WorkflowRunResult run(
            String workflowId,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink) {
        return executeStored(
                workflowId, inputs, conversationId, stepListener, chatSink, UserContextHolder.currentTenantId());
    }

    public WorkflowRunResult runGraph(WorkflowGraph graph, Map<String, Object> inputs, String conversationId) {
        return executeAdHoc(graph, inputs, conversationId, null);
    }

    public WorkflowRunResult runGraph(JsonNode graphDefinition, Map<String, Object> inputs, String conversationId) {
        return executeAdHoc(graphCodec.read(graphDefinition), inputs, conversationId, null);
    }

    public WorkflowRunResult runGraphForTenant(
            JsonNode graphDefinition, Map<String, Object> inputs, String conversationId, String tenantId) {
        return executeAdHoc(
                graphCodec.read(graphDefinition),
                inputs,
                conversationId,
                null,
                defaultIfBlank(tenantId, DEFAULT_TENANT));
    }

    public WorkflowRunResult runGraph(
            WorkflowGraph graph, Map<String, Object> inputs, String conversationId, Consumer<StepRecord> stepListener) {
        return executeAdHoc(graph, inputs, conversationId, stepListener);
    }

    public WorkflowRunResult runGraph(
            JsonNode graphDefinition,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener) {
        return executeAdHoc(graphCodec.read(graphDefinition), inputs, conversationId, stepListener);
    }

    public WorkflowRunResult runGraphForTenant(
            JsonNode graphDefinition,
            Map<String, Object> inputs,
            String conversationId,
            String tenantId,
            Consumer<StepRecord> stepListener) {
        return executeAdHoc(
                graphCodec.read(graphDefinition),
                inputs,
                conversationId,
                stepListener,
                defaultIfBlank(tenantId, DEFAULT_TENANT));
    }

    /** App-bound ad-hoc preview; client attribution is accepted only after application access checks. */
    public WorkflowRunResult runGraphForTenant(
            JsonNode graphDefinition,
            Map<String, Object> inputs,
            String conversationId,
            String tenantId,
            String appId,
            Consumer<StepRecord> stepListener) {
        String tenant = defaultIfBlank(tenantId, DEFAULT_TENANT);
        requireTenantAccess(tenant);
        if (!hasText(appId))
            return executeAdHoc(graphCodec.read(graphDefinition), inputs, conversationId, stepListener, tenant);
        if (appPermissions == null)
            throw new PlatformException(
                    "app_access_unavailable", "Application access checks are required for attributed previews", null);
        checkApp(tenant, appId, true);
        var result = workflowEngine.run(
                graphCodec.read(graphDefinition),
                inputs,
                conversationId,
                stepListener,
                null,
                tenant,
                io.aigoodle.workflow.engine.WorkflowRunOptions.defaults().withAppId(appId));
        runStore.recordAdHocRun(tenant, conversationId, inputs, result);
        return result;
    }

    private WorkflowRunResult executeStored(
            String workflowId,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink,
            String expectedTenantId) {
        WorkflowEntity workflow =
                hasText(expectedTenantId) ? require(expectedTenantId, workflowId) : require(workflowId);
        return executeDefinition(workflow, inputs, conversationId, stepListener, chatSink);
    }

    /** Reads a shared published definition, then executes entirely in the caller's tenant. */
    public WorkflowRunResult runSharedPublished(
            String appId,
            String ownerTenantId,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink) {
        WorkflowEntity workflow = io.aigoodle.persistence.TenantSqlScope.bypass(
                () -> workflowMapper.selectSharedPublished(appId, ownerTenantId));
        if (workflow == null) throw new PlatformException("workflow_not_found", "共享应用未发布或不可访问", null);
        workflow.setTenantId(UserContextHolder.currentTenantId());
        return executeDefinition(workflow, inputs, conversationId, stepListener, chatSink, ownerTenantId);
    }

    private WorkflowRunResult executeDefinition(
            WorkflowEntity workflow,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink) {
        return executeDefinition(workflow, inputs, conversationId, stepListener, chatSink, workflow.getTenantId());
    }

    private WorkflowRunResult executeDefinition(
            WorkflowEntity workflow,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            ChatStreamSink chatSink,
            String resourceTenantId) {
        String workflowId = workflow.getId();
        WorkflowGraph graph = graphOf(workflow);
        Map<String, Object> scopedInputs = new java.util.HashMap<>();
        if (inputs != null) scopedInputs.putAll(inputs);
        scopedInputs.put("_memory_owner_id", workflow.getAppId() == null ? workflowId : workflow.getAppId());
        scopedInputs.put("_memory_tenant_id", workflow.getTenantId() == null ? "default" : workflow.getTenantId());
        ensureChannelConversation(workflow, scopedInputs, conversationId);
        var options = io.aigoodle.workflow.engine.WorkflowRunOptions.defaults()
                .withResourceTenantId(resourceTenantId)
                .withAppId(workflow.getAppId());
        WorkflowRunResult result = persistentRunner == null
                ? workflowEngine.run(
                        graph, scopedInputs, conversationId, stepListener, chatSink, workflow.getTenantId(), options)
                : persistentRunner.start(
                        workflow.getTenantId(),
                        workflowId,
                        workflow.getVersion(),
                        graph,
                        scopedInputs,
                        conversationId,
                        options,
                        stepListener,
                        chatSink);
        runStore.recordStoredRun(workflow.getTenantId(), workflowId, conversationId, scopedInputs, result);
        return result;
    }

    private void ensureChannelConversation(WorkflowEntity workflow, Map<String, Object> inputs, String conversationId) {
        if (!Boolean.parseBoolean(String.valueOf(inputs.get("_channel_conversation")))
                || conversationId == null
                || conversationId.isBlank()
                || workflow.getAppId() == null
                || conversationServices == null) return;
        AppConversationService conversations = conversationServices.getIfAvailable();
        if (conversations == null) return;
        try {
            conversations.ensure(
                    conversationId,
                    workflow.getAppId(),
                    workflow.getTenantId(),
                    text(inputs.get("query")),
                    "channel",
                    text(inputs.get("_conversation_sender_id")));
        } catch (RuntimeException exception) {
            // Conversation indexing must not prevent the workflow from running.
        }
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    public WorkflowRunResult resume(String tenantId, String runId) {
        if (persistentRunner == null) {
            throw new PlatformException("resume_unavailable", "Persistent workflow runner is not configured", null);
        }
        WorkflowRunResult result = persistentRunner.resume(
                defaultIfBlank(tenantId, DEFAULT_TENANT),
                runId,
                io.aigoodle.workflow.engine.WorkflowRunOptions.defaults());
        runStore.recordStoredRun(defaultIfBlank(tenantId, DEFAULT_TENANT), null, null, Map.of(), result);
        return result;
    }

    public boolean cancel(String tenantId, String runId, String reason) {
        if (persistentRunner == null) return false;
        return persistentRunner.cancel(defaultIfBlank(tenantId, DEFAULT_TENANT), runId, reason);
    }

    public boolean pause(String tenantId, String runId, String reason) {
        if (persistentRunner == null) return false;
        return persistentRunner.pause(defaultIfBlank(tenantId, DEFAULT_TENANT), runId, reason);
    }

    public WorkflowSignalResult signal(
            String tenantId, String runId, String resumeToken, String eventId, Map<String, Object> payload) {
        if (persistentRunner == null) {
            throw new PlatformException("resume_unavailable", "Persistent workflow runner is not configured", null);
        }
        return persistentRunner.signal(
                defaultIfBlank(tenantId, DEFAULT_TENANT),
                runId,
                resumeToken,
                eventId,
                payload,
                io.aigoodle.workflow.engine.WorkflowRunOptions.defaults());
    }

    public WorkflowSignalResult signalByCorrelation(
            String tenantId, String correlationKey, String resumeToken, String eventId, Map<String, Object> payload) {
        if (persistentRunner == null) {
            throw new PlatformException("resume_unavailable", "Persistent workflow runner is not configured", null);
        }
        return persistentRunner.signalByCorrelation(
                defaultIfBlank(tenantId, DEFAULT_TENANT),
                correlationKey,
                resumeToken,
                eventId,
                payload,
                io.aigoodle.workflow.engine.WorkflowRunOptions.defaults());
    }

    private WorkflowRunResult executeAdHoc(
            WorkflowGraph graph, Map<String, Object> inputs, String conversationId, Consumer<StepRecord> stepListener) {
        return executeAdHoc(graph, inputs, conversationId, stepListener, UserContextHolder.currentTenantId());
    }

    private WorkflowRunResult executeAdHoc(
            WorkflowGraph graph,
            Map<String, Object> inputs,
            String conversationId,
            Consumer<StepRecord> stepListener,
            String tenantId) {
        WorkflowRunResult result = workflowEngine.run(graph, inputs, conversationId, stepListener, null, tenantId);
        runStore.recordAdHocRun(tenantId, conversationId, inputs, result);
        return result;
    }

    private WorkflowEntity insertDraft(WorkflowDraftDefinition definition) {
        WorkflowEntity draft = WorkflowEntityFactory.draft(definition);
        workflowMapper.insert(draft);
        return draft;
    }

    private void updateOwned(WorkflowEntity workflow) {
        workflowMapper.update(
                workflow,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowEntity>()
                        .eq(WorkflowEntity::getTenantId, defaultIfBlank(workflow.getTenantId(), DEFAULT_TENANT))
                        .eq(WorkflowEntity::getId, workflow.getId()));
    }

    private static void requireAppId(String appId) {
        if (!hasText(appId)) {
            throw new PlatformException("app_id_required", "appId is required for every workflow draft", null);
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
