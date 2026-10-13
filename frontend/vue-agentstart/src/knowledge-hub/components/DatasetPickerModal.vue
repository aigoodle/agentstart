<script setup lang="ts">
/**
 * DatasetPickerModal — Dify-style "选择引用知识库" modal.
 *
 * One shared picker for every place that lets the user attach knowledge bases
 * to something (AppDesignDrawer 知识库 card, workflow KNOWLEDGE_RETRIEVAL node
 * config, etc.). It fetches the catalog itself via {@link useKnowledge}, but
 * a caller with a pre-loaded list can bypass the fetch by passing `datasets`.
 *
 * Usage (v-model:open):
 *
 *   <DatasetPickerModal
 *     v-model:open="open"
 *     :initial-selected-ids="ids"
 *     :tenant-id="tenantId"
 *     @submit="onDatasetsChosen"
 *   />
 *
 * The `submit` event carries the FULL {@link DatasetSummary} objects for the
 * caller to render — most callers want name + indexingTechnique badges without
 * having to look them up again.
 */
import { computed, onMounted, ref, watch } from 'vue';

import { Modal } from '../../ui';
import { useKnowledge } from '../composables/useKnowledge';
import type { DatasetSummary, RetrievalConfig } from '../types';

interface Props {
  /** v-model:open — controls modal visibility. */
  open: boolean;
  /** Pre-selected dataset ids. */
  initialSelectedIds?: string[];
  /** Optional pre-loaded catalog to skip the fetch. */
  datasets?: DatasetSummary[];
  /** Multi-tenant filter passed to the /datasets list endpoint. */
  tenantId?: string;
  /** Header text — override for custom flows. */
  title?: string;
  /** When true, only one dataset can be selected. */
  single?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  initialSelectedIds: () => [],
  datasets: undefined,
  tenantId: undefined,
  title: '选择引用知识库',
  single: false,
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  /** User confirmed — carries full dataset objects for the caller to render. */
  (e: 'submit', datasets: DatasetSummary[]): void;
  (e: 'cancel'): void;
}>();

// ---------------------------------------------------------------------------
// Catalog loading
// ---------------------------------------------------------------------------
const catalog = ref<DatasetSummary[]>([]);
const loading = ref(false);
const loadError = ref<string | null>(null);

async function loadCatalog() {
  if (props.datasets) {
    catalog.value = [...props.datasets];
    return;
  }
  loading.value = true;
  loadError.value = null;
  try {
    const { listDatasets } = useKnowledge();
    catalog.value = await listDatasets(props.tenantId);
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e);
    catalog.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (props.open) loadCatalog();
});

watch(
  () => props.open,
  (open) => {
    if (open) {
      selected.value = new Set(props.initialSelectedIds);
      search.value = '';
      loadCatalog();
    }
  },
);

watch(
  () => props.datasets,
  (v) => {
    if (v) catalog.value = [...v];
  },
);

// ---------------------------------------------------------------------------
// Search + selection
// ---------------------------------------------------------------------------
const search = ref('');
const selected = ref<Set<string>>(new Set(props.initialSelectedIds));

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return catalog.value;
  return catalog.value.filter(
    (d) =>
      (d.name ?? '').toLowerCase().includes(q) ||
      (d.description ?? '').toLowerCase().includes(q),
  );
});

function toggle(id: string) {
  if (props.single) {
    selected.value = new Set(selected.value.has(id) ? [] : [id]);
    return;
  }
  const next = new Set(selected.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selected.value = next;
}

function isSelected(id: string): boolean {
  return selected.value.has(id);
}

// ---------------------------------------------------------------------------
// Retrieval-method badge (parsed from dataset.retrievalConfigJson) — matches
// the "混合检索 / 向量检索 / 全文检索" labels shown next to each row in Dify.
// ---------------------------------------------------------------------------
function methodLabel(d: DatasetSummary): null | string {
  const raw = d.retrievalConfigJson;
  if (!raw) return null;
  try {
    const cfg = JSON.parse(raw) as RetrievalConfig;
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

function techniqueLabel(d: DatasetSummary): null | string {
  if (!d.indexingTechnique) return null;
  return d.indexingTechnique === 'ECONOMY' ? '经济' : '高质量';
}

// ---------------------------------------------------------------------------
// Emit helpers
// ---------------------------------------------------------------------------
function close() {
  emit('update:open', false);
}
function onCancel() {
  emit('cancel');
  close();
}
function onSubmit() {
  const byId = new Map(catalog.value.map((d) => [d.id, d]));
  const out: DatasetSummary[] = [];
  for (const id of selected.value) {
    const d = byId.get(id);
    if (d) out.push(d);
  }
  emit('submit', out);
  close();
}

</script>

<template>
  <Modal
    :open="open"
    class="kh-dpm-panel"
    centered
    :width="480"
    :title="title"
    @cancel="onCancel"
  >
        <div class="kh-dpm-search">
          <span class="kh-dpm-search-icon" aria-hidden="true">🔍</span>
          <input
            v-model="search"
            class="kh-dpm-search-input"
            placeholder="搜索知识库..."
          />
        </div>

        <div class="kh-dpm-list">
          <div v-if="loading" class="kh-dpm-state">加载中…</div>
          <div v-else-if="loadError" class="kh-dpm-state kh-dpm-state-error">
            加载失败：{{ loadError }}
          </div>
          <div v-else-if="filtered.length === 0" class="kh-dpm-state">
            {{ catalog.length === 0 ? '暂无可选知识库' : '未匹配到任何知识库' }}
          </div>
          <button
            v-for="d in filtered"
            v-else
            :key="d.id"
            :class="['kh-dpm-item', { 'kh-dpm-item-on': isSelected(d.id) }]"
            :aria-pressed="isSelected(d.id)"
            @click="toggle(d.id)"
          >
            <span class="kh-dpm-item-icon" aria-hidden="true">📚</span>
            <span class="kh-dpm-item-body">
              <span class="kh-dpm-item-name" :title="d.name">
                {{ d.name }}
              </span>
              <span v-if="d.description" class="kh-dpm-item-desc">
                {{ d.description }}
              </span>
            </span>
            <span class="kh-dpm-item-tags">
              <span v-if="techniqueLabel(d)" class="kh-dpm-tag">
                {{ techniqueLabel(d) }}
              </span>
              <span v-if="methodLabel(d)" class="kh-dpm-tag kh-dpm-tag-muted">
                {{ methodLabel(d) }}
              </span>
              <span v-if="d.documentCount != null" class="kh-dpm-tag kh-dpm-tag-muted">
                {{ d.documentCount }} 文档
              </span>
            </span>
            <span class="kh-dpm-check" aria-hidden="true">
              <span v-if="isSelected(d.id)">✓</span>
            </span>
          </button>
        </div>

        <template #footer>
        <div class="kh-dpm-footer">
          <span class="kh-dpm-count">
            {{ selected.size }} 个知识库被选中
          </span>
          <div class="kh-dpm-actions">
            <button class="kh-dpm-btn kh-dpm-btn-ghost" @click="onCancel">
              取消
            </button>
            <button
              class="kh-dpm-btn kh-dpm-btn-primary"
              :disabled="selected.size === 0"
              @click="onSubmit"
            >
              添加
            </button>
          </div>
        </div>
        </template>
  </Modal>
</template>

<style scoped>
.kh-dpm-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: kh-dpm-fade 0.14s ease-out;
}
@keyframes kh-dpm-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.kh-dpm-panel { max-height: calc(100vh - 80px); }
@keyframes kh-dpm-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.kh-dpm-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}
.kh-dpm-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.kh-dpm-close {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
}
.kh-dpm-close:hover {
  background: #f1f5f9;
  color: #475569;
}

.kh-dpm-search {
  position: relative;
  padding: 12px 18px 8px;
}
.kh-dpm-search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-40%);
  font-size: 12px;
  color: #94a3b8;
  pointer-events: none;
}
.kh-dpm-search-input {
  width: 100%;
  padding: 8px 12px 8px 30px;
  font-size: 13px;
  color: #0f172a;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.kh-dpm-search-input:focus {
  outline: none;
  background: #fff;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.kh-dpm-list {
  flex: 1;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.kh-dpm-state {
  padding: 40px 12px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}
.kh-dpm-state-error {
  color: #dc2626;
}
.kh-dpm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-dpm-item:hover {
  border-color: #c7d2fe;
  background: #f8fafc;
}
.kh-dpm-item-on {
  border-color: #6366f1;
  background: #eef2ff;
}
.kh-dpm-item-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 6px;
  font-size: 14px;
  flex-shrink: 0;
}
.kh-dpm-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kh-dpm-item-name {
  font-size: 13px;
  font-weight: 500;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kh-dpm-item-desc {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kh-dpm-item-tags {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.kh-dpm-tag {
  padding: 1px 6px;
  font-size: 10px;
  color: #4338ca;
  background: #e0e7ff;
  border-radius: 4px;
  white-space: nowrap;
}
.kh-dpm-tag-muted {
  color: #64748b;
  background: #f1f5f9;
}
.kh-dpm-check {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid #cbd5e1;
  border-radius: 4px;
  color: #fff;
  font-size: 11px;
  flex-shrink: 0;
}
.kh-dpm-item-on .kh-dpm-check {
  border-color: #6366f1;
  background: #6366f1;
}

.kh-dpm-footer {
  padding: 12px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
}
.kh-dpm-count {
  font-size: 12px;
  color: #64748b;
}
.kh-dpm-actions {
  display: flex;
  gap: 8px;
}
.kh-dpm-btn {
  padding: 5px 14px;
  font-size: 13px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.kh-dpm-btn-ghost {
  color: #64748b;
  background: #fff;
  border: 1px solid #e5e7eb;
}
.kh-dpm-btn-ghost:hover {
  background: #f8fafc;
}
.kh-dpm-btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
}
.kh-dpm-btn-primary:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
</style>
