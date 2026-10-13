<script setup lang="ts">
/**
 * Thin adapter between DatasetItemCard's imperative
 * {@code showModal()} / {@code formSubmit} API and the shared
 * {@link DatasetPickerModal}. Historically this file was a stand-alone stub
 * that admitted "please replace me" — now the real picker lives in the
 * knowledge-hub module and both the workflow KNOWLEDGE_RETRIEVAL node and the
 * agent-studio 编排 knowledge card share the same UX.
 */
import { computed, ref } from 'vue';

import DatasetPickerModal from '../../../knowledge-hub/components/DatasetPickerModal.vue';
import type { DatasetSummary } from '../../../knowledge-hub/types';

export interface DatasetLike {
  id: string;
  name: string;
  [key: string]: unknown;
}

const props = defineProps<{
  /** Currently selected dataset objects (id + name at minimum). */
  mainData?: DatasetLike[];
  /** Optional tenant filter passed through to the catalog fetch. */
  tenantId?: string;
}>();

const emit = defineEmits<{
  (e: 'formSubmit', datasets: DatasetLike[]): void;
}>();

const open = ref(false);

const initialSelectedIds = computed(() =>
  (props.mainData ?? []).map((d) => d.id).filter((id): id is string => !!id),
);

function showModal() {
  open.value = true;
}
function hideModal() {
  open.value = false;
}

function onPicked(datasets: DatasetSummary[]) {
  // Preserve any callsite-specific fields (e.g. custom rerank overrides
  // stashed on the row) by merging over the previous selection when the id
  // matches, otherwise drop through with just the wire fields.
  const prev = new Map(
    (props.mainData ?? []).map((d) => [d.id, d] as const),
  );
  const merged: DatasetLike[] = datasets.map((d) => ({
    ...(prev.get(d.id) ?? {}),
    id: d.id,
    name: d.name,
  }));
  emit('formSubmit', merged);
}

defineExpose({ showModal, hideModal });
</script>

<template>
  <DatasetPickerModal
    v-model:open="open"
    :initial-selected-ids="initialSelectedIds"
    :tenant-id="tenantId"
    @submit="onPicked"
  />
</template>
