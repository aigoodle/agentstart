<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { ref } from 'vue';

const props = defineProps({
  readonly: {
    type: Boolean,
    default: () => false,
  },
});
const targetElement = ref(null);
const showAttr = ref<boolean>(false);
const formState: any = defineModel();

const checkAttr = (event: any) => {
  const element = event.currentTarget;
  targetElement.value = element;
  showAttr.value = true;
};
const closeAttrPanel = () => {
  showAttr.value = false;
};

const selectAttr = (variableSelector: any, tag: any, node: any) => {
  formState.value.queryVariableSelector = variableSelector;
  showAttr.value = false;
};

const getVarLabel = (variableSelector) => {
  return '';
};
</script>

<template>
  <div>
    <div
      v-if="readonly"
      @click="checkAttr($event)"
      class="flex h-8 w-full cursor-pointer items-center rounded-lg border p-1"
    >
      <a-tag class="text-primary">
        {{ getVarLabel(formState.variableSelector) }}
      </a-tag>
    </div>
    <a-input v-else v-model:value="formState.value" />
  </div>
</template>

<style scoped></style>
