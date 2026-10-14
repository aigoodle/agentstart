package io.aigoodle.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aigoodle.agent.entity.AppConversationEntity;
import io.aigoodle.agent.mapper.AppConversationMapper;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.memory.MemoryManager;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link AppConversationEntity} — the chat sessions grouping messages
 * under an app. The runtime auto-creates rows on the first turn of a new
 * conversation id so users of the memory API don't need to touch this layer;
 * this service exists to serve the console (list / rename / pin / archive).
 */
public class AppConversationService {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int GENERATED_NAME_MAX_LENGTH = 60;

    private final AppConversationMapper conversationMapper;
    private final MemoryManager memoryManager;

    public AppConversationService(AppConversationMapper conversationMapper, MemoryManager memoryManager) {
        this.conversationMapper = conversationMapper;
        this.memoryManager = memoryManager;
    }

    public List<AppConversationEntity> listByApp(String appId) {
        return listByApp(UserContextHolder.currentTenantId(), appId);
    }

    public List<AppConversationEntity> listByApp(String tenantId, String appId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AppConversationEntity>()
                .eq(AppConversationEntity::getTenantId, resolveTenantId(tenantId))
                .eq(AppConversationEntity::getAppId, appId)
                .orderByDesc(AppConversationEntity::getPinned)
                .orderByDesc(AppConversationEntity::getUpdatedAt));
    }

    public AppConversationEntity require(String conversationId) {
        AppConversationEntity conversation =
                conversationMapper.selectOne(new LambdaQueryWrapper<AppConversationEntity>()
                        .eq(AppConversationEntity::getTenantId, UserContextHolder.currentTenantId())
                        .eq(AppConversationEntity::getId, conversationId)
                        .last("LIMIT 1"));
        if (conversation == null) {
            throw new PlatformException("conversation_not_found", "Conversation not found: " + conversationId, null);
        }
        return conversation;
    }

    public AppConversationEntity require(String tenantId, String appId, String conversationId) {
        AppConversationEntity conversation =
                conversationMapper.selectOne(new LambdaQueryWrapper<AppConversationEntity>()
                        .eq(AppConversationEntity::getTenantId, resolveTenantId(tenantId))
                        .eq(AppConversationEntity::getAppId, appId)
                        .eq(AppConversationEntity::getId, conversationId)
                        .last("LIMIT 1"));
        if (conversation == null) {
            throw new PlatformException("conversation_not_found", "Conversation not found: " + conversationId, null);
        }
        return conversation;
    }

    /**
     * Idempotent upsert: called by the chat runtime the first time a
     * conversation id is seen. Never overwrites an existing row's name.
     */
    @Transactional
    public AppConversationEntity ensure(String conversationId, String appId, String tenantId, String firstMessage) {
        return ensure(conversationId, appId, tenantId, firstMessage, "web", null);
    }

    /** Idempotent channel-aware conversation creation. */
    @Transactional
    public AppConversationEntity ensure(
            String conversationId,
            String appId,
            String tenantId,
            String firstMessage,
            String source,
            String endUserId) {
        String tenant = resolveTenantId(tenantId);
        AppConversationEntity existingConversation =
                conversationMapper.selectOne(new LambdaQueryWrapper<AppConversationEntity>()
                        .eq(AppConversationEntity::getTenantId, tenant)
                        .eq(AppConversationEntity::getId, conversationId)
                        .last("LIMIT 1"));
        if (existingConversation != null) {
            if (!tenant.equals(existingConversation.getTenantId()) || !appId.equals(existingConversation.getAppId())) {
                throw new PlatformException(
                        "conversation_id_conflict",
                        "Conversation id is already owned by another tenant or application",
                        null);
            }
            return existingConversation;
        }

        AppConversationEntity newConversation = new AppConversationEntity();
        newConversation.setId(conversationId);
        newConversation.setAppId(appId);
        newConversation.setTenantId(resolveTenantId(tenantId));
        newConversation.setName(truncate(firstMessage, GENERATED_NAME_MAX_LENGTH));
        newConversation.setStatus("normal");
        newConversation.setPinned(false);
        newConversation.setFromSource(source == null || source.isBlank() ? "web" : source);
        newConversation.setFromEndUserId(endUserId);
        conversationMapper.insert(newConversation);
        return newConversation;
    }

    @Transactional
    public AppConversationEntity rename(String conversationId, String name) {
        AppConversationEntity conversation = require(conversationId);
        conversation.setName(name);
        conversationMapper.update(conversation, owned(conversation));
        return conversation;
    }

    @Transactional
    public AppConversationEntity rename(String tenantId, String appId, String conversationId, String name) {
        AppConversationEntity conversation = require(tenantId, appId, conversationId);
        conversation.setName(name);
        conversationMapper.update(conversation, owned(conversation));
        return conversation;
    }

    @Transactional
    public AppConversationEntity togglePinned(String conversationId, boolean pinned) {
        AppConversationEntity conversation = require(conversationId);
        conversation.setPinned(pinned);
        conversationMapper.update(conversation, owned(conversation));
        return conversation;
    }

    @Transactional
    public AppConversationEntity togglePinned(String tenantId, String appId, String conversationId, boolean pinned) {
        AppConversationEntity conversation = require(tenantId, appId, conversationId);
        conversation.setPinned(pinned);
        conversationMapper.update(conversation, owned(conversation));
        return conversation;
    }

    /**
     * Hard delete — also removes the messages under this conversation so the
     * per-app metrics stay honest.
     */
    @Transactional
    public void delete(String conversationId) {
        AppConversationEntity conversation = require(conversationId);
        conversationMapper.delete(ownedQuery(conversation));
        memoryManager.forgetConversation(conversation.getTenantId(), conversation.getAppId(), conversationId);
    }

    @Transactional
    public void delete(String tenantId, String appId, String conversationId) {
        AppConversationEntity conversation = require(tenantId, appId, conversationId);
        conversationMapper.delete(ownedQuery(conversation));
        memoryManager.forgetConversation(conversation.getTenantId(), conversation.getAppId(), conversationId);
    }

    private static com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppConversationEntity> owned(
            AppConversationEntity conversation) {
        return new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppConversationEntity>()
                .eq(AppConversationEntity::getTenantId, conversation.getTenantId())
                .eq(AppConversationEntity::getAppId, conversation.getAppId())
                .eq(AppConversationEntity::getId, conversation.getId());
    }

    private static LambdaQueryWrapper<AppConversationEntity> ownedQuery(AppConversationEntity conversation) {
        return new LambdaQueryWrapper<AppConversationEntity>()
                .eq(AppConversationEntity::getTenantId, conversation.getTenantId())
                .eq(AppConversationEntity::getAppId, conversation.getAppId())
                .eq(AppConversationEntity::getId, conversation.getId());
    }

    private static String resolveTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId;
    }

    private static String truncate(String text, int maximumLength) {
        if (text == null || text.length() <= maximumLength) {
            return text;
        }
        return text.substring(0, maximumLength - 1) + "…";
    }
}
