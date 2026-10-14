package io.aigoodle.completion.service;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AgentExecutionService;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.completion.common.SseBridge;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.workflow.service.WorkflowService;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** Entry point that prepares a chat request and delegates it to the application runtime. */
@Service
@ConditionalOnBean({AgentExecutionService.class, AppService.class})
public class AppGenerateService {

    private static final Logger log = LoggerFactory.getLogger(AppGenerateService.class);

    private final AppService appService;
    private final ChatRequestInitializer requestInitializer;
    private final AppChatRuntimeRouter runtimeRouter;

    public AppGenerateService(
            AppService appService,
            AgentExecutionService executions,
            ObjectProvider<WorkflowService> workflowServiceProvider,
            ObjectProvider<AppConversationService> conversationServiceProvider,
            ObjectProvider<MemoryManager> memoryManagerProvider) {
        this.appService = appService;
        this.requestInitializer = new ChatRequestInitializer(conversationServiceProvider, log);
        this.runtimeRouter = new AppChatRuntimeRouter(
                new AgentChatGenerator(executions), workflowServiceProvider, memoryManagerProvider);
    }

    public OpenAIChatResponse generateBlocking(String appId, OpenAIChatRequest request) {
        return generateBlocking(appId, null, request);
    }

    public OpenAIChatResponse generateBlocking(String appId, String executionTenantId, OpenAIChatRequest request) {
        return generateBlocking(appId, executionTenantId, null, request);
    }

    public OpenAIChatResponse generateBlocking(
            String appId, String executionTenantId, String executionUserId, OpenAIChatRequest request) {
        AppEntity application = prepareRequest(appId, executionTenantId, request);
        return callWithUser(application, executionUserId, () -> runtimeRouter.generateBlocking(application, request));
    }

    public Flux<ServerSentEvent<Object>> generateStream(String appId, OpenAIChatRequest request) {
        return generateStream(appId, null, request);
    }

    public Flux<ServerSentEvent<Object>> generateStream(
            String appId, String executionTenantId, OpenAIChatRequest request) {
        return generateStream(appId, executionTenantId, null, request);
    }

    public Flux<ServerSentEvent<Object>> generateStream(
            String appId, String executionTenantId, String executionUserId, OpenAIChatRequest request) {
        AppEntity application = prepareRequest(appId, executionTenantId, request);
        return SseBridge.stream(emitter -> callWithUser(application, executionUserId, () -> {
            runtimeRouter.generateStream(application, request, emitter);
            return null;
        }));
    }

    public Flux<ServerSentEvent<Object>> generateDifyStream(String appId, OpenAIChatRequest request) {
        return generateDifyStream(appId, null, null, request);
    }

    public Flux<ServerSentEvent<Object>> generateDifyStream(
            String appId, String executionTenantId, String executionUserId, OpenAIChatRequest request) {
        AppEntity application = prepareRequest(appId, executionTenantId, request);
        String taskId = "task-" + UUID.randomUUID();
        return SseBridge.stream(emitter -> callWithUser(application, executionUserId, () -> {
            runtimeRouter.generateStream(
                    application, request, new DifyEmitAdapter(emitter, taskId, request.getConversationId()));
            return null;
        }));
    }

    private AppEntity prepareRequest(String appId, String executionTenantId, OpenAIChatRequest request) {
        String tenantId = executionTenantId == null || executionTenantId.isBlank()
                ? UserContextHolder.currentTenantId()
                : executionTenantId;
        AppEntity application = appService.requireForChat(tenantId, appId);
        AppEntity conversationApp = new AppEntity();
        conversationApp.setId(application.getId());
        conversationApp.setTenantId(tenantId);
        requestInitializer.initialize(conversationApp, request);
        return application;
    }

    static <T> T callWithUser(AppEntity application, String executionUserId, Supplier<T> action) {
        if (executionUserId == null || executionUserId.isBlank()) {
            return action.get();
        }
        CurrentUser caller = UserContextHolder.get();
        if (caller != null && executionUserId.equals(caller.getUserId())) {
            return UserContextHolder.callAs(
                    caller.toBuilder().appId(application.getId()).build(), action);
        }
        CurrentUser user = CurrentUser.builder()
                .userId(executionUserId)
                .username(executionUserId)
                .tenantId(application.getTenantId())
                .appId(application.getId())
                .build();
        return UserContextHolder.callAs(user, action);
    }
}
