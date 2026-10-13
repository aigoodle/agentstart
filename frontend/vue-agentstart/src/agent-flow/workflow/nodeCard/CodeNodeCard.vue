<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const LANGUAGES = [
  { value: 'python', label: 'Python' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'groovy', label: 'Groovy' },
  { value: 'jexl', label: 'JEXL' },
];

const addVariable = () => {
  if (!Array.isArray(formState.value.variables)) formState.value.variables = [];
  formState.value.variables.push({
    variable: '',
    value_selector: [],
  });
};

const removeVariable = (index: number) => {
  formState.value.variables.splice(index, 1);
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="输入变量">
      <template #tooltip>
        映射到代码函数的参数名，可从上游节点变量选择。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addVariable"
        />
      </template>

      <div
        v-if="!formState.variables || formState.variables.length === 0"
        class="code-empty"
      >
        暂无输入变量
      </div>

      <div class="code-var-list">
        <div
          v-for="(v, index) in formState.variables"
          :key="index"
          class="code-var-row"
        >
          <a-input
            v-model:value="v.variable"
            placeholder="参数名"
            size="small"
            style="width: 120px"
          />
          <VariableSelector
            v-model="v.value_selector"
            :node-id="nodeId"
            placeholder="选择变量"
            style="flex: 1"
          />
          <a-button
            :icon="h(DeleteOutlined)"
            size="small"
            type="text"
            danger
            @click="removeVariable(index)"
          />
        </div>
      </div>
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="语言" is-subtitle inline>
      <template #operations>
        <a-select
          v-model:value="formState.code_language"
          :options="LANGUAGES"
          size="small"
          style="width: 140px"
        />
      </template>
    </WfField>

    <WfField title="脚本">
      <template #tooltip>
        必须返回一个字典（对象），会自动映射到本节点的输出变量。
      </template>
      <a-textarea
        v-model:value="formState.code"
        :auto-size="{ minRows: 8, maxRows: 20 }"
        class="code-textarea"
        placeholder="def main(arg1: str, arg2: str) -> dict:&#10;    return { 'result': arg1 + arg2 }"
      />
    </WfField>
  </div>
</template>

<style scoped>
.code-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.code-var-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.code-var-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.code-textarea :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>
