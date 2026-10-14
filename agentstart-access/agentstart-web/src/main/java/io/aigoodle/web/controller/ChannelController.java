package io.aigoodle.web.controller;

import static io.aigoodle.common.context.UserContextHolder.currentTenantId;

import io.aigoodle.api.ApiResponse;
import io.aigoodle.channel.ChannelAccount;
import io.aigoodle.channel.ChannelAuditService;
import io.aigoodle.channel.ChannelCatalogService;
import io.aigoodle.channel.ChannelDefinition;
import io.aigoodle.channel.ChannelRuntimeProvider;
import io.aigoodle.channel.ChannelRuntimeRegistry;
import io.aigoodle.channel.SaveChannelAccountRequest;
import io.aigoodle.web.support.ChannelRuntimeAdministrationPolicy;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider-neutral management API for long-lived message channel accounts. */
@RestController
@ConditionalOnBean(ChannelRuntimeRegistry.class)
@RequestMapping("/channels")
public class ChannelController {
    public record SaveAccountBody(String name, Boolean enabled, Map<String, Object> config) {}

    private final ChannelRuntimeRegistry runtimes;
    private final ChannelCatalogService catalog;
    private final ChannelRuntimeAdministrationPolicy runtimePolicy;
    private final ChannelAuditService audits;

    public ChannelController(
            ChannelRuntimeRegistry runtimes,
            ChannelCatalogService catalog,
            ChannelRuntimeAdministrationPolicy runtimePolicy,
            ChannelAuditService audits) {
        this.runtimes = runtimes;
        this.catalog = catalog;
        this.runtimePolicy = runtimePolicy;
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<List<ChannelDefinition>> channels(
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(required = false) String runtimeNodeId) {
        return ApiResponse.ok(
                catalog.get(currentTenantId(), runtimeNodeId, refresh).channels());
    }

    @GetMapping("/runtime-nodes")
    public ApiResponse<Map<String, List<String>>> runtimeNodes() {
        return ApiResponse.ok(runtimes.nodes());
    }

    @GetMapping("/{provider}/{channelId}/accounts")
    public ApiResponse<List<ChannelAccount>> accounts(@PathVariable String provider, @PathVariable String channelId) {
        runtimePolicy.requireRuntimeAdministrator();
        return ApiResponse.ok(runtimes.require(provider).accounts(channelId));
    }

    @PutMapping("/{provider}/{channelId}/accounts/{accountId}")
    public ApiResponse<ChannelAccount> save(
            @PathVariable String provider,
            @PathVariable String channelId,
            @PathVariable String accountId,
            @RequestBody SaveAccountBody body) {
        runtimePolicy.requireRuntimeAdministrator();
        ChannelRuntimeProvider runtime = runtimes.require(provider);
        ChannelAccount saved = runtime.saveAccount(new SaveChannelAccountRequest(
                channelId, accountId, body.name(), body.enabled() == null || body.enabled(), body.config()));
        audits.success(
                "RUNTIME_ACCOUNT_SAVE",
                "CHANNEL_ACCOUNT",
                accountId,
                Map.of("provider", provider, "channelId", channelId, "runtimeNodeId", runtime.nodeId()));
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{provider}/{channelId}/accounts/{accountId}/test")
    public ApiResponse<ChannelAccount> test(
            @PathVariable String provider, @PathVariable String channelId, @PathVariable String accountId) {
        runtimePolicy.requireRuntimeAdministrator();
        ChannelRuntimeProvider runtime = runtimes.require(provider);
        ChannelAccount tested = runtime.testAccount(channelId, accountId);
        audits.success(
                "RUNTIME_ACCOUNT_TEST",
                "CHANNEL_ACCOUNT",
                accountId,
                Map.of("provider", provider, "channelId", channelId, "runtimeNodeId", runtime.nodeId()));
        return ApiResponse.ok(tested);
    }

    @DeleteMapping("/{provider}/{channelId}/accounts/{accountId}")
    public ApiResponse<Void> delete(
            @PathVariable String provider, @PathVariable String channelId, @PathVariable String accountId) {
        runtimePolicy.requireRuntimeAdministrator();
        ChannelRuntimeProvider runtime = runtimes.require(provider);
        runtime.deleteAccount(channelId, accountId);
        audits.success(
                "RUNTIME_ACCOUNT_DELETE",
                "CHANNEL_ACCOUNT",
                accountId,
                Map.of("provider", provider, "channelId", channelId, "runtimeNodeId", runtime.nodeId()));
        return ApiResponse.ok(null);
    }
}
