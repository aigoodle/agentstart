<script setup lang="ts">
import { computed, ref } from 'vue';

import PromptEditorTagPanel from '@/components/PromptEditorTagPanel.vue';
import { useWorkflowStore } from '@/stores/workflow';
import workflow_utils from '@/workflow/utils/workflow_utils';

/**
 * VarSelectField —— 单变量选择器
 *
 * 与 VarInsertField 的区别：VarInsertField 允许文本 + 变量混排，
 * 而这里只能选中一个上游变量。空状态显示 placeholder，选中后
 * 展示一颗带类型色的 pill；点击 pill 上的 × 清空。
 *
 * v-model 仍是 `{{#nodeId.field#}}` 模板字符串（保持与后端
 * VariableResolver 的契约），空值为 ''。
 */

interface Props {
  modelValue?: string;
  nodeId: string;
  placeholder?: string;
  size?: 'small' | 'middle' | 'large';
  bordered?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择变量',
  size: 'small',
  bordered: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void;
}>();

const workflowStore = useWorkflowStore();

const triggerRef = ref<HTMLElement | null>(null);
const panelOpen = ref(false);

/** `{{#nodeId.field#}}` → ["nodeId","field"]，非合法模板则 null */
const selector = computed<string[] | null>(() => {
  const v = (props.modelValue ?? '').trim();
  const m = v.match(/^\{\{#([^}]+)#\}\}$/);
  if (!m) return null;
  const parts = m[1].split('.');
  return parts.length >= 2 ? parts : null;
});

const pillLabel = computed(() => {
  const sel = selector.value;
  if (!sel) return '';
  return workflow_utils.getVariableLabel(sel) || sel.join('.');
});

const pillType = computed(() => {
  const sel = selector.value;
  if (!sel || sel.length < 2) return '';
  const [nodeId, ...rest] = sel;
  const node = workflowStore.getNodeById(nodeId);
  if (!node?.data) return '';
  const outputs = workflow_utils.getOutputList(node.data) || [];
  let cur: any = outputs.find((o: any) => o.name === rest[0]);
  for (let i = 1; i < rest.length; i++) {
    if (!cur?.children) return cur?.type ?? '';
    cur = cur.children.find((c: any) => c.name === rest[i]);
  }
  return cur?.type ?? '';
});

const pillIconChar = computed(() => (pillType.value?.[0] ?? '·').toUpperCase());

function openPanel(e: MouseEvent) {
  e.preventDefault();
  e.stopPropagation();
  panelOpen.value = true;
}

function closePanel() {
  panelOpen.value = false;
}

function onSelect(variableSelector: string[]) {
  emit('update:modelValue', `{{#${variableSelector.join('.')}#}}`);
  closePanel();
}

function clear(e: MouseEvent) {
  e.preventDefault();
  e.stopPropagation();
  emit('update:modelValue', '');
}
</script>

<template>
  <div
    ref="triggerRef"
    class="wf-vsf"
    :class="[
      `wf-vsf-${size}`,
      { 'wf-vsf-borderless': !bordered, 'wf-vsf-filled': !!selector },
    ]"
    @click="openPanel"
  >
    <template v-if="selector">
      <span class="wf-vsf-pill" :data-type="pillType" :title="selector.join('.')">
        <span class="wf-vsf-pill-icon">{{ pillIconChar }}</span>
        <span class="wf-vsf-pill-label">{{ pillLabel }}</span>
        <span class="wf-vsf-pill-close" title="清除" @click="clear">×</span>
      </span>
    </template>
    <span v-else class="wf-vsf-placeholder">{{ placeholder }}</span>
    <span class="wf-vsf-selector-icon" title="选择上游变量" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="14" height="14">
        <path fill="currentColor" d="M4 6h2v2H4V6zm4 0h12v2H8V6zM4 11h2v2H4v-2zm4 0h12v2H8v-2zM4 16h2v2H4v-2zm4 0h12v2H8v-2z" />
      </svg>
    </span>

    <PromptEditorTagPanel
      :show="panelOpen"
      :node-id="nodeId"
      :target-element="triggerRef"
      @close="closePanel"
      @select="onSelect"
    />
  </div>
</template>

<style scoped>
.wf-vsf {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 24px;
  padding: 2px 6px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.wf-vsf:hover {
  border-color: #a5b4fc;
}

.wf-vsf-filled {
  background: #f9fafb;
}

.wf-vsf-borderless {
  border-color: transparent;
  background: transparent;
}

.wf-vsf-borderless:hover {
  border-color: transparent;
}

.wf-vsf-small { min-height: 24px; font-size: 13px; }
.wf-vsf-middle { min-height: 32px; font-size: 14px; }
.wf-vsf-large { min-height: 40px; font-size: 15px; }
.wf-vsf-placeholder { flex:1; min-width:0; color:#9ca3af; }
.wf-vsf-selector-icon { display:inline-flex; align-items:center; justify-content:center; flex:none; width:22px; color:#94a3b8; }
.wf-vsf:hover .wf-vsf-selector-icon { color:#4f46e5; }

.wf-vsf-placeholder {
  color: #9ca3af;
  font-size: 12px;
}

.wf-vsf-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0 3px 0 2px;
  font-size: 12px;
  line-height: 1.4;
  color: #1f2937;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 4px;
  user-select: none;
}

.wf-vsf-pill-icon {
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

.wf-vsf-pill[data-type='string'] .wf-vsf-pill-icon { background: #3b82f6; }
.wf-vsf-pill[data-type='number'] .wf-vsf-pill-icon { background: #f59e0b; }
.wf-vsf-pill[data-type='boolean'] .wf-vsf-pill-icon { background: #10b981; }
.wf-vsf-pill[data-type='array'] .wf-vsf-pill-icon { background: #a855f7; }
.wf-vsf-pill[data-type='object'] .wf-vsf-pill-icon { background: #6b7280; }
.wf-vsf-pill[data-type='file'] .wf-vsf-pill-icon { background: #ef4444; }

.wf-vsf-pill-label {
  color: #4338ca;
  font-weight: 500;
}

.wf-vsf-pill-close {
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

.wf-vsf-pill-close:hover {
  background: #c7d2fe;
}
</style>
