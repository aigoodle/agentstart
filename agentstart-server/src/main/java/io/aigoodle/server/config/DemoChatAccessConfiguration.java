package io.aigoodle.server.config;

import io.aigoodle.agent.service.AppService;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.completion.service.ChatAccessService;
import io.aigoodle.completion.support.ChatAccessContext;
import io.aigoodle.completion.support.ChatAccessMode;
import io.aigoodle.completion.support.ChatAccessPolicy;
import io.aigoodle.workflow.service.WorkflowService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-login sandbox policy for the standalone demonstration server. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring-agent.demo", name = "enabled", havingValue = "true")
public class DemoChatAccessConfiguration {

    @Bean
    ChatAccessPolicy demoChatAccessPolicy(
            AppService appService,
            ObjectProvider<WorkflowService> workflowServices,
            @Value("${spring-agent.demo.tenant-id:default}") String tenantId,
            @Value("${spring-agent.demo.user-id:demo-user}") String userId,
            @Value("${spring-agent.demo.allow-debug:true}") boolean allowDebug) {
        ChatAccessService resources = new ChatAccessService(appService, workflowServices);
        return new ChatAccessPolicy() {
            @Override
            public ChatAccessContext authorizeInternal(String appId) {
                String resolved = resources.requireOwnedApp(appId, tenantId);
                return context(resolved, ChatAccessMode.INTERNAL);
            }

            @Override
            public ChatAccessContext authorizeDebug(String appId, String workflowId) {
                if (!allowDebug) {
                    throw new PlatformException("demo_debug_disabled", "演示环境未开放调试", null);
                }
                String resolved = resources.requireOwnedWorkflow(appId, tenantId, workflowId);
                return context(resolved, ChatAccessMode.DEBUG);
            }

            @Override
            public ChatAccessContext authorizeExternal(String appId) {
                String resolved = resources.requireExternalApp(appId);
                return context(resolved, ChatAccessMode.EXTERNAL_API);
            }

            private ChatAccessContext context(String appId, ChatAccessMode mode) {
                return new ChatAccessContext(appId, tenantId, userId, mode);
            }
        };
    }
}
