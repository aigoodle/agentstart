package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.service.AppApiTokenService;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.web.dto.dify.DifyConversationNameRequest;
import io.aigoodle.web.dto.dify.DifyConversationVO;
import io.aigoodle.web.dto.dify.DifyMessageVO;
import io.aigoodle.web.dto.dify.DifyPage;
import io.aigoodle.web.support.DifyAppIdResolver;
import io.aigoodle.web.support.DifyHistoryViewMapper;
import io.aigoodle.web.support.DifyMessageHistory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dify-compatible conversation and message-history endpoints. */
@RestController
@ConditionalOnBean(AppConversationService.class)
public class DifyChatHistoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AppConversationService conversationService;
    private final DifyMessageHistory messageHistory;
    private final DifyHistoryViewMapper viewMapper;
    private final AppApiTokenService tokens;
    private final AppService apps;

    public DifyChatHistoryController(
            AppConversationService conversationService,
            DifyMessageHistory messageHistory,
            DifyHistoryViewMapper viewMapper,
            AppApiTokenService tokens,
            AppService apps) {
        this.conversationService = conversationService;
        this.messageHistory = messageHistory;
        this.viewMapper = viewMapper;
        this.tokens = tokens;
        this.apps = apps;
    }

    @GetMapping("/conversations")
    public DifyPage<DifyConversationVO> listConversations(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId,
            @RequestParam(value = "appId", required = false) String queryAppId,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "last_id", required = false) String lastConversationId,
            @RequestParam(value = "limit", required = false) Integer requestedPageSize,
            @RequestParam(value = "sort_by", required = false) String sortBy) {
        Identity identity = identity(queryAppId, headerAppId, authorizationHeader);
        String appId = identity.appId();
        int pageSize = pageSize(requestedPageSize);
        List<AppConversationEntity> conversations =
                new ArrayList<>(conversationService.listByApp(identity.tenantId(), appId));
        conversations.sort(conversationComparator(sortBy));

        int pageStart = indexAfter(conversations, lastConversationId);
        int pageEnd = Math.min(conversations.size(), pageStart + pageSize);
        List<DifyConversationVO> page = conversations.subList(pageStart, pageEnd).stream()
                .map(viewMapper::toConversation)
                .toList();
        return new DifyPage<>(pageSize, pageEnd < conversations.size(), page);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Map<String, Object> deleteConversation(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId,
            @RequestParam(value = "appId", required = false) String queryAppId,
            @PathVariable String conversationId,
            @RequestBody(required = false) Map<String, Object> body) {
        Identity identity = identity(queryAppId, headerAppId, authorizationHeader);
        conversationService.delete(identity.tenantId(), identity.appId(), conversationId);
        return Map.of("result", "success");
    }

    @PostMapping("/conversations/{conversationId}/name")
    public DifyConversationVO renameConversation(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId,
            @RequestParam(value = "appId", required = false) String queryAppId,
            @PathVariable String conversationId,
            @RequestBody(required = false) DifyConversationNameRequest request) {
        Identity identity = identity(queryAppId, headerAppId, authorizationHeader);
        String requestedName = request == null ? null : request.requestedName();
        if (requestedName == null && request != null && request.requestsAutomaticName()) {
            requestedName = messageHistory.suggestTitle(identity.tenantId(), identity.appId(), conversationId);
        }
        AppConversationEntity conversation = requestedName == null
                ? conversationService.require(identity.tenantId(), identity.appId(), conversationId)
                : conversationService.rename(identity.tenantId(), identity.appId(), conversationId, requestedName);
        return viewMapper.toConversation(conversation);
    }

    @GetMapping("/messages")
    public DifyPage<DifyMessageVO> listMessages(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = "X-App-Id", required = false) String headerAppId,
            @RequestParam(value = "appId", required = false) String queryAppId,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam("conversation_id") String conversationId,
            @RequestParam(value = "first_id", required = false) String firstMessageId,
            @RequestParam(value = "limit", required = false) Integer requestedPageSize) {
        Identity identity = identity(queryAppId, headerAppId, authorizationHeader);
        int pageSize = pageSize(requestedPageSize);
        List<DifyMessageVO> messages = viewMapper.toMessages(
                conversationId, messageHistory.findAll(identity.tenantId(), identity.appId(), conversationId));
        messages.sort(
                Comparator.comparingLong(message -> message.getCreatedAt() == null ? 0L : message.getCreatedAt()));
        messages = messagesBefore(messages, firstMessageId);

        boolean hasMore = messages.size() > pageSize;
        int pageStart = hasMore ? messages.size() - pageSize : 0;
        return new DifyPage<>(pageSize, hasMore, new ArrayList<>(messages.subList(pageStart, messages.size())));
    }

    private static int pageSize(Integer requestedPageSize) {
        if (requestedPageSize == null || requestedPageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, requestedPageSize);
    }

    private Identity identity(String queryAppId, String headerAppId, String authorizationHeader) {
        String requestedAppId = firstNonBlank(queryAppId, headerAppId);
        String bearer = bearer(authorizationHeader);
        if (bearer != null) {
            AppApiTokenEntity token = tokens.findByToken(bearer);
            if (token == null) throw new PlatformException("api_token_invalid", "Invalid API token", null);
            if (requestedAppId != null && !Objects.equals(requestedAppId, token.getAppId())) {
                throw new PlatformException(
                        "app_identity_conflict", "API token does not belong to requested app", null);
            }
            return new Identity(token.getTenantId(), token.getAppId());
        }
        String appId = DifyAppIdResolver.resolve(queryAppId, headerAppId, null);
        String tenantId = currentTenantId();
        apps.require(tenantId, appId);
        return new Identity(tenantId, appId);
    }

    private static String bearer(String header) {
        String value = DifyAppIdResolver.trimToNull(header);
        if (value == null) return null;
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? DifyAppIdResolver.trimToNull(value.substring(7)) : value;
    }

    private static String firstNonBlank(String first, String second) {
        String value = DifyAppIdResolver.trimToNull(first);
        return value == null ? DifyAppIdResolver.trimToNull(second) : value;
    }

    private record Identity(String tenantId, String appId) {}

    private static int indexAfter(List<AppConversationEntity> conversations, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        for (int index = 0; index < conversations.size(); index++) {
            if (conversationId.equals(conversations.get(index).getId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static List<DifyMessageVO> messagesBefore(List<DifyMessageVO> messages, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return messages;
        }
        for (int index = 0; index < messages.size(); index++) {
            if (messageId.equals(messages.get(index).getId())) {
                return new ArrayList<>(messages.subList(0, index));
            }
        }
        return messages;
    }

    private static Comparator<AppConversationEntity> conversationComparator(String sortBy) {
        String ordering = sortBy == null ? "-updated_at" : sortBy.trim().toLowerCase();
        return switch (ordering) {
            case "created_at" -> comparing(AppConversationEntity::getCreatedAt, false);
            case "-created_at" -> comparing(AppConversationEntity::getCreatedAt, true);
            case "updated_at" -> comparing(AppConversationEntity::getUpdatedAt, false);
            default -> comparing(AppConversationEntity::getUpdatedAt, true);
        };
    }

    private static Comparator<AppConversationEntity> comparing(
            java.util.function.Function<AppConversationEntity, java.time.LocalDateTime> timestamp, boolean descending) {
        Comparator<java.time.LocalDateTime> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(timestamp, Comparator.nullsLast(order));
    }
}
