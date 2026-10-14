package io.aigoodle.agent.runtime;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.common.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Explicit runtime selector. Unknown runtime types fail closed instead of silently using NATIVE. */
public final class AgentRuntimeRegistry implements AgentRuntime {
    public static final String NATIVE = "NATIVE";
    private final AgentRuntime nativeRuntime;
    private final Map<String, AgentRuntimeExtension> extensions;
    private final List<AgentRuntimeInterceptor> interceptors;

    public AgentRuntimeRegistry(AgentRuntime nativeRuntime, List<AgentRuntimeExtension> extensions) {
        this(nativeRuntime, extensions, List.of());
    }

    public AgentRuntimeRegistry(
            AgentRuntime nativeRuntime,
            List<AgentRuntimeExtension> extensions,
            List<AgentRuntimeInterceptor> interceptors) {
        this.nativeRuntime = java.util.Objects.requireNonNull(nativeRuntime, "nativeRuntime");
        Map<String, AgentRuntimeExtension> registered = new LinkedHashMap<>();
        if (extensions != null)
            for (AgentRuntimeExtension extension : extensions) {
                String type = normalize(extension.runtimeType());
                if (NATIVE.equals(type)) throw new IllegalArgumentException("NATIVE runtime type is reserved");
                if (registered.putIfAbsent(type, extension) != null) {
                    throw new IllegalArgumentException("Duplicate Agent runtime type: " + type);
                }
            }
        this.extensions = Map.copyOf(registered);
        this.interceptors = interceptors == null
                ? List.of()
                : interceptors.stream()
                        .sorted(java.util.Comparator.comparingInt(AgentRuntimeInterceptor::order))
                        .toList();
    }

    @Override
    public AgentResponse run(
            AgentDefinition definition,
            AgentRequest request,
            Consumer<AgentStep> stepListener,
            Consumer<String> tokenListener) {
        String type = normalize(definition == null ? null : definition.getRuntimeType());
        AgentRuntimeInvocation invocation =
                new AgentRuntimeInvocation(type, definition, request, stepListener, tokenListener);
        return proceed(0, invocation);
    }

    private AgentResponse proceed(int index, AgentRuntimeInvocation invocation) {
        if (index < interceptors.size()) {
            return interceptors
                    .get(index)
                    .intercept(
                            invocation,
                            next -> proceed(index + 1, java.util.Objects.requireNonNull(next, "invocation")));
        }
        AgentRuntime target = runtimeForType(invocation.runtimeType());
        return target.run(
                invocation.definition(), invocation.request(), invocation.stepListener(), invocation.tokenListener());
    }

    private AgentRuntime runtimeForType(String runtimeType) {
        String type = normalize(runtimeType);
        if (NATIVE.equals(type)) return nativeRuntime;
        AgentRuntimeExtension extension = extensions.get(type);
        if (extension == null)
            throw new PlatformException("agent_runtime_unavailable", "Agent runtime is not installed: " + type, null);
        return extension;
    }

    public boolean available(String runtimeType) {
        String type = normalize(runtimeType);
        return NATIVE.equals(type) || extensions.containsKey(type);
    }

    public List<String> runtimeTypes() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(NATIVE), extensions.keySet().stream())
                .toList();
    }

    @Override
    public java.util.Optional<AgentRunSnapshot> findRun(String runId) {
        java.util.Optional<AgentRunSnapshot> nativeRun = nativeRuntime.findRun(runId);
        if (nativeRun.isPresent()) return nativeRun;
        for (AgentRuntimeExtension extension : extensions.values()) {
            java.util.Optional<AgentRunSnapshot> found = extension.findRun(runId);
            if (found.isPresent()) return found;
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<AgentRunSnapshot> findRun(String tenantId, String runId) {
        return findRun(runId).filter(run -> java.util.Objects.equals(tenantId, run.tenantId()));
    }

    public List<AgentRunEvent> runEventsForTenant(String tenantId, String runId, long afterSequence, int limit) {
        requireTenantRun(tenantId, runId);
        return runtimeForRun(runId).runEvents(runId, afterSequence, limit);
    }

    public AgentResponse resumeForTenant(String tenantId, String runId, AgentResumeCommand command) {
        requireTenantRun(tenantId, runId);
        return runtimeForRun(runId).resume(runId, command);
    }

    public AgentRunSnapshot cancelForTenant(String tenantId, String runId) {
        requireTenantRun(tenantId, runId);
        return runtimeForRun(runId).cancel(runId);
    }

    @Override
    public List<AgentRunEvent> runEvents(String runId, long afterSequence, int limit) {
        return runtimeForRun(runId).runEvents(runId, afterSequence, limit);
    }

    @Override
    public AgentResponse resume(String runId, AgentResumeCommand command) {
        return runtimeForRun(runId).resume(runId, command);
    }

    @Override
    public AgentRunSnapshot cancel(String runId) {
        return runtimeForRun(runId).cancel(runId);
    }

    private AgentRuntime runtimeForRun(String runId) {
        AgentRunSnapshot snapshot = findRun(runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found: " + runId, null));
        AgentDefinition definition = JsonUtils.parse(snapshot.definitionJson(), AgentDefinition.class);
        String type = normalize(definition == null ? null : definition.getRuntimeType());
        if (NATIVE.equals(type)) return nativeRuntime;
        AgentRuntimeExtension extension = extensions.get(type);
        if (extension == null)
            throw new PlatformException("agent_runtime_unavailable", "Agent runtime is not installed: " + type, null);
        return extension;
    }

    private AgentRunSnapshot requireTenantRun(String tenantId, String runId) {
        return findRun(tenantId, runId)
                .orElseThrow(() -> new PlatformException("agent_run_not_found", "Agent run not found", null));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? NATIVE
                : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
