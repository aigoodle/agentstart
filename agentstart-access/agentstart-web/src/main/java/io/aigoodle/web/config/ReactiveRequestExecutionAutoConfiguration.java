package io.aigoodle.web.config;

import io.aigoodle.common.context.UserContextHolder;
import io.micrometer.context.ContextRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import reactor.core.publisher.Hooks;

/** Moves synchronous SDK controllers off Netty while restoring call-scoped identity. */
@AutoConfiguration(before = GoodleWebAutoConfiguration.class)
@ConditionalOnClass(WebFluxConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRequestExecutionAutoConfiguration implements WebFluxConfigurer {
    public ReactiveRequestExecutionAutoConfiguration() {
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        UserContextHolder.CONTEXT_KEY,
                        UserContextHolder::get,
                        UserContextHolder::set,
                        UserContextHolder::clear);
        Hooks.enableAutomaticContextPropagation();
    }

    @Override
    public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
        configurer.setExecutor(new VirtualThreadTaskExecutor("agentstart-http-"));
        configurer.setControllerMethodPredicate(
                method -> method.getBeanType().getPackageName().startsWith("io.aigoodle.")
                        && ReactiveAdapterRegistry.getSharedInstance()
                                        .getAdapter(method.getReturnType().getParameterType())
                                == null);
    }
}
