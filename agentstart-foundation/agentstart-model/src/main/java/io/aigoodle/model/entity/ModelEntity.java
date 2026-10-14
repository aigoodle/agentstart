package io.aigoodle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A concrete, usable model registered by a tenant: a (provider, modelName, type)
 * triple plus optional model-level config overrides.
 * <p>
 * <b>Data association</b> — three concepts, one direction of ownership:
 * <pre>
 *   ModelProvider (SPI bean, in-memory registry)   // "openai", "deepseek", ...
 *          ▲ providerName
 *   ProviderCredentialEntity (agent_provider_credential)   // tenant's API key
 *          ▲ credentialId (nullable — model can override the whole cred stack)
 *   ModelEntity (agent_model)                              // one usable model
 * </pre>
 * <ul>
 *   <li>{@link #credentialId} — FK to {@link ProviderCredentialEntity#getId()}. Set on
 *       every catalog-imported row so the model automatically inherits the provider
 *       key. Rotate the key at the provider level and every model picks it up.</li>
 *   <li>{@link #encryptedConfig} — AES-GCM encrypted JSON of <em>per-model overrides</em>.
 *       Holds two categories under the same key namespace:
 *       <ul>
 *         <li>Model parameters — {@code temperature}, {@code topP}, {@code maxTokens},
 *             {@code dimensions}, {@code topK}, {@code scoreThreshold} — driven by
 *             {@link io.aigoodle.model.provider.ModelParameterRule} rules from
 *             {@link io.aigoodle.model.provider.ModelProvider#defaultParameterRules}.</li>
 *         <li>Credential overrides — a self-hosted {@code baseUrl}, a dedicated
 *             {@code apiKey} for this one model, a Volcengine {@code endpointId}. These
 *             override the provider credential when merged in
 *             {@link io.aigoodle.model.service.ModelService#resolveEndpoint}.</li>
 *       </ul>
 *       Both live in the same map because that's what the provider ultimately reads
 *       out of {@link io.aigoodle.model.provider.ModelEndpoint#getProperties()}.
 *   </li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_model")
public class ModelEntity extends BaseEntity {

    private String providerName;

    private String modelName;

    private ModelType modelType;

    /** FK to {@link ProviderCredentialEntity#getId()}; null = self-contained config. */
    private String credentialId;

    /** AES-encrypted JSON of model-level overrides (parameters + optional credential overrides). */
    private String encryptedConfig;

    private Boolean enabled;

    /** Whether this is the tenant default for its {@link ModelType}. */
    private Boolean isDefault;
}
