<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import PromptEditorTagPanel from '@/components/PromptEditorTagPanel.vue';
import { useWorkflowStore } from '@/stores/workflow';
import workflow_utils from '@/workflow/utils/workflow_utils';

/**
 * VarInsertField —— 单行文本 + 变量胶囊
 *
 * 视觉/交互对齐 PromptEditor：
 *   - 文本 + `{{#nodeId.field#}}` 混排；已选变量渲染成彩色 pill（带类型图标、× 删除）
 *   - 单行模式：Enter/换行被吞掉；`\n` 不会进入 value
 *   - 右侧 "+" 按钮打开 PromptEditorTagPanel；选中变量后插入到光标位置
 *   - Backspace 光标紧跟 pill 时整颗删除
 *   - v-model 仍是模板字符串（含 `{{#...#}}`），与后端保持一致
 *
 * 场景：Headers / Query 参数 / Authorization API Key Value 等单行值。
 */

interface Props {
  modelValue: string;
  nodeId: string;
  placeholder?: string;
  size?: 'small' | 'middle' | 'large';
  bordered?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  size: 'small',
  bordered: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void;
}>();

const workflowStore = useWorkflowStore();

const editorRef = ref<HTMLDivElement | null>(null);
const insertBtnRef = ref<HTMLElement | null>(null);
const rootRef = ref<HTMLElement | null>(null);

/** 面板状态：mode='insert' 是点 + 按钮打开；contenteditable 内也允许粘贴变量文本 */
const panel = reactive<{
  open: boolean;
  targetElement: HTMLElement | null;
}>({
  open: false,
  targetElement: null,
});

/** 记录展开面板前的 selection，以便面板关闭后把插入点还原回来 */
const savedRange = ref<Range | null>(null);

// ---------- 序列化 / 反序列化 ----------

/** DOM → `{{#...#}}` 混排字符串。忽略 <br> / 多余的空白节点。 */
function serializeToText(): string {
  const root = editorRef.value;
  if (!root) return '';
  let text = '';
  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent ?? '';
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    if (el.classList?.contains('wf-vi-pill')) {
      text += `{{#${el.dataset.key ?? ''}#}}`;
      return;
    }
    if (el.tagName === 'BR') return; // 单行模式：忽略换行
    for (const child of Array.from(el.childNodes)) walk(child);
  };
  for (const child of Array.from(root.childNodes)) walk(child);
  // 去掉 zero-width space（作为光标锚点插入的）
  return text.replace(/​/g, '');
}

/** 模板字符串 → DOM。空串直接清空。 */
function deserializeFromText(text: string) {
  const root = editorRef.value;
  if (!root) return;
  root.innerHTML = '';
  if (!text) return;

  const re = /\{\{#([^}]+)#\}\}/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(text)) !== null) {
    if (match.index > lastIndex) {
      root.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
    }
    root.appendChild(createPill(match[1]));
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    root.appendChild(document.createTextNode(text.slice(lastIndex)));
  }
}

function getPillLabel(selector: string[]): string {
  return workflow_utils.getVariableLabel(selector) || selector.join('.');
}

function getPillType(selector: string[]): string {
  if (!selector || selector.length < 2) return '';
  const [nodeId, ...rest] = selector;
  const node = workflowStore.getNodeById(nodeId);
  if (!node?.data) return '';
  const outputs = workflow_utils.getOutputList(node.data) || [];
  let cur: any = outputs.find((o: any) => o.name === rest[0]);
  for (let i = 1; i < rest.length; i++) {
    if (!cur?.children) return cur?.type ?? '';
    cur = cur.children.find((c: any) => c.name === rest[i]);
  }
  return cur?.type ?? '';
}

function createPill(key: string): HTMLElement {
  const selector = key.split('.');
  const label = getPillLabel(selector);
  const type = getPillType(selector);

  const pill = document.createElement('span');
  pill.className = 'wf-vi-pill';
  pill.contentEditable = 'false';
  pill.dataset.key = key;
  pill.dataset.type = type;
  pill.title = key;

  const icon = document.createElement('span');
  icon.className = 'wf-vi-pill-icon';
  icon.textContent = (type?.[0] ?? '·').toUpperCase();

  const labelEl = document.createElement('span');
  labelEl.className = 'wf-vi-pill-label';
  labelEl.textContent = label;

  const close = document.createElement('span');
  close.className = 'wf-vi-pill-close';
  close.textContent = '×';
  close.title = '删除';

  pill.append(icon, labelEl, close);
  return pill;
}

// ---------- 输入 & 事件 ----------

let emitDebounce: ReturnType<typeof setTimeout> | null = null;
function scheduleEmit() {
  if (emitDebounce) clearTimeout(emitDebounce);
  emitDebounce = setTimeout(() => {
    const val = serializeToText();
    if (val !== props.modelValue) emit('update:modelValue', val);
  }, 30);
}

function onInput() {
  scheduleEmit();
}

/** 单行模式：Enter 直接吞掉，避免插入 <br> 或 <div>。 */
function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault();
    return;
  }

  // Backspace 前面是 pill 时整颗删除
  if (e.key === 'Backspace' && !e.ctrlKey && !e.metaKey) {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && sel.isCollapsed) {
      const range = sel.getRangeAt(0);
      const { startContainer, startOffset } = range;
      if (startContainer.nodeType === Node.TEXT_NODE && startOffset === 0) {
        const prev = (startContainer as Text).previousSibling;
        if (prev instanceof HTMLElement && prev.classList.contains('wf-vi-pill')) {
          e.preventDefault();
          prev.remove();
          scheduleEmit();
          return;
        }
      }
      if (startContainer === editorRef.value && startOffset > 0) {
        const child = editorRef.value!.childNodes[startOffset - 1];
        if (child instanceof HTMLElement && child.classList.contains('wf-vi-pill')) {
          e.preventDefault();
          child.remove();
          scheduleEmit();
          return;
        }
      }
    }
  }
}

/** 点击 pill 上的 × 删除该 pill */
function onEditorClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (target.classList?.contains('wf-vi-pill-close')) {
    const pill = target.closest('.wf-vi-pill');
    if (pill) {
      pill.remove();
      scheduleEmit();
    }
    e.preventDefault();
  }
}

/** 只允许粘贴纯文本，避免带入 rich HTML */
function onPaste(e: ClipboardEvent) {
  e.preventDefault();
  const text = e.clipboardData?.getData('text/plain') ?? '';
  // 单行：去掉换行
  document.execCommand('insertText', false, text.replace(/[\r\n]+/g, ' '));
}

function saveSelection() {
  const sel = window.getSelection();
  if (
    sel &&
    sel.rangeCount > 0 &&
    editorRef.value?.contains(sel.getRangeAt(0).startContainer)
  ) {
    savedRange.value = sel.getRangeAt(0).cloneRange();
  }
}

function restoreSelection() {
  if (!savedRange.value || !editorRef.value) return;
  const sel = window.getSelection();
  sel?.removeAllRanges();
  sel?.addRange(savedRange.value);
  editorRef.value.focus();
}

// ---------- 变量插入面板 ----------

function toggleInsertPanel(e: MouseEvent) {
  e.preventDefault();
  if (panel.open) {
    closePanel();
    return;
  }
  saveSelection();
  panel.targetElement = insertBtnRef.value;
  panel.open = true;
}

function closePanel() {
  panel.open = false;
  panel.targetElement = null;
}

function insertPillAtSelection(pill: HTMLElement) {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || !editorRef.value?.contains(sel.getRangeAt(0).startContainer)) {
    editorRef.value?.appendChild(pill);
    return;
  }
  const range = sel.getRangeAt(0);
  range.deleteContents();
  range.insertNode(pill);
  // 光标塌到 pill 右侧：插一个 zero-width space 作为落脚点
  const after = document.createTextNode('​');
  pill.parentNode?.insertBefore(after, pill.nextSibling);
  const newRange = document.createRange();
  newRange.setStart(after, 1);
  newRange.setEnd(after, 1);
  sel.removeAllRanges();
  sel.addRange(newRange);
}

function onPanelSelect(variableSelector: string[]) {
  restoreSelection();
  const pill = createPill(variableSelector.join('.'));
  insertPillAtSelection(pill);
  closePanel();
  scheduleEmit();
  editorRef.value?.focus();
}

// ---------- 生命周期 ----------

function onGlobalMouseDown(e: MouseEvent) {
  if (!panel.open) return;
  const t = e.target as Node;
  if (editorRef.value?.contains(t)) return;
  if (insertBtnRef.value?.contains(t)) return;
  const panelEl = document.querySelector('.wf-tag-panel');
  if (panelEl?.contains(t)) return;
  closePanel();
}

onMounted(() => {
  deserializeFromText(props.modelValue ?? '');
  document.addEventListener('mousedown', onGlobalMouseDown);
});

onUnmounted(() => {
  document.removeEventListener('mousedown', onGlobalMouseDown);
  if (emitDebounce) clearTimeout(emitDebounce);
});

// 外部灌值：只有真正不同时才重刷 DOM，避免打断光标
watch(
  () => props.modelValue,
  (val) => {
    if (!editorRef.value) return;
    if ((val ?? '') === serializeToText()) return;
    deserializeFromText(val ?? '');
  },
);
</script>

<template>
  <div
    ref="rootRef"
    class="wf-vi"
    :class="[
      `wf-vi-${size}`,
      { 'wf-vi-borderless': !bordered },
    ]"
  >
    <div
      ref="editorRef"
      class="wf-vi-input"
      contenteditable="true"
      :data-placeholder="placeholder"
      @input="onInput"
      @keydown="onKeyDown"
      @click="onEditorClick"
      @paste="onPaste"
      @mouseup="saveSelection"
      @keyup="saveSelection"
      @blur="saveSelection"
    ></div>

    <a
      ref="insertBtnRef"
      class="wf-vi-insert-btn"
      title="插入变量"
      tabindex="-1"
      @mousedown.prevent
      @click="toggleInsertPanel"
    >
      <svg viewBox="0 0 24 24" width="12" height="12">
        <path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z" />
      </svg>
    </a>

    <PromptEditorTagPanel
      :show="panel.open"
      :node-id="nodeId"
      :target-element="panel.targetElement"
      @close="closePanel"
      @select="onPanelSelect"
    />
  </div>
</template>

<style scoped>
.wf-vi {
  display: flex;
  align-items: stretch;
  width: 100%;
  min-height: 24px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.wf-vi:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
}

.wf-vi-borderless {
  border-color: transparent;
  background: transparent;
}

.wf-vi-borderless:focus-within {
  border-color: transparent;
  box-shadow: none;
}

.wf-vi-small {
  min-height: 24px;
  font-size: 13px;
}

.wf-vi-middle {
  min-height: 32px;
  font-size: 14px;
}

.wf-vi-large {
  min-height: 40px;
  font-size: 15px;
}

/* 单行 input：pill 与文本内联，纵向居中；超长时横向滚动 */
.wf-vi-input {
  flex: 1;
  min-width: 0;
  padding: 2px 6px;
  line-height: 1.6;
  color: #1f2937;
  outline: none;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;
  word-break: keep-all;
}

.wf-vi-input:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  pointer-events: none;
}

.wf-vi-insert-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 22px;
  color: #9ca3af;
  background: transparent;
  cursor: pointer;
  transition: color 0.12s, background 0.12s;
}

.wf-vi-insert-btn:hover {
  color: #4338ca;
  background: #eef2ff;
}
</style>

<style>
/* pill 全局样式（同 PromptEditor 的做法）：因为 pill 是 createElement 挂进 contenteditable
 * 的裸 DOM 节点，<style scoped> 的 data-v-xxx 不会自动加到它们上，所以样式必须放到
 * 非 scoped 块里。类名前缀 wf-vi- 与 wf-pe- 隔离，互不影响。 */
.wf-vi-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0 3px 0 2px;
  margin: 0 1px;
  font-size: 12px;
  line-height: 1.4;
  color: #1f2937;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 4px;
  cursor: default;
  user-select: none;
  vertical-align: baseline;
  transition: background 0.15s, border-color 0.15s;
}

.wf-vi-pill:hover {
  background: #e0e7ff;
  border-color: #a5b4fc;
}

.wf-vi-pill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  font-size: 8px;
  font-weight: 700;
  color: #ffffff;
  background: #6366f1;
  border-radius: 2px;
  text-transform: uppercase;
}

.wf-vi-pill[data-type='string'] .wf-vi-pill-icon { background: #3b82f6; }
.wf-vi-pill[data-type='number'] .wf-vi-pill-icon { background: #f59e0b; }
.wf-vi-pill[data-type='boolean'] .wf-vi-pill-icon { background: #10b981; }
.wf-vi-pill[data-type='array'] .wf-vi-pill-icon { background: #a855f7; }
.wf-vi-pill[data-type='object'] .wf-vi-pill-icon { background: #6b7280; }
.wf-vi-pill[data-type='file'] .wf-vi-pill-icon { background: #ef4444; }

.wf-vi-pill-label {
  color: #4338ca;
  font-weight: 500;
}

.wf-vi-pill-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  font-size: 12px;
  line-height: 1;
  color: #6366f1;
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-vi-pill-close:hover {
  background: #c7d2fe;
}
</style>
