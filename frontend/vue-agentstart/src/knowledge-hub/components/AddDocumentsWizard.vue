<script setup lang="ts">
/**
 * AddDocumentsWizard — 3-step wizard for adding documents to an existing
 * dataset, mirroring the CreateDatasetWizard UX so the user experience of
 * "+ 添加文件" matches the "+ 新建" flow.
 *
 * Screens:
 *   1. 选择数据源  — source cards + file dropzone
 *   2. 文本分段与清洗 — chunking + retrieval preview (defaults seeded from the
 *      current dataset's processRule so the user can review before confirming)
 *   3. 处理并完成 — progress + summary
 *
 * Difference from CreateDatasetWizard: no dataset name/description, no dataset
 * creation. The final action just uploads the picked files into the current
 * dataset — chunking/retrieval settings on step 2 are informational (backend
 * uses the dataset's existing config for the upload endpoint).
 */
import { computed, ref, watch } from 'vue';

import type { ChunkPreview } from '../types/api';
import type { ParentMode, ProcessRule } from '../types/dataset';
import type { RetrievalMethod } from '../types/retrieval';

interface Props {
  /** Show the wizard (host controls open state). */
  open: boolean;
  /** Existing dataset name — shown in the back button for context. */
  datasetName?: string;
  /** Existing dataset processRule (parsed) — seeds step 2 defaults. */
  processRule?: ProcessRule;
  /** Existing retrieval method — seeds step 2 defaults. */
  retrievalMethod?: RetrievalMethod;
  /** Existing dataset indexing technique — shown on step 2/3 for context. */
  indexingTechnique?: 'ECONOMY' | 'HIGH_QUALITY';
  /**
   * Backend preview call — same reader/cleaner/chunker as ingestion, so what
   * the user sees on step 2 matches what actually lands on save. When omitted
   * the wizard falls back to a mock preview so hosts without the backing
   * endpoint still ship a working wizard.
   */
  previewChunks?: (
    file: File,
    rule: ProcessRule,
    limit?: number,
  ) => Promise<ChunkPreview>;
}

const props = withDefaults(defineProps<Props>(), {
  datasetName: '',
  processRule: () => ({}),
  retrievalMethod: 'HYBRID',
  indexingTechnique: 'HIGH_QUALITY',
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  /**
   * Emitted on step-2 "保存并处理". Now carries {@code processRule} in
   * addition to the picked files so the host can persist chunking overrides
   * back onto the dataset before the upload runs — the previous behaviour
   * silently discarded any edits the user made on this step, giving the
   * impression that "分段模式 就不再支持" after the first upload.
   */
  (
    e: 'confirm',
    payload: { files: File[]; processRule: ProcessRule },
  ): void;
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
  files.value = [];
  previewLoaded.value = false;
  previewChunksList.value = [];
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
  const filtered = list.filter((f) => f.size <= 15 * 1024 * 1024);
  files.value = [...files.value, ...filtered].slice(0, 5);
}
function removeFile(i: number) {
  files.value.splice(i, 1);
}

const step1Ready = computed(() => files.value.length > 0);

// ------ step 2: chunking (seeded from the dataset's existing processRule)
const chunkMode = ref<'general' | 'parent-child' | 'structure-aware'>(
  props.processRule?.template === 'PARENT_CHILD' ? 'parent-child'
    : props.processRule?.template === 'STRUCTURE_AWARE' ? 'structure-aware' : 'general',
);
const chunkSeparator = ref('\\n\\n');
const chunkMaxTokens = ref(props.processRule?.chunkTokens ?? 1024);
const chunkOverlap = ref(props.processRule?.overlapTokens ?? 50);
const removeExtraWhitespace = ref(
  props.processRule?.removeExtraWhitespace ?? true,
);
const removeUrlsEmails = ref(props.processRule?.removeUrlsEmails ?? false);

const parentMode = ref<ParentMode>(
  props.processRule?.parentMode ?? 'PARAGRAPH',
);
const parentSeparator = ref('\\n\\n');
const parentMaxTokens = ref(props.processRule?.parentChunkTokens ?? 1024);
const childSeparator = ref('\\n');
const childMaxTokens = ref(props.processRule?.chunkTokens ?? 512);

const retrievalMethodDisplay = computed(() => props.retrievalMethod);

// Re-seed defaults if the dataset context changes while the wizard is closed.
watch(
  () => [props.processRule, props.retrievalMethod] as const,
  ([rule]) => {
    if (props.open) return;
    chunkMode.value =
      rule?.template === 'PARENT_CHILD' ? 'parent-child'
        : rule?.template === 'STRUCTURE_AWARE' ? 'structure-aware' : 'general';
    chunkMaxTokens.value = rule?.chunkTokens ?? 1024;
    chunkOverlap.value = rule?.overlapTokens ?? 50;
    removeExtraWhitespace.value = rule?.removeExtraWhitespace ?? true;
    removeUrlsEmails.value = rule?.removeUrlsEmails ?? false;
    parentMode.value = rule?.parentMode ?? 'PARAGRAPH';
    parentMaxTokens.value = rule?.parentChunkTokens ?? 1024;
    childMaxTokens.value = rule?.chunkTokens ?? 512;
  },
  { deep: true },
);

// ------ step 2 preview
const previewLoaded = ref(false);
const previewLoading = ref(false);
const previewTotal = ref(0);
const previewError = ref<string | null>(null);
const previewChunksList = ref<ChunkPreview['chunks']>([]);
const previewParse = ref<Pick<ChunkPreview, 'parser' | 'mediaType' | 'pageCount' | 'blockCount' | 'warnings'>>({});

/**
 * Snapshot the current step-2 form into a {@link ProcessRule} for a preview
 * or a real save. Kept as a computed-style function so both {@code
 * loadPreview} and {@code submit} produce identical payloads.
 */
function currentRule(): ProcessRule {
  return {
    template: chunkMode.value === 'parent-child' ? 'PARENT_CHILD'
      : chunkMode.value === 'structure-aware' ? 'STRUCTURE_AWARE' : 'NAIVE',
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
      previewChunksList.value = res.chunks;
      previewParse.value = res;
      previewLoaded.value = true;
    } catch (e: any) {
      previewError.value = e?.message ?? '预览失败';
      previewLoaded.value = true;
      previewChunksList.value = [];
    } finally {
      previewLoading.value = false;
    }
    return;
  }
  // Fallback for hosts that haven't wired the preview endpoint yet — a mock
  // still lets the wizard flow through, but with an inline note that this is
  // NOT the real chunking result.
  previewLoaded.value = true;
  previewTotal.value = 6;
  const filename = file.name;
  previewChunksList.value = Array.from({ length: 6 }, (_, i) => ({
    index: i + 1,
    text: `SEG-${String(i + 1).padStart(2, '0')} · 来自「${filename}」的第 ${i + 1} 段（示例）。`,
    tokens: 240 + Math.floor(Math.random() * 400),
  }));
  previewError.value = '未接入 previewChunks 接口，当前展示的是占位示例。';
}
function resetChunking() {
  chunkSeparator.value = '\\n\\n';
  chunkMaxTokens.value = props.processRule?.chunkTokens ?? 1024;
  chunkOverlap.value = props.processRule?.overlapTokens ?? 50;
  previewLoaded.value = false;
  previewChunksList.value = [];
}

// ------ step 3: submit
function goToStep2() {
  step.value = 2;
}
function submit() {
  const processRule: ProcessRule = {
    template: chunkMode.value === 'parent-child' ? 'PARENT_CHILD'
      : chunkMode.value === 'structure-aware' ? 'STRUCTURE_AWARE' : 'NAIVE',
    chunkTokens: chunkMaxTokens.value,
    overlapTokens: chunkOverlap.value,
    parentMode: parentMode.value,
    parentChunkTokens: parentMaxTokens.value,
    removeExtraWhitespace: removeExtraWhitespace.value,
    removeUrlsEmails: removeUrlsEmails.value,
  };
  emit('confirm', { files: files.value, processRule });
  step.value = 3;
}

function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

const backLabel = computed(() => props.datasetName || '知识库');
</script>

<template>
  <Teleport to="body" :disabled="!open">
    <div v-if="open" class="kh-wizard-mask" @click.self="close">
      <div class="kh-wizard-shell">
        <!-- top bar (modal-style: title + stepper + close ×) -->
        <div class="kh-topbar">
          <div class="kh-topbar-title">添加文件 · {{ backLabel }}</div>
          <div class="kh-stepper">
            <div
              v-for="(s, i) in steps"
              :key="s.id"
              class="kh-step"
              :class="{
                'kh-step-active': step === s.id,
                'kh-step-done': step > s.id,
              }"
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
          <button class="kh-close" aria-label="close" @click="close">×</button>
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
                <div class="kh-source-icon" :style="{ background: s.iconBg }">
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
              <div v-for="(f, i) in files" :key="i" class="kh-file-row">
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
          </div>
        </div>

        <!-- STEP 2: 分段与清洗 -->
        <div v-if="step === 2" class="kh-body kh-body-split">
          <!-- LEFT -->
          <div class="kh-split-left">
            <div class="kh-hint kh-hint-block">
              💡 新文件将沿用知识库当前的分段与检索配置。你可以在下方查看，如需修改整套配置请在"设置"标签中调整。
            </div>

            <div class="kh-panel" :class="{ 'kh-panel-active': chunkMode === 'structure-aware' }" @click="chunkMode = 'structure-aware'">
              <div class="kh-panel-header">
                <div class="kh-panel-title"><span class="kh-panel-radio" :class="{ 'kh-panel-radio-on': chunkMode === 'structure-aware' }" /><span>结构感知（推荐）</span></div>
                <div class="kh-panel-hint">保留标题、页码、表格、列表与代码块</div>
              </div>
              <div v-if="chunkMode === 'structure-aware'" class="kh-panel-body">
                <div class="kh-grid-2">
                  <div class="kh-field"><label>目标分段 Tokens</label><input v-model.number="chunkMaxTokens" type="number" class="kh-input" /></div>
                  <div class="kh-field"><label>重叠 Tokens</label><input v-model.number="chunkOverlap" type="number" class="kh-input" /></div>
                </div>
              </div>
            </div>

            <div
              class="kh-panel"
              :class="{ 'kh-panel-active': chunkMode === 'general' }"
              @click="chunkMode = 'general'"
            >
              <div class="kh-panel-header">
                <div class="kh-panel-title">
                  <span
                    class="kh-panel-radio"
                    :class="{ 'kh-panel-radio-on': chunkMode === 'general' }"
                  />
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
                  <input v-model="removeExtraWhitespace" type="checkbox" />
                  <span>替换掉连续的空格、换行符和制表符</span>
                </label>
                <label class="kh-check">
                  <input v-model="removeUrlsEmails" type="checkbox" />
                  <span>删除所有 URL 和电子邮件地址</span>
                </label>
                <div class="kh-inline-actions">
                  <button
                    class="kh-btn-mini kh-btn-mini-primary"
                    @click.stop="loadPreview"
                  >
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
                  <span
                    class="kh-panel-radio"
                    :class="{
                      'kh-panel-radio-on': chunkMode === 'parent-child',
                    }"
                  />
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
                    :class="{
                      'kh-parent-mode-active': parentMode === 'PARAGRAPH',
                    }"
                    @click.stop="parentMode = 'PARAGRAPH'"
                  >
                    <div class="kh-pm-head">
                      <span
                        class="kh-panel-radio"
                        :class="{
                          'kh-panel-radio-on': parentMode === 'PARAGRAPH',
                        }"
                      />
                      <span class="kh-pm-title">段落</span>
                    </div>
                    <div class="kh-pm-desc">
                      此模式根据预设的分隔符将文本划分为段落。检索到匹配段后返回所在段落作为父块。
                    </div>
                    <div
                      v-if="parentMode === 'PARAGRAPH'"
                      class="kh-grid-2"
                      style="margin-top: 10px"
                    >
                      <div class="kh-field">
                        <label>分段标识符</label>
                        <input
                          v-model="parentSeparator"
                          class="kh-input"
                          @click.stop
                        />
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
                    :class="{
                      'kh-parent-mode-active': parentMode === 'FULL_DOC',
                    }"
                    @click.stop="parentMode = 'FULL_DOC'"
                  >
                    <div class="kh-pm-head">
                      <span
                        class="kh-panel-radio"
                        :class="{
                          'kh-panel-radio-on': parentMode === 'FULL_DOC',
                        }"
                      />
                      <span class="kh-pm-title">全文</span>
                    </div>
                    <div class="kh-pm-desc">
                      此模式会将文本转成全文视图，检索到匹配段后返回整个文档作为父块。适用于短文档。
                    </div>
                  </button>
                </div>

                <div class="kh-sub-label" style="margin-top: 14px">
                  子块用于检索
                </div>
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

                <div class="kh-inline-actions">
                  <button
                    class="kh-btn-mini kh-btn-mini-primary"
                    @click.stop="loadPreview"
                  >
                    ⓘ 预览块
                  </button>
                  <button class="kh-btn-mini" @click.stop="resetChunking">
                    重置
                  </button>
                </div>
              </div>
            </div>

            <div class="kh-section-label" style="margin-top: 18px">
              索引方式（沿用当前配置）
            </div>
            <div class="kh-readonly-card">
              <span class="kh-readonly-icon">⭐</span>
              <div>
                <div class="kh-readonly-title">
                  {{ indexingTechnique === 'HIGH_QUALITY' ? '高质量' : '经济' }}
                </div>
                <div class="kh-readonly-desc">
                  {{
                    indexingTechnique === 'HIGH_QUALITY'
                      ? '调用嵌入模型处理文档以实现更精确的检索。'
                      : '使用关键词检索，不消耗 tokens，准确性较低。'
                  }}
                </div>
              </div>
            </div>

            <div class="kh-section-label" style="margin-top: 16px">
              检索方式（沿用当前配置）
            </div>
            <div class="kh-readonly-card">
              <span class="kh-readonly-icon">
                {{
                  retrievalMethodDisplay === 'VECTOR'
                    ? '◈'
                    : retrievalMethodDisplay === 'FULL_TEXT'
                      ? '≡'
                      : '⚡'
                }}
              </span>
              <div>
                <div class="kh-readonly-title">
                  {{
                    retrievalMethodDisplay === 'VECTOR'
                      ? '向量检索'
                      : retrievalMethodDisplay === 'FULL_TEXT'
                        ? '全文检索'
                        : '混合检索'
                  }}
                </div>
                <div class="kh-readonly-desc">
                  可以在知识库的"设置"标签中调整检索方式。
                </div>
              </div>
            </div>

            <div class="kh-step-actions">
              <button class="kh-btn kh-btn-secondary" @click="step = 1">
                ← 上一步
              </button>
              <button
                class="kh-btn kh-btn-primary"
                :disabled="files.length === 0"
                @click="submit"
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
                  <template v-if="previewTotal > previewChunksList.length">
                    （前 {{ previewChunksList.length }} 段）
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
                v-for="c in previewChunksList"
                :key="c.index"
                class="kh-preview-chunk"
              >
                <div class="kh-preview-chunk-head">
                  <span class="kh-chunk-tag">
                    SEG-{{ String(c.index).padStart(2, '0') }}
                  </span>
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
                <div class="kh-done-title">文件已提交</div>
                <div class="kh-done-sub">
                  文件已开始处理，稍后将出现在文档列表中。
                </div>
              </div>
            </div>

            <div class="kh-done-files">
              <div v-for="(f, i) in files" :key="i" class="kh-done-file-row">
                <span class="kh-file-icon">📄</span>
                <span class="kh-file-name">{{ f.name }}</span>
                <span class="kh-file-size">{{ humanSize(f.size) }}</span>
              </div>
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
                  {{
                    chunkMode === 'parent-child'
                      ? childMaxTokens
                      : chunkMaxTokens
                  }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">索引方式</span>
                <span class="kh-fact-val" style="color: #b45309">
                  ⭐
                  {{ indexingTechnique === 'HIGH_QUALITY' ? '高质量' : '经济' }}
                </span>
              </div>
              <div>
                <span class="kh-fact-label">检索设置</span>
                <span class="kh-fact-val" style="color: #4338ca">
                  {{
                    retrievalMethodDisplay === 'VECTOR'
                      ? '◈ 向量检索'
                      : retrievalMethodDisplay === 'FULL_TEXT'
                        ? '≡ 全文检索'
                        : '⚡ 混合检索'
                  }}
                </span>
              </div>
            </div>

            <div class="kh-done-actions">
              <button class="kh-btn kh-btn-primary" @click="close">
                前往文档 →
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.kh-wizard-mask {
  position: fixed;
  inset: 0;
  z-index: 1045;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.kh-wizard-shell {
  width: 100%;
  max-width: 1200px;
  height: min(920px, calc(100vh - 48px));
  background: #fff;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.28);
  overflow: hidden;
  animation: kh-wizard-in 0.2s ease-out;
}
@keyframes kh-wizard-in {
  from {
    transform: scale(0.98);
    opacity: 0.8;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.kh-topbar {
  display: grid;
  grid-template-columns: 260px 1fr 60px;
  align-items: center;
  padding: 14px 24px;
  border-bottom: 1px solid #f1f5f9;
  background: #fff;
}
.kh-topbar-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-close {
  justify-self: end;
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
.kh-close:hover {
  background: #f1f5f9;
  color: #475569;
}
.kh-stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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
.kh-body {
  flex: 1;
  overflow-y: auto;
  padding: 40px 40px 80px;
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

.kh-section-label {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

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

.kh-step1-actions {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
}

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
  background: radial-gradient(circle, #6366f1 30%, transparent 32%) center /
    cover;
}
.kh-panel-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

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
.kh-inline-actions {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}

.kh-hint {
  font-size: 11px;
  color: #94a3b8;
}
.kh-hint-block {
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.5;
}

.kh-readonly-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 8px;
}
.kh-readonly-icon {
  font-size: 20px;
}
.kh-readonly-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.kh-readonly-desc {
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

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
.kh-done-files {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 22px;
}
.kh-done-file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  color: #334155;
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
