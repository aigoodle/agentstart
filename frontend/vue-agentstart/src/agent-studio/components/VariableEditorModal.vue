<script setup lang="ts">
/**
 * VariableEditorModal — 应用配置的「变量」新增/编辑弹窗。
 *
 * 视觉上对齐 AppDesignDrawer 的自定义样式（不引入 ant-design），交互上对齐
 * 流程设计里 StartNodeCard 用的 VariableModal：一次调用 `open(item?)` 就把
 * 初值同步写进内部表单，避免通过 prop 中转导致的时序问题。
 *
 * 用法：
 *   const editorRef = ref<InstanceType<typeof VariableEditorModal> | null>(null);
 *   editorRef.value?.open(item);          // 编辑
 *   editorRef.value?.open();               // 新增
 *
 * `@submit` 事件返回一个完整的 `AgentVariable` 对象，宿主自行 push / replace
 * 到自己的数组里。
 */
import { reactive, ref } from 'vue';

import { Button, Modal } from '../../ui';
import type { AgentVariable, AgentVariableType } from '../types';

const TYPES: { value: AgentVariableType; label: string; hint: string }[] = [
  { value: 'string', label: 'String', hint: '任意文本' },
  { value: 'number', label: 'Number', hint: '数值' },
  { value: 'boolean', label: 'Boolean', hint: '布尔' },
  { value: 'array', label: 'Array', hint: '数组' },
  { value: 'object', label: 'Object', hint: '对象' },
  { value: 'file', label: 'File', hint: '文件' },
];

const emit = defineEmits<{
  (e: 'submit', v: AgentVariable): void;
  (e: 'cancel'): void;
}>();

const open = ref(false);
const editing = ref(false);

const form = reactive<AgentVariable>({
  id: undefined,
  name: '',
  label: '',
  type: 'string',
  required: false,
  description: '',
  defaultValue: '',
});

const errors = reactive<{ name?: string }>({});

function resetForm(src?: Partial<AgentVariable>) {
  form.id = src?.id;
  form.name = src?.name ?? '';
  form.label = src?.label ?? '';
  form.type = (src?.type as AgentVariableType) ?? 'string';
  form.required = !!src?.required;
  form.description = src?.description ?? '';
  form.defaultValue = src?.defaultValue ?? '';
  errors.name = undefined;
}

function openModal(item?: Partial<AgentVariable>) {
  resetForm(item);
  editing.value = !!item?.id;
  open.value = true;
}

function closeModal() {
  open.value = false;
  emit('cancel');
}

/**
 * 变量名验证 —— 只允许字母/数字/下划线，且不能以数字开头，避免后续在
 * 提示词 `{{#user.xxx#}}` 模板里踩变量插值的语法坑。
 */
function validateName(name: string): string | null {
  if (!name.trim()) return '变量名不能为空';
  if (!/^[A-Za-z_]\w*$/.test(name.trim())) {
    return '仅允许字母/数字/下划线，且不能以数字开头';
  }
  return null;
}

function submit() {
  const err = validateName(form.name);
  if (err) {
    errors.name = err;
    return;
  }
  const payload: AgentVariable = {
    id: form.id ?? Date.now(),
    name: form.name.trim(),
    label: (form.label ?? '').trim() || form.name.trim(),
    type: form.type,
    required: form.required,
    description: (form.description ?? '').trim(),
    defaultValue: form.defaultValue,
  };
  emit('submit', payload);
  open.value = false;
}

defineExpose({ open: openModal, close: closeModal });
</script>

<template>
  <Modal
    :open="open"
    class="vem-panel"
    centered
    :width="500"
    :title="editing ? '编辑变量' : '新增变量'"
    @cancel="closeModal"
  >
        <div class="vem-body">
          <div class="vem-field">
            <label class="vem-label">
              变量名
              <span class="vem-required">*</span>
            </label>
            <input
              v-model="form.name"
              class="vem-input"
              :class="{ 'vem-input-error': errors.name }"
              placeholder="例如 query"
              @input="errors.name = undefined"
            />
            <div class="vem-hint">
              用于在提示词中通过
              <code v-pre>{{ variable }}</code>
              引用
            </div>
            <div v-if="errors.name" class="vem-error">{{ errors.name }}</div>
          </div>

          <div class="vem-field">
            <label class="vem-label">显示名</label>
            <input
              v-model="form.label"
              class="vem-input"
              placeholder="用户看到的中文标题（留空则用变量名）"
            />
          </div>

          <div class="vem-field">
            <label class="vem-label">类型</label>
            <div class="vem-type-grid">
              <button
                v-for="t in TYPES"
                :key="t.value"
                type="button"
                :class="['vem-type-chip', { on: form.type === t.value }]"
                @click="form.type = t.value"
              >
                <span class="vem-type-name">{{ t.label }}</span>
                <span class="vem-type-hint">{{ t.hint }}</span>
              </button>
            </div>
          </div>

          <div class="vem-field">
            <label class="vem-label">默认值</label>
            <input
              v-model="form.defaultValue"
              class="vem-input"
              placeholder="留空表示无默认值"
            />
          </div>

          <div class="vem-field">
            <label class="vem-label">描述</label>
            <textarea
              v-model="form.description"
              class="vem-textarea"
              placeholder="给填写者的说明"
              rows="3"
            />
          </div>

          <label class="vem-switch-row">
            <input v-model="form.required" type="checkbox" />
            <span class="vem-switch-slider" />
            <span class="vem-switch-text">
              <span class="vem-switch-title">必填</span>
              <span class="vem-switch-desc">
                运行时未填写将阻止提交
              </span>
            </span>
          </label>
        </div>

        <template #footer>
        <div class="vem-footer">
          <Button @click="closeModal">
            取消
          </Button>
          <Button type="primary" @click="submit">
            {{ editing ? '保存' : '添加' }}
          </Button>
        </div>
        </template>
  </Modal>
</template>

<style scoped>
.vem-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: vem-fade 0.14s ease-out;
}
@keyframes vem-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.vem-panel { max-height: calc(100vh - 80px); }
@keyframes vem-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.vem-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}
.vem-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.vem-close {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
}
.vem-close:hover {
  background: #f1f5f9;
  color: #475569;
}

.vem-body {
  flex: 1;
  padding: 16px 18px 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.vem-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vem-label {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}
.vem-required {
  color: #ef4444;
  margin-left: 2px;
}
.vem-input,
.vem-textarea {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: #0f172a;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-family: inherit;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.vem-textarea {
  resize: vertical;
  line-height: 1.55;
}
.vem-input:focus,
.vem-textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.vem-input-error {
  border-color: #ef4444;
}
.vem-input-error:focus {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
}
.vem-hint {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.5;
}
.vem-hint code {
  padding: 1px 5px;
  background: #f1f5f9;
  color: #4338ca;
  border-radius: 4px;
  font-size: 11px;
}
.vem-error {
  font-size: 11px;
  color: #dc2626;
}

.vem-type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}
.vem-type-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}
.vem-type-chip:hover {
  border-color: #c7d2fe;
  background: #f8fafc;
}
.vem-type-chip.on {
  background: #eef2ff;
  border-color: #6366f1;
}
.vem-type-name {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.vem-type-chip.on .vem-type-name {
  color: #4338ca;
}
.vem-type-hint {
  font-size: 10px;
  color: #94a3b8;
}

/* Custom switch — repurpose the drawer's dr-switch look for a full row. */
.vem-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
}
.vem-switch-row input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.vem-switch-slider {
  position: relative;
  width: 32px;
  height: 18px;
  background: #cbd5e1;
  border-radius: 999px;
  transition: background 0.15s;
  flex-shrink: 0;
}
.vem-switch-slider::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.15s;
}
.vem-switch-row input:checked + .vem-switch-slider {
  background: #6366f1;
}
.vem-switch-row input:checked + .vem-switch-slider::before {
  transform: translateX(14px);
}
.vem-switch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.vem-switch-title {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}
.vem-switch-desc {
  font-size: 11px;
  color: #94a3b8;
}

.vem-footer {
  padding: 12px 18px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.vem-btn {
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s,
    box-shadow 0.15s;
}
.vem-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.25);
}
.vem-btn-ghost {
  background: transparent;
  color: #64748b;
}
.vem-btn-ghost:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.vem-btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  box-shadow:
    0 1px 2px rgba(79, 70, 229, 0.24),
    0 1px 1px rgba(15, 23, 42, 0.06);
}
.vem-btn-primary:hover {
  box-shadow:
    0 3px 8px rgba(79, 70, 229, 0.32),
    0 1px 2px rgba(15, 23, 42, 0.08);
}
</style>
