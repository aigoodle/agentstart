<script setup lang="ts">
import { h, ref } from 'vue';

import {
  DeleteOutlined,
  PlusOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue';

import ToolChooser from '@/components/stubs/ToolChooser.vue';

const formState: any = defineModel();
const toolChooserRef = ref();

const toolChooserSubmit = (tools: any[]) => {
  formState.value.tools = tools.map(tool => ({ ...tool, enabled: true }));
};

function openChooser() {
  toolChooserRef.value?.showModal?.(formState.value.tools || []);
}

function removeTool(index: number) {
  formState.value.tools.splice(index, 1);
}
</script>

<template>
  <div class="wf-tools">
    <div class="wf-tools-header">
      <span class="wf-tools-hint">添加工具增强 Agent 能力</span>
      <a-button
        :icon="h(PlusOutlined)"
        size="small"
        type="primary"
        @click="openChooser"
      />
    </div>

    <div v-if="!formState.tools || formState.tools.length === 0" class="wf-tools-empty">
      尚未添加工具
    </div>

    <div v-else class="wf-tools-list">
      <div
        v-for="(tool, index) in formState.tools"
        :key="tool.id ?? index"
        class="wf-tools-item"
      >
        <div class="wf-tools-item-left">
          <div class="wf-tools-item-icon">
            <ToolOutlined />
          </div>
          <div class="wf-tools-item-name">
            {{ tool.name || tool.label || `工具 ${index + 1}` }}
          </div>
        </div>
        <div class="wf-tools-item-right">
          <a-switch
            v-model:checked="tool.enabled"
            size="small"
            :default-checked="true"
          />
          <a-button
            :icon="h(DeleteOutlined)"
            type="text"
            size="small"
            danger
            @click="removeTool(index)"
          />
        </div>
      </div>
    </div>

    <ToolChooser
      ref="toolChooserRef"
      @form-submit="toolChooserSubmit"
    />
  </div>
</template>

<style scoped>
.wf-tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wf-tools-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wf-tools-hint {
  font-size: 11px;
  color: #9ca3af;
}

.wf-tools-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: #f9fafb;
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.wf-tools-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wf-tools-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  transition: border-color 0.15s;
}

.wf-tools-item:hover {
  border-color: #6366f1;
}

.wf-tools-item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.wf-tools-item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #7c3aed;
  background: #f3e8ff;
  border-radius: 6px;
}

.wf-tools-item-name {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-tools-item-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
