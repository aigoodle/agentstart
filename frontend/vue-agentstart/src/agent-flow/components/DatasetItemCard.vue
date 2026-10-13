<script setup lang="ts">
import { h, ref } from 'vue';

import {
  DeleteOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue';

import DatasetChooser from '@/components/stubs/DatasetChooser.vue';
import VariableSelector from '@/components/VariableSelector.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

defineProps({
  nodeId: {
    type: String,
    default: '',
  },
});

const formState: any = defineModel();
const conditionOperators: any = workflow_utils.conditionOperators;
const datasetItems: any = ref([]);
const datasetChooserRef = ref();

const datasetChooserSubmit = (data: any) => {
  formState.value.datasets = [...data];
};

function toChooserDataset() {
  datasetItems.value = [...(formState.value.datasets || [])];
  datasetChooserRef.value?.showModal?.();
}

function toRemoveItem(index: number) {
  formState.value.datasets.splice(index, 1);
}

function toAddMetadataCondition() {
  const metadataConditions = formState.value.metadataConditions || [];
  metadataConditions.push({ name: '', value: '', operator: 'IS' });
  formState.value.metadataConditions = metadataConditions;
}

function toRemoveMetadataCondition(index: number) {
  formState.value.metadataConditions.splice(index, 1);
}
</script>

<template>
  <div class="wf-ds">
    <div class="wf-ds-toolbar">
      <a class="wf-ds-link" @click="toChooserDataset">
        <SettingOutlined />
        召回设置
      </a>
      <a
        v-if="!formState.dynamic"
        class="wf-ds-link"
        @click="toChooserDataset"
      >
        <PlusOutlined />
        添加
      </a>
      <div class="wf-ds-dynamic">
        <a-switch v-model:checked="formState.dynamic" size="small" />
        <span>动态</span>
      </div>
    </div>

    <div v-if="formState.dynamic" class="wf-ds-dynamic-slot">
      <slot name="dynamic" />
    </div>

    <div v-else-if="!formState.datasets || formState.datasets.length === 0" class="wf-ds-empty">
      您可以导入知识库作为上下文
    </div>

    <div v-else class="wf-ds-list">
      <div
        v-for="(dataset, index) in formState.datasets"
        :key="dataset.name || index"
        class="wf-ds-item"
      >
        <div class="wf-ds-item-left">
          <div class="wf-ds-item-icon">
            <FolderOpenOutlined />
          </div>
          <div class="wf-ds-item-name">{{ dataset.name }}</div>
        </div>
        <a-popconfirm title="确定要移除吗?" @confirm="toRemoveItem(index)">
          <a-button
            danger
            :icon="h(DeleteOutlined)"
            type="text"
            size="small"
          />
        </a-popconfirm>
      </div>
    </div>

    <div class="wf-ds-options">
      <div class="wf-ds-option-row">
        <span class="wf-ds-option-label">优先使用快捷标注</span>
        <a-switch v-model:checked="formState.useAnnotation" size="small" />
      </div>

      <div class="wf-ds-option-row">
        <span class="wf-ds-option-label">元数据过滤</span>
        <div style="display: flex; align-items: center; gap: 6px">
          <a-button
            v-if="formState.metadataFilter === 'condition'"
            :icon="h(PlusOutlined)"
            size="small"
            type="primary"
            @click="toAddMetadataCondition"
          />
          <a-select
            v-model:value="formState.metadataFilter"
            size="small"
            style="width: 92px"
          >
            <a-select-option value="disabled">禁用</a-select-option>
            <a-select-option value="condition">手动</a-select-option>
            <a-select-option value="autop">自动</a-select-option>
          </a-select>
        </div>
      </div>

      <div
        v-if="formState.metadataFilter === 'condition'"
        class="wf-ds-cond-list"
      >
        <div
          v-for="(condition, i) in formState.metadataConditions"
          :key="i"
          class="wf-ds-cond-item"
        >
          <a-input
            v-model:value="condition.name"
            placeholder="字段名"
            size="small"
            style="flex: 1"
          />
          <a-select
            v-model:value="condition.operator"
            :options="conditionOperators"
            size="small"
            style="width: 96px"
          />
          <VariableSelector
            v-model="condition.variableSelector"
            :node-id="nodeId"
            placeholder="选择变量"
            style="flex: 1"
          />
          <a-button
            danger
            :icon="h(DeleteOutlined)"
            type="text"
            size="small"
            @click="toRemoveMetadataCondition(i)"
          />
        </div>
      </div>
    </div>

    <DatasetChooser
      ref="datasetChooserRef"
      :main-data="datasetItems"
      @form-submit="datasetChooserSubmit"
    />
  </div>
</template>

<style scoped>
.wf-ds {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wf-ds-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.wf-ds-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6366f1;
  cursor: pointer;
}

.wf-ds-link:hover {
  color: #4338ca;
}

.wf-ds-dynamic {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 12px;
  color: #4b5563;
}

.wf-ds-dynamic-slot {
  padding: 10px;
  background: #eef2ff;
  border-radius: 6px;
}

.wf-ds-empty {
  padding: 20px 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: #f9fafb;
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.wf-ds-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wf-ds-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  transition: border-color 0.15s;
}

.wf-ds-item:hover {
  border-color: #6366f1;
}

.wf-ds-item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.wf-ds-item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 6px;
}

.wf-ds-item-name {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-ds-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.wf-ds-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #f9fafb;
  border-radius: 6px;
}

.wf-ds-option-label {
  font-size: 12px;
  color: #4b5563;
}

.wf-ds-cond-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  background: #f9fafb;
  border-radius: 6px;
}

.wf-ds-cond-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
