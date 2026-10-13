<script setup lang="ts">
import { computed, ref } from 'vue';

import { LinkOutlined } from '@ant-design/icons-vue';

import PromptEditorTagPanel from '@/components/PromptEditorTagPanel.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

interface Props {
  nodeId: string;
  placeholder?: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '选择变量',
  disabled: false,
});

const model: any = defineModel();

const targetElement = ref<HTMLElement | null>(null);
const showPanel = ref<boolean>(false);

const label = computed(() =>
  model.value && model.value.length > 0
    ? workflow_utils.getVariableLabel(model.value)
    : '',
);

const openPanel = (event: MouseEvent) => {
  if (props.disabled) return;
  targetElement.value = event.currentTarget as HTMLElement;
  showPanel.value = true;
};

const closePanel = () => {
  showPanel.value = false;
};

const selectAttr = (variableSelector: any) => {
  model.value = variableSelector;
  showPanel.value = false;
};

const clear = (e: MouseEvent) => {
  e.stopPropagation();
  model.value = [];
};
</script>

<template>
  <div class="wf-var-selector">
    <div
      ref="targetElement"
      class="wf-var-selector-trigger"
      :class="{ 'is-disabled': disabled, 'has-value': label }"
      @click="openPanel"
    >
      <template v-if="label">
        <LinkOutlined class="wf-var-selector-icon" />
        <span class="wf-var-selector-value" :title="label">{{ label }}</span>
        <span
          v-if="!disabled"
          class="wf-var-selector-clear"
          @click="clear"
        >
          ×
        </span>
      </template>
      <template v-else>
        <LinkOutlined class="wf-var-selector-icon" />
        <span class="wf-var-selector-placeholder">{{ placeholder }}</span>
      </template>
    </div>

    <PromptEditorTagPanel
      :show="showPanel"
      :target-element="targetElement"
      :node-id="nodeId"
      @close="closePanel"
      @select="selectAttr"
    />
  </div>
</template>

<style scoped>
.wf-var-selector {
  width: 100%;
}

.wf-var-selector-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  min-height: 28px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.wf-var-selector-trigger:hover:not(.is-disabled) {
  border-color: #6366f1;
  background: #fafbff;
}

.wf-var-selector-trigger.has-value {
  background: #eef2ff;
  border-color: #c7d2fe;
}

.wf-var-selector-trigger.is-disabled {
  cursor: not-allowed;
  background: #f9fafb;
  color: #9ca3af;
}

.wf-var-selector-icon {
  flex: none;
  font-size: 12px;
  color: #6366f1;
}

.wf-var-selector-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #4338ca;
  font-weight: 500;
}

.wf-var-selector-placeholder {
  flex: 1;
  font-size: 12px;
  color: #9ca3af;
}

.wf-var-selector-clear {
  flex: none;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  color: #6366f1;
  border-radius: 50%;
  transition: background 0.15s;
}

.wf-var-selector-clear:hover {
  background: #c7d2fe;
  color: #1e1b4b;
}
</style>
