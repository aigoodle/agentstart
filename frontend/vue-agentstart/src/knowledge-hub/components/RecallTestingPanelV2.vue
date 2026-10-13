<script setup lang="ts">
/**
 * RecallTestingPanelV2 — Dify-parity 召回测试 tab (screenshot 5).
 *
 * The 检索设置 lives in a floating {@link RetrievalConfigPopover} anchored to
 * the top-right method button of the query textarea. Everything the popover
 * carries (method + rerank + TopK + score threshold) round-trips through a
 * single {@link RetrievalConfig} v-model, matching the shape the wizard and
 * the settings panel already speak.
 */
import { computed, ref } from 'vue';

import type { RecallHit, RecentQuery } from '../types';
import type {
  IndexingTechnique,
  RetrievalConfig,
} from '../types/dataset';
import RetrievalConfigPopover from './RetrievalConfigPopover.vue';

interface Props {
  hits?: RecallHit[];
  history?: RecentQuery[];
  loading?: boolean;
  /**
   * Seed for the popover so the panel opens showing the dataset's current
   * retrieval config, not defaults. Callers typically parse the dataset's
   * {@code retrievalConfigJson} once and pass it in.
   */
  initialConfig?: RetrievalConfig;
  rerankModels?: Array<{ id: string; label: string }>;
  indexingTechnique?: IndexingTechnique;
}

const props = withDefaults(defineProps<Props>(), {
  hits: () => [],
  history: () => [],
  loading: false,
  initialConfig: () => ({ method: 'VECTOR', topK: 3 }),
  rerankModels: () => [],
});

const emit = defineEmits<{
  (
    e: 'run',
    payload: { query: string; method: string; config: RetrievalConfig },
  ): void;
  (e: 'replay', h: RecentQuery): void;
}>();

const query = ref('');
const config = ref<RetrievalConfig>({ ...props.initialConfig });

const methodLabel = computed(() => {
  if (config.value.method === 'HYBRID') return '混合检索';
  if (config.value.method === 'FULL_TEXT') return '全文检索';
  return '向量检索';
});
const methodIcon = computed(() => {
  if (config.value.method === 'HYBRID') return '⚡';
  if (config.value.method === 'FULL_TEXT') return '≡';
  return '◈';
});

const charCount = computed(() => query.value.length);
const canRun = computed(() => query.value.trim().length > 0);

// Popover trigger anchor + open state.
const methodBtnRef = ref<HTMLElement | null>(null);
const popoverOpen = ref(false);
function toggleConfig() {
  popoverOpen.value = !popoverOpen.value;
}

function run() {
  if (!canRun.value) return;
  emit('run', {
    query: query.value,
    method: config.value.method ?? 'VECTOR',
    config: { ...config.value },
  });
}

function replay(h: RecentQuery) {
  query.value = h.query;
  emit('replay', h);
}
</script>

<template>
  <div class="kh-recall">
    <div class="kh-recall-header">
      <div class="kh-recall-title">召回测试</div>
      <div class="kh-recall-sub">根据给定的查询文本测试知识的召回效果。</div>
    </div>

    <div class="kh-recall-body">
      <!-- LEFT -->
      <div class="kh-recall-left">
        <div class="kh-recall-input-wrap">
          <div class="kh-recall-input-toolbar">
            <span class="kh-recall-input-label">源文本</span>
            <!-- 混合检索 icon button — opens the popover-style 检索设置 card.
                 The button is only the anchor; the card is Teleport'd to <body>
                 in RetrievalConfigPopover so it can float over anything. -->
            <button
              ref="methodBtnRef"
              class="kh-recall-mode-btn"
              @click="toggleConfig"
            >
              <span class="kh-recall-mode-icon">{{ methodIcon }}</span>
              <span>{{ methodLabel }}</span>
              <span class="kh-recall-mode-caret">▾</span>
            </button>
          </div>
          <textarea
            v-model="query"
            class="kh-recall-textarea"
            placeholder="请输入文本，建议使用简短的陈述句。"
            maxlength="500"
          />
          <div class="kh-recall-input-footer">
            <span class="kh-recall-char-count">
              {{ charCount }}/500
            </span>
            <button
              class="kh-btn kh-btn-primary"
              :disabled="!canRun || loading"
              @click="run"
            >
              测试
            </button>
          </div>
        </div>

        <!-- 记录 -->
        <div class="kh-recall-history">
          <div class="kh-recall-history-title">记录</div>
          <div v-if="history.length === 0" class="kh-recall-history-empty">
            <div class="kh-recall-empty-icon-wrap">
              <div class="kh-recall-empty-clock">🕐</div>
            </div>
            <div class="kh-recall-empty-text">最近无查询结果</div>
          </div>
          <div v-else class="kh-recall-history-list">
            <div
              v-for="h in history"
              :key="h.id"
              class="kh-recall-history-row"
              @click="replay(h)"
            >
              <span class="kh-recall-history-method">{{ h.method }}</span>
              <span class="kh-recall-history-query">{{ h.query }}</span>
              <span class="kh-recall-history-count">{{ h.hitCount }} 命中</span>
              <span class="kh-recall-history-at">{{ h.at }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- RIGHT — hit results always render their title at the very top so the
           panel doesn't waste the upper half on whitespace. Empty / loading /
           populated states now share the same anchor. -->
      <div class="kh-recall-right">
        <div class="kh-recall-results-toolbar">
          <div class="kh-recall-results-title">
            命中片段
            <span v-if="hits.length > 0" class="kh-recall-results-count">
              · {{ hits.length }} 个
            </span>
          </div>
        </div>
        <template>
          <div v-if="hits.length === 0" class="kh-recall-right-empty">
            <div class="kh-recall-empty-target">🎯</div>
            <div class="kh-recall-empty-hint">召回测试结果显示在这里</div>
          </div>
          <div v-else class="kh-recall-results">
            <div
              v-for="(hit, i) in hits"
              :key="hit.segmentId"
              class="kh-recall-hit"
            >
              <div class="kh-recall-hit-head">
                <span class="kh-recall-hit-tag">{{ i + 1 }}</span>
                <span v-if="hit.documentName" class="kh-recall-hit-doc">
                  📄 {{ hit.documentName }}
                </span>
                <span v-if="hit.blockType" class="kh-recall-hit-type">
                  {{ hit.blockType }}
                </span>
                <span v-if="hit.heading" class="kh-recall-hit-heading">
                  {{ hit.heading }}
                </span>
                <span class="kh-recall-flex" />
                <span class="kh-recall-hit-score">
                  综合 {{ hit.score.toFixed(3) }}
                </span>
              </div>
              <div
                v-if="hit.vectorScore != null || hit.keywordScore != null"
                class="kh-recall-hit-metrics"
              >
                <span v-if="hit.vectorScore != null">
                  向量 {{ hit.vectorScore.toFixed(3) }}
                </span>
                <span v-if="hit.keywordScore != null">
                  关键词 {{ hit.keywordScore.toFixed(3) }}
                </span>
              </div>
              <div class="kh-recall-hit-body">{{ hit.content }}</div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Floating 检索设置 card — Teleport'd to <body>, positioned against the
         anchor button. Keeps the panel layout untouched even when open. -->
    <RetrievalConfigPopover
      v-model:open="popoverOpen"
      :trigger-el="methodBtnRef"
      v-model="config"
      :rerank-models="rerankModels"
      :indexing-technique="indexingTechnique"
    />
  </div>
</template>

<style scoped>
.kh-recall {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 24px 20px;
}
.kh-recall-header {
  margin-bottom: 10px;
}
.kh-recall-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-recall-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--kh-color-text-muted);
}

.kh-recall-body {
  flex: 1;
  display: grid;
  /* Give the hit-results side a bit more room — the query editor + history
     list on the left doesn't need half the width, and the hit cards benefit
     from extra text width. */
  grid-template-columns: minmax(340px, 5fr) minmax(0, 7fr);
  gap: 20px;
  overflow: hidden;
}

/* LEFT */
.kh-recall-left {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-width: 0;
}
.kh-recall-input-wrap {
  border: 1.5px solid var(--kh-color-primary);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--kh-color-surface-sunken);
}
.kh-recall-input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.kh-recall-input-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-color-text-secondary);
}
.kh-recall-mode-btn-wrap {
  position: relative;
}
.kh-recall-mode-btn {
  padding: 4px 10px;
  border: 1px solid var(--kh-input-border);
  border-radius: 6px;
  background: var(--kh-input-bg);
  font-size: 11px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.kh-recall-mode-btn:hover {
  border-color: var(--kh-color-border-hover);
}
.kh-recall-mode-icon {
  color: var(--kh-color-primary);
}
.kh-recall-mode-caret {
  font-size: 9px;
  color: var(--kh-color-text-muted);
}
.kh-recall-mode-menu {
  position: absolute;
  right: 0;
  top: 30px;
  z-index: 20;
  min-width: 160px;
  padding: 4px;
  background: var(--kh-color-surface-raised);
  border: 1px solid var(--kh-color-border);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.1);
}
.kh-recall-mode-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
}
.kh-recall-mode-item:hover {
  background: var(--kh-color-surface-hover);
}
.kh-recall-mode-item-active {
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary);
}

.kh-recall-textarea {
  width: 100%;
  min-height: 140px;
  padding: 8px 4px;
  border: none;
  outline: none;
  resize: vertical;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: var(--kh-color-text-primary);
  line-height: 1.55;
}
.kh-recall-textarea::placeholder {
  color: var(--kh-color-text-muted);
}

.kh-recall-input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.kh-recall-char-count {
  font-size: 11px;
  color: var(--kh-color-text-muted);
}

.kh-btn {
  padding: 6px 20px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
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

/* History */
.kh-recall-history-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kh-color-text-secondary);
  margin-bottom: 10px;
}
.kh-recall-history-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.kh-recall-empty-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: var(--kh-color-surface-sunken);
  border: 1px solid var(--kh-color-border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kh-recall-empty-clock {
  font-size: 18px;
  color: var(--kh-color-text-muted);
}
.kh-recall-empty-text {
  font-size: 11px;
  color: var(--kh-color-text-muted);
}
.kh-recall-history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kh-recall-history-row {
  display: grid;
  grid-template-columns: 60px 1fr auto auto;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 11px;
  align-items: center;
}
.kh-recall-history-row:hover {
  background: var(--kh-color-surface-hover);
}
.kh-recall-history-method {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary);
  font-size: 10px;
  text-align: center;
}
.kh-recall-history-query {
  color: var(--kh-color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-recall-history-count {
  color: var(--kh-color-text-tertiary);
}
.kh-recall-history-at {
  color: var(--kh-color-text-muted);
}

/* RIGHT — anchored to the top so both empty and populated states share the
   same starting line as the query editor on the left. */
.kh-recall-right {
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 4px 8px;
  background: var(--kh-color-surface-sunken);
  border: 1px solid var(--kh-color-divider);
  border-radius: 10px;
}
.kh-recall-results-title {
  padding: 10px 12px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-color-text-secondary);
}
.kh-recall-results-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.kh-recall-results-count {
  color: var(--kh-color-text-muted);
  font-weight: 400;
}
.kh-recall-right-empty {
  padding: 32px 12px 40px;
  text-align: center;
  color: var(--kh-color-text-muted);
}
.kh-recall-empty-target {
  font-size: 38px;
  opacity: 0.5;
}
.kh-recall-empty-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--kh-color-text-muted);
}
.kh-recall-results {
  width: 100%;
  padding: 0 10px 6px;
}
.kh-recall-hit {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--kh-color-surface);
  border: 1px solid var(--kh-color-border);
  border-radius: 8px;
}
.kh-recall-hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  margin-bottom: 8px;
}
.kh-recall-hit-tag {
  padding: 2px 8px;
  background: #4338ca;
  color: #fff;
  border-radius: 4px;
  font-weight: 600;
  font-size: 10px;
}
.kh-recall-hit-doc {
  color: var(--kh-color-primary);
}
.kh-recall-hit-score {
  color: #b45309;
  font-weight: 600;
}
.kh-recall-flex {
  flex: 1;
}
.kh-recall-hit-body {
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  line-height: 1.55;
}
.kh-recall-hit-type,
.kh-recall-hit-heading {
  color: var(--kh-color-text-tertiary);
  font-size: 11px;
  border: 1px solid var(--kh-color-border);
  border-radius: 999px;
  padding: 1px 8px;
  background: var(--kh-color-surface-sunken);
}
.kh-recall-hit-metrics {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  margin-bottom: 4px;
}
</style>
