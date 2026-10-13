<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const addOutput = () => {
  if (!Array.isArray(formState.value.output)) formState.value.output = [];
  formState.value.output.push({
    type: 'string',
    name: '',
    value: '',
    description: '',
    variableSelector: [],
  });
};

const removeOutput = (index: number) => {
  formState.value.output.splice(index, 1);
};

const TYPES = ['string', 'number', 'boolean', 'array', 'object'];
</script>

<template>
  <div class="wf-config-section">
    <WfField class="wf-output-field" title="输出变量" required>
      <template #tooltip>
        工作流结束时返回的字段，通常从上游节点选取变量。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addOutput"
        />
      </template>

      <div
        v-if="!formState.output || formState.output.length === 0"
        class="end-empty"
      >
        尚未配置输出，点击右上角 + 添加
      </div>

      <div class="end-output-list">
        <div
          v-for="(it, index) in formState.output"
          :key="index"
          class="end-output-item"
        >
          <div class="end-output-row">
            <a-input
              v-model:value="it.name"
              placeholder="变量名"
              size="small"
              style="flex: 1"
            />
            <a-select
              v-model:value="it.type"
              :options="TYPES.map((t) => ({ value: t, label: t }))"
              size="small"
              style="width: 96px"
            />
            <a-button
              :icon="h(DeleteOutlined)"
              size="small"
              type="text"
              danger
              @click="removeOutput(index)"
            />
          </div>
          <VariableSelector
            v-model="it.variableSelector"
            :node-id="nodeId"
            placeholder="选择上游变量"
          />
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.end-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.end-output-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.end-output-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.end-output-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
