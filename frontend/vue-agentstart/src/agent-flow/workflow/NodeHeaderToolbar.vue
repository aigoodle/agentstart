<script setup lang="ts">
import { h, ref } from 'vue';

import {
  CopyOutlined,
  DeleteOutlined,
  MoreOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons-vue';

defineProps<{ nodeId?: string }>();

const emit = defineEmits(['duplicate', 'delete', 'run']);
const morePopoverOpen = ref(false);

const onDuplicate = () => emit('duplicate');
const onDelete = () => emit('delete');
const onRun = () => emit('run');

const closeMore = () => (morePopoverOpen.value = false);
</script>

<template>
  <div class="wf-header-toolbar">
    <a-tooltip title="运行到此节点">
      <a-button :icon="h(PlayCircleOutlined)" size="small" @click="onRun" />
    </a-tooltip>
    <a-tooltip title="复制 (Ctrl+D)">
      <a-button :icon="h(CopyOutlined)" size="small" @click="onDuplicate" />
    </a-tooltip>
    <a-tooltip title="删除 (Del)">
      <a-button :icon="h(DeleteOutlined)" size="small" danger @click="onDelete" />
    </a-tooltip>
    <a-popover v-model:open="morePopoverOpen" trigger="click" placement="bottom">
      <template #content>
        <div class="wf-header-toolbar-menu">
          <div class="wf-header-toolbar-menu-item" @click="closeMore">
            重命名
          </div>
          <div class="wf-header-toolbar-menu-item" @click="closeMore">
            查看变量
          </div>
          <div class="wf-header-toolbar-menu-item" @click="closeMore">
            关于此节点
          </div>
        </div>
      </template>
      <a-button :icon="h(MoreOutlined)" size="small" />
    </a-popover>
  </div>
</template>

<style scoped>
.wf-header-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.wf-header-toolbar-menu {
  min-width: 140px;
  padding: 4px 0;
}

.wf-header-toolbar-menu-item {
  padding: 6px 12px;
  font-size: 13px;
  color: #1f2937;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-header-toolbar-menu-item:hover {
  background: #f3f4f6;
}
</style>
