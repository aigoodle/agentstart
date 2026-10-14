/**
 * Types mirror the backend REST payloads (see spring-agent-web ModelController).
 * Kept minimal so consumers don't have to re-declare shapes.
 */

export type ModelType =
  | 'VIDEO'
  | 'IMAGE'
  | 'LLM'
  | 'MODERATION'
  | 'RERANK'
  | 'SPEECH2TEXT'
  | 'TEXT_EMBEDDING'
  | 'TTS';

export interface CredentialField {
  name: string;
  label: string;
  type: string;
  required: boolean;
  secret: boolean;
  defaultValue?: string;
  placeholder?: string;
}

export interface PredefinedModel {
  model: string;
  label: string;
  modelType: string;
  features?: string[];
  contextLength?: number;
  dimensions?: number;
  /** Rule overrides for this specific model, when it differs from the provider default. */
  parameterRules?: ModelParameterRule[];
}

/**
 * Declarative parameter rule (Dify-parity). Mirrors ModelParameterRule.java. The UI
 * uses this to render sliders / number inputs in the parameter drawer without
 * hard-coding what any specific model exposes.
 */
export interface ModelParameterRule {
  name: string;
  label: string;
  type: 'BOOLEAN' | 'FLOAT' | 'INT' | 'STRING';
  min?: number;
  max?: number;
  step?: number;
  precision?: number;
  defaultValue?: boolean | number | string;
  placeholder?: string;
  help?: string;
  required?: boolean;
}

/** GET /models/{id}/parameters response. */
export interface ModelParameters {
  rules: ModelParameterRule[];
  parameters: Record<string, boolean | number | string>;
}

/**
 * Dify-parity provider definition row (agent_model_provider). Read by
 * GET /model-providers now via the new DB-driven path; also the payload for
 * POST /model-provider-definitions when adding an external / custom provider.
 */
export interface ProviderDefinition {
  id?: string;
  tenantId?: string;
  name: string;
  label: string;
  description?: string;
  icon?: string;
  supportedModelTypes: ModelType[];
  credentialSchema: CredentialField[];
  defaultParameterRules?: Record<string, ModelParameterRule[]>;
  implementationKey: string;
  defaultBaseUrl?: string;
  source?: 'builtin' | 'external' | 'custom';
  sortOrder?: number;
  enabled?: boolean;
  supportsRemoteModelListing?: boolean;
}

/**
 * One row of a provider catalog view (predefined + custom merged). Enabled /
 * isDefault flags come from the tenant's settings tables. `source` tells the
 * UI whether this row lives in the shared predefined catalog (edit-only for
 * admins) or as a tenant custom model (delete-able).
 */
export interface CatalogRow {
  id: string;
  model: string;
  label: string;
  modelType: ModelType;
  contextLength?: number;
  dimensions?: number;
  features?: string[];
  parameterRules?: ModelParameterRule[];
  /**
   * Where this row comes from:
   *   predefined — DB-seeded shipped model (from agent_predefined_model).
   *   custom     — tenant-registered custom model (agent_model row).
   *   remote     — transient live-fetched from vendor `/models`; not persisted
   *                until user explicitly enables it (then a setting row is
   *                inserted; no agent_predefined_model row is created).
   */
  source: 'custom' | 'predefined' | 'remote';
  enabled: boolean;
  loadBalancingEnabled?: boolean;
  isDefault: boolean;
  credentialId?: string;
}

/**
 * One entry from a vendor's live catalog (GET /model-providers/{name}/remote-models).
 * `typeInferred` = we guessed by keyword and the UI should let the user confirm.
 */
export interface RemoteModel {
  modelId: string;
  label: string;
  modelType: ModelType;
  contextLength?: number;
  dimensions?: number;
  features?: string[];
  ownedBy?: string;
  typeInferred?: boolean;
}

/**
 * Provider descriptor returned by /model-providers. The trailing four fields are
 * tenant-scoped state added by the backend so a single request feeds the whole
 * "configured / pending / installable" three-section layout.
 */
export interface ProviderView {
  name: string;
  label: string;
  supportedModelTypes: string[];
  credentialSchema: CredentialField[];
  predefinedModels: PredefinedModel[];
  /** Provider-wide fallback rules keyed by model type name (e.g. "LLM", "TEXT_EMBEDDING"). */
  defaultParameterRules?: Record<string, ModelParameterRule[]>;
  supportsRemoteModelListing: boolean;

  /**
   * Legacy single-slot icon column on {@code agent_model_provider.icon}. May
   * carry inline SVG markup, an absolute URL, or a bare key — the
   * {@link providerIconProps} helper in ProviderHubShell disambiguates.
   */
  icon?: string;
  /** Raw SVG markup for user-defined providers stored in the DB. */
  svgIcon?: string;
  /** CDN or backend URL that serves the icon (e.g. {@code /api/agent-start/providers/{name}/icon}). */
  iconUrl?: string;

  credentialConfigured: boolean;
  credentialId?: string;
  credentialMasked?: Record<string, unknown>;
  /** Custom (agent_model) rows, excluding materialized copies of predefined models. */
  installedModelCount: number;
  /**
   * Models whose switch is currently ON (predefined rows gated by the settings
   * table + enabled custom rows). Same gate as the default-model dropdown, so
   * the card counter matches what the popover / dropdown will offer.
   */
  enabledModelCount?: number;
}

export interface ProviderCredentialInfo {
  providerName: string;
  configured: boolean;
  credentialId?: string;
  credentialName?: string;
}

export interface ModelEntity {
  id: string;
  tenantId: string;
  providerName: string;
  modelName: string;
  modelType: ModelType;
  enabled: boolean;
  isDefault: boolean;
  credentialId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ModelRegistration {
  tenantId?: string;
  providerName: string;
  modelName: string;
  modelType: ModelType;
  credentials?: Record<string, unknown>;
  credentialId?: string;
  asDefault?: boolean;
}

/**
 * One provider entry inside a "grouped-by-type" bucket, mirroring the reference
 * project's a-select-opt-group binding: provider + description drive the group
 * header, declaration carries icon hints, modelList feeds the option rows.
 */
export interface GroupedProviderView {
  id: string;
  provider: string;
  label?: string;
  description?: string;
  declaration?: {
    icon?: string;
    svg_icon?: string;
    icon_url?: string;
  };
  modelList: GroupedModelView[];
}

/**
 * A single option inside {@link GroupedProviderView.modelList}. `id` is the
 * composite `${providerName}::${modelName}::${modelType}` — sent back verbatim
 * to identify the target of the set-default call.
 */
export interface GroupedModelView {
  id: string;
  providerName: string;
  modelName: string;
  modelType: ModelType;
}

/**
 * v-model payload for {@link ModelPickerPopover}. Three-tuple keys any model
 * row uniquely and is the same shape the backend uses in default-model and
 * enable-toggle endpoints, so callers can persist it without adaptation.
 *
 * The trailing fields are optional carry-throughs: {@code completionParams}
 * is the parameter-tuning payload (temperature / top_p / max_tokens / …) that
 * the popover now surfaces inline, while {@code provider}/{@code modelProvider}/
 * {@code modelId}/{@code mode} are legacy aliases used by workflow nodes'
 * `formState.model` so they can bind the same picker without a shape adapter.
 * {@code modelId} is only read (never written) — the picker now emits pairs of
 * {@code modelProvider}+{@code modelName} instead of a composite id.
 */
export interface SelectedModel {
  providerName?: string;
  modelName?: string;
  modelType?: ModelType;
  completionParams?: Record<string, boolean | number | string>;
  provider?: string;
  /** Provider key alias — matches the backend column name. */
  modelProvider?: string;
  /** @deprecated Legacy composite id; the picker no longer writes it. */
  modelId?: string;
  mode?: string;
}

export interface ModelTestResult {
  ok: boolean;
  latencyMs: number;
  kind?: string;
  snippet?: string;
  dimensions?: number;
  error?: string;
  message?: string;
}
