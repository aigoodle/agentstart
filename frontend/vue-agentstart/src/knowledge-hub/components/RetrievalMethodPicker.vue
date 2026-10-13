<script setup lang="ts">
/**
 * RetrievalMethodPicker — Dify-parity 检索方法 selector, extracted so the same
 * UI serves the create wizard (step 2), the settings panel, and the recall
 * testing popover without three near-identical copies of the same 300 lines.
 *
 * v-models a {@link RetrievalConfig}; consumers only care about that shape.
 *
 * Layout inside is:
 *   ┌─ 向量检索 (radio card, VECTOR-specific: Rerank + TopK + Score) ─┐
 *   ├─ 全文检索 (radio card, TopK + Score) ─────────────────────────┤
 *   └─ 混合检索 (radio card, [权重设置|Rerank] tabs + TopK + Score) ─┘
 *
 * When {@code indexingTechnique === 'ECONOMY'} the vector/hybrid cards are
 * disabled (Dify parity — economy indexing only supports keyword search).
 */
import { computed, ref, watch } from 'vue';

import type {
  IndexingTechnique,
  RetrievalConfig,
} from '../types/dataset';
import type { RetrievalMethod } from '../types/retrieval';

interface Props {
  modelValue: RetrievalConfig;
  rerankModels?: Array<{ id: string; label: string }>;
  /** Restricts VECTOR / HYBRID selection when set to 'ECONOMY'. */
  indexingTechnique?: IndexingTechnique;
  /**
   * 'inline' (default) is the roomy wizard/settings layout;
   * 'compact' tightens paddings for popover use.
   */
  variant?: 'compact' | 'inline';
}
const props = withDefaults(defineProps<Props>(), {
  rerankModels: () => [],
  variant: 'inline',
});
const emit = defineEmits<{
  (e: 'update:modelValue', v: RetrievalConfig): void;
}>();

// Local ref shadow so v-model round-trips cleanly through partial edits.
const method = ref<RetrievalMethod>(props.modelValue.method ?? 'VECTOR');
const topK = ref<number>(props.modelValue.topK ?? 3);
const scoreThreshold = ref<number>(props.modelValue.scoreThreshold ?? 0.5);
const scoreThresholdEnabled = ref<boolean>(
  props.modelValue.scoreThreshold !== undefined,
);
const vectorWeight = ref<number>(props.modelValue.vectorWeight ?? 0.7);
const fusionMethod = ref<'WEIGHTED_SCORE' | 'RECIPROCAL_RANK'>(
  props.modelValue.fusionMethod ?? 'RECIPROCAL_RANK',
);
const recallMultiplier = ref<number>(props.modelValue.recallMultiplier ?? 6);
const maxChunksPerDocument = ref<number>(props.modelValue.maxChunksPerDocument ?? 3);
const neighborWindow = ref<number>(props.modelValue.neighborWindow ?? 0);
const queryExpansionEnabled = ref<boolean>(props.modelValue.queryExpansionEnabled ?? true);
const maxQueryVariants = ref<number>(props.modelValue.maxQueryVariants ?? 3);
const rerankEnabled = ref<boolean>(!!props.modelValue.rerankEnabled);
const rerankModelId = ref<string>(props.modelValue.rerankModelId ?? '');
// The hybrid card has two sub-tabs — the user picks either weight-based
// blending or a rerank model to combine vector+keyword results.
const hybridSubMode = ref<'rerank' | 'weight'>(
  props.modelValue.rerankEnabled ? 'rerank' : 'weight',
);

// Push local edits back up as one atomic config. Watchers are cheap; the
// consumer sees a single object mutation per user tap.
function emitConfig() {
  emit('update:modelValue', {
    method: method.value,
    topK: topK.value,
    scoreThreshold: scoreThresholdEnabled.value ? scoreThreshold.value : undefined,
    vectorWeight: method.value === 'HYBRID' ? vectorWeight.value : undefined,
    fusionMethod: method.value === 'HYBRID' ? fusionMethod.value : undefined,
    recallMultiplier: recallMultiplier.value,
    maxChunksPerDocument: maxChunksPerDocument.value,
    neighborWindow: neighborWindow.value,
    queryExpansionEnabled: queryExpansionEnabled.value,
    maxQueryVariants: maxQueryVariants.value,
    rerankEnabled: rerankEnabled.value,
    rerankModelId: rerankEnabled.value ? rerankModelId.value : undefined,
    // rerankerName mirrors dify's flag: 'model' when a real reranker is used,
    // 'weighted' for pure weight blending in hybrid mode.
    rerankerName:
      method.value === 'HYBRID'
        ? hybridSubMode.value === 'rerank' && rerankEnabled.value
          ? 'model'
          : 'weighted'
        : rerankEnabled.value
          ? 'model'
          : undefined,
  });
}

watch(
  [
    method,
    topK,
    scoreThreshold,
    scoreThresholdEnabled,
    vectorWeight,
    fusionMethod,
    recallMultiplier,
    maxChunksPerDocument,
    neighborWindow,
    queryExpansionEnabled,
    maxQueryVariants,
    rerankEnabled,
    rerankModelId,
    hybridSubMode,
  ],
  emitConfig,
);

// Re-hydrate locals when parent overwrites modelValue (e.g. dataset row load).
watch(
  () => props.modelValue,
  (v) => {
    if (!v) return;
    method.value = v.method ?? 'VECTOR';
    topK.value = v.topK ?? 3;
    scoreThreshold.value = v.scoreThreshold ?? 0.5;
    scoreThresholdEnabled.value = v.scoreThreshold !== undefined;
    vectorWeight.value = v.vectorWeight ?? 0.7;
    fusionMethod.value = v.fusionMethod ?? 'RECIPROCAL_RANK';
    recallMultiplier.value = v.recallMultiplier ?? 6;
    maxChunksPerDocument.value = v.maxChunksPerDocument ?? 3;
    neighborWindow.value = v.neighborWindow ?? 0;
    queryExpansionEnabled.value = v.queryExpansionEnabled ?? true;
    maxQueryVariants.value = v.maxQueryVariants ?? 3;
    rerankEnabled.value = !!v.rerankEnabled;
    rerankModelId.value = v.rerankModelId ?? '';
    hybridSubMode.value = v.rerankEnabled ? 'rerank' : 'weight';
  },
  { deep: true },
);

const disableVectorish = computed(
  () => props.indexingTechnique === 'ECONOMY',
);

function pickMethod(m: RetrievalMethod) {
  if (disableVectorish.value && (m === 'VECTOR' || m === 'HYBRID')) return;
  method.value = m;
}
</script>

<template>
  <div class="rmp" :class="[`rmp-${variant}`]">
    <!-- 向量检索 -->
    <div
      class="rmp-card"
      :class="{
        'rmp-card-active': method === 'VECTOR',
        'rmp-card-disabled': disableVectorish,
      }"
      @click="pickMethod('VECTOR')"
    >
      <div class="rmp-head">
        <span
          class="rmp-radio"
          :class="{ 'rmp-radio-on': method === 'VECTOR' }"
        />
        <span class="rmp-title">◈ 向量检索</span>
      </div>
      <div class="rmp-desc">
        通过生成查询嵌入，查询与其向量表示最相似的文本分段。
      </div>
      <div
        v-if="method === 'VECTOR' && !disableVectorish"
        class="rmp-body"
        @click.stop
      >
        <label class="rmp-check">
          <input type="checkbox" v-model="rerankEnabled" />
          <span>Rerank 模型</span>
          <span class="rmp-hint-inline">对检索结果按语义相关度重排</span>
        </label>
        <select
          v-if="rerankEnabled"
          v-model="rerankModelId"
          class="rmp-select"
        >
          <option value="">选择一个 Rerank 模型</option>
          <option v-for="m in rerankModels" :key="m.id" :value="m.id">
            {{ m.label }}
          </option>
        </select>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <span>Top K</span>
            <span class="rmp-slider-val">{{ topK }}</span>
          </div>
          <input
            v-model.number="topK"
            type="range"
            min="1"
            max="10"
            step="1"
            class="rmp-range"
          />
        </div>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <label class="rmp-check rmp-check-inline">
              <input type="checkbox" v-model="scoreThresholdEnabled" />
              <span>Score 阈值</span>
            </label>
            <span class="rmp-slider-val">
              {{ scoreThresholdEnabled ? scoreThreshold.toFixed(2) : '—' }}
            </span>
          </div>
          <input
            v-model.number="scoreThreshold"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="rmp-range"
            :disabled="!scoreThresholdEnabled"
          />
        </div>
      </div>
    </div>

    <!-- 全文检索 -->
    <div
      class="rmp-card"
      :class="{ 'rmp-card-active': method === 'FULL_TEXT' }"
      @click="pickMethod('FULL_TEXT')"
    >
      <div class="rmp-head">
        <span
          class="rmp-radio"
          :class="{ 'rmp-radio-on': method === 'FULL_TEXT' }"
        />
        <span class="rmp-title">≡ 全文检索</span>
      </div>
      <div class="rmp-desc">
        索引文档中的所有词汇，从而允许用户查询任意词汇。
      </div>
      <div v-if="method === 'FULL_TEXT'" class="rmp-body" @click.stop>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <span>Top K</span>
            <span class="rmp-slider-val">{{ topK }}</span>
          </div>
          <input
            v-model.number="topK"
            type="range"
            min="1"
            max="10"
            step="1"
            class="rmp-range"
          />
        </div>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <label class="rmp-check rmp-check-inline">
              <input type="checkbox" v-model="scoreThresholdEnabled" />
              <span>Score 阈值</span>
            </label>
            <span class="rmp-slider-val">
              {{ scoreThresholdEnabled ? scoreThreshold.toFixed(2) : '—' }}
            </span>
          </div>
          <input
            v-model.number="scoreThreshold"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="rmp-range"
            :disabled="!scoreThresholdEnabled"
          />
        </div>
      </div>
    </div>

    <!-- 混合检索 -->
    <div
      class="rmp-card"
      :class="{
        'rmp-card-active': method === 'HYBRID',
        'rmp-card-disabled': disableVectorish,
      }"
      @click="pickMethod('HYBRID')"
    >
      <div class="rmp-head">
        <span
          class="rmp-radio"
          :class="{ 'rmp-radio-on': method === 'HYBRID' }"
        />
        <span class="rmp-title">⚡ 混合检索</span>
        <span class="rmp-badge">推荐</span>
      </div>
      <div class="rmp-desc">
        同时执行全文与向量检索并重排；可选权重或 Rerank 模型融合两路结果。
      </div>
      <div
        v-if="method === 'HYBRID' && !disableVectorish"
        class="rmp-body"
        @click.stop
      >
        <div class="rmp-tabs">
          <button
            type="button"
            class="rmp-tab"
            :class="{ 'rmp-tab-active': hybridSubMode === 'weight' }"
            @click="hybridSubMode = 'weight'"
          >
            权重设置
          </button>
          <button
            type="button"
            class="rmp-tab"
            :class="{ 'rmp-tab-active': hybridSubMode === 'rerank' }"
            @click="hybridSubMode = 'rerank'"
          >
            Rerank 模型
          </button>
        </div>
        <div v-if="hybridSubMode === 'weight'" class="rmp-slider-row">
          <div class="rmp-slider-label">
            <span>召回融合</span>
            <select v-model="fusionMethod" class="rmp-select rmp-select-inline">
              <option value="RECIPROCAL_RANK">RRF 排名融合（推荐）</option>
              <option value="WEIGHTED_SCORE">加权分数融合</option>
            </select>
          </div>
          <div class="rmp-slider-label">
            <span>
              语义 {{ vectorWeight.toFixed(1) }} · 关键词
              {{ (1 - vectorWeight).toFixed(1) }}
            </span>
          </div>
          <input
            v-model.number="vectorWeight"
            type="range"
            min="0"
            max="1"
            step="0.1"
            class="rmp-range"
          />
        </div>
        <div v-else>
          <label class="rmp-check">
            <input type="checkbox" v-model="rerankEnabled" />
            <span>启用 Rerank 模型</span>
          </label>
          <select
            v-if="rerankEnabled"
            v-model="rerankModelId"
            class="rmp-select"
          >
            <option value="">选择一个 Rerank 模型</option>
            <option v-for="m in rerankModels" :key="m.id" :value="m.id">
              {{ m.label }}
            </option>
          </select>
        </div>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <span>Top K</span>
            <span class="rmp-slider-val">{{ topK }}</span>
          </div>
          <input
            v-model.number="topK"
            type="range"
            min="1"
            max="10"
            step="1"
            class="rmp-range"
          />
        </div>
        <div class="rmp-advanced-grid">
          <label>
            <span>候选池倍数</span>
            <input v-model.number="recallMultiplier" type="number" min="1" max="20" class="rmp-select" />
          </label>
          <label>
            <span>单文档最多命中</span>
            <input v-model.number="maxChunksPerDocument" type="number" min="0" max="20" class="rmp-select" />
          </label>
          <label>
            <span>相邻上下文窗口</span>
            <input v-model.number="neighborWindow" type="number" min="0" max="5" class="rmp-select" />
          </label>
          <label>
            <span>查询变体上限</span>
            <input v-model.number="maxQueryVariants" type="number" min="1" max="8" class="rmp-select" :disabled="!queryExpansionEnabled" />
          </label>
        </div>
        <label class="rmp-check">
          <input v-model="queryExpansionEnabled" type="checkbox" />
          <span>启用查询清洗与多路召回</span>
        </label>
        <div class="rmp-slider-row">
          <div class="rmp-slider-label">
            <label class="rmp-check rmp-check-inline">
              <input type="checkbox" v-model="scoreThresholdEnabled" />
              <span>Score 阈值</span>
            </label>
            <span class="rmp-slider-val">
              {{ scoreThresholdEnabled ? scoreThreshold.toFixed(2) : '—' }}
            </span>
          </div>
          <input
            v-model.number="scoreThreshold"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="rmp-range"
            :disabled="!scoreThresholdEnabled"
          />
        </div>
      </div>
    </div>

    <p v-if="disableVectorish" class="rmp-econ-note">
      当前索引方式为「经济」，仅支持全文检索。
    </p>
  </div>
</template>

<style scoped>
.rmp {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rmp-compact {
  gap: 6px;
}

.rmp-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.rmp-card:hover {
  border-color: #cbd5e1;
}
.rmp-card-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  box-shadow: 0 3px 10px rgba(99, 102, 241, 0.14);
  background: #fbfbff;
}
.rmp-card-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.rmp-compact .rmp-card {
  padding: 10px 12px;
}

.rmp-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rmp-radio {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid #cbd5e1;
  background: #fff;
  transition: all 0.15s;
}
.rmp-radio-on {
  border-color: #6366f1;
  box-shadow: inset 0 0 0 3.5px #6366f1;
}
.rmp-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.rmp-badge {
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #4f46e5;
  border-radius: 4px;
}
.rmp-desc {
  margin-top: 4px;
  padding-left: 22px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.rmp-body {
  margin-top: 10px;
  padding-left: 22px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rmp-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #334155;
  cursor: pointer;
}
.rmp-check input[type='checkbox'] {
  width: 14px;
  height: 14px;
  accent-color: #6366f1;
}
.rmp-check-inline {
  display: inline-flex;
}
.rmp-hint-inline {
  margin-left: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.rmp-select {
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: #0f172a;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  outline: none;
}
.rmp-select:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.rmp-select-inline { width: auto; padding: 3px 8px; }
.rmp-advanced-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.rmp-advanced-grid label { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: #64748b; }

.rmp-slider-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rmp-slider-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
}
.rmp-slider-val {
  font-size: 12px;
  color: #4338ca;
  font-weight: 500;
}
.rmp-range {
  width: 100%;
  accent-color: #6366f1;
}
.rmp-range:disabled {
  opacity: 0.5;
}

.rmp-tabs {
  display: inline-flex;
  padding: 2px;
  gap: 2px;
  background: #eef2ff;
  border-radius: 6px;
}
.rmp-tab {
  padding: 3px 10px;
  background: transparent;
  border: none;
  border-radius: 4px;
  font-size: 11px;
  color: #64748b;
  cursor: pointer;
}
.rmp-tab-active {
  background: #fff;
  color: #4338ca;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.rmp-econ-note {
  margin: 0;
  padding: 6px 10px;
  font-size: 11px;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 6px;
}
</style>
