package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A per-app API access token. Ported from the legacy {@code api_tokens} table.
 * Consumers hit the public chat/completions endpoints with this token in the
 * {@code Authorization: Bearer <token>} header.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_api_tokens")
public class AppApiTokenEntity extends BaseEntity {

    /** FK to {@code apps.id}. */
    private String appId;

    /** {@code app} / {@code dataset} — scope of the token. Defaults to {@code app}. */
    private String type;

    /** Human-readable label the user assigns to the token. */
    private String name;

    /** One-way SHA-256 verifier plus a four-character display hint; raw value is returned only on create. */
    private String token;

    /** Optional: when the token was last used (bumped by the runtime — MVP unused). */
    private java.time.LocalDateTime lastUsedAt;
}
