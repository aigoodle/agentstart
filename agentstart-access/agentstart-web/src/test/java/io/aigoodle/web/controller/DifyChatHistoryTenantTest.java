package io.aigoodle.web.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.entity.AppApiTokenEntity;
import io.aigoodle.agent.service.AppApiTokenService;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.web.support.DifyHistoryViewMapper;
import io.aigoodle.web.support.DifyMessageHistory;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DifyChatHistoryTenantTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void hostSessionListsOnlyTheTrustedTenantsAppConversations() {
        AppConversationService conversations = mock(AppConversationService.class);
        AppService apps = mock(AppService.class);
        when(conversations.listByApp("tenant-a", "app-1")).thenReturn(List.of());
        DifyChatHistoryController controller = controller(conversations, mock(AppApiTokenService.class), apps);
        UserContextHolder.set(
                CurrentUser.builder().tenantId("tenant-a").userId("employee-1").build());

        controller.listConversations(null, "app-1", null, null, null, 20, null);

        verify(apps).require("tenant-a", "app-1");
        verify(conversations).listByApp("tenant-a", "app-1");
        verify(conversations, never()).listByApp("app-1");
    }

    @Test
    void apiTokenDefinesTenantAndAppAndRejectsConflictingRequestedApp() {
        AppApiTokenService tokens = mock(AppApiTokenService.class);
        AppApiTokenEntity token = new AppApiTokenEntity();
        token.setTenantId("tenant-token");
        token.setAppId("app-token");
        when(tokens.findByToken("secret-token")).thenReturn(token);
        DifyChatHistoryController controller =
                controller(mock(AppConversationService.class), tokens, mock(AppService.class));

        assertThatThrownBy(() ->
                        controller.listConversations("Bearer secret-token", "other-app", null, null, null, 20, null))
                .hasMessageContaining("does not belong");
    }

    @Test
    void unknownBearerTokenIsNeverTreatedAsAnAppId() {
        DifyChatHistoryController controller =
                controller(mock(AppConversationService.class), mock(AppApiTokenService.class), mock(AppService.class));

        assertThatThrownBy(() -> controller.listConversations("Bearer unknown", null, null, null, null, 20, null))
                .hasMessageContaining("Invalid API token");
    }

    private static DifyChatHistoryController controller(
            AppConversationService conversations, AppApiTokenService tokens, AppService apps) {
        return new DifyChatHistoryController(
                conversations, mock(DifyMessageHistory.class), mock(DifyHistoryViewMapper.class), tokens, apps);
    }
}
