package io.aigoodle.tool.execution;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates an immutable tool identity snapshot from the host-authenticated user context. */
public final class CurrentUserToolExecutionContextProvider implements ToolExecutionContextProvider {

    @Override
    public ToolExecutionContext currentContext() {
        CurrentUser user = UserContextHolder.tryGet().orElse(null);
        if (user == null) return ToolExecutionContext.anonymous();

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (user.getPrincipalType() != null)
            metadata.put("principalType", user.getPrincipalType().name());
        if (user.getRoles() != null && !user.getRoles().isEmpty()) metadata.put("roles", user.getRoles());
        if (user.getScopes() != null && !user.getScopes().isEmpty()) metadata.put("scopes", user.getScopes());
        if (user.getAppId() != null && !user.getAppId().isBlank()) metadata.put("appId", user.getAppId());
        Object authorization = user.attr("authorization");
        if (authorization != null && !authorization.toString().isBlank()) {
            metadata.put("authorization", authorization.toString());
        }
        return new ToolExecutionContext(null, user.getTenantId(), user.getUserId(), null, metadata);
    }
}
