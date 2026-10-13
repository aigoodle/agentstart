<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const addItem = () => {
  if (!formState.value.output) formState.value.output = [{ children: [] }];
  if (!Array.isArray(formState.value.output[0].children)) {
    formState.value.output[0].children = [];
  }
  formState.value.output[0].children.push({
    type: 'string',
    name: '',
    label: '',
    value: '',
    description: '',
    variableSelector: [],
  });
};

const removeItem = (index: number) => {
  formState.value.output[0].children.splice(index, 1);
};

const onNameChange = (it: any) => {
  it.label = it.name;
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="聚合变量" required>
      <template #tooltip>
        把多个上游变量映射到本节点的输出字段，供下游统一引用。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addItem"
        />
      </template>

      <div
        v-if="
          !formState.output ||
          !formState.output[0]?.children ||
          formState.output[0].children.length === 0
        "
        class="var-empty"
      >
        点击右上角 + 添加聚合项
      </div>

      <div class="var-list">
        <div
          v-for="(it, index) in formState.output[0]?.children"
          :key="index"
          class="var-item"
        >
          <a-input
            v-model:value="it.name"
            placeholder="字段名"
            size="small"
            style="width: 120px"
            @change="onNameChange(it)"
          />
          <VariableSelector
            v-model="it.variableSelector"
            :node-id="nodeId"
            placeholder="选择上游变量"
            style="flex: 1"
          />
          <a-button
            :icon="h(DeleteOutlined)"
            size="small"
            type="text"
            danger
            @click="removeItem(index)"
          />
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.var-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.var-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
</style>
