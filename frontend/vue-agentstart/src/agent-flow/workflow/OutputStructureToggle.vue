<script setup lang="ts">
const formState: any = defineModel();
</script>

<template>
  <div v-show="formState?.structOutput?.data" class="wf-output-structure-toggle">
    <span>结构化输出</span>
    <a-switch
      v-model:checked="formState.structOutputEnabled"
      size="small"
    />
  </div>
</template>

<style scoped>
.wf-output-structure-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--wf-config-text-secondary, #6b7280);
  font-size: 11px;
}
</style>
