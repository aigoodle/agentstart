package io.aigoodle.web.controller;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.host.PluginHostApi;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Invocation-token authenticated API. Compatible with both MVC and WebFlux hosts. */
@RestController
@ConditionalOnClass(PluginHostApi.class)
@ConditionalOnBean(PluginHostApi.class)
@RequestMapping("/plugin-host/v1")
public class PluginHostController {
    private final PluginHostApi host;

    public PluginHostController(PluginHostApi host) {
        this.host = host;
    }

    @PostMapping("/models/chat")
    public Mono<Object> chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> call(authorization, "model.chat", request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/capabilities/{capability}")
    public Mono<Object> capability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String capability,
            @RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> call(authorization, capability, request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Object call(String authorization, String capability, Map<String, Object> request) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Plugin invocation token required");
        try {
            return host.call(authorization.substring(7), capability, request);
        } catch (ConnectorException exception) {
            HttpStatus status = "plugin_host_unauthorized".equals(exception.code())
                    ? HttpStatus.UNAUTHORIZED
                    : "plugin_capability_denied".equals(exception.code()) || "plugin_disabled".equals(exception.code())
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, exception.getMessage());
        }
    }
}
