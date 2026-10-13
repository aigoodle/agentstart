<script setup lang="ts">
import { ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import type { AgentVersion } from '../../client/agents';

const props = withDefaults(defineProps<{
  client: AgentStartClient;
  agentId?: string;
  modelValue?: string;
  followLabel?: string;
}>(), {
  agentId: '',
  modelValue: '',
  followLabel: '跟随当前发布版本',
});

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

const versions = ref<AgentVersion[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
let requestSequence = 0;

function labelOf(version: AgentVersion) {
  const status = version.status === 'ACTIVE' ? '当前发布' : '历史可运行';
  return `v${version.versionNumber} · ${status}${version.changeSummary ? ` · ${version.changeSummary}` : ''}`;
}

watch(
  () => props.agentId,
  async (agentId) => {
    const sequence = ++requestSequence;
    versions.value = [];
    loadFailed.value = false;
    if (!agentId) {
      loading.value = false;
      if (props.modelValue) emit('update:modelValue', '');
      return;
    }
    loading.value = true;
    try {
      const result = await props.client.agents.listVersions(agentId);
      if (sequence !== requestSequence) return;
      versions.value = result.filter((version) => version.status !== 'DISABLED');
      if (props.modelValue && !versions.value.some((version) => version.id === props.modelValue)) {
        emit('update:modelValue', '');
      }
    } catch {
      if (sequence === requestSequence) loadFailed.value = true;
    } finally {
      if (sequence === requestSequence) loading.value = false;
    }
  },
  { immediate: true },
);
</script>

<template>
  <select
    :value="modelValue"
    :disabled="!agentId || loading"
    aria-label="Agent 版本"
    @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
  >
    <option value="">
      {{ !agentId ? '请先选择 Agent' : loading ? '正在加载版本…' : loadFailed ? '版本加载失败，保存时跟随当前版本' : followLabel }}
    </option>
    <option v-for="version in versions" :key="version.id" :value="version.id">
      {{ labelOf(version) }}
    </option>
  </select>
</template>
