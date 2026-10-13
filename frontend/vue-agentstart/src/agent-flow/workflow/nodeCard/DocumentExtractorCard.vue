<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const EXTRACT_MODES = [
  { value: 'text', label: '纯文本' },
  { value: 'markdown', label: 'Markdown' },
  { value: 'json', label: '智能结构化 (JSON)' },
];

const SPLIT_MODES = [
  { value: 'none', label: '不切分' },
  { value: 'paragraph', label: '按段落' },
  { value: 'page', label: '按页' },
  { value: 'length', label: '按长度' },
];
</script>

<template>
  <div class="wf-config-section">
    <WfField title="源文件" required>
      <template #tooltip>
        从上游变量选择 File 类型的变量，节点会提取其中的文本内容。
      </template>
      <VariableSelector
        v-model="formState.variableSelector"
        :node-id="nodeId"
        placeholder="选择文件变量"
      />
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="提取模式" is-subtitle>
      <template #tooltip>
        纯文本：直接返回文本；Markdown：保留结构标题；智能结构化：让 LLM 提取键值对。
      </template>
      <a-select
        v-model:value="formState.extractMode"
        :options="EXTRACT_MODES"
        style="width: 100%"
      />
    </WfField>

    <WfField title="切分策略" is-subtitle>
      <template #tooltip>
        对于长文档，可以按段落或按页切分为多个片段。
      </template>
      <a-select
        v-model:value="formState.splitMode"
        :options="SPLIT_MODES"
        style="width: 100%"
      />
    </WfField>

    <WfField
      v-if="formState.splitMode === 'length'"
      title="每段最大字符数"
      is-subtitle
    >
      <a-input-number
        v-model:value="formState.chunkSize"
        :min="100"
        :step="100"
        style="width: 100%"
      />
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="支持格式" is-subtitle inline>
      <template #operations>
        <span style="font-size: 11px; color: #6b7280">
          PDF · DOCX · TXT · MD · HTML
        </span>
      </template>
    </WfField>
  </div>
</template>
