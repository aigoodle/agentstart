<script setup lang="ts">
/**
 * ProviderHubShell — Dify-parity three-section settings page.
 *
 * Section 1 · 模型列表      : compact card grid (~5 per row, responsive). Click a
 *                              card → slide-in right drawer shows the catalog
 *                              grouped by model type, each entry a scrollable
 *                              pill row with an enable/disable toggle. Header
 *                              actions live in the drawer: 重新拉取 · 手动添加 ·
 *                              编辑凭证 · 删除凭证.
 * Section 2 · 待配置        : providers with credential but empty catalog.
 * Section 3 · 安装模型供应商 : providers without credentials — catalog grid.
 *
 * Catalog display cost: each provider row is a fixed-height scrollable panel, so
 * a provider with 100+ models doesn't stretch the page — the shell stays scannable.
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

const models = computed(() => props.models ?? localModels.value);

/**
 * Lazily-loaded catalog per provider. Populated by the new Dify-parity
 * /catalog endpoint on demand (openPopover) — that endpoint returns the
 * union of predefined DB rows + custom agent_model rows with the tenant's
 * enable/default state pre-joined. Legacy `models` list stays around as a
 * fallback for the compact top-card counts and pending-section derivation.
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
 * models. Used ONLY for the pending/configured section split and the top
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
  providers.value.filter(
    (p) => p.credentialConfigured && knownModelCount(p) > 0,
  ),
);
const pending = computed(() =>
  providers.value.filter(
    (p) => p.credentialConfigured && knownModelCount(p) === 0,
  ),
);
const installable = computed(() => {
  const q = search.value.trim().toLowerCase();
  return providers.value
    .filter((p) => !p.credentialConfigured)
    .filter(
      (p) =>
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.label.toLowerCase().includes(q),
    )
    .sort((a, b) => a.label.localeCompare(b.label));
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
    for (const r of rows) set.add(r.modelType);
    return [...set];
  }
  // Fallback to what the provider advertises via predefined models.
  const set = new Set<string>();
  for (const pd of p.predefinedModels ?? []) set.add(pd.modelType);
  return [...set];
}

// -------------------------------------------------- credential modal
const modalOpen = ref(false);
const modalProvider = ref<ProviderView | null>(null);
const modalEditMode = ref(false);

function openCredentialModal(p: ProviderView, editMode = false) {
  modalProvider.value = p;
  modalEditMode.value = editMode;
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
    !confirm(
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
 * Drop every provider-hub client-side cache keyed by {@code name} so a
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
  popoverProvider.value = p;
  loadCatalog(p.name);
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
    // Two-step: (1) reload persisted catalog so any user changes propagate;
    // (2) hit the vendor for the live list and merge transient entries in as
    // source='remote'. Nothing is persisted at DB level from the refresh.
    const [remoteList] = await Promise.all([
      refreshCatalog(p.name, props.tenantId),
      loadCatalog(p.name),
    ]);
    mergeRemoteIntoCatalog(p.name, remoteList);
    emit('change');
  } finally {
    const after = new Set(refreshingCatalog.value);
    after.delete(p.name);
    refreshingCatalog.value = after;
  }
}

/**
 * Add live-fetched entries into the local catalog cache as source='remote' rows,
 * unless an existing persisted (predefined/custom) row already covers the same
 * (model, modelType). Remote rows default to disabled — user enables them via
 * the toggle, which triggers the settings-table INSERT.
 */
function mergeRemoteIntoCatalog(providerName: string, remote: any[]) {
  if (!remote || remote.length === 0) return;
  const existing = catalogByProvider.value[providerName] ?? [];
  const seen = new Set<string>();
  for (const r of existing) seen.add(`${r.model}::${r.modelType}`);
  const additions: CatalogRow[] = [];
  for (const rm of remote) {
    const key = `${rm.modelId}::${rm.modelType}`;
    if (seen.has(key)) continue;
    if (!rm.modelType) continue;
    additions.push({
      id: `remote::${providerName}::${rm.modelId}::${rm.modelType}`,
      model: rm.modelId,
      label: rm.label ?? rm.modelId,
      modelType: rm.modelType,
      contextLength: rm.contextLength,
      dimensions: rm.dimensions,
      features: rm.features ?? [],
      source: 'remote',
      enabled: false,
      isDefault: false,
    });
    seen.add(key);
  }
  catalogByProvider.value = {
    ...catalogByProvider.value,
    [providerName]: [...existing, ...additions],
  };
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
    manualAddError.value = e?.message ?? String(e);
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
function toggleCardMenu(providerName: string, e: Event) {
  e.stopPropagation();
  openMenuFor.value = openMenuFor.value === providerName ? null : providerName;
}
function closeCardMenu() {
  openMenuFor.value = null;
}

// -------------------------------------------------- popover filter tabs
type EnabledFilter = 'all' | 'disabled' | 'enabled';
const enabledFilter = ref<EnabledFilter>('all');
const typeFilter = ref<'all' | ModelType>('all');

function filteredCatalogRows(providerName: string): CatalogRow[] {
  const rows = catalogRows(providerName);
  return rows.filter((r) => {
    if (enabledFilter.value === 'enabled' && !r.enabled) return false;
    if (enabledFilter.value === 'disabled' && r.enabled) return false;
    if (typeFilter.value !== 'all' && r.modelType !== typeFilter.value) return false;
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

function availableTypesInCatalog(providerName: string): ModelType[] {
  const set = new Set<ModelType>();
  for (const r of catalogRows(providerName)) set.add(r.modelType);
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
  if (t === 'LLM') return '💬';
  if (t === 'TEXT_EMBEDDING') return '🧮';
  if (t === 'RERANK') return '🔀';
  if (t === 'SPEECH2TEXT') return '🎤';
  if (t === 'TTS') return '🔊';
  if (t === 'MODERATION') return '🛡';
  return '🤖';
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
      out.push({ key: 'VISION', label: 'Vision', color: 'purple' });
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
  <div class="phs-root">
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
          <input
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

    <!-- Section 1 · 模型列表 (compact card grid; no click-to-open) -->
    <div v-if="configured.length > 0" class="phs-section">
      <div class="phs-section-title">模型列表</div>
      <div class="phs-configured-grid">
        <Card variant="management"
          v-for="p in configured"
          :key="p.name"
          class="phs-compact-card"
          :title="p.label"
          :footer-divider="false"
        >
          <template #icon>
            <ProviderIcon
              :name="p.name"
              :size="36"
              v-bind="providerIconProps(p)"
            />
          </template>

          <template #actions>
            <div class="phs-card-menu-anchor">
              <button
                class="phs-card-menu-btn"
                title="更多操作"
                @click.stop="toggleCardMenu(p.name, $event)"
              >
                ⋯
              </button>
              <div
                v-if="openMenuFor === p.name"
                class="phs-card-menu"
                @click.stop
              >
                <button class="phs-card-menu-item" @click="onRefreshCatalog(p); closeCardMenu()">
                  ↻ 重新拉取模型
                </button>
                <button class="phs-card-menu-item" @click="openCredentialModal(p, true); closeCardMenu()">
                  🔑 编辑凭证
                </button>
                <button class="phs-card-menu-item" @click="openManualAdd(p); closeCardMenu()">
                  + 手动添加模型
                </button>
                <div class="phs-card-menu-sep" />
                <button class="phs-card-menu-item phs-card-menu-item--danger" @click="clearCredential(p); closeCardMenu()">
                  🗑 删除凭证
                </button>
              </div>
            </div>
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

          <!-- Antdv 气泡卡片：click count → floating popover, NOT a modal -->
          <Popover
            :open="popoverProvider?.name === p.name"
            trigger="click"
            placement="rightTop"
            overlay-class-name="phs-model-popover-overlay"
            :arrow="false"
            :destroy-tooltip-on-hide="true"
            @update:open="(v: boolean) => (v ? openPopover(p) : closePopover())"
          >
            <template #content>
              <div
                v-if="popoverProvider?.name === p.name"
                class="phs-popover-inner"
                @click.stop
              >
                <header class="phs-popover-head">
                  <ProviderIcon
                    :name="p.name"
                    :size="32"
                    v-bind="providerIconProps(p)"
                  />
                  <div class="phs-popover-head-body">
                    <div class="phs-popover-title">{{ p.label }}</div>
                    <div class="phs-popover-subtitle">
                      {{ enabledCount(p) }} 已启用 · 共
                      {{ catalogRows(p.name).length || knownModelCount(p) }} 个模型
                    </div>
                  </div>
                </header>

                <div class="phs-popover-actions">
                  <Button
                    v-if="p.supportsRemoteModelListing"
                    type="text"
                    :loading="refreshingCatalog.has(p.name)"
                    @click="onRefreshCatalog(p)"
                  >
                    ↻ 重新拉取
                  </Button>
                  <Button type="text" @click="openManualAdd(p)">
                    + 手动添加
                  </Button>
                </div>

                <div class="phs-filter-bar">
                  <div class="phs-filter-group">
                    <button
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'all' }"
                      @click="enabledFilter = 'all'"
                    >
                      全部 <span class="phs-filter-count">{{ countBy(p.name, 'all') }}</span>
                    </button>
                    <button
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'enabled' }"
                      @click="enabledFilter = 'enabled'"
                    >
                      已启用 <span class="phs-filter-count">{{ countBy(p.name, 'enabled') }}</span>
                    </button>
                    <button
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': enabledFilter === 'disabled' }"
                      @click="enabledFilter = 'disabled'"
                    >
                      未启用 <span class="phs-filter-count">{{ countBy(p.name, 'disabled') }}</span>
                    </button>
                  </div>
                  <div class="phs-filter-group">
                    <button
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': typeFilter === 'all' }"
                      @click="typeFilter = 'all'"
                    >
                      所有类型
                    </button>
                    <button
                      v-for="t in availableTypesInCatalog(p.name)"
                      :key="t"
                      class="phs-filter-tab"
                      :class="{ 'phs-filter-tab--on': typeFilter === t }"
                      :data-color="modelTypeColor(t)"
                      @click="typeFilter = t"
                    >
                      {{ modelTypeLabel(t) }}
                    </button>
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
                        <button
                          v-if="row.source === 'custom'"
                          class="phs-row-gear"
                          title="参数 / 设为默认 / 删除"
                          @click.stop="openDrawer(catalogRowToModelEntity(row, p.name), p)"
                        >
                          ⚙
                        </button>
                        <label class="phs-switch">
                          <input
                            type="checkbox"
                            :checked="row.enabled"
                            :disabled="togglingKey.has(toggleKey(row))"
                            @change="(e) => onToggleEnabled(p.name, row, (e.target as HTMLInputElement).checked)"
                          />
                          <span class="phs-switch-slider" />
                        </label>
                      </div>
                    </div>
                  </template>
                </div>

                <footer class="phs-popover-foot">
                  <Button
                    type="text"
                    @click="openCredentialModal(p, true)"
                  >
                    编辑凭证
                  </Button>
                  <Button
                    type="text"
                    danger
                    @click="clearCredential(p)"
                  >
                    删除凭证
                  </Button>
                </footer>
              </div>
            </template>

            <button
              class="phs-count-btn"
              title="点击查看模型列表"
              @click.stop
            >
              <span class="phs-count-enabled">{{ enabledCount(p) }}</span>
              <span class="phs-count-sep">/</span>
              <span class="phs-count-total">{{ knownModelCount(p) }}</span>
              <span class="phs-count-label">个模型 →</span>
            </button>
          </Popover>
        </Card>
      </div>
    </div>

    <!-- Section 2 · 待配置 (same compact grid style) -->
    <div v-if="pending.length > 0" class="phs-section">
      <div class="phs-section-title">待配置</div>
      <div class="phs-configured-grid">
        <Card variant="management"
          v-for="p in pending"
          :key="p.name"
          class="phs-compact-card phs-compact-card--pending"
          :title="p.label"
        >
          <template #icon>
            <ProviderIcon
              :name="p.name"
              :size="32"
              v-bind="providerIconProps(p)"
            />
          </template>

          <div class="phs-compact-caps">
            <span
              v-for="t in p.supportedModelTypes"
              :key="t"
              class="phs-cap"
              :data-color="modelTypeColor(t)"
            >
              {{ modelTypeLabel(t) }}
            </span>
          </div>

          <button
            class="phs-compact-pending"
            @click.stop="p.supportsRemoteModelListing ? onRefreshCatalog(p) : openManualAdd(p)"
          >
            {{ p.supportsRemoteModelListing ? '↻ 点击拉取模型' : '+ 手动添加模型' }}
          </button>
        </Card>
      </div>
    </div>

    <!-- Section 3 · 安装模型供应商 -->
    <div v-if="installable.length > 0" class="phs-section">
      <div class="phs-section-title">安装模型供应商</div>
      <div class="phs-installable-grid">
        <Card variant="management"
          v-for="p in installable"
          :key="p.name"
          class="phs-installable-card"
          :title="p.label"
          :description="p.name"
        >
          <template #icon>
            <ProviderIcon
              :name="p.name"
              :size="32"
              v-bind="providerIconProps(p)"
            />
          </template>

          <template v-if="p.supportsRemoteModelListing" #badge>
            <span class="phs-installable-badge">自动拉取</span>
          </template>

          <template #actions>
            <Button
              size="small"
              @click="openCredentialModal(p)"
            >
              添加 API 密钥
            </Button>
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

    <div v-if="loading" class="phs-loading">加载中…</div>
    <div v-else-if="providers.length === 0" class="phs-empty">
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
            <input
              v-model="manualAddForm.modelName"
              class="phs-mini-input"
              placeholder="例如 gpt-4o-2025-preview"
            />
          </div>
          <div class="phs-mini-field">
            <label>模型类型</label>
            <select v-model="manualAddForm.modelType" class="phs-mini-input">
              <option
                v-for="t in manualAddProvider?.supportedModelTypes ?? []"
                :key="t"
                :value="t"
              >
                {{ modelTypeLabel(t) }}
              </option>
            </select>
          </div>
          <div v-if="manualAddError" class="phs-mini-error">
            {{ manualAddError }}
          </div>
        </div>
        <template #footer>
        <div class="phs-mini-footer">
          <Button @click="manualAddOpen = false">取消</Button>
          <Button
            type="primary"
            :loading="manualAddSaving"
            @click="submitManualAdd"
          >
            添加
          </Button>
        </div>
        </template>
    </Modal>
  </div>
</template>

<style scoped>
.phs-root {
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
/* 搜索框样式 */
.phs-search-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.phs-search-icon {
  position: absolute;
  left: 10px;
  color: #9ca3af;
  pointer-events: none;
}
.phs-search {
  width: 240px;
  max-width: 100%;
  height: 36px;
  padding: 0 12px 0 32px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.phs-search::placeholder {
  color: #9ca3af;
}
.phs-search:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
:global(.dark) .phs-search {
  background: #2d2d2d;
  color: #f3f4f6;
  border-color: #3d3d3d;
}
.phs-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.phs-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}
:global(.dark) .phs-section-title {
  color: #d1d5db;
}

/* -------- compact grid (larger cards, ~3-4 per row) -------- */
.phs-configured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.phs-compact-card {
  cursor: pointer;
  gap: 12px;
  min-height: 110px;
  position: relative;
}
/* Card: actions slot holds the "⋯" menu in the top-right corner,
   lifted out of flow so the title row can use the full width. */
.phs-compact-card:deep(.as-management-card__actions) {
  position: absolute;
  top: 16px;
  right: 18px;
  z-index: 5;
  width: 30px;
  height: 28px;
  margin: 0;
  padding: 0;
  border: 0;
  flex: none;
}
.phs-compact-card:deep(.as-management-card__actions) .phs-card-menu-anchor {
  position: relative;
  width: 30px;
  height: 28px;
}
/* Menu is now anchored to the actions-slot wrapper (top-right corner of the
   card), so reset its absolute offsets to hang from the anchor's bottom-right. */
.phs-compact-card .phs-card-menu {
  top: calc(100% + 2px);
  right: 0;
}
.phs-compact-card--pending {
  border-style: dashed;
}
.phs-compact-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.phs-compact-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
:global(.dark) .phs-compact-title {
  color: #f3f4f6;
}
.phs-compact-caps {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.phs-count-btn {
  background: transparent;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: baseline;
  gap: 3px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
  margin-top: auto;
}
.phs-count-btn:hover {
  border-color: #6366f1;
  background: #eef2ff;
}
.phs-count-enabled {
  font-weight: 700;
  color: #059669;
  font-size: 16px;
}
.phs-count-sep,
.phs-count-total {
  color: #9ca3af;
  font-size: 14px;
}
.phs-count-label {
  margin-left: 6px;
  color: #6b7280;
}
.phs-compact-pending {
  font-size: 13px;
  color: #b45309;
  background: transparent;
  border: 1px dashed #fbbf24;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
  margin-top: auto;
}
.phs-compact-pending:hover {
  background: #fef3c7;
  border-color: #d97706;
}

/* ⋯ menu on each card */
.phs-card-menu-btn {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 20px;
  padding: 0 6px;
  border-radius: 6px;
  line-height: 1;
  flex-shrink: 0;
}
.phs-card-menu-btn:hover {
  background: #f3f4f6;
  color: #374151;
}
.phs-card-menu {
  position: absolute;
  top: 44px;
  right: 12px;
  min-width: 180px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  z-index: 10;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
:global(.dark) .phs-card-menu {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
.phs-card-menu-item {
  padding: 8px 12px;
  font-size: 13px;
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  color: #374151;
}
.phs-card-menu-item:hover {
  background: #f3f4f6;
}
:global(.dark) .phs-card-menu-item {
  color: #d1d5db;
}
:global(.dark) .phs-card-menu-item:hover {
  background: #2d2d2d;
}
.phs-card-menu-item--danger {
  color: #dc2626;
}
.phs-card-menu-item--danger:hover {
  background: #fef2f2;
}
.phs-card-menu-sep {
  height: 1px;
  background: #e5e7eb;
  margin: 4px 0;
}
:global(.dark) .phs-card-menu-sep {
  background: #2d2d2d;
}

/* Popover filter tab bar */
.phs-filter-bar {
  padding: 10px 18px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
:global(.dark) .phs-filter-bar {
  border-bottom-color: #2d2d2d;
}
.phs-filter-group {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.phs-filter-tab {
  background: #f3f4f6;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
.phs-filter-tab:hover {
  background: #e5e7eb;
}
.phs-filter-tab--on {
  background: #eef2ff;
  border-color: #6366f1;
  color: #4338ca;
  font-weight: 500;
}
:global(.dark) .phs-filter-tab {
  background: #262626;
  color: #d1d5db;
}
:global(.dark) .phs-filter-tab:hover {
  background: #2d2d2d;
}
:global(.dark) .phs-filter-tab--on {
  background: #312e81;
  color: #a5b4fc;
}
.phs-filter-count {
  background: rgba(255, 255, 255, 0.6);
  padding: 0 5px;
  border-radius: 999px;
  font-size: 10.5px;
  color: #6b7280;
  min-width: 18px;
  text-align: center;
}
.phs-filter-tab--on .phs-filter-count {
  background: #fff;
  color: #4338ca;
}


.phs-cap {
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  letter-spacing: 0.5px;
}
.phs-cap[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.phs-cap[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.phs-cap[data-color='purple'] {
  background: #faf5ff;
  color: #7c3aed;
}

.phs-btn {
  padding: 5px 12px;
  font-size: 12px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  cursor: pointer;
  white-space: nowrap;
}
.phs-btn:hover {
  background: #f9fafb;
}
.phs-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.phs-btn-ghost {
  background: transparent;
}
.phs-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}
.phs-btn-primary:hover {
  background: #4f46e5;
}
.phs-btn-danger {
  color: #dc2626;
}
.phs-btn-danger:hover {
  background: #fef2f2;
}

/* -------- installable grid -------- */
.phs-installable-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}
.phs-installable-card {
  gap: 8px;
}
/* Card footer: action button pushed to the right, anchored at the
   bottom of the card. */
.phs-installable-card:deep(.as-management-card__footer) {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.phs-installable-card:deep(.as-management-card__actions) {
  margin: 0;
  padding: 0;
  border: 0;
  flex: none;
}
.phs-installable-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.phs-installable-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
:global(.dark) .phs-installable-name {
  color: #f3f4f6;
}
.phs-installable-desc {
  font-size: 11px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 6px;
}
.phs-installable-badge {
  padding: 0 6px;
  background: #ecfdf5;
  color: #059669;
  border-radius: 3px;
  font-weight: 500;
}
.phs-installable-caps {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.phs-installable-footer {
  margin-top: auto;
}

.phs-loading,
.phs-empty {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

/* -------- Shared popover header / body / actions styling -------- */
.phs-popover-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
}
:global(.dark) .phs-popover-head {
  border-bottom-color: #2d2d2d;
}
.phs-popover-head-body {
  flex: 1;
  min-width: 0;
}
.phs-popover-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:global(.dark) .phs-popover-title {
  color: #f3f4f6;
}
.phs-popover-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}
.phs-popover-actions {
  display: flex;
  gap: 6px;
  padding: 10px 18px;
  border-bottom: 1px solid #e5e7eb;
  flex-wrap: wrap;
  flex-shrink: 0;
}
:global(.dark) .phs-popover-actions {
  border-bottom-color: #2d2d2d;
}
.phs-popover-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px 18px;
}
.phs-popover-empty {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  line-height: 1.6;
}
.phs-popover-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

/* Antdv Popover container — override defaults so our content fits nicely */
:global(.phs-model-popover-overlay .ant-popover-inner) {
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  box-shadow:
    0 18px 48px rgba(15, 23, 42, 0.14),
    0 2px 8px rgba(15, 23, 42, 0.06);
}
:global(.phs-model-popover-overlay .ant-popover-inner-content) {
  padding: 0;
}
.phs-popover-inner {
  width: 520px;
  max-width: 90vw;
  max-height: 78vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.phs-popover-inner .phs-popover-head {
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}
.phs-popover-inner .phs-popover-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 10px;
}
.phs-popover-inner .phs-popover-actions {
  padding: 8px 16px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  gap: 6px;
}
.phs-popover-foot {
  padding: 8px 16px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

/* Row: single-line — icon | name (flex:1) | tags | gear | switch */
.phs-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 4px 8px;
  background: #fff;
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
  overflow: hidden;
}
.phs-row:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}
.phs-row--on {
  background: #f5f7ff;
  border-color: #dfe3ff;
  box-shadow: inset 2px 0 0 #6366f1;
}
:global(.dark) .phs-row {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
:global(.dark) .phs-row:hover {
  background: #262626;
}
:global(.dark) .phs-row--on {
  background: #312e81;
  border-color: #6366f1;
}
.phs-row-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.phs-row-icon[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.phs-row-icon[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.phs-row-icon[data-color='purple'] {
  background: #faf5ff;
  color: #7c3aed;
}
.phs-row-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 550;
  color: #111827;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:global(.dark) .phs-row-name {
  color: #f3f4f6;
}
.phs-row-cap {
  flex-shrink: 0;
  padding: 1px 5px;
  font-size: 9.5px;
  font-weight: 500;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  letter-spacing: 0.1px;
  white-space: nowrap;
}
.phs-row-cap[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.phs-row-cap[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.phs-row-cap[data-color='purple'] {
  background: #faf5ff;
  color: #7c3aed;
}
.phs-row-cap[data-color='blue'] {
  background: #dbeafe;
  color: #1e40af;
}
.phs-row-cap--ctx {
  background: #f3f4f6;
  color: #4b5563;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
}
.phs-row-cap--custom {
  background: #fef3c7;
  color: #b45309;
}
.phs-row-cap--remote {
  background: #dcfce7;
  color: #15803d;
}
.phs-row-cap--default {
  background: #dbeafe;
  color: #1e40af;
}
.phs-row-gear {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  width: 24px;
  height: 24px;
  font-size: 12px;
  padding: 0;
  border-radius: 4px;
  flex-shrink: 0;
}
.phs-row-gear:hover {
  background: #eef2ff;
  color: #6366f1;
}

/* Pill row — legacy compact list style, kept for potential future use */
.phs-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #f9fafb;
  border: 1px solid transparent;
  border-radius: 999px;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.phs-pill:hover {
  background: #f3f4f6;
}
.phs-pill--on {
  background: #eef2ff;
  border-color: #a5b4fc;
}
:global(.dark) .phs-pill {
  background: #262626;
}
:global(.dark) .phs-pill:hover {
  background: #2d2d2d;
}
:global(.dark) .phs-pill--on {
  background: #312e81;
  border-color: #6366f1;
}
.phs-pill-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:global(.dark) .phs-pill-name {
  color: #f3f4f6;
}
.phs-pill-meta {
  display: flex;
  gap: 4px;
}
.phs-pill-badge {
  padding: 0 6px;
  font-size: 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1e40af;
}
.phs-pill-badge--default {
  background: #dbeafe;
  color: #1e40af;
}
.phs-pill-badge--feat {
  background: #f5f3ff;
  color: #6d28d9;
}
.phs-pill-badge--custom {
  background: #fef3c7;
  color: #b45309;
}
.phs-pill-badge--remote {
  background: #dcfce7;
  color: #15803d;
}
.phs-pill-gear {
  background: transparent;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  font-size: 13px;
  padding: 0 4px;
  border-radius: 4px;
  flex-shrink: 0;
}
.phs-pill-gear:hover {
  background: #eef2ff;
  color: #6366f1;
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
.phs-switch-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #cbd5e1;
  border-radius: 999px;
  transition: 0.15s;
}
.phs-switch-slider:before {
  position: absolute;
  content: '';
  height: 12px;
  width: 12px;
  left: 2px;
  bottom: 2px;
  background: #fff;
  border-radius: 50%;
  transition: 0.15s;
}
.phs-switch input:checked + .phs-switch-slider {
  background: #6366f1;
}
.phs-switch input:checked + .phs-switch-slider:before {
  transform: translateX(12px);
}

@media (max-width: 560px) {
  .phs-popover-inner {
    width: min(520px, calc(100vw - 24px));
  }
  .phs-row-cap--ctx,
  .phs-row-cap[data-color='blue'] {
    display: none;
  }
}
.phs-switch input:disabled + .phs-switch-slider {
  opacity: 0.5;
  cursor: not-allowed;
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
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
}
:global(.dark) .phs-mini-modal {
  background: #1f1f1f;
  color: #f3f4f6;
}
.phs-mini-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}
.phs-mini-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
:global(.dark) .phs-mini-title {
  color: #f3f4f6;
}
.phs-mini-close {
  background: transparent;
  border: none;
  font-size: 16px;
  color: #6b7280;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.phs-mini-close:hover {
  background: #f3f4f6;
}
.phs-mini-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.phs-mini-hint {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}
.phs-mini-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.phs-mini-field label {
  font-size: 12px;
  color: #374151;
  font-weight: 500;
}
.phs-mini-input {
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
}
:global(.dark) .phs-mini-input {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.phs-mini-input:focus {
  outline: none;
  border-color: #6366f1;
}
.phs-mini-error {
  padding: 6px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  font-size: 12px;
}
.phs-mini-footer {
  padding: 10px 16px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.phs-req {
  color: #dc2626;
}
</style>
