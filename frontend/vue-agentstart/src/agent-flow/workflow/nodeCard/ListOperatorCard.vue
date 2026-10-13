<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const OPERATIONS = [
  { value: 'filter', label: '过滤（filter）' },
  { value: 'map', label: '映射（map）' },
  { value: 'reduce', label: '归约（reduce）' },
  { value: 'slice', label: '截取（slice）' },
  { value: 'sort', label: '排序（sort）' },
  { value: 'reverse', label: '反转（reverse）' },
  { value: 'count', label: '计数（count）' },
];

const SORT_ORDERS = [
  { value: 'asc', label: '升序' },
  { value: 'desc', label: '降序' },
];

const addFilter = () => {
  if (!Array.isArray(formState.value.filterConditions)) {
    formState.value.filterConditions = [];
  }
  formState.value.filterConditions.push({
    field: '',
    operator: 'CONTAINS',
    value: '',
  });
};

const removeFilter = (index: number) => {
  formState.value.filterConditions.splice(index, 1);
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="输入列表" required>
      <VariableSelector
        v-model="formState.inputVariableSelector"
        :node-id="nodeId"
        placeholder="选择要操作的列表变量"
      />
    </WfField>

    <WfField title="操作类型" required>
      <a-select
        v-model:value="formState.operation"
        :options="OPERATIONS"
        style="width: 100%"
      />
    </WfField>
  </div>

  <!-- 各操作对应的额外配置 -->
  <div v-if="formState.operation === 'filter'" class="wf-config-section">
    <WfField title="过滤条件">
      <template #tooltip>
        所有条件都满足才保留该项。留空字段名表示对整个 item 操作。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addFilter"
        />
      </template>
      <div
        v-if="!formState.filterConditions || formState.filterConditions.length === 0"
        class="list-op-empty"
      >
        点击右上角 + 添加条件
      </div>
      <div class="list-op-filter-list">
        <div
          v-for="(f, i) in formState.filterConditions"
          :key="i"
          class="list-op-filter-row"
        >
          <a-input
            v-model:value="f.field"
            placeholder="字段路径"
            size="small"
            style="flex: 1"
          />
          <a-select
            v-model:value="f.operator"
            :options="workflow_utils.conditionOperators"
            size="small"
            style="width: 96px"
          />
          <a-input
            v-model:value="f.value"
            placeholder="比较值"
            size="small"
            style="flex: 1"
          />
          <a-button
            :icon="h(DeleteOutlined)"
            size="small"
            type="text"
            danger
            @click="removeFilter(i)"
          />
        </div>
      </div>
    </WfField>
  </div>

  <div v-if="formState.operation === 'map'" class="wf-config-section">
    <WfField title="映射表达式">
      <template #tooltip>
        以 <code>item</code> 引用当前项。示例：<code>item.name</code>、<code>item.price * 1.1</code>
      </template>
      <a-input
        v-model:value="formState.mapExpression"
        placeholder="item.name"
      />
    </WfField>
  </div>

  <div v-if="formState.operation === 'slice'" class="wf-config-section">
    <WfField title="偏移" is-subtitle>
      <a-input-number
        v-model:value="formState.offset"
        :min="0"
        style="width: 100%"
      />
    </WfField>
    <WfField title="数量" is-subtitle>
      <a-input-number
        v-model:value="formState.limit"
        :min="0"
        style="width: 100%"
      />
    </WfField>
  </div>

  <div v-if="formState.operation === 'sort'" class="wf-config-section">
    <WfField title="排序字段" is-subtitle>
      <a-input
        v-model:value="formState.sortField"
        placeholder="留空按 item 本身"
      />
    </WfField>
    <WfField title="顺序" is-subtitle>
      <a-select
        v-model:value="formState.sortOrder"
        :options="SORT_ORDERS"
        style="width: 100%"
      />
    </WfField>
  </div>
</template>

<style scoped>
.list-op-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.list-op-filter-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-op-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
