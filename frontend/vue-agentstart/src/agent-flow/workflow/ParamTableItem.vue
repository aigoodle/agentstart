<script setup lang="ts">
import { Input as AInput, Select as ASelect, SelectOption as ASelectOption } from '../../ui';

import { Button as AsShadcnButton } from '../../ui/shadcn/button';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VarSelectField from './VarSelectField.vue';

defineProps({
  name: {
    type: String,
    default: '',
  },
  /** 当前节点 id —— 变量插入面板需要，用来查询上游节点输出。 */
  nodeId: {
    type: String,
    default: '',
  },
});

const formState: any = defineModel();

function addItem() {
  if (!Array.isArray(formState.value)) formState.value = [];
  formState.value.push({ name: '', value: '', type: 'text' });
}

function removeItem(index: number) {
  formState.value.splice(index, 1);
}
</script>

<template>
  <div class="wf-param-table">
    <div class="wf-param-table-header">
      <div class="wf-param-cell wf-param-cell-key">键</div>
      <div v-if="name === 'FORM_DATA'" class="wf-param-cell wf-param-cell-type">
        类型
      </div>
      <div class="wf-param-cell wf-param-cell-value">值</div>
      <div class="wf-param-cell wf-param-cell-action">
        <AsShadcnButton variant="ghost" class="wf-param-add" title="添加" @click="addItem">
          <PlusOutlined />
        </AsShadcnButton>
      </div>
    </div>

    <div v-if="!formState || formState.length === 0" class="wf-param-empty">
      暂无条目，点击 + 添加
    </div>

    <div
      v-for="(it, index) in formState"
      :key="index"
      class="wf-param-table-row"
    >
      <div class="wf-param-cell wf-param-cell-key">
        <a-input
          v-model:value="it.name"
          :bordered="false"
          size="small"
          placeholder="key"
        />
      </div>
      <div v-if="name === 'FORM_DATA'" class="wf-param-cell wf-param-cell-type">
        <a-select
          v-model:value="it.type"
          :bordered="false"
          size="small"
          style="width: 100%"
        >
          <a-select-option value="TEXT">text</a-select-option>
          <a-select-option value="FILE">file</a-select-option>
        </a-select>
      </div>
      <div class="wf-param-cell wf-param-cell-value">
        <VarSelectField
          v-model="it.value"
          :node-id="nodeId"
          :bordered="false"
          size="small"
          placeholder="选择变量"
        />
      </div>
      <div class="wf-param-cell wf-param-cell-action">
        <AsShadcnButton variant="ghost" class="wf-param-del" title="删除" @click="removeItem(index)">
          <DeleteOutlined />
        </AsShadcnButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-param-table {
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--background);
}

.wf-param-table-header {
  display: flex;
  align-items: center;
  padding: 4px 0;
  background: var(--muted);
  border-bottom: 1px solid var(--border);
  font-size: 11px;
  font-weight: 600;
  color: var(--muted-foreground);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.wf-param-table-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border);
  transition: background 0.12s;
}

.wf-param-table-row:last-child {
  border-bottom: 0;
}

.wf-param-table-row:hover {
  background: var(--muted);
}

.wf-param-cell {
  padding: 4px 8px;
  min-width: 0;
}

.wf-param-cell-key {
  flex: 1;
  min-width: 60px;
}

.wf-param-cell-type {
  width: 88px;
  border-left: 1px solid var(--border);
  border-right: 1px solid var(--border);
}

.wf-param-cell-value {
  flex: 2;
  min-width: 80px;
  border-left: 1px solid var(--border);
}

.wf-param-cell-action {
  width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-left: 1px solid var(--border);
}

.wf-param-cell :deep(.ant-input),
.wf-param-cell :deep(.ant-select-selector) {
  padding: 0 !important;
  background: transparent !important;
}

.wf-param-cell :deep(.ant-input:focus),
.wf-param-cell :deep(.ant-select-focused .ant-select-selector) {
  box-shadow: none !important;
}

.wf-param-add,
.wf-param-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: var(--muted-foreground);
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  transition: background 0.15s, color 0.15s;
}

.wf-param-add:hover {
  color: #4338ca;
  background: #eef2ff;
}

.wf-param-del:hover {
  color: #dc2626;
  background: #fef2f2;
}

.wf-param-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--muted-foreground);
}
</style>
