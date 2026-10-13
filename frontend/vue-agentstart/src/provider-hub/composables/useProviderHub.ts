/**
 * useProviderHub — legacy global-singleton API over the unified
 * {@link createAgentStartClient}. Doesn't take a hard dep on axios so the
 * module can be pulled into any Vue 3 app.
 *
 * New code should prefer creating its own client:
 *
 *   const client = createAgentStartClient({ baseUrl: '/api', getAccessToken: … });
 *   client.providers.list(); client.models.register(…);
 *
 * This composable keeps the historical module-level `setProviderHubApiBase` /
 * `setProviderHubHeaders` contract: a single shared client is (re)created
 * whenever either setter runs, and every method below delegates to it.
 */
import { createAgentStartClient, type AgentStartClient } from '../../client';
import type { ModelEntity, ModelType, ProviderDefinition } from '../types';

/**
 * 追加到每个请求的 header。传函数会在每次请求前重新求值，方便宿主接入
 * pinia store 里的 access-token —— 登录/退出时不用重挂 composable。
 */
export type HeadersLike =
  | Record<string, string>
  | (() => Promise<Record<string, string>> | Record<string, string>);

let apiBase = '/api';
let headersProvider: () => Promise<Record<string, string>> | Record<string, string> = () => ({});
let shared: AgentStartClient | null = null;

function client(): AgentStartClient {
  // Rebuild lazily so the setters below can mutate configuration without
  // touching existing references returned by useProviderHub().
  if (!shared) {
    shared = createAgentStartClient({
      baseUrl: apiBase,
      headers: () => headersProvider(),
    });
  }
  return shared;
}

function invalidate() {
  shared = null;
}

export function setProviderHubApiBase(base: string) {
  apiBase = base.replace(/\/+$/, '');
  invalidate();
}

/**
 * 设置每次请求都会拼上的 header（如 Authorization）。示例：
 *   setProviderHubHeaders(() => ({
 *     Authorization: `Bearer ${useAccessStore().accessToken}`,
 *   }));
 */
export function setProviderHubHeaders(headers: HeadersLike) {
  headersProvider = typeof headers === 'function' ? headers : () => headers;
  invalidate();
}

export function useProviderHub() {
  return {
    // ------- providers (tenant-aware — pass tenantId to get "configured" flags)
    listProviders: (tenantId?: string) => client().providers.list(tenantId),
    getProvider: (name: string, tenantId?: string) =>
      client().providers.get(name, tenantId),

    // ------- provider-level credential (Dify-parity "one key per provider")
    getProviderCredential: (name: string, tenantId?: string) =>
      client().providers.getCredential(name, tenantId),
    saveProviderCredential: (
      name: string,
      credentials: Record<string, unknown>,
      tenantId?: string,
    ) => client().providers.saveCredential(name, credentials, tenantId),
    deleteProviderCredential: (name: string, tenantId?: string) =>
      client().providers.deleteCredential(name, tenantId),

    // ------- remote-model discovery (non-destructive peek at the vendor catalog)
    listRemoteModels: (name: string, tenantId?: string) =>
      client().providers.listRemoteModels(name, tenantId),

    /**
     * "重新拉取": ask the backend to re-hit the vendor and return the raw list —
     * <em>transient</em>, no DB writes. The caller merges the returned entries
     * with its cached catalog; only user-enabled models get persisted as
     * settings. This matches the user's requested design:
     *     "读取到的模型太多了 我只想存启用的到 DB，全部就直接掉接口获取"
     */
    refreshCatalog: (name: string, tenantId?: string) =>
      client().providers.refreshCatalog(name, tenantId),

    // ------- installed-model CRUD
    listModels: (tenantId?: string, type?: ModelType) =>
      client().models.list({ tenantId, type }),
    registerModel: (req: Parameters<AgentStartClient['models']['register']>[0]) =>
      client().models.register(req),
    updateCredentials: (id: string, patch: Record<string, unknown>) =>
      client().models.updateCredentials(id, patch),
    setDefault: (id: string) => client().models.setDefault(id),
    /** Flip the {@code enabled} switch on a single row. Off rows stay in the catalog. */
    setModelEnabled: (id: string, enabled: boolean) =>
      client().models.setEnabled(id, enabled),
    deleteModel: (id: string) => client().models.remove(id),
    validateModel: (req: Parameters<AgentStartClient['models']['validate']>[0]) =>
      client().models.validate(req),
    testModel: (id: string) => client().models.test(id),

    // ------- per-model parameters (Dify-parity parameter drawer)
    /** Read current parameter values + the rules the UI should render. */
    getModelParameters: (id: string) => client().models.getParameters(id),
    /**
     * Save a partial parameter patch. `null` values remove the override so the
     * provider falls back to its own default.
     */
    saveModelParameters: (
      id: string,
      parameters: Record<string, boolean | null | number | string>,
    ) => client().models.saveParameters(id, parameters),

    /** Tenant's default model per {@link ModelType}. Feeds the "系统默认模型" panel. */
    listDefaults: async (tenantId?: string) => {
      // Back-compat: the legacy signature was Record<string, ModelEntity>;
      // drop null entries so existing consumers keep compiling unchanged.
      const defaults = await client().models.defaults(tenantId);
      const out: Record<string, ModelEntity> = {};
      for (const [key, value] of Object.entries(defaults)) {
        if (value != null) out[key] = value;
      }
      return out;
    },

    /**
     * Enabled models grouped by ModelType → Provider → modelList. Feeds the
     * "系统默认模型" panel's per-type &lt;a-select&gt; with opt-groups. Only
     * providers with a saved credential + enabled models appear.
     */
    listModelsGroupedByType: (tenantId?: string) =>
      client().models.groupedByType(tenantId),

    // ------- Dify-parity DB-driven metadata (new endpoints)
    /**
     * Unified catalog view for a provider: predefined DB rows + custom rows,
     * each annotated with the tenant's enable / default state pulled from the
     * new settings tables.
     */
    getProviderCatalog: (name: string, tenantId?: string) =>
      client().providers.getCatalog(name, tenantId),

    /**
     * Toggle a predefined model's enabled state by (provider, model, type).
     * Writes to agent_provider_model_setting; missing row = enabled (Dify
     * convention), so we only persist "off" flips.
     */
    setModelEnabledByName: (
      providerName: string,
      modelName: string,
      modelType: ModelType,
      enabled: boolean,
      tenantId?: string,
    ) =>
      client().providers.setModelEnabledByName(
        providerName,
        modelName,
        modelType,
        enabled,
        tenantId,
      ),

    /** Set the tenant default model per model type (Dify-parity path). */
    setModelDefaultByName: (
      providerName: string,
      modelName: string,
      modelType: ModelType,
      tenantId?: string,
    ) =>
      client().providers.setModelDefaultByName(
        providerName,
        modelName,
        modelType,
        tenantId,
      ),

    // ------- provider definition CRUD (extend supported providers via DB)
    /** Available Java implementation bean keys — the pool a definition can point at. */
    listImplementationKeys: () => client().providers.listImplementationKeys(),

    createProviderDefinition: (payload: ProviderDefinition) =>
      client().providers.createDefinition(payload),

    updateProviderDefinition: (id: string, patch: Partial<ProviderDefinition>) =>
      client().providers.updateDefinition(id, patch),

    deleteProviderDefinition: (id: string) =>
      client().providers.deleteDefinition(id),

    /**
     * Extend a provider's predefined catalog — POST adds one row, keyed
     * uniquely by (provider, model, model_type). Idempotent upsert.
     */
    addPredefinedModel: (
      providerName: string,
      payload: Parameters<AgentStartClient['providers']['addPredefinedModel']>[1],
    ) => client().providers.addPredefinedModel(providerName, payload),

    deletePredefinedModel: (providerName: string, id: string) =>
      client().providers.deletePredefinedModel(providerName, id),
  };
}

// Visual helpers — provider iconography lifted straight from the Dify default set
// so cards look right at first glance.
const PROVIDER_ICONS: Record<string, string> = {
  deepseek: '🐳',
  moonshot: '🌘',
  ollama: '🦙',
  openai: '🅾',
  qwen: '🐫',
  siliconflow: '🧪',
  volcengine: '🌋',
  zhipu: '🧠',
};

const PROVIDER_BGS: Record<string, string> = {
  deepseek: '#EFF6FF',
  moonshot: '#F1F5F9',
  ollama: '#F5F3FF',
  openai: '#F0FDF4',
  qwen: '#FEF3C7',
  siliconflow: '#FEF2F2',
  volcengine: '#FEF3C7',
  zhipu: '#EEF2FF',
};

export function providerIcon(name: string): string {
  return PROVIDER_ICONS[name.toLowerCase()] ?? '🤖';
}

export function providerBg(name: string): string {
  return PROVIDER_BGS[name.toLowerCase()] ?? '#F1F5F9';
}

export function modelTypeLabel(t: ModelType | string): string {
  if (t === 'LLM') return 'LLM';
  if (t === 'TEXT_EMBEDDING') return 'TEXT EMBEDDING';
  if (t === 'RERANK') return 'RERANK';
  if (t === 'SPEECH2TEXT') return 'SPEECH2TEXT';
  if (t === 'TTS') return 'TTS';
  if (t === 'MODERATION') return 'MODERATION';
  if (t === 'IMAGE') return 'IMAGE';
  if (t === 'VIDEO') return '视频生成';
  return String(t);
}

export function modelTypeColor(t: ModelType | string): string {
  if (t === 'LLM') return 'blue';
  if (t === 'TEXT_EMBEDDING') return 'green';
  if (t === 'RERANK') return 'orange';
  if (t === 'VIDEO') return 'purple';
  if (t === 'SPEECH2TEXT' || t === 'TTS') return 'purple';
  return 'default';
}
