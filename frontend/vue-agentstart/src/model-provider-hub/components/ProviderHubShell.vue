<script setup lang="ts">
import { Spinner } from '../../ui/shadcn/spinner';
import { Card as LoadingCard } from '../../ui/shadcn/card';
import { message } from '../../ui/message';
import { confirmAction } from '../../ui/components/modalService';
import UiNotice from '../../ui/components/InlineNotice.vue';
import { ShadcnBoolean as AsShadcnBoolean } from '../../ui/components/ShadcnBoolean';

import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';
import { ShadcnSelect as AsShadcnSelect } from '../../ui/components/ShadcnSelect';

/**
 * ProviderHubShell — compact provider settings.
 * Configured providers, including those with an empty catalog, expose model
 * management through the count button. The full supported-provider catalog
 * below exposes credential setup and editing.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Button, Card, Modal, Popover } from '../../ui';

import {
  modelTypeColor,
  modelTypeLabel,
  useProviderHub,
} from '../composables/useProviderHub';
import type { CatalogRow, ModelEntity, ModelType, ProviderView } from '../types';
import DefaultModelsPanel from './DefaultModelsPanel.vue';
import ModelParameterDrawer from './ModelParameterDrawer.vue';
import ProviderCredentialModal from './ProviderCredentialModal.vue';
import ProviderIcon from './ProviderIcon.vue';

interface Props {
  tenantId?: string;
  /** Optional external override — if omitted the shell fetches models itself. */
  models?: ModelEntity[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'change'): void;
}>();

const {
  listProviders,
  synchronizeBuiltinCatalog,
  listModels,
  deleteProviderCredential,
  refreshCatalog,
  setModelEnabled,
  setModelEnabledByName,
  getProviderCatalog,
  registerModel,
} = useProviderHub();

const providers = ref<ProviderView[]>([]);
const localModels = ref<ModelEntity[]>([]);
const loading = ref(false);
const search = ref('');
const syncing = ref(false);
const busy = computed(() => loading.value || syncing.value);

async function synchronizeCatalog() {
  syncing.value = true;
  try {
    await synchronizeBuiltinCatalog();
    await refresh();
    message.success('预置目录已同步；远程模型请在供应商中手动拉取。');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '同步失败，请重试');
  } finally {
    syncing.value = false;
  }
}

const models = computed(() => props.models ?? localModels.value);

/**
 * Lazily-loaded catalog per provider. Populated by the new Dify-parity
 * /catalog endpoint on demand (openPopover) — that endpoint returns the
 * union of predefined DB rows + custom agent_model rows with the tenant's
 * enable/default state pre-joined. Legacy `models` list stays around as a
 * fallback for the compact card model counts.
 */
const catalogByProvider = ref<Record<string, CatalogRow[]>>({});

async function refresh() {
  loading.value = true;
  try {
    const ps = await listProviders(props.tenantId);
    providers.value = ps;
    if (!props.models) {
      localModels.value = await listModels(props.tenantId);
    }
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
watch(() => props.tenantId, refresh);

// -------------------------------------------------- helpers
/**
 * Bind icon props for {@link ProviderIcon} from whatever shape the backend
 * gave us. Explicit {@code svgIcon} / {@code iconUrl} win over the legacy
 * single-slot {@code icon} column; when only {@code icon} is present we
 * sniff the payload — inline SVG markup vs. URL — and route to the right
 * prop. Returns {@code {}} when no icon is known so ProviderIcon falls
 * back to its own local file lookup (matched against {@code p.name}).
 */
function providerIconProps(p: ProviderView): {
  svg?: string;
  iconUrl?: string;
} {
  if (p.svgIcon) return { svg: p.svgIcon };
  if (p.iconUrl) return { iconUrl: p.iconUrl };
  const raw = p.icon?.trim();
  if (!raw) return {};
  if (/^<(\?xml|svg\b)/i.test(raw)) return { svg: raw };
  if (/^(https?:|data:|\/)/i.test(raw)) return { iconUrl: raw };
  return {};
}

/**
 * Total known model count for the compact card. Combines the provider's
 * predefined catalog (visible without opening) + tenant-registered custom
 * models. Used only for the top
 * card's "N 个模型" summary — the popover uses the authoritative catalog.
 */
function knownModelCount(p: ProviderView): number {
  const predef = p.predefinedModels?.length ?? 0;
  const custom = p.installedModelCount ?? 0;
  return predef + custom;
}

/** Catalog rows for a provider — either the lazily-loaded /catalog result
 *  or an empty array if not fetched yet (popover shows loading state). */
function catalogRows(providerName: string): CatalogRow[] {
  return catalogByProvider.value[providerName] ?? [];
}

function enabledCount(p: ProviderView): number {
  const rows = catalogRows(p.name);
  if (rows.length > 0) return rows.filter((r) => r.enabled).length;
  // Fallback before the catalog was fetched: the backend pre-joins the same
  // switch state the popover uses (opt-in: missing setting row = disabled).
  return p.enabledModelCount ?? 0;
}

const configured = computed(() =>
  providers.value.filter((p) => p.credentialConfigured),
);
const installable = computed(() => {
  const q = search.value.trim().toLowerCase();
  return providers.value
    .filter(
      (p) =>
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.label.toLowerCase().includes(q),
    )
    .sort((a, b) => {
      if (a.name === 'openai-compatible') return 1;
      if (b.name === 'openai-compatible') return -1;
      return a.label.localeCompare(b.label);
    });
});

/**
 * Group the popover catalog by model type. Uses the authoritative /catalog
 * response, so predefined + custom are merged with correct enabled/isDefault
 * flags coming from the settings tables.
 */
function groupCatalogByType(providerName: string): Record<string, CatalogRow[]> {
  const acc: Record<string, CatalogRow[]> = {};
  for (const r of catalogRows(providerName)) {
    (acc[r.modelType] ??= []).push(r);
  }
  for (const key of Object.keys(acc)) {
    acc[key]!.sort((a, b) => a.model.localeCompare(b.model));
  }
  return acc;
}

function distinctModelTypes(p: ProviderView): string[] {
  const rows = catalogRows(p.name);
  if (rows.length > 0) {
    const set = new Set<string>();
    for (const r of rows) {
      set.add(r.modelType);
      if (r.features?.includes('VISION')) set.add('VISION');
    }
    return [...set];
  }
  // Fallback to what the provider advertises via predefined models.
  const set = new Set<string>();
  for (const pd of p.predefinedModels ?? []) {
    set.add(pd.modelType);
    if (pd.features?.includes('VISION')) set.add('VISION');
  }
  if (set.size === 0) for (const t of p.supportedModelTypes ?? []) set.add(t);
  return [...set];
}

// -------------------------------------------------- credential modal
const modalOpen = ref(false);
const modalProvider = ref<ProviderView | null>(null);
const modalEditMode = ref(false);

function openCredentialModal(p: ProviderView, editMode = false) {
  modalProvider.value = p;
  modalEditMode.value = editMode || p.credentialConfigured;
  modalOpen.value = true;
}

async function onCredentialSaved() {
  const name = modalProvider.value?.name;
  // Invalidate every per-provider cache before refetching — otherwise the
  // popover briefly shows the previous credential's rows (bug: "添加之后
  // 数据没有刷新过来"). Belt-and-suspenders: we also refetch below.
  if (name) invalidateProviderCache(name);
  await refresh();
  emit('change');
  // Auto-load the fresh catalog for the newly-configured provider so the
  // popover has data ready the moment the user clicks it. We do this even
  // when knownModelCount === 0 so a 0-catalog provider still shows an empty
  // popover (with the "点击 重新拉取" hint) instead of falling back to the
  // legacy count derived from a stale cache.
  if (name) {
    const p = providers.value.find((x) => x.name === name);
    if (p) {
      popoverProvider.value = p;
      await loadCatalog(p.name);
      await defaultsPanel.value?.refresh();
    }
  }
}

async function clearCredential(p: ProviderView) {
  if (
    !await confirmAction(
      `确认删除 ${p.label} 的 API Key？已启用的模型 / 默认模型 / 自定义模型条目会一起清除，下次重新添加从零开始。`,
    )
  )
    return;
  await deleteProviderCredential(p.name, props.tenantId);
  popoverProvider.value = null;
  // Purge every cache keyed by this provider — stale catalog rows would
  // otherwise resurface on the next popover open before the network refetch
  // finishes.
  invalidateProviderCache(p.name);
  await refresh();
  await defaultsPanel.value?.refresh();
  emit('change');
}

/**
 * Drop every model-provider-hub client-side cache keyed by {@code name} so a
 * subsequent open / refetch reads a clean slate. Called after credential
 * save AND delete because both invalidate the tenant-scoped catalog + toggle
 * state that lives behind that provider.
 */
function invalidateProviderCache(name: string) {
  if (name in catalogByProvider.value) {
    const next = { ...catalogByProvider.value };
    delete next[name];
    catalogByProvider.value = next;
  }
  if (catalogLoading.value.has(name)) {
    const s = new Set(catalogLoading.value);
    s.delete(name);
    catalogLoading.value = s;
  }
  if (refreshingCatalog.value.has(name)) {
    const s = new Set(refreshingCatalog.value);
    s.delete(name);
    refreshingCatalog.value = s;
  }
}

// -------------------------------------------------- catalog drawer
const popoverProvider = ref<ProviderView | null>(null);
const catalogLoading = ref<Set<string>>(new Set());

/**
 * Fetch the full Dify-parity catalog view for a provider (predefined + custom
 * union, with enabled/isDefault flags pre-joined from the settings tables).
 * Cached in {@link catalogByProvider} so re-opens don't re-hit the network.
 */
async function loadCatalog(name: string) {
  const next = new Set(catalogLoading.value);
  next.add(name);
  catalogLoading.value = next;
  try {
    const rows = await getProviderCatalog(name, props.tenantId);
    catalogByProvider.value = { ...catalogByProvider.value, [name]: rows };
  } finally {
    const after = new Set(catalogLoading.value);
    after.delete(name);
    catalogLoading.value = after;
  }
}

function openPopover(p: ProviderView) {
  closeCardMenu();
  popoverProvider.value = p;
  void loadCatalog(p.name);
}
function closePopover() {
  popoverProvider.value = null;
}

// Keep the popover's `p` in sync with fresh `providers` after refetch so
// installedModelCount / credentialConfigured reflect current state.
watch(providers, (ps) => {
  if (popoverProvider.value) {
    const still = ps.find((x) => x.name === popoverProvider.value!.name);
    popoverProvider.value = still ?? null;
  }
});

// -------------------------------------------------- catalog operations
const refreshingCatalog = ref<Set<string>>(new Set());
async function onRefreshCatalog(p: ProviderView) {
  const next = new Set(refreshingCatalog.value);
  next.add(p.name);
  refreshingCatalog.value = next;
  try {
    // Refresh persists discovery before reading the authoritative catalog.
    await refreshCatalog(p.name, props.tenantId);
    await loadCatalog(p.name);
    await refresh();
    emit('change');
  } finally {
    const after = new Set(refreshingCatalog.value);
    after.delete(p.name);
    refreshingCatalog.value = after;
  }
}



const togglingKey = ref<Set<string>>(new Set());
function toggleKey(row: CatalogRow): string {
  return `${row.source}::${row.id}`;
}

/**
 * Dify-parity toggle. Routes by source:
 *   · predefined → PUT /model-providers/{name}/models/{model}/enabled — writes to
 *     agent_provider_model_setting (missing row = enabled).
 *   · custom     → PATCH /models/{id}/enabled — writes to agent_model.enabled.
 * Updates the local catalog row optimistically so the pill flips instantly.
 */
async function onToggleEnabled(providerName: string, row: CatalogRow, next: boolean) {
  const key = toggleKey(row);
  const set = new Set(togglingKey.value);
  set.add(key);
  togglingKey.value = set;
  try {
    if (row.source === 'custom') {
      // Custom (agent_model) row — legacy id-based enable path.
      await setModelEnabled(row.id, next);
    } else {
      // Predefined or remote-fetched — new (provider, model, modelType) path.
      // For 'remote' rows the backend materializes an agent_predefined_model
      // row on enable so the entry survives page reload.
      await setModelEnabledByName(
        providerName,
        row.model,
        row.modelType,
        next,
        props.tenantId,
      );
    }
    // Optimistic update — replace just this row in the cached catalog.
    const list = catalogByProvider.value[providerName] ?? [];
    const patched = list.map((r) =>
      r.id === row.id && r.source === row.source ? { ...r, enabled: next } : r,
    );
    catalogByProvider.value = { ...catalogByProvider.value, [providerName]: patched };
    // Also keep legacy models list in sync for the compact card counters.
    if (row.source === 'custom' && !props.models) {
      localModels.value = localModels.value.map((x) =>
        x.id === row.id ? { ...x, enabled: next } : x,
      );
    }
    // Defaults dropdown pool = enabled catalog rows across all providers, so a
    // toggle can add/remove candidates — refresh so the Select stays in sync.
    await defaultsPanel.value?.refresh();
    emit('change');
  } finally {
    const after = new Set(togglingKey.value);
    after.delete(key);
    togglingKey.value = after;
  }
}

// -------------------------------------------------- manual add
const manualAddOpen = ref(false);
const manualAddProvider = ref<ProviderView | null>(null);
const manualAddForm = reactive({
  modelName: '',
  modelType: 'LLM' as ModelType,
});
const manualAddSaving = ref(false);
const manualAddError = ref<string | null>(null);

function openManualAdd(p: ProviderView) {
  manualAddProvider.value = p;
  manualAddForm.modelName = '';
  manualAddForm.modelType = (p.supportedModelTypes?.[0] as ModelType) ?? 'LLM';
  manualAddError.value = null;
  manualAddOpen.value = true;
}

async function submitManualAdd() {
  if (!manualAddProvider.value) return;
  if (!manualAddForm.modelName.trim()) {
    manualAddError.value = '请填入模型名称';
    return;
  }
  const p = manualAddProvider.value;
  manualAddSaving.value = true;
  manualAddError.value = null;
  try {
    await registerModel({
      tenantId: props.tenantId,
      providerName: p.name,
      modelName: manualAddForm.modelName.trim(),
      modelType: manualAddForm.modelType,
      credentialId: p.credentialId,
      credentials: {},
    });
    manualAddOpen.value = false;
    await refresh();
    emit('change');
  } catch (e: any) {
    message.error(e?.message ?? String(e));
  } finally {
    manualAddSaving.value = false;
  }
}

// -------------------------------------------------- parameter drawer
const drawerOpen = ref(false);
const drawerModel = ref<ModelEntity | null>(null);
const drawerProvider = ref<ProviderView | null>(null);
const defaultsPanel = ref<InstanceType<typeof DefaultModelsPanel> | null>(null);

function openDrawer(m: ModelEntity, p: ProviderView) {
  drawerModel.value = m;
  drawerProvider.value = p;
  drawerOpen.value = true;
}

async function onDrawerSaved() {
  await refresh();
  await defaultsPanel.value?.refresh();
  emit('change');
}

async function onDrawerDeleted() {
  await refresh();
  await defaultsPanel.value?.refresh();
  emit('change');
}

/** Fires after DefaultModelsPanel auto-saves a new default. Sync own state so
 *  the popover's "默认" tag and card counters reflect the change. */
async function onDefaultsChanged() {
  await refresh();
  emit('change');
}

// -------------------------------------------------- feature helpers
function featureLabel(f: string): string {
  if (f === 'VISION') return '视觉';
  if (f === 'TOOL_CALL' || f === 'STREAM_TOOL_CALL') return '工具';
  if (f === 'STRUCTURED_OUTPUT') return 'JSON';
  if (f === 'AGENT_THOUGHT') return '推理';
  return '';
}

/** Feature badges for a catalog row (predefined-sourced or custom). */
function primaryFeaturesForRow(row: CatalogRow): string[] {
  const raw = row.features ?? [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const f of raw) {
    const l = featureLabel(f);
    if (l && !seen.has(l)) {
      seen.add(l);
      out.push(f);
    }
  }
  return out;
}

/**
 * Materialize a catalog row into a ModelEntity-shaped object for the
 * parameter drawer. For custom rows this is trivial (real agent_model id);
 * for predefined rows we synthesize a placeholder — the drawer's save path
 * will need a materialization endpoint later (deferred).
 */
function catalogRowToModelEntity(row: CatalogRow, providerName: string): ModelEntity {
  return {
    id: row.id,
    tenantId: props.tenantId ?? 'default',
    providerName,
    modelName: row.model,
    modelType: row.modelType,
    enabled: row.enabled,
    isDefault: row.isDefault,
    credentialId: row.credentialId,
  };
}

// -------------------------------------------------- card ⋯ dropdown menu
const openMenuFor = ref<string | null>(null);
function toggleCardMenu(providerName: string) {
  closePopover();
  openMenuFor.value = openMenuFor.value === providerName ? null : providerName;
}
function closeCardMenu() {
  openMenuFor.value = null;
}

// -------------------------------------------------- popover filter tabs
type EnabledFilter = 'all' | 'disabled' | 'enabled';
const enabledFilter = ref<EnabledFilter>('all');
const typeFilter = ref<'all' | ModelType | 'VISION'>('all');

function filteredCatalogRows(providerName: string): CatalogRow[] {
  const rows = catalogRows(providerName);
  return rows.filter((r) => {
    if (enabledFilter.value === 'enabled' && !r.enabled) return false;
    if (enabledFilter.value === 'disabled' && r.enabled) return false;
    if (typeFilter.value === 'VISION') {
      if (!r.features?.includes('VISION')) return false;
    } else if (typeFilter.value !== 'all' && r.modelType !== typeFilter.value) return false;
    return true;
  });
}

function groupFilteredByType(providerName: string): Record<string, CatalogRow[]> {
  const acc: Record<string, CatalogRow[]> = {};
  for (const r of filteredCatalogRows(providerName)) {
    (acc[r.modelType] ??= []).push(r);
  }
  for (const key of Object.keys(acc)) {
    acc[key]!.sort((a, b) => a.model.localeCompare(b.model));
  }
  return acc;
}

function availableTypesInCatalog(providerName: string): Array<ModelType | 'VISION'> {
  const set = new Set<ModelType | 'VISION'>();
  for (const r of catalogRows(providerName)) {
    set.add(r.modelType);
    if (r.features?.includes('VISION')) set.add('VISION');
  }
  return [...set];
}

function countBy(providerName: string, kind: EnabledFilter): number {
  const rows = catalogRows(providerName);
  if (kind === 'all') return rows.length;
  return rows.filter((r) => (kind === 'enabled' ? r.enabled : !r.enabled)).length;
}

function resetFilters() {
  enabledFilter.value = 'all';
  typeFilter.value = 'all';
}

watch(popoverProvider, () => resetFilters());

// -------------------------------------------------- row display helpers
function modelTypeIcon(t: ModelType | string): string {
  if (t === 'VISION') return '👁';
  if (t === 'VIDEO') return '🎬';
  if (t === 'IMAGE') return '🖼';
  if (t === 'LLM') return '💬';
  if (t === 'TEXT_EMBEDDING') return '🧮';
  if (t === 'RERANK') return '🔀';
  if (t === 'SPEECH2TEXT') return '🎤';
  if (t === 'TTS') return '🔊';
  if (t === 'MODERATION') return '🛡';
  return '🤖';
}

function modelTypeTagStyle(type: string) {
  const colors: Record<string, string> = {
    LLM: '#2563eb', TEXT_EMBEDDING: '#059669', RERANK: '#d97706',
    VISION: '#9333ea', VIDEO: '#7c3aed', IMAGE: '#db2777', SPEECH2TEXT: '#0891b2',
    TTS: '#4f46e5', MODERATION: '#64748b',
  };
  const color = colors[type] ?? '#64748b';
  return {
    '--phs-type-color': color,
    color: `color-mix(in srgb, ${color} 65%, var(--foreground))`,
    background: `color-mix(in srgb, ${color} 12%, var(--background))`,
  };
}

/**
 * Format a context window size like Dify does: 1024→"1K", 128000→"128K",
 * 1000000→"1M". Returns without units when < 1000 (rare for context windows).
 */
function formatContext(n?: number): string {
  if (!n || n <= 0) return '';
  if (n >= 1_000_000) {
    const m = n / 1_000_000;
    return `${Math.round(m * 10) / 10}M`;
  }
  if (n >= 1000) {
    const k = Math.round(n / 1000);
    return `${k}K`;
  }
  return String(n);
}

/**
 * Compact capability chips shown next to the model name (Dify parity: CHAT /
 * Vision / etc.). Distinct from the "features" pillbox — we add a few
 * type-inferred defaults on top so all LLMs get "CHAT" without needing every
 * predefined row to carry it.
 */
function capabilityTags(
  row: CatalogRow,
): { color: string; key: string; label: string }[] {
  const out: { color: string; key: string; label: string }[] = [];
  // LLMs default to CHAT mode (Dify convention). If the row explicitly declares
  // COMPLETION or something else via features, we'd render that too.
  if (row.modelType === 'LLM') {
    out.push({ key: 'CHAT', label: 'CHAT', color: 'blue' });
  }
  const seen = new Set(out.map((t) => t.key));
  for (const f of row.features ?? []) {
    if (f === 'VISION' && !seen.has('VISION')) {
      out.push({ key: 'VISION', label: '视觉理解 · Vision', color: 'purple' });
      seen.add('VISION');
    } else if (
      (f === 'TOOL_CALL' || f === 'STREAM_TOOL_CALL') &&
      !seen.has('TOOL')
    ) {
      out.push({ key: 'TOOL', label: 'Tools', color: 'green' });
      seen.add('TOOL');
    } else if (f === 'STRUCTURED_OUTPUT' && !seen.has('JSON')) {
      out.push({ key: 'JSON', label: 'JSON', color: 'orange' });
      seen.add('JSON');
    }
  }
  return out;
}
</script>

<template>
  <div class="phs-root" :aria-busy="busy">
    <div class="phs-content" :inert="busy">
    <!-- 页面头部：使用共用样式类 -->
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
            <rect x="4" y="4" width="16" height="16" rx="2" />
            <rect x="9" y="9" width="6" height="6" />
            <path d="M9 2v2" />
            <path d="M15 2v2" />
            <path d="M9 20v2" />
            <path d="M15 20v2" />
            <path d="M2 9h2" />
            <path d="M2 15h2" />
            <path d="M20 9h2" />
            <path d="M20 15h2" />
          </svg>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">模型供应商</div>
          <div class="as-page-subtitle">
            接入与管理模型供应商 · 凭证 · 模型目录 · 默认模型
          </div>
        </div>
      </div>
      <div class="as-page-header-controls">
        <Button type="primary" :loading="syncing" :disabled="busy" @click="synchronizeCatalog">
          {{ syncing ? '同步中…' : '同步预置目录' }}
        </Button>
        <div class="phs-search-wrap">
          <svg
            class="phs-search-icon"
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
          <AsShadcnInput
            v-model="search"
            class="phs-search"
            placeholder="搜索供应商"
          />
        </div>
      </div>
    </div>

    <!-- Section 0 · 系统默认模型 -->
    <DefaultModelsPanel
      ref="defaultsPanel"
      :tenant-id="tenantId"
      @change="onDefaultsChanged"
    />

    <!-- 模型列表：已配置供应商，包括尚未添加模型的供应商。 -->
    <div v-if="configured.length > 0" class="phs-section">
      <div class="phs-section-title">模型列表</div>
      <div class="phs-configured-grid">
        <Card variant="management"
          v-for="p in configured"
          :key="p.name"
          class="phs-compact-card"
          body-class="flex flex-1 flex-col gap-3"
          :title="p.label"
          :subtitle="p.name"
          :interactive="false"
          compact
          :footer-divider="false"
        >
          <template #icon>
            <ProviderIcon
              :name="p.name"
              :size="32"
              v-bind="providerIconProps(p)"
            />
          </template>

          <template #badge>
            <Popover
              :open="openMenuFor === p.name"
              trigger="click"
              placement="bottomRight"
              overlay-class-name="phs-card-menu-overlay"
              @update:open="(v: boolean) => (v ? openMenuFor = p.name : closeCardMenu())"
            >
              <AsShadcnButton variant="ghost"
                type="button"
                class="phs-card-menu-btn"
                :class="{ 'phs-card-menu-btn--open': openMenuFor === p.name }"
                title="更多操作"
                :aria-label="`${p.label} 更多操作`"
                :aria-expanded="openMenuFor === p.name"
                @click.stop="toggleCardMenu(p.name)"
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
              </AsShadcnButton>
              <template #content>
                <div class="phs-card-menu-content" role="menu">
                  <AsShadcnButton variant="ghost"
                    v-if="p.supportsRemoteModelListing"
                    type="button"
                    role="menuitem"
                    class="phs-card-menu-item"
                    @click="onRefreshCatalog(p); closeCardMenu()"
                  >
                    <svg
                      class="phs-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" />
                      <path d="M21 3v5h-5" />
                    </svg>
                    重新拉取模型
                  </AsShadcnButton>
                  <AsShadcnButton variant="ghost"
                    type="button"
                    role="menuitem"
                    class="phs-card-menu-item"
                    @click="openCredentialModal(p, true); closeCardMenu()"
                  >
                    <svg
                      class="phs-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <circle cx="7.5" cy="15.5" r="4.5" />
                      <path d="m20.5 3.5-9 9" />
                      <path d="m15 7 3 3 3-3.5-3-3" />
                    </svg>
                    编辑凭证
                  </AsShadcnButton>
                  <AsShadcnButton variant="ghost"
                    type="button"
                    role="menuitem"
                    class="phs-card-menu-item"
                    @click="openManualAdd(p); closeCardMenu()"
                  >
                    <svg
                      class="phs-card-menu-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      aria-hidden="true"
                    >
                      <circle cx="12" cy="12" r="9" />
                      <path d="M12 8v8" />
                      <path d="M8 12h8" />
                    </svg>
                    手动添加模型
                  </AsShadcnButton>
                  <div class="phs-card-menu-sep" role="separator" />
                  <AsShadcnButton variant="ghost"
                    type="button"
                    role="menuitem"
                    class="phs-card-menu-item phs-card-menu-item--danger"
                    @click="clearCredential(p); closeCardMenu()"
                  >
                    <svg
                      class="phs-card-menu-icon"
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
                    删除凭证
                  </AsShadcnButton>
                </div>
              </template>
            </Popover>
          </template>
          <div class="phs-compact-caps">
            <span
              v-for="t in distinctModelTypes(p)"
              :key="t"
              class="phs-cap"
              :data-color="modelTypeColor(t)"
            >
              {{ modelTypeLabel(t) }}
            </span>
          </div>

          <Modal
            :open="popoverProvider?.name === p.name"
            :width="800"
            centered
            class="phs-model-modal"
            :title="`${p.label} · 模型管理`"
            @cancel="closePopover"
          >
            <div class="phs-popover-inner">
              <div class="phs-catalog-toolbar">
                <span class="phs-catalog-summary">
                  <strong>{{ enabledCount(p) }}</strong> 已启用 · 共
                  {{ catalogByProvider[p.name] ? catalogRows(p.name).length : knownModelCount(p) }} 个模型
                </span>
                <div class="phs-popover-actions">
                  <Button v-if="p.supportsRemoteModelListing" size="small" :loading="refreshingCatalog.has(p.name)" @click="onRefreshCatalog(p)">
                    <span aria-hidden="true">↻</span> 重新拉取
                  </Button>
                  <Button type="primary" size="small" @click="openManualAdd(p)">
                    <span aria-hidden="true">＋</span> 手动添加
                  </Button>
                </div>
              </div>
                <div class="phs-filter-bar">
                  <div class="phs-filter-group">
                    <AsShadcnButton variant="ghost"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'all' }"
                      :aria-pressed="enabledFilter === 'all'"
                      @click="enabledFilter = 'all'"
                    >
                      全部 <span class="phs-filter-count">{{ countBy(p.name, 'all') }}</span>
                    </AsShadcnButton>
                    <AsShadcnButton variant="ghost"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'enabled' }"
                      :aria-pressed="enabledFilter === 'enabled'"
                      @click="enabledFilter = 'enabled'"
                    >
                      已启用 <span class="phs-filter-count">{{ countBy(p.name, 'enabled') }}</span>
                    </AsShadcnButton>
                    <AsShadcnButton variant="ghost"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'disabled' }"
                      :aria-pressed="enabledFilter === 'disabled'"
                      @click="enabledFilter = 'disabled'"
                    >
                      未启用 <span class="phs-filter-count">{{ countBy(p.name, 'disabled') }}</span>
                    </AsShadcnButton>
                  </div>
                  <div class="phs-filter-group phs-type-filters">
                    <AsShadcnButton variant="ghost"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': typeFilter === 'all' }"
                      @click="typeFilter = 'all'"
                    >
                      <span aria-hidden="true">▦</span> 所有类型
                    </AsShadcnButton>
                    <AsShadcnButton variant="ghost"
                      v-for="t in availableTypesInCatalog(p.name)"
                      :key="t"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': typeFilter === t }"
                      :style="modelTypeTagStyle(t)"
                      :aria-pressed="typeFilter === t"
                      @click="typeFilter = t"
                    >
                      <span aria-hidden="true">{{ modelTypeIcon(t) }}</span> {{ modelTypeLabel(t) }}
                    </AsShadcnButton>
                  </div>
                </div>

                <div class="phs-popover-body">
                  <div v-if="catalogLoading.has(p.name)" class="phs-popover-empty">
                    加载目录中…
                  </div>
                  <template v-else-if="filteredCatalogRows(p.name).length === 0">
                    <div class="phs-popover-empty">
                      <template v-if="catalogRows(p.name).length === 0">
                        目录为空 —
                        {{
                          p.supportsRemoteModelListing
                            ? '点击 "重新拉取" 从供应商获取列表'
                            : '点击 "手动添加" 录入具体模型名称'
                        }}
                      </template>
                      <template v-else>
                        当前过滤条件下没有匹配的模型
                      </template>
                    </div>
                  </template>
                  <template v-else>
                    <div class="phs-popover-list">
                      <div
                        v-for="row in filteredCatalogRows(p.name)"
                        :key="`${row.source}::${row.id}`"
                        class="phs-row"
                        :class="{ 'phs-row--on': row.enabled }"
                      >
                        <span
                          class="phs-row-icon"
                          :data-color="modelTypeColor(row.modelType)"
                          :style="modelTypeTagStyle(row.modelType)"
                          :title="modelTypeLabel(row.modelType)"
                        >
                          {{ modelTypeIcon(row.modelType) }}
                        </span>
                        <span class="phs-row-name" :title="row.model">
                          {{ row.model }}
                        </span>
                        <span
                          class="phs-row-cap"
                          :data-color="modelTypeColor(row.modelType)"
                          :style="modelTypeTagStyle(row.modelType)"
                        >
                          {{ modelTypeLabel(row.modelType) }}
                        </span>
                        <span
                          v-for="c in capabilityTags(row)"
                          :key="c.key"
                          class="phs-row-cap"
                          :data-color="c.color"
                        >
                          {{ c.label }}
                        </span>
                        <span
                          v-if="row.contextLength"
                          class="phs-row-cap phs-row-cap--ctx"
                        >
                          {{ formatContext(row.contextLength) }}
                        </span>
                        <span
                          v-if="row.source === 'remote'"
                          class="phs-row-cap phs-row-cap--remote"
                          title="从供应商 /models 接口新拉取的模型 · 启用后才写入数据库"
                        >
                          新增
                        </span>
                        <span
                          v-if="row.source === 'custom'"
                          class="phs-row-cap phs-row-cap--custom"
                        >
                          自定义
                        </span>
                        <span
                          v-if="row.isDefault"
                          class="phs-row-cap phs-row-cap--default"
                        >
                          默认
                        </span>
                        <AsShadcnButton variant="ghost"
                          v-if="row.source === 'custom'"
                          class="phs-row-gear"
                          title="参数 / 设为默认 / 删除"
                          @click.stop="openDrawer(catalogRowToModelEntity(row, p.name), p)"
                        >
                          ⚙
                        </AsShadcnButton>
                        <label class="inline-flex items-center">
                          <AsShadcnBoolean
                            type="checkbox"
                            :checked="row.enabled"
                            :disabled="togglingKey.has(toggleKey(row))"
                            @change="(e) => onToggleEnabled(p.name, row, (e.target as HTMLInputElement).checked)"
                          />

                        </label>
                      </div>
                    </div>
                  </template>
                </div>

            </div>
            <template #footer>
              <div class="phs-modal-footer">
                <Button @click="openCredentialModal(p, true)">编辑凭证</Button>
                <Button danger @click="clearCredential(p)">删除凭证</Button>
                <Button class="phs-modal-done" @click="closePopover">完成</Button>
              </div>
            </template>
          </Modal>

            <AsShadcnButton variant="ghost"
              class="phs-count-btn"
              title="点击查看模型列表"
              @click.stop="popoverProvider?.name === p.name ? closePopover() : openPopover(p)"
              :aria-expanded="popoverProvider?.name === p.name"
            >
              <span class="phs-count-enabled">{{ enabledCount(p) }}</span>
              <span class="phs-count-label">已启用</span>
              <span class="phs-count-total">共 {{ catalogByProvider[p.name] ? catalogRows(p.name).length : knownModelCount(p) }} 个模型</span>
              <span class="phs-count-arrow" aria-hidden="true">→</span>
            </AsShadcnButton>
        </Card>
      </div>
    </div>

    <!-- Section 3 · 安装模型供应商 -->
    <div v-if="installable.length > 0" class="phs-section">
      <div class="phs-section-title">已支持模型供应商</div>
      <div class="phs-installable-grid">
        <Card variant="management"
          v-for="p in installable"
          :key="p.name"
          class="phs-installable-card"
          :title="p.label"
          :subtitle="p.name"
          :interactive="false"
          compact
        >
          <template #icon>
            <ProviderIcon
              :name="p.name"
              :size="32"
              v-bind="providerIconProps(p)"
            />
          </template>

          <template #badge>
            <AsShadcnButton variant="ghost"
              type="button"
              class="phs-installable-config"
              :title="p.name === 'openai-compatible' ? '配置自定义接口' : p.credentialConfigured ? '管理配置' : '配置供应商'"
              :aria-label="`配置 ${p.label}`"
              @click="openCredentialModal(p)"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="m9.5 3-.6 2.2-1.7 1L5 5.7 2.5 10l1.6 1.6v1.8L2.5 15 5 19.3l2.2-.5 1.7 1 .6 2.2h5l.6-2.2 1.7-1 2.2.5 2.5-4.3-1.6-1.6v-1.8L21.5 10 19 5.7l-2.2.5-1.7-1L14.5 3z" />
                <circle cx="12" cy="12.5" r="3" />
              </svg>
            </AsShadcnButton>
          </template>

          <div class="phs-installable-caps">
            <span
              v-for="t in p.supportedModelTypes"
              :key="t"
              class="phs-cap"
              :data-color="modelTypeColor(t)"
            >
              {{ modelTypeLabel(t) }}
            </span>
          </div>
        </Card>
      </div>
    </div>

    <div v-if="!busy && providers.length === 0" class="phs-empty">
      没有可用的供应商
    </div>

    <!-- Catalog popover is now rendered inline per-card via <Popover>. -->


    <ProviderCredentialModal
      v-model:open="modalOpen"
      :provider="modalProvider"
      :edit-mode="modalEditMode"
      :tenant-id="tenantId"
      @saved="onCredentialSaved"
    />

    <ModelParameterDrawer
      v-model:open="drawerOpen"
      :model="drawerModel"
      :provider="drawerProvider"
      @saved="onDrawerSaved"
      @deleted="onDrawerDeleted"
      @defaulted="onDrawerSaved"
    />

    <!-- Manual add mini-modal -->
    <Modal
      v-model:open="manualAddOpen"
      class="phs-mini-modal"
      centered
      :width="520"
      :title="`手动添加模型 · ${manualAddProvider?.label ?? ''}`"
      :mask-closable="!manualAddSaving"
      :keyboard="!manualAddSaving"
      :closable="!manualAddSaving"
    >
        <div class="phs-mini-body">
          <div class="phs-mini-hint">
            用于目录里没有的自定义模型（自建 endpoint / 未公开的模型名）。
          </div>
          <div class="phs-mini-field">
            <label>模型名称 <span class="phs-req">*</span></label>
            <AsShadcnInput
              v-model="manualAddForm.modelName"
              class="phs-mini-input"
              placeholder="例如 gpt-4o-2025-preview"
            />
          </div>
          <div class="phs-mini-field">
            <label>模型类型</label>
            <AsShadcnSelect v-model="manualAddForm.modelType" class="phs-mini-input">
              <option
                v-for="t in manualAddProvider?.supportedModelTypes ?? []"
                :key="t"
                :value="t"
              >
                {{ modelTypeLabel(t) }}
              </option>
            </AsShadcnSelect>
          </div>
          <UiNotice type="error" v-if="manualAddError" class="phs-mini-error">
            {{ manualAddError }}
          </UiNotice>
        </div>
        <template #footer>
          <Button @click="manualAddOpen = false">取消</Button>
          <Button
            type="primary"
            :loading="manualAddSaving"
            @click="submitManualAdd"
          >
            添加
          </Button>
        </template>
    </Modal>
    </div>
    <div v-if="busy" class="phs-loading-overlay">
      <LoadingCard class="phs-loading-card" role="status" aria-live="polite" aria-atomic="true">
        <Spinner class="size-5 text-primary" aria-hidden="true" role="presentation" />
        <span>{{ syncing ? '正在同步预置目录…' : '正在加载供应商…' }}</span>
      </LoadingCard>
    </div>
  </div>
</template>

<style scoped>
.phs-root {
  position: relative;
  min-height: 320px;
  container: provider-hub / inline-size;
  display: flex;
  flex-direction: column;
  gap: 20px;
  /* Full-page surface: light top / left / right margins only — the content
   * still spans the full host width. */
  box-sizing: border-box;
  padding: 20px 24px;
}
@media (max-width: 640px) {
  .phs-root {
    padding: 12px 16px;
  }
}
.phs-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.phs-loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--background) 75%, transparent);
  border-radius: var(--radius);
  cursor: wait;
}
.phs-loading-card {
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 16px 24px;
  font-size: 13px;
}
/* 搜索框样式 */
.phs-search-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.phs-search-icon {
  position: absolute;
  left: 10px;
  color: var(--muted-foreground);
  pointer-events: none;
}
.phs-search {
  width: 240px;
  max-width: 100%;
  height: 36px;
  padding: 0 12px 0 32px;
  font-size: 13px;
  border: 1px solid var(--input);
  border-radius: 8px;
  background: transparent;
  color: var(--foreground);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.phs-search::placeholder {
  color: var(--muted-foreground);
}
.phs-search:focus {
  outline: none;
  border-color: var(--ring);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ring) 25%, transparent);
}
.phs-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.phs-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--foreground);
  margin-bottom: 4px;
}

/* -------- compact configured providers -------- */
.phs-configured-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}






.phs-compact-card:deep(.as-popover) { display: block; width: 100%; }
.phs-compact-caps { display: flex; gap: 4px; flex-wrap: wrap; }
.phs-count-btn {
  margin-top: auto;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  border: 1px solid var(--as-mgmt-line, #eaecf0);
  border-radius: 6px;
  background: var(--as-bg-layout, #f8fafc);
  color: var(--as-text-secondary, #667085);
  font-size: 11px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.phs-count-btn:hover {
  border-color: var(--as-primary, var(--primary));
  background: color-mix(in srgb, var(--as-primary, var(--primary)) 10%, var(--as-bg, #fff));
}
.phs-count-btn:focus-visible { outline: 2px solid var(--as-primary, var(--primary)); outline-offset: 2px; }
.phs-count-enabled { font-weight: 600; color: var(--as-success, #059669); font-size: 12px; }
.phs-count-total { margin-left: auto; }
.phs-count-arrow { margin-left: 2px; }
/* antd Dropdown 观感：容器 padding 4px、菜单项 5px 12px、行高 22px 的紧凑尺寸，
   字号按本产品管理页的 12px 密度收敛。 */
.phs-card-menu-content {
  /* 面板经 Teleport 挂在 body 下，继承不到 .as-management 容器上的
     --as-mgmt-* 令牌；这里用可随暗色翻转的全局令牌自建局部变量中枢。 */
  --phs-menu-text: var(--as-text, #374151);
  --phs-menu-muted: var(--as-text-secondary, #98a2b3);
  --phs-menu-line: var(--as-border-secondary, #e5e7eb);
  min-width: 160px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
}

/* ⋯ menu on each card */
.phs-card-menu-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--muted-foreground);
  cursor: pointer;
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 8px;
  line-height: 1;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.phs-card-menu-btn:hover {
  background: var(--as-mgmt-soft, #f3f4f6);
  color: var(--as-mgmt-text, #374151);
}
.phs-card-menu-btn--open {
  background: color-mix(in srgb, var(--as-primary, var(--primary)) 10%, transparent);
  color: var(--as-primary, var(--primary));
}
.phs-card-menu-btn:focus-visible {
  outline: 2px solid var(--as-primary, var(--primary));
  outline-offset: 1px;
}

.phs-card-menu-item {
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
  color: var(--phs-menu-text);
  transition: background 0.15s ease, color 0.15s ease;
}
.phs-card-menu-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--phs-menu-muted);
  transition: color 0.15s ease;
}
.phs-card-menu-item:hover {
  background: color-mix(in srgb, var(--as-primary, #6366f1) 8%, transparent);
  color: var(--as-primary, #6366f1);
}
.phs-card-menu-item:hover .phs-card-menu-icon {
  color: var(--as-primary, #6366f1);
}
.phs-card-menu-item:active {
  background: color-mix(in srgb, var(--as-primary, var(--primary)) 14%, transparent);
}
.phs-card-menu-item:focus-visible {
  outline: 2px solid var(--as-primary, var(--primary));
  outline-offset: -2px;
}
.phs-card-menu-item--danger,
.phs-card-menu-item--danger .phs-card-menu-icon {
  color: var(--as-danger, #dc2626);
}
.phs-card-menu-item--danger:hover {
  background: color-mix(in srgb, var(--as-danger, #dc2626) 8%, transparent);
  color: var(--as-danger, #dc2626);
}
.phs-card-menu-item--danger:hover .phs-card-menu-icon {
  color: var(--as-danger, #dc2626);
}
.phs-card-menu-item--danger:active {
  background: color-mix(in srgb, var(--as-danger, #dc2626) 14%, transparent);
}
.phs-card-menu-item--danger:focus-visible {
  outline-color: var(--as-danger, #dc2626);
}
.phs-card-menu-sep {
  height: 1px;
  background: var(--phs-menu-line);
  margin: 4px 6px;
}

/* Popover filter tab bar */
.phs-filter-bar {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.phs-filter-group {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.phs-filter-tab {
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--foreground);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
.phs-filter-tab:hover {
  background: var(--accent);
  color: var(--accent-foreground);
}
.phs-filter-tab--on {
  background: color-mix(in srgb, var(--primary) 10%, var(--background));
  border-color: var(--primary);
  color: var(--primary);
  font-weight: 500;
}
.phs-filter-count {
  background: var(--background);
  padding: 0 5px;
  border-radius: 999px;
  font-size: 10.5px;
  color: var(--muted-foreground);
  min-width: 18px;
  text-align: center;
}
.phs-filter-tab--on .phs-filter-count {
  background: var(--background);
  color: var(--primary);
}


.phs-cap {
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  color: color-mix(in srgb, #4338ca 65%, var(--foreground));
  letter-spacing: 0.5px;
}
.phs-cap[data-color='green'] {
  background: color-mix(in srgb, #059669 12%, var(--background));
  color: color-mix(in srgb, #059669 65%, var(--foreground));
}
.phs-cap[data-color='orange'] {
  background: color-mix(in srgb, #c2410c 12%, var(--background));
  color: color-mix(in srgb, #c2410c 65%, var(--foreground));
}
.phs-cap[data-color='purple'] {
  background: color-mix(in srgb, #7c3aed 12%, var(--background));
  color: color-mix(in srgb, #7c3aed 65%, var(--foreground));
}

.phs-btn {
  padding: 5px 12px;
  font-size: 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--background);
  color: var(--foreground);
  cursor: pointer;
  white-space: nowrap;
}
.phs-btn:hover {
  background: var(--muted);
}
.phs-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.phs-btn-ghost {
  background: transparent;
}
.phs-btn-primary {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.phs-btn-primary:hover {
  background: var(--primary);
}
.phs-btn-danger {
  color: var(--destructive);
}
.phs-btn-danger:hover {
  background: color-mix(in srgb, var(--destructive) 10%, var(--background));
}

/* -------- compact provider catalog -------- */
.phs-installable-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}





.phs-installable-config {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--as-mgmt-muted, #6b7280);
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}
.phs-installable-config:hover {
  background: var(--as-primary-soft, #eef2ff);
  color: var(--as-primary, #4f46e5);
}
.phs-installable-config:focus-visible {
  outline: 2px solid var(--as-primary, var(--primary));
  outline-offset: 2px;
}
.phs-installable-caps {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
@container provider-hub (max-width: 1099px) {
  .phs-configured-grid, .phs-installable-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@container provider-hub (max-width: 879px) {
  .phs-configured-grid, .phs-installable-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@container provider-hub (max-width: 659px) {
  .phs-configured-grid, .phs-installable-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@container provider-hub (max-width: 439px) {
  .phs-configured-grid, .phs-installable-grid { grid-template-columns: minmax(0, 1fr); }
}

.phs-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--muted-foreground);
  font-size: 13px;
}

/* Model manager uses the shared Modal chrome and footer. */
:global(.phs-card-menu-overlay) {
  width: max-content;
  min-width: 0;
  max-width: calc(100vw - 16px);
  padding: 6px;
}
.phs-popover-inner { display: flex; flex-direction: column; min-width: 0; }
.phs-catalog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding-bottom: 14px;
}
.phs-catalog-summary { font-size: 12px; color: var(--as-text-secondary, #667085); }
.phs-catalog-summary strong { color: color-mix(in srgb, #059669 65%, var(--foreground)); font-size: 15px; }
.phs-popover-actions { display: flex; align-items: center; gap: 8px; }
.phs-popover-body {
  height: min(52vh, 520px);
  flex-shrink: 0;
  box-sizing: border-box;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding-top: 10px;
}
.phs-popover-empty { padding: 40px 12px; text-align: center; color: var(--as-text-secondary, #667085); font-size: 13px; }
.phs-popover-list { display: flex; flex-direction: column; gap: 4px; }
.phs-modal-footer { display: flex; gap: 8px; width: 100%; }
.phs-modal-done { margin-left: auto; }
.phs-type-filters .phs-filter-tab { border-radius: 999px; padding: 4px 10px; }
.phs-type-filters .phs-filter-tab--on {
  border-color: var(--phs-type-color, var(--primary));
  box-shadow: 0 0 0 1px var(--phs-type-color, var(--primary));
}
.phs-filter-tab:focus-visible { outline: 2px solid var(--phs-type-color, var(--primary)); outline-offset: 2px; }

/* Row: single-line — icon | name (flex:1) | tags | gear | switch */
.phs-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 4px 8px;
  background: var(--background);
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
  overflow: hidden;
}
.phs-row:hover {
  background: var(--muted);
  border-color: var(--border);
}
.phs-row--on {
  background: color-mix(in srgb, var(--primary) 7%, var(--background));
  border-color: color-mix(in srgb, var(--primary) 25%, var(--border));
  box-shadow: inset 2px 0 0 var(--primary);
}
.phs-row-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  color: color-mix(in srgb, #4338ca 65%, var(--foreground));
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.phs-row-icon[data-color='green'] {
  background: color-mix(in srgb, #059669 12%, var(--background));
  color: color-mix(in srgb, #059669 65%, var(--foreground));
}
.phs-row-icon[data-color='orange'] {
  background: color-mix(in srgb, #c2410c 12%, var(--background));
  color: color-mix(in srgb, #c2410c 65%, var(--foreground));
}
.phs-row-icon[data-color='purple'] {
  background: color-mix(in srgb, #7c3aed 12%, var(--background));
  color: color-mix(in srgb, #7c3aed 65%, var(--foreground));
}
.phs-row-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--foreground);
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.phs-row-cap {
  flex-shrink: 0;
  padding: 1px 5px;
  font-size: 9.5px;
  font-weight: 500;
  border-radius: 4px;
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  color: color-mix(in srgb, #4338ca 65%, var(--foreground));
  letter-spacing: 0.1px;
  white-space: nowrap;
}
.phs-row-cap[data-color='green'] {
  background: color-mix(in srgb, #059669 12%, var(--background));
  color: color-mix(in srgb, #059669 65%, var(--foreground));
}
.phs-row-cap[data-color='orange'] {
  background: color-mix(in srgb, #c2410c 12%, var(--background));
  color: color-mix(in srgb, #c2410c 65%, var(--foreground));
}
.phs-row-cap[data-color='purple'] {
  background: color-mix(in srgb, #7c3aed 12%, var(--background));
  color: color-mix(in srgb, #7c3aed 65%, var(--foreground));
}
.phs-row-cap[data-color='blue'] {
  background: color-mix(in srgb, #2563eb 12%, var(--background));
  color: color-mix(in srgb, #1e40af 65%, var(--foreground));
}
.phs-row-cap--ctx {
  background: var(--muted);
  color: var(--muted-foreground);
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
}
.phs-row-cap--custom {
  background: color-mix(in srgb, #b45309 12%, var(--background));
  color: color-mix(in srgb, #b45309 65%, var(--foreground));
}
.phs-row-cap--remote {
  background: color-mix(in srgb, #15803d 12%, var(--background));
  color: color-mix(in srgb, #15803d 65%, var(--foreground));
}
.phs-row-cap--default {
  background: color-mix(in srgb, #2563eb 12%, var(--background));
  color: color-mix(in srgb, #1e40af 65%, var(--foreground));
}
.phs-row-gear {
  background: transparent;
  border: none;
  color: var(--muted-foreground);
  cursor: pointer;
  width: 24px;
  height: 24px;
  font-size: 12px;
  padding: 0;
  border-radius: 4px;
  flex-shrink: 0;
}
.phs-row-gear:hover {
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  color: color-mix(in srgb, #6366f1 65%, var(--foreground));
}

/* Pill row — legacy compact list style, kept for potential future use */
.phs-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--muted);
  border: 1px solid transparent;
  border-radius: 999px;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.phs-pill:hover {
  background: var(--muted);
}
.phs-pill--on {
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  border-color: #a5b4fc;
}
.phs-pill-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  color: var(--foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.phs-pill-meta {
  display: flex;
  gap: 4px;
}
.phs-pill-badge {
  padding: 0 6px;
  font-size: 10px;
  border-radius: 999px;
  background: color-mix(in srgb, #2563eb 12%, var(--background));
  color: color-mix(in srgb, #1e40af 65%, var(--foreground));
}
.phs-pill-badge--default {
  background: color-mix(in srgb, #2563eb 12%, var(--background));
  color: color-mix(in srgb, #1e40af 65%, var(--foreground));
}
.phs-pill-badge--feat {
  background: color-mix(in srgb, #6d28d9 12%, var(--background));
  color: color-mix(in srgb, #6d28d9 65%, var(--foreground));
}
.phs-pill-badge--custom {
  background: color-mix(in srgb, #b45309 12%, var(--background));
  color: color-mix(in srgb, #b45309 65%, var(--foreground));
}
.phs-pill-badge--remote {
  background: color-mix(in srgb, #15803d 12%, var(--background));
  color: color-mix(in srgb, #15803d 65%, var(--foreground));
}
.phs-pill-gear {
  background: transparent;
  border: none;
  color: var(--muted-foreground);
  cursor: pointer;
  font-size: 13px;
  padding: 0 4px;
  border-radius: 4px;
  flex-shrink: 0;
}
.phs-pill-gear:hover {
  background: color-mix(in srgb, var(--primary) 12%, var(--background));
  color: color-mix(in srgb, #6366f1 65%, var(--foreground));
}

/* toggle switch */
.phs-switch {
  position: relative;
  display: inline-block;
  width: 28px;
  height: 16px;
  flex-shrink: 0;
}
.phs-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

@media (max-width: 560px) {
  .phs-row-cap--ctx,
  .phs-row-cap[data-color='blue'] {
    display: none;
  }
}

/* -------- manual add mini-modal -------- */
.phs-mini-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.phs-mini-modal {
  width: 420px;
  max-width: 92vw;
  background: var(--background);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
}
.phs-mini-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.phs-mini-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--foreground);
}
.phs-mini-close {
  background: transparent;
  border: none;
  font-size: 16px;
  color: var(--muted-foreground);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.phs-mini-close:hover {
  background: var(--muted);
}
.phs-mini-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.phs-mini-hint {
  font-size: 12px;
  color: var(--muted-foreground);
  line-height: 1.5;
}
.phs-mini-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.phs-mini-field label {
  font-size: 12px;
  color: var(--foreground);
  font-weight: 500;
}
.phs-mini-input {
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
  color: var(--foreground);
}
.phs-mini-input:focus {
  outline: none;
  border-color: var(--primary);
}
.phs-mini-error {
  padding: 6px 10px;
  background: color-mix(in srgb, var(--destructive) 10%, var(--background));
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  font-size: 12px;
}
.phs-req {
  color: var(--destructive);
}

</style>
