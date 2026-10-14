package io.aigoodle.completion.support;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.completion.service.ChatAccessService;

/** Convenience policy for hosts that populate {@link UserContextHolder}. */
public final class UserContextChatAccessPolicy implements ChatAccessPolicy {

    private final ChatAccessService resources;

    public UserContextChatAccessPolicy(ChatAccessService resources) {
        this.resources = resources;
    }

    @Override
    public ChatAccessContext authorizeInternal(String appId) {
        requireUser();
        String resolved = resources.requireOwnedApp(appId, UserContextHolder.currentTenantId());
        return context(resolved, ChatAccessMode.INTERNAL);
    }

    public ChatAccessContext authorizeInternalByCode(String appCode) {
        requireUser();
        var app = resources.requireVisibleAppByCode(appCode, UserContextHolder.currentTenantId());
        return new ChatAccessContext(
                app.getId(),
                UserContextHolder.currentTenantId(),
                UserContextHolder.currentUserId(),
                ChatAccessMode.INTERNAL);
    }

    @Override
    public ChatAccessContext authorizeDebug(String appId, String workflowId) {
        requireUser();
        String resolved = resources.requireOwnedWorkflow(appId, UserContextHolder.currentTenantId(), workflowId);
        return context(resolved, ChatAccessMode.DEBUG);
    }

    @Override
    public ChatAccessContext authorizeExternal(String appId) {
        var app = resources.requireExternalAppEntity(appId);
        return new ChatAccessContext(app.getId(), app.getTenantId(), null, ChatAccessMode.EXTERNAL_API);
    }

    private static void requireUser() {
        if (!UserContextHolder.isAuthenticated()) {
            throw new PlatformException("authentication_required", "请先登录后再使用 AI 聊天", null);
        }
    }

    private static ChatAccessContext context(String appId, ChatAccessMode mode) {
        return new ChatAccessContext(
                appId, UserContextHolder.currentTenantId(), UserContextHolder.currentUserId(), mode);
    }
}
