<script setup lang="ts">
import type { RetrievedSegment } from '../types';

defineProps<{
  hits: RetrievedSegment[];
}>();
</script>

<template>
  <div v-if="hits.length === 0" class="py-4 text-center text-sm text-gray-400">
    暂无结果
  </div>
  <div
    v-for="h in hits"
    :key="h.segmentId"
    class="mb-2 rounded border border-gray-100 p-2"
  >
    <div class="mb-1 flex items-center justify-between text-xs text-gray-500">
      <span>#{{ h.position }} · doc {{ h.documentId?.slice(0, 8) }}</span>
      <span>综合 {{ h.score.toFixed(3) }}</span>
    </div>
    <div class="whitespace-pre-wrap text-sm">{{ h.content }}</div>
  </div>
</template>
