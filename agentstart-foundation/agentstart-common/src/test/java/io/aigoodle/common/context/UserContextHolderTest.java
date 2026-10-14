package io.aigoodle.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextHolderTest {

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void nestedScopesRestoreThePreviousUser() {
        CurrentUser outerUser = user("outer", "tenant-a");
        CurrentUser innerUser = user("inner", "tenant-b");

        try (UserContextHolder.ContextScope ignored = UserContextHolder.openScope(outerUser)) {
            assertThat(UserContextHolder.currentUserId()).isEqualTo("outer");
            try (UserContextHolder.ContextScope nested = UserContextHolder.openScope(innerUser)) {
                assertThat(UserContextHolder.currentUserId()).isEqualTo("inner");
            }
            assertThat(UserContextHolder.currentUserId()).isEqualTo("outer");
        }

        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void callAsRestoresContextWhenOperationFails() {
        CurrentUser originalUser = user("original", "tenant-a");
        UserContextHolder.set(originalUser);

        assertThatThrownBy(() -> UserContextHolder.callAs(user("temporary", "tenant-b"), () -> {
                    throw new IllegalStateException("failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(UserContextHolder.get()).isSameAs(originalUser);
    }

    @Test
    void blankTenantUsesProjectDefault() {
        String tenantId = UserContextHolder.callAs(user("user-1", "  "), UserContextHolder::currentTenantId);

        assertThat(tenantId).isEqualTo(UserContextHolder.DEFAULT_TENANT);
    }

    @Test
    void exposesApplicationAndScopesFromTrustedContext() {
        CurrentUser user = CurrentUser.builder()
                .userId("user-1")
                .tenantId("tenant-a")
                .appId(" app-1 ")
                .principalType(PrincipalType.USER)
                .scopes(java.util.Set.of("APP_CHAT", "APP_DEBUG"))
                .build();

        UserContextHolder.runAs(user, () -> {
            assertThat(UserContextHolder.requireAppId()).isEqualTo("app-1");
            assertThat(UserContextHolder.currentPrincipalType()).isEqualTo(PrincipalType.USER);
            assertThat(UserContextHolder.hasScope("APP_DEBUG")).isTrue();
            assertThat(UserContextHolder.hasScope("APP_DELETE")).isFalse();
        });
    }

    private static CurrentUser user(String userId, String tenantId) {
        return CurrentUser.builder().userId(userId).tenantId(tenantId).build();
    }
}
