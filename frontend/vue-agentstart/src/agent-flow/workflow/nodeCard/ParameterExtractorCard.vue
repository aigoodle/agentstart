<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h, reactive, ref, watchEffect } from 'vue';

import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue';

import PromptEditor from '@/components/PromptEditor.vue';
import VariableSelector from '@/components/VariableSelector.vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
import { Modal } from '../../../ui';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const PARAM_TYPES = [
  { value: 'string', label: 'String' },
  { value: 'number', label: 'Number' },
  { value: 'boolean', label: 'Boolean' },
  { value: 'array', label: 'Array' },
  { value: 'object', label: 'Object' },
];

const TYPE_META: Record<string, { label: string; color: string; bg: string }> =
  {
    string: { label: 'Str', color: '#2563eb', bg: '#dbeafe' },
    number: { label: 'Num', color: '#7c3aed', bg: '#ede9fe' },
    boolean: { label: 'Bool', color: '#16a34a', bg: '#dcfce7' },
    array: { label: 'Arr', color: '#ea580c', bg: '#ffedd5' },
    object: { label: 'Obj', color: '#db2777', bg: '#fce7f3' },
  };

const typeMeta = (t: string) => TYPE_META[t] || TYPE_META.string;

// 兼容历史数据：早期保存的节点没有 systemPrompt，进入面板时补一个默认
// 结构，保证 PromptEditor 的 v-model 有对象可写。
watchEffect(() => {
  if (formState.value && !formState.value.systemPrompt) {
    formState.value.systemPrompt = {
      id: 'prompt_template',
      role: 'system',
      text: '',
    };
  }
});

const modalOpen = ref(false);
const editingIndex = ref<number>(-1);
const form = reactive<{
  name: string;
  type: string;
  description: string;
  required: boolean;
}>({
  name: '',
  type: 'string',
  description: '',
  required: false,
});

function openModal(index?: number) {
  if (typeof index === 'number') {
    const src = formState.value.parameters[index] || {};
    editingIndex.value = index;
    form.name = src.name || '';
    form.type = src.type || 'string';
    form.description = src.description || '';
    form.required = !!src.required;
  } else {
    editingIndex.value = -1;
    form.name = '';
    form.type = 'string';
    form.description = '';
    form.required = false;
  }
  modalOpen.value = true;
}

function submitParam() {
  if (!Array.isArray(formState.value.parameters)) {
    formState.value.parameters = [];
  }
  const payload = {
    name: form.name.trim(),
    type: form.type,
    description: form.description,
    required: form.required,
  };
  if (!payload.name) return;
  if (editingIndex.value >= 0) {
    formState.value.parameters[editingIndex.value] = payload;
  } else {
    formState.value.parameters.push(payload);
  }
  modalOpen.value = false;
}

function removeParam(index: number) {
  formState.value.parameters.splice(index, 1);
}
</script>

<template>
  <Modal
    v-model:open="modalOpen"
    :mask-closable="false"
    :title="editingIndex >= 0 ? '编辑抽取参数' : '新增抽取参数'"
    width="480px"
    :ok-text="editingIndex >= 0 ? '保存' : '添加'"
    cancel-text="取消"
    :ok-button-props="{ disabled: !form.name.trim() }"
    @ok="submitParam"
  >
    <a-form
      layout="vertical"
      :model="form"
      autocomplete="off"
      style="padding-top: 8px"
    >
      <a-form-item label="参数名" required>
        <a-input v-model:value="form.name" placeholder="例如 city" />
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model:value="form.type" :options="PARAM_TYPES" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea
          v-model:value="form.description"
          placeholder="字段说明，用于帮助模型准确抽取，例如：用户所在城市名"
          :auto-size="{ minRows: 2, maxRows: 4 }"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="form.required">必填</a-checkbox>
      </a-form-item>
    </a-form>
  </Modal>

  <div class="wf-config-section">
    <WfField title="输入变量" required>
      <VariableSelector
        v-model="formState.inputVariableSelector"
        :node-id="nodeId"
        placeholder="选择输入文本变量"
      />
    </WfField>

    <WfField title="模型" required>
      <WorkflowModelPicker
        v-model="formState.model"
        model-type="LLM"
        placeholder="点击选择模型"
      />
    </WfField>
  </div>

  <!-- SYSTEM 提示词：用于指导抽取任务的上下文/风格；schema 约束仍由后端注入 -->
  <PromptEditor
    v-if="formState.systemPrompt"
    class="wf-config-prompt"
    title="SYSTEM"
    :node-id="nodeId"
    v-model="formState.systemPrompt.text"
    placeholder="可选：补充抽取任务的背景/规则,例如:若字段缺失请返回 null"
  />

  <div class="wf-config-section">
    <WfField title="抽取参数">
      <template #tooltip>
        为每个字段定义名称、类型与说明；模型会按此结构从输入中抽取值。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="openModal()"
        />
      </template>

      <div
        v-if="!formState.parameters || formState.parameters.length === 0"
        class="param-empty"
        @click="openModal()"
      >
        <PlusOutlined class="param-empty-icon" />
        <span>点击添加待抽取的参数</span>
      </div>

      <div v-else class="param-list">
        <div
          v-for="(p, index) in formState.parameters"
          :key="index"
          class="param-item"
          @click="openModal(index)"
        >
          <div class="param-left">
            <span
              class="param-type-badge"
              :style="{
                color: typeMeta(p.type).color,
                background: typeMeta(p.type).bg,
              }"
            >
              {{ typeMeta(p.type).label }}
            </span>
            <div class="param-text">
              <div class="param-name">
                {{ p.name || '未命名参数' }}
                <span v-if="p.required" class="param-required">*</span>
              </div>
              <div v-if="p.description" class="param-desc">
                {{ p.description }}
              </div>
            </div>
          </div>
          <div class="param-right" @click.stop>
            <a-button
              :icon="h(EditOutlined)"
              size="small"
              type="text"
              @click="openModal(index)"
            />
            <a-button
              :icon="h(DeleteOutlined)"
              size="small"
              type="text"
              danger
              @click="removeParam(index)"
            />
          </div>
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.param-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition:
    color 0.15s,
    border-color 0.15s,
    background 0.15s;
}

.param-empty:hover {
  color: #eab308;
  border-color: #eab308;
  background: #fefce8;
}

.param-empty-icon {
  font-size: 12px;
}

.param-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.param-item:hover {
  background: var(--wf-config-surface-hover, #f9fafb);
  border-color: #e5e7eb;
}

.param-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.param-text {
  min-width: 0;
  flex: 1;
}

.param-name {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.param-required {
  margin-left: 2px;
  color: #ef4444;
  font-weight: 700;
}

.param-desc {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 1px;
}

.param-type-badge {
  flex-shrink: 0;
  min-width: 34px;
  padding: 2px 6px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  border-radius: 4px;
}

.param-right {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
</style>
