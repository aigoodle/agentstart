<script setup lang="ts">
import { ref } from 'vue';

import { useKnowledge } from '../composables/useKnowledge';
import type { RetrievalMethod, RetrievedSegment } from '../types';

import RetrievedList from './RetrievedList.vue';

interface Props {
  datasetId: string;
  initialQuery?: string;
  initialMethod?: RetrievalMethod;
  topK?: number;
}

const props = withDefaults(defineProps<Props>(), {
  initialQuery: '',
  initialMethod: 'HYBRID',
  topK: 5,
});

const query = ref(props.initialQuery);
const method = ref<RetrievalMethod>(props.initialMethod);
const hits = ref<RetrievedSegment[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

async function run() {
  if (!props.datasetId || !query.value.trim()) return;
  loading.value = true;
  error.value = null;
  try {
    const { retrieve } = useKnowledge();
    hits.value = await retrieve(props.datasetId, {
      query: query.value,
      method: method.value,
      topK: props.topK,
    });
  } catch (e: any) {
    error.value = e?.message ?? String(e);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="knowledge-hub-panel space-y-2">
    <textarea
      v-model="query"
      :rows="2"
      placeholder="输入检索问题"
      class="w-full rounded border px-2 py-1"
    ></textarea>
    <div class="flex items-center justify-between">
      <label class="text-sm">
        <select v-model="method" class="rounded border px-1 py-0.5">
          <option value="HYBRID">混合</option>
          <option value="VECTOR">仅向量</option>
          <option value="FULL_TEXT">仅关键词</option>
        </select>
      </label>
      <button
        :disabled="loading"
        class="rounded bg-blue-500 px-3 py-1 text-white disabled:opacity-50"
        @click="run"
      >
        {{ loading ? '检索中...' : '检索' }}
      </button>
    </div>
    <div v-if="error" class="text-sm text-red-500">❌ {{ error }}</div>
    <RetrievedList :hits="hits" />
  </div>
</template>
