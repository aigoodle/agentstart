<script setup lang="ts">
// Adapted from shadcn-vue's DialogContent. Portal, overlay and visual skin are
// composed by Modal so existing HUD corners, rails and transitions stay intact.
import type { DialogContentEmits, DialogContentProps } from 'reka-ui';
import { DialogContent, useForwardPropsEmits } from 'reka-ui';

const props = defineProps<DialogContentProps>();
const emits = defineEmits<DialogContentEmits>();
const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
  <DialogContent v-bind="forwarded">
    <slot />
  </DialogContent>
</template>
