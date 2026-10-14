package io.aigoodle.web.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DefaultChannelAdministrationPolicyTest {
    private final DefaultChannelAdministrationPolicy policy = new DefaultChannelAdministrationPolicy();

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void rejectsAnonymousAndOrdinaryEmployees() {
        assertThatThrownBy(policy::requireAdministrator).isInstanceOf(ResponseStatusException.class);
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("tenant-a")
                .userId("employee-1")
                .roles(Set.of("EMPLOYEE"))
                .build());
        assertThatThrownBy(policy::requireAdministrator).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsHostAuthenticatedTenantAdministrator() {
        UserContextHolder.set(CurrentUser.builder()
                .tenantId("tenant-a")
                .userId("admin-1")
                .roles(Set.of("TENANT_ADMIN"))
                .build());
        assertThatCode(policy::requireAdministrator).doesNotThrowAnyException();
    }
}
