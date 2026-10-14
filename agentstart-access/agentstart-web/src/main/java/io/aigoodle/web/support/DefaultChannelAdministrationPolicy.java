package io.aigoodle.web.support;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe default: only host-authenticated tenant administrators may bypass the ownership model. */
public final class DefaultChannelAdministrationPolicy implements ChannelAdministrationPolicy {
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "TENANT_ADMIN", "CONNECTOR_ADMIN");

    @Override
    public void requireAdministrator() {
        CurrentUser user = UserContextHolder.get();
        boolean allowed = user != null
                && user.getRoles() != null
                && user.getRoles().stream().map(String::toUpperCase).anyMatch(ADMIN_ROLES::contains);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant administrator role is required");
    }
}
