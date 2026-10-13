<script setup lang="ts">
defineProps<{ modelValue?: any }>();
</script>

<template>
  <div class="agent-flow-stub">
    <slot>
      <span class="agent-flow-stub__hint">[Embedding stub — 请注入实现]</span>
    </slot>
  </div>
</template>

<style scoped>
.agent-flow-stub {
  display: inline-block;
  padding: 4px 8px;
  border: 1px dashed #d1d5db;
  border-radius: 4px;
  color: #6b7280;
  font-size: 12px;
}
</style>
