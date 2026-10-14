<script setup lang="ts">
import { computed } from 'vue';
import type { RagCapabilities, RagPolicy, RagRetrievalChannel } from '../types';
const props = defineProps<{ modelValue: RagPolicy; capabilities?: RagCapabilities; rerankModels?: Array<{ id: string; label: string }> }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: RagPolicy): void }>();
const availableChannels = computed<RagRetrievalChannel[]>(() => [...new Set([
  ...(props.capabilities?.retrievalChannels || ['VECTOR']), ...props.modelValue.retrieval.channels,
])] as RagRetrievalChannel[]);
const queryLabels: Record<string, string> = { DIRECT: '直接检索', MULTI_QUERY: '多查询扩展', HYDE: '假设文档', STEP_BACK: '回退提问', DECOMPOSITION: '问题拆解' };
const channelLabels: Record<string, string> = { VECTOR: '向量检索', SPARSE: '全文检索', RAPTOR: '层次摘要', GRAPH: '图谱检索', MULTIMODAL: '多模态检索' };
function update(section: keyof RagPolicy, key: string, value: unknown) {
  if (typeof value === 'number' && (!Number.isFinite(value) || value < 0)) return;
  const next: RagPolicy = JSON.parse(JSON.stringify(props.modelValue));
  (next[section] as unknown as Record<string, unknown>)[key] = value;
  if (section === 'queryPlanning' && key === 'strategy' && value !== 'DIRECT' && next.queryPlanning.maxQueries === 1) {
    next.queryPlanning.maxQueries = 3;
    next.budget.maxQueries = Math.max(3, next.budget.maxQueries);
  }
  if (section === 'rerank' && key === 'topN') next.budget.maxEvidenceItems = Math.max(Number(value), next.budget.maxEvidenceItems);
  emit('update:modelValue', next);
}
function toggleChannel(channel: RagRetrievalChannel, checked: boolean) {
  const channels = new Set(props.modelValue.retrieval.channels);
  if (!checked && channels.size === 1) return;
  checked ? channels.add(channel) : channels.delete(channel);
  const next: RagPolicy = JSON.parse(JSON.stringify(props.modelValue));
  next.retrieval.channels = [...channels];
  if (channels.size > 1 && next.fusion.method === 'NONE') next.fusion.method = 'RRF';
  if (channels.size === 1) next.fusion.method = 'NONE';
  emit('update:modelValue', next);
}
const unavailable = (channel: RagRetrievalChannel) => !!props.capabilities && !props.capabilities.retrievalChannels.includes(channel);
</script>

<template>
  <div class="rag-policy">
    <fieldset class="rag-channels"><legend>召回方式</legend>
      <label v-for="channel in availableChannels" :key="channel" class="rag-channel" :class="{ selected: modelValue.retrieval.channels.includes(channel) }">
        <input type="checkbox" :checked="modelValue.retrieval.channels.includes(channel)" :disabled="unavailable(channel) && !modelValue.retrieval.channels.includes(channel)" @change="toggleChannel(channel, ($event.target as HTMLInputElement).checked)" />
        {{ channelLabels[channel] || channel }}<small v-if="unavailable(channel)">未启用</small>
      </label>
    </fieldset>
    <div class="rag-fields">
      <label>返回片段数 · Top K<input type="number" min="1" max="100" :value="modelValue.rerank.topN" @change="update('rerank', 'topN', Math.min(100, Math.max(1, Number(($event.target as HTMLInputElement).value))))" /></label>
      <label>每路候选数量<input type="number" min="1" max="500" :value="modelValue.retrieval.candidateLimitPerQuery" @change="update('retrieval', 'candidateLimitPerQuery', Math.min(500, Math.max(1, Number(($event.target as HTMLInputElement).value))))" /></label>
      <label v-if="modelValue.retrieval.channels.includes('VECTOR')">向量相似度阈值<input type="number" min="0" max="1" step="0.05" :value="modelValue.retrieval.scoreThreshold ?? 0" @change="update('retrieval', 'scoreThreshold', Math.min(1, Math.max(0, Number(($event.target as HTMLInputElement).value))))" /><small>0 表示不限制</small></label>
      <label>单文档片段上限<input type="number" min="0" max="100" :value="modelValue.retrieval.maxChunksPerDocument ?? 0" @change="update('retrieval', 'maxChunksPerDocument', Math.max(0, Number(($event.target as HTMLInputElement).value)))" /><small>0 表示不限制</small></label>
    </div>
    <div class="rag-rerank">
      <label class="rag-toggle"><input type="checkbox" :checked="modelValue.rerank.enabled" @change="update('rerank', 'enabled', ($event.target as HTMLInputElement).checked)" />启用重排序</label>
      <div v-if="modelValue.rerank.enabled" class="rag-fields">
        <label>重排方式<select :value="modelValue.rerank.provider || 'score'" @change="update('rerank', 'provider', ($event.target as HTMLSelectElement).value)"><option v-for="name in (capabilities?.rerankers || ['score'])" :key="name" :value="name">{{ name === 'score' ? '按检索得分排序' : name === 'model' ? '模型重排' : name }}</option></select></label>
        <label v-if="modelValue.rerank.provider === 'model'">重排模型<select v-if="rerankModels?.length" :value="modelValue.rerank.model || ''" @change="update('rerank', 'model', ($event.target as HTMLSelectElement).value)"><option value="" disabled>选择模型</option><option v-for="model in rerankModels" :key="model.id" :value="model.id">{{ model.label }}</option></select><input v-else :value="modelValue.rerank.model" placeholder="填写模型 ID" @input="update('rerank', 'model', ($event.target as HTMLInputElement).value)" /></label>
        <label>重排候选池<input type="number" min="1" max="500" :value="modelValue.rerank.candidateLimit" @change="update('rerank', 'candidateLimit', Math.max(1, Number(($event.target as HTMLInputElement).value)))" /></label>
      </div>
    </div>
    <details class="rag-advanced">
      <summary>查询规划、融合与上下文</summary>
      <div class="rag-fields">
        <label>查询规划<select :value="modelValue.queryPlanning.strategy" @change="update('queryPlanning', 'strategy', ($event.target as HTMLSelectElement).value)"><option v-for="strategy in (capabilities?.queryStrategies || ['DIRECT'])" :key="strategy" :value="strategy">{{ queryLabels[strategy] || strategy }}</option></select></label>
        <label v-if="modelValue.queryPlanning.strategy !== 'DIRECT'">规划模型 ID<input :value="modelValue.queryPlanning.modelId" placeholder="填写模型 ID" @input="update('queryPlanning', 'modelId', ($event.target as HTMLInputElement).value)" /></label>
        <label v-if="modelValue.queryPlanning.strategy !== 'DIRECT'">最多查询数<input type="number" min="1" max="10" :value="modelValue.queryPlanning.maxQueries" @change="update('queryPlanning', 'maxQueries', Math.min(10, Math.max(1, Number(($event.target as HTMLInputElement).value))))" /></label>
        <label>结果融合<select :value="modelValue.fusion.method" @change="update('fusion', 'method', ($event.target as HTMLSelectElement).value)"><option v-for="method in (capabilities?.fusionMethods || ['NONE', 'RRF'])" :key="method" :value="method">{{ method === 'NONE' ? '按得分合并' : method === 'RRF' ? 'RRF 排名融合' : method }}</option></select></label>
        <label v-if="modelValue.fusion.method === 'RRF'">RRF K<input type="number" min="1" :value="modelValue.fusion.rrfK" @change="update('fusion', 'rrfK', Math.max(1, Number(($event.target as HTMLInputElement).value)))" /></label>
        <label>相邻上下文窗口<input type="number" min="0" max="5" :value="modelValue.context.neighborWindow" @change="update('context', 'neighborWindow', Math.min(5, Math.max(0, Number(($event.target as HTMLInputElement).value))))" /></label>
        <label>上下文预算 · 估算 Token<input type="number" min="256" max="100000" :value="modelValue.context.maxTokens" @change="update('context', 'maxTokens', Math.max(256, Number(($event.target as HTMLInputElement).value)))" /></label>
      </div>
      <label class="rag-toggle"><input type="checkbox" :checked="modelValue.context.parentExpansion" @change="update('context', 'parentExpansion', ($event.target as HTMLInputElement).checked)" />扩展父片段上下文</label>
    </details>
    <details class="rag-advanced"><summary>运行预算</summary><div class="rag-fields">
      <label>检索查询数上限<input type="number" min="1" max="10" :value="modelValue.budget.maxQueries" @change="update('budget', 'maxQueries', Math.min(10, Math.max(1, Number(($event.target as HTMLInputElement).value))))" /></label>
      <label>候选总数上限<input type="number" min="1" max="1000" :value="modelValue.budget.maxCandidates" @change="update('budget', 'maxCandidates', Math.max(1, Number(($event.target as HTMLInputElement).value)))" /></label>
      <label>证据总数上限<input type="number" min="1" max="100" :value="modelValue.budget.maxEvidenceItems" @change="update('budget', 'maxEvidenceItems', Math.max(1, Number(($event.target as HTMLInputElement).value)))" /></label>
    </div></details>
  </div>
</template>

<style scoped>
.rag-policy { display: grid; gap: 16px; }
.rag-policy * { box-sizing: border-box; }
.rag-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.rag-policy label { display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: var(--kh-color-text-secondary); min-width: 0; }
.rag-policy input, .rag-policy select { width: 100%; min-width: 0; border: 1px solid var(--kh-input-border); border-radius: 7px; padding: 8px; background: var(--kh-input-bg); color: var(--kh-color-text-primary); font-family: inherit; font-size: 12px; }
.rag-policy small { font-size: 10px; color: var(--kh-color-text-tertiary); line-height: 1.4; }
.rag-channels { padding: 0; margin: 0; border: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.rag-channels legend { padding: 0; margin-bottom: 9px; font-size: 12px; color: var(--kh-color-text-secondary); }
.rag-policy .rag-channel { display: flex; flex-direction: row; align-items: center; gap: 7px; padding: 9px 10px; border: 1px solid var(--kh-color-border); border-radius: 8px; cursor: pointer; background: var(--kh-color-surface-sunken); }
.rag-channel.selected { border-color: var(--kh-color-primary); background: var(--kh-color-primary-soft); }
.rag-policy .rag-toggle { flex-direction: row; align-items: center; gap: 8px; cursor: pointer; }
.rag-policy input[type=checkbox] { width: 14px; height: 14px; accent-color: var(--kh-color-primary); margin: 0; }
.rag-rerank { display: grid; gap: 12px; padding-top: 14px; border-top: 1px solid var(--kh-color-divider); }
.rag-advanced { border-top: 1px solid var(--kh-color-divider); padding-top: 12px; }
.rag-advanced summary { cursor: pointer; color: var(--kh-color-text-secondary); font-size: 12px; }
.rag-advanced .rag-fields { margin-top: 14px; }
.rag-advanced > .rag-toggle { margin-top: 12px; }
.rag-policy input:focus-visible, .rag-policy select:focus-visible, .rag-policy summary:focus-visible { outline: 2px solid var(--kh-color-primary); outline-offset: 2px; }
@media (max-width: 420px) { .rag-fields { grid-template-columns: minmax(0, 1fr); } }
</style>
