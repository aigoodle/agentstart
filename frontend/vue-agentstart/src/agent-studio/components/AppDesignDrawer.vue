<script setup lang="ts">
/**
 * AppDesignDrawer — Dify workspace-style full-screen design drawer.
 *
 * Layout parity with dify workspace / spring-agent-start's workspace/design:
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │  🤖 app-name                                       [ 保存 ][关闭×] │
 *   ├───────────────────┬──────────────────────────────────────────────────┤
 *   │ ┌─────────────┐   │                                                  │
 *   │ │ 🤖 App icon │   │   编排 pane                                       │
 *   │ │ app-name    │   │   ┌───────────────────┬──────────────────────┐   │
 *   │ │ description │   │   │  提示词           │  调试与预览           │   │
 *   │ │ 2024-01-15  │   │   │  变量 +添加       │  ─────                │   │
 *   │ │ v1.2 · 运行 │   │   │  知识库 +添加     │  聊天消息...           │   │
 *   │ └─────────────┘   │   │  工具 +添加       │  ─────                │   │
 *   │                   │   │  视觉 ⚪⚫         │  [ 和 Bot 聊天 ]      │   │
 *   │ ⚙ 编排            │   │                   │                      │   │
 *   │ 🔌 访问 API        │   └───────────────────┴──────────────────────┘   │
 *   │ 📋 日志与标注      │                                                  │
 *   │ 📈 监测           │   * For workflow / chatflow modes, the pane is    │
 *   │                   │     replaced by a full-width slot=designer.       │
 *   │  «« 折叠          │                                                  │
 *   └───────────────────┴──────────────────────────────────────────────────┘
 *
 * The drawer is UI-only — persistence + preview streaming go through the host
 * via the emitted `save` and `preview` events so the parent can call whatever
 * backend API it wants.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  ApiOutlined,
  AppstoreOutlined,
  BugOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  CompassOutlined,
  FileTextOutlined,
  LineChartOutlined,
  PlayCircleOutlined,
  RocketOutlined,
  SaveOutlined,
  ShopOutlined,
} from '@ant-design/icons-vue';

import ChatIframePanel from '../../agent-flow/components/ChatIframePanel.vue';
import ChatflowExecutionTrace from '../../agent-flow/components/ChatflowExecutionTrace.vue';
import WorkflowDebugPanel from '../../agent-flow/components/WorkflowDebugPanel.vue';
import type {
  ChatDebugVariable,
  ChatIframeConfig,
} from '../../agent-flow/components/chat-iframe-types';
import DatasetPickerModal from '../../knowledge-hub/components/DatasetPickerModal.vue';
import { useKnowledge } from '../../knowledge-hub/composables/useKnowledge';
import type { DatasetSummary } from '../../knowledge-hub/types';
import ModelPickerPopover from '../../provider-hub/components/ModelPickerPopover.vue';
import SkillSelect from '../../skill-hub/SkillSelect.vue';
import type { SelectedModel } from '../../provider-hub/types';
import { Drawer } from '../../ui';

import type {
  AgentEntity,
  AgentVariable,
  StudioChatMessage,
  StudioKnowledge,
  StudioModelOption,
  StudioTool,
} from '../types';
import type { AppStudioApi } from '../api';
import type { AppMode } from '../adapters/types';
import DrawerFlowDesigner from '../panels/DrawerFlowDesigner.vue';
import LogAnnotationPanel from '../panels/LogAnnotationPanel.vue';
import MonitorPanel from '../panels/MonitorPanel.vue';
import AgentApiDocs, { type ApiEndpoint } from './AgentApiDocs.vue';
import VariableEditorModal from './VariableEditorModal.vue';

interface Props {
  open: boolean;
  /** The app row being edited — null while the drawer is closed. */
  app: AgentEntity | null;
  /**
   * Raw workflow-graph JSON for flow-mode apps. Provided by the host after it
   * has fetched the draft (Dify parity — the app row itself no longer carries
   * the graph). Leave empty for non-flow modes; the drawer only threads it
   * into the `#designer` slot.
   */
  initialGraphJson?: string;
  workflowHistory?: Array<{
    id: string;
    version?: string;
    markedName?: string;
    markedComment?: string;
    createdAt?: string;
  }>;
  /**
   * @deprecated Legacy dropdown data source — left in for backward compat, but
   * the top-right picker now uses {@link ModelPickerPopover}, which pulls the
   * enabled-model catalog itself via provider-hub. Only meaningful if you have
   * an existing caller passing a hand-curated model list.
   */
  models?: StudioModelOption[];
  tools?: StudioTool[];
  knowledgeBases?: StudioKnowledge[];
  /** Passed to the provider-hub picker for multi-tenant deployments. */
  tenantId?: string;
  /**
   * Callback bag for the drawer's built-in panels (日志/标注/监测). Every
   * method is optional — a panel whose backing call is missing degrades to
   * an empty-state placeholder rather than crashing. Consumers who want full
   * custom panels can still override via the {@code #logs} / {@code #monitor}
   * / {@code #designer} slots, in which case {@code api} is ignored.
   */
  api?: AppStudioApi;
  /**
   * 聊天调试 iframe 配置。
   *   - 非 flow 模式（agent / chat / completion）：右侧「调试与预览」列直接
   *     渲染 iframe，AgentDebugPanel 兜底不再使用；
   *   - flow 模式（workflow / chatflow）：透传给 FlowDesigner 的
   *     「发起调试」按钮，点击后弹右侧抽屉。
   * 不传 → 保留原有的简单 previewMessages 面板。
   */
  chatConfig?: ChatIframeConfig | null;
  /**
   * 宿主的代理前缀，只用于「访问 API」tab 里生成 curl 示例的 base URL；
   * 组件内部真正发请求走的是 :api 里的回调（AgentAppsPage 会把它拼上
   * /agent-start 命名空间）。默认 `/api`，最终展示成
   * `${window.location.origin}/api/agent-start`。
   */
  apiBase?: string;
}

const props = withDefaults(defineProps<Props>(), {
  models: () => [],
  tools: () => [],
  knowledgeBases: () => [],
  workflowHistory: () => [],
  api: () => ({}),
  apiBase: '/api',
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (
    e: 'save',
    payload: {
      appId: string;
      mode: AppMode;
      graphJson?: string;
      name?: string;
      instructions?: string;
      openingStatement?: string;
      /** Plain vendor model name (e.g. {@code qwen3.6-plus}). */
      modelName?: string;
      /** Provider key that owns {@link modelName} (e.g. {@code qwen}). */
      modelProvider?: string;
      /** Structured selection (kept for callers that want the full record). */
      modelSelection?: SelectedModel;
      toolNames?: string[];
      skillIds?: string[];
      datasetIds?: string[];
      /** Serialised RetrievalConfig — top-k / method / rerank. */
      retrievalConfigJson?: string;
    },
  ): void;
  (
    e: 'preview',
    payload: {
      appId: string;
      query: string;
      onChunk: (text: string) => void;
      onDone: () => void;
      onError: (msg: string) => void;
    },
  ): void;
  /**
   * Host receives the full draft and must await its save before publishing.
   */
  (
    e: 'publish',
    payload: NonNullable<ReturnType<typeof collectSavePayload>>,
  ): void;
  /** Restore one selected immutable snapshot into the mutable draft. */
  (e: 'restore', payload: { appId: string; snapshotId: string }): void;
  /** Open the app's runtime page in a new tab (Dify parity: "运行"). */
  (e: 'run', payload: { appId: string }): void;
  /** Show the embed-snippet dialog. */
  (e: 'embed', payload: { appId: string }): void;
  /** Open the app inside the Explore marketplace. */
  (e: 'openInExplore', payload: { appId: string }): void;
  /** Publish the app to the tenant's marketplace / plaza. */
  (e: 'publishToMarket', payload: { appId: string }): void;
  /**
   * Open the "编辑基本信息" surface — fired from the top-left brand popover.
   * The drawer only exposes the trigger; the host owns the actual edit modal.
   */
  (e: 'editInfo', payload: { appId: string }): void;
  /** Duplicate the app row — brand popover shortcut. */
  (e: 'duplicate', payload: { appId: string }): void;
  /** Export the app as a Dify-compatible DSL YAML. */
  (e: 'exportDsl', payload: { appId: string }): void;
}>();

// ---------------------------------------------------------------------------
// Nav state — Dify's 4 canonical sections for an app's design page.
// ---------------------------------------------------------------------------
type NavKey = 'api' | 'logs' | 'monitor' | 'orchestrate';
const nav = ref<NavKey>('orchestrate');
const collapsed = ref(false);

const isFlowMode = computed(
  () => props.app?.mode === 'workflow' || props.app?.mode === 'chatflow',
);
const isWorkflowMode = computed(() => props.app?.mode === 'workflow');
const modeLabel = computed(() => {
  const m = props.app?.mode ?? 'agent';
  if (m === 'chat') return 'CHAT';
  if (m === 'agent') return 'AGENT';
  if (m === 'workflow') return 'WORKFLOW';
  if (m === 'chatflow') return 'CHATFLOW';
  if (m === 'completion') return 'COMPLETION';
  return String(m).toUpperCase();
});

// ---------------------------------------------------------------------------
// Editable form (staged locally, only committed on 保存).
// ---------------------------------------------------------------------------
interface EditableForm {
  name: string;
  instructions: string;
  openingStatement: string;
  /** Plain vendor model name (e.g. {@code qwen3.6-plus}). */
  modelName: string;
  /** Provider key that owns {@link modelName} (e.g. {@code qwen}). */
  modelProvider: string;
  modelSelection: SelectedModel;
  toolNames: string[];
  skillIds: string[];
  datasetIds: string[];
  /**
   * Pass-through storage for the app-level RetrievalConfig JSON. The drawer
   * doesn't edit this itself yet (召回设置 opens a placeholder), but we still
   * round-trip it on save so a value written elsewhere isn't lost.
   */
  retrievalConfigJson: string;
  graphJson: string;
  vision: boolean;
  metadataFilter: boolean;
}
const form = ref<EditableForm>(makeEmptyForm());

function makeEmptyForm(): EditableForm {
  return {
    name: '',
    instructions: '',
    openingStatement: '',
    modelName: '',
    modelProvider: '',
    modelSelection: {},
    toolNames: [],
    skillIds: [],
    datasetIds: [],
    retrievalConfigJson: '',
    graphJson: '',
    vision: false,
    metadataFilter: false,
  };
}

function onPickModel(sel: SelectedModel) {
  form.value.modelSelection = sel;
  form.value.modelName = sel.modelName ?? '';
  form.value.modelProvider = sel.providerName ?? sel.provider ?? '';
}

/**
 * Rebuild the popover's structured selection from the two flat columns the
 * backend carries. LLM is the only type the app-level picker supports, so we
 * default {@code modelType} rather than round-tripping it.
 * <p>
 * Also folds the persisted {@code modelSettingsJson} — {temperature, topP,
 * maxTokens, thinkingMode, enable_thinking, …} — back onto
 * {@code completionParams} so the picker's parameter panel opens with the
 * values the user last saved, not the vendor defaults.
 */
function hydrateSelection(app: AgentEntity): SelectedModel {
  if (!app.modelName || !app.modelProvider) return {};
  const sel: SelectedModel = {
    providerName: app.modelProvider,
    modelName: app.modelName,
    modelType: 'LLM',
  };
  if (app.modelSettingsJson) {
    try {
      const parsed = JSON.parse(app.modelSettingsJson);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        sel.completionParams = parsed as Record<
          string,
          boolean | number | string
        >;
      }
    } catch {
      // Ignore malformed JSON — picker will fall back to defaults.
    }
  }
  return sel;
}

watch(
  () => [props.open, props.app, props.initialGraphJson] as const,
  ([open, app, graphJson]) => {
    if (!open || !app) return;
    form.value = {
      name: app.name ?? '',
      instructions: app.instructions ?? '',
      openingStatement: app.openingStatement ?? '',
      modelName: app.modelName ?? '',
      modelProvider: app.modelProvider ?? '',
      modelSelection: hydrateSelection(app),
      toolNames: safeParseArray(app.toolNamesJson),
      skillIds: safeParseArray(app.skillIdsJson),
      datasetIds: safeParseArray(app.datasetIdsJson),
      retrievalConfigJson: app.retrievalConfigJson ?? '',
      // Graph now flows via the `initialGraphJson` prop — the host fetches the
      // draft from /apps/{id}/workflow/draft and passes it in. AgentEntity no
      // longer carries the graph itself.
      graphJson: graphJson ?? '',
      vision: false,
      metadataFilter: false,
    };
    nav.value = 'orchestrate';
    previewMessages.value = [];
    previewInput.value = '';
  },
  { immediate: true },
);

function safeParseArray(json?: string): string[] {
  if (!json) return [];
  try {
    const v = JSON.parse(json);
    return Array.isArray(v) ? v.map(String) : [];
  } catch {
    return [];
  }
}

// ---------------------------------------------------------------------------
// Flow designer wiring — host slots in @agent-start/agent-flow's FlowDesigner
// via `#designer`, hands us a `registerDesigner(inst)` callback so we can
// call getFlowInfo() / reloadGraph() without a hard dep.
// ---------------------------------------------------------------------------
const designerRef = ref<null | {
  getFlowInfo: () => unknown;
  reloadGraph: (g: unknown) => void;
}>(null);

// ---------------------------------------------------------------------------
// Save handler.
//
// Two-phase Dify parity:
//   - "保存" (submitSave)  — persists the current canvas as the mutable draft.
//                            App metadata → /agents/{id}; graph → the draft.
//   - "发布" (submitPublish) — saves first, then asks the host to snapshot the
//                              draft as an immutable published workflow.
// ---------------------------------------------------------------------------
const saving = ref(false);
const publishing = ref(false);

function collectSavePayload() {
  if (!props.app) return null;
  let graphJson: string | undefined;
  if (isFlowMode.value && designerRef.value) {
    try {
      graphJson = JSON.stringify(designerRef.value.getFlowInfo() ?? {});
    } catch {
      graphJson = undefined;
    }
  }
  // Prefer the picker's selection (it also carries the parameter drawer's
  // completionParams — temperature / topP / maxTokens / thinkingMode / …).
  // Falls back to any pre-existing flat modelName/modelProvider values so a
  // save that never re-opened the picker still round-trips the previous model.
  const sel = form.value.modelSelection;
  const modelSettings =
    sel?.completionParams && Object.keys(sel.completionParams).length > 0
      ? sel.completionParams
      : undefined;
  return {
    appId: props.app.id,
    mode: props.app.mode ?? 'agent',
    graphJson,
    name: form.value.name,
    instructions: form.value.instructions,
    openingStatement: form.value.openingStatement,
    modelName: sel?.modelName || form.value.modelName || undefined,
    modelProvider:
      sel?.providerName || sel?.provider || form.value.modelProvider || undefined,
    modelSelection: sel?.providerName ? sel : undefined,
    // Flat blob the backend persists on app_model_configs.configs. Vendor
    // translation of thinkingMode into enable_thinking / think / thinking.type
    // happens server-side in AgentChatOptionsFactory.
    modelSettings,
    toolNames: form.value.toolNames,
    skillIds: form.value.skillIds,
    datasetIds: form.value.datasetIds,
    retrievalConfigJson: form.value.retrievalConfigJson || undefined,
  };
}

async function submitSave() {
  const payload = collectSavePayload();
  if (!payload) return;
  saving.value = true;
  try {
    emit('save', payload);
  } finally {
    saving.value = false;
  }
}

async function submitPublish() {
  const payload = collectSavePayload();
  if (!payload) return;
  publishing.value = true;
  try {
    // Persist the current draft first so the snapshot captures what the user
    // sees on screen. Host is responsible for awaiting the save before firing
    // the publish (see /agent/list.vue::onDrawerSave + onDrawerPublish).
    emit('publish', payload);
  } finally {
    publishing.value = false;
  }
}

function close() {
  emit('update:open', false);
}

// ---------------------------------------------------------------------------
// 知识库 (dataset) picker — Dify parity for the 编排 "知识库 + 添加" card.
//
// The shared DatasetPickerModal (from knowledge-hub) does the actual selection
// UI; here we keep a lazily-fetched catalog around so:
//   • the modal can skip its own fetch (we hand it :datasets), and
//   • the card can render the selected rows with names / rerank badges without
//     the caller having to preload {@link StudioKnowledge} objects.
// ---------------------------------------------------------------------------
const datasetPickerOpen = ref(false);
const datasetCatalog = ref<DatasetSummary[]>([]);
const datasetCatalogLoaded = ref(false);

async function loadDatasetCatalog() {
  if (datasetCatalogLoaded.value) return;
  try {
    const { listDatasets } = useKnowledge();
    datasetCatalog.value = await listDatasets(props.tenantId);
    datasetCatalogLoaded.value = true;
  } catch {
    // Swallow — the card still works with just ids, it just shows the raw id
    // as the row label when the catalog can't be loaded.
    datasetCatalog.value = [];
  }
}

function openDatasetPicker() {
  void loadDatasetCatalog();
  datasetPickerOpen.value = true;
}

function onDatasetsPicked(datasets: DatasetSummary[]) {
  // Merge any newly-picked rows into the catalog so the card can render them
  // immediately (in case the fetch returned a stale list).
  const seen = new Set(datasetCatalog.value.map((d) => d.id));
  for (const d of datasets) {
    if (!seen.has(d.id)) datasetCatalog.value.push(d);
  }
  form.value.datasetIds = datasets.map((d) => d.id);
}

function removeDataset(id: string) {
  form.value.datasetIds = form.value.datasetIds.filter((x) => x !== id);
}

/** Render helpers for the selected-list rows. */
const selectedDatasets = computed(() => {
  const byId = new Map<string, DatasetSummary>();
  for (const d of datasetCatalog.value) byId.set(d.id, d);
  // Also fold in the legacy `knowledgeBases` prop as a fallback lookup so
  // existing hosts that pre-load a slim {id,name} list still see names.
  const fallback = new Map((props.knowledgeBases ?? []).map((k) => [k.id, k]));
  return form.value.datasetIds.map((id) => {
    const full = byId.get(id);
    if (full) return full;
    const slim = fallback.get(id);
    return {
      id,
      name: slim?.name ?? id,
      documentCount: slim?.documentCount,
    } as DatasetSummary;
  });
});

function datasetTechniqueLabel(d: DatasetSummary): null | string {
  if (!d.indexingTechnique) return null;
  return d.indexingTechnique === 'ECONOMY' ? '经济' : '高质量';
}
function datasetMethodLabel(d: DatasetSummary): null | string {
  if (!d.retrievalConfigJson) return null;
  try {
    const cfg = JSON.parse(d.retrievalConfigJson) as { method?: string };
    switch (cfg.method) {
      case 'VECTOR':
        return '向量检索';
      case 'FULL_TEXT':
        return '全文检索';
      case 'HYBRID':
        return '混合检索';
      default:
        return null;
    }
  } catch {
    return null;
  }
}

// Preload the catalog the first time the drawer opens with an app that
// already has attached datasets — otherwise the rows would render as bare
// ids until the user clicks 添加.
watch(
  () => [props.open, props.app?.id] as const,
  ([open, appId]) => {
    if (!open || !appId) return;
    if (form.value.datasetIds.length > 0) void loadDatasetCatalog();
  },
  { immediate: true },
);

// ---------------------------------------------------------------------------
// 发布 dropdown menu — Dify workspace parity.
//
// Clicking 发布 opens a menu with:
//   ▸ 最新发布 header (last-published timestamp + 恢复 button)
//   ▸ 发布更新 primary action (Ctrl+Shift+P shortcut)
//   ▸ 运行 / 嵌入网站 / 在"探索"中打开 / 访问 API quick links
//   ▸ 发布到市场 (marketplace push)
//
// Each quick-link emits a dedicated event so the host wires it to whatever
// route / modal makes sense — the drawer stays UI-only.
// ---------------------------------------------------------------------------
const publishMenuOpen = ref(false);
const publishMenuRef = ref<HTMLElement | null>(null);

function togglePublishMenu() {
  publishMenuOpen.value = !publishMenuOpen.value;
}
function closePublishMenu() {
  publishMenuOpen.value = false;
}

// ---------------------------------------------------------------------------
// Top-left brand popover — click the caret next to the app name to reveal
// the Dify-style "basic info" panel (编辑信息 / 复制 / 导出 DSL / …更多,
// then Web App / 后端服务 API / MCP 服务 status blocks).
// ---------------------------------------------------------------------------
const brandMenuOpen = ref(false);
const brandMenuRef = ref<HTMLElement | null>(null);

function toggleBrandMenu() {
  brandMenuOpen.value = !brandMenuOpen.value;
}
function closeBrandMenu() {
  brandMenuOpen.value = false;
}

/** SSR-safe origin — empty string on the server. */
function safeOrigin(): string {
  return typeof window !== 'undefined' ? window.location.origin : '';
}

/** Public Web-App URL under the drawer's own origin — Dify parity. */
const webAppUrl = computed(() => {
  if (!props.app?.id || typeof window === 'undefined') return '';
  return `${window.location.origin}/embed/agent/${props.app.id}`;
});
/**
 * Backend REST base — apps run under {@code ${apiBase}/agent-start} on the same host
 * in dev (apiBase 默认 `/api`，最终成 `${origin}/api/agent-start`）。
 */
const apiBaseUrl = computed(() => {
  if (!props.app?.id || typeof window === 'undefined') return '';
  const base = props.apiBase.replace(/\/+$/, '');
  return `${window.location.origin}${base}/agent-start`;
});

/**
 * Endpoint samples for the 访问 API tab. Focused on the two entry points a
 * customer integrates against. Browser iframe calls use the business JWT;
 * external server-to-server calls use a per-app API key.
 */
const apiEndpoints = computed<ApiEndpoint[]>(() => {
  // SSR guard: apiBaseUrl is '' on the server, and reading window.location
  // would throw before the hardcoded fallback could apply.
  const base = apiBaseUrl.value || `${safeOrigin() || 'http://localhost:18090'}/api/agent-start`;
  return [
    {
      id: 'chat-completions',
      title: '发起对话（OpenAI 兼容）',
      method: 'POST',
      path: '/v1/chat/completions',
      description:
        '外部服务端调用入口。Authorization: Bearer <api-key> 必填，后端根据 key 解析所属应用；不要把长期 key 放进浏览器。',
      code: `curl -X POST '${base}/v1/chat/completions' \\
  -H 'Authorization: Bearer {API_KEY}' \\
  -H 'Content-Type: application/json' \\
  --data-raw '{
    "model": "app-runtime",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": true
  }'`,
    },
  ];
});

/** Web-App / API / MCP 三块的运行开关 —— 目前是 UI-only 状态展示。 */
const brandWebAppOn = ref(true);
const brandApiOn = ref(true);
const brandMcpOn = ref(false);

async function copyToClipboard(text: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // silently no-op — the drawer stays UI-only
  }
}

function onBrandEdit() {
  if (!props.app) return;
  emit('editInfo', { appId: props.app.id });
  closeBrandMenu();
}
function onBrandDuplicate() {
  if (!props.app) return;
  emit('duplicate', { appId: props.app.id });
  closeBrandMenu();
}
function onBrandExport() {
  if (!props.app) return;
  emit('exportDsl', { appId: props.app.id });
  closeBrandMenu();
}

const latestPublished = computed(() => props.workflowHistory[0] ?? null);
const publishedAgo = computed(() => relativeTime(latestPublished.value?.createdAt));
const historyOpen = ref(false);

function relativeTime(ts?: string | number | null): string {
  if (!ts) return '刚刚';
  const d = new Date(normalizeDateTime(ts));
  const t = d.getTime();
  if (Number.isNaN(t)) return String(ts);
  const diff = Date.now() - t;
  if (diff < 60_000) return '刚刚';
  const mins = Math.floor(diff / 60_000);
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(diff / 3_600_000);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(diff / 86_400_000);
  if (days < 30) return `${days} 天前`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} 个月前`;
  return `${Math.floor(months / 12)} 年前`;
}

/** Java LocalDateTime may contain nanoseconds, while browsers accept milliseconds reliably. */
function normalizeDateTime(ts: string | number): string | number {
  return typeof ts === 'string'
    ? ts.replace(/(\.\d{3})\d+$/, '$1')
    : ts;
}

function formatDateTime(ts?: string | number | null): string {
  if (!ts) return '--';
  const date = new Date(normalizeDateTime(ts));
  if (Number.isNaN(date.getTime())) return String(ts).replace('T', ' ').split('.')[0];
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

async function onPublishUpdate() {
  closePublishMenu();
  await submitPublish();
}
function onRestore() {
  if (!props.app) return;
  historyOpen.value = true;
  closePublishMenu();
}
function restoreSnapshot(snapshotId: string) {
  if (!props.app) return;
  emit('restore', { appId: props.app.id, snapshotId });
  historyOpen.value = false;
}
function onRun() {
  if (!props.app) return;
  emit('run', { appId: props.app.id });
  closePublishMenu();
}
function onEmbed() {
  if (!props.app) return;
  emit('embed', { appId: props.app.id });
  closePublishMenu();
}
function onOpenInExplore() {
  if (!props.app) return;
  emit('openInExplore', { appId: props.app.id });
  closePublishMenu();
}
function onAccessApi() {
  nav.value = 'api';
  closePublishMenu();
}
function onPublishToMarket() {
  if (!props.app) return;
  emit('publishToMarket', { appId: props.app.id });
  closePublishMenu();
}

function onDocClick(e: MouseEvent) {
  if (publishMenuOpen.value) {
    const wrap = publishMenuRef.value;
    if (wrap && !wrap.contains(e.target as Node)) closePublishMenu();
  }
  if (brandMenuOpen.value) {
    const wrap = brandMenuRef.value;
    if (wrap && !wrap.contains(e.target as Node)) closeBrandMenu();
  }
}
function onDocKeydown(e: KeyboardEvent) {
  if (!props.open) return;
  if (e.key === 'Escape' && publishMenuOpen.value) {
    closePublishMenu();
    return;
  }
  if (e.key === 'Escape' && brandMenuOpen.value) {
    closeBrandMenu();
    return;
  }
  // Ctrl+Shift+P → publish update, matches Dify's shortcut hint.
  const key = e.key.toLowerCase();
  if ((e.ctrlKey || e.metaKey) && e.shiftKey && key === 'p') {
    e.preventDefault();
    void submitPublish();
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onDocClick);
  document.addEventListener('keydown', onDocKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick);
  document.removeEventListener('keydown', onDocKeydown);
});

// ---------------------------------------------------------------------------
// Preview chat (chat / agent / completion modes only).
// ---------------------------------------------------------------------------
const previewMessages = ref<StudioChatMessage[]>([]);
const previewInput = ref('');
const previewSending = ref(false);

function sendPreview() {
  const q = previewInput.value.trim();
  if (!q || !props.app || previewSending.value) return;
  previewMessages.value.push({ role: 'user', content: q });
  previewMessages.value.push({ role: 'assistant', content: '' });
  const idx = previewMessages.value.length - 1;
  previewInput.value = '';
  previewSending.value = true;
  emit('preview', {
    appId: props.app.id,
    query: q,
    onChunk: (text) => {
      const m = previewMessages.value[idx];
      if (m) m.content = (m.content ?? '') + text;
    },
    onDone: () => {
      previewSending.value = false;
    },
    onError: (msg) => {
      const m = previewMessages.value[idx];
      if (m) {
        m.content = msg;
        m.failed = true;
      }
      previewSending.value = false;
    },
  });
}

// ---------------------------------------------------------------------------
// Variable list (a Dify-parity concept — parses out `{{#user.xxx#}}` variables
// used by the prompt into a form-editable list). Editing is delegated to
// VariableEditorModal to match the flow designer's StartNode UX — same shape,
// same interaction, no more inline single-line inputs.
// ---------------------------------------------------------------------------
const variables = ref<AgentVariable[]>([]);

const variableEditorRef = ref<InstanceType<typeof VariableEditorModal> | null>(
  null,
);

function openVariableEditor(item?: AgentVariable) {
  variableEditorRef.value?.open(item);
}

function onVariableSubmit(payload: AgentVariable) {
  const items = variables.value;
  const idx = items.findIndex((v) => v.id === payload.id);
  if (idx === -1) items.push(payload);
  else items[idx] = { ...payload };
}

function removeVariable(i: number) {
  variables.value.splice(i, 1);
}

/**
 * 把 AgentVariable 转成 ChatIframePanel 需要的 ChatDebugVariable。
 * 类型统一小写；defaultValue 直接透传，由 ChatIframePanel 内部按类型解析。
 */
const chatDebugVariables = computed<ChatDebugVariable[]>(() =>
  variables.value
    .filter((v) => !!v.name)
    .map((v) => ({
      name: v.name,
      label: v.label || v.name,
      type: (v.type || 'string').toLowerCase(),
      required: !!v.required,
      description: v.description,
      defaultValue: v.defaultValue,
    })),
);

// ---------------------------------------------------------------------------
// 「调试」按钮 & 浮层 — 挂在抽屉顶部保存/发布按钮之前。
// 非 flow 模式的调试聊天在右侧编排面板里常驻，不需要再弹浮层；flow 模式
// (workflow / chatflow) 画布占满整屏，才用这个按钮开右侧浮层。
// ---------------------------------------------------------------------------
const debugPanelOpen = ref(false);
const chatflowTraceExpanded = ref(false);
const chatflowTraceRef = ref<InstanceType<typeof ChatflowExecutionTrace> | null>(null);
/**
 * 每次打开按当前时间刷新，用作 ChatIframePanel 的 key，强制重挂 iframe：
 * 用户改完变量之后再点一次「调试」希望是"新一轮"，避免旧会话残留。
 */
const debugSessionSuffix = ref(0);

/** 从当前画布上的 START 节点抽出 variables，转成 ChatDebugVariable。 */
const flowDebugVariables = computed<ChatDebugVariable[]>(() => {
  if (!isFlowMode.value) return [];
  const info = designerRef.value?.getFlowInfo?.() as
    | { nodes?: Array<{ type?: string; data?: any }> }
    | undefined;
  const startNode = info?.nodes?.find(
    (n) => String(n?.type ?? '').toUpperCase() === 'START',
  );
  const raw = startNode?.data?.variables;
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((v: any) => v && v.name)
    .map((v: any) => ({
      name: String(v.name),
      label: v.label ? String(v.label) : String(v.name),
      type: v.type ? String(v.type).toLowerCase() : 'string',
      required: !!v.required,
      description: v.description ? String(v.description) : undefined,
      defaultValue: v.defaultValue ?? v.value,
    }));
});

const workflowDebugGraph = computed<Record<string, unknown>>(() =>
  (designerRef.value?.getFlowInfo?.() ?? {}) as Record<string, unknown>,
);

function openDebug() {
  if (!isWorkflowMode.value && !props.chatConfig?.src) return;
  debugSessionSuffix.value = Date.now();
  chatflowTraceExpanded.value = false;
  debugPanelOpen.value = true;
}
function closeDebug() {
  debugPanelOpen.value = false;
}

// ---------------------------------------------------------------------------
// 浮动 & 拖动 —— 面板浮在抽屉右上层，不加蒙版；用户可拖 header 换位。
// 位置写入 localStorage，多次打开维持上次位置；resize 视口时夹回可视区。
// ---------------------------------------------------------------------------
const DEBUG_POS_KEY = 'agent-start-debug-panel-pos';
/**
 * 普通工作流和 CHATFLOW 默认与 ChatIframePanel 同宽；仅展开执行过程时加倍。
 * 拖动夹取必须用同一个值计算，否则会按旧宽度把面板推出屏幕。
 */
function debugPanelW(): number {
  const fullWidth = Math.min(920, Math.round(window.innerWidth * 0.92));
  return props.app?.mode === 'chatflow' && chatflowTraceExpanded.value
    ? fullWidth
    : Math.min(460, fullWidth);
}
const DEBUG_PANEL_H_RATIO = 0.86; // 相对抽屉高度的比例
const DRAG_HANDLE_HEIGHT = 46; // 与 ChatIframePanel .cip-head 保持一致
const DRAG_MARGIN = 8;

const debugPos = ref<{ left: number; top: number } | null>(null);
const debugDragging = ref(false);
let debugDragStart:
  | { pointerX: number; pointerY: number; origLeft: number; origTop: number }
  | null = null;

function clampDebugPos(pos: { left: number; top: number }): {
  left: number;
  top: number;
} {
  const w = debugPanelW();
  const h = Math.max(320, Math.round(window.innerHeight * DEBUG_PANEL_H_RATIO));
  const maxLeft = Math.max(DRAG_MARGIN, window.innerWidth - w - DRAG_MARGIN);
  const maxTop = Math.max(DRAG_MARGIN, window.innerHeight - h - DRAG_MARGIN);
  return {
    left: Math.min(Math.max(DRAG_MARGIN, pos.left), maxLeft),
    top: Math.min(Math.max(DRAG_MARGIN, pos.top), maxTop),
  };
}

function loadDebugPos(): { left: number; top: number } | null {
  try {
    const raw = window.localStorage.getItem(DEBUG_POS_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (
      !parsed ||
      typeof parsed.left !== 'number' ||
      typeof parsed.top !== 'number'
    ) {
      return null;
    }
    return clampDebugPos(parsed);
  } catch {
    return null;
  }
}

function saveDebugPos(pos: { left: number; top: number }) {
  try {
    window.localStorage.setItem(DEBUG_POS_KEY, JSON.stringify(pos));
  } catch {
    /* localStorage 不可用则退化为仅本次会话生效 */
  }
}

/** 默认落在抽屉右上角（右缘留 24px、header 下 80px） */
function defaultDebugPos(): { left: number; top: number } {
  return clampDebugPos({
    left: window.innerWidth - debugPanelW() - 24,
    top: 80,
  });
}

function toggleChatflowTrace() {
  const oldWidth = debugPanelW();
  const oldLeft = debugPos.value?.left;
  chatflowTraceExpanded.value = !chatflowTraceExpanded.value;
  if (oldLeft !== undefined) {
    // 扩缩时固定右边缘，避免聊天窗在屏幕上跳动。
    debugPos.value = clampDebugPos({
      left: oldLeft + oldWidth - debugPanelW(),
      top: debugPos.value!.top,
    });
  }
}

function onChatflowEvent(payload: { name: string; data?: unknown }) {
  chatflowTraceRef.value?.receive(payload);
}

function onDebugPanelPointerDown(e: PointerEvent) {
  // 只从"顶部 handle 区域"起手，避开按钮 / iframe / 变量气泡卡里的输入
  const el = e.target as HTMLElement | null;
  if (!el) return;
  if (
    el.closest(
      'button, input, textarea, select, a, iframe, .cip-var-panel',
    )
  ) {
    return;
  }
  const panelEl = e.currentTarget as HTMLElement;
  const rect = panelEl.getBoundingClientRect();
  if (e.clientY - rect.top > DRAG_HANDLE_HEIGHT) return;
  // 首次拖动时把面板从 CSS 的 right 定位切成 left/top 定位
  if (!debugPos.value) {
    debugPos.value = clampDebugPos({ left: rect.left, top: rect.top });
  }
  debugDragStart = {
    pointerX: e.clientX,
    pointerY: e.clientY,
    origLeft: debugPos.value.left,
    origTop: debugPos.value.top,
  };
  debugDragging.value = true;
  panelEl.setPointerCapture?.(e.pointerId);
  e.preventDefault();
}

function onDebugPanelPointerMove(e: PointerEvent) {
  if (!debugDragging.value || !debugDragStart) return;
  debugPos.value = clampDebugPos({
    left: debugDragStart.origLeft + (e.clientX - debugDragStart.pointerX),
    top: debugDragStart.origTop + (e.clientY - debugDragStart.pointerY),
  });
}

function onDebugPanelPointerUp(e: PointerEvent) {
  if (!debugDragging.value) return;
  debugDragging.value = false;
  debugDragStart = null;
  (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
  if (debugPos.value) saveDebugPos(debugPos.value);
}

/** 打开时装填初始坐标（之前拖过 → 恢复；否则默认右上角） */
watch(
  () => debugPanelOpen.value,
  (open) => {
    if (open) {
      debugPos.value = loadDebugPos() ?? defaultDebugPos();
    }
  },
);

function onWindowResizeForDebugPos() {
  if (!debugPanelOpen.value || !debugPos.value) return;
  const clamped = clampDebugPos(debugPos.value);
  if (clamped.left !== debugPos.value.left || clamped.top !== debugPos.value.top) {
    debugPos.value = clamped;
    saveDebugPos(clamped);
  }
}

onMounted(() => {
  window.addEventListener('resize', onWindowResizeForDebugPos);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResizeForDebugPos);
});

function variableTypeLabel(type: AgentVariable['type']): string {
  switch (type) {
    case 'boolean':
      return 'Boolean';
    case 'number':
      return 'Number';
    case 'array':
      return 'Array';
    case 'object':
      return 'Object';
    case 'file':
      return 'File';
    default:
      return 'String';
  }
}
</script>

<template>
  <Drawer
    :open="open"
    class="dr-app-drawer"
    width="100vw"
    :closable="false"
    :mask-closable="false"
    :body-style="{ padding: '0', overflow: 'hidden' }"
    @update:open="emit('update:open', $event)"
  >
      <template #title>
          <div class="dr-brand">
            <div
              class="dr-icon-sm"
              :style="{ background: app?.iconBackground || '#EEF4FF' }"
            >
              {{ app?.icon || '🤖' }}
            </div>
            <div class="dr-title-inline">
              <span class="dr-title">{{ form.name || app?.name }}</span>
              <span class="dr-mode-badge">{{ modeLabel }}</span>
            </div>
          </div>
      </template>
      <template #extra>
          <div class="dr-header-right">
            <!-- Model picker (right-side of the header) — hidden for workflow
                 mode: the model is selected per-node inside the canvas (each
                 LLM/Agent node has its own model config), so a drawer-level
                 picker would be misleading. Chat / chatflow / agent modes still
                 show it as the app-wide default. -->
            <div v-if="!isFlowMode" class="dr-header-model">
              <ModelPickerPopover
                :model-value="form.modelSelection"
                model-type="LLM"
                :tenant-id="tenantId"
                placement="bottomRight"
                :width="380"
                placeholder="选择模型"
                @update:model-value="onPickModel"
              />
            </div>

            <div class="dr-actions">
            <!-- 「调试」— 只在 flow 模式露出（agent/chat 已经在右侧编排面板里
                 常驻 ChatIframePanel，不需要再叠层）。宿主没配 chatConfig 时
                 disabled + 提示 —— 避免用户点了没反应。 -->
            <button
              v-if="isFlowMode"
              class="dr-btn dr-btn-secondary"
              :class="{ 'dr-btn-active': debugPanelOpen }"
              :disabled="!isWorkflowMode && !chatConfig?.src"
              :title="isWorkflowMode ? '打开工作流调试' : (chatConfig?.src ? '打开聊天调试' : '未配置调试 iframe：宿主传入 :chat-config 即可启用')"
              @click="openDebug"
            >
              <BugOutlined class="dr-btn-icon dr-btn-icon-debug" />
              <span>调试</span>
            </button>
            <button
              class="dr-btn dr-btn-secondary"
              :disabled="saving || publishing"
              @click="submitSave"
            >
              <SaveOutlined class="dr-btn-icon" />
              <span>{{ saving ? '保存中…' : '保存' }}</span>
            </button>
            <div ref="publishMenuRef" class="dr-publish-wrap">
              <button
                class="dr-btn dr-btn-primary dr-publish-btn"
                :class="{ 'dr-publish-btn-open': publishMenuOpen }"
                :disabled="saving || publishing"
                @click="togglePublishMenu"
              >
                <RocketOutlined class="dr-btn-icon" />
                <span>{{ publishing ? '发布中…' : '发布' }}</span>
                <span class="dr-publish-caret" aria-hidden="true">▾</span>
              </button>

              <div v-if="publishMenuOpen" class="dr-publish-menu hud-panel">
                <div class="dr-publish-head">
                  <div class="dr-publish-head-title">最新发布</div>
                  <div class="dr-publish-head-row">
                    <span class="dr-publish-head-time">
                      {{ latestPublished ? `发布于 ${publishedAgo}` : '尚未发布' }}
                    </span>
                    <button class="dr-publish-restore" :disabled="!latestPublished" @click="onRestore">
                      恢复
                    </button>
                  </div>
                </div>

                <button
                  class="dr-publish-primary"
                  :disabled="saving || publishing"
                  @click="onPublishUpdate"
                >
                  <span class="dr-publish-primary-main">
                    <CloudUploadOutlined class="dr-publish-primary-icon" />
                    <span>{{ publishing ? '发布中…' : '发布更新' }}</span>
                  </span>
                  <span class="dr-publish-shortcut">
                    <kbd>Ctrl</kbd>
                    <kbd>⇧</kbd>
                    <kbd>P</kbd>
                  </span>
                </button>

                <div class="dr-publish-divider" />

                <button class="dr-publish-item" @click="onRun">
                  <PlayCircleOutlined class="dr-publish-item-icon" />
                  <span class="dr-publish-item-label">运行</span>
                  <span class="dr-publish-item-arrow">↗</span>
                </button>
                <button class="dr-publish-item" @click="onEmbed">
                  <CodeOutlined class="dr-publish-item-icon" />
                  <span class="dr-publish-item-label">嵌入网站</span>
                  <span class="dr-publish-item-arrow">↗</span>
                </button>
                <button class="dr-publish-item" @click="onOpenInExplore">
                  <CompassOutlined class="dr-publish-item-icon" />
                  <span class="dr-publish-item-label">在“探索”中打开</span>
                  <span class="dr-publish-item-arrow">↗</span>
                </button>
                <button class="dr-publish-item" @click="onAccessApi">
                  <ApiOutlined class="dr-publish-item-icon" />
                  <span class="dr-publish-item-label">访问 API</span>
                  <span class="dr-publish-item-arrow">↗</span>
                </button>

                <div class="dr-publish-divider" />

                <button class="dr-publish-item" @click="onPublishToMarket">
                  <ShopOutlined class="dr-publish-item-icon" />
                  <span class="dr-publish-item-label">发布到市场</span>
                  <span class="dr-publish-item-arrow">↗</span>
                </button>
              </div>
              <div v-if="historyOpen" class="dr-history-mask" @click.self="historyOpen = false">
                <section class="dr-history-panel">
                  <header class="dr-history-header">
                    <strong>发布历史</strong>
                    <button class="dr-history-close" @click="historyOpen = false">×</button>
                  </header>
                  <div v-if="workflowHistory.length === 0" class="dr-history-empty">暂无发布记录</div>
                  <div v-else class="dr-history-list">
                    <article v-for="item in workflowHistory" :key="item.id" class="dr-history-item">
                      <div class="dr-history-info">
                        <strong>{{ item.markedName || item.version || '发布版本' }}</strong>
                        <span>{{ formatDateTime(item.createdAt) }}（{{ relativeTime(item.createdAt) }}）</span>
                        <p v-if="item.markedComment">{{ item.markedComment }}</p>
                      </div>
                      <button class="dr-publish-restore" @click="restoreSnapshot(item.id)">恢复此版本</button>
                    </article>
                  </div>
                </section>
              </div>
            </div>
            <button class="dr-btn dr-btn-ghost" title="关闭" @click="close">
              <span class="dr-btn-icon dr-btn-icon-x" aria-hidden="true">✕</span>
              <span>关闭</span>
            </button>
            </div>
          </div>
      </template>

      <div class="dr-panel">

        <!-- ==== Two-column body: LEFT rail + MAIN content ==== -->
        <div class="dr-body">
          <!-- ── LEFT rail: app info card + 4-nav ── -->
          <aside class="dr-rail" :class="{ 'dr-rail-collapsed': collapsed }">
            <!-- App info card — click the ⋯ icon (top-right on hover) to open
                 the basic-info popover (Web App / API / MCP status). -->
            <div
              ref="brandMenuRef"
              class="dr-info-card"
              :class="{ 'dr-info-card-active': brandMenuOpen }"
            >
              <button
                v-show="!collapsed"
                type="button"
                class="dr-info-trigger"
                :class="{ 'dr-info-trigger-open': brandMenuOpen }"
                :title="brandMenuOpen ? '收起基本信息' : '展开基本信息'"
                @click.stop="toggleBrandMenu"
              >
                ⋯
              </button>

              <div
                class="dr-info-avatar"
                :style="{ background: app?.iconBackground || '#EEF4FF' }"
              >
                {{ app?.icon || '🤖' }}
              </div>
              <div v-show="!collapsed" class="dr-info-body">
                <div class="dr-info-name">{{ app?.name }}</div>
                <div class="dr-info-desc">
                  {{ app?.description || 'AI 驱动的智能应用' }}
                </div>
                <div class="dr-info-meta">
                  <span>🕒 {{ app?.updatedAt || '刚刚' }}</span>
                </div>
                <div class="dr-info-tags">
                  <span class="dr-tag dr-tag-live">● 运行中</span>
                  <span class="dr-tag dr-tag-ver">v1.0</span>
                </div>
              </div>
              <div v-show="collapsed" class="dr-info-collapsed-dot" />

              <div v-if="brandMenuOpen" class="dr-brand-panel" @click.stop>
                <!-- Header: icon + name + mode -->
                <div class="dr-bp-head">
                  <div
                    class="dr-bp-icon"
                    :style="{ background: app?.iconBackground || '#EEF4FF' }"
                  >
                    {{ app?.icon || '🤖' }}
                  </div>
                  <div class="dr-bp-title-wrap">
                    <div class="dr-bp-title">{{ form.name || app?.name }}</div>
                    <div class="dr-bp-mode">{{ modeLabel }}</div>
                  </div>
                </div>

                <!-- Quick actions row -->
                <div class="dr-bp-actions">
                  <button class="dr-bp-action" @click="onBrandEdit">
                    <span class="dr-bp-action-icon">✎</span>
                    <span>编辑信息</span>
                  </button>
                  <button class="dr-bp-action" @click="onBrandDuplicate">
                    <span class="dr-bp-action-icon">⧉</span>
                    <span>复制</span>
                  </button>
                  <button class="dr-bp-action" @click="onBrandExport">
                    <span class="dr-bp-action-icon">⇩</span>
                    <span>导出 DSL</span>
                  </button>
                  <button class="dr-bp-action">
                    <span class="dr-bp-action-icon">⋯</span>
                    <span>更多</span>
                  </button>
                </div>

                <!-- Web App section -->
                <section class="dr-bp-section">
                  <div class="dr-bp-section-head">
                    <span class="dr-bp-section-icon dr-bp-section-icon-web">◧</span>
                    <span class="dr-bp-section-title">Web App</span>
                    <span class="dr-bp-status" :class="{ on: brandWebAppOn }">
                      ● {{ brandWebAppOn ? '运行中' : '已停用' }}
                    </span>
                    <label class="dr-bp-switch">
                      <input type="checkbox" v-model="brandWebAppOn" />
                      <span class="dr-bp-switch-track" />
                    </label>
                  </div>
                  <div class="dr-bp-section-label">公开访问 URL</div>
                  <div class="dr-bp-url-row">
                    <span class="dr-bp-url" :title="webAppUrl">{{ webAppUrl }}</span>
                    <button
                      class="dr-bp-icon-btn"
                      title="复制链接"
                      @click="copyToClipboard(webAppUrl)"
                    >
                      ⎘
                    </button>
                    <button class="dr-bp-icon-btn" title="二维码">▦</button>
                    <button class="dr-bp-icon-btn" title="刷新">↻</button>
                  </div>
                  <div class="dr-bp-links">
                    <button class="dr-bp-link">↗ 启动</button>
                    <button class="dr-bp-link">◱ 嵌入</button>
                    <button class="dr-bp-link">🎨 定制化</button>
                    <button class="dr-bp-link">⚙ 设置</button>
                  </div>
                </section>

                <!-- Backend API section -->
                <section class="dr-bp-section">
                  <div class="dr-bp-section-head">
                    <span class="dr-bp-section-icon dr-bp-section-icon-api">🖳</span>
                    <span class="dr-bp-section-title">后端服务 API</span>
                    <span class="dr-bp-status" :class="{ on: brandApiOn }">
                      ● {{ brandApiOn ? '运行中' : '已停用' }}
                    </span>
                    <label class="dr-bp-switch">
                      <input type="checkbox" v-model="brandApiOn" />
                      <span class="dr-bp-switch-track" />
                    </label>
                  </div>
                  <div class="dr-bp-section-label">API 访问地址</div>
                  <div class="dr-bp-url-row">
                    <span class="dr-bp-url" :title="apiBaseUrl">{{ apiBaseUrl }}</span>
                    <button
                      class="dr-bp-icon-btn"
                      title="复制"
                      @click="copyToClipboard(apiBaseUrl)"
                    >
                      ⎘
                    </button>
                  </div>
                  <div class="dr-bp-links">
                    <button class="dr-bp-link">🔑 API 密钥</button>
                    <button class="dr-bp-link" @click="onAccessApi">
                      📄 查看 API 文档
                    </button>
                  </div>
                </section>

                <!-- MCP Server section -->
                <section class="dr-bp-section">
                  <div class="dr-bp-section-head">
                    <span class="dr-bp-section-icon dr-bp-section-icon-mcp">◈</span>
                    <span class="dr-bp-section-title">MCP 服务</span>
                    <span class="dr-bp-status" :class="{ on: brandMcpOn }">
                      ● {{ brandMcpOn ? '运行中' : '已停用' }}
                    </span>
                    <label class="dr-bp-switch">
                      <input type="checkbox" v-model="brandMcpOn" />
                      <span class="dr-bp-switch-track" />
                    </label>
                  </div>
                  <div class="dr-bp-section-label">服务器的 URL</div>
                  <div class="dr-bp-url-row">
                    <span class="dr-bp-url dr-bp-url-muted">
                      {{ brandMcpOn ? apiBaseUrl + '/mcp' : '···········' }}
                    </span>
                  </div>
                  <div class="dr-bp-links">
                    <button class="dr-bp-link">✎ 添加描述</button>
                  </div>
                </section>
              </div>
            </div>

            <!-- Nav items -->
            <nav class="dr-nav">
              <button
                v-for="item in [
                  { id: 'orchestrate', icon: AppstoreOutlined, label: '编排' },
                  { id: 'api', icon: ApiOutlined, label: '访问 API' },
                  { id: 'logs', icon: FileTextOutlined, label: '日志与标注' },
                  { id: 'monitor', icon: LineChartOutlined, label: '监测' },
                ] as const"
                :key="item.id"
                :class="['dr-nav-item', { on: nav === item.id }]"
                @click="nav = item.id"
              >
                <component :is="item.icon" class="dr-nav-icon" />
                <span v-show="!collapsed" class="dr-nav-label">
                  {{ item.label }}
                </span>
              </button>
            </nav>

            <button
              class="dr-rail-collapse"
              @click="collapsed = !collapsed"
              :title="collapsed ? '展开' : '折叠'"
            >
              {{ collapsed ? '»' : '«' }}
            </button>
          </aside>

          <!-- ── MAIN content ── -->
          <main class="dr-main">
            <!-- 编排 tab -->
            <div v-if="nav === 'orchestrate'" class="dr-orch">
              <!-- Flow modes → full-width designer. Built-in
                   DrawerFlowDesigner covers 99% of use cases; a host that
                   wants full control can still override via the
                   {@code #designer} slot. -->
              <div v-if="isFlowMode" class="dr-flow-slot">
                <slot
                  name="designer"
                  :register-designer="
                    (inst: any) => (designerRef = inst)
                  "
                  :initial-graph-json="form.graphJson"
                >
                  <DrawerFlowDesigner
                    :initial-graph-json="form.graphJson"
                    :app-id="app?.id"
                    :app-mode="app?.mode"
                    :workflow-options-loader="api.listPublishedWorkflowOptions"
                    :register-designer="
                      (inst: any) => (designerRef = inst)
                    "
                  />
                </slot>
              </div>

              <!-- Non-flow modes → 50/50 orchestrate + preview -->
              <div v-else class="dr-chat-split">
                <!-- LEFT: config cards -->
                <section class="dr-config-col">
                  <!-- 提示词 -->
                  <div class="dr-card">
                    <div class="dr-card-head">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        提示词
                      </span>
                      <button class="dr-link-btn">↗ 生成</button>
                    </div>
                    <textarea
                      v-model="form.instructions"
                      class="dr-prompt-textarea"
                      placeholder="在这里编写你的提示词，输入「{」插入变量，输入「/」插入提示内容块"
                      rows="8"
                    />
                  </div>

                  <!-- 变量 —— 与流程设计里 StartNode 的「输入变量」同源，
                       用弹窗编辑，避免行内输入的信息密度低 / 类型不可选。 -->
                  <div class="dr-card">
                    <div class="dr-card-head">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        变量
                      </span>
                      <button
                        class="dr-link-btn dr-link-btn-solid"
                        title="添加变量"
                        @click="openVariableEditor()"
                      >
                        + 添加
                      </button>
                    </div>
                    <div v-if="variables.length === 0" class="dr-var-empty">
                      尚未配置变量，点击右上角 + 添加。变量可通过
                      <code v-pre>{{ name }}</code>
                      在提示词或开场白中引用。
                    </div>
                    <div v-else class="dr-var-list">
                      <div
                        v-for="(v, i) in variables"
                        :key="v.id ?? i"
                        class="dr-var-item"
                      >
                        <div class="dr-var-left">
                          <span class="dr-var-icon" aria-hidden="true">◈</span>
                          <div class="dr-var-meta">
                            <div class="dr-var-label" :title="v.label || v.name">
                              {{ v.label || v.name || '未命名变量' }}
                            </div>
                            <div class="dr-var-name" :title="v.name">
                              {{ v.name || '—' }}
                            </div>
                          </div>
                        </div>
                        <div class="dr-var-right">
                          <span class="dr-var-tag">
                            {{ variableTypeLabel(v.type) }}
                          </span>
                          <span
                            v-if="v.required"
                            class="dr-var-tag dr-var-tag-warn"
                          >
                            必填
                          </span>
                          <button
                            class="dr-icon-btn"
                            title="编辑"
                            @click="openVariableEditor(v)"
                          >
                            ✎
                          </button>
                          <button
                            class="dr-icon-btn dr-icon-btn-danger"
                            title="删除"
                            @click="removeVariable(i)"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- 知识库 -->
                  <div class="dr-card">
                    <div class="dr-card-head">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        知识库
                      </span>
                      <div class="dr-card-actions">
                        <button class="dr-link-btn dr-link-muted">
                          🔧 召回设置
                        </button>
                        <button class="dr-link-btn" @click="openDatasetPicker">
                          + 添加
                        </button>
                      </div>
                    </div>
                    <div
                      v-if="form.datasetIds.length === 0"
                      class="dr-card-empty"
                    >
                      您可以导入知识库作为上下文
                    </div>
                    <div v-else class="dr-ds-list">
                      <div
                        v-for="d in selectedDatasets"
                        :key="d.id"
                        class="dr-ds-row"
                      >
                        <span class="dr-ds-row-icon" aria-hidden="true">
                          📚
                        </span>
                        <span class="dr-ds-row-name" :title="d.name">
                          {{ d.name }}
                        </span>
                        <span class="dr-ds-row-tags">
                          <span
                            v-if="datasetTechniqueLabel(d)"
                            class="dr-ds-tag"
                          >
                            {{ datasetTechniqueLabel(d) }}
                          </span>
                          <span
                            v-if="datasetMethodLabel(d)"
                            class="dr-ds-tag dr-ds-tag-muted"
                          >
                            {{ datasetMethodLabel(d) }}
                          </span>
                        </span>
                        <button
                          class="dr-icon-btn"
                          title="移除"
                          @click="removeDataset(d.id)"
                        >
                          ✕
                        </button>
                      </div>
                    </div>

                    <!-- 元数据过滤 -->
                    <div class="dr-inline-row">
                      <span class="dr-inline-label">元数据过滤</span>
                      <select v-model="form.metadataFilter" class="dr-mini-select">
                        <option :value="false">禁用</option>
                        <option :value="true">启用</option>
                      </select>
                    </div>
                  </div>

                  <!-- 工具 -->
                  <div class="dr-card">
                    <div class="dr-card-head"><span class="dr-card-title"><span class="dr-card-title-dot" />Skill</span></div>
                    <SkillSelect v-model="form.skillIds" />
                  </div>

                  <!-- 工具 -->
                  <div class="dr-card">
                    <div class="dr-card-head">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        工具
                      </span>
                      <button class="dr-link-btn">+ 添加</button>
                    </div>
                    <div v-if="tools.length === 0" class="dr-card-empty">
                      您可以添加工具来扩展智能体能力
                    </div>
                    <div v-else class="dr-chip-list">
                      <label
                        v-for="t in tools"
                        :key="t.name"
                        class="dr-chip"
                        :class="{ on: form.toolNames.includes(t.name) }"
                      >
                        <input
                          type="checkbox"
                          :value="t.name"
                          :checked="form.toolNames.includes(t.name)"
                          @change="
                            (e: Event) => {
                              const on = (e.target as HTMLInputElement).checked;
                              if (on) form.toolNames.push(t.name);
                              else
                                form.toolNames = form.toolNames.filter(
                                  (n) => n !== t.name,
                                );
                            }
                          "
                        />
                        <span>{{ t.icon || '🔧' }} {{ t.name }}</span>
                      </label>
                    </div>
                  </div>

                  <!-- 视觉 -->
                  <div class="dr-card dr-card-inline">
                    <div class="dr-card-head dr-card-head-inline">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        视觉
                      </span>
                      <div class="dr-card-actions">
                        <button class="dr-link-btn dr-link-muted">⚙ 设置</button>
                        <label class="dr-switch">
                          <input v-model="form.vision" type="checkbox" />
                          <span class="dr-switch-slider" />
                        </label>
                      </div>
                    </div>
                  </div>
                </section>

                <!-- RIGHT: preview col
                     宿主传了 chatConfig → 用 ChatIframePanel 承载 antd-react-chat
                     iframe（变量抽出为可填参数）；未传则保留旧的简单预览面板。 -->
                <section class="dr-preview-col">
                  <ChatIframePanel
                    v-if="chatConfig?.src"
                    class="hud-panel"
                    :src="chatConfig.src"
                    :params="chatConfig.params"
                    :context="chatConfig.context"
                    :title="chatConfig.title || '调试与预览'"
                    :session-key="chatConfig.sessionKey || `agent-${app?.id || 'draft'}`"
                    :variables="chatDebugVariables"
                    :closable="false"
                  />
                  <template v-else>
                    <div class="dr-preview-head">
                      <span class="dr-card-title">
                        <span class="dr-card-title-dot" />
                        调试与预览
                      </span>
                      <button
                        class="dr-icon-btn"
                        title="清空"
                        @click="previewMessages = []"
                      >
                        ↻
                      </button>
                    </div>
                    <div class="dr-preview-scroll">
                      <div
                        v-if="previewMessages.length === 0"
                        class="dr-preview-empty"
                      >
                        发一条消息试试 —— 上面的编辑先「发布」后才会作用于预览。
                      </div>
                      <div
                        v-for="(m, i) in previewMessages"
                        :key="i"
                        class="dr-msg"
                        :class="`dr-msg-${m.role}`"
                      >
                        <div class="dr-msg-role">
                          {{ m.role === 'user' ? '你' : '助手' }}
                        </div>
                        <div
                          class="dr-msg-body"
                          :class="{ 'dr-msg-failed': m.failed }"
                        >
                          {{ m.content || (m.role === 'assistant' ? '…' : '') }}
                        </div>
                      </div>
                    </div>
                    <div class="dr-preview-composer">
                      <input
                        v-model="previewInput"
                        class="dr-input"
                        placeholder="和 Bot 聊天"
                        :disabled="previewSending"
                        @keydown.enter.prevent="sendPreview"
                      />
                      <button
                        class="dr-send-btn"
                        :disabled="previewSending || !previewInput.trim()"
                        @click="sendPreview"
                      >
                        ▶
                      </button>
                    </div>
                  </template>
                </section>
              </div>
            </div>

            <!-- 访问 API — Dify-parity docs page with API-key management. -->
            <div v-else-if="nav === 'api'" class="dr-api-slot">
              <AgentApiDocs
                :base-url="apiBaseUrl"
                :endpoints="apiEndpoints"
                :app-id="app?.id"
                :api="api"
              />
            </div>

            <!-- 日志与标注 — built-in panel wired to props.api. Host can
                 override via the `#logs` slot for full customisation. -->
            <div v-else-if="nav === 'logs'" class="dr-logs-slot">
              <slot name="logs" :app="app">
                <LogAnnotationPanel :app="app" :api="api" />
              </slot>
            </div>

            <!-- 监测 — built-in panel wired to props.api. Host can override
                 via the `#monitor` slot. -->
            <div v-else-if="nav === 'monitor'" class="dr-monitor-slot">
              <slot name="monitor" :app="app">
                <MonitorPanel :app="app" :api="api" />
              </slot>
            </div>
          </main>
        </div>

        <!-- Dataset picker — shared with the workflow KNOWLEDGE_RETRIEVAL
             node config so both entry points use the same UX. -->
        <DatasetPickerModal
          v-model:open="datasetPickerOpen"
          :initial-selected-ids="form.datasetIds"
          :datasets="datasetCatalogLoaded ? datasetCatalog : undefined"
          :tenant-id="tenantId"
          @submit="onDatasetsPicked"
        />

        <!-- Variable editor — mirrors the StartNode input-variable modal in
             the flow designer so the two surfaces feel identical. -->
        <VariableEditorModal
          ref="variableEditorRef"
          @submit="onVariableSubmit"
        />

        <!-- 调试浮层：浮在抽屉右上，无蒙版 —— 下面的画布/表单仍可交互。
             顶部 header 可拖动（避开按钮/输入/iframe），位置写入 localStorage。
             变量清单：flow 模式从 designerRef 的 START 节点抽；非 flow 走
             variables 列表（当前不会走这个分支，保留兼容）。 -->
        <Teleport to="body">
          <transition name="dr-debug-pop">
            <div
            v-if="debugPanelOpen && (isWorkflowMode || chatConfig?.src)"
            class="dr-debug-panel"
            :class="{
              'dr-debug-panel-dragging': debugDragging,
              'dr-debug-panel-chatflow': app?.mode === 'chatflow',
              'dr-debug-panel-expanded': app?.mode === 'chatflow' && chatflowTraceExpanded,
            }"
            :style="
              debugPos
                ? { left: debugPos.left + 'px', top: debugPos.top + 'px' }
                : undefined
            "
            @pointerdown="onDebugPanelPointerDown"
            @pointermove="onDebugPanelPointerMove"
            @pointerup="onDebugPanelPointerUp"
            @pointercancel="onDebugPanelPointerUp"
          >
            <WorkflowDebugPanel
              v-if="isWorkflowMode"
              :key="debugSessionSuffix"
              class="hud-panel"
              :graph="workflowDebugGraph"
              :variables="flowDebugVariables"
              :execute-workflow="api.runWorkflowGraph"
              :execute-workflow-stream="api.runWorkflowGraphStream"
              :title="`调试：${app?.name || '工作流'}`"
              @close="closeDebug"
            />
            <template v-else>
              <button
                v-if="app?.mode === 'chatflow'"
                type="button"
                class="dr-chatflow-expand"
                :title="chatflowTraceExpanded ? '收起执行过程' : '展开执行过程'"
                :aria-label="chatflowTraceExpanded ? '收起执行过程' : '展开执行过程'"
                @pointerdown.stop
                @click.stop="toggleChatflowTrace"
              >{{ chatflowTraceExpanded ? '▶' : '◀' }}</button>
              <div v-if="app?.mode === 'chatflow'" class="dr-chatflow-layout">
                <ChatflowExecutionTrace
                  v-show="chatflowTraceExpanded"
                  ref="chatflowTraceRef"
                  class="dr-chatflow-trace"
                  :graph="workflowDebugGraph"
                />
                <ChatIframePanel
                  :key="debugSessionSuffix"
                  class="hud-panel"
                  :src="chatConfig?.src || '/chat/'"
                  :params="chatConfig?.params"
                  :context="chatConfig?.context"
                  :title="chatConfig?.title || `调试：${app?.name || ''}`"
                  :session-key="chatConfig?.sessionKey || `chatflow-${app?.id || 'draft'}-${debugSessionSuffix}`"
                  :variables="flowDebugVariables"
                  @event="onChatflowEvent"
                  @close="closeDebug"
                />
              </div>
              <ChatIframePanel
                v-else
                :key="debugSessionSuffix"
                class="hud-panel"
                :src="chatConfig?.src || '/chat/'"
                :params="chatConfig?.params"
                :context="chatConfig?.context"
                :title="chatConfig?.title || `调试：${app?.name || ''}`"
                :session-key="chatConfig?.sessionKey || `${app?.mode || 'app'}-${app?.id || 'draft'}-${debugSessionSuffix}`"
                :variables="chatDebugVariables"
                @close="closeDebug"
              />
            </template>
            </div>
          </transition>
        </Teleport>
      </div>
  </Drawer>
</template>

<style scoped>
/* ==== Mask + panel (full-screen) ==== */
.dr-mask {
  position: fixed;
  inset: 0;
  z-index: 1050;
  background: #fff;
  animation: dr-fade-in 0.14s ease-out;
}
.dr-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  overflow: hidden;
}
:global(.dr-app-drawer) {
  max-width: 100vw;
}
/* The publish menu lives in the drawer header while the body is its following
 * sibling.  Give the header its own, higher stacking level so the body cannot
 * paint over header popovers; the menu's z-index alone cannot escape a lower
 * parent stacking context. */
:global(.dr-app-drawer .as-drawer__header) {
  position: relative;
  z-index: 2;
  overflow: visible;
}
:global(.dr-app-drawer .as-drawer__body) {
  position: relative;
  z-index: 1;
}
@keyframes dr-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ==== Top header ==== */
.dr-header {
  flex: none;
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  gap: 16px;
}
.dr-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.dr-icon-sm {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 15px;
  flex-shrink: 0;
}
.dr-title-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.dr-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.dr-mode-badge {
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 500;
  color: #4338ca;
  background: #eef2ff;
  border-radius: 4px;
  letter-spacing: 0.05em;
}

/* ----- Info-card brand popover — anchored to the left-rail info card,
 * pops out to the right so the 380px panel doesn't clip the 224px rail. ----- */
/* Panel container — soft floating card with a slight gradient wash and a
 * more generous shadow so it visually separates from the drawer chrome. */
.dr-brand-panel {
  position: absolute;
  top: 0;
  left: calc(100% + 12px);
  width: 400px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfbfe 100%);
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow:
    0 20px 40px -12px rgba(15, 23, 42, 0.16),
    0 4px 12px rgba(15, 23, 42, 0.06);
  padding: 14px;
  z-index: 60;
  animation: dr-fade-in 0.14s ease-out;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Panel header ─ app icon + name + mode chip */
.dr-bp-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 2px 2px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.dr-bp-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}
.dr-bp-title-wrap {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dr-bp-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}
.dr-bp-mode {
  align-self: flex-start;
  padding: 1px 7px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: #4338ca;
  background: #eef2ff;
  border-radius: 4px;
  line-height: 1.5;
}

/* Quick action tiles ─ 4 up, icon-over-label, lifts on hover */
.dr-bp-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  padding: 0;
  border-bottom: none;
}
.dr-bp-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 4px;
  border: 1px solid #f1f5f9;
  background: #fafbfc;
  border-radius: 10px;
  color: #475569;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}
.dr-bp-action:hover {
  border-color: #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(99, 102, 241, 0.1);
}
.dr-bp-action-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  line-height: 1;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.dr-bp-action:hover .dr-bp-action-icon {
  background: #4338ca;
  color: #fff;
}

/* Section cards — each channel (Web App / API / MCP) sits in its own
 * light-tinted card with a status chip + toggle in the head row. */
.dr-bp-section {
  padding: 12px;
  background: #fafbfc;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.dr-bp-section:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}
.dr-bp-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dr-bp-section-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 12px;
  flex-shrink: 0;
}
.dr-bp-section-icon-web {
  background: #dbeafe;
  color: #1d4ed8;
}
.dr-bp-section-icon-api {
  background: #e0e7ff;
  color: #4338ca;
}
.dr-bp-section-icon-mcp {
  background: #ffedd5;
  color: #c2410c;
}
.dr-bp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.dr-bp-status {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 2px;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 500;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 999px;
}
.dr-bp-status.on {
  color: #15803d;
  background: #dcfce7;
}
.dr-bp-switch {
  position: relative;
  margin-left: auto;
  width: 32px;
  height: 18px;
  cursor: pointer;
  flex-shrink: 0;
}
.dr-bp-switch input {
  position: absolute;
  opacity: 0;
  inset: 0;
  cursor: pointer;
  z-index: 1;
}
.dr-bp-switch-track {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.15s ease;
}
.dr-bp-switch-track::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.18);
  transition: transform 0.15s ease;
}
.dr-bp-switch input:checked + .dr-bp-switch-track {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
}
.dr-bp-switch input:checked + .dr-bp-switch-track::after {
  transform: translateX(14px);
}

/* URL row — reads as an input field with trailing icon buttons */
.dr-bp-section-label {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}
.dr-bp-url-row {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 5px 6px 5px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 10px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.dr-bp-url-row:hover {
  border-color: #c7d2fe;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.06);
}
.dr-bp-url {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-bp-url-muted {
  color: #94a3b8;
  letter-spacing: 0.05em;
}
.dr-bp-icon-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.dr-bp-icon-btn:hover {
  background: #eef2ff;
  color: #4338ca;
}

/* Link pills — rounded chip style, so they visually group as related
 * shortcuts rather than dissolve into flat text buttons. */
.dr-bp-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.dr-bp-link {
  padding: 4px 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 999px;
  color: #475569;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}
.dr-bp-link:hover {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4338ca;
}

/* Right-aligned wrapper for the model picker + action buttons. Grouping them
 * under a single margin-left: auto keeps both flush right — two separate auto
 * margins would split the flex free space and push the picker to the middle.
 * Works uniformly whether or not the picker is rendered. */
.dr-header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.dr-header-model {
  display: flex;
  align-items: center;
  min-width: 260px;
  max-width: 320px;
}
/* The picker's default trigger is a full-width block; keep it compact enough
 * to sit next to the 发布 / 关闭 buttons without pushing the app title around. */
.dr-header-model :deep(.ph-mp-trigger) {
  width: 100%;
  min-height: 36px;
  padding: 4px 10px;
}
.dr-actions {
  display: flex;
  gap: 8px;
}
.dr-btn {
  height: 32px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease,
    opacity 0.15s ease;
}
.dr-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.25);
}
.dr-btn-icon {
  font-size: 13px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
/* 调试 button — bug icon uses success-green so it reads as "run/test",
   even when the surrounding secondary button is muted. */
.dr-btn-icon-debug {
  color: #16a34a;
}
.dr-btn:disabled .dr-btn-icon-debug {
  color: #94a3b8;
}
.dr-btn-icon-x {
  font-size: 12px;
  color: #94a3b8;
  transition: color 0.15s ease;
}

/* 发布 — primary gradient CTA */
.dr-btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  box-shadow:
    0 1px 2px rgba(79, 70, 229, 0.24),
    0 1px 1px rgba(15, 23, 42, 0.06);
}
.dr-btn-primary:hover:not(:disabled) {
  box-shadow:
    0 3px 8px rgba(79, 70, 229, 0.32),
    0 1px 2px rgba(15, 23, 42, 0.08);
  transform: translateY(-0.5px);
}
.dr-btn-primary:disabled {
  background: #cbd5e1;
  box-shadow: none;
  cursor: not-allowed;
}

/* 保存 — outlined button, hover picks up an indigo hint to echo the CTA */
.dr-btn-secondary {
  background: #fff;
  color: #334155;
  border-color: #e2e8f0;
}
.dr-btn-secondary:hover:not(:disabled) {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4338ca;
}
.dr-btn-secondary:active:not(:disabled) {
  background: #e0e7ff;
}
.dr-btn-secondary:disabled {
  color: #94a3b8;
  background: #f8fafc;
  cursor: not-allowed;
}
/* 「调试」按钮打开中 —— 用轻微填充 + 边框色标记正在活动 */
.dr-btn-active {
  background: #eef2ff;
  border-color: #a5b4fc;
  color: #4338ca;
}

/* 调试浮层：浮在抽屉右上，无蒙版，下面内容仍可点/滚 */
.dr-debug-panel {
  position: fixed; /* 用 fixed 让坐标以视口为准，跨 iframe / scroll 都稳定 */
  top: 80px;
  right: 24px;
  /* 面板 Teleport 到 body 后使用全局浮层级别，确保高于抽屉标题栏。 */
  z-index: 1300;
  width: min(460px, 92vw); /* 与 ChatIframePanel 一致；展开执行过程时才加倍 */
  height: 86vh;
  max-height: calc(100vh - 96px);
  box-shadow:
    0 20px 50px rgba(15, 23, 42, 0.24),
    0 4px 12px rgba(15, 23, 42, 0.1);
  border-radius: 12px;
  overflow: hidden;
  touch-action: none; /* 阻止移动端手势卷动，让 pointermove 走拖动 */
}
.dr-debug-panel-chatflow {
  width: min(460px, 92vw);
  overflow: visible;
  transition: width 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.dr-debug-panel-chatflow.dr-debug-panel-expanded { width: min(920px, 92vw); }
.dr-chatflow-layout{display:grid;grid-template-columns:1fr;width:100%;height:100%;gap:10px}
.dr-debug-panel-expanded .dr-chatflow-layout{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}
.dr-chatflow-trace{min-width:0}
.dr-chatflow-expand{position:absolute;z-index:4;left:-22px;top:50%;display:grid;place-items:center;width:24px;height:48px;padding:0;transform:translateY(-50%);border:1px solid #c7d2fe;border-right:0;border-radius:9px 0 0 9px;background:#fff;color:#4f46e5;box-shadow:-4px 3px 10px rgb(15 23 42/.12);cursor:pointer;font-size:11px}
.dr-chatflow-expand:hover{background:#eef2ff;color:#3730a3}
/* 用了 left/top 内联样式时，覆盖默认的 right 定位 */
.dr-debug-panel[style*='left'] {
  right: auto;
}
/* 顶部 header 区（第一屏 46px）呈 grab 光标，提示可拖 */
.dr-debug-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 46px;
  cursor: grab;
  pointer-events: none; /* 只做视觉提示，事件仍由子元素接收 */
}
.dr-debug-panel-dragging {
  user-select: none;
  transition: none !important;
}
.dr-debug-panel-dragging::before {
  cursor: grabbing;
}

.dr-debug-pop-enter-active,
.dr-debug-pop-leave-active {
  transition:
    transform 0.2s cubic-bezier(0.16, 1, 0.3, 1),
    opacity 0.2s ease;
  transform-origin: top right;
}
.dr-debug-pop-enter-from,
.dr-debug-pop-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

/* 关闭 — ghost button, quiet by default */
.dr-btn-ghost {
  background: transparent;
  color: #64748b;
}
.dr-btn-ghost:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.dr-btn-ghost:hover .dr-btn-icon-x {
  color: #0f172a;
}
.dr-btn-ghost:active {
  background: #e2e8f0;
}

/* ==== Publish split-button + dropdown menu (Dify parity) ==== */
.dr-publish-wrap {
  position: relative;
  display: inline-flex;
}
.dr-publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 10px;
}
.dr-publish-caret {
  font-size: 10px;
  opacity: 0.85;
  transition: transform 0.15s ease;
  display: inline-block;
}
.dr-publish-btn-open .dr-publish-caret {
  transform: rotate(180deg);
}
.dr-publish-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 20;
  width: 280px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow:
    0 12px 40px rgba(15, 23, 42, 0.12),
    0 2px 6px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-direction: column;
  gap: 6px;
  animation: dr-publish-in 0.12s ease-out;
}
@keyframes dr-publish-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.dr-publish-head {
  padding: 4px 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dr-publish-head-title {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  letter-spacing: 0.02em;
}
.dr-publish-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.dr-publish-head-time {
  font-size: 13px;
  color: #334155;
}
.dr-publish-restore {
  padding: 3px 10px;
  font-size: 12px;
  color: #4338ca;
  background: #fff;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.dr-publish-restore:hover {
  background: #eef2ff;
}
.dr-publish-primary {
  margin-top: 2px;
  padding: 9px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.dr-publish-primary:hover:not(:disabled) {
  opacity: 0.92;
}
.dr-publish-primary:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
.dr-publish-shortcut {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.dr-publish-shortcut kbd {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
  font-size: 10px;
  font-weight: 500;
  color: #e0e7ff;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 4px;
}
.dr-publish-divider {
  height: 1px;
  margin: 4px 0;
  background: #f1f5f9;
}
.dr-publish-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  font-size: 13px;
  color: #334155;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}
.dr-publish-item:hover {
  background: #f8fafc;
}
.dr-publish-item-icon {
  width: 18px;
  color: #64748b;
  font-size: 15px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.dr-publish-primary-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.dr-publish-primary-icon {
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.dr-publish-item-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-publish-item-arrow {
  color: #cbd5e1;
  font-size: 12px;
  flex-shrink: 0;
}
.dr-publish-item:hover .dr-publish-item-arrow {
  color: #64748b;
}

/* ==== Body: left rail + main ==== */
.dr-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

/* -- Left rail -- */
.dr-rail {
  width: 224px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  /* Note: rail must allow visible overflow so the info-card popover can
   * escape to the right; the nav section owns its own overflow-y scroll. */
  overflow: visible;
}
.dr-rail-collapsed {
  width: 72px;
}
.dr-info-card {
  position: relative;
  margin: 10px 10px 4px;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.dr-info-card:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}
.dr-info-card:hover .dr-info-trigger {
  opacity: 1;
}
.dr-info-card-active {
  background: #eef2ff;
  border-color: #c7d2fe;
  box-shadow: 0 1px 2px rgba(99, 102, 241, 0.08);
}
.dr-info-card-active .dr-info-trigger {
  opacity: 1;
  color: #4338ca;
  background: #e0e7ff;
}
.dr-info-trigger {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease;
  z-index: 2;
}
.dr-info-trigger:hover {
  background: #eef2ff;
  color: #4338ca;
}
.dr-info-trigger-open {
  opacity: 1;
}
.dr-rail-collapsed .dr-info-card {
  margin: 10px 6px 4px;
  padding: 12px 8px;
}
.dr-rail-collapsed .dr-info-trigger {
  display: none;
}
.dr-info-avatar {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24px;
  background: #eef2ff;
  color: #4338ca;
}
.dr-info-body {
  width: 100%;
  text-align: center;
}
.dr-info-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-info-desc {
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.dr-info-meta {
  margin-top: 6px;
  font-size: 10px;
  color: #94a3b8;
}
.dr-info-tags {
  margin-top: 6px;
  display: flex;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}
.dr-tag {
  padding: 1px 6px;
  font-size: 10px;
  border-radius: 999px;
}
.dr-tag-live {
  color: #15803d;
  background: #dcfce7;
}
.dr-tag-ver {
  color: #1d4ed8;
  background: #dbeafe;
}
.dr-info-collapsed-dot {
  width: 6px;
  height: 6px;
  background: #22c55e;
  border-radius: 50%;
  animation: dr-pulse 1.6s ease-in-out infinite;
}
@keyframes dr-pulse {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

.dr-nav {
  flex: 1;
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}
.dr-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.dr-nav-item:hover {
  background: #f1f5f9;
}
.dr-nav-item.on {
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #c7d2fe;
  font-weight: 500;
}
.dr-nav-icon {
  font-size: 16px;
  width: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: inherit;
}
.dr-rail-collapsed .dr-nav-item {
  justify-content: center;
  padding: 8px;
}

.dr-rail-collapse {
  flex: none;
  padding: 8px;
  border: none;
  background: #f8fafc;
  border-top: 1px solid #f1f5f9;
  color: #64748b;
  font-size: 14px;
  cursor: pointer;
}
.dr-rail-collapse:hover {
  background: #f1f5f9;
  color: #0f172a;
}

/* -- Main content -- */
.dr-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

/* ==== Orchestrate pane ==== */
.dr-orch {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.dr-flow-slot {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  /* VueFlow needs an *explicit* size on its container or it renders empty —
     make this a flex container so the slotted FlowDesigner's height:100% has
     a resolved height context. */
  display: flex;
  flex-direction: column;
}
.dr-flow-slot > * {
  flex: 1;
  min-height: 0;
}
.dr-logs-slot {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.dr-logs-slot > * {
  flex: 1;
  min-height: 0;
}
.dr-monitor-slot {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.dr-monitor-slot > * {
  flex: 1;
  min-height: 0;
}
.dr-api-slot {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.dr-api-slot > * {
  flex: 1;
  min-height: 0;
}
.dr-chat-split {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}
.dr-config-col {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}
.dr-preview-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

/* ==== Config cards ==== */
.dr-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 16px;
}
.dr-card-inline {
  padding: 10px 16px;
}
.dr-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.dr-card-head-inline {
  margin-bottom: 0;
}
.dr-card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.dr-card-title-dot {
  width: 4px;
  height: 12px;
  background: #6366f1;
  border-radius: 2px;
}
.dr-card-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}
.dr-card-empty {
  font-size: 12px;
  color: #94a3b8;
  padding: 6px 0;
}
.dr-card-empty code {
  padding: 1px 4px;
  background: #f1f5f9;
  color: #6366f1;
  border-radius: 3px;
  font-size: 11px;
}
.dr-link-btn {
  padding: 2px 8px;
  font-size: 12px;
  color: #4338ca;
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 4px;
}
.dr-link-btn:hover {
  background: #eef2ff;
}
.dr-link-btn-solid {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(79, 70, 229, 0.24);
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;
}
.dr-link-btn-solid:hover {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  box-shadow: 0 2px 6px rgba(79, 70, 229, 0.32);
  transform: translateY(-0.5px);
}
.dr-link-muted {
  color: #64748b;
}
.dr-link-muted:hover {
  background: #f1f5f9;
}

.dr-prompt-textarea {
  width: 100%;
  padding: 10px 12px;
  font-size: 13px;
  border: 1.5px solid #6366f1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-family: inherit;
  resize: vertical;
  min-height: 140px;
  line-height: 1.6;
}
.dr-prompt-textarea:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.dr-input {
  padding: 6px 10px;
  font-size: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #0f172a;
}
.dr-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.dr-input-sm {
  padding: 4px 8px;
  font-size: 12px;
}

/* Variable card list — mirrors the flow designer's StartNode input-variable
 * rows so the two surfaces feel identical. Empty state + item rows. */
.dr-var-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  line-height: 1.6;
}
.dr-var-empty code {
  padding: 1px 5px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 4px;
  font-size: 11px;
}
.dr-var-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dr-var-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition:
    background 0.15s,
    border-color 0.15s;
}
.dr-var-item:hover {
  background: #f8fafc;
  border-color: #c7d2fe;
}
.dr-var-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.dr-var-icon {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 6px;
  font-size: 13px;
  flex-shrink: 0;
}
.dr-var-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.dr-var-label {
  font-size: 12px;
  font-weight: 500;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-var-name {
  font-size: 11px;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-var-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.dr-var-tag {
  padding: 1px 6px;
  font-size: 10px;
  color: #4338ca;
  background: #e0e7ff;
  border-radius: 4px;
  white-space: nowrap;
}
.dr-var-tag-warn {
  color: #b45309;
  background: #fef3c7;
}
.dr-icon-btn-danger:hover {
  color: #dc2626;
  background: #fee2e2;
}

.dr-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.dr-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: 12px;
  background: #f1f5f9;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  color: #334155;
}
.dr-chip.on {
  background: #eef2ff;
  border-color: #6366f1;
  color: #4338ca;
}
.dr-chip input {
  display: none;
}

/* Selected-dataset list (Dify parity — rows of icon + name + badges + ✕) */
.dr-ds-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dr-ds-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition: border-color 0.15s;
}
.dr-ds-row:hover {
  border-color: #c7d2fe;
}
.dr-ds-row-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 6px;
  font-size: 13px;
  flex-shrink: 0;
}
.dr-ds-row-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #0f172a;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dr-ds-row-tags {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.dr-ds-tag {
  padding: 1px 6px;
  font-size: 10px;
  color: #4338ca;
  background: #e0e7ff;
  border-radius: 4px;
  white-space: nowrap;
}
.dr-ds-tag-muted {
  color: #64748b;
  background: #f1f5f9;
}

.dr-inline-row {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dr-inline-label {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}
.dr-mini-select {
  padding: 3px 8px;
  font-size: 11px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #fff;
  color: #64748b;
}

/* ==== Switch (视觉) ==== */
.dr-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.dr-switch input {
  position: absolute;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
}
.dr-switch-slider {
  width: 32px;
  height: 18px;
  background: #cbd5e1;
  border-radius: 999px;
  position: relative;
  transition: background 0.15s;
}
.dr-switch-slider::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.15s;
}
.dr-switch input:checked + .dr-switch-slider {
  background: #6366f1;
}
.dr-switch input:checked + .dr-switch-slider::before {
  transform: translateX(14px);
}

.dr-icon-btn {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 4px;
  font-size: 13px;
}
.dr-icon-btn:hover {
  background: #f1f5f9;
  color: #475569;
}

/* ==== Preview col ==== */
.dr-preview-head {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f1f5f9;
}
.dr-preview-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dr-preview-empty {
  margin: auto;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
.dr-msg {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.dr-msg-user {
  align-items: flex-end;
}
.dr-msg-role {
  font-size: 10px;
  color: #94a3b8;
  padding: 0 4px;
}
.dr-msg-body {
  padding: 8px 12px;
  font-size: 13px;
  border-radius: 12px;
  max-width: 85%;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
}
.dr-msg-user .dr-msg-body {
  background: #4f46e5;
  color: #fff;
}
.dr-msg-assistant .dr-msg-body {
  background: #f1f5f9;
  color: #0f172a;
}
.dr-msg-failed {
  background: #fef2f2 !important;
  color: #b91c1c !important;
}
.dr-preview-composer {
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  gap: 8px;
}
.dr-preview-composer .dr-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  border-radius: 999px;
}
.dr-send-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  border-radius: 50%;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dr-send-btn:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}

/* ==== Plain content tabs (API / logs / monitor) ==== */
.dr-plain {
  padding: 32px 40px;
  overflow-y: auto;
}
.dr-plain-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
}
.dr-plain-hint {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.7;
}
.dr-plain-hint code {
  padding: 1px 6px;
  background: #f1f5f9;
  color: #4338ca;
  border-radius: 4px;
  font-size: 12px;
}
.dr-code {
  margin-top: 10px;
  padding: 12px 14px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  overflow-x: auto;
  line-height: 1.6;
}

/* ==== Empty state (flow slot placeholder) ==== */
.dr-empty {
  margin: auto;
  text-align: center;
  padding: 40px;
  color: #94a3b8;
}
.dr-empty-title {
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  margin-bottom: 6px;
}
.dr-empty-hint {
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
}
.dr-empty-hint code {
  padding: 1px 5px;
  background: #f1f5f9;
  border-radius: 4px;
  color: #4338ca;
  font-size: 11px;
}
.dr-history-mask { position: fixed; inset: 0; z-index: 1200; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, 0.35); }
.dr-history-panel { width: min(460px, 92vw); height: 100%; padding: 20px; overflow-y: auto; background: #fff; box-shadow: -8px 0 24px rgba(15, 23, 42, 0.15); }
.dr-history-header, .dr-history-item { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.dr-history-header { margin-bottom: 16px; font-size: 16px; }
.dr-history-close { border: 0; background: transparent; color: #64748b; font-size: 24px; cursor: pointer; }
.dr-history-list { display: grid; gap: 10px; }
.dr-history-item { padding: 14px; border: 1px solid #e2e8f0; border-radius: 10px; }
.dr-history-info { display: grid; min-width: 0; gap: 4px; }
.dr-history-info span, .dr-history-info p, .dr-history-empty { margin: 0; color: #64748b; font-size: 12px; }
.dr-history-info p { overflow-wrap: anywhere; }
.dr-history-empty { padding: 40px 0; text-align: center; }
</style>
