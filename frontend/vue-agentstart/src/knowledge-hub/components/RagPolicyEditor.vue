<script setup lang="ts">
import { computed } from 'vue';
import type { RagCapabilities, RagPolicy, RagRetrievalChannel } from '../types';

const props = defineProps<{ modelValue: RagPolicy; capabilities?: RagCapabilities }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: RagPolicy): void }>();
const availableChannels = computed<RagRetrievalChannel[]>(() => props.capabilities?.retrievalChannels || ['VECTOR', 'SPARSE']);
const copy = (): RagPolicy => structuredClone(props.modelValue);
function update(section: keyof RagPolicy, key: string, value: unknown) {
  const next = copy();
  (next[section] as unknown as Record<string, unknown>)[key] = value;
  emit('update:modelValue', next);
}
function toggleChannel(channel: RagRetrievalChannel, checked: boolean) {
  const channels = new Set(props.modelValue.retrieval.channels);
  checked ? channels.add(channel) : channels.delete(channel);
  const next = copy();
  next.retrieval.channels = [...channels];
  if (channels.size > 1 && next.fusion.method === 'NONE') next.fusion.method = 'RRF';
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="rag-policy">
    <label>查询规划<select :value="modelValue.queryPlanning.strategy" @change="update('queryPlanning','strategy',($event.target as HTMLSelectElement).value)">
      <option v-for="item in (capabilities?.queryStrategies || ['DIRECT','MULTI_QUERY','HYDE','STEP_BACK','DECOMPOSITION'])" :key="item" :value="item">{{ item }}</option>
    </select></label>
    <label>最多查询数<input type="number" min="1" max="10" :value="modelValue.queryPlanning.maxQueries" @input="update('queryPlanning','maxQueries',Number(($event.target as HTMLInputElement).value))" /></label>
    <fieldset><legend>召回通道</legend><label v-for="channel in availableChannels" :key="channel">
      <input type="checkbox" :checked="modelValue.retrieval.channels.includes(channel)" @change="toggleChannel(channel,($event.target as HTMLInputElement).checked)" />{{ channel }}
    </label></fieldset>
    <label>每路候选数<input type="number" min="1" max="200" :value="modelValue.retrieval.candidateLimitPerQuery" @input="update('retrieval','candidateLimitPerQuery',Number(($event.target as HTMLInputElement).value))" /></label>
    <label>融合<select :value="modelValue.fusion.method" @change="update('fusion','method',($event.target as HTMLSelectElement).value)">
      <option v-for="item in (capabilities?.fusionMethods || ['NONE','RRF'])" :key="item" :value="item">{{ item }}</option>
    </select></label>
    <label>RRF K<input type="number" min="1" :value="modelValue.fusion.rrfK" @input="update('fusion','rrfK',Number(($event.target as HTMLInputElement).value))" /></label>
    <label><input type="checkbox" :checked="modelValue.rerank.enabled" @change="update('rerank','enabled',($event.target as HTMLInputElement).checked)" />重排序</label>
    <label>最终 Top N<input type="number" min="1" :value="modelValue.rerank.topN" @input="update('rerank','topN',Number(($event.target as HTMLInputElement).value))" /></label>
    <label><input type="checkbox" :checked="modelValue.context.parentExpansion" @change="update('context','parentExpansion',($event.target as HTMLInputElement).checked)" />父块扩展</label>
    <label>上下文 Token<input type="number" min="256" :value="modelValue.context.maxTokens" @input="update('context','maxTokens',Number(($event.target as HTMLInputElement).value))" /></label>
  </div>
</template>

<style scoped>
.rag-policy{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}.rag-policy label,.rag-policy fieldset{display:grid;gap:5px;font-size:12px;color:#475467}.rag-policy input,.rag-policy select{border:1px solid #d0d5dd;border-radius:7px;padding:7px;background:#fff}.rag-policy input[type=checkbox]{width:auto}.rag-policy fieldset{border:1px solid #e5e7eb;border-radius:8px;grid-auto-flow:column;justify-content:start}.rag-policy fieldset label{display:flex;align-items:center}
</style>
