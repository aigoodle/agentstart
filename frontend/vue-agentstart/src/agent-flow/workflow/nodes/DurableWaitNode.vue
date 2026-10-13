<script setup lang="ts">
import { computed, ref } from 'vue';
import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps<{ id?: string; type?: string; data?: Record<string, any> }>();
const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);
const hovered = ref(false);
const meta = computed(() => ({
  APPROVAL: { subtitle: '等待审批决定', field: '审批策略', value: props.data?.timeoutStrategy || 'TIMEOUT' },
  WAIT_EVENT: { subtitle: '等待外部事件', field: '关联键', value: props.data?.correlationKey || '未配置' },
  SLEEP_UNTIL: { subtitle: '持久化定时等待', field: '唤醒时间', value: props.data?.until || (props.data?.delayMillis ? `延迟 ${props.data.delayMillis} ms` : '未配置') },
  HUMAN_INPUT: { subtitle: '等待人工输入', field: '表单字段', value: `${Object.keys(props.data?.inputSchema?.properties || {}).length} 个` },
}[props.type || 'HUMAN_INPUT']));
</script>

<template>
  <div class="wf-node durable-wait-node" @mouseenter="hovered = true" @mouseleave="hovered = false">
    <NodeHoverToolbar v-if="hovered" :node-type="type" @duplicate="emit('duplicate', id)" @delete="emit('delete', id)" />
    <div class="wf-node-header">
      <div class="wf-node-icon"><Icon :name="type === 'SLEEP_UNTIL' ? 'schedule' : 'user'" /></div>
      <div class="wf-node-info"><div class="wf-node-title">{{ data?.label }}</div><div class="wf-node-subtitle">{{ meta?.subtitle }}</div></div>
    </div>
    <div class="wf-node-content">
      <div class="wf-node-description">{{ data?.description }}</div>
      <div class="wf-node-preview"><div class="wf-node-preview-label">{{ meta?.field }}</div><div class="wf-node-preview-value">{{ meta?.value }}</div></div>
    </div>
    <NodeHandle :id="id" type="target" position="Left" class-name="flow-node-handle-left" :style="{ background: '#f59e0b', border: '2px solid #fff' }" />
    <NodeHandle :id="id" type="source" position="Right" :is-hovered="hovered" class-name="flow-node-handle-right" :style="{ background: '#f59e0b', border: '2px solid #fff' }" @connection-plus-click="(event: Event, nodeId: string, handleId: string) => emit('connection-plus-click', event, nodeId, handleId)" />
  </div>
</template>

<style scoped>.durable-wait-node{--wf-accent:#f59e0b;--wf-accent-hover:#d97706}</style>
