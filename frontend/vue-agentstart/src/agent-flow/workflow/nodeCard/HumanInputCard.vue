<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';
import PromptEditor from '@/components/PromptEditor.vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
import WfField from '@/workflow/WfField.vue';
import { createAgentStartClient } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import type { ChannelConnection, ChannelDefinition } from '../../../channel-hub/types';

defineProps<{ nodeId?: string }>();
const formState: any = defineModel();
type FieldType = 'string' | 'textarea' | 'number' | 'select' | 'boolean' | 'date' | 'datetime';
type FormField = { name: string; label: string; type: FieldType; required: boolean; placeholder?: string; options?: Array<{ label: string; value: string }> };
const FIELD_TYPES = [
  { value: 'string', label: '输入框' }, { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字输入' }, { value: 'select', label: '下拉框' },
  { value: 'boolean', label: '开关/确认' }, { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
];
const TIME_UNITS = [
  { value: 'minute', label: '分钟', seconds: 60 },
  { value: 'hour', label: '小时', seconds: 3600 },
  { value: 'day', label: '天', seconds: 86400 },
];
const FORM_MODES = [
  { value: 'FIXED', label: '固定表单' },
  { value: 'AI', label: 'AI 动态生成' },
  { value: 'HYBRID', label: '固定 + AI 补充' },
];
const PRESENTATIONS = [
  { value: 'AUTO', label: '自动适配' }, { value: 'CHAT_COMPONENT', label: '聊天表单' },
  { value: 'WEB_FORM', label: '网页表单' }, { value: 'CHANNEL_NATIVE', label: '渠道原生' },
  { value: 'TEXT', label: '纯文本' },
];
const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: global.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(global.headers) });
const channels = ref<ChannelDefinition[]>([]);
const connections = ref<ChannelConnection[]>([]);
const channelOptions = computed(() => channels.value.filter((item) => (item.capabilities?.humanInteraction as any)?.outbound)
  .map((item) => ({ value: `${item.provider}:${item.channelId}`, label: item.name })));
const selectedChannelKey = computed({
  get: () => formState.value.channelProvider && formState.value.channelId ? `${formState.value.channelProvider}:${formState.value.channelId}` : '',
  set: (key: string) => { const [provider = '', channelId = ''] = (key || '').split(':'); formState.value.channelProvider = provider; formState.value.channelId = channelId; formState.value.channelConnectionId = ''; },
});
const connectionOptions = computed(() => connections.value.filter((item) => item.provider === formState.value.channelProvider && item.channelId === formState.value.channelId)
  .map((item) => ({ value: item.id, label: item.name })));
const modeExpanded = ref(true);
const usesAi = computed(() => modeExpanded.value && (formState.value.formMode === 'AI' || formState.value.formMode === 'HYBRID'));
const usesFixed = computed(() => modeExpanded.value && formState.value.formMode !== 'AI');
function selectMode(mode: string) {
  if (formState.value.formMode === mode) {
    modeExpanded.value = !modeExpanded.value;
    return;
  }
  formState.value.formMode = mode;
  modeExpanded.value = true;
}
const fields = computed<FormField[]>(() => formState.value.formFields || []);
const unitSeconds = computed(() => TIME_UNITS.find((item) => item.value === formState.value.timeoutUnit)?.seconds || 3600);

function schemaToFields(schema: any): FormField[] {
  const required = new Set<string>(schema?.required || []);
  return Object.entries(schema?.properties || {}).map(([name, raw]: [string, any]) => ({
    name, label: raw.title || name,
    type: raw['x-component'] || (raw.enum ? 'select' : ['integer', 'number'].includes(raw.type) ? 'number' : raw.type === 'boolean' ? 'boolean' : raw.format === 'date' ? 'date' : raw.format === 'date-time' ? 'datetime' : 'string'),
    required: required.has(name), placeholder: raw.description || '',
    options: (raw.enum || []).map((value: string, index: number) => ({ value, label: raw['x-enum-labels']?.[index] || value })),
  }));
}
function syncGeneratedConfig() {
  const properties: Record<string, any> = {};
  const required: string[] = [];
  const outputs: any[] = [];
  for (const field of fields.value) {
    const name = field.name?.trim();
    if (!name) continue;
    const property: Record<string, any> = { type: field.type === 'number' ? 'number' : field.type === 'boolean' ? 'boolean' : 'string', title: field.label?.trim() || name };
    if (field.placeholder) property.description = field.placeholder;
    if (field.type === 'textarea') property['x-component'] = 'textarea';
    if (field.type === 'select') {
      const options = (field.options || []).filter((item) => item.value);
      property.enum = options.map((item) => item.value);
      property['x-enum-labels'] = options.map((item) => item.label || item.value);
    }
    if (field.type === 'date') property.format = 'date';
    if (field.type === 'datetime') property.format = 'date-time';
    properties[name] = property;
    if (field.required) required.push(name);
    outputs.push({ type: property.type, name, value: property.type === 'boolean' ? false : property.type === 'number' ? 0 : '', label: property.title });
  }
  formState.value.inputSchema = {
    type: 'object',
    title: formState.value.formTitle?.trim() || '人工输入',
    description: formState.value.prompt?.trim() || '',
    'x-submit-button-text': formState.value.submitButtonText?.trim() || '提交',
    properties,
    ...(required.length ? { required } : {}),
  };
  formState.value.output = [
    { type: 'object', name: 'values', value: {}, label: '人工提交数据' },
    ...(formState.value.formMode !== 'AI' ? outputs : []),
  ];
  formState.value.timeoutSeconds = formState.value.timeoutEnabled ? Math.round((Number(formState.value.timeoutValue) || 1) * unitSeconds.value) : 0;
}
onMounted(() => {
  const legacyFields = formState.value.formFields;
  const legacySchema = formState.value.inputSchema;
  const isImplicitLegacyResponse = Array.isArray(legacyFields)
    && legacyFields.length === 1
    && legacyFields[0]?.name === 'response'
    && (legacyFields[0]?.label === '回复内容' || !legacyFields[0]?.label)
    && Object.keys(legacySchema?.properties || {}).length === 1
    && legacySchema?.properties?.response?.type === 'string';
  if (isImplicitLegacyResponse) {
    formState.value.formFields = [];
    formState.value.inputSchema = { type: 'object', properties: {} };
    formState.value.output = [];
  }
  if (!Array.isArray(formState.value.formFields) || !formState.value.formFields.length) formState.value.formFields = schemaToFields(formState.value.inputSchema);
  if (formState.value.timeoutEnabled == null) formState.value.timeoutEnabled = Number(formState.value.timeoutSeconds) > 0;
  if (!formState.value.timeoutUnit) formState.value.timeoutUnit = 'hour';
  if (!formState.value.timeoutValue) formState.value.timeoutValue = Math.max(1, (Number(formState.value.timeoutSeconds) || 3600) / 3600);
  if (!formState.value.submitButtonText) formState.value.submitButtonText = '提交';
  if (!formState.value.formMode) formState.value.formMode = 'FIXED';
  if (!formState.value.model) formState.value.model = { modelId: '', modelName: '', modelProvider: '', mode: 'chat', completionParams: {} };
  if (!formState.value.generationPrompt) formState.value.generationPrompt = '{{#sys.query#}}';
  if (!formState.value.presentationMode) formState.value.presentationMode = 'AUTO';
  if (!formState.value.deliveryFallback) formState.value.deliveryFallback = 'WEB_LINK_THEN_TEXT';
  if (!formState.value.formTitle) formState.value.formTitle = formState.value.inputSchema?.title || '人工输入';
  void Promise.all([client.channels.listChannels(), client.channels.listChannelConnections()]).then(([catalog, saved]) => { channels.value = catalog; connections.value = saved; }).catch(() => { /* channel hub may be disabled */ });
  syncGeneratedConfig();
});
watch(() => [formState.value.formMode, formState.value.formFields, formState.value.formTitle, formState.value.prompt, formState.value.submitButtonText, formState.value.timeoutEnabled, formState.value.timeoutValue, formState.value.timeoutUnit], syncGeneratedConfig, { deep: true });
const addField = () => {
  if (!Array.isArray(formState.value.formFields)) formState.value.formFields = [];
  const next = formState.value.formFields.length + 1;
  formState.value.formFields.push({ name: `field_${next}`, label: `字段 ${next}`, type: 'string', required: false, placeholder: '', options: [] });
};
const removeField = (index: number) => formState.value.formFields.splice(index, 1);
const moveField = (index: number, offset: number) => {
  const target = index + offset;
  if (target < 0 || target >= formState.value.formFields.length) return;
  const [field] = formState.value.formFields.splice(index, 1);
  formState.value.formFields.splice(target, 0, field);
};
const addOption = (field: FormField) => {
  if (!Array.isArray(field.options)) field.options = [];
  field.options.push({ label: '', value: '' });
};
</script>

<template>
  <div class="wf-config-section">
    <WfField title="表单生成方式" required>
      <div class="human-mode-list">
        <button
          v-for="mode in FORM_MODES"
          :key="mode.value"
          type="button"
          class="human-mode-button"
          :class="{ 'is-active': formState.formMode === mode.value && modeExpanded }"
          @click="selectMode(mode.value)"
        >
          <span class="human-mode-check">{{ formState.formMode === mode.value ? '✓' : '' }}</span>
          <span>{{ mode.label }}</span>
        </button>
      </div>
      <div class="human-help">AI 模式的 JSON Schema 格式、组件白名单和输出约束由后台自动补充。</div>
    </WfField>
  </div>
  <div v-if="usesAi" class="wf-config-section">
    <WfField title="生成模型" required>
      <WorkflowModelPicker v-model="formState.model" model-type="LLM" placeholder="点击选择生成表单的模型" />
    </WfField>
  </div>
  <PromptEditor
    v-if="usesAi"
    class="wf-config-prompt"
    title="生成要求"
    :node-id="nodeId"
    v-model="formState.generationPrompt"
  />
  <div v-if="usesAi" class="human-ai-tip">只需描述业务需要收集什么信息，可插入上游变量；无需编写 JSON 或表单格式提示词。</div>
  <div v-if="usesFixed" class="wf-config-section">
    <WfField title="表单标题" required><a-input v-model:value="formState.formTitle" placeholder="例如：确认请假信息" /></WfField>
  </div>
  <PromptEditor v-if="usesFixed" class="wf-config-prompt" title="表单描述" :node-id="nodeId" v-model="formState.prompt" />
  <div v-if="usesFixed" class="wf-config-section">
    <WfField title="表单控件" required>
      <template #tooltip>字段会自动转换成后台所需的完整 JSON Schema，并成为本节点的输出变量。</template>
      <template #operations><a-button :icon="h(PlusOutlined)" size="small" type="primary" @click="addField">添加控件</a-button></template>
      <div v-if="!fields.length" class="human-empty">点击“添加控件”，配置输入框、下拉框或其他表单项</div>
      <div class="human-field-list">
        <div v-for="(field, index) in fields" :key="index" class="human-field-card">
          <div class="human-field-toolbar">
            <span class="human-field-index">{{ index + 1 }}</span>
            <a-button :icon="h(ArrowUpOutlined)" size="small" type="text" :disabled="index === 0" @click="moveField(index, -1)" />
            <a-button :icon="h(ArrowDownOutlined)" size="small" type="text" :disabled="index === fields.length - 1" @click="moveField(index, 1)" />
            <a-button :icon="h(DeleteOutlined)" size="small" type="text" danger @click="removeField(index)" />
          </div>
          <div class="human-field-grid">
            <a-input v-model:value="field.label" placeholder="显示名称" size="small" />
            <a-input v-model:value="field.name" placeholder="字段名（英文）" size="small" />
            <a-select v-model:value="field.type" :options="FIELD_TYPES" size="small" />
            <a-input v-model:value="field.placeholder" placeholder="提示文字（可选）" size="small" />
          </div>
          <div v-if="field.type === 'select'" class="human-options">
            <div v-for="(option, optionIndex) in field.options" :key="optionIndex" class="human-option-row">
              <a-input v-model:value="option.label" placeholder="选项名称" size="small" />
              <a-input v-model:value="option.value" placeholder="选项值" size="small" />
              <a-button size="small" type="text" danger @click="field.options?.splice(optionIndex, 1)">删除</a-button>
            </div>
            <a-button size="small" type="dashed" block @click="addOption(field)">+ 添加下拉选项</a-button>
          </div>
          <a-checkbox v-model:checked="field.required">必填</a-checkbox>
        </div>
      </div>
    </WfField>
  </div>
  <div v-if="usesFixed" class="wf-config-section"><WfField title="操作按钮"><a-input v-model:value="formState.submitButtonText" placeholder="提交" /></WfField></div>
  <div class="wf-config-section">
    <WfField title="呈现方式" required>
      <div class="human-mode-list">
        <button v-for="mode in PRESENTATIONS" :key="mode.value" type="button" class="human-mode-button"
          :class="{ 'is-active': formState.presentationMode === mode.value }" @click="formState.presentationMode = mode.value">
          <span class="human-mode-check">{{ formState.presentationMode === mode.value ? '✓' : '' }}</span><span>{{ mode.label }}</span>
        </button>
      </div>
      <div class="human-help">自动适配会按连接器能力依次使用原生交互、网页表单和纯文本。</div>
    </WfField>
  </div>
  <div v-if="!['CHAT_COMPONENT'].includes(formState.presentationMode)" class="wf-config-section">
    <WfField title="通知渠道">
      <div class="human-channel-fields">
        <a-select v-model:value="selectedChannelKey" allow-clear :options="channelOptions" placeholder="可选：从连接器 Hub 选择支持发送的渠道" />
        <a-select v-if="selectedChannelKey" v-model:value="formState.channelConnectionId" allow-clear :options="connectionOptions" placeholder="选择已配置连接" />
        <a-input v-if="selectedChannelKey" v-model:value="formState.channelTarget" placeholder="接收目标，可填写地址或上游变量" />
      </div>
    </WfField>
  </div>
  <div class="wf-config-section">
    <WfField title="响应时限">
      <a-switch v-model:checked="formState.timeoutEnabled" checked-children="限时" un-checked-children="不限时" />
      <a-input-group v-if="formState.timeoutEnabled" compact class="human-timeout">
        <a-input-number v-model:value="formState.timeoutValue" :min="1" :precision="0" style="width: 65%" />
        <a-select v-model:value="formState.timeoutUnit" :options="TIME_UNITS" style="width: 35%" />
      </a-input-group>
      <div v-if="formState.timeoutEnabled" class="human-help">聊天端将显示倒计时；后台保存值为 {{ formState.timeoutSeconds }} 秒。</div>
    </WfField>
  </div>
</template>

<style scoped>
.human-empty { padding: 18px 10px; text-align: center; font-size: 12px; color: #9ca3af; background: var(--wf-config-surface-hover, #f9fafb); border: 1px dashed #d1d5db; border-radius: 8px; }
.human-field-list { display: flex; flex-direction: column; gap: 10px; }
.human-field-card { padding: 10px; background: var(--wf-config-surface-hover, #f9fafb); border: 1px solid #eef0f3; border-radius: 8px; }
.human-field-toolbar { display: flex; align-items: center; justify-content: flex-end; margin-bottom: 8px; }
.human-field-index { margin-right: auto; width: 22px; height: 22px; line-height: 22px; text-align: center; color: #fff; background: #f59e0b; border-radius: 50%; font-size: 11px; }
.human-field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-bottom: 8px; }
.human-options { display: flex; flex-direction: column; gap: 6px; padding: 8px; margin-bottom: 8px; background: var(--wf-config-surface, #fff); border-radius: 6px; }
.human-option-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 5px; }
.human-timeout { display: flex; margin-top: 8px; }
.human-channel-fields { display: grid; width: 100%; gap: 8px; }
.human-channel-fields :deep(.ant-select), .human-channel-fields :deep(.ant-input) { width: 100%; min-width: 0; }
.human-help { margin-top: 6px; color: #9ca3af; font-size: 11px; }
.human-ai-tip { margin: -2px 16px 14px; padding: 9px 11px; color: #7c3aed; font-size: 12px; background: #f5f3ff; border-radius: 7px; }
.human-mode-list { display: flex; flex-wrap: wrap; gap: 6px; }
.human-mode-button { display: inline-flex; align-items: center; min-width: 0; padding: 5px 9px; color: #64748b; background: var(--wf-config-surface, #fff); border: 1px solid #e2e8f0; border-radius: 7px; cursor: pointer; font-size: 12px; line-height: 18px; transition: all .18s ease; }
.human-mode-button:hover { color: #7c3aed; border-color: #c4b5fd; background: #faf9ff; }
.human-mode-button.is-active { color: #6d28d9; border-color: #8b5cf6; background: #f5f3ff; box-shadow: 0 0 0 2px rgb(139 92 246 / 10%); }
.human-mode-check { flex: 0 0 15px; height: 15px; margin-right: 5px; line-height: 15px; text-align: center; color: #fff; background: #8b5cf6; border-radius: 50%; font-size: 10px; }
.human-mode-button:not(.is-active) .human-mode-check { background: #e2e8f0; }
</style>
