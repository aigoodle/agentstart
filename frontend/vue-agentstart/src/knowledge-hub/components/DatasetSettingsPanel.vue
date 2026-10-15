<script setup lang="ts">
import { Popover, PopoverTrigger, PopoverContent } from '../../ui/shadcn/popover';
import { Card as AsShadcnCard } from '../../ui/shadcn/card';

import { ShadcnBoolean as AsShadcnBoolean } from '../../ui/components/ShadcnBoolean';
import { ShadcnRange as AsShadcnRange } from '../../ui/components/ShadcnRange';

import { Button as AsShadcnButton } from '../../ui/shadcn/button';
import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Textarea as AsShadcnTextarea } from '../../ui/shadcn/textarea';
import { ShadcnSelect as AsShadcnSelect } from '../../ui/components/ShadcnSelect';

/**
 * DatasetSettingsPanel — 设置 tab (screenshot 6).
 *
 * Vertical stack of "label + control" rows, matching Dify's layout:
 *   名称和图标, 描述, 可见权限, 分段模式 (3 stacked cards),
 *   索引模式 (2 cards, keyword-count slider inside 经济), Embedding 模型,
 *   摘要自动生成 toggle, 检索设置 (向量检索 selected card + Rerank switch).
 */
import { computed, ref, watch } from 'vue';

import GroupedModelSelect, {
  type GroupedModelSelectOption,
} from '../../model-provider-hub/components/GroupedModelSelect.vue';
import type { EmbeddingModelOption } from '../types/api';
import type { RetrievalConfig } from '../types/dataset';
import RetrievalMethodPicker from './RetrievalMethodPicker.vue';

interface DatasetForm {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  iconBg?: string;
  permission?: 'ONLY_ME' | 'ORG_MEMBERS';
  chunkMode?: 'general' | 'parent-child' | 'qa' | 'structure-aware';
  // Per-chunk-mode parameters — pre-seeded from the dataset's persisted
  // processRule so the panel opens showing the actual current setup. Empty
  // values fall back to the same wizard defaults (1024 / 50 / PARAGRAPH / …).
  chunkTokens?: number;
  overlapTokens?: number;
  parentMode?: 'FULL_DOC' | 'PARAGRAPH';
  parentChunkTokens?: number;
  removeExtraWhitespace?: boolean;
  removeUrlsEmails?: boolean;
  indexingTechnique?: 'ECONOMY' | 'HIGH_QUALITY';
  keywordCount?: number;
  embeddingModelId?: string;
  autoSummary?: boolean;
  /**
   * Retrieval settings live in one config object v-model'd through the
   * shared {@link RetrievalMethodPicker}. Kept optional for backward compat
   * with hosts still constructing forms by legacy field names — the parent
   * derives {@code retrievalMethod} etc. from this on save.
   */
  retrievalConfig?: RetrievalConfig;
  // Legacy — kept so existing DatasetDetailDrawer field-by-field save code
  // still compiles. Panel wraps them into {@code retrievalConfig} on mount.
  retrievalMethod?: 'FULL_TEXT' | 'HYBRID' | 'VECTOR';
  rerankEnabled?: boolean;
  rerankModelId?: string;
}

interface Props {
  dataset: DatasetForm;
  /** Route retrieval settings to the unified RAG workbench when embedded in the drawer. */
  retrievalManagedExternally?: boolean;
  embeddingModels?: EmbeddingModelOption[];
  rerankModels?: Array<{ id: string; label: string }>;
  /**
   * Total documents already ingested for this dataset. When > 0 the chunk-mode
   * cards are locked: switching the mode after indexing would create chunks
   * inconsistent with the ones already in the vector store. Users are told to
   * re-create the dataset instead.
   */
  documentCount?: number;
  /**
   * Tenant-scoped default embedding model id from
   * {@code /models/defaults} — auto-selected when the dataset itself has no
   * {@code embeddingModelId} yet, so a fresh dataset opens with the shared
   * default rather than "选择一个 Embedding 模型" placeholder.
   */
  defaultEmbeddingModelId?: null | string;
}

const props = withDefaults(defineProps<Props>(), {
  retrievalManagedExternally: false,
  embeddingModels: () => [],
  rerankModels: () => [],
  documentCount: 0,
  defaultEmbeddingModelId: null,
});

const emit = defineEmits<{
  (e: 'save', payload: Partial<DatasetForm>): void;
  (e: 'open-rag'): void;
}>();

const form = ref<DatasetForm>({ ...props.dataset });

// Retrieval settings are v-model'd through the shared picker as a single
// RetrievalConfig; local ref stays synced with the legacy per-field props so
// callers that still read {@code retrievalMethod} / {@code rerankEnabled}
// separately keep working during the migration.
const retrievalConfig = ref<RetrievalConfig>({
  method: props.dataset.retrievalMethod ?? 'HYBRID',
  topK: 3,
  vectorWeight: 0.7,
  rerankEnabled: props.dataset.rerankEnabled ?? false,
  rerankModelId: props.dataset.rerankModelId,
});

// Track "dirty" so we can enable the sticky save button. Dify does this too.
const dirty = ref(false);
/**
 * When we seed the form ourselves (loading the dataset, auto-picking the
 * tenant default embedding model), suppress the deep watcher's dirty flip.
 * Without this the sticky footer would appear the moment the panel opens.
 */
let seeding = false;

watch(
  () => props.dataset,
  (d) => {
    seeding = true;
    form.value = { ...d };
    retrievalConfig.value = {
      ...(d.retrievalConfig ?? {}),
      method: d.retrievalMethod ?? d.retrievalConfig?.method ?? 'HYBRID',
      topK: d.retrievalConfig?.topK ?? 3,
      vectorWeight: d.retrievalConfig?.vectorWeight ?? 0.7,
      rerankEnabled: d.rerankEnabled ?? d.retrievalConfig?.rerankEnabled ?? false,
      rerankModelId: d.rerankModelId ?? d.retrievalConfig?.rerankModelId,
    };
    dirty.value = false;
    void Promise.resolve().then(() => {
      seeding = false;
    });
  },
);

watch(
  form,
  () => {
    if (seeding) return;
    dirty.value = true;
  },
  { deep: true },
);

// Picker changes → mirror back into form so `save()` sends everything on the
// same emit shape callers already expect.
watch(
  retrievalConfig,
  (c) => {
    dirty.value = true;
    form.value.retrievalMethod = c.method;
    form.value.rerankEnabled = c.rerankEnabled;
    form.value.rerankModelId = c.rerankModelId;
    form.value.retrievalConfig = { ...c };
  },
  { deep: true },
);

const CHUNK_MODES = [
  {
    id: 'structure-aware' as const,
    icon: '§',
    title: 'Structure-aware',
    desc: '保留标题路径、列表、表格和代码块边界，适合结构化文档。',
  },
  {
    id: 'general' as const,
    icon: '≡',
    title: 'General',
    desc: '通过文本分块模式，检索和召回块获取相关部分。',
  },
  {
    id: 'parent-child' as const,
    icon: '👥',
    title: 'Parent-Child',
    desc: '使用父子文档结构，子块用于检索，父块用作上下文。',
  },
  {
    id: 'qa' as const,
    icon: '💬',
    title: 'Q&A',
    desc: '使用 Q&A 分段，将检索到的内容作为知识源，构建更精细的问题-答案配对。',
  },
];

/**
 * Chunk-mode cards lock once the dataset has at least one document — reindexing
 * across incompatible chunk shapes is an offline op we don't want users to
 * accidentally trigger from settings. UI shows a hint pointing at "重新创建
 * 知识库" instead.
 */
const chunkModeLocked = computed(() => (props.documentCount ?? 0) > 0);
function selectChunkMode(id: 'general' | 'parent-child' | 'qa' | 'structure-aware') {
  if (chunkModeLocked.value) return;
  form.value.chunkMode = id;
}

// ---- Embedding model picker options ----
// The grouped dropdown lives in the shared `GroupedModelSelect`. We just adapt
// the wire shape (`EmbeddingModelOption`) to the picker's option shape here —
// everything else (grouping, search, brand icons) lives in the shared
// component so 系统默认模型 uses the same UX.
const embedOptions = computed<GroupedModelSelectOption[]>(() =>
  props.embeddingModels.map((m) => ({
    id: m.id,
    label: m.label,
    providerName: m.providerName,
    providerLabel: m.providerLabel,
    isDefault: m.isDefault,
  })),
);
function onPickEmbedding(id: null | string) {
  form.value.embeddingModelId = id ?? undefined;
}

/**
 * Auto-select the tenant default embedding model when the dataset opens with
 * an empty `embeddingModelId`. Fires whenever the default id or the model list
 * changes so hosts that load these async still get the preselection.
 */
watch(
  () => [
    props.defaultEmbeddingModelId,
    props.embeddingModels.map((m) => m.id).join('|'),
  ],
  () => {
    if (form.value.embeddingModelId) return;
    const defaultId =
      props.defaultEmbeddingModelId ??
      props.embeddingModels.find((m) => m.isDefault)?.id ??
      null;
    if (!defaultId) return;
    if (!props.embeddingModels.some((m) => m.id === defaultId)) return;
    seeding = true;
    form.value.embeddingModelId = defaultId;
    // Release the guard on the next tick so any legit user edit right after
    // still flips dirty.
    void Promise.resolve().then(() => {
      seeding = false;
    });
  },
  { immediate: true },
);

const canSave = computed(() => dirty.value && form.value.name.trim().length > 0);

function save() {
  if (!canSave.value) return;
  emit('save', { ...form.value });
  dirty.value = false;
}
</script>

<template>
  <div class="kh-settings">
    <div class="kh-settings-head">
      <div class="kh-settings-title">知识库设置</div>
      <div class="kh-settings-sub">
        在这里，您可以修改此知识库的属性和检索设置
      </div>
    </div>

    <div class="kh-settings-body">
      <!-- 名称和图标 -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">名称和图标</label>
        <div class="kh-settings-name-row">
          <Popover><PopoverTrigger as-child><AsShadcnButton variant="ghost" class="kh-settings-icon-btn" aria-label="选择知识库图标" :style="{ background: form.iconBg ?? '#FFEAD5' }">
            {{ form.icon ?? '📙' }}
          </AsShadcnButton></PopoverTrigger>
<PopoverContent class="w-64 p-3" align="start">
  <div class="mb-2 text-xs text-muted-foreground">选择图标</div>
  <div class="grid grid-cols-5 gap-1">
    <AsShadcnButton v-for="emoji in ['📙','📚','🧭','📗','📄','💡','🧠','🔬','💼','🗂️']" :key="emoji" variant="ghost" size="icon" :aria-label="`图标 ${emoji}`" :aria-pressed="form.icon === emoji" @click="form.icon = emoji">{{ emoji }}</AsShadcnButton>
  </div>
  <div class="my-2 text-xs text-muted-foreground">背景颜色</div>
  <div class="flex gap-2">
    <AsShadcnButton v-for="color in ['#FFEAD5','#DBEAFE','#DCFCE7','#F3E8FF','#FCE7F3']" :key="color" variant="outline" size="icon-sm" :style="{background:color}" :aria-label="`图标背景 ${color}`" :aria-pressed="form.iconBg === color" @click="form.iconBg = color" />
  </div>
</PopoverContent></Popover>
          <AsShadcnInput v-model="form.name" class="kh-input" />
        </div>
      </div>

      <!-- 描述 -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">描述</label>
        <AsShadcnTextarea
          v-model="form.description"
          class="kh-input kh-textarea"
          rows="3"
          placeholder="useful for when you want to answer queries about the …"
        />
      </div>

      <!-- 可见权限 -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">可见权限</label>
        <AsShadcnSelect v-model="form.permission" class="kh-input">
          <option value="ONLY_ME">🔒 只有我</option>
          <option value="ORG_MEMBERS">👥 团队成员</option>
        </AsShadcnSelect>
      </div>

      <!-- 分段模式 —— includes per-mode parameter inputs so the panel is the
           single source of truth for dataset chunking config (mirrors the
           create wizard). Previous version only exposed the 3 mode cards,
           forcing users to re-create the dataset just to tweak chunk size. -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">
          分段模式
          <div v-if="chunkModeLocked" class="kh-settings-sub-label">
            已存在文档，无法切换分段模式。如需变更请新建知识库。
          </div>
          <div v-else class="kh-settings-sub-label">了解更多关于分段模式。</div>
        </label>
        <div
          class="kh-settings-cards"
          :class="{ 'kh-settings-cards-locked': chunkModeLocked }"
        >
          <AsShadcnCard
            v-for="m in CHUNK_MODES"
            :key="m.id"
            class="kh-mode-card"
            :class="{
              'kh-mode-card-active': form.chunkMode === m.id,
              'kh-mode-card-disabled': chunkModeLocked && form.chunkMode !== m.id,
            }"
            @click="selectChunkMode(m.id)"
          >
            <div class="kh-mode-card-head">
              <span class="kh-mode-card-icon">{{ m.icon }}</span>
              <span class="kh-mode-card-title">{{ m.title }}</span>
              <span
                v-if="chunkModeLocked && form.chunkMode === m.id"
                class="kh-mode-card-lock"
              >
                🔒 已锁定
              </span>
            </div>
            <div class="kh-mode-card-desc">{{ m.desc }}</div>
          </AsShadcnCard>
        </div>
        <!-- General / QA modes share the same chunk-size + overlap fields.
             Parent-child adds a parent-chunk-size + parent-mode picker. All of
             these are hidden once the chunk mode is locked — showing them
             read-only would just be visual noise since the backend won't act
             on a re-submitted value here. -->
        <template v-if="!chunkModeLocked">
          <div
            v-if="form.chunkMode !== 'parent-child'"
            class="kh-settings-inline-grid"
          >
            <div class="kh-field">
              <label>分段最大长度</label>
              <div class="kh-input-suffix">
                <AsShadcnInput
                  v-model.number="form.chunkTokens"
                  type="number"
                  class="kh-input"
                  placeholder="1024"
                />
                <span class="kh-suffix">characters</span>
              </div>
            </div>
            <div class="kh-field">
              <label>分段重叠长度</label>
              <div class="kh-input-suffix">
                <AsShadcnInput
                  v-model.number="form.overlapTokens"
                  type="number"
                  class="kh-input"
                  placeholder="50"
                />
                <span class="kh-suffix">characters</span>
              </div>
            </div>
          </div>
          <div v-else class="kh-settings-inline-grid">
            <div class="kh-field">
              <label>父块最大长度</label>
              <div class="kh-input-suffix">
                <AsShadcnInput
                  v-model.number="form.parentChunkTokens"
                  type="number"
                  class="kh-input"
                  placeholder="1024"
                />
                <span class="kh-suffix">characters</span>
              </div>
            </div>
            <div class="kh-field">
              <label>子块最大长度</label>
              <div class="kh-input-suffix">
                <AsShadcnInput
                  v-model.number="form.chunkTokens"
                  type="number"
                  class="kh-input"
                  placeholder="512"
                />
                <span class="kh-suffix">characters</span>
              </div>
            </div>
            <div class="kh-field">
              <label>父块模式</label>
              <AsShadcnSelect v-model="form.parentMode" class="kh-input">
                <option value="PARAGRAPH">段落</option>
                <option value="FULL_DOC">全文</option>
              </AsShadcnSelect>
            </div>
          </div>
          <div class="kh-settings-inline-checks">
            <label class="kh-check">
              <AsShadcnBoolean type="checkbox" v-model="form.removeExtraWhitespace" />
              <span>替换掉连续的空格、换行符和制表符</span>
            </label>
          </div>
        </template>
      </div>

      <!-- 索引模式 — locked once documents are ingested. HIGH_QUALITY vs
           ECONOMY changes the retrieval pipeline (vector store vs keyword
           inverted index), and the backend does not re-embed on switch — so
           allowing the toggle post-ingest would leave chunks stranded in the
           wrong store. Mirrors the chunk-mode lock behaviour. -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">
          索引模式
          <div v-if="chunkModeLocked" class="kh-settings-sub-label">
            已存在文档，无法切换索引模式。
          </div>
        </label>
        <div
          class="kh-settings-cards"
          :class="{ 'kh-settings-cards-locked': chunkModeLocked }"
        >
          <AsShadcnCard
            class="kh-mode-card"
            :class="{
              'kh-mode-card-active': form.indexingTechnique === 'HIGH_QUALITY',
              'kh-mode-card-disabled':
                chunkModeLocked && form.indexingTechnique !== 'HIGH_QUALITY',
            }"
            @click="!chunkModeLocked && (form.indexingTechnique = 'HIGH_QUALITY')"
          >
            <div class="kh-mode-card-head">
              <span class="kh-mode-card-icon" style="color: var(--kh-color-warning)">⭐</span>
              <span class="kh-mode-card-title">高质量</span>
              <span v-if="!chunkModeLocked" class="kh-mode-badge">推荐</span>
              <span
                v-if="chunkModeLocked && form.indexingTechnique === 'HIGH_QUALITY'"
                class="kh-mode-card-lock"
              >
                🔒 已锁定
              </span>
            </div>
            <div class="kh-mode-card-desc">
              调用嵌入模型处理文档以实现更精确的检索，可以帮助 LLM 生成高质量的答案。
            </div>
          </AsShadcnCard>
          <AsShadcnCard
            class="kh-mode-card"
            :class="{
              'kh-mode-card-active': form.indexingTechnique === 'ECONOMY',
              'kh-mode-card-disabled':
                chunkModeLocked && form.indexingTechnique !== 'ECONOMY',
            }"
            @click="!chunkModeLocked && (form.indexingTechnique = 'ECONOMY')"
          >
            <div class="kh-mode-card-head">
              <span class="kh-mode-card-icon" style="color: var(--kh-color-primary-strong)">💰</span>
              <span class="kh-mode-card-title">经济</span>
              <span
                v-if="chunkModeLocked && form.indexingTechnique === 'ECONOMY'"
                class="kh-mode-card-lock"
              >
                🔒 已锁定
              </span>
            </div>
            <div class="kh-mode-card-desc">
              每个数据块使用 10 个关键词进行检索，不消耗任何 tokens，你会得到较低的检索准确性。
            </div>
            <div
              v-if="form.indexingTechnique === 'ECONOMY' && !chunkModeLocked"
              class="kh-keyword-row"
            >
              <label class="kh-keyword-lbl">关键词数量</label>
              <AsShadcnRange
                v-model.number="form.keywordCount"
                type="range"
                min="1"
                max="20"
                class="kh-keyword-slider"
              />
              <span class="kh-keyword-val">{{ form.keywordCount ?? 10 }}</span>
            </div>
          </AsShadcnCard>
        </div>
      </div>

      <!-- Embedding 模型 — grouped-by-provider dropdown (shared with 系统默认
           模型). Auto-fills from the tenant default when the dataset has no
           embedding model set yet (see `defaultEmbeddingModelId` prop). -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">Embedding 模型</label>
        <GroupedModelSelect
          :model-value="form.embeddingModelId ?? null"
          :options="embedOptions"
          placeholder="选择一个 Embedding 模型"
          empty-text="暂无可用的嵌入模型"
          empty-hint="请先在「模型供应商」里配置并启用"
          @update:model-value="onPickEmbedding"
        />
      </div>

      <!-- 摘要自动生成 -->
      <div class="kh-settings-row">
        <label class="kh-settings-label">
          摘要自动生成
          <div class="kh-settings-sub-label">
            启用后，将会自动为新添加的文档生成摘要。已有的文档也可以手动使用。
          </div>
        </label>
        <label class="inline-flex items-center">
          <AsShadcnBoolean appearance="switch" type="checkbox" v-model="form.autoSummary" />
        </label>
        <span class="kh-switch-lbl">
          {{ form.autoSummary ? '已启用' : '已禁用' }}
        </span>
      </div>

      <!-- 检索设置 — shared RetrievalMethodPicker so wizard / settings /
           recall popover all use the same UI. -->
      <div v-if="!retrievalManagedExternally" class="kh-settings-row">
        <label class="kh-settings-label">
          检索设置
          <div class="kh-settings-sub-label">了解更多关于检索方法。</div>
        </label>
        <RetrievalMethodPicker
          v-model="retrievalConfig"
          :rerank-models="rerankModels"
          :indexing-technique="form.indexingTechnique"
        />
      </div>
      <div v-else class="kh-settings-row">
        <label class="kh-settings-label">检索策略<div class="kh-settings-sub-label">策略设置、检索测试与结果查看已统一到 RAG 运行。</div></label>
        <AsShadcnButton variant="ghost" class="kh-btn kh-btn-secondary" @click="emit('open-rag')">打开 RAG 运行</AsShadcnButton>
      </div>
    </div>

    <div v-if="dirty" class="kh-settings-sticky">
      <AsShadcnButton variant="ghost" class="kh-btn kh-btn-secondary" @click="form = { ...dataset }; dirty = false">
        取消
      </AsShadcnButton>
      <AsShadcnButton variant="ghost"
        class="kh-btn kh-btn-primary"
        :disabled="!canSave"
        @click="save"
      >
        保存
      </AsShadcnButton>
    </div>
  </div>
</template>

<style scoped>
.kh-settings {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 24px 32px;
  overflow-y: auto;
  position: relative;
}
.kh-settings-head {
  margin-bottom: 22px;
}
.kh-settings-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-settings-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--kh-color-text-muted);
}

.kh-settings-body {
  max-width: 720px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.kh-settings-row {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 20px;
  align-items: flex-start;
}
.kh-settings-label {
  padding-top: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kh-color-text-secondary);
}
.kh-settings-sub-label {
  margin-top: 2px;
  font-size: 10px;
  color: var(--kh-color-text-muted);
  font-weight: 400;
  line-height: 1.4;
}

.kh-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid var(--kh-color-border-hover);
  border-radius: 8px;
  background: var(--kh-color-surface);
  color: var(--kh-color-text-primary);
  outline: none;
  font-family: inherit;
}
.kh-input:focus {
  border-color: var(--kh-color-primary);
  box-shadow: 0 0 0 3px var(--kh-color-primary-outline);
}
.kh-textarea {
  resize: vertical;
  min-height: 60px;
}

.kh-settings-name-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.kh-settings-icon-btn {
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--kh-color-border);
  border-radius: 10px;
  font-size: 22px;
  cursor: pointer;
  flex-shrink: 0;
}

/* Per-chunk-mode parameter panels shown below the mode card row. */
.kh-settings-inline-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 10px;
}
.kh-settings-inline-checks {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}
.kh-input-suffix {
  position: relative;
  display: flex;
  align-items: center;
}
.kh-input-suffix .kh-input {
  padding-right: 74px;
  width: 100%;
}
.kh-input-suffix .kh-suffix {
  position: absolute;
  right: 10px;
  font-size: 11px;
  color: var(--kh-color-text-muted);
  pointer-events: none;
}
.kh-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kh-field label {
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
}
.kh-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
}
.kh-check input {
  width: 15px;
  height: 15px;
  accent-color: var(--kh-color-primary);
}

/* Mode cards */
.kh-settings-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.kh-mode-card {
  padding: 12px 14px;
  border: 1px solid var(--kh-color-border);
  border-radius: 10px;
  background: var(--kh-color-surface);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-mode-card:hover {
  border-color: var(--kh-color-border-hover);
}
.kh-mode-card-active {
  border-color: transparent;
  outline: 1.5px solid var(--kh-color-primary);
  background: var(--kh-color-primary-soft);
}
.kh-mode-card-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.kh-mode-card-disabled:hover {
  border-color: var(--kh-color-border);
}
.kh-settings-cards-locked .kh-mode-card-active {
  outline-color: var(--kh-color-text-muted);
  background: var(--kh-color-surface-sunken);
}
.kh-mode-card-lock {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-tertiary);
  font-size: 10px;
  font-weight: 500;
}
.kh-mode-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-mode-card-icon {
  font-size: 16px;
}
.kh-mode-card-title {
  color: var(--kh-color-text-primary);
}
.kh-mode-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--kh-color-warning);
  color: #fff;
  font-size: 10px;
}
.kh-mode-card-desc {
  margin-top: 4px;
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  line-height: 1.5;
}

/* Keyword slider row */
.kh-keyword-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.kh-keyword-lbl {
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  width: 80px;
}
.kh-keyword-slider {
  flex: 1;
  accent-color: var(--kh-color-primary);
}
.kh-keyword-val {
  min-width: 32px;
  padding: 2px 8px;
  border: 1px solid var(--kh-color-border);
  border-radius: 6px;
  background: var(--kh-color-surface);
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  text-align: center;
}

/* Switch */
.kh-switch {
  position: relative;
  display: inline-block;
  width: 32px;
  height: 18px;
  cursor: pointer;
}
.kh-switch input {
  display: none;
}
.kh-switch-lbl {
  margin-left: 8px;
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
}
.kh-switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  margin-top: 8px;
}
.kh-switch-row input {
  accent-color: var(--kh-color-primary);
}

/* Retrieve card */
.kh-retrieve-select-card {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--kh-color-primary-soft);
  border: 1.5px solid var(--kh-color-primary);
}
.kh-retrieve-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-retrieve-icon {
  padding: 3px 8px;
  border-radius: 4px;
  background: var(--kh-color-primary-strong);
  color: var(--kh-color-primary-contrast);
  font-size: 11px;
}
.kh-retrieve-desc {
  margin-top: 6px;
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  line-height: 1.5;
}

/* Sticky footer */
.kh-settings-sticky {
  position: sticky;
  bottom: 0;
  right: 0;
  margin-top: 24px;
  margin-left: auto;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(6px);
  border-top: 1px solid var(--kh-color-divider);
  border-radius: 10px;
  display: flex;
  gap: 10px;
  box-shadow: 0 -4px 12px rgba(15, 23, 42, 0.05);
}
.kh-btn {
  padding: 6px 20px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.kh-btn-primary {
  background: var(--kh-color-primary);
  color: var(--kh-color-primary-contrast);
}
.kh-btn-primary:disabled {
  background: var(--kh-color-border);
  cursor: not-allowed;
}
.kh-btn-secondary {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.kh-btn-secondary:hover {
  background: var(--kh-color-surface-sunken);
}

/* Fix alignment of the remaining process-cleanup checkbox — the input's
   baseline was drifting below the label text on Windows Chrome. */
.kh-check {
  align-items: flex-start;
  line-height: 1.4;
}
.kh-check input {
  flex-shrink: 0;
  margin-top: 1px;
}
</style>
