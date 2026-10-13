<script setup lang="ts">
import { h } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';

import PromptEditor from '@/components/PromptEditor.vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const addClass = () => {
  if (!Array.isArray(formState.value.classes)) formState.value.classes = [];
  formState.value.classes.push({
    id: formState.value.classes.length + 1,
    name: '',
  });
};

const removeClass = (index: number) => {
  formState.value.classes.splice(index, 1);
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="模型" required>
      <WorkflowModelPicker
        v-model="formState.model"
        model-type="LLM"
        placeholder="点击选择分类模型"
      />
    </WfField>
  </div>

  <PromptEditor
    class="wf-config-prompt"
    title="INPUT"
    :node-id="nodeId"
    v-model="formState.query"
  />

  <div class="wf-config-section">
    <WfField title="分类定义" required>
      <template #tooltip>
        逐条描述每个类别；模型根据描述将输入归到匹配度最高的类。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="addClass"
        />
      </template>

      <div
        v-if="!formState.classes || formState.classes.length === 0"
        class="classifier-empty"
      >
        至少配置一个分类，点击右上角 + 添加
      </div>

      <div class="classifier-list">
        <div
          v-for="(cls, index) in formState.classes"
          :key="cls.id ?? index"
          class="classifier-item"
        >
          <div class="classifier-item-head">
            <span class="classifier-item-name">分类 {{ index + 1 }}</span>
            <a-button
              :icon="h(DeleteOutlined)"
              size="small"
              type="text"
              danger
              @click="removeClass(index)"
            />
          </div>
          <PromptEditor
            :title="`CLASS ${index + 1}`"
            :node-id="nodeId"
            v-model="cls.name"
            :show-toolbar="false"
            min-height="60px"
            max-height="120px"
          />
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.classifier-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.classifier-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.classifier-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.classifier-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.classifier-item-name {
  font-size: 12px;
  font-weight: 500;
  color: #4b5563;
}
</style>
