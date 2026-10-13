<script setup lang="ts">
/**
 * KnowledgeHubApp — the drop-in top-level component for the knowledge-base
 * feature. Consumers only need to implement the `KnowledgeHubApi` interface
 * (a bag of async REST calls) and drop this component in — the whole Dify-
 * parity flow is contained inside:
 *
 *   ┌────────────────────────────────────────────────────────┐
 *   │  <KnowledgeHubApp :api="apiImpl" />                    │
 *   └────────────────────────────────────────────────────────┘
 *
 *      renders the card grid.
 *      +新建 click            → left-side wizard drawer (self-contained)
 *      dataset card click     → right-side detail drawer with 4 tabs +
 *                                inline chunks browser (no route change)
 *
 * The parent app doesn't need routes, watchers or dialog plumbing for the
 * knowledge feature. Emits `changed` when list-affecting mutations happen so
 * consumers can refresh their own counters if needed.
 *
 * Expose `openCreate()` / `openDetail(id)` via defineExpose so the host can
 * also trigger the drawers programmatically (per the demo request).
 */
import { computed, onMounted, ref, watch } from 'vue';

import { type KhLocale, provideKhI18n, useKhI18n } from '../i18n';
import type {
  DatasetCardItem,
  DatasetDetailHub,
  DatasetTab,
  EmbeddingModelOption,
  KnowledgeHubApi,
  ProcessRule,
  RetrievalConfig,
} from '../types';
import CreateDatasetWizard from './CreateDatasetWizard.vue';
import DatasetCardGrid from './DatasetCardGrid.vue';
import DatasetDetailDrawer from './DatasetDetailDrawer.vue';
import KhDialog from './internal/KhDialog.vue';

interface Props {
  api: KnowledgeHubApi;
  /**
   * Optional locale — either a language code ('zh-CN' | 'en-US') or a partial
   * message catalog for host-owned overrides. Default: zh-CN.
   */
  locale?: KhLocale;
  /** Optional page title override — falls back to the locale catalog. */
  title?: string;
  /** Optional description override — falls back to the locale catalog. */
  description?: string;
}

const props = defineProps<Props>();
provideKhI18n(() => props.locale);
const { t } = useKhI18n();

const displayTitle = computed(() => props.title ?? t('app.title'));
const displayDescription = computed(
  () => props.description ?? t('app.description'),
);

const emit = defineEmits<{
  (e: 'changed'): void;
}>();

// -------- state
const datasets = ref<DatasetCardItem[]>([]);
const loading = ref(false);
const keyword = ref('');

const wizardOpen = ref(false);
const detailOpen = ref(false);
const detailId = ref<null | string>(null);
const detailInitialTab = ref<DatasetTab | undefined>(undefined);

const embeddingModels = ref<EmbeddingModelOption[]>([]);
const rerankModels = ref<Array<{ id: string; label: string }>>([]);

// -------- helpers
function notifySuccess(msg: string) {
  props.api.onSuccess?.(msg);
}
function notifyError(msg: string) {
  props.api.onError?.(msg);
}

// -------- load
async function refresh() {
  loading.value = true;
  try {
    datasets.value = await props.api.listDatasets();
  } catch (e: any) {
    notifyError(`${t('common.loadFailed')}: ${e?.message ?? e}`);
    datasets.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadEmbeddingModels() {
  try {
    embeddingModels.value = await props.api.listEmbeddingModels();
  } catch {
    embeddingModels.value = [];
  }
}

async function loadRerankModels() {
  if (!props.api.listRerankModels) {
    rerankModels.value = [];
    return;
  }
  try {
    rerankModels.value = await props.api.listRerankModels();
  } catch {
    rerankModels.value = [];
  }
}

onMounted(async () => {
  await Promise.all([refresh(), loadEmbeddingModels(), loadRerankModels()]);
});

// -------- create
function openCreate() {
  // Re-load embedding + rerank models each time so newly-registered ones show up.
  loadEmbeddingModels();
  loadRerankModels();
  wizardOpen.value = true;
}

async function onWizardCreate(payload: {
  description: string;
  embeddingModelId: string;
  files: File[];
  indexingTechnique: 'ECONOMY' | 'HIGH_QUALITY';
  name: string;
  processRule: ProcessRule;
  retrievalConfig: RetrievalConfig;
}) {
  try {
    const created = await props.api.createDataset({
      name: payload.name,
      description: payload.description,
      embeddingModelId: payload.embeddingModelId || undefined,
      indexingTechnique: payload.indexingTechnique,
      processRule: payload.processRule,
      retrievalConfig: payload.retrievalConfig,
    });
    // Serial uploads keep progress easy to reason about.
    for (const file of payload.files) {
      await props.api.uploadDocument(created.id, file);
    }
    notifySuccess(t('common.createdSuccess'));
    wizardOpen.value = false;
    await refresh();
    emit('changed');
    // Auto-open the detail drawer on the freshly-created dataset — matches Dify.
    openDetail(created.id);
  } catch (e: any) {
    notifyError(`${t('common.createFailed')}: ${e?.message ?? e}`);
  }
}

// -------- create empty (KhDialog prompt, replaces window.prompt)
const emptyPromptOpen = ref(false);
function onWizardSkipEmpty() {
  // Ask the user via our own dialog — no native browser prompts.
  wizardOpen.value = false;
  emptyPromptOpen.value = true;
}
async function onEmptyPromptConfirm(name: string | undefined) {
  const trimmed = (name ?? '').trim();
  if (!trimmed) return;
  try {
    const created = await props.api.createDataset({
      name: trimmed,
      indexingTechnique: 'ECONOMY',
    });
    notifySuccess(t('common.createdSuccess'));
    await refresh();
    emit('changed');
    openDetail(created.id);
  } catch (e: any) {
    notifyError(`${t('common.createFailed')}: ${e?.message ?? e}`);
  }
}

// -------- open detail
function openDetail(id: string, tab?: DatasetTab) {
  detailId.value = id;
  detailInitialTab.value = tab;
  detailOpen.value = true;
}
function openSettings(d: DatasetCardItem) {
  openDetail(d.id, 'settings');
}

// -------- delete from card (KhDialog confirm, replaces window.confirm)
const deleteConfirmOpen = ref(false);
const deleteTarget = ref<DatasetCardItem | null>(null);

function onDeleteCard(d: DatasetCardItem) {
  deleteTarget.value = d;
  deleteConfirmOpen.value = true;
}
async function onDeleteConfirm() {
  const d = deleteTarget.value;
  if (!d) return;
  try {
    await props.api.deleteDataset(d.id);
    notifySuccess(t('common.deleteSuccess'));
    await refresh();
    emit('changed');
  } catch (e: any) {
    notifyError(`${e?.message ?? e}`);
  }
}

// -------- detail drawer hub adapter (satisfies DatasetDetailHub interface)
const detailHub = computed<DatasetDetailHub>(() => ({
  loadDataset: (id) => props.api.getDataset(id),
  updateDataset: (id, patch) => props.api.updateDataset(id, patch),
  deleteDataset: async (id) => {
    await props.api.deleteDataset(id);
    await refresh();
    emit('changed');
  },
  listDocuments: (id) => props.api.listDocuments(id),
  uploadDocuments: async (id, files) => {
    for (const f of files) await props.api.uploadDocument(id, f);
  },
  deleteDocument: (id, docId) => props.api.deleteDocument(id, docId),
  setDocumentEnabled: props.api.setDocumentEnabled,
  listSegments: (id, docId, page, pageSize) =>
    props.api.listSegments(id, docId, page, pageSize),
  loadDocumentMetadata: (id, docId) => props.api.loadDocumentMetadata(id, docId),
  updateSegment: (id, docId, segId, content) =>
    props.api.updateSegment(id, docId, segId, content),
  deleteSegment: (id, docId, segId) => props.api.deleteSegment(id, docId, segId),
  setSegmentEnabled: (id, docId, segId, enabled) =>
    props.api.setSegmentEnabled(id, docId, segId, enabled),
  appendSegment: props.api.appendSegment,
  retrieve: (id, req) => props.api.retrieve(id, req),
  listRecallHistory: (id, limit) => props.api.listRecallHistory(id, limit),
  listEmbeddingModels: () => props.api.listEmbeddingModels(),
  previewChunks: props.api.previewChunks,
  onCopyApi: props.api.onCopyApi,
}));

function onDetailChanged() {
  refresh();
  emit('changed');
}

watch(detailOpen, (v) => {
  if (!v) {
    // Refresh on close in case anything mutated inside the drawer.
    refresh();
  }
});

// -------- public API for host programmatic control
defineExpose({
  openCreate,
  openDetail,
  refresh,
});
</script>

<template>
  <div class="kh-app">
    <!--
      header — replace the whole thing with #header, or extend with #header-actions
      to keep the title/search but add extra buttons on the right.
    -->
    <slot
      name="header"
      :title="displayTitle"
      :description="displayDescription"
      :search-value="keyword"
      :on-search="(v: string) => (keyword = v)"
      :open-create="openCreate"
    >
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
              <path d="M12 7v14" />
              <path
                d="M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a2 2 0 0 1 2 2 2 2 0 0 1 2-2h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a1 1 0 0 0-1 1 1 1 0 0 0-1-1z"
              />
            </svg>
          </div>
          <div class="as-page-header-text">
            <div class="as-page-title">{{ displayTitle }}</div>
            <div class="as-page-subtitle">{{ displayDescription }}</div>
          </div>
        </div>
        <div class="as-page-header-controls">
          <div class="kh-app-search">
            <svg
              class="kh-app-search-icon"
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
              v-model="keyword"
              class="kh-app-search-input"
              :placeholder="t('app.searchPlaceholder')"
            />
          </div>
          <slot name="header-actions" :open-create="openCreate" />
        </div>
      </div>
    </slot>

    <!--
      First-time-use nudge: no embedding model registered yet. Only shows once
      the initial fetch has resolved with an empty list — avoids a flash while
      loading. Consumers wire {@code api.onGoToEmbeddingSetup} to their model
      route so the "去配置模型" button jumps there in-app. When the callback
      is omitted the card still renders (useful hint) but without the button.
    -->
<!--    <div
      v-if="!loading && embeddingModels.length === 0"
      class="kh-app-nudge"
    >
      <div class="kh-app-nudge-icon">🎯</div>
      <div class="kh-app-nudge-body">
        <div class="kh-app-nudge-title">{{ t('app.nudgeEmbeddingTitle') }}</div>
        <div class="kh-app-nudge-desc">{{ t('app.nudgeEmbeddingDesc') }}</div>
        <button
          v-if="api.onGoToEmbeddingSetup"
          class="kh-app-nudge-btn"
          type="button"
          @click="api.onGoToEmbeddingSetup?.()"
        >
          {{ t('app.nudgeEmbeddingCta') }}
        </button>
      </div>
    </div>-->

    <!-- card grid — replace with #cards to render your own layout, or use #empty
         to override just the empty state. -->
    <div class="kh-app-body">
      <div v-if="loading" class="kh-app-loading">
        <slot name="loading">{{ t('app.loading') }}</slot>
      </div>
      <template v-else-if="datasets.length === 0">
        <slot name="empty" :open-create="openCreate">
          <DatasetCardGrid
            :datasets="datasets"
            :keyword="keyword"
            @create="openCreate"
            @open="(d: DatasetCardItem) => openDetail(d.id)"
            @settings="openSettings"
            @delete="onDeleteCard"
          />
        </slot>
      </template>
      <slot
        v-else
        name="cards"
        :datasets="datasets"
        :keyword="keyword"
        :open-create="openCreate"
        :open-detail="openDetail"
        :open-settings="openSettings"
        :on-delete="onDeleteCard"
      >
        <DatasetCardGrid
          :datasets="datasets"
          :keyword="keyword"
          @create="openCreate"
          @open="(d: DatasetCardItem) => openDetail(d.id)"
          @settings="openSettings"
          @delete="onDeleteCard"
        />
      </slot>
    </div>

    <!-- create wizard (left-side drawer). previewChunks runs through the api
         adapter so the step-2 preview matches real ingestion output. -->
    <CreateDatasetWizard
      :open="wizardOpen"
      :embedding-models="embeddingModels"
      :rerank-models="rerankModels"
      :preview-chunks="api.previewChunks"
      @update:open="wizardOpen = $event"
      @create="onWizardCreate"
      @skip-to-empty="onWizardSkipEmpty"
    />

    <!-- detail drawer (right-side) -->
    <DatasetDetailDrawer
      :open="detailOpen"
      :dataset-id="detailId"
      :initial-tab="detailInitialTab"
      :hub="detailHub"
      @update:open="detailOpen = $event"
      @refresh-list="onDetailChanged"
    />

    <!-- inline prompt for "create empty knowledge base" — replaces window.prompt -->
    <KhDialog
      :open="emptyPromptOpen"
      :title="t('wizard.createEmptyPromptTitle')"
      :prompt-default="t('wizard.createEmptyPromptDefault')"
      @update:open="emptyPromptOpen = $event"
      @confirm="onEmptyPromptConfirm"
    />

    <!-- inline confirm for dataset delete — replaces window.confirm -->
    <KhDialog
      :open="deleteConfirmOpen"
      :title="t('card.delete')"
      :content="deleteTarget
        ? t('card.confirmDelete', { name: deleteTarget.name })
        : ''"
      :ok-text="t('common.delete')"
      danger
      @update:open="deleteConfirmOpen = $event"
      @confirm="onDeleteConfirm"
    />
  </div>
</template>

<style scoped>
.kh-app {
  display: flex;
  flex-direction: column;
  /* Comfortable page margins — content never hugs the viewport edges. */
  padding: var(--kh-space-5) var(--kh-space-6) var(--kh-space-6);
}
/* Note: Page header now uses unified .as-page-header styles from ui/style.css */
/* First-time-use nudge — "先注册 Embedding 模型" empty-state card. */
.kh-app-nudge {
  display: flex;
  align-items: flex-start;
  gap: var(--kh-space-3);
  padding: var(--kh-space-3) var(--kh-space-4);
  background: var(--kh-color-surface);
  border: 1px solid var(--kh-color-border);
  border-radius: 8px;
}
.kh-app-nudge-icon {
  font-size: 22px;
  line-height: 1;
}
.kh-app-nudge-body {
  flex: 1;
}
.kh-app-nudge-title {
  font-size: var(--kh-fs-md);
  font-weight: 500;
  color: var(--kh-color-text-primary);
}
.kh-app-nudge-desc {
  margin-top: 4px;
  font-size: var(--kh-fs-sm, 13px);
  color: var(--kh-color-text-muted);
  line-height: 1.55;
}
.kh-app-nudge-btn {
  margin-top: 8px;
  padding: 4px 12px;
  background: var(--kh-color-primary-strong);
  color: var(--kh-color-primary-contrast);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.kh-app-nudge-btn:hover {
  background: var(--kh-color-primary);
}
.kh-app-toolbar {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
}
.kh-app-search {
  position: relative;
  display: flex;
  align-items: center;
}
.kh-app-search-icon {
  position: absolute;
  left: 10px;
  color: var(--kh-color-text-muted);
  pointer-events: none;
}
.kh-app-search-input {
  width: 260px;
  max-width: 100%;
  height: 36px;
  padding: 0 var(--kh-space-3) 0 32px;
  border: 1px solid var(--kh-color-border);
  border-radius: var(--kh-input-radius);
  outline: none;
  font-size: var(--kh-fs-lg);
  background: var(--kh-input-bg);
  color: var(--kh-color-text-primary);
  transition:
    border-color var(--kh-tx-fast),
    box-shadow var(--kh-tx-fast);
}
.kh-app-search-input::placeholder {
  color: var(--kh-color-text-muted);
}
.kh-app-search-input:focus {
  border-color: var(--kh-color-primary);
  box-shadow: var(--kh-focus-ring);
}
/* Narrow screens: stack the header so the search stays usable. */
@media (max-width: 640px) {
  .kh-app {
    padding: var(--kh-space-4);
  }
  .kh-app-toolbar {
    width: 100%;
  }
  .kh-app-search {
    flex: 1;
  }
  .kh-app-search-input {
    width: 100%;
  }
}
.kh-app-body {
  min-height: 200px;
}
.kh-app-loading {
  padding: 60px 20px;
  text-align: center;
  color: var(--kh-color-text-muted);
  font-size: var(--kh-fs-md);
}
</style>
