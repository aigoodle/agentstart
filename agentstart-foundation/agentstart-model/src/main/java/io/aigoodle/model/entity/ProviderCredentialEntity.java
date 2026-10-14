package io.aigoodle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Provider-level credentials shared by every model of a provider within a tenant,
 * e.g. one OpenAI api key reused by gpt-4o and text-embedding-3.
 * <p>
 * A tenant may configure at most one <em>primary</em> credential per provider name
 * ({@code providerName + tenantId} is effectively unique in typical use); catalog
 * imports and manual model registration both point their {@code credentialId} back
 * at this row. Rotating the key here changes it for every dependent
 * {@link ModelEntity} at once.
 * <p>
 * The rendered credential schema (which fields to prompt for) comes from the
 * in-memory {@link io.aigoodle.model.provider.ModelProvider#credentialSchema()},
 * so this table stores only the encrypted <em>values</em>, not the shape.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_provider_credential")
public class ProviderCredentialEntity extends BaseEntity {

    private String providerName;

    /** Friendly name so a tenant can keep several keys per provider. */
    private String credentialName;

    /** AES-encrypted JSON of credential fields. */
    private String encryptedConfig;

    private Boolean enabled;
}
