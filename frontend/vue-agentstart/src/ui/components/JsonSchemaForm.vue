<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { JsonSchema } from '../../connector-hub/types';
import { parseJsonSchema } from '../../connector-hub/types';
import { parseSchemaUi, visibleSchemaFields } from '../../connector-hub/schema-ui';
import VarInsertField from '../../agent-flow/workflow/VarInsertField.vue';
import ModelPickerPopover from '../../provider-hub/components/ModelPickerPopover.vue';

const props = withDefaults(
  defineProps<{
    modelValue?: Record<string, unknown>;
    schema?: string | JsonSchema;
    secretFields?: string[];
    allowAdvanced?: boolean;
    emptyText?: string;
    nodeId?: string;
    uiSchema?: unknown;
    configuredSecretFields?: string[];
  }>(),
  { modelValue: () => ({}), secretFields: () => [], configuredSecretFields: () => [], allowAdvanced: true, emptyText: '该操作没有需要填写的参数' },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void;
}>();

const value = ref<Record<string, unknown>>({ ...props.modelValue });
const advanced = ref(false);
const jsonText = ref('{}');
const jsonError = ref('');

const parsed = computed(() => parseJsonSchema(props.schema));
const ui = computed(() => parseSchemaUi(props.uiSchema));
const fields = computed(() => visibleSchemaFields(parsed.value, ui.value, value.value));
const isOpen = computed(
  () => fields.value.length === 0 && parsed.value.additionalProperties !== false,
);

watch(
  () => props.modelValue,
  (v) => {
    value.value = { ...(v ?? {}) };
    jsonText.value = JSON.stringify(value.value, null, 2);
  },
  { deep: true, immediate: true },
);

function setField(name: string, schema: JsonSchema, raw: unknown) {
  let next = raw;
  if (schema.type === 'number' || schema.type === 'integer') {
    next = typeof raw === 'string' && raw.includes('{{#') ? raw : raw === '' ? undefined : Number(raw);
  }
  if (schema.type === 'boolean') next = Boolean(raw);
  if ((schema.type === 'object' || schema.type === 'array') && typeof raw === 'string') {
    try { next = JSON.parse(raw); } catch { next = raw; }
  }
  value.value = { ...value.value, [name]: next };
  emit('update:modelValue', value.value);
  jsonText.value = JSON.stringify(value.value, null, 2);
}

function applyJson() {
  try {
    const next = JSON.parse(jsonText.value);
    if (!next || Array.isArray(next) || typeof next !== 'object') {
      throw new Error('必须是 JSON 对象');
    }
    value.value = next;
    emit('update:modelValue', next);
    jsonError.value = '';
  } catch (e: any) {
    jsonError.value = e?.message ?? 'JSON 格式错误';
  }
}

function inputType(name: string, schema: JsonSchema) {
  if (props.secretFields.includes(name) || schema.format === 'password' || schema.writeOnly || ui.value.fields?.[name]?.widget === 'password') {
    return 'password';
  }
  if (schema.type === 'number' || schema.type === 'integer') return 'number';
  return 'text';
}

function placeholder(name: string, schema: JsonSchema) {
  if (props.configuredSecretFields.includes(name) && inputType(name, schema) === 'password') {
    return '已配置，留空表示不修改';
  }
  return ui.value.fields?.[name]?.placeholder;
}
</script>

<template>
  <div class="jsf">
    <div v-if="fields.length > 0 && !advanced" class="jsf-fields">
      <label v-for="[name, field] in fields" :key="name" class="jsf-field">
        <span class="jsf-label">
          {{ field.title || name }}
          <b v-if="parsed.required?.includes(name)">*</b>
        </span>
        <small v-if="field.description">{{ field.description }}</small>
        <ModelPickerPopover
          v-if="ui.fields?.[name]?.widget === 'model-selector'"
          :model-value="(value[name] ?? {}) as any"
          :model-type="ui.fields?.[name]?.modelType ?? 'LLM'"
          :show-params="false"
          :auto-load-default="false"
          @update:model-value="setField(name, field, $event)"
        />
        <select
          v-else-if="field.enum"
          :value="value[name]"
          @change="
            setField(name, field, ($event.target as HTMLSelectElement).value)
          "
        >
          <option value="">请选择</option>
          <option v-for="item in field.enum" :key="String(item)" :value="item">
            {{ item }}
          </option>
        </select>
        <input
          v-else-if="field.type === 'boolean'"
          type="checkbox"
          :checked="Boolean(value[name])"
          @change="
            setField(name, field, ($event.target as HTMLInputElement).checked)
          "
        />
        <textarea
          v-else-if="
            ui.fields?.[name]?.widget === 'textarea' || field.format === 'textarea' ||
            field.type === 'object' ||
            field.type === 'array'
          "
          :value="
            typeof value[name] === 'string'
              ? value[name]
              : JSON.stringify(value[name] ?? (field.type === 'array' ? [] : {}), null, 2)
          "
          @change="
            setField(name, field, ($event.target as HTMLTextAreaElement).value)
          "
        />
        <VarInsertField
          v-else-if="['string', 'number', 'integer'].includes(field.type || 'string') && nodeId && ui.fields?.[name]?.allowVariable !== false && inputType(name, field) !== 'password'"
          :model-value="String(value[name] ?? '')"
          :node-id="nodeId"
          :placeholder="ui.fields?.[name]?.placeholder || field.description || `输入${field.title || name}，或选择上游参数`"
          @update:model-value="setField(name, field, $event)"
        />
        <input
          v-else
          :type="inputType(name, field)"
          :placeholder="placeholder(name, field)"
          :min="field.minimum"
          :max="field.maximum"
          :value="value[name] as any"
          @input="
            setField(name, field, ($event.target as HTMLInputElement).value)
          "
        />
      </label>
    </div>
    <div v-else-if="advanced || (isOpen && allowAdvanced)" class="jsf-json-wrap">
      <textarea
        v-model="jsonText"
        class="jsf-json"
        spellcheck="false"
        @blur="applyJson"
      />
      <div v-if="jsonError" class="jsf-error">{{ jsonError }}</div>
    </div>
    <div v-else class="jsf-empty">{{ emptyText }}</div>
    <button
      v-if="allowAdvanced && !isOpen"
      type="button"
      class="jsf-mode"
      @click="advanced = !advanced"
    >
      {{ advanced ? '返回表单模式' : 'JSON 高级模式' }}
    </button>
  </div>
</template>

<style scoped>
.jsf {
  display: grid;
  gap: 12px;
}
.jsf-fields {
  display: grid;
  gap: 14px;
}
.jsf-field {
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
  font-weight: 500;
}
.jsf-label b {
  color: #ef4444;
  font-weight: 600;
}
.jsf-field small {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
}

/* 输入控件（对齐项目统一的输入框样式） */
.jsf-field input:not([type='checkbox']),
.jsf-field select,
.jsf-field textarea,
.jsf-json {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  font-size: 13px;
  color: #111827;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.jsf-field input:not([type='checkbox']):focus,
.jsf-field select:focus,
.jsf-field textarea:focus,
.jsf-json:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.jsf-field input[type='checkbox'] {
  width: 16px;
  height: 16px;
  justify-self: start;
  accent-color: #6366f1;
  cursor: pointer;
}
.jsf-field textarea,
.jsf-json {
  min-height: 110px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.6;
  resize: vertical;
}
.jsf-json {
  min-height: 220px;
}
.jsf-json-wrap {
  display: grid;
  gap: 6px;
}

/* 模式切换 / 错误提示 */
.jsf-mode {
  justify-self: start;
  border: 0;
  background: none;
  padding: 4px 0;
  font-size: 12px;
  color: #4f46e5;
  cursor: pointer;
  transition: color 0.15s ease;
}
.jsf-mode:hover {
  color: #6366f1;
  text-decoration: underline;
}
.jsf-error {
  margin-top: 4px;
  font-size: 12px;
  color: #dc2626;
}
.jsf-empty {
  padding: 12px;
  color: #6b7280;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
}
:global(.dark) .jsf-field {
  color: #d1d5db;
}
:global(.dark) .jsf-field input:not([type='checkbox']),
:global(.dark) .jsf-field select,
:global(.dark) .jsf-field textarea,
:global(.dark) .jsf-json {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .jsf-mode {
  color: #818cf8;
}
:global(.dark) .jsf-error {
  color: #f87171;
}
</style>
