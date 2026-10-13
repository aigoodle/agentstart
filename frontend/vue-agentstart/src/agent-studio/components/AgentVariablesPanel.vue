<script setup lang="ts">
/**
 * 变量 block from Dify's orchestrate tab. Emits `add` and mutates rows in
 * place — the host owns the array (v-model:list).
 */
import type { PromptVariable, VariableType } from '../types';

interface Props {
  list: PromptVariable[];
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:list', v: PromptVariable[]): void;
}>();

const TYPE_OPTIONS: Array<{ label: string; value: VariableType }> = [
  { label: '文本', value: 'text' },
  { label: '段落', value: 'paragraph' },
  { label: '下拉', value: 'select' },
];

// Kept in script so the template's mustache parser doesn't try to eat the
// inner `{{` of the sample token.
const SAMPLE_TOKEN = '{' + '{#key#}' + '}';

function addRow(rows: PromptVariable[]) {
  emit('update:list', [
    ...rows,
    { key: '', label: '', type: 'text', required: false },
  ]);
}

function updateRow(rows: PromptVariable[], idx: number, patch: Partial<PromptVariable>) {
  const next = rows.slice();
  next[idx] = { ...next[idx]!, ...patch };
  emit('update:list', next);
}

function removeRow(rows: PromptVariable[], idx: number) {
  emit(
    'update:list',
    rows.filter((_, i) => i !== idx),
  );
}
</script>

<template>
  <div class="var-card">
    <div class="var-head">
      <div class="var-title">
        变量
        <span class="var-help" title="变量能让用户在表单里传入提示词，把它们插入到提示词中输入">
          ⓘ
        </span>
      </div>
      <button type="button" class="var-add" @click="addRow(list)">+ 添加</button>
    </div>
    <div v-if="list.length === 0" class="var-empty">
      变量能让用户在表单中输入提示词或直接引用。你可以试试在提示词中输入
      <code>{{ SAMPLE_TOKEN }}</code>。
    </div>
    <div v-else class="var-list">
      <div v-for="(row, i) in list" :key="i" class="var-row">
        <input
          class="var-input var-input-key"
          :value="row.key"
          placeholder="key"
          @input="(e) => updateRow(list, i, { key: (e.target as HTMLInputElement).value })"
        />
        <input
          class="var-input"
          :value="row.label"
          placeholder="展示名"
          @input="(e) => updateRow(list, i, { label: (e.target as HTMLInputElement).value })"
        />
        <select
          class="var-input var-input-type"
          :value="row.type"
          @change="(e) => updateRow(list, i, { type: (e.target as HTMLSelectElement).value as VariableType })"
        >
          <option v-for="o in TYPE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </option>
        </select>
        <label class="var-required">
          <input
            type="checkbox"
            :checked="row.required ?? false"
            @change="(e) => updateRow(list, i, { required: (e.target as HTMLInputElement).checked })"
          />
          必填
        </label>
        <button type="button" class="var-remove" @click="removeRow(list, i)">
          ×
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.var-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.var-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.var-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.var-help {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 11px;
  cursor: help;
}
.var-add {
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #4338ca;
  cursor: pointer;
}
.var-add:hover {
  background: #eef2ff;
}
.var-empty {
  padding: 4px 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.var-empty code {
  padding: 1px 5px;
  background: #f1f5f9;
  border-radius: 4px;
  font-family: ui-monospace, monospace;
}
.var-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.var-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.var-input {
  flex: 1;
  min-width: 0;
  padding: 5px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #0f172a;
}
.var-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15);
}
.var-input-key {
  flex: 0 0 100px;
  font-family: ui-monospace, monospace;
}
.var-input-type {
  flex: 0 0 72px;
}
.var-required {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}
.var-remove {
  padding: 0 6px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}
.var-remove:hover {
  background: #fef2f2;
  color: #dc2626;
}
</style>
