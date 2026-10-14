package io.aigoodle.completion.support;

import io.aigoodle.common.exception.PlatformException;

/** Secure fallback used when a host has not supplied its own policy. */
public final class DenyChatAccessPolicy implements ChatAccessPolicy {

    @Override
    public ChatAccessContext authorizeInternal(String appId) {
        throw missingPolicy();
    }

    @Override
    public ChatAccessContext authorizeDebug(String appId, String workflowId) {
        throw missingPolicy();
    }

    @Override
    public ChatAccessContext authorizeExternal(String appId) {
        throw missingPolicy();
    }

    private static PlatformException missingPolicy() {
        return new PlatformException("chat_access_policy_missing", "宿主项目尚未配置 ChatAccessPolicy，已拒绝聊天请求", null);
    }
}
