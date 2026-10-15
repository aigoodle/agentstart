<script setup lang="ts">
import UiNotice from '../../ui/components/InlineNotice.vue';
import { ShadcnBoolean as AsShadcnBoolean } from '../../ui/components/ShadcnBoolean';

import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';
import { Textarea as AsShadcnTextarea } from '../../ui/shadcn/textarea';

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
            <AsShadcnInput
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
            <UiNotice type="error" v-if="errors.name" class="vem-error">{{ errors.name }}</UiNotice>
          </div>

          <div class="vem-field">
            <label class="vem-label">显示名</label>
            <AsShadcnInput
              v-model="form.label"
              class="vem-input"
              placeholder="用户看到的中文标题（留空则用变量名）"
            />
          </div>

          <div class="vem-field">
            <label class="vem-label">类型</label>
            <div class="vem-type-grid">
              <AsShadcnButton variant="ghost"
                v-for="t in TYPES"
                :key="t.value"
                type="button"
                :class="['vem-type-chip', { on: form.type === t.value }]"
                @click="form.type = t.value"
              >
                <span class="vem-type-name">{{ t.label }}</span>
                <span class="vem-type-hint">{{ t.hint }}</span>
              </AsShadcnButton>
            </div>
          </div>

          <div class="vem-field">
            <label class="vem-label">默认值</label>
            <AsShadcnInput
              v-model="form.defaultValue"
              class="vem-input"
              placeholder="留空表示无默认值"
            />
          </div>

          <div class="vem-field">
            <label class="vem-label">描述</label>
            <AsShadcnTextarea
              v-model="form.description"
              class="vem-textarea"
              placeholder="给填写者的说明"
              rows="3"
            />
          </div>

          <label class="vem-switch-row">
            <AsShadcnBoolean appearance="switch" v-model="form.required" type="checkbox" />

            <span class="vem-switch-text">
              <span class="vem-switch-title">必填</span>
              <span class="vem-switch-desc">
                运行时未填写将阻止提交
              </span>
            </span>
          </label>
        </div>

        <template #footer>
          <Button @click="closeModal">
            取消
          </Button>
          <Button type="primary" @click="submit">
            {{ editing ? '保存' : '添加' }}
          </Button>
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
  border-bottom: 1px solid var(--border);
}
.vem-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--foreground);
}
.vem-close {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--muted-foreground);
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
}
.vem-close:hover {
  background: var(--muted);
  color: var(--muted-foreground);
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
  color: var(--foreground);
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
  color: var(--foreground);
  background: var(--background);
  border: 1px solid var(--border);
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
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}
.vem-input-error {
  border-color: #ef4444;
}
.vem-input-error:focus {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
}
.vem-hint {
  font-size: 11px;
  color: var(--muted-foreground);
  line-height: 1.5;
}
.vem-hint code {
  padding: 1px 5px;
  background: var(--muted);
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
  background: var(--background);
  border: 1px solid var(--border);
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
  background: var(--muted);
}
.vem-type-chip.on {
  background: color-mix(in srgb, var(--primary) 10%, var(--background));
  border-color: var(--primary);
}
.vem-type-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--foreground);
}
.vem-type-chip.on .vem-type-name {
  color: var(--primary);
}
.vem-type-hint {
  font-size: 10px;
  color: var(--muted-foreground);
}

/* Custom switch — repurpose the drawer's dr-switch look for a full row. */
.vem-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--muted);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
}
.vem-switch-row input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.vem-switch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.vem-switch-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--foreground);
}
.vem-switch-desc {
  font-size: 11px;
  color: var(--muted-foreground);
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
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 25%, transparent);
}
.vem-btn-ghost {
  background: transparent;
  color: var(--muted-foreground);
}
.vem-btn-ghost:hover {
  background: var(--muted);
  color: var(--foreground);
}
.vem-btn-primary {
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary) 100%);
  color: var(--primary-foreground);
  box-shadow:
    0 1px 2px color-mix(in srgb, var(--primary) 24%, transparent),
    0 1px 1px rgba(15, 23, 42, 0.06);
}
.vem-btn-primary:hover {
  box-shadow:
    0 3px 8px color-mix(in srgb, var(--primary) 32%, transparent),
    0 1px 2px rgba(15, 23, 42, 0.08);
}
</style>
