package io.aigoodle.model.registry;

import io.aigoodle.common.exception.PlatformException;
import io.aigoodle.model.provider.ModelProvider;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every {@link ModelProvider} discovered in the application context, keyed by
 * provider name. Third-party providers published as Spring beans are included
 * automatically.
 * <p>
 * Precedence: the <b>first</b> entry to claim a name wins. The auto-configuration
 * feeds application-published beans before built-in presets, so a starter jar
 * shipping its own {@code "zhipu"} provider transparently overrides the built-in
 * OpenAI-compatible {@code "zhipu"} preset.
 */
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers = new LinkedHashMap<>();
    /** Secondary lookup by implementation key (Dify-parity: DB row → Java impl). */
    private final Map<String, ModelProvider> byImplKey = new LinkedHashMap<>();

    public ModelProviderRegistry(List<ModelProvider> providerBeans) {
        if (providerBeans != null) {
            for (ModelProvider provider : providerBeans) {
                providers.putIfAbsent(provider.getName().toLowerCase(), provider);
                byImplKey.putIfAbsent(provider.implementationKey().toLowerCase(), provider);
            }
        }
    }

    public ModelProvider get(String providerName) {
        if (providerName == null) {
            throw new PlatformException("provider_name_required", "Provider name must not be null", null);
        }
        ModelProvider provider = providers.get(providerName.toLowerCase());
        if (provider == null) {
            // Fall back to impl key — supports DB definitions whose `name` differs
            // from their Java implementation ("my-openai-proxy" → openai bean).
            provider = byImplKey.get(providerName.toLowerCase());
        }
        if (provider == null) {
            throw new PlatformException(
                    "provider_not_found",
                    "No model provider registered with name or impl-key '" + providerName + "'. Registered: "
                            + providers.keySet(),
                    null);
        }
        return provider;
    }

    /** Look up a Java implementation bean by its stable impl key. */
    public ModelProvider getByImplementationKey(String implKey) {
        if (implKey == null) return null;
        return byImplKey.get(implKey.toLowerCase());
    }

    public boolean contains(String providerName) {
        return providerName != null
                && (providers.containsKey(providerName.toLowerCase())
                        || byImplKey.containsKey(providerName.toLowerCase()));
    }

    public Collection<ModelProvider> all() {
        return List.copyOf(providers.values());
    }

    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    public List<String> implementationKeys() {
        return List.copyOf(byImplKey.keySet());
    }
}
