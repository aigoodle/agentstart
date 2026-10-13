<script setup lang="ts">
/**
 * CreateDatasetWizard — Dify-parity 3-step wizard for creating a knowledge base.
 *
 * Screens (match Snipaste_2026-07-09_08-56-09..47.png):
 *   1. 选择数据源  — source cards + file dropzone
 *   2. 文本分段与清洗 — chunking + indexing + retrieval, right-side preview
 *   3. 处理并完成 — success screen with progress + summary
 *
 * Component owns UI state only. The host provides callbacks for file upload
 * and dataset creation via events — this keeps the package framework-agnostic
 * (no baked-in axios / antd deps).
 */
import { computed, ref } from 'vue';

import { Modal } from '../../ui/components/Modal';
import type { ChunkPreview } from '../types/api';
import type {
  ParentMode,
  ProcessRule,
  RetrievalConfig,
} from '../types/dataset';
import type { RetrievalMethod } from '../types/retrieval';
import RetrievalMethodPicker from './RetrievalMethodPicker.vue';

interface Props {
  /** Available embedding models to pick from. */
  embeddingModels?: Array<{ id: string; label: string }>;
  /** Available rerank models — feeds the retrieval settings' Rerank dropdown. */
  rerankModels?: Array<{ id: string; label: string }>;
  /** Show the wizard (host controls open state). */
  open: boolean;
  /**
   * Backend preview call. Runs the same extract → clean → chunk pipeline as
   * real ingestion, so the "文本分段与清洗" step's preview matches what the
   * dataset gets on save. Falls back to a mock when omitted so hosts without
   * the backing endpoint still get a functional wizard.
   */
  previewChunks?: (
    file: File,
    rule: ProcessRule,
    limit?: number,
  ) => Promise<ChunkPreview>;
}

const props = withDefaults(defineProps<Props>(), {
  embeddingModels: () => [],
  rerankModels: () => [],
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (
    e: 'create',
    payload: {
      name: string;
      description: string;
      files: File[];
      indexingTechnique: 'ECONOMY' | 'HIGH_QUALITY';
      embeddingModelId: string;
      processRule: ProcessRule;
      retrievalConfig: RetrievalConfig;
    },
  ): void;
  (e: 'skip-to-empty'): void;
}>();

// ------ step tracker
type StepId = 1 | 2 | 3;
const step = ref<StepId>(1);

const steps = [
  { id: 1 as const, label: '选择数据源' },
  { id: 2 as const, label: '文本分段与清洗' },
  { id: 3 as const, label: '处理并完成' },
];

function close() {
  step.value = 1;
  emit('update:open', false);
}

// ------ step 1: data source
type SourceKind = 'notion' | 'text' | 'web';
const source = ref<SourceKind>('text');
const files = ref<File[]>([]);

const SOURCES = [
  {
    id: 'text' as const,
    icon: '📄',
    iconBg: '#EEF4FF',
    label: '导入已有文本',
    enabled: true,
  },
  {
    id: 'notion' as const,
    icon: 'N',
    iconBg: '#F1F5F9',
    label: '同步自 Notion 内容',
    enabled: false,
  },
  {
    id: 'web' as const,
    icon: '🌐',
    iconBg: '#EEF4FF',
    label: '同步自 Web 站点',
    enabled: false,
  },
];

const dragOver = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

const ALLOWED = [
  'TXT',
  'CSV',
  'XLS',
  'MARKDOWN',
  'HTML',
  'MDX',
  'PDF',
  'VTT',
  'DOCX',
  'HTM',
  'PROPERTIES',
  'MD',
  'XLSX',
];

function onFileInput(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) addFiles([...input.files]);
  input.value = '';
}
function onDrop(e: DragEvent) {
  e.preventDefault();
  dragOver.value = false;
  const dropped = e.dataTransfer?.files;
  if (dropped) addFiles([...dropped]);
}
function addFiles(list: File[]) {
  // 15MB per file, 5 files per batch — matches Dify's default.
  const filtered = list.filter((f) => f.size <= 15 * 1024 * 1024);
  files.value = [...files.value, ...filtered].slice(0, 5);
}
function removeFile(i: number) {
  files.value.splice(i, 1);
}

const step1Ready = computed(() => files.value.length > 0);

// ------ step 2: chunking
const chunkMode = ref<'general' | 'parent-child' | 'structure-aware'>('structure-aware');
const chunkSeparator = ref('\\n\\n');
const chunkMaxTokens = ref(1024);
const chunkOverlap = ref(50);
const removeExtraWhitespace = ref(true);
const removeUrlsEmails = ref(false);
const qaMode = ref(false);
const qaLanguage = ref('Chinese Simplified');

// -- parent-child mode config (Dify parity)
// Parent context: PARAGRAPH slices the doc into paragraph-sized parents; FULL_DOC
// treats the whole document as one parent (max context, higher token cost).
const parentMode = ref<ParentMode>('PARAGRAPH');
const parentSeparator = ref('\\n\\n');
const parentMaxTokens = ref(1024);
const childSeparator = ref('\\n');
const childMaxTokens = ref(512);

const indexingTechnique = ref<'ECONOMY' | 'HIGH_QUALITY'>('HIGH_QUALITY');
const embeddingModelId = ref(props.embeddingModels[0]?.id ?? '');

// Retrieval settings live in a single {@link RetrievalConfig} v-model'd
// through the shared {@link RetrievalMethodPicker}. Wizard doesn't manage
// method / topK / rerank refs individually anymore — one shape, one place.
const retrievalConfig = ref<RetrievalConfig>({
  method: 'HYBRID',
  topK: 3,
  vectorWeight: 0.7,
  fusionMethod: 'RECIPROCAL_RANK',
  recallMultiplier: 6,
  maxChunksPerDocument: 3,
  rerankEnabled: false,
});

/** Convert an escaped `\\n` literal typed in the input back to a real newline. */
function unescapeSeparator(raw: string): string {
  return raw.replace(/\\n/g, '\n').replace(/\\t/g, '\t');
}

function buildProcessRule(): ProcessRule {
  if (chunkMode.value === 'parent-child') {
    return {
      template: 'PARENT_CHILD',
      chunkTokens: childMaxTokens.value,
      overlapTokens: 0,
      parentChunkTokens: parentMaxTokens.value,
      parentMode: parentMode.value,
      separators: [
        unescapeSeparator(parentSeparator.value),
        unescapeSeparator(childSeparator.value),
      ],
      removeExtraWhitespace: removeExtraWhitespace.value,
      removeUrlsEmails: removeUrlsEmails.value,
    };
  }
  return {
    template: chunkMode.value === 'structure-aware' ? 'STRUCTURE_AWARE' : 'NAIVE',
    chunkTokens: chunkMaxTokens.value,
    overlapTokens: chunkOverlap.value,
    separators: [unescapeSeparator(chunkSeparator.value)],
    removeExtraWhitespace: removeExtraWhitespace.value,
    removeUrlsEmails: removeUrlsEmails.value,
    protectStructuredBlocks: true,
    includeHeadingContext: true,
  };
}

function buildRetrievalConfig(): RetrievalConfig {
  // Shape the picker emits is the same shape we send to createDataset —
  // just return it verbatim.
  return { ...retrievalConfig.value };
}

// ------ step 2 preview — goes through props.previewChunks when the host has
// wired a backend endpoint; falls back to a synthesized sample otherwise so
// the panel still comes alive (with an inline warning that it's not real).
const previewLoaded = ref(false);
const previewLoading = ref(false);
const previewError = ref<string | null>(null);
const previewTotal = ref(0);
const previewChunks = ref<ChunkPreview['chunks']>([]);
const previewParse = ref<Pick<ChunkPreview, 'parser' | 'mediaType' | 'pageCount' | 'blockCount' | 'warnings'>>({});

/** Snapshot current step-2 form into a ProcessRule (shared with save path). */
function currentRule(): ProcessRule {
  return {
    template: chunkMode.value === 'parent-child'
      ? 'PARENT_CHILD'
      : chunkMode.value === 'structure-aware'
        ? 'STRUCTURE_AWARE'
        : 'NAIVE',
    chunkTokens: chunkMaxTokens.value,
    overlapTokens: chunkOverlap.value,
    parentMode: parentMode.value,
    parentChunkTokens: parentMaxTokens.value,
    removeExtraWhitespace: removeExtraWhitespace.value,
    removeUrlsEmails: removeUrlsEmails.value,
  };
}

async function loadPreview() {
  const file = files.value[0];
  if (!file) return;
  previewError.value = null;
  if (props.previewChunks) {
    previewLoading.value = true;
    try {
      const res = await props.previewChunks(file, currentRule(), 10);
      previewTotal.value = res.totalChunks;
      previewChunks.value = res.chunks;
      previewParse.value = res;
      previewLoaded.value = true;
    } catch (e: any) {
      previewError.value = e?.message ?? '预览失败';
      previewLoaded.value = true;
      previewChunks.value = [];
    } finally {
      previewLoading.value = false;
    }
    return;
  }
  // Mock fallback — kept so hosts that haven't wired the endpoint still get a
  // functional wizard, but flagged so the user isn't misled.
  previewLoaded.value = true;
  previewTotal.value = 6;
  const filename = file.name;
  previewChunks.value = Array.from({ length: 6 }, (_, i) => ({
    index: i + 1,
    text: `SEG-${String(i + 1).padStart(2, '0')} · 来自「${filename}」的第 ${i + 1} 段（示例）。`,
    tokens: 240 + Math.floor(Math.random() * 400),
  }));
  previewError.value = '未接入 previewChunks 接口，当前展示的是占位示例。';
}

function resetChunking() {
  chunkSeparator.value = '\\n\\n';
  chunkMaxTokens.value = 1024;
  chunkOverlap.value = 50;
  previewLoaded.value = false;
  previewError.value = null;
  previewChunks.value = [];
}

// ------ step 3: create + done state
const datasetName = ref('');
const creating = ref(false);
const created = ref(false);

async function goToStep2() {
  // Default the name to the first file's base name (Dify does this too).
  const first = files.value[0]?.name ?? '';
  datasetName.value = first.replace(/\.[^./\\]+$/, '') || '知识库';
  step.value = 2;
}

async function submitCreate() {
  creating.value = true;
  emit('create', {
    name: datasetName.value,
    description: '',
    files: files.value,
    indexingTechnique: indexingTechnique.value,
    embeddingModelId: embeddingModelId.value,
    processRule: buildProcessRule(),
    retrievalConfig: buildRetrievalConfig(),
  });
  // Move to step 3 to show the progress screen; the host is expected to update
  // `created` via ref exposure or by closing the modal on real success.
  step.value = 3;
  created.value = true; // mock — real caller can prop-drive this
  creating.value = false;
}

function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<template>
  <Modal
    :open="open"
    title="创建知识库"
    :width="1200"
    :footer="false"
    @update:open="close"
  >
    <div class="kh-wizard-content">
      <!-- 步骤指示器移到内容区域顶部 -->
      <div class="kh-stepper">
        <div
          v-for="(s, i) in steps"
          :key="s.id"
          class="kh-step"
          :class="{ 'kh-step-active': step === s.id, 'kh-step-done': step > s.id }"
        >
          <span class="kh-step-num">
            <span v-if="step === s.id" class="kh-step-num-badge">
              STEP {{ s.id }}
            </span>
            <span v-else>{{ s.id }}</span>
          </span>
          <span class="kh-step-label">{{ s.label }}</span>
          <span v-if="i < steps.length - 1" class="kh-step-dash" />
        </div>
      </div>

      <!-- STEP 1: 选择数据源 -->
      <div v-if="step === 1" class="kh-body kh-body-center">
        <div class="kh-content-narrow">
          <div class="kh-section-label">选择数据源</div>
          <div class="kh-source-row">
            <button
              v-for="s in SOURCES"
              :key="s.id"
              type="button"
              class="kh-source"
              :class="{
                'kh-source-active': source === s.id,
                'kh-source-disabled': !s.enabled,
              }"
              :disabled="!s.enabled"
              @click="s.enabled && (source = s.id)"
            >
              <div
                class="kh-source-icon"
                :style="{ background: s.iconBg }"
              >
                {{ s.icon }}
              </div>
              <div class="kh-source-label">{{ s.label }}</div>
            </button>
          </div>

          <div class="kh-section-label" style="margin-top: 22px">上传文本文件</div>
          <div
            class="kh-dropzone"
            :class="{ 'kh-dropzone-over': dragOver }"
            @dragenter.prevent="dragOver = true"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop="onDrop"
            @click="fileInputRef?.click()"
          >
            <div class="kh-dz-row">
              <span class="kh-dz-icon">☁</span>
              <span class="kh-dz-text">
                拖拽文件或文件夹至此，或者
                <span class="kh-dz-link">选择文件</span>
              </span>
            </div>
            <div class="kh-dz-hint">
              已支持
              <template v-for="(a, i) in ALLOWED" :key="a">
                <span>{{ a }}</span>
                <span v-if="i < ALLOWED.length - 1">、</span>
              </template>
              。每批最多 5 个文件，每个文件不超过 15 MB。
            </div>
            <input
              ref="fileInputRef"
              type="file"
              multiple
              class="kh-hidden-input"
              @change="onFileInput"
            />
          </div>

          <div v-if="files.length > 0" class="kh-file-list">
            <div
              v-for="(f, i) in files"
              :key="i"
              class="kh-file-row"
            >
              <span class="kh-file-icon">📄</span>
              <span class="kh-file-name">{{ f.name }}</span>
              <span class="kh-file-size">{{ humanSize(f.size) }}</span>
              <button class="kh-file-x" @click="removeFile(i)">×</button>
            </div>
          </div>

          <div class="kh-step1-actions">
            <button
              class="kh-btn kh-btn-primary"
              :disabled="!step1Ready"
              @click="goToStep2"
            >
              下一步 →
            </button>
          </div>

          <div class="kh-empty-link" @click="emit('skip-to-empty')">
            📂 创建一个空知识库
          </div>
        </div>
      </div>

        <!-- STEP 2: 分段与清洗 + 索引 + 检索 -->
        <div v-if="step === 2" class="kh-body kh-body-split">
          <!-- LEFT -->
          <div class="kh-split-left">
            <div
              class="kh-panel"
              :class="{ 'kh-panel-active': chunkMode === 'structure-aware' }"
              @click="chunkMode = 'structure-aware'"
            >
              <div class="kh-panel-header">
                <div class="kh-panel-title">
                  <span class="kh-panel-radio" :class="{ 'kh-panel-radio-on': chunkMode === 'structure-aware' }" />
                  <span>结构感知（推荐）</span>
                </div>
                <div class="kh-panel-hint">保留标题层级、表格与代码块</div>
              </div>
              <div v-if="chunkMode === 'structure-aware'" class="kh-panel-body">
                <div class="kh-grid-2">
                  <div class="kh-field"><label>目标分段 Tokens</label><input v-model.number="chunkMaxTokens" type="number" class="kh-input" /></div>
                  <div class="kh-field"><label>重叠 Tokens</label><input v-model.number="chunkOverlap" type="number" class="kh-input" /></div>
                </div>
                <div class="kh-tip">适用于 Markdown、技术文档、手册以及包含表格或代码的文档。</div>
              </div>
            </div>
            <!-- 分段设置 -->
            <div
              class="kh-panel"
              :class="{ 'kh-panel-active': chunkMode === 'general' }"
              @click="chunkMode = 'general'"
            >
              <div class="kh-panel-header">
                <div class="kh-panel-title">
                  <span class="kh-panel-radio" :class="{ 'kh-panel-radio-on': chunkMode === 'general' }" />
                  <span>通用</span>
                </div>
                <div class="kh-panel-hint">
                  通过文本分块模式，检索和召回块获取相关部分。
                </div>
              </div>
              <div v-if="chunkMode === 'general'" class="kh-panel-body">
                <div class="kh-grid-3">
                  <div class="kh-field">
                    <label>分段标识符</label>
                    <input v-model="chunkSeparator" class="kh-input" />
                  </div>
                  <div class="kh-field">
                    <label>分段最大长度</label>
                    <div class="kh-input-suffix">
                      <input
                        v-model.number="chunkMaxTokens"
                        type="number"
                        class="kh-input"
                      />
                      <span class="kh-suffix">characters</span>
                    </div>
                  </div>
                  <div class="kh-field">
                    <label>分段重叠长度</label>
                    <div class="kh-input-suffix">
                      <input
                        v-model.number="chunkOverlap"
                        type="number"
                        class="kh-input"
                      />
                      <span class="kh-suffix">characters</span>
                    </div>
                  </div>
                </div>
                <div class="kh-sub-label">文本预处理规则</div>
                <label class="kh-check">
                  <input type="checkbox" v-model="removeExtraWhitespace" />
                  <span>替换掉连续的空格、换行符和制表符</span>
                </label>
                <label class="kh-check">
                  <input type="checkbox" v-model="removeUrlsEmails" />
                  <span>删除所有 URL 和电子邮件地址</span>
                </label>
                <label class="kh-check">
                  <input type="checkbox" v-model="qaMode" />
                  <span>
                    摘要自动生成
                    <span class="kh-hint-inline">使用 Q&A 分段，语言</span>
                    <select v-model="qaLanguage" class="kh-select-inline" @click.stop>
                      <option>Chinese Simplified</option>
                      <option>English</option>
                    </select>
                  </span>
                </label>
                <div class="kh-inline-actions">
                  <button class="kh-btn-mini kh-btn-mini-primary" @click.stop="loadPreview">
                    ⓘ 预览块
                  </button>
                  <button class="kh-btn-mini" @click.stop="resetChunking">
                    重置
                  </button>
                </div>
              </div>
            </div>

            <div
              class="kh-panel"
              :class="{ 'kh-panel-active': chunkMode === 'parent-child' }"
              @click="chunkMode = 'parent-child'"
            >
              <div class="kh-panel-header">
                <div class="kh-panel-title">
                  <span class="kh-panel-radio" :class="{ 'kh-panel-radio-on': chunkMode === 'parent-child' }" />
                  <span>👥 父子分段</span>
                </div>
                <div class="kh-panel-hint">
                  地区父子块的父块，子块用于检索，父块作为上下文
                </div>
              </div>
              <div v-if="chunkMode === 'parent-child'" class="kh-panel-body">
                <div class="kh-sub-label">父块用作上下文</div>
                <div class="kh-parent-mode-row">
                  <button
                    type="button"
                    class="kh-parent-mode-card"
                    :class="{ 'kh-parent-mode-active': parentMode === 'PARAGRAPH' }"
                    @click.stop="parentMode = 'PARAGRAPH'"
                  >
                    <div class="kh-pm-head">
                      <span
                        class="kh-panel-radio"
                        :class="{ 'kh-panel-radio-on': parentMode === 'PARAGRAPH' }"
                      />
                      <span class="kh-pm-title">段落</span>
                    </div>
                    <div class="kh-pm-desc">
                      此模式根据预设的分隔符将文本划分为段落。检索到匹配段后返回所在段落作为父块。
                    </div>
                    <div v-if="parentMode === 'PARAGRAPH'" class="kh-grid-2" style="margin-top: 10px">
                      <div class="kh-field">
                        <label>分段标识符</label>
                        <input v-model="parentSeparator" class="kh-input" @click.stop />
                      </div>
                      <div class="kh-field">
                        <label>分段最大长度</label>
                        <div class="kh-input-suffix">
                          <input
                            v-model.number="parentMaxTokens"
                            type="number"
                            class="kh-input"
                            @click.stop
                          />
                          <span class="kh-suffix">characters</span>
                        </div>
                      </div>
                    </div>
                  </button>
                  <button
                    type="button"
                    class="kh-parent-mode-card"
                    :class="{ 'kh-parent-mode-active': parentMode === 'FULL_DOC' }"
                    @click.stop="parentMode = 'FULL_DOC'"
                  >
                    <div class="kh-pm-head">
                      <span
                        class="kh-panel-radio"
                        :class="{ 'kh-panel-radio-on': parentMode === 'FULL_DOC' }"
                      />
                      <span class="kh-pm-title">全文</span>
                    </div>
                    <div class="kh-pm-desc">
                      此模式会将文本转成全文视图，检索到匹配段后返回整个文档作为父块。适用于短文档。
                    </div>
                  </button>
                </div>

                <div class="kh-sub-label" style="margin-top: 14px">子块用于检索</div>
                <div class="kh-grid-2">
                  <div class="kh-field">
                    <label>分段标识符</label>
                    <input v-model="childSeparator" class="kh-input" @click.stop />
                  </div>
                  <div class="kh-field">
                    <label>分段最大长度</label>
                    <div class="kh-input-suffix">
                      <input
                        v-model.number="childMaxTokens"
                        type="number"
                        class="kh-input"
                        @click.stop
                      />
                      <span class="kh-suffix">characters</span>
                    </div>
                  </div>
                </div>

                <div class="kh-sub-label" style="margin-top: 14px">文本预处理规则</div>
                <label class="kh-check">
                  <input type="checkbox" v-model="removeExtraWhitespace" @click.stop />
                  <span>替换掉连续的空格、换行符和制表符</span>
                </label>
                <label class="kh-check">
                  <input type="checkbox" v-model="removeUrlsEmails" @click.stop />
                  <span>删除所有 URL 和电子邮件地址</span>
                </label>

                <div class="kh-inline-actions">
                  <button class="kh-btn-mini kh-btn-mini-primary" @click.stop="loadPreview">
                    ⓘ 预览块
                  </button>
                  <button class="kh-btn-mini" @click.stop="resetChunking">
                    重置
                  </button>
                </div>
              </div>
            </div>

            <!-- 索引方式 —— stacked one card per row (Dify parity). Two-up
                 wraps their descriptions awkwardly on typical viewport widths;
                 stacking gives each card room to breathe. -->
            <div class="kh-section-label" style="margin-top: 18px">索引方式</div>
            <div class="kh-index-row">
              <button
                type="button"
                class="kh-index-card"
                :class="{ 'kh-index-card-active': indexingTechnique === 'HIGH_QUALITY' }"
                @click="indexingTechnique = 'HIGH_QUALITY'"
              >
                <div class="kh-index-head">
                  <span class="kh-index-icon" style="background: #FEF3C7; color: #b45309">⭐</span>
                  <span>高质量</span>
                  <span class="kh-index-badge">推荐</span>
                </div>
                <div class="kh-index-desc">
                  调用嵌入模型处理文档以实现更精确的检索，可以帮助 LLM 生成高质量的答案。
                </div>
              </button>
              <button
                type="button"
                class="kh-index-card"
                :class="{ 'kh-index-card-active': indexingTechnique === 'ECONOMY' }"
                @click="indexingTechnique = 'ECONOMY'"
              >
                <div class="kh-index-head">
                  <span class="kh-index-icon" style="background: #EEF4FF; color: #4338ca">💰</span>
                  <span>经济</span>
                </div>
                <div class="kh-index-desc">
                  每个数据块使用 10 个关键词进行检索，不会消耗任何 tokens。你会得到较低的检索准确性。
                </div>
              </button>
            </div>

            <div class="kh-tip">
              💡 使用高质量模式引入的数据，无法切换回经济模式。
            </div>

            <div class="kh-section-label" style="margin-top: 16px">Embedding 模型</div>
            <select v-model="embeddingModelId" class="kh-select">
              <option value="">选择一个 Embedding 模型</option>
              <option
                v-for="m in embeddingModels"
                :key="m.id"
                :value="m.id"
              >
                {{ m.label }}
              </option>
            </select>

            <div class="kh-section-label" style="margin-top: 16px">检索设置</div>
            <div class="kh-hint" style="margin-top: -4px">
              了解更多关于检索方式，你可以随时在知识库的设置中更改此设置。
            </div>

            <!-- 检索方法 —— 共用 RetrievalMethodPicker，之前这里 230 行
                 手写卡片 + rerank + TopK + score 阈值现在一个组件搞定。 -->
            <RetrievalMethodPicker
              v-model="retrievalConfig"
              :rerank-models="rerankModels"
              :indexing-technique="indexingTechnique"
            />

            <div class="kh-step-actions">
              <button class="kh-btn kh-btn-secondary" @click="step = 1">
                ← 上一步
              </button>
              <button
                class="kh-btn kh-btn-primary"
                :disabled="!embeddingModelId && indexingTechnique === 'HIGH_QUALITY'"
                @click="submitCreate"
              >
                保存并处理 →
              </button>
            </div>
          </div>

          <!-- RIGHT: preview -->
          <div class="kh-split-right">
            <div class="kh-preview-title">
              <span>预览</span>
              <span v-if="files[0]" class="kh-preview-file">
                📄 {{ files[0].name }}
                <template v-if="previewLoaded">
                  · 共 {{ previewTotal }} 段
                  <template v-if="previewTotal > previewChunks.length">
                    （前 {{ previewChunks.length }} 段）
                  </template>
                </template>
              </span>
            </div>
            <div v-if="previewLoading" class="kh-preview-empty">
              <div class="kh-preview-empty-text">分段中…</div>
            </div>
            <div v-else-if="!previewLoaded" class="kh-preview-empty">
              <div class="kh-preview-empty-icon">◉</div>
              <div class="kh-preview-empty-text">
                点击左侧的"预览块"按钮来加载预览
              </div>
            </div>
            <div v-else class="kh-preview-body">
              <div v-if="previewError" class="kh-preview-warning">
                ⚠️ {{ previewError }}
              </div>
              <div v-if="previewParse.parser" class="kh-preview-warning kh-preview-parser">
                解析器：{{ previewParse.parser }} · {{ previewParse.blockCount ?? 0 }} 个结构块
                <template v-if="previewParse.pageCount"> · {{ previewParse.pageCount }} 页</template>
                <div v-for="warning in previewParse.warnings" :key="warning">⚠ {{ warning }}</div>
              </div>
              <div
                v-for="c in previewChunks"
                :key="c.index"
                class="kh-preview-chunk"
              >
                <div class="kh-preview-chunk-head">
                  <span class="kh-chunk-tag">SEG-{{ String(c.index).padStart(2, '0') }}</span>
                  <span class="kh-chunk-tokens">{{ c.tokens }} tokens</span>
                </div>
                <div class="kh-preview-chunk-body">{{ c.text }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- STEP 3: 处理并完成 -->
        <div v-if="step === 3" class="kh-body kh-body-center">
          <div class="kh-content-narrow">
            <div class="kh-done-header">
              <span class="kh-done-emoji">🎉</span>
              <div>
                <div class="kh-done-title">知识库已创建</div>
                <div class="kh-done-sub">
                  我们自动为您把知识库起了个名称，您也可以随时修改。
                </div>
              </div>
            </div>
            <div class="kh-done-name-row">
              <span class="kh-done-name-label">知识库名称</span>
              <div class="kh-done-name-input">
                <span class="kh-done-name-icon">📙</span>
                <input v-model="datasetName" class="kh-input" />
              </div>
            </div>

            <div class="kh-done-progress-row">
              <span class="kh-done-progress-label">嵌入处理中…</span>
              <span class="kh-done-progress-file">
                📄 {{ files[0]?.name ?? '' }}
              </span>
              <div class="kh-done-progress-bar">
                <div class="kh-done-progress-fill" style="width: 0%" />
              </div>
              <span class="kh-done-progress-pct">0%</span>
            </div>

            <div class="kh-done-facts">
              <div>
                <span class="kh-fact-label">分段模式</span>
                <span class="kh-fact-val">
                  {{ chunkMode === 'general' ? '自定义' : '父子分段' }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">最大分段长度</span>
                <span class="kh-fact-val">
                  {{ chunkMode === 'parent-child' ? childMaxTokens : chunkMaxTokens }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">文本预处理规则</span>
                <span class="kh-fact-val">
                  {{ removeExtraWhitespace ? '替换掉连续的空格、换行符和制表符' : '不处理' }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">索引方式</span>
                <span class="kh-fact-val" style="color: #b45309">
                  ⭐ {{ indexingTechnique === 'HIGH_QUALITY' ? '高质量' : '经济' }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">检索设置</span>
                <span class="kh-fact-val" style="color: #4338ca">
                  {{
                    retrievalConfig.method === 'VECTOR'
                      ? '◈ 向量检索'
                      : retrievalConfig.method === 'FULL_TEXT'
                        ? '≡ 全文检索'
                        : '⚡ 混合检索'
                  }}
                  · Top K {{ retrievalConfig.topK }}
                </span>
              </div>
            </div>

            <div class="kh-done-actions">
              <button class="kh-btn kh-btn-secondary">◈ Access the API</button>
              <button class="kh-btn kh-btn-primary" @click="close">
                前往文档 →
              </button>
            </div>
          </div>
        </div>
    </div>
  </Modal>
</template>

<style scoped>
/* Wizard content wrapper */
.kh-wizard-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* Stepper moved to content area */
.kh-stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px 24px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}
.kh-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94a3b8;
}
.kh-step-num {
  min-width: 20px;
  text-align: center;
}
.kh-step-num-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  background: #4f46e5;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
}
.kh-step-active {
  color: #4f46e5;
  font-weight: 600;
}
.kh-step-done {
  color: #10b981;
}
.kh-step-dash {
  display: inline-block;
  width: 24px;
  height: 1px;
  background: #cbd5e1;
  margin: 0 4px;
}
/* Body layouts */
.kh-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 40px 40px;
}
.kh-body-center {
  display: flex;
  justify-content: center;
}
.kh-body-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 32px;
  max-width: 1400px;
  margin: 0 auto;
  /* Let each column own its scroll — preview on the right can scroll without
     dragging the segmentation strategy on the left along with it. */
  padding: 0;
  overflow: hidden;
}
.kh-split-left {
  min-height: 0;
  overflow-y: auto;
  padding: 40px 12px 80px 40px;
}
.kh-content-narrow {
  width: 660px;
  max-width: 100%;
}

/* Section labels */
.kh-section-label {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

/* Step 1 — source cards */
.kh-source-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.kh-source {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-source:hover:not(.kh-source-disabled) {
  border-color: #cbd5e1;
}
.kh-source-active {
  border-color: transparent !important;
  outline: 1.5px solid #6366f1;
  background: #eef2ff;
}
.kh-source-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.kh-source-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  color: #4338ca;
}
.kh-source-label {
  font-size: 14px;
  color: #0f172a;
}

/* Step 1 — dropzone */
.kh-dropzone {
  padding: 20px 24px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #f8fafc;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}
.kh-dropzone:hover,
.kh-dropzone-over {
  border-color: #6366f1;
  background: #eef2ff;
}
.kh-dz-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #475569;
}
.kh-dz-icon {
  font-size: 18px;
  color: #94a3b8;
}
.kh-dz-link {
  color: #4338ca;
  font-weight: 500;
}
.kh-dz-hint {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.6;
}
.kh-hidden-input {
  display: none;
}
.kh-file-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.kh-file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  color: #334155;
}
.kh-file-icon {
  color: #4338ca;
}
.kh-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-file-size {
  color: #94a3b8;
}
.kh-file-x {
  padding: 0 6px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

/* Step 1 — actions */
.kh-step1-actions {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
}
.kh-empty-link {
  margin-top: 20px;
  font-size: 13px;
  color: #4338ca;
  cursor: pointer;
}

/* Buttons */
.kh-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.kh-btn-primary {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  box-shadow: 0 2px 6px rgba(79, 70, 229, 0.3);
}
.kh-btn-primary:disabled {
  background: #cbd5e1;
  box-shadow: none;
  cursor: not-allowed;
}
.kh-btn-secondary {
  background: #f1f5f9;
  color: #475569;
}
.kh-btn-secondary:hover {
  background: #e2e8f0;
}
.kh-btn-mini {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.kh-btn-mini:hover {
  background: #f8fafc;
}
.kh-btn-mini-primary {
  color: #4338ca;
  border-color: #c7d2fe;
  background: #eef2ff;
}

/* Step 2 — chunking panels */
.kh-panel {
  padding: 14px 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-panel:hover {
  border-color: #cbd5e1;
}
.kh-panel-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  background: #f5f6ff;
}
.kh-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.kh-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.kh-panel-hint {
  font-size: 11px;
  color: #94a3b8;
}
.kh-panel-radio {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid #cbd5e1;
  background: #fff;
  display: inline-block;
}
.kh-panel-radio-on {
  border-color: #6366f1;
  background:
    radial-gradient(circle, #6366f1 30%, transparent 32%) center / cover;
}
.kh-panel-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

/* Fields */
.kh-grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.kh-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
/* Indexing-mode cards: stacked one-per-row (Dify parity). Descriptions
   need horizontal room; a 2-column layout wraps 高质量/经济 desc awkwardly. */
.kh-index-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.kh-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kh-field label {
  font-size: 11px;
  color: #64748b;
}
.kh-input {
  padding: 7px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  outline: none;
  background: #fff;
  color: #0f172a;
  width: 100%;
}
.kh-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.kh-input-suffix {
  position: relative;
}
.kh-input-suffix .kh-input {
  padding-right: 74px;
}
.kh-suffix {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: #94a3b8;
}
.kh-select,
.kh-select-inline {
  padding: 7px 28px 7px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  outline: none;
  color: #0f172a;
}
.kh-select {
  width: 100%;
}
.kh-select-inline {
  padding: 3px 20px 3px 8px;
  font-size: 11px;
  margin-left: 4px;
}

.kh-sub-label {
  margin-top: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}
.kh-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 12px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}
.kh-check input {
  accent-color: #6366f1;
}
.kh-hint-inline {
  color: #94a3b8;
  font-size: 11px;
  margin-left: 4px;
}
.kh-inline-actions {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}

/* Indexing cards */
.kh-index-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-index-card:hover {
  border-color: #cbd5e1;
}
.kh-index-card-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  background: #f5f6ff;
}
.kh-index-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.kh-index-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 12px;
}
.kh-index-badge {
  padding: 1px 6px;
  font-size: 10px;
  border-radius: 4px;
  background: #b45309;
  color: #fff;
}
.kh-index-desc {
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.kh-tip {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
}

/* Retrieval card */
.kh-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 8px;
}
.kh-retrieve-card {
  padding: 12px 14px;
  margin-bottom: 8px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-retrieve-card:hover {
  border-color: #cbd5e1;
}
.kh-retrieve-card-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  background: #f5f6ff;
}
.kh-retrieve-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kh-retrieve-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.kh-retrieve-icon {
  padding: 3px 8px;
  border-radius: 4px;
  background: #4f46e5;
  color: #fff;
  font-size: 11px;
}
.kh-retrieve-desc {
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
}
.kh-retrieve-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
  cursor: default;
}
.kh-slider-row {
  margin-top: 10px;
}
.kh-slider-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}
.kh-slider-val {
  font-size: 11px;
  color: #4338ca;
  font-weight: 600;
}
.kh-range {
  width: 100%;
  accent-color: #6366f1;
}
.kh-range:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.kh-check-inline {
  margin: 0;
}
.kh-hybrid-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  background: #eef2ff;
  border-radius: 6px;
  margin-bottom: 8px;
}
.kh-hybrid-tab {
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
}
.kh-hybrid-tab-active {
  background: #fff;
  color: #4338ca;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

/* Parent-child parent-mode cards */
.kh-parent-mode-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.kh-parent-mode-card {
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.kh-parent-mode-card:hover {
  border-color: #cbd5e1;
}
.kh-parent-mode-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  background: #f5f6ff;
}
.kh-pm-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.kh-pm-title {
  font-weight: 600;
  color: #0f172a;
}
.kh-pm-desc {
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.kh-step-actions {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
}

/* Preview pane (right) */
.kh-split-right {
  min-height: 0;
  overflow-y: auto;
  padding: 40px 40px 80px 24px;
  border-left: 1px solid #f1f5f9;
}
.kh-preview-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.kh-preview-file {
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
}
.kh-preview-empty {
  padding: 100px 20px;
  text-align: center;
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}
.kh-preview-empty-icon {
  font-size: 34px;
  color: #cbd5e1;
}
.kh-preview-empty-text {
  margin-top: 12px;
  font-size: 12px;
  color: #94a3b8;
}
.kh-preview-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kh-preview-warning {
  padding: 8px 10px;
  font-size: 12px;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 6px;
  line-height: 1.5;
}
.kh-preview-chunk {
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}
.kh-preview-chunk-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
  margin-bottom: 6px;
}
.kh-chunk-tag {
  padding: 2px 6px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 4px;
  font-weight: 600;
}
.kh-chunk-tokens {
  color: #94a3b8;
}
.kh-preview-chunk-body {
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
}

/* Step 3 — done */
.kh-done-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}
.kh-done-emoji {
  font-size: 34px;
}
.kh-done-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}
.kh-done-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
.kh-done-name-row {
  margin-bottom: 22px;
}
.kh-done-name-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}
.kh-done-name-input {
  position: relative;
  display: flex;
  align-items: center;
}
.kh-done-name-icon {
  position: absolute;
  left: 12px;
  font-size: 18px;
}
.kh-done-name-input .kh-input {
  padding-left: 40px;
  background: #f8fafc;
}
.kh-done-progress-row {
  padding: 12px 16px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 22px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
}
.kh-done-progress-label {
  font-size: 12px;
  color: #64748b;
  grid-column: 1 / -1;
}
.kh-done-progress-file {
  grid-column: 1 / 2;
  font-size: 12px;
  color: #334155;
}
.kh-done-progress-bar {
  grid-column: 2 / 3;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}
.kh-done-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #4f46e5);
  transition: width 0.3s;
}
.kh-done-progress-pct {
  grid-column: 3 / 4;
  font-size: 12px;
  color: #64748b;
}
.kh-done-facts {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  margin-bottom: 26px;
}
.kh-done-facts > div {
  display: grid;
  grid-template-columns: 120px 1fr;
  font-size: 12px;
  padding: 4px 0;
}
.kh-fact-label {
  color: #94a3b8;
}
.kh-fact-val {
  color: #334155;
}
.kh-done-actions {
  display: flex;
  gap: 10px;
}
</style>
