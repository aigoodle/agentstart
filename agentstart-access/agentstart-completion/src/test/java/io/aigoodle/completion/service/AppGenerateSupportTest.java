package io.aigoodle.completion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.service.AppConversationService;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;

@SuppressWarnings("unchecked")
class AppGenerateSupportTest {

    @Test
    void sharedChatKeepsCallerTenantAndIdentity() {
        AppEntity app = new AppEntity();
        app.setId("shared-app");
        app.setTenantId("owner");
        var caller = io.aigoodle.common.context.CurrentUser.builder()
                .tenantId("consumer")
                .userId("consumer-user")
                .departmentId("dept")
                .build();
        UserContextHolder.runAs(
                caller,
                () -> AppGenerateService.callWithUser(app, "consumer-user", () -> {
                    assertThat(UserContextHolder.currentTenantId()).isEqualTo("consumer");
                    assertThat(UserContextHolder.currentDepartmentId()).isEqualTo("dept");
                    assertThat(UserContextHolder.currentAppId()).isEqualTo("shared-app");
                    return null;
                }));
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void preservesTrustedDepartmentAndRolesWhenBindingSameChatUser() {
        AppEntity app = new AppEntity();
        app.setId("app-1");
        app.setTenantId("t1");
        var caller = io.aigoodle.common.context.CurrentUser.builder()
                .userId("u1")
                .tenantId("t1")
                .departmentId("team")
                .roleIds(java.util.Set.of("role-1"))
                .build();
        UserContextHolder.runAs(caller, () -> {
            AppGenerateService.callWithUser(app, "u1", () -> {
                assertThat(UserContextHolder.currentDepartmentId()).isEqualTo("team");
                assertThat(UserContextHolder.currentRoleIds()).containsExactly("role-1");
                assertThat(UserContextHolder.currentAppId()).isEqualTo("app-1");
                return null;
            });
            assertThat(UserContextHolder.get()).isSameAs(caller);
        });
    }

    @Test
    void recognizesBothSupportedFlowModes() {
        assertThat(AppChatRuntimeRouter.isFlowApplication(applicationWithMode("workflow")))
                .isTrue();
        assertThat(AppChatRuntimeRouter.isFlowApplication(applicationWithMode("chatflow")))
                .isTrue();
        assertThat(AppChatRuntimeRouter.isFlowApplication(applicationWithMode("agent")))
                .isFalse();
    }

    @Test
    void assignsConversationIdWhenConversationCatalogIsUnavailable() {
        ObjectProvider<AppConversationService> conversationServices = mock(ObjectProvider.class);
        when(conversationServices.getIfAvailable()).thenReturn(null);
        OpenAIChatRequest request = new OpenAIChatRequest();

        new ChatRequestInitializer(conversationServices, mock(Logger.class)).initialize(new AppEntity(), request);

        assertThat(request.getConversationId()).isNotBlank();
    }

    @Test
    void preservesClientProvidedConversationId() {
        ObjectProvider<AppConversationService> conversationServices = mock(ObjectProvider.class);
        when(conversationServices.getIfAvailable()).thenReturn(null);
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setConversationId("existing-conversation");

        new ChatRequestInitializer(conversationServices, mock(Logger.class)).initialize(new AppEntity(), request);

        assertThat(request.getConversationId()).isEqualTo("existing-conversation");
    }

    @Test
    void bindsResolvedChatUserDuringExecutionAndClearsItAfterwards() {
        AppEntity application = new AppEntity();
        application.setId("app-1");
        application.setTenantId("demo-tenant");

        String resolved = AppGenerateService.callWithUser(application, "demo-user", () -> {
            assertThat(UserContextHolder.currentUserId()).isEqualTo("demo-user");
            assertThat(UserContextHolder.currentTenantId()).isEqualTo("demo-tenant");
            assertThat(UserContextHolder.currentAppId()).isEqualTo("app-1");
            return UserContextHolder.currentUserId();
        });

        assertThat(resolved).isEqualTo("demo-user");
        assertThat(UserContextHolder.get()).isNull();
    }

    private static AppEntity applicationWithMode(String mode) {
        AppEntity application = new AppEntity();
        application.setMode(mode);
        return application;
    }
}
