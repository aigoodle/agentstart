<script setup lang="ts">
import ModelPickerPopover from '../../model-provider-hub/components/ModelPickerPopover.vue';
import type { SelectedModel } from '../../model-provider-hub/types';

defineOptions({ inheritAttrs: false });

const model = defineModel<SelectedModel>({ default: () => ({}) });
</script>

<template>
  <ModelPickerPopover
    v-bind="$attrs"
    v-model="model"
    placement="bottomLeft"
    match-trigger-width
  />
</template>
