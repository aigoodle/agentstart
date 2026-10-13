<script setup lang="ts">
/**
 * 提示词 block from Dify's orchestrate tab. Textarea + "生成" chip (host wires
 * up prompt-generation via `generate`) + variable insertion helpers.
 */
import { computed, ref } from 'vue';

import type { PromptVariable } from '../types';

interface Props {
  modelValue: string;
  variables?: PromptVariable[];
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  variables: () => [],
  placeholder: '在这里写下你的提示词，输入「{」插入变量、输入「/」插入提示内容块',
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void;
  (e: 'generate'): void;
}>();

const textarea = ref<HTMLTextAreaElement | null>(null);
const showVars = ref(false);

const insertableVars = computed(() =>
  props.variables.filter((v) => v.key.trim().length > 0),
);

function onInput(e: Event) {
  const t = e.target as HTMLTextAreaElement;
  emit('update:modelValue', t.value);
}

function tokenOf(key: string): string {
  // Split concatenation so the Vue template compiler doesn't misread the
  // inner `{{` as a mustache when this helper is called from the template.
  return '{' + '{#' + key + '#}' + '}';
}

function insertVariable(v: PromptVariable) {
  const el = textarea.value;
  const token = tokenOf(v.key);
  if (!el) {
    emit('update:modelValue', (props.modelValue ?? '') + token);
    showVars.value = false;
    return;
  }
  const start = el.selectionStart ?? props.modelValue.length;
  const end = el.selectionEnd ?? start;
  const next = props.modelValue.slice(0, start) + token + props.modelValue.slice(end);
  emit('update:modelValue', next);
  showVars.value = false;
  requestAnimationFrame(() => {
    el.focus();
    const caret = start + token.length;
    el.setSelectionRange(caret, caret);
  });
}
</script>

<template>
  <div class="prompt-card">
    <div class="prompt-head">
      <div class="prompt-title">
        提示词
        <span class="prompt-help" title="用变量占位符 {{#key#}} 引用左侧变量">
          ⓘ
        </span>
      </div>
      <div class="prompt-head-actions">
        <button
          v-if="insertableVars.length > 0"
          type="button"
          class="prompt-chip"
          @click="showVars = !showVars"
        >
          + 变量
        </button>
        <button type="button" class="prompt-chip prompt-chip-primary" @click="emit('generate')">
          ✨ 生成
        </button>
      </div>
    </div>
    <div class="prompt-body">
      <textarea
        ref="textarea"
        :value="modelValue"
        class="prompt-textarea"
        :placeholder="placeholder"
        rows="8"
        @input="onInput"
      />
      <div v-if="showVars" class="prompt-var-pop">
        <div class="prompt-var-title">插入变量</div>
        <button
          v-for="v in insertableVars"
          :key="v.key"
          type="button"
          class="prompt-var-item"
          @click="insertVariable(v)"
        >
          <code>{{ tokenOf(v.key) }}</code>
          <span class="prompt-var-item-label">{{ v.label || v.key }}</span>
        </button>
      </div>
    </div>
    <div class="prompt-footer">
      <span class="prompt-count">{{ modelValue?.length ?? 0 }} 字符</span>
    </div>
  </div>
</template>

<style scoped>
.prompt-card {
  padding: 12px 14px 8px;
  background: #fff;
  border: 1.5px solid #6366f1;
  border-radius: 12px;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.08);
}
.prompt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.prompt-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.prompt-help {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 11px;
  cursor: help;
}
.prompt-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.prompt-chip {
  padding: 3px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  color: #4338ca;
  cursor: pointer;
  transition: background 0.15s;
}
.prompt-chip:hover {
  background: #eef2ff;
}
.prompt-chip-primary {
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  border-color: #c7d2fe;
}
.prompt-body {
  position: relative;
}
.prompt-textarea {
  width: 100%;
  min-height: 180px;
  padding: 8px 4px;
  border: none;
  outline: none;
  resize: vertical;
  font-family: inherit;
  font-size: 13px;
  color: #0f172a;
  background: transparent;
  line-height: 1.6;
}
.prompt-textarea::placeholder {
  color: #94a3b8;
}
.prompt-var-pop {
  position: absolute;
  top: 32px;
  right: 8px;
  min-width: 220px;
  padding: 6px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  z-index: 5;
}
.prompt-var-title {
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}
.prompt-var-item {
  display: flex;
  width: 100%;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.prompt-var-item:hover {
  background: #f1f5f9;
}
.prompt-var-item code {
  padding: 1px 5px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 4px;
  font-size: 11px;
  font-family: ui-monospace, monospace;
}
.prompt-var-item-label {
  color: #475569;
}
.prompt-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px solid #f1f5f9;
}
.prompt-count {
  font-size: 11px;
  color: #94a3b8;
}
</style>
