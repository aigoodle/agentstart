package io.aigoodle.plugin.example;

import io.aigoodle.plugin.*;
import java.util.Map;

/**
 * Example business code: prepare a video brief from authorized internal product data.
 */
public final class ProductVideoPlugin implements Plugin {
    private final PluginManifest manifest;

    public ProductVideoPlugin() {
        try {
            manifest = PluginManifests.load(
                    new org.springframework.core.io.ClassPathResource("plugins/product-video-brief/manifest.yaml"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load packaged video brief manifest", exception);
        }
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public PluginResult execute(PluginInvocation invocation, PluginContext context) {
        if (!"prepare".equals(invocation.actionId()))
            return PluginResult.failure("unknown_action", "Unknown action", false);
        Object productId = invocation.inputs().get("productId");
        if (!(productId instanceof String id) || id.isBlank())
            return PluginResult.failure("invalid_input", "productId is required", false);
        Object product = context.host().call("product.read", Map.of("productId", productId));
        return PluginResult.success(Map.of(
                "text", "请根据商品资料制作视频脚本，风格：" + invocation.inputs().getOrDefault("style", "简洁"), "product", product));
    }
}
