package io.aigoodle.completion.controller;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.completion.dto.dify.DifyChatMessagesRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.completion.service.AppGenerateService;
import io.aigoodle.completion.service.ConversationHistoryService;
import io.aigoodle.completion.support.AppAccessResolver;
import io.aigoodle.completion.support.ChatAccessContext;
import io.aigoodle.completion.support.ChatAccessPolicy;
import io.aigoodle.completion.support.DifyChatAdapter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Reactive OpenAI/Dify chat facade plus console conversation-history endpoints. */
@RestController
@RequestMapping("${spring-agent.web.base-path:/agent-start}")
@ConditionalOnBean({
    AppGenerateService.class,
    io.aigoodle.agent.service.AgentExecutionService.class,
    io.aigoodle.agent.service.AppService.class
})
public class ChatController {

    private static final Scheduler BLOCKING_SCHEDULER =
            Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "chat-blocking");

    private final AppGenerateService appGenerateService;
    private final ConversationHistoryService conversationHistoryService;
    private final AppAccessResolver appAccessResolver;
    private final ChatAccessPolicy chatAccessPolicy;

    public ChatController(
            AppGenerateService appGenerateService,
            AppAccessResolver appAccessResolver,
            ChatAccessPolicy chatAccessPolicy,
            ConversationHistoryService conversationHistoryService) {
        this.appGenerateService = appGenerateService;
        this.conversationHistoryService = conversationHistoryService;
        this.appAccessResolver = appAccessResolver;
        this.chatAccessPolicy = chatAccessPolicy;
    }

    @PostMapping(
            value = "/internal/apps/{appId}/chat/completions",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> internalCompletions(@PathVariable String appId, @RequestBody OpenAIChatRequest request) {
        ChatAccessContext access = chatAccessPolicy.authorizeInternal(appId);
        request.setDebug(null);
        request.setWorkflowId(null);
        request.setAppId(null);
        return generateOpenAI(access, request);
    }

    @PostMapping(
            value = {"/chat/{appCode}/completions", "/internal/apps/by-code/{appCode}/chat/completions"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> internalCompletionsByCode(
            @PathVariable String appCode, @RequestBody OpenAIChatRequest request) {
        ChatAccessContext access = chatAccessPolicy.authorizeInternalByCode(appCode);
        request.setDebug(null);
        request.setWorkflowId(null);
        request.setAppId(null);
        return generateOpenAI(access, request);
    }

    @PostMapping(
            value = "/console/apps/{appId}/debug/chat/completions",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> debugCompletions(
            @PathVariable String appId,
            @RequestHeader(value = "X-Workflow-Id", required = false) String workflowIdHeader,
            @RequestBody OpenAIChatRequest request) {
        String workflowId = AppAccessResolver.firstNonBlank(workflowIdHeader, request.getWorkflowId());
        ChatAccessContext access = chatAccessPolicy.authorizeDebug(appId, workflowId);
        request.setDebug(workflowId == null);
        request.setWorkflowId(workflowId);
        request.setAppId(null);
        return generateOpenAI(access, request);
    }

    /**
     * OpenAI-compatible endpoint for end-user chat windows. The target app is
     * resolved from the API key, so the browser request never carries appId.
     */
    @PostMapping(
            value = "/v1/chat/completions",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> openAICompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody OpenAIChatRequest request) {
        ChatAccessContext access =
                chatAccessPolicy.authorizeExternal(appAccessResolver.requireTokenApp(authorizationHeader));
        // Public OpenAI-compatible calls may only execute the app's published binding.
        request.setDebug(null);
        request.setWorkflowId(null);
        request.setAppId(null);
        return generateOpenAI(access, request);
    }

    @PostMapping(
            value = "/chat/completions/{appId}",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> completions(
            @PathVariable String appId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-Debug-Mode", required = false) String debugHeader,
            @RequestHeader(value = "X-Workflow-Id", required = false) String workflowIdHeader,
            @RequestParam(value = "debug", required = false) String debugParameter,
            @RequestParam(value = "workflowId", required = false) String workflowIdParameter,
            @RequestBody OpenAIChatRequest request) {
        mergeDebugParameters(request, debugHeader, debugParameter, workflowIdHeader, workflowIdParameter);
        String resolvedAppId = appAccessResolver.enforcePathApp(appId, authorizationHeader, isDebugRun(request));
        ChatAccessContext access = chatAccessPolicy.authorizeExternal(resolvedAppId);
        return generateOpenAI(access, request);
    }

    @PostMapping(
            value = "/chat-messages",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> chatMessages(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-App-Id", required = false) String appIdHeader,
            @RequestHeader(value = "X-Debug-Mode", required = false) String debugHeader,
            @RequestHeader(value = "X-Workflow-Id", required = false) String workflowIdHeader,
            @RequestParam(value = "appId", required = false) String appIdParameter,
            @RequestParam(value = "debug", required = false) String debugParameter,
            @RequestParam(value = "workflowId", required = false) String workflowIdParameter,
            @RequestBody DifyChatMessagesRequest request) {
        OpenAIChatRequest internalRequest = DifyChatAdapter.toInternalRequest(request);
        mergeDebugParameters(
                internalRequest,
                AppAccessResolver.firstNonBlank(
                        debugHeader,
                        request.getDebug() == null ? null : request.getDebug().toString()),
                debugParameter,
                AppAccessResolver.firstNonBlank(workflowIdHeader, request.getWorkflowId()),
                workflowIdParameter);
        ChatAccessContext access;
        if (isDebugRun(internalRequest)) {
            String appId = appAccessResolver.resolveDifyApp(appIdParameter, appIdHeader, authorizationHeader, request);
            access = chatAccessPolicy.authorizeDebug(appId, internalRequest.getWorkflowId());
        } else {
            String appId = appAccessResolver.requireTokenApp(authorizationHeader);
            access = chatAccessPolicy.authorizeExternal(appId);
            internalRequest.setDebug(null);
            internalRequest.setWorkflowId(null);
            internalRequest.setAppId(null);
        }

        if (request.streaming()) {
            Flux<ServerSentEvent<Object>> stream = appGenerateService.generateDifyStream(
                    access.appId(), access.tenantId(), access.userId(), internalRequest);
            return eventStream(stream);
        }
        Mono<Map<String, Object>> response = Mono.fromCallable(() -> {
                    OpenAIChatResponse generated = appGenerateService.generateBlocking(
                            access.appId(), access.tenantId(), access.userId(), internalRequest);
                    return DifyChatAdapter.toBlockingResponse(generated, internalRequest.getConversationId());
                })
                .subscribeOn(BLOCKING_SCHEDULER);
        return json(response);
    }

    @PostMapping(
            value = "/chat/conversations/{appId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> listConversations(
            @PathVariable String appId, @RequestBody(required = false) HistoryQuery query) {
        int limit = resolveLimit(query, 100, 500);
        ChatAccessContext access = chatAccessPolicy.authorizeInternal(appId);
        return ApiResponse.ok(conversationHistoryService.conversations(access.tenantId(), access.appId(), limit));
    }

    @PostMapping(
            value = "/chat/conversations/{appId}/{conversationId}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> conversationMessages(
            @PathVariable String appId,
            @PathVariable String conversationId,
            @RequestBody(required = false) HistoryQuery query) {
        int limit = resolveLimit(query, 500, 500);
        ChatAccessContext access = chatAccessPolicy.authorizeInternal(appId);
        return ApiResponse.ok(
                conversationHistoryService.messages(access.tenantId(), access.appId(), conversationId, limit));
    }

    /**
     * JSON-only history endpoints used by the embedded chat client. Keeping these
     * on the chat facade ensures chat streaming and history ship as one service.
     */
    @PostMapping(
            value = "/conversations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> listConversationsJson(@RequestBody HistoryQuery query) {
        String appId = requiredHistoryValue(query == null ? null : query.appId, "appId");
        ChatAccessContext access = chatAccessPolicy.authorizeInternal(appId);
        return ApiResponse.ok(conversationHistoryService.conversations(
                access.tenantId(), access.appId(), resolveLimit(query, 100, 500)));
    }

    @PostMapping(
            value = "/conversations/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> conversationMessagesJson(@RequestBody HistoryQuery query) {
        String appId = requiredHistoryValue(query == null ? null : query.appId, "appId");
        String conversationId = requiredHistoryValue(query == null ? null : query.conversationId, "conversationId");
        ChatAccessContext access = chatAccessPolicy.authorizeInternal(appId);
        return ApiResponse.ok(conversationHistoryService.messages(
                access.tenantId(), access.appId(), conversationId, resolveLimit(query, 500, 500)));
    }

    private static ResponseEntity<?> eventStream(Flux<ServerSentEvent<Object>> stream) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream);
    }

    private ResponseEntity<?> generateOpenAI(ChatAccessContext access, OpenAIChatRequest request) {
        if (request.streaming()) {
            return eventStream(
                    appGenerateService.generateStream(access.appId(), access.tenantId(), access.userId(), request));
        }
        Mono<OpenAIChatResponse> response = Mono.fromCallable(() -> appGenerateService.generateBlocking(
                        access.appId(), access.tenantId(), access.userId(), request))
                .subscribeOn(BLOCKING_SCHEDULER);
        return json(response);
    }

    private static ResponseEntity<?> json(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static boolean isDebugRun(OpenAIChatRequest request) {
        return request != null && Boolean.TRUE.equals(request.getDebug());
    }

    private static void mergeDebugParameters(
            OpenAIChatRequest request,
            String debugHeader,
            String debugParameter,
            String workflowIdHeader,
            String workflowIdParameter) {
        if (request == null) {
            return;
        }
        if (request.getDebug() == null) {
            Boolean debug = parseBooleanFlag(AppAccessResolver.firstNonBlank(debugHeader, debugParameter));
            if (debug != null) {
                request.setDebug(debug);
            }
        }
        if (request.getWorkflowId() == null || request.getWorkflowId().isBlank()) {
            String workflowId = AppAccessResolver.firstNonBlank(workflowIdHeader, workflowIdParameter);
            if (workflowId != null) {
                request.setWorkflowId(workflowId);
            }
        }
    }

    private static Boolean parseBooleanFlag(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on", "enable", "enabled" -> Boolean.TRUE;
            case "false", "0", "no", "off", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static int resolveLimit(HistoryQuery query, int defaultValue, int maximum) {
        if (query == null || query.limit == null || query.limit <= 0) {
            return defaultValue;
        }
        return Math.min(maximum, query.limit);
    }

    private static String requiredHistoryValue(String value, String field) {
        String normalized = AppAccessResolver.trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    public static class HistoryQuery {

        public String appId;

        public String conversationId;

        public Integer limit;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }
}
