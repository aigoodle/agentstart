<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useVueFlow } from '@vue-flow/core';

import { useWorkflowStore } from '@/stores/workflow';
import workflow_utils from '@/workflow/utils/workflow_utils';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: { type: String, default: '' },
  data: {
    type: Object,
    default: () => ({
      label: '条件分支',
      description: '条件判断分支节点',
      cases: [],
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);
const workflowStore = useWorkflowStore();
const isHovered = ref(false);
const { updateNodeInternals } = useVueFlow();

// 当 cases 数量或顺序变化时，通知 vue-flow 重新测量本节点的所有 handle 边界，
// 否则新加的 handle 边界为空，边会被画到节点默认锚点（右上角）而不是对应 case。
watch(
  () => props.data.cases?.map((c: any) => c.id).join(',') ?? '',
  async () => {
    await nextTick();
    updateNodeInternals([props.id]);
  },
);

onMounted(async () => {
  await nextTick();
  updateNodeInternals([props.id]);
});

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);
const onConnectionPlusClick = (event, nodeId, handleId) => {
  emit('connection-plus-click', event, nodeId, handleId);
};

const getOperatorText = (operator) => workflow_utils.getOperator(operator);

const getCaseSummary = (item: any) => {
  const list = item?.conditions || [];
  if (list.length === 0) return '';
  const logical = (item?.logicalOperator || 'and').toUpperCase();
  return list
    .map((it: any) => {
      const varLabel = workflow_utils.getVariableLabel(it.variableSelector) || '?';
      const op = getOperatorText(it.operator) || '';
      const val = it.value ?? '';
      return `${varLabel} ${op} ${val}`.trim();
    })
    .join(` ${logical} `);
};

const conditionCount = computed(() => props.data.cases?.length || 0);
</script>

<template>
  <div
    class="wf-node condition-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="condition"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="condition" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">
          <span>{{ conditionCount }} 个条件</span>
          <span class="wf-node-description">{{ data.description }}</span>
        </div>
      </div>
    </div>

    <div class="wf-node-content">
      <div v-if="!data.cases || data.cases.length === 0" class="wf-node-empty">
        <span class="wf-node-empty-text">尚未配置分支条件</span>
      </div>
      <div
        v-for="(item, idx) in data.cases"
        :key="item.id"
        class="condition-case"
        :style="{ borderLeftColor: item.color }"
        :title="getCaseSummary(item)"
      >
        <span class="condition-case-badge">
          {{ idx === 0 ? 'IF' : 'ELIF' }}
        </span>
        <span class="condition-case-index">#{{ idx + 1 }}</span>
        <span class="condition-case-summary">
          <template v-if="item.conditions && item.conditions.length > 0">
            {{ getCaseSummary(item) }}
          </template>
          <template v-else>
            <span class="condition-case-placeholder">未设置条件</span>
          </template>
        </span>
        <!-- 每个 case 卡片内嵌自己的 source handle。位置由卡片布局决定，
             不再依赖 JS 测量 nodeRect/caseRect，避免缩放 & 时序问题。 -->
        <NodeHandle
          :id="id"
          :handle-id="item.id"
          type="source"
          position="Right"
          :is-hovered="isHovered"
          class-name="flow-node-handle-right condition-case-handle"
          :style="{
            background: item.color || '#f59e0b',
            border: '2px solid #ffffff',
          }"
          @connection-plus-click="onConnectionPlusClick"
        />
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#f59e0b', border: '2px solid #ffffff' }"
    />

    <!-- 没有 case 时也要有一个默认 source handle，保证能连线 -->
    <NodeHandle
      v-if="!data.cases || data.cases.length === 0"
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#f59e0b', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.condition-node {
  --wf-accent: #f59e0b;
  --wf-accent-hover: #d97706;
}

.condition-case {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  margin: 4px 0;
  padding: 0 8px;
  border: 1px solid var(--wf-accent);
  border-left: 3px solid var(--wf-accent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--wf-accent) 6%, transparent);
  /* 不裁剪，让 handle 能突出到卡片外，落在节点右边缘 */
  overflow: visible;
}

.condition-case + .condition-case {
  margin-top: 6px;
}

.condition-case-badge {
  flex: 0 0 auto;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  background: color-mix(in srgb, var(--wf-accent) 85%, #000);
  border-radius: 3px;
  letter-spacing: 0.3px;
  line-height: 14px;
}

.condition-case-index {
  flex: 0 0 auto;
  font-size: 10px;
  color: color-mix(in srgb, var(--wf-accent) 80%, #000);
  font-weight: 500;
  opacity: 0.7;
}

.condition-case-summary {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 11px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.condition-case-placeholder {
  color: #9ca3af;
  font-style: italic;
}

/* 内嵌到 case 卡片的 source handle：
   - top: 50% + translateY(-50%) → 精确对齐卡片纵向中心
   - right: -18px → 补偿节点 padding(16) + 边框(2)，让 handle 中心落在节点右边框
   - translateX(50%) → 让 handle 半个身位在节点外，与其他节点视觉一致
   :deep 用于穿透 vue-flow 默认 .vue-flow__handle-right 优先级 */
.condition-case :deep(.condition-case-handle) {
  top: 50% !important;
  right: -18px !important;
  transform: translate(50%, -50%) !important;
}
</style>
