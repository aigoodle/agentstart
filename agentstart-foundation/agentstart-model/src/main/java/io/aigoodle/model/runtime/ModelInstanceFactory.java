package io.aigoodle.model.runtime;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.provider.ModelProvider;
import io.aigoodle.model.registry.ModelProviderRegistry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Builds and caches {@link ModelInstance}s from {@link ModelEndpoint}s. The cache key
 * is tenant + endpoint id + model type; {@link #evict(String, String)} must be called whenever a
 * tenant model's configuration or credentials change so a fresh instance is rebuilt on next use.
 * <p>
 * Built chat models are passed through any registered {@link ChatModelDecorator}s,
 * which is how observability/metering plugs in without coupling this module to it.
 */
public class ModelInstanceFactory {

    private record CacheKey(String tenantId, String endpointId, ModelType modelType) {}

    private static final Logger log = LoggerFactory.getLogger(ModelInstanceFactory.class);

    private final ModelProviderRegistry registry;
    private final List<ChatModelDecorator> decorators;
    private final ConcurrentHashMap<CacheKey, ModelInstance> cache = new ConcurrentHashMap<>();

    public ModelInstanceFactory(ModelProviderRegistry registry) {
        this(registry, List.of());
    }

    public ModelInstanceFactory(ModelProviderRegistry registry, List<ChatModelDecorator> decorators) {
        this.registry = registry;
        this.decorators = decorators == null ? List.of() : decorators;
    }

    public ModelInstance getOrCreate(ModelEndpoint endpoint) {
        CacheKey key = cacheKey(endpoint);
        return cache.computeIfAbsent(key, k -> build(endpoint));
    }

    /** Build without touching the cache (e.g. credential validation / one-off calls). */
    public ModelInstance build(ModelEndpoint endpoint) {
        ModelProvider provider = registry.get(endpoint.getProviderName());
        ModelType type = endpoint.getModelType();
        if (type == null) {
            throw new PlatformException(
                    "model_type_required",
                    "Model type is required to build an instance for " + endpoint.getModelName(),
                    null);
        }
        log.debug(
                "Building model instance: provider={}, model={}, type={}",
                endpoint.getProviderName(),
                endpoint.getModelName(),
                type);
        return switch (type) {
            case LLM -> ModelInstance.forChat(
                    endpoint.getId(), endpoint, decorate(provider.createChatModel(endpoint), endpoint));
            case TEXT_EMBEDDING -> ModelInstance.forEmbedding(
                    endpoint.getId(), endpoint, provider.createEmbeddingModel(endpoint));
            default -> throw new PlatformException(
                    "unsupported_model_type", "Model type " + type + " is not yet supported by the runtime", null);
        };
    }

    private ChatModel decorate(ChatModel model, ModelEndpoint endpoint) {
        ChatModel result = model;
        for (ChatModelDecorator decorator : decorators) {
            result = decorator.decorate(result, endpoint);
        }
        return result;
    }

    public void evict(String tenantId, String endpointId) {
        if (endpointId == null) return;
        String normalizedTenant = normalizeTenant(tenantId);
        cache.keySet()
                .removeIf(key -> key.tenantId().equals(normalizedTenant)
                        && key.endpointId().equals(endpointId));
    }

    /**
     * Compatibility and deployment-maintenance operation. Business services should use
     * {@link #evict(String, String)} so one tenant cannot invalidate another tenant's runtime.
     */
    @Deprecated(forRemoval = false)
    public void evict(String endpointId) {
        evictAllTenants(endpointId);
    }

    public void evictAllTenants(String endpointId) {
        if (endpointId == null) return;
        cache.keySet().removeIf(key -> key.endpointId().equals(endpointId));
    }

    public void clear() {
        cache.clear();
    }

    private CacheKey cacheKey(ModelEndpoint endpoint) {
        String id = endpoint.getId() != null
                ? endpoint.getId()
                : endpoint.getProviderName() + ":" + endpoint.getModelName();
        return new CacheKey(normalizeTenant(endpoint.getTenantId()), id, endpoint.getModelType());
    }

    private static String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
    }
}
