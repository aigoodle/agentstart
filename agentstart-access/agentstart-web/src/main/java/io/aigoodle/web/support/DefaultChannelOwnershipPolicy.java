package io.aigoodle.web.support;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe default based only on the trusted context supplied by the embedding host. */
public final class DefaultChannelOwnershipPolicy implements ChannelOwnershipPolicy {
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "TENANT_ADMIN", "CONNECTOR_ADMIN");

    @Override
    public String visibleOwnerId(String requestedOwnerId) {
        CurrentUser user = requireUser();
        if (isAdministrator(user)) return trimToNull(requestedOwnerId);
        String ownerId = ownerId(user);
        if (requestedOwnerId != null && !requestedOwnerId.isBlank() && !ownerId.equals(requestedOwnerId.trim())) {
            throw forbidden();
        }
        return ownerId;
    }

    @Override
    public void requireAccess(String ownerType, String ownerId) {
        CurrentUser user = requireUser();
        if (isAdministrator(user)) return;
        if (ownerId == null || !ownerId(user).equals(ownerId.trim())) throw forbidden();
        String type = ownerType == null ? "USER" : ownerType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("USER", "EMPLOYEE").contains(type)) throw forbidden();
    }

    private static CurrentUser requireUser() {
        CurrentUser user = UserContextHolder.get();
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "host application must provide a trusted CurrentUser");
        }
        return user;
    }

    private static boolean isAdministrator(CurrentUser user) {
        return user.getRoles() != null
                && user.getRoles().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(value -> value.toUpperCase(Locale.ROOT))
                        .anyMatch(ADMIN_ROLES::contains);
    }

    private static String ownerId(CurrentUser user) {
        Object employeeId = user.attr("employeeId");
        String value = employeeId == null ? null : trimToNull(String.valueOf(employeeId));
        return value == null ? user.getUserId().trim() : value;
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "channel account belongs to another employee");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
