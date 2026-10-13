<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import {
  CopyOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue';
import { message } from '../../ui';

import { useWorkflowStore } from '@/stores/workflow';
import workflow_utils from '@/workflow/utils/workflow_utils';

import PromptEditorTagPanel from './PromptEditorTagPanel.vue';

/**
 * PromptEditor —— Dify 风格的提示词编辑器
 *
 * 特性
 *   - 变量以彩色 pill 显示，可点删除
 *   - `/` 或 `{{` 触发上游变量选择
 *   - 键盘：↑↓ 选择、Enter 插入、Esc 关闭
 *   - Backspace 前是 pill 时直接删 pill
 *   - 复制按钮、字符计数、全屏（Esc 退出）
 *   - v-model 用 `{{#nodeId.field#}}` 模板串（与后端对齐）
 *
 * 公开 API（defineExpose）
 *   - focus() / blur()
 *   - insertVariable(selector: string[]) 从外部插入一个变量
 *   - getContent() / setContent(text)
 */

interface Props {
  modelValue?: string;
  title?: string;
  nodeId?: string;
  placeholder?: string;
  minHeight?: string;
  maxHeight?: string;
  readonly?: boolean;
  showToolbar?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  title: '提示词',
  nodeId: '',
  placeholder: '输入 / 或 {{ 引用上游变量',
  minHeight: '120px',
  maxHeight: '360px',
  readonly: false,
  showToolbar: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void;
}>();

const workflowStore = useWorkflowStore();

/** ---------- refs ---------- */
const editorRef = ref<HTMLDivElement | null>(null);
const insertBtnRef = ref<HTMLElement | null>(null);
const isFullscreen = ref(false);

/** 弹层状态：mode='insert' → 点击插入按钮打开；mode='trigger' → / 或 {{ 触发 */
const panel = reactive<{
  open: boolean;
  mode: 'insert' | 'trigger';
  targetElement: HTMLElement | null;
  search: string;
  selectedIndex: number;
  triggerRange: {
    node: Text | null;
    start: number;
    end: number;
  } | null;
}>({
  open: false,
  mode: 'insert',
  targetElement: null,
  search: '',
  selectedIndex: 0,
  triggerRange: null,
});

const savedRange = ref<null | Range>(null);

/** ---------- content <-> DOM 同步 ---------- */

/** 序列化编辑器 DOM 为 {{#nodeId.field#}} 模板字符串 */
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
    if (el.classList?.contains('wf-pe-pill')) {
      const key = el.dataset.key ?? '';
      text += `{{#${key}#}}`;
      return;
    }
    // <br> 或块级换行
    if (el.tagName === 'BR') {
      text += '\n';
      return;
    }
    for (const child of Array.from(el.childNodes)) walk(child);
    // 块级元素后补换行（div/p）
    if (
      (el.tagName === 'DIV' || el.tagName === 'P') &&
      el !== root &&
      !text.endsWith('\n')
    ) {
      text += '\n';
    }
  };
  for (const child of Array.from(root.childNodes)) walk(child);
  return text.replace(/[\u200B\uFEFF]/g, '').replace(/\n+$/g, '');
}

/** 把模板字符串反序列化到 DOM */
function deserializeFromText(text: string) {
  const root = editorRef.value;
  if (!root) return;
  root.innerHTML = '';
  if (!text) return;

  const frag = document.createDocumentFragment();
  const re = /\{\{#([^}]+)#\}\}/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(text)) !== null) {
    if (match.index > lastIndex) {
      appendTextWithNewlines(frag, text.slice(lastIndex, match.index));
    }
    frag.appendChild(createPill(match[1]));
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    appendTextWithNewlines(frag, text.slice(lastIndex));
  }
  root.appendChild(frag);
}

/** 把纯文本按 \n 拆开插入，每个 \n 变一个 <br> */
function appendTextWithNewlines(parent: Node, str: string) {
  const parts = str.split('\n');
  parts.forEach((p, i) => {
    if (p) parent.appendChild(document.createTextNode(p));
    if (i < parts.length - 1) parent.appendChild(document.createElement('br'));
  });
}

/** 上游变量 selector -> 显示 label */
function getPillLabel(selector: string[]): string {
  return workflow_utils.getVariableLabel(selector) || selector.join('.');
}

/** 上游变量 selector -> 类型（用于 pill 上色） */
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

/** 构造一个 pill 元素 */
function createPill(key: string): HTMLElement {
  const selector = key.split('.');
  const label = getPillLabel(selector);
  const type = getPillType(selector);

  const pill = document.createElement('span');
  pill.className = 'wf-pe-pill';
  pill.contentEditable = 'false';
  pill.dataset.key = key;
  pill.dataset.type = type;
  pill.title = key;

  const icon = document.createElement('span');
  icon.className = 'wf-pe-pill-icon';
  icon.textContent = (type?.[0] ?? '·').toUpperCase();

  const labelEl = document.createElement('span');
  labelEl.className = 'wf-pe-pill-label';
  labelEl.textContent = label;

  const close = document.createElement('span');
  close.className = 'wf-pe-pill-close';
  close.textContent = '×';
  close.title = '删除';

  pill.append(icon, labelEl, close);
  return pill;
}

/** ---------- 输入 / 事件 ---------- */

let inputDebounce: ReturnType<typeof setTimeout> | null = null;
function scheduleEmit() {
  if (inputDebounce) clearTimeout(inputDebounce);
  inputDebounce = setTimeout(() => {
    const val = serializeToText();
    if (val !== props.modelValue) emit('update:modelValue', val);
  }, 50);
}

function onInput() {
  scheduleEmit();
  detectTrigger();
}

function onKeyDown(e: KeyboardEvent) {
  // 面板打开时的键盘导航
  if (panel.open) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      panel.selectedIndex++;
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      panel.selectedIndex = Math.max(0, panel.selectedIndex - 1);
      return;
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      closePanel();
      return;
    }
    // Enter 由 TagPanel 内部 emit('select') 处理
  }

  // Backspace 删除前面的 pill
  if (e.key === 'Backspace' && !e.ctrlKey && !e.metaKey) {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0 && sel.isCollapsed) {
      const range = sel.getRangeAt(0);
      const { startContainer, startOffset } = range;
      if (startContainer.nodeType === Node.TEXT_NODE && startOffset === 0) {
        const prev = (startContainer as Text).previousSibling;
        if (
          prev instanceof HTMLElement &&
          prev.classList.contains('wf-pe-pill')
        ) {
          e.preventDefault();
          prev.remove();
          scheduleEmit();
          return;
        }
      }
      if (startContainer === editorRef.value && startOffset > 0) {
        const child = editorRef.value.childNodes[startOffset - 1];
        if (
          child instanceof HTMLElement &&
          child.classList.contains('wf-pe-pill')
        ) {
          e.preventDefault();
          child.remove();
          scheduleEmit();
          return;
        }
      }
    }
  }

  // Fullscreen Esc
  if (e.key === 'Escape' && isFullscreen.value && !panel.open) {
    e.preventDefault();
    isFullscreen.value = false;
  }
}

function onEditorClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (target.classList?.contains('wf-pe-pill-close')) {
    const pill = target.closest('.wf-pe-pill');
    if (pill) {
      pill.remove();
      scheduleEmit();
    }
    e.preventDefault();
  }
}

function onPaste(e: ClipboardEvent) {
  // 只粘贴纯文本，去除格式
  e.preventDefault();
  const text = e.clipboardData?.getData('text/plain') ?? '';
  document.execCommand('insertText', false, text);
}

/** ---------- 触发检测：/ 或 {{ ---------- */
function detectTrigger() {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || !sel.isCollapsed) return;
  const range = sel.getRangeAt(0);
  const node = range.startContainer;
  if (node.nodeType !== Node.TEXT_NODE) return;
  const text = node.textContent ?? '';
  const offset = range.startOffset;

  // 检测 `/` 后的搜索文本（到当前光标）
  const beforeCursor = text.slice(0, offset);
  const slashIdx = beforeCursor.lastIndexOf('/');
  const braceIdx = beforeCursor.lastIndexOf('{{');

  // 优先取更靠近光标的触发字符
  let triggerIdx = -1;
  let triggerLen = 0;
  if (slashIdx > braceIdx) {
    triggerIdx = slashIdx;
    triggerLen = 1;
  } else if (braceIdx > -1) {
    triggerIdx = braceIdx;
    triggerLen = 2;
  }
  if (triggerIdx === -1) {
    if (panel.open && panel.mode === 'trigger') closePanel();
    return;
  }

  // 触发字符后到光标之间必须都是 word 字符（否则视为无效）
  const searchRaw = beforeCursor.slice(triggerIdx + triggerLen);
  if (/\s/.test(searchRaw)) {
    if (panel.open && panel.mode === 'trigger') closePanel();
    return;
  }

  // 触发字符前必须是空白/句首（避免 URL 中的斜杠误触发）
  const charBefore = triggerIdx > 0 ? beforeCursor[triggerIdx - 1] : '';
  if (charBefore && !/[\s\n]/.test(charBefore)) {
    if (panel.open && panel.mode === 'trigger') closePanel();
    return;
  }

  panel.triggerRange = {
    node: node as Text,
    start: triggerIdx,
    end: offset,
  };
  panel.search = searchRaw;
  panel.selectedIndex = 0;
  panel.mode = 'trigger';
  panel.targetElement = null;
  panel.open = true;
  updateAnchor(range);
}

/** 用光标位置作为面板锚点：造一个 0×0 的临时元素 */
function updateAnchor(range: Range) {
  const rect = range.getBoundingClientRect();
  panel.targetElement = {
    getBoundingClientRect: () =>
      new DOMRect(rect.left, rect.top, 0, rect.height || 18),
  } as unknown as HTMLElement;
}

/** ---------- 变量插入 ---------- */

function insertPillAtSelection(pill: HTMLElement) {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    editorRef.value?.appendChild(pill);
    return;
  }
  const range = sel.getRangeAt(0);
  if (!editorRef.value?.contains(range.startContainer)) {
    editorRef.value?.appendChild(pill);
    return;
  }
  range.deleteContents();
  range.insertNode(pill);
  // 光标移到 pill 之后
  const after = document.createTextNode('​'); // zero-width space 避免光标塌陷
  pill.parentNode?.insertBefore(after, pill.nextSibling);
  const newRange = document.createRange();
  newRange.setStart(after, 1);
  newRange.setEnd(after, 1);
  sel.removeAllRanges();
  sel.addRange(newRange);
}

/** TagPanel emit('select', selector, tag, node) 的处理 */
function onPanelSelect(variableSelector: string[]) {
  // trigger 模式：删除触发符 + 已经输入的搜索文本
  if (panel.mode === 'trigger' && panel.triggerRange?.node) {
    const { node, start, end } = panel.triggerRange;
    const text = node.textContent ?? '';
    node.textContent = text.slice(0, start) + text.slice(end);
    const range = document.createRange();
    range.setStart(node, start);
    range.setEnd(node, start);
    const sel = window.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(range);
  } else {
    // insert 模式：恢复之前保存的 selection
    restoreSelection();
  }
  const pill = createPill(variableSelector.join('.'));
  insertPillAtSelection(pill);
  closePanel();
  scheduleEmit();
  editorRef.value?.focus();
}

/** ---------- 面板打开/关闭 ---------- */

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

function toggleInsertPanel() {
  if (panel.open && panel.mode === 'insert') {
    closePanel();
    return;
  }
  saveSelection();
  panel.mode = 'insert';
  panel.search = '';
  panel.selectedIndex = 0;
  panel.triggerRange = null;
  panel.targetElement = insertBtnRef.value;
  panel.open = true;
}

function closePanel() {
  panel.open = false;
  panel.search = '';
  panel.selectedIndex = 0;
  panel.triggerRange = null;
  panel.targetElement = null;
}

/** ---------- 公开工具 ---------- */

const charCount = computed(() => (props.modelValue || '').length);

async function copyToClipboard() {
  try {
    await navigator.clipboard.writeText(props.modelValue || '');
    message.success('已复制');
  } catch {
    message.error('复制失败');
  }
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value;
}

function insertVariable(selector: string[]) {
  editorRef.value?.focus();
  const pill = createPill(selector.join('.'));
  insertPillAtSelection(pill);
  scheduleEmit();
}

function focus() {
  editorRef.value?.focus();
}

function blur() {
  editorRef.value?.blur();
}

function getContent() {
  return serializeToText();
}

function setContent(str: string) {
  deserializeFromText(str);
  scheduleEmit();
}

defineExpose({ focus, blur, insertVariable, getContent, setContent });

/** ---------- 生命周期 & watch ---------- */

// 外部 modelValue 变化 → 只在真正不同时才 patch DOM
watch(
  () => props.modelValue,
  (val) => {
    if (!editorRef.value) return;
    if (val === serializeToText()) return;
    // 尽量保留光标：粗暴做法是把光标放到末尾（外部灌值时用户一般不在编辑）
    deserializeFromText(val ?? '');
  },
);

function onGlobalMouseDown(e: MouseEvent) {
  if (!panel.open) return;
  const target = e.target as Node;
  if (editorRef.value?.contains(target)) return;
  if (insertBtnRef.value?.contains(target)) return;
  // TagPanel 内部点击不关闭（它自己会阻止事件冒泡到 document 之外的关闭逻辑）
  const panelEl = document.querySelector('.wf-tag-panel');
  if (panelEl?.contains(target)) return;
  closePanel();
}

onMounted(() => {
  deserializeFromText(props.modelValue);
  document.addEventListener('mousedown', onGlobalMouseDown);
});

onUnmounted(() => {
  document.removeEventListener('mousedown', onGlobalMouseDown);
  if (inputDebounce) clearTimeout(inputDebounce);
});
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
    <div
      class="wf-pe"
      :class="{
        'wf-pe-fullscreen': isFullscreen,
        'hud-panel': isFullscreen,
      }"
    >
    <div v-if="showToolbar" class="wf-pe-toolbar">
      <div class="wf-pe-title">{{ title }}</div>
      <div class="wf-pe-actions">
        <a
          ref="insertBtnRef"
          class="wf-pe-btn"
          title="插入变量"
          @mousedown.prevent
          @click.prevent="toggleInsertPanel"
        >
          <PlusOutlined />
          <span>变量</span>
        </a>
        <a
          class="wf-pe-btn"
          title="复制"
          @mousedown.prevent
          @click.prevent="copyToClipboard"
        >
          <CopyOutlined />
        </a>
        <a
          class="wf-pe-btn"
          :title="isFullscreen ? '退出全屏' : '全屏（Esc 退出）'"
          @mousedown.prevent
          @click.prevent="toggleFullscreen"
        >
          <FullscreenExitOutlined v-if="isFullscreen" />
          <FullscreenOutlined v-else />
        </a>
      </div>
    </div>

    <div
      ref="editorRef"
      class="wf-pe-input"
      :contenteditable="!readonly ? 'true' : 'false'"
      :data-placeholder="placeholder"
      :style="{
        minHeight: isFullscreen ? undefined : minHeight,
        maxHeight: isFullscreen ? undefined : maxHeight,
      }"
      @input="onInput"
      @keydown="onKeyDown"
      @click="onEditorClick"
      @paste="onPaste"
      @mouseup="saveSelection"
      @keyup="saveSelection"
      @blur="saveSelection"
    ></div>

    <div v-if="showToolbar" class="wf-pe-footer">
      <span class="wf-pe-count">{{ charCount }} 字符</span>
    </div>

    <PromptEditorTagPanel
      :show="panel.open"
      :node-id="nodeId"
      :target-element="panel.targetElement"
      :is-slash-mode="panel.mode === 'trigger'"
      :selected-index="panel.selectedIndex"
      :search="panel.search"
      @close="closePanel"
      @select="onPanelSelect"
    />
    </div>
  </Teleport>
</template>

<style scoped>
.wf-pe {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.wf-pe:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.wf-pe-fullscreen {
  position: fixed;
  inset: 32px;
  z-index: 1500;
  box-sizing: border-box;
  max-width: calc(100vw - 64px);
  max-height: calc(100vh - 64px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.wf-pe-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #f9fafb;
  border-bottom: 1px solid #f0f0f0;
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.wf-pe-title {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.wf-pe-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wf-pe-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
  color: #4b5563;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.wf-pe-btn:hover {
  background: #eef2ff;
  color: #4338ca;
}

.wf-pe-input {
  padding: 10px 12px;
  overflow-y: auto;
  overflow-x: hidden;
  font-size: 13px;
  line-height: 1.6;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
  outline: none;
}

.wf-pe-input:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  pointer-events: none;
}

.wf-pe-fullscreen .wf-pe-input {
  flex: 1;
  min-height: 0;
  font-size: 15px;
}

.wf-pe-footer {
  display: flex;
  justify-content: flex-end;
  padding: 4px 12px;
  background: #f9fafb;
  border-top: 1px solid #f0f0f0;
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
}

.wf-pe-count {
  font-size: 11px;
  color: #9ca3af;
}

/* Pill 样式（非 scoped：pill 是通过 innerHTML 插入的） */
</style>

<style>
/* pill 全局样式：因为是通过 createElement 挂进 contenteditable 内部，
 * <style scoped> 的 data-v-xxx 不会自动加到运行时创建的元素上 */
.wf-pe-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 4px 1px 3px;
  margin: 0 2px;
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

.wf-pe-pill:hover {
  background: #e0e7ff;
  border-color: #a5b4fc;
}

.wf-pe-pill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  font-size: 9px;
  font-weight: 700;
  color: #ffffff;
  background: #6366f1;
  border-radius: 3px;
  text-transform: uppercase;
}

.wf-pe-pill[data-type='string'] .wf-pe-pill-icon {
  background: #3b82f6;
}
.wf-pe-pill[data-type='number'] .wf-pe-pill-icon {
  background: #f59e0b;
}
.wf-pe-pill[data-type='boolean'] .wf-pe-pill-icon {
  background: #10b981;
}
.wf-pe-pill[data-type='array'] .wf-pe-pill-icon {
  background: #a855f7;
}
.wf-pe-pill[data-type='object'] .wf-pe-pill-icon {
  background: #6b7280;
}
.wf-pe-pill[data-type='file'] .wf-pe-pill-icon {
  background: #ef4444;
}

.wf-pe-pill-label {
  color: #4338ca;
  font-weight: 500;
}

.wf-pe-pill-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  font-size: 12px;
  line-height: 1;
  color: #6366f1;
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-pe-pill-close:hover {
  background: #c7d2fe;
  color: #1e1b4b;
}
</style>
