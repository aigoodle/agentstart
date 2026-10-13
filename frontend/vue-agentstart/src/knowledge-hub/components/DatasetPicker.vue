<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';

import { useKnowledge } from '../composables/useKnowledge';
import type { Dataset } from '../types';

interface Props {
  modelValue?: string[];
  multiple?: boolean;
  tenantId?: string;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  multiple: true,
  placeholder: '选择知识库',
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: string[]): void;
}>();

const options = ref<Dataset[]>([]);
const selected = ref<string[]>(props.modelValue);

watch(
  () => props.modelValue,
  (v) => (selected.value = v),
);
watch(selected, (v) => emit('update:modelValue', v));

onMounted(async () => {
  const { listDatasets } = useKnowledge();
  try {
    options.value = await listDatasets(props.tenantId);
  } catch {
    options.value = [];
  }
});
</script>

<template>
  <!--
    Note: this component intentionally does NOT depend on ant-design-vue — it
    uses a native multi-select so any Vue 3 app can host it. Style it via the
    class hooks below.
  -->
  <select
    v-model="selected"
    :multiple="multiple"
    class="knowledge-hub-picker w-full rounded border px-2 py-1"
  >
    <option v-for="d in options" :key="d.id" :value="d.id">
      {{ d.name }}
      <span v-if="d.documentCount">· {{ d.documentCount }} 文档</span>
    </option>
  </select>
</template>

<style scoped>
.knowledge-hub-picker {
  min-height: 32px;
}
</style>
