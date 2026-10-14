package io.aigoodle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Tenant default model per {@link ModelType} (Dify-parity {@code tenant_default_models}).
 * At most one row per (tenant, model_type); we uphold this in the service layer.
 * Replaces the deprecated {@code agent_model.is_default} flag.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_tenant_default_model")
public class TenantDefaultModelEntity extends BaseEntity {

    private String providerName;

    private String modelName;

    private ModelType modelType;
}
