<script setup lang="ts">
/**
 * AgentAppsPage — 一站式「应用列表」页面组件。
 *
 * 目标：宿主只需要 `<AgentAppsPage />` 就能获得完整的 Dify 风格应用管理页 ——
 * 列表 / 过滤 / 创建（类型选择器）/ 编辑（设计抽屉）/ 分享嵌入 / 发布 / 删除，
 * 全部内置。api-base 默认 `/api`（宿主代理前缀），/agent-start 命名空间由组件
 * 内部自动拼上。带鉴权的宿主用 :headers 注入 Authorization。
 *
 * 设计约束：
 *   - 组件只依赖 vue / vue-router / 内置 UI 基座 / @ant-design/icons-vue，
 *     以及本模块内的 AppDesignDrawer / CreateAppModal，绝不依赖 Vben 生态
 *     （@vben/*）；
 *   - 所有后端调用都走统一的 createAgentStartClient（props.apiBase 作为
 *     baseUrl，/agent-start 命名空间由 client 内部拼）—— 组件自身不再
 *     直接 fetch 或拼路径；
 *   - 导航（独立对话）通过 useRouter() 内部完成，捕获异常防止宿主没配对应
 *     路由时报错。
 */
import {
  computed,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
} from 'vue';
import { useRouter } from 'vue-router';

import { SearchOutlined } from '@ant-design/icons-vue';
import {
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  Card,
  message,
  Modal,
  Popover,
  Select,
  Skeleton,
  Spin,
  Tag,
  Textarea,
} from '../../ui';

import type { ChatIframeConfig } from '../../agent-flow/components/chat-iframe-types';

import {
  createAgentStartClient,
  readSseEvents,
  type AgentStartClient,
} from '../../client';
import { provideAgentStartClient } from '../../client/vue';
import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import type { AppMode, WorkflowGraphLike } from '../adapters/types';
import type { AppStudioApi } from '../api';
import type { AgentEntity, AgentStrategy, AppType } from '../types';

import AppDesignDrawer from './AppDesignDrawer.vue';
import CreateAppModal from './CreateAppModal.vue';
import AppPermissionsModal from './AppPermissionsModal.vue';

const permissionsApp = ref<AgentEntity | null>(null);
const permissionsOpen = ref(false);
function openPermissions(app: AgentEntity) {
  permissionsApp.value = app;
  permissionsOpen.value = true;
}

// ---------------------------------------------------------------------------
// Card ⋯ menu — the unified ui Popover "bubble card" (mirrors the
// model-provider-hub ProviderHubShell pattern). A single shared open id keeps
// only one card menu open at a time; the Popover panel teleports to <body>, so
// it is never clipped by the card and flips inside the viewport on its own.
// ---------------------------------------------------------------------------
const openMenuFor = ref<string | null>(null);
function toggleCardMenu(id: string) {
  openMenuFor.value = openMenuFor.value === id ? null : id;
}
function closeCardMenu() {
  openMenuFor.value = null;
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------
/**
 * Header 值可以是静态对象，也可以是同步/异步函数 —— 每次请求前都会重新
 * 求值，方便宿主接入自家 access-token store（token 轮换时无须重挂组件）。
 */
interface Props {
  /**
   * 宿主的代理前缀。默认 `/api`：与宿主 vite `/api` 代理约定一致，代理
   * 剥掉 `/api` 后剩下的路径就是后端。组件内部会自动拼上 spring-agent-web
   * 给控制器加的固定 `/agent-start` 命名空间 —— 那是本组件与后端的约定，
   * 宿主不用关心。所以最终请求 URL 是 `${apiBase}/agent-start/xxx`。
   *
   * 若宿主没走 `/api` 代理而是同源直连后端，传 `''`（空字符串），最终
   * 就是 `/agent-start/xxx`。
   */
  apiBase?: string;
  /**
   * 追加到每个请求上的 header。宿主用 vben 等业务框架时，把
   * `Authorization: Bearer xxx` 通过这里注入即可，不用改组件内部代码。
   *
   * 传函数就每次请求前重新求值 —— 推荐用于依赖 pinia store 的 token
   * （登录/退出时不用重挂组件）：
   *   :headers="() => ({ Authorization: `Bearer ${accessStore.accessToken}` })"
   *
   * 传对象就是静态 header —— 适合固定 API-key：
   *   :headers="{ 'X-Api-Key': 'xxx' }"
   */
  headers?: AgentStartHeaders;
}

const props = defineProps<Props>();
const globalConfig = useAgentStartConfig();
const apiBase = computed(() => props.apiBase ?? globalConfig.apiBase ?? '/api');

// ---------------------------------------------------------------------------
// Backend client — every request goes through the unified
// createAgentStartClient. The component no longer concatenates
// `/agent-start` itself; the namespace lives in the client. The instance is
// cached per apiBase and rebuilt when the prop changes, so this component
// stays a true drop-in with zero shared state.
// ---------------------------------------------------------------------------
let cachedClient: AgentStartClient | null = null;
let cachedClientKey = '';

function client(): AgentStartClient {
  if (!cachedClient || cachedClientKey !== apiBase.value) {
    cachedClientKey = apiBase.value;
    cachedClient = createAgentStartClient({
      baseUrl: apiBase.value,
      headers: () =>
        mergeAgentStartHeaders(globalConfig.headers, props.headers),
    });
  }
  return cachedClient;
}

// FlowDesigner and its deeply nested node panels share the same authenticated
// client. Without this provide, those panels can only see global plugin headers
// and lose the headers passed directly to AgentAppsPage.
provideAgentStartClient(client());

// ---- CreateAgent / UpdateAgent request shape (mirrors #/api/agent) ----
interface CreateAgentRequestFull {
  tenantId?: string;
  appCode?: string;
  visibility?: 'GLOBAL' | 'PRIVATE' | 'TENANT_LIST';
  name: string;
  description?: string;
  icon?: string;
  iconBackground?: string;
  mode?: AppMode;
  instructions?: string;
  openingStatement?: string;
  suggestedQuestions?: string[];
  datasetIds?: string[];
  retrievalConfig?: Record<string, unknown>;
  modelName?: string;
  modelProvider?: string;
  runtimeType?: 'NATIVE' | 'SPRING_AI_ALIBABA' | string;
  runtimeRef?: string;
  strategy?: AgentStrategy;
  toolNames?: string[];
  skillIds?: string[];
  approvalRequiredTools?: string[];
  delegateAgentIds?: string[];
  maxIterations?: number;
  memoryEnabled?: boolean;
  memoryWindow?: number;
  published?: boolean;
  modelSettings?: Record<string, unknown>;
}

// ---- Agent CRUD ----
const listAgents = () => client().agents.list() as Promise<AgentEntity[]>;
const listRuntimes = () => client().agents.listRuntimes();
const createAgentReq = (req: CreateAgentRequestFull) =>
  client().agents.create(req) as Promise<AgentEntity>;
const updateAgentReq = (id: string, req: CreateAgentRequestFull) =>
  client().agents.update(id, req) as Promise<AgentEntity>;
const deleteAgentReq = (id: string) => client().agents.remove(id);

// ---- Chat stream (raw fetch — SSE) ----
function previewStreamReq(id: string, body: { query: string }) {
  return client().agents.previewStream(id, body);
}

// ---- Conversations + history (called by AppStudioApi bag) ----
interface ConversationSummary {
  conversationId: string;
  name?: string;
  userId?: null | string;
  firstMessage?: string;
  updatedAt?: string;
  pinned?: boolean;
}
interface HistoryMessage {
  role: 'ASSISTANT' | 'SYSTEM' | 'USER';
  content: string;
}
const listConversationsReq = (appId: string, limit = 100) =>
  client().agents.listConversations(appId, limit) as Promise<
    ConversationSummary[]
  >;
const fetchHistoryReq = (appId: string, conversationId: string, limit = 500) =>
  client().agents.listHistoryMessages(appId, conversationId, limit) as Promise<
    HistoryMessage[]
  >;

// ---- Annotations ----
interface AppAnnotation {
  id: string;
  appId: string;
  question?: string;
  content?: string;
  enabled?: boolean;
  hitCount?: number;
  createdAt?: string;
  updatedAt?: string;
}
interface AppAnnotationRequest {
  question: string;
  content: string;
  enabled?: boolean;
}
const listAnnotationsReq = (appId: string) =>
  client().agents.listAnnotations(appId) as unknown as Promise<AppAnnotation[]>;
const createAnnotationReq = (appId: string, req: AppAnnotationRequest) =>
  client().agents.createAnnotation(
    appId,
    req,
  ) as unknown as Promise<AppAnnotation>;
const updateAnnotationReq = (
  appId: string,
  id: string,
  req: AppAnnotationRequest,
) =>
  client().agents.updateAnnotation(
    appId,
    id,
    req,
  ) as unknown as Promise<AppAnnotation>;
const deleteAnnotationReq = (appId: string, id: string) =>
  client().agents.deleteAnnotation(appId, id);

// ---- API keys ----
interface AppApiKey {
  id: string;
  appId: string;
  name?: string;
  type?: string;
  token: string;
  createdAt?: string;
  lastUsedAt?: string;
}
const listApiKeysReq = (appId: string) =>
  client().agents.listApiKeys(appId) as unknown as Promise<AppApiKey[]>;
const createApiKeyReq = (appId: string, name?: string) =>
  client().agents.createApiKey(appId, name) as unknown as Promise<AppApiKey>;
const renameApiKeyReq = (appId: string, id: string, name: string) =>
  client().agents.renameApiKey(
    appId,
    id,
    name,
  ) as unknown as Promise<AppApiKey>;
const deleteApiKeyReq = (appId: string, id: string) =>
  client().agents.deleteApiKey(appId, id);

// ---- Metrics / LLM Ops ----
interface AppMetrics {
  appId: string;
  totalConversations: number;
  totalMessages: number;
  userMessages: number;
  assistantMessages: number;
  avgInteractionsPerConversation: number;
  lastActivityAt?: string;
}
interface LlmUsageStats {
  model?: string;
  calls: number;
  errors: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  costMicros: number;
  avgLatencyMs: number;
}
interface LlmCallRecord {
  id: string;
  provider: string;
  model: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  costMicros?: number;
  latencyMs?: number;
  success: boolean;
  errorType?: string;
  createdAt?: string;
}
const fetchAppMetricsReq = (appId: string) =>
  client().agents.fetchAppMetrics(appId) as unknown as Promise<AppMetrics>;
const fetchTotalReq = () =>
  client().agents.fetchLlmUsage() as unknown as Promise<LlmUsageStats>;
const fetchRecentReq = (limit = 50) =>
  client().agents.fetchRecentLlmCalls(limit) as unknown as Promise<
    LlmCallRecord[]
  >;
const fetchLlmTrendReq = (range: 'HOUR' | 'DAY' | 'WEEK') =>
  client().agents.fetchLlmTrend(range);

// ---- Models / Tools / Datasets ----
interface ModelEntity {
  id: string;
  tenantId?: string;
  providerName: string;
  modelName: string;
  modelType: string;
  enabled: boolean;
  isDefault: boolean;
}
interface ToolView {
  name: string;
  description: string;
  inputSchema?: string;
  label?: string;
  category?: string;
  icon?: string;
  provider?: string;
  connectorId?: string;
  actionId?: string;
  riskLevel?: string;
  configured?: boolean;
}
interface DatasetEntity {
  id: string;
  name: string;
  description?: string;
}
const listModelsReq = (type?: string) =>
  client().models.list({ type }) as unknown as Promise<ModelEntity[]>;
const listToolsReq = () =>
  client().agents.listToolsLite() as unknown as Promise<ToolView[]>;
const listDatasetsReq = () =>
  client().agents.listDatasetsLite() as unknown as Promise<DatasetEntity[]>;

// ---- Workflow draft ----
interface WorkflowEntity {
  id: string;
  appId?: string;
  graph?: unknown;
  version?: string;
  published?: boolean;
  markedName?: string;
  markedComment?: string;
  createdAt?: string;
  publishedAt?: string;
  versionNumber?: number;
  status?: 'ACTIVE' | 'SUPERSEDED' | 'DISABLED';
}
const getWorkflowDraftReq = (appId: string) =>
  client().workflows.getDraft(appId) as unknown as Promise<WorkflowEntity>;
const saveWorkflowDraftReq = (appId: string, graph: unknown) =>
  client().workflows.saveDraft(
    appId,
    graph as WorkflowGraphLike,
  ) as unknown as Promise<WorkflowEntity>;
const publishWorkflowDraftReq = (appId: string) =>
  client().workflows.publishDraft(appId) as unknown as Promise<WorkflowEntity>;
const listWorkflowHistoryReq = (appId: string) =>
  client().workflows.listByApp(appId) as unknown as Promise<WorkflowEntity[]>;
const restoreWorkflowSnapshotReq = (appId: string, snapshotId: string) =>
  client().workflows.restoreSnapshot(appId, snapshotId) as unknown as Promise<WorkflowEntity>;

// ---------------------------------------------------------------------------
// AppStudioApi callback bag — the drawer's built-in panels call these.
// ---------------------------------------------------------------------------
const studioApi: AppStudioApi = {
  runWorkflowGraph: (payload) =>
    client().workflows.runGraph(payload as any),
  runWorkflowGraphStream: (payload, opts) =>
    client().workflows.runGraphStream(payload as any, opts),
  cancelWorkflowRun: (runId, reason) => client().workflows.cancelRun(runId, reason),
  pauseWorkflowRun: (runId, reason) => client().workflows.pauseRun(runId, reason),
  resumeWorkflowRun: (runId) => client().workflows.resumeRun(runId),
  signalWorkflowRun: (runId, request) => client().workflows.signalRun(runId, request as any),
  listPublishedWorkflowOptions: () =>
    client().agents.listPublishedWorkflowOptions(),
  listConversations: (appId: string, limit?: number) =>
    listConversationsReq(appId, limit),
  fetchHistory: (appId: string, conversationId: string, limit?: number) =>
    fetchHistoryReq(appId, conversationId, limit),
  listAnnotations: (appId: string) => listAnnotationsReq(appId),
  createAnnotation: (appId: string, req: any) =>
    createAnnotationReq(appId, req),
  updateAnnotation: (appId: string, id: string, req: any) =>
    updateAnnotationReq(appId, id, req),
  deleteAnnotation: (appId: string, id: string) =>
    deleteAnnotationReq(appId, id),
  fetchAppMetrics: (appId: string) => fetchAppMetricsReq(appId),
  fetchAppLlmUsage: (appId: string) => client().agents.fetchAppLlmUsage(appId),
  fetchAppRecentLlmCalls: (appId: string, limit = 10) => client().agents.fetchAppRecentLlmCalls(appId, limit),
  fetchAppLlmTrend: (appId: string, range: 'HOUR' | 'DAY' | 'WEEK') => client().agents.fetchAppLlmTrend(appId, range),
  fetchLlmUsage: () => fetchTotalReq() as any,
  fetchRecentLlmCalls: (limit?: number) => fetchRecentReq(limit) as any,
  fetchLlmTrend: (range: 'HOUR' | 'DAY' | 'WEEK') => fetchLlmTrendReq(range) as any,
  listApiKeys: (appId: string) => listApiKeysReq(appId) as any,
  createApiKey: (appId: string, name?: string) =>
    createApiKeyReq(appId, name) as any,
  renameApiKey: (appId: string, id: string, name: string) =>
    renameApiKeyReq(appId, id, name) as any,
  deleteApiKey: (appId: string, id: string) => deleteApiKeyReq(appId, id),
};

// ---------------------------------------------------------------------------
// Navigation helpers — routed via useRouter(); wrapped in try/catch so hosts
// that lack the expected routes (AgentChat, ModelList) don't blow up.
// ---------------------------------------------------------------------------
const router = useRouter();

function openChat(a: AgentEntity) {
  try {
    router?.push({ name: 'AgentChat', params: { id: a.id } });
  } catch {
    // Host doesn't have an AgentChat route — silently no-op.
  }
}

// ---------------------------------------------------------------------------
// Chat iframe config — minimal port of #/adapter/chat-iframe-config.ts, sans
// any @vben/preferences / @vben/stores dependency. `context.user` stays null
// (hosts can wrap the iframe in their own theme provider if needed).
// ---------------------------------------------------------------------------
interface AgentLike {
  id?: string;
  name?: string;
  mode?: string;
  workflowId?: string;
}

type HostTheme = 'dark' | 'light';

/**
 * iframe 的主题必须来自宿主，而不是在组件库里写死。优先兼容常见的
 * `data-theme="dark|light"` 约定，同时兼容 Ant Design/Tailwind 常用的
 * `<html class="dark">`。
 */
const hostTheme = ref<HostTheme>('light');
let themeObserver: MutationObserver | null = null;

function readHostTheme(): HostTheme {
  if (typeof document === 'undefined') return 'light';
  const root = document.documentElement;
  if (root.dataset.theme === 'dark' || root.dataset.theme === 'light') {
    return root.dataset.theme;
  }
  return root.classList.contains('dark') ? 'dark' : 'light';
}

function syncHostTheme() {
  hostTheme.value = readHostTheme();
}

function resolveIframeBase(): string {
  const raw = (
    import.meta.env.VITE_CHAT_IFRAME_BASE as string | undefined
  )?.trim();
  const base = raw && raw.length > 0 ? raw : '/chat/';
  return base.endsWith('/') ? base : `${base}/`;
}

function buildChatIframeConfig(
  agent: AgentLike | null,
  options: { debug?: boolean; timeoutMs?: number } = {},
): ChatIframeConfig {
  const src = resolveIframeBase();
  const label = agent?.name || agent?.id || 'spring-agent';
  const workflowId = agent?.workflowId;
  return {
    src,
    title: agent?.name ? `调试：${agent.name}` : '调试与预览',
    sessionKey: `${agent?.mode ?? 'agent'}-${agent?.id ?? 'draft'}`,
    params: {
      // `mode` belongs to the chat UI and must remain Copilot.
      // Backend routing uses a separate parameter to avoid changing iframe UI mode.
      mode: 'Copilot',
      accessMode: options.debug ? 'debug' : 'internal',
      label,
      appId: agent?.id,
      // Debug defaults to the mutable draft (backend invariant: draft id == appId).
      // A concrete workflow version is only supplied by an explicit version picker.
      workflowId: options.debug ? undefined : workflowId,
      timeoutMs: options.timeoutMs ?? (options.debug ? 120_000 : undefined),
    },
    context: {
      appId: agent?.id,
      appMode: agent?.mode,
      appName: agent?.name,
      user: null,
      theme: hostTheme.value,
    },
  };
}

// ---------------------------------------------------------------------------
// Reactive state — direct port of the original list.vue.
// ---------------------------------------------------------------------------
const agents = ref<AgentEntity[]>([]);
const llmModels = ref<ModelEntity[]>([]);
const runtimeTypes = ref<string[]>(['NATIVE']);
const tools = ref<ToolView[]>([]);
const datasets = ref<DatasetEntity[]>([]);
const loading = ref(false);
const llmLoaded = ref(false);

const keyword = ref('');
/**
 * 按"应用类型"(mode)过滤 —— 对应 Dify 的 chat / agent / workflow / completion 家族。
 * 'ALL' 表示不过滤。
 */
const filterMode = ref<'ALL' | AppMode>('ALL');
/** Draft vs published filter — Dify's "只看已发布 / 只看草稿" toggle. */
const filterPublished = ref<'all' | 'draft' | 'published'>('all');

const showCreate = ref(false);
const submitting = ref(false);
/**
 * Dify-style two-step create: first pick an "app type" from CreateAppModal,
 * then land in the config form with defaults seeded from that choice.
 */
const showTypePicker = ref(false);
const editingId = ref<null | string>(null);
/**
 * Basic-info edit form — the "编辑基本信息" surface intentionally only carries
 * the four presentation fields (name / icon / iconBackground / description).
 */
const form = reactive({
  name: '',
  description: '',
  icon: '',
  iconBackground: '',
  appCode: '',
  visibility: 'PRIVATE' as 'GLOBAL' | 'PRIVATE' | 'TENANT_LIST',
});

// Dify's app-card mode icons — colored square with an emoji
const ICONS = ['🤖', '💬', '🧠', '🎯', '🛠', '📊', '💡', '⚡', '🔎', '📎'];
// Background colors for icon picker
const BGS = [
  '#FFF4ED', '#EEF4FF', '#EFFDF4', '#FEF3F2',
  '#FFF8E6', '#FDF2FA', '#F0F9FF', '#F0FDF9',
];
// Background colors now use CSS variables for automatic dark mode support
const BG_COUNT = 8;
function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = Math.trunc((h << 5) - h + s.charCodeAt(i));
  }
  return Math.abs(h);
}
function iconOf(a: AgentEntity) {
  return a.icon || ICONS[hashCode(a.id || a.name) % ICONS.length];
}
function bgOf(a: AgentEntity) {
  if (a.iconBackground) return a.iconBackground;
  const idx = (hashCode((a.id || a.name) + '.bg') % BG_COUNT) + 1;
  return `var(--as-card-icon-bg-${idx})`;
}
function modeLabel(m?: string): string {
  if (m === 'chat') return 'Chat';
  if (m === 'chatflow') return 'Chatflow';
  if (m === 'workflow') return 'Workflow';
  if (m === 'completion') return 'Completion';
  return 'Agent';
}
function modeColor(m?: string): string {
  if (m === 'chat') return 'blue';
  if (m === 'chatflow') return 'cyan';
  if (m === 'workflow') return 'purple';
  if (m === 'completion') return 'orange';
  return 'geekblue';
}

function fromNow(iso?: string): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86_400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86_400 * 30) return `${Math.floor(diff / 86_400)} 天前`;
  return new Date(iso).toLocaleDateString();
}

function strategyLabel(s?: string): string {
  if (s === 'REACT') return 'ReAct';
  if (s === 'FUNCTION_CALLING') return 'Function Calling';
  if (s === 'PLAN_EXECUTE') return 'Plan & Execute';
  return s ?? '';
}
function strategyColor(s?: string): string {
  if (s === 'FUNCTION_CALLING') return 'green';
  if (s === 'PLAN_EXECUTE') return 'purple';
  return 'blue';
}

function modelLabel(a: AgentEntity): string {
  if (a.modelProvider && a.modelName) {
    return `${a.modelProvider} · ${a.modelName}`;
  }
  if (a.modelName) return a.modelName;
  return '未选择模型';
}

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  return agents.value.filter((a) => {
    if (filterMode.value !== 'ALL' && a.mode !== filterMode.value) {
      return false;
    }
    if (filterPublished.value === 'published' && a.published !== true)
      return false;
    if (filterPublished.value === 'draft' && a.published === true)
      return false;
    if (!q) return true;
    return (
      a.name.toLowerCase().includes(q) ||
      (a.description ?? '').toLowerCase().includes(q) ||
      (a.instructions ?? '').toLowerCase().includes(q)
    );
  });
});

/** Convenience count helpers for the filter tabs. */
const publishedCount = computed(
  () => agents.value.filter((a) => a.published === true).length,
);
const draftCount = computed(
  () => agents.value.filter((a) => a.published !== true).length,
);

async function refresh() {
  loading.value = true;
  try {
    agents.value = await listAgents();
  } catch (e: any) {
    // 后端 401/404/网络断开等都在这里兜住 —— 不能让异常冒到
    // onMounted 里变成 unhandledrejection,那会触发 vite 错误覆盖层,
    // 视觉上像 <KeepAlive> 里的路由切换全部白屏。
    agents.value = [];
    message.error(`加载应用列表失败：${e?.message ?? e}`);
  } finally {
    loading.value = false;
  }
}

async function loadDeps() {
  try {
    runtimeTypes.value = (await listRuntimes()).map((item) => item.type);
  } catch {
    runtimeTypes.value = ['NATIVE'];
  }
  try {
    llmModels.value = await listModelsReq('LLM');
  } catch {
    llmModels.value = [];
  } finally {
    llmLoaded.value = true;
  }
  try {
    tools.value = await listToolsReq();
  } catch {
    tools.value = [];
  }
  try {
    datasets.value = await listDatasetsReq();
  } catch {
    datasets.value = [];
  }
}

function openTypePicker() {
  showTypePicker.value = true;
}

/**
 * Dify-parity create flow — always creates an `apps` row and refreshes the list
 * in place. Creating an app must never be gated on model availability.
 *
 * The 4 app types map to Dify's `mode` field on the `apps` row so a single
 * table backs the whole family:
 *   chatbot        → mode='chat'         · REACT
 *   agent          → mode='agent'        · FUNCTION_CALLING
 *   workflow       → mode='workflow'     · FUNCTION_CALLING + empty graph seed
 *   text-generator → mode='completion'   · PLAN_EXECUTE, memory off
 */
async function onCreateApp(payload: {
  appType: AppType;
  name: string;
  description: string;
  icon: { emoji: string; background: string };
}) {
  if (!llmLoaded.value) {
    await loadDeps();
  }
  const defaultModel =
    llmModels.value.find((m) => m.isDefault) ?? llmModels.value[0];

  const modeByType: Record<string, AppMode> = {
    agent: 'agent',
    chatbot: 'chat',
    chatflow: 'chatflow',
    'text-generator': 'completion',
    workflow: 'workflow',
  };
  const strategyByType: Record<string, AgentStrategy> = {
    agent: 'FUNCTION_CALLING',
    chatbot: 'REACT',
    chatflow: 'REACT',
    'text-generator': 'PLAN_EXECUTE',
    workflow: 'FUNCTION_CALLING',
  };
  const mode = modeByType[payload.appType] ?? 'agent';
  const strategy = strategyByType[payload.appType] ?? 'REACT';
  const maxIterations = payload.appType === 'text-generator' ? 3 : 6;
  const memoryWindow = payload.appType === 'text-generator' ? 0 : 20;

  submitting.value = true;
  try {
    await createAgentReq({
      name: payload.name,
      published: false,
      description: payload.description || undefined,
      icon: payload.icon.emoji,
      iconBackground: payload.icon.background,
      mode,
      instructions: payload.description || undefined,
      modelName: defaultModel?.modelName ?? undefined,
      modelProvider: defaultModel?.providerName ?? undefined,
      strategy,
      toolNames: [],
      maxIterations,
      memoryEnabled: memoryWindow > 0,
      memoryWindow,
    });
    message.success(`已创建「${payload.name}」`);
    await refresh();
  } catch (e: any) {
    message.error(e?.message ?? '创建失败');
  } finally {
    submitting.value = false;
  }
}

// ---------------------------------------------------------------------------
// Design drawer — click a card to open a Dify-style drawer where the user can
// design the app. Workflow-mode apps see the DAG canvas; others see the
// orchestrate/preview two-pane. Persisted via updateAgent().
// ---------------------------------------------------------------------------
const drawerOpen = ref(false);
const drawerApp = ref<AgentEntity | null>(null);
/**
 * Draft-workflow graph JSON prefetched for the currently open drawer. Only
 * populated for workflow / chatflow apps.
 */
const drawerGraphJson = ref<string>('');
const drawerWorkflowHistory = ref<WorkflowEntity[]>([]);

/**
 * 抽屉里挂 ChatIframePanel / FlowDesigner 的调试 iframe 配置。每次打开抽屉时
 * 按当前 app 重新构造 —— session key 走 mode+id，切换 app 不会串会话。
 */
const drawerChatConfig = computed<ChatIframeConfig | null>(() =>
  drawerApp.value
    ? buildChatIframeConfig(drawerApp.value as AgentLike, { debug: true })
    : null,
);

async function openDesigner(a: AgentEntity) {
  drawerApp.value = a;
  drawerGraphJson.value = '';
  drawerWorkflowHistory.value = [];
  // Prefetch the draft BEFORE opening the drawer for flow-mode apps so
  // FlowDesigner mounts with the real payload already on `props` (avoids a
  // race with initGraph()'s setTimeout(10) seed).
  if (a.mode === 'workflow' || a.mode === 'chatflow') {
    try {
      const [draft, history] = await Promise.all([
        getWorkflowDraftReq(a.id),
        listWorkflowHistoryReq(a.id),
      ]);
      if (draft?.graph) drawerGraphJson.value = JSON.stringify(draft.graph);
      drawerWorkflowHistory.value = history.filter((item) => item.published === true);
    } catch {
      drawerGraphJson.value = '';
    }
  } else {
    try {
      drawerWorkflowHistory.value = (await client().agents.listVersions(a.id)).map((version) => ({
        id: version.id,
        versionNumber: version.versionNumber,
        version: `v${version.versionNumber}`,
        status: version.status,
        markedComment: version.changeSummary,
        publishedAt: version.publishedAt,
      }));
    } catch {
      drawerWorkflowHistory.value = [];
    }
  }
  drawerOpen.value = true;
  if (!llmLoaded.value) loadDeps();
}

/**
 * Safely parse the drawer's serialised retrieval config so we can hand the
 * backend a plain object. Empty / malformed input returns undefined.
 */
function parseRetrievalConfig(
  json?: string,
): Record<string, unknown> | undefined {
  if (!json) return undefined;
  try {
    const v = JSON.parse(json);
    return v && typeof v === 'object' && !Array.isArray(v) ? v : undefined;
  } catch {
    return undefined;
  }
}

interface DrawerSavePayload {
  appId: string;
  mode: AppMode;
  graphJson?: string;
  name?: string;
  instructions?: string;
  openingStatement?: string;
  modelName?: string;
  modelProvider?: string;
  runtimeType?: 'NATIVE' | 'SPRING_AI_ALIBABA' | string;
  runtimeRef?: string;
  modelSettings?: Record<string, unknown>;
  toolNames?: string[];
  skillIds?: string[];
  datasetIds?: string[];
  retrievalConfigJson?: string;
}

async function persistDrawerDraft(payload: DrawerSavePayload) {
  const existing = agents.value.find((x) => x.id === payload.appId);
  await updateAgentReq(payload.appId, {
      name: payload.name ?? existing?.name ?? '',
      mode: payload.mode,
      instructions: payload.instructions,
      openingStatement: payload.openingStatement,
      modelName: payload.modelName ?? existing?.modelName,
      modelProvider: payload.modelProvider ?? existing?.modelProvider,
      runtimeType: payload.runtimeType ?? existing?.runtimeType ?? 'NATIVE',
      runtimeRef: payload.runtimeRef,
      modelSettings: payload.modelSettings,
      strategy: (existing?.strategy as AgentStrategy) ?? 'REACT',
      toolNames: payload.toolNames ?? [],
      skillIds: payload.skillIds ?? [],
      datasetIds: payload.datasetIds ?? [],
      retrievalConfig: parseRetrievalConfig(payload.retrievalConfigJson),
      maxIterations: existing?.maxIterations,
      memoryEnabled: existing?.memoryEnabled,
      memoryWindow: existing?.memoryWindow,
      published: existing?.published,
  });
  if ((payload.mode === 'workflow' || payload.mode === 'chatflow') && payload.graphJson) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(payload.graphJson);
    } catch {
      throw new Error('画布数据无法解析');
    }
    await saveWorkflowDraftReq(payload.appId, parsed);
  }
}

async function onDrawerSave(payload: DrawerSavePayload) {
  submitting.value = true;
  try {
    await persistDrawerDraft(payload);
    message.success('已保存');
    await refresh();
    const fresh = agents.value.find((x) => x.id === payload.appId);
    if (fresh) drawerApp.value = fresh;
  } catch (e: any) {
    message.error(e?.message ?? '保存失败');
  } finally {
    submitting.value = false;
  }
}

/**
 * Brand popover → 编辑信息 shortcut. Reuses the same basic-info modal the
 * card ⋯ menu opens; the drawer stays open so the user can drop back into
 * design after saving.
 */
function onDrawerEditInfo(payload: { appId: string }) {
  const a = agents.value.find((x) => x.id === payload.appId);
  if (a) openEdit(a);
}
function onDrawerExportDsl(_payload: { appId: string }) {
  message.info('导出 DSL 功能开发中');
}
function onDrawerDuplicate(_payload: { appId: string }) {
  message.info('复制应用功能开发中');
}

async function onDrawerPublish(payload: DrawerSavePayload) {
  try {
    // Save and publish are deliberately sequential: an immutable version must
    // snapshot exactly the draft visible in the designer.
    await persistDrawerDraft(payload);
    const app = agents.value.find((item) => item.id === payload.appId);
    const flowMode = app?.mode === 'workflow' || app?.mode === 'chatflow';
    if (flowMode) {
      await publishWorkflowDraftReq(payload.appId);
    } else {
      await client().agents.publishVersion(payload.appId, '从应用设计器发布');
    }
    message.success('已发布新版本');
    await refresh();
    if (flowMode) {
      drawerWorkflowHistory.value = (await listWorkflowHistoryReq(payload.appId))
        .filter((item) => item.published === true);
    } else {
      drawerWorkflowHistory.value = (await client().agents.listVersions(payload.appId)).map((version) => ({
        id: version.id,
        versionNumber: version.versionNumber,
        version: `v${version.versionNumber}`,
        status: version.status,
        markedComment: version.changeSummary,
        publishedAt: version.publishedAt,
      }));
    }
  } catch (e: any) {
    message.error(e?.message ?? '发布失败');
  }
}

async function onDrawerRestore(payload: { appId: string; snapshotId: string }) {
  try {
    const app = agents.value.find((item) => item.id === payload.appId);
    if (app?.mode !== 'workflow' && app?.mode !== 'chatflow') {
      await client().agents.rollbackVersion(payload.appId, payload.snapshotId, '从应用设计器回滚');
      drawerWorkflowHistory.value = (await client().agents.listVersions(payload.appId)).map((version) => ({
        id: version.id,
        versionNumber: version.versionNumber,
        version: `v${version.versionNumber}`,
        status: version.status,
        markedComment: version.changeSummary,
        publishedAt: version.publishedAt,
      }));
      message.success('已回滚并发布新版本');
      return;
    }
    const draft = await restoreWorkflowSnapshotReq(payload.appId, payload.snapshotId);
    drawerGraphJson.value = draft.graph ? JSON.stringify(draft.graph) : '';
    message.success('已恢复到所选发布版本，请保存或重新发布');
  } catch (e: any) {
    message.error(e?.message ?? '恢复失败');
  }
}

async function onDrawerDisableVersion(payload: { appId: string; versionId: string }) {
  try {
    await client().agents.disableVersion(payload.appId, payload.versionId);
    drawerWorkflowHistory.value = (await client().agents.listVersions(payload.appId)).map((version) => ({
      id: version.id,
      versionNumber: version.versionNumber,
      version: `v${version.versionNumber}`,
      status: version.status,
      markedComment: version.changeSummary,
      publishedAt: version.publishedAt,
    }));
    message.success('已停用该版本，生产请求将停止使用它');
  } catch (e: any) {
    message.error(e?.message ?? '停用失败');
  }
}

/**
 * Handle the drawer's `preview` event — pipe the SSE chat stream into the
 * preview pane. Only used for non-workflow modes.
 */
async function onDrawerPreview(payload: {
  appId: string;
  query: string;
  onChunk: (t: string) => void;
  onDone: () => void;
  onError: (m: string) => void;
}) {
  try {
    const res = await previewStreamReq(payload.appId, { query: payload.query });
    if (!res.ok || !res.body) {
      payload.onError(`预览失败：${res.status}`);
      return;
    }
    for await (const { event, data } of readSseEvents(res)) {
      if (event !== 'result') continue;
      try {
        const r = JSON.parse(data);
        if (r.text) payload.onChunk(String(r.text));
      } catch {
        // ignore malformed frame
      }
    }
    payload.onDone();
  } catch (e: any) {
    payload.onError(String(e?.message ?? e));
  }
}

function openEdit(a: AgentEntity) {
  editingId.value = a.id;
  form.name = a.name;
  form.description = a.description ?? '';
  form.icon = a.icon ?? '';
  form.iconBackground = a.iconBackground ?? '';
  form.appCode = a.appCode ?? '';
  form.visibility = a.visibility ?? 'PRIVATE';
  showCreate.value = true;
}

/**
 * Save the trimmed basic-info form. Carries over every other field from the
 * existing entity so the PUT doesn't clobber model / strategy / tools /
 * instructions / memory / published state — those live in the design drawer.
 */
async function submitCreate() {
  if (!form.name) {
    message.warning('请填写名称');
    return;
  }
  if (!editingId.value) return;
  const existing = agents.value.find((x) => x.id === editingId.value);
  if (!existing) {
    message.error('未找到应用');
    return;
  }
  submitting.value = true;
  try {
    const body: CreateAgentRequestFull = {
      name: form.name,
      description: form.description || undefined,
      icon: form.icon || undefined,
      iconBackground: form.iconBackground || undefined,
      appCode: form.appCode || undefined,
      visibility: form.visibility,
      mode: existing.mode,
      instructions: existing.instructions,
      openingStatement: existing.openingStatement,
      modelName: existing.modelName,
      modelProvider: existing.modelProvider,
      runtimeType: existing.runtimeType,
      runtimeRef: existing.runtimeRef,
      strategy: (existing.strategy as AgentStrategy) ?? 'REACT',
      toolNames: existing.toolNamesJson
        ? safeParseArray(existing.toolNamesJson)
        : [],
      skillIds: existing.skillIdsJson
        ? safeParseArray(existing.skillIdsJson)
        : [],
      datasetIds: existing.datasetIdsJson
        ? safeParseArray(existing.datasetIdsJson)
        : [],
      maxIterations: existing.maxIterations,
      memoryEnabled: existing.memoryEnabled,
      memoryWindow: existing.memoryWindow,
      published: existing.published,
    };
    await updateAgentReq(editingId.value, body);
    message.success('已更新');
    showCreate.value = false;
    editingId.value = null;
    await refresh();
  } finally {
    submitting.value = false;
  }
}

function safeParseArray(json?: string): string[] {
  if (!json) return [];
  try {
    const v = JSON.parse(json);
    return Array.isArray(v) ? v.map(String) : [];
  } catch {
    return [];
  }
}

function remove(a: AgentEntity) {
  Modal.confirm({
    maskClosable: false,
    title: '删除智能体',
    content: `确认删除「${a.name}」？相关对话历史仍会保留在数据库中。`,
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      await deleteAgentReq(a.id);
      message.success('已删除');
      await refresh();
    },
  });
}

// ---- share embed modal ----
const showShare = ref(false);
const shareAgent = ref<AgentEntity | null>(null);

function openShare(a: AgentEntity) {
  shareAgent.value = a;
  showShare.value = true;
}

const shareUrl = computed(() => {
  // SSR guard: only rendered inside the (closed-by-default) share modal, but
  // don't rely on antd Modal's lazy mount to keep `window` alive.
  if (!shareAgent.value || typeof window === 'undefined') return '';
  return `${window.location.origin}/embed/agent/${shareAgent.value.id}`;
});

const iframeSnippet = computed(() =>
  shareUrl.value
    ? `<iframe src="${shareUrl.value}" width="420" height="620" style="border:1px solid #eee;border-radius:8px" allow="microphone"></iframe>`
    : '',
);

async function copyText(txt: string) {
  try {
    await navigator.clipboard.writeText(txt);
    message.success('已复制');
  } catch {
    message.error('复制失败，请手动选中');
  }
}

onMounted(() => {
  syncHostTheme();
  themeObserver = new MutationObserver(syncHostTheme);
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  });

  // 两个都是 async fn,但不 await —— 显式 .catch 兜住 unhandledrejection,
  // 避免任何一次网络失败把 <KeepAlive> 缓存里的组件挂坏。
  refresh().catch(() => {});
  loadDeps().catch(() => {});
});

// 无论宿主是停用 KeepAlive 缓存还是直接卸载页面，都先关闭所有 Teleport
// 弹层。只关闭 drawerOpen 不够：创建、分享和类型选择弹窗同样会挂到 body，
// 任意一个残留都会用全屏遮罩盖住下一条路由。
function closeTransientUi() {
  drawerOpen.value = false;
  showCreate.value = false;
  showShare.value = false;
  showTypePicker.value = false;
  // The card ⋯ menu (ui Popover) also teleports to body — close it too so a
  // floating menu never survives a route change / KeepAlive deactivation.
  openMenuFor.value = null;
}

function disconnectThemeObserver() {
  themeObserver?.disconnect();
  themeObserver = null;
}

onDeactivated(closeTransientUi);
onBeforeUnmount(() => {
  disconnectThemeObserver();
  closeTransientUi();
});
</script>

<template>
  <div class="agent-apps-page">
    <!-- 卡片式工具栏：左侧图标徽章 + 标题 + 副标题，右侧搜索 / 过滤控件 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="3" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="3" width="7" height="7" rx="1.5" />
            <rect x="3" y="14" width="7" height="7" rx="1.5" />
            <rect x="14" y="14" width="7" height="7" rx="1.5" />
          </svg>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">应用</div>
          <div class="as-page-subtitle">
            对话 · 智能体 · 工作流 · 文本生成
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <div class="agent-apps-search">
          <Input
            v-model:value="keyword"
            placeholder="搜索..."
            allow-clear
          >
            <template #prefix>
              <SearchOutlined style="color: #9ca3af" />
            </template>
          </Input>
        </div>
        <Select
          v-model:value="filterMode"
          :options="[
            { label: '全部类型', value: 'ALL' },
            { label: '对话', value: 'chat' },
            { label: '智能体', value: 'agent' },
            { label: '工作流', value: 'workflow' },
            { label: 'Chatflow', value: 'chatflow' },
            { label: '文本生成', value: 'completion' },
          ]"
          style="width: 120px; flex: 0 0 auto"
        />
        <div class="aa-pub-tabs">
          <button
            class="aa-pub-tab"
            :class="{ 'aa-pub-tab--on': filterPublished === 'all' }"
            @click="filterPublished = 'all'"
          >
            全部 <span class="aa-pub-count">{{ agents.length }}</span>
          </button>
          <button
            class="aa-pub-tab"
            :class="{ 'aa-pub-tab--on': filterPublished === 'published' }"
            @click="filterPublished = 'published'"
          >
            已发布
            <span class="aa-pub-count">{{ publishedCount }}</span>
          </button>
          <button
            class="aa-pub-tab"
            :class="{ 'aa-pub-tab--on': filterPublished === 'draft' }"
            @click="filterPublished = 'draft'"
          >
            草稿 <span class="aa-pub-count">{{ draftCount }}</span>
          </button>
        </div>
        <span class="agent-apps-count">
          共 {{ filtered.length }} / {{ agents.length }} 个
        </span>
      </div>
    </div>

    <div v-if="loading && agents.length === 0" class="aa-grid">
      <div v-for="n in 6" :key="n" class="aa-card">
        <Skeleton :active="true" :paragraph="{ rows: 3 }" />
      </div>
    </div>

    <Spin :spinning="loading && agents.length > 0">
      <div v-if="!loading || agents.length > 0" class="aa-grid">
        <!-- "新建" 占位卡 —— 点开走 Dify 风格的 CreateAppModal 选类型。
             Never disabled: apps can be created without a model configured. -->
        <Card
          variant="management"
          class="as-create-card"
          interactive
          @click="openTypePicker"
        >
          <div class="as-create-card__inner">
            <div class="as-create-card__plus">+</div>
            <div class="as-create-card__title">新建应用</div>
            <div class="as-create-card__subtitle">对话 / Agent / 工作流 / Chatflow / 文本生成</div>
          </div>
        </Card>

        <!-- 智能体卡 -->
        <Card variant="management"
          v-for="a in filtered"
          :key="a.id"
          class="aa-card"
          :title="a.name"
          :description="a.description || a.instructions || '暂无描述'"
          @click="openDesigner(a)"
        >
          <template #icon>
            <div class="aa-icon" :style="{ background: bgOf(a) }">
              {{ iconOf(a) }}
            </div>
          </template>

          <template #title>
            <span class="aa-title" :title="a.name">{{ a.name }}</span>
            <Tag
              :color="a.published === true ? 'green' : 'orange'"
              size="small"
              style="margin-left: 4px; font-size: 10px"
            >
              {{ a.published === true ? '已发布' : '草稿' }}
            </Tag>
          </template>

          <template #subtitle>
            <Tag :color="modeColor(a.mode)" style="margin-right: 4px">
              {{ modeLabel(a.mode) }}
            </Tag>
            <Tag
              :color="strategyColor(a.strategy)"
              style="margin-right: 4px"
            >
              {{ strategyLabel(a.strategy) }}
            </Tag>
            <span>· {{ fromNow(a.updatedAt) || '刚刚' }}</span>
          </template>

          <template #meta>
            <span class="aa-footer-item" :title="modelLabel(a)">
              🧠 {{ modelLabel(a) }}
            </span>
            <span
              v-if="a.maxIterations"
              class="aa-footer-item"
              :title="`最多 ${a.maxIterations} 轮`"
            >
              🔁 {{ a.maxIterations }}
            </span>
          </template>

          <!-- ⋯ menu — unified ui Popover bubble card in the card's #badge
               (top-right). Controlled by the shared `openMenuFor` id; each item
               closes the menu itself because Popover panels don't auto-close. -->
          <template #badge>
            <Popover
              :open="openMenuFor === a.id"
              trigger="click"
              placement="bottomRight"
              overlay-class-name="aa-card-menu-overlay"
              @update:open="
                (v: boolean) => (v ? (openMenuFor = a.id) : closeCardMenu())
              "
            >
              <button
                type="button"
                class="aa-card-menu-btn"
                :class="{ 'aa-card-menu-btn--open': openMenuFor === a.id }"
                title="更多操作"
                :aria-label="`${a.name} 更多操作`"
                :aria-expanded="openMenuFor === a.id"
                @click.stop="toggleCardMenu(a.id)"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <circle cx="5" cy="12" r="1.6" />
                  <circle cx="12" cy="12" r="1.6" />
                  <circle cx="19" cy="12" r="1.6" />
                </svg>
              </button>
              <template #content>
                <div class="aa-card-menu-content" role="menu">
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item"
                    @click="openDesigner(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z" />
                      <path d="M18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9L18 15z" />
                    </svg>
                    <span>设计应用</span>
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item"
                    @click="openChat(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                    </svg>
                    <span>独立对话</span>
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item"
                    @click="openEdit(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
                    </svg>
                    <span>编辑基本信息</span>
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item"
                    @click="openPermissions(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                    </svg>
                    <span>数据权限</span>
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item"
                    @click="openShare(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <circle cx="18" cy="5" r="3" />
                      <circle cx="6" cy="12" r="3" />
                      <circle cx="18" cy="19" r="3" />
                      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
                    </svg>
                    <span>分享嵌入</span>
                  </button>
                  <div class="aa-card-menu-sep" role="separator" />
                  <button
                    type="button"
                    role="menuitem"
                    class="aa-card-menu-item aa-card-menu-item--danger"
                    @click="remove(a); closeCardMenu()"
                  >
                    <svg
                      class="aa-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M3 6h18" />
                      <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                      <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
                      <path d="M10 11v6" />
                      <path d="M14 11v6" />
                    </svg>
                    <span>删除</span>
                  </button>
                </div>
              </template>
            </Popover>
          </template>
        </Card>
      </div>

      <Empty
        v-if="!loading && filtered.length === 0"
        class="mt-8"
        :description="
          keyword
            ? '没有找到匹配的智能体'
            : '还没有智能体，点上面的“新建智能体”开始吧'
        "
      />
    </Spin>

    <Modal
      v-model:open="showCreate"
      :mask-closable="false"
      title="编辑基本信息"
      width="520px"
      :confirm-loading="submitting"
      ok-text="保存"
      @ok="submitCreate"
    >
      <Form :model="form" layout="vertical">
        <FormItem label="图标">
          <div class="flex flex-wrap gap-1">
            <button
              v-for="ic in ICONS"
              :key="ic"
              type="button"
              class="aa-emoji-btn"
              :class="{ 'aa-emoji-btn--on': form.icon === ic }"
              :style="{ background: form.iconBackground || '#F3F4F6' }"
              @click="form.icon = ic"
            >
              {{ ic }}
            </button>
          </div>
        </FormItem>
        <FormItem label="背景颜色">
          <div class="flex flex-wrap gap-1">
            <button
              v-for="bg in BGS"
              :key="bg"
              type="button"
              class="aa-bg-btn"
              :class="{ 'aa-bg-btn--on': form.iconBackground === bg }"
              :style="{ background: bg }"
              @click="form.iconBackground = bg"
            />
          </div>
        </FormItem>
        <FormItem label="名称" required>
          <Input v-model:value="form.name" placeholder="例如 report-writer" />
        </FormItem>
        <FormItem label="描述">
          <Textarea
            v-model:value="form.description"
            :rows="3"
            placeholder="一句话说明这个智能体在做什么 —— 显示在卡片上"
          />
        </FormItem>
        <FormItem label="内部应用编码">
          <Input
            v-model:value="form.appCode"
            placeholder="例如 campus-admin-assistant"
          />
        </FormItem>
        <FormItem label="租户可见范围">
          <Select
            v-model:value="form.visibility"
            :options="[
              { label: '仅当前租户', value: 'PRIVATE' },
              { label: '所有租户（根租户共享）', value: 'GLOBAL' },
              { label: '指定租户（预留）', value: 'TENANT_LIST' },
            ]"
          />
        </FormItem>
      </Form>
    </Modal>

    <Modal
      v-model:open="showShare"
      :mask-closable="false"
      :title="shareAgent ? `分享 · ${shareAgent.name}` : '分享'"
      width="640px"
      :footer="null"
    >
      <div class="mb-2 text-xs text-gray-500">
        无登录嵌入。iframe 到你的站点即可用，会话会自动保存到浏览器
        localStorage。
      </div>
      <div class="mb-1 text-xs font-medium">直链</div>
      <div class="mb-3 flex gap-2">
        <Input :value="shareUrl" readonly class="flex-1" />
        <Button @click="copyText(shareUrl)">复制</Button>
      </div>
      <div class="mb-1 text-xs font-medium">iframe 嵌入代码</div>
      <div class="flex gap-2">
        <Textarea
          :value="iframeSnippet"
          :rows="4"
          readonly
          class="flex-1"
          style="font-family: monospace; font-size: 12px"
        />
        <Button @click="copyText(iframeSnippet)">复制</Button>
      </div>
      <div class="mt-3 text-xs text-gray-400">
        提示：URL 支持 <code>?title=xxx</code> 覆盖标题、
        <code>?conversation=xxx</code> 恢复到指定会话。
      </div>
    </Modal>

    <!-- Dify-style app-type picker. Single-page: type tiles + form on the
         left, big preview on the right. Chatflow is a first-class primary
         tile alongside workflow — same DAG canvas, but the runtime keeps
         per-conversation memory + streams via ANSWER nodes. -->
    <AppPermissionsModal v-if="permissionsApp" v-model:open="permissionsOpen" presentation="drawer"
      :tenant-id="permissionsApp.tenantId"
      :app-id="permissionsApp.id" :app-name="permissionsApp.name" :api="client().agents" @saved="refresh" />
    <CreateAppModal
      v-model:open="showTypePicker"
      :allow="['chatbot', 'agent', 'workflow', 'chatflow', 'text-generator']"
      @create="onCreateApp"
    />

    <!-- Click-a-card design drawer. Drawer's built-in DrawerFlowDesigner /
         LogAnnotationPanel / MonitorPanel handle everything internally now —
         we just wire them via :api to the local fetch bag. -->
    <AppDesignDrawer
      v-model:open="drawerOpen"
      :app="drawerApp"
      :initial-graph-json="drawerGraphJson"
      :workflow-history="drawerWorkflowHistory"
      :api="studioApi"
      :api-base="apiBase"
      :chat-config="drawerChatConfig"
      :runtime-types="runtimeTypes"
      :models="
        llmModels.map((m) => ({
          id: m.id,
          label: `${m.providerName} · ${m.modelName}`,
          provider: m.providerName,
        }))
      "
      :tools="tools.map((t) => ({ ...t, category: t.category || (t.name.startsWith('connector__') ? 'Connector' : '插件'), label: t.label || (t.name.startsWith('connector__') ? t.name.split('__').slice(2).join(' / ') : t.name) }))"
      :knowledge-bases="datasets.map((d) => ({ id: d.id, name: d.name }))"
      :permissions-api="client().agents"
      @permissions-saved="refresh"
      @save="onDrawerSave"
      @preview="onDrawerPreview"
      @publish="onDrawerPublish"
      @restore="onDrawerRestore"
      @disable-version="onDrawerDisableVersion"
      @edit-info="onDrawerEditInfo"
      @export-dsl="onDrawerExportDsl"
      @duplicate="onDrawerDuplicate"
    />
  </div>
</template>

<style scoped>
.agent-apps-page {
  box-sizing: border-box;
  padding: 14px 18px;
}
@media (max-width: 640px) {
  .agent-apps-page {
    padding: 10px 12px;
  }
}
/* Note: Page header now uses unified .as-page-header styles from ui/style.css */

.agent-apps-count {
  margin-left: 4px;
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}
:global(.dark) .agent-apps-count {
  color: #9ca3af;
}
/* 搜索框固定宽度(不再 flex 充满),让整行控件紧凑右对齐 */
.agent-apps-search {
  flex: 0 0 auto;
  width: 160px;
}
.agent-apps-search :deep(.as-input-wrap) {
  width: 100%;
}
/* 隐藏搜索框的焦点高亮(边框 + box-shadow),让它在工具栏里更像"裸文本字段" */
.agent-apps-search :deep(.as-input-wrap:hover) {
  border-color: var(--as-border, #e5e7eb);
}
.agent-apps-search :deep(.as-input-wrap:focus-within) {
  border-color: var(--as-border, #e5e7eb);
  box-shadow: none;
}

.aa-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.aa-card {
  position: relative;
  cursor: pointer;
  /* The ⋯ menu is a ui Popover whose panel teleports to <body>, so it
     neither needs room inside the card nor can be clipped by it. */
  overflow: visible;
}
/* Card's footer (#meta) must sit at the bottom of the fixed-height card,
   taking the role of the old .aa-footer. */
.aa-card:deep(.as-management-card__footer) {
  font-size: 11px;
  overflow: hidden;
  white-space: nowrap;
}
.aa-card:hover {
  z-index: 20;
}
.aa-header {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.aa-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  font-size: 19px;
}
.aa-title-wrap {
  min-width: 0;
  flex: 1;
}
.aa-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:global(.dark) .aa-title {
  color: #f3f4f6;
}
.aa-meta {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  white-space: nowrap;
}
.aa-meta :deep(.as-tag) {
  flex-shrink: 0;
  white-space: nowrap;
}
.aa-desc {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
:global(.dark) .aa-desc {
  color: #9ca3af;
}
.aa-spacer {
  flex: 1;
}
.aa-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: #6b7280;
  border-top: 1px solid #f3f4f6;
  padding-top: 6px;
  overflow: hidden;
  white-space: nowrap;
}
:global(.dark) .aa-footer {
  border-top-color: #2d2d2d;
}
.aa-footer-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
/* Card ⋯ menu — unified ui Popover bubble card, ported from the
   model-provider-hub ProviderHubShell card menu (.phs-card-menu-*) so both
   pages share one look. The overlay panel is rendered by AsPopover and
   teleports to <body> without this component's scope attribute, so it is
   styled through :global(.aa-card-menu-overlay); the #content elements are
   compiled here and carry the data-v attribute even when teleported, so the
   scoped rules below reach them. Dark mode is handled by the shared
   .as-popover__panel chrome plus the global tokens in the local hub. */
.aa-card-menu-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 8px;
  line-height: 1;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.aa-card-menu-btn:hover {
  background: var(--as-mgmt-soft, #f3f4f6);
  color: var(--as-mgmt-text, #374151);
}
.aa-card-menu-btn--open {
  background: color-mix(in srgb, var(--as-primary, #6366f1) 10%, transparent);
  color: var(--as-primary, #6366f1);
}
.aa-card-menu-btn:focus-visible {
  outline: 2px solid var(--as-primary, #6366f1);
  outline-offset: 1px;
}
:global(.aa-card-menu-overlay) {
  width: max-content;
  min-width: 0;
  max-width: calc(100vw - 16px);
  padding: 6px;
}
.aa-card-menu-content {
  /* 面板经 Teleport 挂在 body 下，继承不到 .as-management 容器上的
     --as-mgmt-* 令牌；这里用可随暗色翻转的全局令牌自建局部变量中枢。 */
  --aa-menu-text: var(--as-text, #374151);
  --aa-menu-muted: var(--as-text-secondary, #98a2b3);
  --aa-menu-line: var(--as-border-secondary, #e5e7eb);
  min-width: 160px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
}
.aa-card-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 5px 10px;
  font-size: 13px;
  line-height: 20px;
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  color: var(--aa-menu-text);
  transition: background 0.15s ease, color 0.15s ease;
}
.aa-card-menu-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--aa-menu-muted);
  transition: color 0.15s ease;
}
.aa-card-menu-item:hover {
  background: color-mix(in srgb, var(--as-primary, #6366f1) 8%, transparent);
  color: var(--as-primary, #6366f1);
}
.aa-card-menu-item:hover .aa-card-menu-icon {
  color: var(--as-primary, #6366f1);
}
.aa-card-menu-item:active {
  background: color-mix(in srgb, var(--as-primary, #6366f1) 14%, transparent);
}
.aa-card-menu-item:focus-visible {
  outline: 2px solid var(--as-primary, #6366f1);
  outline-offset: -2px;
}
.aa-card-menu-item--danger,
.aa-card-menu-item--danger .aa-card-menu-icon {
  color: var(--as-danger, #dc2626);
}
.aa-card-menu-item--danger:hover {
  background: color-mix(in srgb, var(--as-danger, #dc2626) 8%, transparent);
  color: var(--as-danger, #dc2626);
}
.aa-card-menu-item--danger:hover .aa-card-menu-icon {
  color: var(--as-danger, #dc2626);
}
.aa-card-menu-item--danger:active {
  background: color-mix(in srgb, var(--as-danger, #dc2626) 14%, transparent);
}
.aa-card-menu-item--danger:focus-visible {
  outline-color: var(--as-danger, #dc2626);
}
.aa-card-menu-sep {
  height: 1px;
  background: var(--aa-menu-line);
  margin: 4px 6px;
}

/* Emoji picker for the agent icon */
.aa-emoji-btn {
  width: 32px;
  height: 32px;
  font-size: 18px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    border-color 0.15s,
    transform 0.1s;
}
.aa-emoji-btn:hover {
  border-color: #a5b4fc;
  transform: scale(1.05);
}
.aa-emoji-btn--on {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15);
}

/* Published filter tabs */
.aa-pub-tabs {
  display: inline-flex;
  gap: 1px;
  padding: 2px;
  background: #f3f4f6;
  border-radius: 6px;
  flex-wrap: nowrap;
  flex-shrink: 0;
}
.aa-pub-tab {
  background: transparent;
  border: none;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: 1.2;
  white-space: nowrap;
  flex-shrink: 0;
}
.aa-pub-tab:hover {
  color: #111827;
}
.aa-pub-tab--on {
  background: #fff;
  color: #111827;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.aa-pub-count {
  padding: 0 4px;
  font-size: 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  min-width: 16px;
  text-align: center;
}
.aa-pub-tab--on .aa-pub-count {
  background: #eef2ff;
  color: #4338ca;
}

/* Background color picker */
.aa-bg-btn {
  width: 28px;
  height: 28px;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.aa-bg-btn:hover {
  border-color: #9ca3af;
}
.aa-bg-btn--on {
  border-color: #6366f1;
}
</style>
