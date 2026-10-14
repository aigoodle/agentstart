package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Published-site configuration for a chat widget / hosted page (Dify parity).
 * When an app is exposed publicly ({@code apps.enable_site = true}) the
 * consumer-facing surface uses this row for branding and copy.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_sites")
public class AppSiteEntity extends BaseEntity {

    /** FK to {@code apps.id}. */
    private String appId;

    /** Page / widget title. */
    private String title;

    /** Emoji or icon key. */
    private String icon;

    /** Hex tint. */
    private String iconBackground;

    /** {@code emoji} / {@code image}. */
    private String iconType;

    /** Public description shown under the title. */
    private String description;

    /** {@code en-US} / {@code zh-Hans} etc. */
    private String defaultLanguage;

    /** Copyright footer. */
    private String copyright;

    /** Privacy policy URL / text. */
    private String privacyPolicy;

    /** Custom disclaimer shown at conversation start. */
    private String customDisclaimer;

    /** Public URL slug — the widget renders at {@code /site/{code}}. */
    private String code;

    /** JSON blob of chat theme colours. */
    private String chatColorTheme;

    /** Dark-mode variant. */
    private Boolean chatColorThemeInverted;

    /** Show the workflow step trace to end users. */
    private Boolean showWorkflowSteps;

    /** Reuse the app icon for assistant message avatars. */
    private Boolean useIconAsAnswerIcon;

    /** {@code normal} / {@code disabled}. */
    private String status;
}
