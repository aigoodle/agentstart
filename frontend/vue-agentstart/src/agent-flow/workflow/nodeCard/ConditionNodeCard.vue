<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import {
  DeleteOutlined,
  PlusCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue';
import { VueDraggableNext } from 'vue-draggable-next';

import VariableSelector from '@/components/VariableSelector.vue';
import langUtils from '@/utils/langUtils';
import WfField from '@/workflow/WfField.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();
const conditionOperators: any = workflow_utils.conditionOperators;

const CASE_COLORS = [
  '#f97316',
  '#f59e0b',
  '#eab308',
  '#22c55e',
  '#14b8a6',
  '#06b6d4',
  '#3b82f6',
  '#6366f1',
  '#8b5cf6',
  '#a855f7',
  '#ec4899',
];

const getColor = (index: number) => CASE_COLORS[index % CASE_COLORS.length];

const addCase = () => {
  if (!Array.isArray(formState.value.cases)) formState.value.cases = [];
  const len = formState.value.cases.length;
  formState.value.cases.push({
    id: langUtils.getId('case'),
    name: '',
    caseId: 'true',
    color: getColor(len),
    logicalOperator: 'and',
    conditions: [
      {
        variableSelector: [],
        operator: 'CONTAINS',
        value: '',
      },
    ],
  });
};

const removeCase = (index: number) => {
  formState.value.cases.splice(index, 1);
};

const addCondition = (caseItem: any) => {
  caseItem.conditions.push({
    variableSelector: [],
    operator: 'CONTAINS',
    value: '',
  });
};

const removeCondition = (caseItem: any, condIndex: number) => {
  caseItem.conditions.splice(condIndex, 1);
};

// Case 级别的 AND / OR 切换：一次改整案的 logicalOperator，与
// `ConditionNode.getCaseSummary` 展示的连接词保持一致。
const toggleLogicalOperator = (caseItem: any) => {
  const current = (caseItem.logicalOperator || 'and').toLowerCase();
  caseItem.logicalOperator = current === 'and' ? 'or' : 'and';
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="分支条件">
      <template #tooltip>
        按顺序判断，第一个满足的分支生效。ELSE 分支从画布右侧默认输出走。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addCase"
        />
      </template>
    </WfField>

    <div
      v-if="!formState.cases || formState.cases.length === 0"
      class="cond-empty"
    >
      尚未添加分支，点击右上角 + 添加
    </div>

    <VueDraggableNext
      v-model="formState.cases"
      item-key="id"
      :animation="200"
      ghost-class="cond-ghost"
      chosen-class="cond-chosen"
      class="cond-cases"
    >
      <div
        v-for="(item, index) in formState.cases"
        :key="item.id"
        class="cond-case"
        :style="{ borderLeftColor: item.color || getColor(index) }"
      >
        <div class="cond-case-head">
          <div class="cond-case-title">
            <span class="cond-case-badge">
              {{ index === 0 ? 'IF' : 'ELIF' }}
            </span>
            <span class="cond-case-tag">CASE {{ index + 1 }}</span>
          </div>
          <a-button
            :icon="h(DeleteOutlined)"
            size="small"
            type="text"
            danger
            @click="removeCase(index)"
          />
        </div>

        <div class="cond-condition-list">
          <template
            v-for="(cond, condIndex) in item.conditions"
            :key="condIndex"
          >
            <!-- 相邻条件之间的 AND / OR 连接词：点击可整案切换 —— Dify parity
                 这里的逻辑操作符是 case 级别（所有条件共享一个），而不是每对
                 相邻条件独立设置。 -->
            <button
              v-if="condIndex > 0"
              type="button"
              class="cond-logical-op"
              :class="{
                'cond-logical-op-or': (item.logicalOperator || 'and') === 'or',
              }"
              @click="toggleLogicalOperator(item)"
              :title="'点击切换 AND / OR'"
            >
              {{ (item.logicalOperator || 'and').toUpperCase() }}
            </button>

            <div class="cond-condition">
              <div class="cond-condition-row">
                <VariableSelector
                  v-model="cond.variableSelector"
                  :node-id="nodeId"
                  placeholder="选择变量"
                  style="flex: 1"
                />
                <a-select
                  v-model:value="cond.operator"
                  :options="conditionOperators"
                  size="small"
                  style="width: 110px"
                />
              </div>
              <div class="cond-condition-value">
                <a-input v-model:value="cond.value" placeholder="比较值" />
                <a-button
                  v-if="item.conditions.length > 1"
                  :icon="h(DeleteOutlined)"
                  size="small"
                  type="text"
                  danger
                  @click="removeCondition(item, condIndex)"
                />
              </div>
            </div>
          </template>
        </div>

        <a-button
          :icon="h(PlusCircleOutlined)"
          size="small"
          type="link"
          @click="addCondition(item)"
        >
          添加条件
        </a-button>
      </div>
    </VueDraggableNext>

  </div>
</template>

<style scoped>
.cond-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.cond-cases {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cond-case {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  background: var(--wf-config-surface, #ffffff);
  border: 1px solid #f0f0f0;
  border-left: 3px solid #d1d5db;
  border-radius: 6px;
  cursor: move;
  transition: box-shadow 0.15s;
}

.cond-case:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.cond-case-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cond-case-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cond-case-badge {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  color: #ffffff;
  background: #6366f1;
  border-radius: 4px;
}

.cond-case-tag {
  font-size: 11px;
  color: #9ca3af;
}

.cond-condition-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cond-condition {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border-radius: 4px;
}

/* 条件之间的 AND / OR 连接钮：一颗药丸样式，AND 走 accent 蓝、OR 走琥珀色以示区别 */
.cond-logical-op {
  align-self: flex-start;
  margin: 2px 0 2px 12px;
  padding: 1px 10px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.6px;
  color: #4338ca;
  background: color-mix(in srgb, var(--wf-accent, #6366f1) 14%, var(--wf-config-surface, #fff));
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  line-height: 16px;
}
.cond-logical-op:hover {
  background: color-mix(in srgb, var(--wf-accent, #6366f1) 20%, var(--wf-config-surface, #fff));
  border-color: #a5b4fc;
}
.cond-logical-op-or {
  color: #b45309;
  background: #fef3c7;
  border-color: #fcd34d;
}
.cond-logical-op-or:hover {
  background: #fde68a;
  border-color: #fbbf24;
}

.cond-condition-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cond-condition-value {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cond-ghost {
  opacity: 0.4;
  background: var(--wf-config-surface-hover, #f9fafb);
}

.cond-chosen {
  background: color-mix(in srgb, var(--wf-accent, #6366f1) 14%, var(--wf-config-surface, #fff));
}
</style>
