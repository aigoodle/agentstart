package io.aigoodle.tool.annotation;

import io.aigoodle.tool.ToolDefinition;
import io.aigoodle.tool.ToolProvider;
import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.adapter.SpringAiCallbackToolDefinition;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Discovers Spring AI {@link Tool @Tool} methods on every application bean. */
public final class AnnotatedToolProvider implements ToolProvider, BeanPostProcessor, SmartInitializingSingleton {
    private final Map<String, ToolDefinition> discovered = new LinkedHashMap<>();
    private final ObjectProvider<ToolRegistry> registryProvider;

    public AnnotatedToolProvider(ObjectProvider<ToolRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Map<Method, Tool> methods =
                MethodIntrospector.selectMethods(targetClass, (MethodIntrospector.MetadataLookup<Tool>)
                        method -> AnnotatedElementUtils.findMergedAnnotation(method, Tool.class));
        if (methods.isEmpty()) return bean;
        for (var callback :
                MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()) {
            ToolDefinition definition = new SpringAiCallbackToolDefinition(callback);
            discovered.put(definition.name(), definition);
        }
        return bean;
    }

    @Override
    public List<ToolDefinition> getTools() {
        return List.copyOf(discovered.values());
    }

    @Override
    public void afterSingletonsInstantiated() {
        ToolRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) registry.refresh();
    }
}
