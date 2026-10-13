<script setup lang="ts">
/**
 * DatasetDetailDrawer — self-contained full-screen drawer that hosts the whole
 * dataset-detail experience without navigating away.
 *
 * Sliding in from the LEFT (occupies ~88vw so the workspace stays roomy), it
 * contains:
 *   - DatasetSidebar on the left (4 nav items switch content in-place)
 *   - Right pane switches between DocumentTable / stub / RecallTestingPanelV2
 *     / DatasetSettingsPanel based on the internal `tab` state
 *   - When a document row is clicked in DocumentTable, right pane in-place
 *     replaces with DocumentChunksView plus a "back" button — still inside the
 *     drawer, no route change
 *
 * All API access is delegated to the parent via props (`hub` object with
 * async methods). This keeps the component embeddable in any Vue 3 app.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { Drawer, Modal } from '../../ui';

import type {
  Chunk,
  DatasetDetailHub,
  DatasetSummary,
  DatasetTab,
  DocMetadata,
  DocumentRow,
  EmbeddingModelOption,
  ProcessRule,
  ParsedDocument,
  RecallHit,
  RecentQuery,
} from '../types';
import type { RetrievalMethod } from '../types/retrieval';
import AddDocumentsWizard from './AddDocumentsWizard.vue';
import DatasetSettingsPanel from './DatasetSettingsPanel.vue';
import DatasetSidebar from './DatasetSidebar.vue';
import DocumentChunksView from './DocumentChunksView.vue';
import DocumentTable from './DocumentTable.vue';
import RecallTestingPanelV2 from './RecallTestingPanelV2.vue';
import RagOperationsPanel from './RagOperationsPanel.vue';

interface Props {
  open: boolean;
  /** The dataset id to open. Watched — changing it reloads. */
  datasetId: null | string;
  hub: DatasetDetailHub;
  /** Optional starting tab. Defaults to 'documents'. Re-applied every open. */
  initialTab?: DatasetTab;
}

const props = defineProps<Props>();

const parsedDocument = ref<ParsedDocument | null>(null);
const parsedDocumentLoading = ref(false);

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'deleted', id: string): void;
  (e: 'refresh-list'): void;
}>();

// ---------- state
const dataset = ref<DatasetSummary | null>(null);
const documents = ref<DocumentRow[]>([]);
const documentsLoading = ref(false);

const tab = ref<DatasetTab>('documents');
const openDocId = ref<null | string>(null);
const segments = ref<Chunk[]>([]);
const metadata = ref<DocMetadata>({});
const segmentsLoading = ref(false);
const chunkPage = ref(1);
const chunkPageSize = ref(20);

const hits = ref<RecallHit[]>([]);
const recallHistory = ref<RecentQuery[]>([]);
const recallLoading = ref(false);

const embeddingModels = ref<EmbeddingModelOption[]>([]);
const defaultEmbeddingModelId = ref<null | string>(null);

// Segment editor modal state (owned by the drawer since the chunks view emits
// an event). `editorMode = 'add'` opens the same modal for creating a new
// segment via `hub.appendSegment`.
const editingSeg = ref<Chunk | null>(null);
const editContent = ref('');
const editorMode = ref<'add' | 'edit' | null>(null);
const editorSaving = ref(false);

// ---------- lifecycle: reload on open / id change
watch(
  () => [props.open, props.datasetId] as const,
  async ([isOpen, id]) => {
    if (!isOpen || !id) {
      dataset.value = null;
      documents.value = [];
      tab.value = 'documents';
      openDocId.value = null;
      return;
    }
    tab.value = props.initialTab ?? 'documents';
    await Promise.all([
      loadDataset(),
      loadDocuments(),
      loadEmbeddingModels(),
      loadRerankModels(),
      loadRecallHistory(),
    ]);
  },
  { immediate: true },
);

async function loadDataset() {
  if (!props.datasetId) return;
  try {
    dataset.value = await props.hub.loadDataset(props.datasetId);
  } catch {
    dataset.value = null;
  }
}
async function loadDocuments(options: { silent?: boolean } = {}) {
  if (!props.datasetId) return;
  if (!options.silent) documentsLoading.value = true;
  try {
    documents.value = await props.hub.listDocuments(props.datasetId);
  } finally {
    if (!options.silent) documentsLoading.value = false;
  }
  scheduleStatusPoll();
}

/**
 * When at least one document is in PROCESSING state, poll listDocuments
 * every 3s until they all settle. This is how the async ingest pipeline
 * surfaces "处理中 → 已完成" to the user without an SSE stream (SSE is a
 * nice next step but polling covers the demand for now).
 */
let statusPollTimer: ReturnType<typeof setTimeout> | null = null;
function scheduleStatusPoll() {
  if (statusPollTimer) {
    clearTimeout(statusPollTimer);
    statusPollTimer = null;
  }
  const anyPending = documents.value.some((d) => d.status === 'PROCESSING');
  if (!anyPending) return;
  statusPollTimer = setTimeout(() => {
    void loadDocuments({ silent: true });
  }, 3000);
}
onBeforeUnmount(() => {
  if (statusPollTimer) clearTimeout(statusPollTimer);
});
async function loadEmbeddingModels() {
  try {
    embeddingModels.value = await props.hub.listEmbeddingModels();
  } catch {
    embeddingModels.value = [];
  }
  // Best-effort default id — used by DatasetSettingsPanel to auto-select the
  // tenant default when the dataset has no embedding model set. Prefer the
  // dedicated endpoint if the adapter exposes it; otherwise derive from the
  // `isDefault` flag we already annotated on each row.
  try {
    if (props.hub.getDefaultEmbeddingModelId) {
      defaultEmbeddingModelId.value =
        (await props.hub.getDefaultEmbeddingModelId()) ?? null;
    } else {
      defaultEmbeddingModelId.value =
        embeddingModels.value.find((m) => m.isDefault)?.id ?? null;
    }
  } catch {
    defaultEmbeddingModelId.value =
      embeddingModels.value.find((m) => m.isDefault)?.id ?? null;
  }
}
async function loadRecallHistory() {
  if (!props.datasetId) return;
  try {
    recallHistory.value = await props.hub.listRecallHistory(props.datasetId, 20);
  } catch {
    recallHistory.value = [];
  }
}

// ---------- sidebar
async function onNav(next: DatasetTab) {
  // Reset the doc-drilldown when nav switches away from documents.
  if (next !== 'documents') openDocId.value = null;
  tab.value = next;
}
function onCopyApi() {
  if (props.datasetId && props.hub.onCopyApi) props.hub.onCopyApi(props.datasetId);
}
function close() {
  emit('update:open', false);
}

// ---------- documents tab — "+ 添加文件" opens the 3-step wizard rather than
// popping the native file picker straight away, mirroring the create-flow.
const addWizardOpen = ref(false);
function pickFiles() {
  addWizardOpen.value = true;
}
async function onAddWizardConfirm(payload: {
  files: File[];
  processRule?: ProcessRule;
}) {
  if (!props.datasetId || payload.files.length === 0) return;
  try {
    // If the user tweaked chunking on step 2 of the add wizard, persist those
    // edits onto the dataset first so the upload actually indexes with the
    // new rule. Skipped when the payload matches the current persisted rule
    // (nothing to update) so a bare "just upload more docs" click stays a
    // single request.
    if (payload.processRule && shouldPatchProcessRule(payload.processRule)) {
      await props.hub.updateDataset(props.datasetId, {
        processRule: payload.processRule,
      });
      await loadDataset();
    }
    await props.hub.uploadDocuments(props.datasetId, payload.files);
    await loadDocuments();
    emit('refresh-list');
  } catch {
    // errors surface via the host's onError hook — nothing to do here
  }
}

/**
 * Compare the wizard-emitted ProcessRule against the currently-persisted one
 * so the upload flow only PUTs when the user actually changed something. Deep
 * equality by JSON.stringify is fine for this shape (small, primitive-only).
 */
function shouldPatchProcessRule(next: ProcessRule): boolean {
  const current = parsedProcessRule.value ?? {};
  return JSON.stringify(next) !== JSON.stringify(current);
}

// Parsed once from the dataset so the wizard can display current chunking /
// retrieval defaults on step 2 without re-parsing on every render.
const parsedProcessRule = computed<ProcessRule>(() => {
  try {
    return dataset.value?.processRuleJson
      ? (JSON.parse(dataset.value.processRuleJson) as ProcessRule)
      : {};
  } catch {
    return {};
  }
});
const parsedRetrievalConfig = computed(() => {
  try {
    if (dataset.value?.retrievalConfigJson) {
      return JSON.parse(dataset.value.retrievalConfigJson) as {
        method?: RetrievalMethod;
        topK?: number;
        scoreThreshold?: number;
        rerankEnabled?: boolean;
        rerankModelId?: string;
        vectorWeight?: number;
      };
    }
  } catch {
    // fall through to default
  }
  return { method: 'HYBRID' as RetrievalMethod, topK: 3 };
});
const parsedRetrievalMethod = computed<RetrievalMethod>(
  () => parsedRetrievalConfig.value.method ?? 'HYBRID',
);
// Rerank models come from the hub if it exposes listRerankModels; the recall
// panel gracefully renders an empty dropdown otherwise.
const rerankModels = ref<Array<{ id: string; label: string }>>([]);
async function loadRerankModels() {
  if (!props.hub.listRerankModels) return;
  try {
    rerankModels.value = await props.hub.listRerankModels();
  } catch {
    rerankModels.value = [];
  }
}
async function onOpenDoc(d: DocumentRow) {
  if (!props.datasetId) return;
  openDocId.value = d.id;
  chunkPage.value = 1;
  segmentsLoading.value = true;
  try {
    const [segs, meta] = await Promise.all([
      props.hub.listSegments(
        props.datasetId,
        d.id,
        chunkPage.value,
        chunkPageSize.value,
      ),
      props.hub.loadDocumentMetadata(props.datasetId, d.id),
    ]);
    segments.value = segs;
    metadata.value = meta;
  } finally {
    segmentsLoading.value = false;
  }
}
async function loadSegmentPage() {
  if (!props.datasetId || !openDocId.value) return;
  segmentsLoading.value = true;
  try {
    segments.value = await props.hub.listSegments(
      props.datasetId,
      openDocId.value,
      chunkPage.value,
      chunkPageSize.value,
    );
  } finally {
    segmentsLoading.value = false;
  }
}
function onChunkPageChange(p: number) {
  chunkPage.value = p;
  loadSegmentPage();
}
function onChunkPageSizeChange(s: number) {
  chunkPageSize.value = s;
  chunkPage.value = 1;
  loadSegmentPage();
}
async function onDeleteDoc(d: DocumentRow) {
  if (!props.datasetId) return;
  await props.hub.deleteDocument(props.datasetId, d.id);
  await loadDocuments();
  emit('refresh-list');
}
async function onReparseDoc(d: DocumentRow) {
  if (!props.datasetId || !props.hub.reparseDocument) return;
  await props.hub.reparseDocument(props.datasetId, d.id);
  await loadDocuments();
}
async function onViewParsedDoc(d: DocumentRow) {
  if (!props.datasetId || !props.hub.getParsedDocument) return;
  parsedDocumentLoading.value = true;
  try {
    parsedDocument.value = await props.hub.getParsedDocument(props.datasetId, d.id);
  } finally {
    parsedDocumentLoading.value = false;
  }
}
async function onToggleDocEnabled(d: DocumentRow, next: boolean) {
  if (!props.datasetId || !props.hub.setDocumentEnabled) return;
  await props.hub.setDocumentEnabled(props.datasetId, d.id, next);
  await loadDocuments();
}

// ---------- chunks view (opened when openDocId is set)
const currentDoc = computed(() =>
  documents.value.find((d) => d.id === openDocId.value) ?? null,
);
function backToDocs() {
  openDocId.value = null;
  segments.value = [];
  metadata.value = {};
  chunkPage.value = 1;
}
async function onToggleChunk(c: Chunk, next: boolean) {
  if (!props.datasetId || !openDocId.value) return;
  const updated = await props.hub.setSegmentEnabled(
    props.datasetId,
    openDocId.value,
    c.id,
    next,
  );
  const idx = segments.value.findIndex((s) => s.id === c.id);
  if (idx !== -1) segments.value[idx] = { ...segments.value[idx], enabled: !!updated.enabled };
}
async function onDeleteChunk(c: Chunk) {
  if (!props.datasetId || !openDocId.value) return;
  await props.hub.deleteSegment(props.datasetId, openDocId.value, c.id);
  metadata.value = await props.hub.loadDocumentMetadata(
    props.datasetId,
    openDocId.value,
  );
  await loadSegmentPage();
}
function onEditChunk(c: Chunk) {
  editorMode.value = 'edit';
  editingSeg.value = c;
  editContent.value = c.content;
}
function onAddChunk() {
  editorMode.value = 'add';
  editingSeg.value = null;
  editContent.value = '';
}
async function saveChunkEdit() {
  if (!props.datasetId || !openDocId.value || editorSaving.value) return;
  const content = editContent.value.trim();
  if (!content) return;

  editorSaving.value = true;
  try {
    if (editorMode.value === 'add') {
      if (!props.hub.appendSegment) return;
      await props.hub.appendSegment(props.datasetId, openDocId.value, content);
      // Refresh both the current page and the metadata so total + avg update.
      metadata.value = await props.hub.loadDocumentMetadata(
        props.datasetId,
        openDocId.value,
      );
      await loadSegmentPage();
    } else if (editorMode.value === 'edit' && editingSeg.value) {
      const updated = await props.hub.updateSegment(
        props.datasetId,
        openDocId.value,
        editingSeg.value.id,
        content,
      );
      const idx = segments.value.findIndex((s) => s.id === editingSeg.value!.id);
      if (idx !== -1) {
        segments.value[idx] = {
          ...segments.value[idx],
          content: updated.content,
          charCount: updated.content?.length ?? 0,
          tokenCount: updated.tokenCount,
        };
      }
    }
    cancelChunkEdit();
  } finally {
    editorSaving.value = false;
  }
}
function cancelChunkEdit() {
  editingSeg.value = null;
  editContent.value = '';
  editorMode.value = null;
}

// ---------- recall
async function onRunRecall(payload: {
  method: string;
  query: string;
  config?: {
    topK?: number;
    scoreThreshold?: number;
    rerankEnabled?: boolean;
    rerankModelId?: string;
    vectorWeight?: number;
  };
}) {
  if (!props.datasetId) return;
  recallLoading.value = true;
  try {
    // Forward the popover's full config so topK / score / rerank land on the
    // backend request (previously fixed at topK=10, ignoring the picker).
    hits.value = await props.hub.retrieve(props.datasetId, {
      query: payload.query,
      method: payload.method,
      topK: payload.config?.topK ?? 10,
      scoreThreshold: payload.config?.scoreThreshold,
      rerankEnabled: payload.config?.rerankEnabled,
      rerankModelId: payload.config?.rerankModelId,
      vectorWeight: payload.config?.vectorWeight,
    } as any);
    await loadRecallHistory();
  } finally {
    recallLoading.value = false;
  }
}

// ---------- settings form (adapts dataset to DatasetSettingsPanel shape)
//
// Reads BOTH indexingTechnique / retrievalConfig AND the chunking mode from
// the persisted processRule so the panel opens showing the actual current
// setup (previously chunkMode was hard-coded to 'general', hiding whatever
// the user had configured at create time).
const settingsForm = computed(() => {
  if (!dataset.value) return { id: '', name: '', chunkMode: 'general' as const };
  let retrievalConfig: any = {};
  try {
    if (dataset.value.retrievalConfigJson) {
      retrievalConfig = JSON.parse(dataset.value.retrievalConfigJson);
    }
  } catch {
    retrievalConfig = {};
  }
  let processRule: ProcessRule = {};
  try {
    if (dataset.value.processRuleJson) {
      processRule = JSON.parse(dataset.value.processRuleJson);
    }
  } catch {
    processRule = {};
  }
  return {
    id: dataset.value.id,
    name: dataset.value.name,
    description: dataset.value.description ?? '',
    icon: '📙',
    iconBg: '#FFEAD5',
    permission: 'ONLY_ME' as const,
    chunkMode:
      processRule.template === 'PARENT_CHILD'
        ? ('parent-child' as const)
        : processRule.template === 'QA'
          ? ('qa' as const)
          : processRule.template === 'STRUCTURE_AWARE'
            ? ('structure-aware' as const)
            : ('general' as const),
    // Expose chunk params so the panel's per-mode inputs pre-fill instead of
    // showing wizard defaults every time.
    chunkTokens: processRule.chunkTokens,
    overlapTokens: processRule.overlapTokens,
    parentMode: processRule.parentMode,
    parentChunkTokens: processRule.parentChunkTokens,
    removeExtraWhitespace: processRule.removeExtraWhitespace,
    removeUrlsEmails: processRule.removeUrlsEmails,
    indexingTechnique: dataset.value.indexingTechnique ?? 'HIGH_QUALITY',
    keywordCount: 10,
    embeddingModelId: dataset.value.embeddingModelId,
    autoSummary: false,
    retrievalMethod: (retrievalConfig.method ?? 'VECTOR') as
      | 'FULL_TEXT'
      | 'HYBRID'
      | 'VECTOR',
    rerankEnabled: !!retrievalConfig.rerankEnabled,
    retrievalConfig,
  };
});

async function onSaveSettings(payload: any) {
  if (!props.datasetId) return;
  // Rebuild a full ProcessRule from the panel's chunk-mode + per-mode inputs
  // so switching mode in settings actually persists to the backend. Backends
  // that don't understand a given field just ignore it (schema is additive).
  const template =
    payload.chunkMode === 'parent-child'
      ? 'PARENT_CHILD'
      : payload.chunkMode === 'qa'
        ? 'QA'
        : payload.chunkMode === 'structure-aware'
          ? 'STRUCTURE_AWARE'
        : 'NAIVE';
  const processRule: ProcessRule = {
    template,
    chunkTokens: payload.chunkTokens,
    overlapTokens: payload.overlapTokens,
    parentMode: payload.parentMode,
    parentChunkTokens: payload.parentChunkTokens,
    removeExtraWhitespace: payload.removeExtraWhitespace,
    removeUrlsEmails: payload.removeUrlsEmails,
    protectStructuredBlocks: payload.chunkMode === 'structure-aware',
    includeHeadingContext: payload.chunkMode === 'structure-aware',
  };
  await props.hub.updateDataset(props.datasetId, {
    name: payload.name,
    description: payload.description,
    embeddingModelId: payload.embeddingModelId,
    indexingTechnique: payload.indexingTechnique,
    processRule,
    retrievalConfig: payload.retrievalConfig ?? {
      method: payload.retrievalMethod,
      topK: 10,
      rerankEnabled: !!payload.rerankEnabled,
    },
  });
  await loadDataset();
  emit('refresh-list');
}

// ---------- sidebar data
const sidebarData = computed(() => ({
  id: props.datasetId ?? '',
  name: dataset.value?.name ?? '加载中...',
  description: dataset.value?.description,
  documentCount: documents.value.length,
  segmentCount: dataset.value?.segmentCount ?? 0,
}));
</script>

<template>
  <Drawer
    :open="open"
    class="kh-dataset-drawer"
    placement="left"
    width="88vw"
    :closable="false"
    :body-style="{ padding: '0', overflow: 'hidden' }"
    @update:open="emit('update:open', $event)"
  >
      <template #title>
        <div class="kh-drawer-topbar-title">
          <template v-if="openDocId && currentDoc">
            <button
              type="button"
              class="kh-drawer-back"
              aria-label="返回文档列表"
              @click="backToDocs"
            >
              <span class="kh-drawer-back-icon" aria-hidden="true">←</span>
              <span>返回文档列表</span>
            </button>
            <span class="kh-drawer-title-divider" aria-hidden="true" />
            <span class="kh-drawer-doc-name">📄 {{ currentDoc.name }}</span>
          </template>
          <template v-else>
            <span class="kh-drawer-title-name">{{ dataset?.name ?? '知识库详情' }}</span>
            <span class="kh-drawer-title-tab">
              · {{ {
                documents: '文档', recall: '召回测试',
                operations: 'RAG 运行', settings: '设置',
              }[tab] }}
            </span>
          </template>
        </div>
      </template>
      <template #extra>
        <button class="kh-drawer-close" aria-label="关闭" @click="close">×</button>
      </template>
      <div class="kh-drawer-shell">
        <DatasetSidebar
          :dataset="sidebarData"
          :active="tab"
          @nav="onNav"
          @copy-api="onCopyApi"
        />

        <div class="kh-drawer-main">
          <!-- Content -->
          <div class="kh-drawer-content">
            <!-- Chunks view overlays everything else while a document is open -->
            <template v-if="openDocId && currentDoc">
              <DocumentChunksView
                :document-name="currentDoc.name"
                :document-enabled="currentDoc.enabled"
                :chunks="segments"
                :metadata="metadata"
                :loading="segmentsLoading"
                :can-append="!!hub.appendSegment"
                :page="chunkPage"
                :page-size="chunkPageSize"
                :total="metadata.totalChunks ?? 0"
                @toggle-doc-enabled="(v: boolean) => onToggleDocEnabled(currentDoc!, v)"
                @edit-chunk="onEditChunk"
                @toggle-chunk="onToggleChunk"
                @delete-chunk="onDeleteChunk"
                @add-chunk="onAddChunk"
                @update:page="onChunkPageChange"
                @update:page-size="onChunkPageSizeChange"
              />
            </template>
            <template v-else>
              <DocumentTable
                v-if="tab === 'documents'"
                :documents="documents"
                :loading="documentsLoading"
                @add-file="pickFiles"
                @open="onOpenDoc"
                @toggle-enabled="onToggleDocEnabled"
                @delete="onDeleteDoc"
                @reparse="onReparseDoc"
                @view-parsed="onViewParsedDoc"
              />
              <RecallTestingPanelV2
                v-else-if="tab === 'recall'"
                :hits="hits"
                :history="recallHistory"
                :loading="recallLoading"
                :initial-config="parsedRetrievalConfig"
                :rerank-models="rerankModels"
                :indexing-technique="dataset?.indexingTechnique"
                @run="onRunRecall"
              />
              <RagOperationsPanel
                v-else-if="tab === 'operations' && datasetId"
                :dataset-id="datasetId"
                :hub="hub"
              />
              <DatasetSettingsPanel
                v-else-if="tab === 'settings'"
                :dataset="settingsForm"
                :document-count="documents.length"
                :embedding-models="embeddingModels"
                :default-embedding-model-id="defaultEmbeddingModelId"
                :rerank-models="rerankModels"
                @save="onSaveSettings"
              />
            </template>
          </div>
        </div>

        <!-- Add-documents wizard (3-step, upload → 分段配置 → 完成). The
             preview-chunks callback runs through the hub so what the user
             sees on step 2 is the real chunker output, not a mock. -->
        <AddDocumentsWizard
          :open="addWizardOpen"
          :dataset-name="dataset?.name"
          :process-rule="parsedProcessRule"
          :retrieval-method="parsedRetrievalMethod"
          :indexing-technique="dataset?.indexingTechnique"
          :preview-chunks="hub.previewChunks"
          @update:open="addWizardOpen = $event"
          @confirm="onAddWizardConfirm"
        />

        <!-- Add & edit reuse the project-wide modal. -->
        <Modal
          :open="!!editorMode"
          :title="editorMode === 'add' ? '添加分段' : '编辑分段'"
          :width="720"
          ok-text="保存"
          cancel-text="取消"
          centered
          :confirm-loading="editorSaving"
          :mask-closable="!editorSaving"
          :keyboard="!editorSaving"
          :closable="!editorSaving"
          :ok-button-props="{ disabled: !editContent.trim() || editorSaving }"
          @ok="saveChunkEdit"
          @update:open="(open: boolean) => { if (!open && !editorSaving) cancelChunkEdit(); }"
        >
          <div class="kh-drawer-editor-content">
            <textarea
              v-model="editContent"
              class="kh-drawer-editor-textarea"
              placeholder="分段内容..."
            />
            <div class="kh-drawer-editor-hint">
              {{
                editorMode === 'add'
                  ? '新分段会被追加到当前文档末尾，并自动计算 embedding 入向量库。'
                  : '保存后会重新计算 embedding 并入向量库，可能需要几秒。'
              }}
            </div>
          </div>
        </Modal>
      </div>
      <div v-if="parsedDocument || parsedDocumentLoading" class="kh-parsed-mask" @click.self="parsedDocument = null">
        <section class="kh-parsed-panel">
          <header>
            <div>
              <strong>{{ parsedDocument?.filename ?? '解析结构' }}</strong>
              <small v-if="parsedDocument">{{ parsedDocument.parser }} · {{ parsedDocument.pageCount }} 页 · {{ parsedDocument.blocks.length }} 块</small>
            </div>
            <button class="kh-drawer-close" @click="parsedDocument = null">×</button>
          </header>
          <p v-if="parsedDocumentLoading">正在加载解析结果…</p>
          <div v-else class="kh-parsed-blocks">
            <article v-for="block in parsedDocument?.blocks ?? []" :key="block.index">
              <span>{{ block.type }} · P{{ block.page ?? '-' }}<template v-if="block.headingPath"> · {{ block.headingPath }}</template></span>
              <pre>{{ block.text }}</pre>
            </article>
          </div>
        </section>
      </div>
  </Drawer>
</template>

<style scoped>
.kh-dataset-drawer {
  max-width: 1600px;
}
.kh-drawer-shell {
  width: 100%;
  height: 100%;
  background: #fff;
  display: flex;
}

.kh-drawer-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.kh-drawer-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #f1f5f9;
}
.kh-drawer-topbar-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: inherit;
}
.kh-drawer-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px 6px 8px;
  border: 1px solid var(--kh-color-primary);
  border-radius: var(--kh-radius-md);
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--kh-tx-fast),
    border-color var(--kh-tx-fast),
    transform var(--kh-tx-fast),
    box-shadow var(--kh-tx-fast);
}
.kh-drawer-back:hover {
  border-color: var(--kh-color-primary-strong);
  background: var(--kh-color-primary);
  color: var(--kh-color-primary-contrast);
  box-shadow: var(--kh-shadow-sm);
  transform: translateX(-1px);
}
.kh-drawer-back:focus-visible {
  outline: none;
  box-shadow: var(--kh-focus-ring);
}
.kh-drawer-back-icon {
  font-size: 16px;
  line-height: 1;
}
.kh-drawer-title-divider {
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: var(--kh-color-border);
}
.kh-drawer-doc-name {
  min-width: 0;
  overflow: hidden;
  color: var(--kh-color-text-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-drawer-title-name {
  color: #0f172a;
  font-weight: 600;
}
.kh-drawer-title-tab {
  color: #94a3b8;
}
.kh-drawer-close {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.kh-drawer-close:hover {
  background: #f1f5f9;
  color: #475569;
}

.kh-drawer-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.kh-drawer-stub {
  padding: 80px 40px;
  text-align: center;
  color: #94a3b8;
}
.kh-drawer-stub-icon {
  font-size: 42px;
  opacity: 0.5;
}
.kh-drawer-stub-title {
  margin-top: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #64748b;
}
.kh-drawer-stub-sub {
  margin-top: 6px;
  font-size: 12px;
}

/* Chunk editor content rendered inside the shared Modal component. */
.kh-drawer-editor-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kh-drawer-editor-textarea {
  min-height: 240px;
  max-height: 60vh;
  padding: 10px 12px;
  border: 1px solid var(--kh-input-border);
  border-radius: 8px;
  background: var(--kh-input-bg);
  outline: none;
  font-family: inherit;
  font-size: 13px;
  color: var(--kh-color-text-primary);
  resize: vertical;
}
.kh-drawer-editor-textarea::placeholder {
  color: var(--kh-color-text-muted);
}
.kh-drawer-editor-textarea:focus {
  border-color: var(--kh-color-primary);
  box-shadow: 0 0 0 3px var(--kh-color-primary-soft);
}
.kh-drawer-editor-hint {
  font-size: 11px;
  color: var(--kh-color-text-muted);
}
.kh-btn {
  padding: 6px 18px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.kh-btn-primary {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
}
.kh-btn-primary:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
.kh-btn-secondary {
  background: #f1f5f9;
  color: #475569;
}
.kh-btn-secondary:hover {
  background: #e2e8f0;
}
.kh-parsed-mask { position: fixed; inset: 0; z-index: 10020; display: flex; justify-content: flex-end; background: rgba(15,23,42,.35); }
.kh-parsed-panel { width: min(720px, 88vw); height: 100%; padding: 20px; overflow: hidden; background: #fff; box-shadow: -10px 0 30px rgba(15,23,42,.15); }
.kh-parsed-panel header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 14px; border-bottom: 1px solid #e2e8f0; }
.kh-parsed-panel header div { display: flex; flex-direction: column; gap: 4px; }
.kh-parsed-panel small, .kh-parsed-blocks article > span { color: #64748b; font-size: 12px; }
.kh-parsed-blocks { height: calc(100% - 62px); overflow: auto; padding-top: 12px; }
.kh-parsed-blocks article { padding: 10px 12px; margin-bottom: 8px; border: 1px solid #e2e8f0; border-radius: 8px; }
.kh-parsed-blocks pre { margin: 6px 0 0; white-space: pre-wrap; word-break: break-word; font: inherit; color: #1e293b; }
</style>
