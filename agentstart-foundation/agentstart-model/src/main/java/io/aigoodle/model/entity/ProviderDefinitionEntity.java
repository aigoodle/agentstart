package io.aigoodle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Provider <em>definition</em> — the Dify-parity metadata row that describes a
 * model vendor (label, icon, credential fields, parameter rules). This is the
 * runtime-facing source of truth for provider listing; the Java {@link
 * io.aigoodle.model.provider.ModelProvider} bean referenced by
 * {@link #implementationKey} only supplies the wire code (build ChatModel /
 * EmbeddingModel / listRemoteModels).
 * <p>
 * Rows with {@code source='builtin'} are seeded from the Java provider registry
 * at first boot — so the OOTB experience still works with zero DB config. Rows
 * with {@code source='external'|'custom'} are added by:
 * <ul>
 *   <li>Another Maven module publishing a {@code ProviderDefinitionSeeder} bean
 *       (e.g. a marketplace jar that only knows about metadata, no Java code).</li>
 *   <li>An admin API call to POST {@code /model-provider-definitions} — the
 *       "manage supported providers from the UI without redeploy" flow.</li>
 * </ul>
 * <p>
 * All JSON columns are string-encoded JSON so the schema is portable across H2
 * and MySQL — deserialised into the corresponding value types in the service.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_model_provider")
public class ProviderDefinitionEntity extends BaseEntity {

    /** Unique provider key (e.g. {@code "openai"}, {@code "langgenius/tongyi/tongyi"}). */
    private String name;

    private String label;

    private String description;

    /** SVG key or asset URL — a short identifier / CDN link (VARCHAR). */
    private String icon;

    /**
     * Raw SVG markup for the provider mark, stored as the {@code svg_icon}
     * column (TEXT). Populated for user-defined providers that don't have a
     * built-in {@code svg/<name>.svg} shipped with the frontend. The web UI
     * ({@code ProviderIcon.vue}) renders this via {@code data:image/svg+xml}
     * URL through {@code <img>}, so browsers load it in image mode with
     * scripts and event handlers disabled — safe against XSS by construction.
     */
    private String svgIcon;

    /** JSON array of {@code ModelType.name()} values. */
    private String supportedModelTypes;

    /** JSON array of {@code CredentialField} objects. */
    private String credentialSchema;

    /** JSON map keyed by ModelType name → array of ModelParameterRule. */
    private String defaultParameterRules;

    /**
     * Points to the Java bean in the registry that supplies the actual runtime
     * logic — e.g. {@code "openai_compatible"} or {@code "ollama"}. Multiple DB
     * rows can share one implementation (a new "moonshot-proxy" definition can
     * reuse the OpenAI-compatible impl).
     */
    private String implementationKey;

    private String defaultBaseUrl;

    /** {@code 'builtin' | 'external' | 'custom'}. */
    private String source;

    private Integer sortOrder;

    private Boolean enabled;

    private Boolean supportsRemoteModelListing;
}
