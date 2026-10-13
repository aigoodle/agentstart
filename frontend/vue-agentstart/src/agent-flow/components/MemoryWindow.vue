<script setup lang="ts">
const memory: any = defineModel();

const OPTIONS: any[] = [
  { value: '0', label: '忽略' },
  { value: 'ALL', label: '全部' },
  { value: '1', label: '最后1条' },
  { value: '5', label: '最后5条' },
];
</script>

<template>
  <div class="wf-mem">
    <div class="wf-mem-toggle">
      <span class="wf-mem-toggle-label">记忆窗口</span>
      <a-switch v-model:checked="memory.window.enabled" size="small" />
    </div>

    <div class="wf-mem-slider">
      <a-slider
        v-model:value="memory.window.size"
        :disabled="!memory.window.enabled"
        :min="1"
        :max="20"
        style="flex: 1"
      />
      <a-input-number
        v-model:value="memory.window.size"
        :disabled="!memory.window.enabled"
        :min="1"
        :max="20"
        size="small"
        style="width: 72px"
      />
    </div>

    <div class="wf-mem-roles">
      <div class="wf-mem-role">
        <label>User</label>
        <a-select
          v-model:value="memory.window.user"
          :options="OPTIONS"
          :disabled="!memory.window.enabled"
          size="small"
          style="flex: 1"
        />
      </div>
      <div class="wf-mem-role">
        <label>Assistant</label>
        <a-select
          v-model:value="memory.window.assistant"
          :options="OPTIONS"
          :disabled="!memory.window.enabled"
          size="small"
          style="flex: 1"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-mem {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wf-mem-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wf-mem-toggle-label {
  font-size: 12px;
  color: #4b5563;
}

.wf-mem-slider {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wf-mem-roles {
  display: flex;
  gap: 8px;
}

.wf-mem-role {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wf-mem-role label {
  flex: none;
  font-size: 11px;
  color: #6b7280;
  min-width: 56px;
}
</style>
