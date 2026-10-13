<script setup>
import { computed, ref } from 'vue';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({ id: String, data: { type: Object, default: () => ({}) } });
const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);
const isHovered = ref(false);

const scheduleLabel = computed(() => {
  if (Array.isArray(props.data.inputVariableSelector) && props.data.inputVariableSelector.length) {
    return 'LLM 自动新增、修改或删除定时任务';
  }
  if (typeof props.data.schedule === 'string') {
    return props.data.schedule ? '运行时动态参数' : '未选择定时参数';
  }
  const schedule = props.data.schedule || {};
  if (schedule.scheduleType === 'ONCE') return schedule.runAt || '未设置执行时间';
  const labels = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月', CUSTOM: '自定义 Cron' };
  const prefix = labels[props.data.scheduleMode] || '周期执行';
  return props.data.scheduleMode === 'CUSTOM'
    ? (schedule.expression || '未设置 Cron')
    : `${prefix} ${props.data.time || '08:00'}`;
});
</script>

<template>
  <div class="wf-node schedule-trigger-node" @mouseenter="isHovered = true" @mouseleave="isHovered = false">
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="schedule-trigger"
      @duplicate="emit('duplicate', id)"
      @delete="emit('delete', id)"
    />
    <div class="wf-node-header">
      <div class="wf-node-icon"><Icon name="schedule" /></div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">定时任务</div>
      </div>
    </div>
    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div class="schedule-preview">
        <span class="schedule-time">{{ scheduleLabel }}</span>
        <span v-if="data.targetWorkflowId" class="schedule-target">
          <span
            class="schedule-target-icon"
            :style="{ background: data.targetWorkflowIconBackground || '#FEF6EE' }"
          >{{ data.targetWorkflowIcon || '🧬' }}</span>
          <span class="schedule-target-name">{{ data.targetWorkflowName || '目标工作流' }}</span>
        </span>
        <span v-else class="schedule-target-empty">未选择目标工作流</span>
      </div>
    </div>
    <NodeHandle :id="id" type="target" position="Left" class-name="flow-node-handle-left" :style="{ background: '#f59e0b', border: '2px solid #ffffff' }" />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#f59e0b', border: '2px solid #ffffff' }"
      @connection-plus-click="(event, nodeId, handleId) => emit('connection-plus-click', event, nodeId, handleId)"
    />
  </div>
</template>

<style scoped>
.schedule-trigger-node { --wf-accent: #f59e0b; --wf-accent-hover: #d97706; }
.schedule-preview { display: flex; flex-direction: column; gap: 5px; min-width: 0; font-size: 11px; }
.schedule-time { color: #92400e; font-weight: 600; }
.schedule-target { display: flex; align-items: center; gap: 6px; min-width: 0; color: #4b5563; }
.schedule-target-icon { display: inline-flex; flex: none; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 5px; font-size: 12px; }
.schedule-target-name { overflow: hidden; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.schedule-target-empty { color: #9ca3af; }
</style>
