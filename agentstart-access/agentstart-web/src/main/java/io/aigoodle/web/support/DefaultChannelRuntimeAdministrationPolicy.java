package io.aigoodle.web.support;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe default: tenant administrators cannot enumerate or mutate shared runtime accounts. */
public final class DefaultChannelRuntimeAdministrationPolicy implements ChannelRuntimeAdministrationPolicy {
    private static final Set<String> RUNTIME_ADMIN_ROLES = Set.of("PLATFORM_ADMIN", "SYSTEM_ADMIN", "DEPLOYMENT_ADMIN");

    @Override
    public void requireRuntimeAdministrator() {
        CurrentUser user = UserContextHolder.get();
        boolean allowed = user != null
                && user.getRoles() != null
                && user.getRoles().stream().map(String::toUpperCase).anyMatch(RUNTIME_ADMIN_ROLES::contains);
        if (!allowed)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "deployment administrator role is required for shared channel runtime accounts");
    }
}
