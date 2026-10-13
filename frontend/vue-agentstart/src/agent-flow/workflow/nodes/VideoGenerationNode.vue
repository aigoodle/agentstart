<script setup>
import { ref } from 'vue';
import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';
defineProps({ id: String, data: { type: Object, default: () => ({}) } });
const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);
const hover = ref(false);
</script>
<template>
  <div class="wf-node video-node" @mouseenter="hover = true" @mouseleave="hover = false">
    <NodeHoverToolbar v-if="hover" node-type="VIDEO_GENERATION" @duplicate="emit('duplicate', id)" @delete="emit('delete', id)" />
    <div class="wf-node-header">
      <div class="wf-node-icon"><Icon name="llm" /></div>
      <div class="wf-node-info"><div class="wf-node-title">{{ data.label || '视频生成' }}</div><div class="wf-node-subtitle">{{ data.model?.providerName || '选择视频模型' }}</div></div>
    </div>
    <div class="wf-node-content">{{ data.model?.modelName || '未选择模型' }}</div>
    <NodeHandle :id="id" type="target" position="Left" class-name="flow-node-handle-left" />
    <NodeHandle :id="id" type="source" position="Right" :is-hovered="hover" class-name="flow-node-handle-right" @connection-plus-click="(event, node, handle) => emit('connection-plus-click', event, node, handle)" />
  </div>
</template>
<style scoped>.video-node { --wf-accent: #7c3aed; --wf-accent-hover: #6d28d9; }</style>
