<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const WRITE_MODES = [
  { value: 'overwrite', label: '覆盖' },
  { value: 'append', label: '追加' },
  { value: 'clear', label: '清空' },
];

const addAssignment = () => {
  if (!Array.isArray(formState.value.assignments)) formState.value.assignments = [];
  formState.value.assignments.push({
    target: '',
    source: [],
    writeMode: 'overwrite',
  });
};

const removeAssignment = (index: number) => {
  formState.value.assignments.splice(index, 1);
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="赋值项">
      <template #tooltip>
        为每一项指定目标变量、来源变量、写入模式。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addAssignment"
        />
      </template>

      <div
        v-if="!formState.assignments || formState.assignments.length === 0"
        class="assigner-empty"
      >
        点击右上角 + 添加赋值项
      </div>

      <div class="assigner-list">
        <div
          v-for="(a, index) in formState.assignments"
          :key="index"
          class="assigner-item"
        >
          <div class="assigner-row-1">
            <a-input
              v-model:value="a.target"
              placeholder="目标变量（如 sys.foo）"
              size="small"
              style="flex: 1"
            />
            <a-select
              v-model:value="a.writeMode"
              :options="WRITE_MODES"
              size="small"
              style="width: 80px"
            />
            <a-button
              :icon="h(DeleteOutlined)"
              size="small"
              type="text"
              danger
              @click="removeAssignment(index)"
            />
          </div>
          <VariableSelector
            v-if="a.writeMode !== 'clear'"
            v-model="a.source"
            :node-id="nodeId"
            placeholder="来源变量"
          />
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.assigner-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.assigner-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assigner-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border-radius: 6px;
}

.assigner-row-1 {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
