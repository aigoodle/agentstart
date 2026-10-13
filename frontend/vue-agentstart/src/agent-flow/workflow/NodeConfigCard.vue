<script setup lang="ts">
import { computed, defineEmits, onBeforeMount, ref, watch } from 'vue';

import {
  BlockOutlined,
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  EllipsisOutlined,
  PlayCircleOutlined,
  ReadOutlined,
  RetweetOutlined,
} from '@ant-design/icons-vue';

import Icon from './Icon.vue';
import AgentNodeCard from './nodeCard/AgentNodeCard.vue';
import AnswerNodeCard from './nodeCard/AnswerNodeCard.vue';
import ClassifierCard from './nodeCard/ClassifierCard.vue';
import CodeNodeCard from './nodeCard/CodeNodeCard.vue';
import ConditionNodeCard from './nodeCard/ConditionNodeCard.vue';
import EndNodeCard from './nodeCard/EndNodeCard.vue';
import DocumentExtractorCard from './nodeCard/DocumentExtractorCard.vue';
import HttpNodeCard from './nodeCard/HttpNodeCard.vue';
import DurableWaitCard from './nodeCard/DurableWaitCard.vue';
import HumanInputCard from './nodeCard/HumanInputCard.vue';
import IterationCard from './nodeCard/IterationCard.vue';
import KnowledgeRetrievalCard from './nodeCard/KnowledgeRetrievalCard.vue';
import ListOperatorCard from './nodeCard/ListOperatorCard.vue';
import LLMNodeCard from './nodeCard/LLMNodeCard.vue';
import ParameterExtractorCard from './nodeCard/ParameterExtractorCard.vue';
import ServiceApiNodeCard from './nodeCard/ServiceApiNodeCard.vue';
import ConnectorNodeCard from './nodeCard/ConnectorNodeCard.vue';
import VideoGenerationNodeCard from './nodeCard/VideoGenerationNodeCard.vue';
import ScheduleTriggerNodeCard from './nodeCard/ScheduleTriggerNodeCard.vue';
import StartNodeCard from './nodeCard/StartNodeCard.vue';
import VariableAssignerCard from './nodeCard/VariableAssignerCard.vue';
import VariableNodeCard from './nodeCard/VariableNodeCard.vue';

import './_config-base.css';

const props = defineProps({
  selectNode: { type: Object, default: () => ({}) },
  parentHierarchy: { type: Array, default: () => [] },
  mainData: { type: Object, default: () => ({}) },
  workflowOptionsLoader: { type: Function, default: undefined },
});
const emit = defineEmits([
  'onClose',
  'dataChange',
  'runStep',
  'changeType',
  'copyNode',
  'duplicateNode',
  'deleteNode',
]);

const menuOpen = ref(false);

function menuAction(action: 'run' | 'change' | 'copy' | 'duplicate' | 'delete') {
  menuOpen.value = false;
  const id = props.selectNode?.id;
  if (!id) return;
  switch (action) {
    case 'run':
      emit('runStep', id);
      break;
    case 'change':
      emit('changeType', id);
      break;
    case 'copy':
      emit('copyNode', id);
      break;
    case 'duplicate':
      emit('duplicateNode', id);
      break;
    case 'delete':
      emit('deleteNode', id);
      break;
  }
}

/** 是否允许删除：START 节点不能删 */
const canDelete = computed(() => props.selectNode?.type !== 'START');
const canRun = computed(() =>
  ['LLM', 'AGENT', 'CODE', 'HTTP_REQUEST', 'SERVICE_API', 'CONNECTOR', 'SCHEDULE_TRIGGER', 'KNOWLEDGE_RETRIEVAL', 'QUESTION_CLASSIFIER'].includes(
    props.selectNode?.type,
  ),
);

const formData: any = ref({});
const attrListGroup: any = ref([]);
let suppressWatch = false;

/** 节点类型（backend NodeType 名） → 图标 + 主色（用于面板头部 accent 色） */
const nodeTypeTheme: Record<string, { icon: string; accent: string }> = {
  START: { icon: 'start', accent: '#10b981' },
  END: { icon: 'end', accent: '#ef4444' },
  LLM: { icon: 'llm', accent: '#6366f1' },
  VIDEO_GENERATION: { icon: 'llm', accent: '#8b5cf6' },
  AGENT: { icon: 'agent', accent: '#8b5cf6' },
  KNOWLEDGE_RETRIEVAL: { icon: 'knowledge', accent: '#14b8a6' },
  QUESTION_CLASSIFIER: { icon: 'classifier', accent: '#7c3aed' },
  IF_ELSE: { icon: 'condition', accent: '#f59e0b' },
  CODE: { icon: 'code', accent: '#84cc16' },
  ANSWER: { icon: 'answer', accent: '#f97316' },
  HTTP_REQUEST: { icon: 'http', accent: '#06b6d4' },
  SERVICE_API: { icon: 'service', accent: '#0d9488' },
  CONNECTOR: { icon: 'connector', accent: '#ec4899' },
  SCHEDULE_TRIGGER: { icon: 'schedule', accent: '#f59e0b' },
  VARIABLE_AGGREGATOR: { icon: 'variable', accent: '#0ea5e9' },
  LOOP: { icon: 'loop', accent: '#dc2626' },
  TEMPLATE_TRANSFORM: { icon: 'template', accent: '#a855f7' },
  USER_INPUT: { icon: 'user', accent: '#3b82f6' },
  FILE_UPLOAD: { icon: 'file', accent: '#059669' },
  ITERATION: { icon: 'loop', accent: '#f472b6' },
  PARAMETER_EXTRACTOR: { icon: 'variable', accent: '#eab308' },
  LIST_OPERATOR: { icon: 'variable', accent: '#22d3ee' },
  DOCUMENT_EXTRACTOR: { icon: 'file', accent: '#0891b2' },
  HUMAN_INPUT: { icon: 'user', accent: '#f59e0b' },
  APPROVAL: { icon: 'user', accent: '#f59e0b' },
  WAIT_EVENT: { icon: 'user', accent: '#0ea5e9' },
  SLEEP_UNTIL: { icon: 'schedule', accent: '#8b5cf6' },
  VARIABLE_ASSIGNER: { icon: 'variable', accent: '#059669' },
};

const currentTheme = computed(
  () => nodeTypeTheme[props.selectNode?.type] || nodeTypeTheme.LLM,
);

/* -------- 面板宽度：可通过左边缘手柄拖动来调整，localStorage 记忆 --------
 * 用 pointer capture 而不是全局 window 监听：move / up 事件即使指针滑出手柄
 * 也照样发到手柄元素上，避免遗漏 up 造成的"拖动粘滞"，也不用在 unmount 时
 * 手动清理监听器。 */
const PANEL_WIDTH_KEY = 'wf-config-panel-width';
const PANEL_MIN_WIDTH = 320;
const PANEL_MAX_WIDTH = 960;
const PANEL_DEFAULT_WIDTH = 420;

function clampPanelWidth(w: number): number {
  return Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, w));
}

function loadInitialPanelWidth(): number {
  if (typeof window === 'undefined') return PANEL_DEFAULT_WIDTH;
  const raw = window.localStorage?.getItem(PANEL_WIDTH_KEY);
  const parsed = raw == null ? Number.NaN : Number(raw);
  if (Number.isFinite(parsed) && parsed >= PANEL_MIN_WIDTH && parsed <= PANEL_MAX_WIDTH) {
    return parsed;
  }
  return PANEL_DEFAULT_WIDTH;
}

const panelWidth = ref(loadInitialPanelWidth());
const resizing = ref(false);
const resizeStart = { clientX: 0, width: 0, pointerId: -1 };

function onResizeStart(e: PointerEvent) {
  if (e.button !== 0) return;
  resizing.value = true;
  resizeStart.clientX = e.clientX;
  resizeStart.width = panelWidth.value;
  resizeStart.pointerId = e.pointerId;
  (e.currentTarget as Element).setPointerCapture?.(e.pointerId);
  document.body.style.userSelect = 'none';
  document.body.style.cursor = 'col-resize';
  e.preventDefault();
}

function onResizeMove(e: PointerEvent) {
  if (!resizing.value || e.pointerId !== resizeStart.pointerId) return;
  // 手柄在面板左边缘：向左拖 → clientX 变小 → delta 为正 → 面板变宽
  const delta = resizeStart.clientX - e.clientX;
  panelWidth.value = clampPanelWidth(resizeStart.width + delta);
}

function onResizeEnd(e: PointerEvent) {
  if (!resizing.value || e.pointerId !== resizeStart.pointerId) return;
  resizing.value = false;
  const target = e.currentTarget as Element;
  if (target.hasPointerCapture?.(e.pointerId)) {
    target.releasePointerCapture(e.pointerId);
  }
  document.body.style.userSelect = '';
  document.body.style.cursor = '';
  try {
    window.localStorage?.setItem(PANEL_WIDTH_KEY, String(panelWidth.value));
  } catch {
    // 无 storage 权限（隐私模式）直接忽略
  }
}

const panelStyle = computed(() => ({
  '--wf-accent': currentTheme.value.accent,
  width: `${panelWidth.value}px`,
}));

const closeCard = () => emit('onClose', false);

const panelInit = () => {
  if (props.selectNode) {
    suppressWatch = true;
    // 深拷贝以免面板编辑直接改动画布内部数据；改动通过 dataChange 事件回写
    formData.value = JSON.parse(JSON.stringify(props.selectNode.data ?? {}));
    // 下一轮 tick 才恢复监听，避免 init 时 watch 立刻触发一次
    setTimeout(() => (suppressWatch = false), 0);
  }
};

/** 深度监听 formData，任何修改都通过 dataChange 事件冒泡到宿主，
 *  由 FlowDesigner.patchNodeData 落回画布节点 */
watch(
  () => formData.value,
  (val) => {
    if (suppressWatch || !props.selectNode?.id) return;
    emit('dataChange', {
      nodeId: props.selectNode.id,
      data: JSON.parse(JSON.stringify(val)),
    });
  },
  { deep: true },
);

onBeforeMount(panelInit);
watch(() => props.selectNode, panelInit);
</script>

<template>
  <div class="wf-config-panel hud-panel" :style="panelStyle">
    <!-- 左边缘拖拽手柄：向左拖动可加宽面板 -->
    <div
      class="wf-config-resize"
      :class="{ 'wf-config-resize-active': resizing }"
      title="拖动可调整面板宽度"
      @pointerdown="onResizeStart"
      @pointermove="onResizeMove"
      @pointerup="onResizeEnd"
      @pointercancel="onResizeEnd"
    >
      <div class="wf-config-resize-bar" />
    </div>

    <!-- Header：图标 + 可编辑标题 + 操作 -->
    <div class="wf-config-header">
      <div class="wf-config-header-icon">
        <Icon :name="currentTheme.icon" />
      </div>
      <a-input
        v-model:value="formData.label"
        :bordered="false"
        size="small"
        class="wf-config-header-title-input"
      />
      <div class="wf-config-header-actions">
        <a-popover
          v-model:open="menuOpen"
          placement="bottomRight"
          trigger="click"
          :overlay-class-name="'wf-config-menu-overlay'"
        >
          <template #content>
            <div class="wf-config-menu">
              <div
                v-if="canRun"
                class="wf-config-menu-item"
                @click="menuAction('run')"
              >
                <PlayCircleOutlined class="wf-config-menu-icon" />
                <span>运行此步骤</span>
              </div>
              <div class="wf-config-menu-item" @click="menuAction('change')">
                <RetweetOutlined class="wf-config-menu-icon" />
                <span>更改节点类型</span>
              </div>
              <div class="wf-config-menu-divider" />
              <div class="wf-config-menu-item" @click="menuAction('copy')">
                <CopyOutlined class="wf-config-menu-icon" />
                <span>复制</span>
                <span class="wf-config-menu-shortcut">Ctrl+C</span>
              </div>
              <div class="wf-config-menu-item" @click="menuAction('duplicate')">
                <BlockOutlined class="wf-config-menu-icon" />
                <span>创建副本</span>
                <span class="wf-config-menu-shortcut">Ctrl+D</span>
              </div>
              <template v-if="canDelete">
                <div class="wf-config-menu-divider" />
                <div
                  class="wf-config-menu-item wf-config-menu-item-danger"
                  @click="menuAction('delete')"
                >
                  <DeleteOutlined class="wf-config-menu-icon" />
                  <span>删除节点</span>
                  <span class="wf-config-menu-shortcut">Del</span>
                </div>
              </template>
            </div>
          </template>
          <button class="wf-config-header-btn" title="更多">
            <EllipsisOutlined />
          </button>
        </a-popover>
        <a-tooltip title="帮助文档" placement="bottom">
          <button class="wf-config-header-btn">
            <ReadOutlined />
          </button>
        </a-tooltip>
        <div class="wf-config-header-divider" />
        <a-tooltip title="关闭" placement="bottom">
          <button class="wf-config-header-btn" @click="closeCard">
            <CloseOutlined />
          </button>
        </a-tooltip>
      </div>
    </div>

    <!-- 描述行 -->
    <div class="wf-config-desc">
      <a-input
        v-model:value="formData.description"
        :bordered="false"
        placeholder="添加描述"
      />
    </div>

    <!-- Body：按 type 分发到具体表单。所有 card 统一收到 nodeId=selectNode.id，
         这样卡片内部 PromptEditor / VariableSelector / VarInsertField 弹出的
         "上游变量选择" 面板能通过 workflowStore.getParentNodeList(nodeId) 正确
         定位上游节点。formData 是 node.data 的深拷贝，不带 id，所以不能直接
         用 formState.id —— 那个字段常年 undefined，导致变量面板打开时是空的。 -->
    <div class="wf-config-body">
      <AgentNodeCard
        v-if="selectNode.type === 'AGENT'"
        v-model="formData"
        :attr-list-group="attrListGroup"
        :node-id="selectNode.id"
      />
      <LLMNodeCard
        v-else-if="selectNode.type === 'LLM'"
        v-model="formData"
        :attr-list-group="attrListGroup"
        :node-id="selectNode.id"
      />
      <KnowledgeRetrievalCard
        v-else-if="selectNode.type === 'KNOWLEDGE_RETRIEVAL'"
        v-model="formData"
        :attr-list-group="attrListGroup"
        :node-id="selectNode.id"
      />
      <ClassifierCard
        v-else-if="selectNode.type === 'QUESTION_CLASSIFIER'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <ConditionNodeCard
        v-else-if="selectNode.type === 'IF_ELSE'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <StartNodeCard
        v-else-if="selectNode.type === 'START'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <CodeNodeCard
        v-else-if="selectNode.type === 'CODE'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <AnswerNodeCard
        v-else-if="selectNode.type === 'ANSWER'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <HttpNodeCard
        v-else-if="selectNode.type === 'HTTP_REQUEST'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <ServiceApiNodeCard
        v-else-if="selectNode.type === 'SERVICE_API'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <VideoGenerationNodeCard v-else-if="selectNode.type === 'VIDEO_GENERATION'" v-model="formData" :node-id="selectNode.id" />
      <ConnectorNodeCard
        v-else-if="selectNode.type === 'CONNECTOR'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <ScheduleTriggerNodeCard
        v-else-if="selectNode.type === 'SCHEDULE_TRIGGER'"
        v-model="formData"
        :node-id="selectNode.id"
        :current-app-id="mainData.appId || mainData.id"
        :workflow-options-loader="workflowOptionsLoader"
      />
      <EndNodeCard
        v-else-if="selectNode.type === 'END'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <VariableNodeCard
        v-else-if="selectNode.type === 'VARIABLE_AGGREGATOR'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <IterationCard
        v-else-if="selectNode.type === 'ITERATION'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <ParameterExtractorCard
        v-else-if="selectNode.type === 'PARAMETER_EXTRACTOR'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <ListOperatorCard
        v-else-if="selectNode.type === 'LIST_OPERATOR'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <DocumentExtractorCard
        v-else-if="selectNode.type === 'DOCUMENT_EXTRACTOR'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <HumanInputCard
        v-else-if="selectNode.type === 'HUMAN_INPUT'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <DurableWaitCard
        v-else-if="['APPROVAL', 'WAIT_EVENT', 'SLEEP_UNTIL'].includes(selectNode.type)"
        v-model="formData"
        :node-type="selectNode.type"
      />
      <VariableAssignerCard
        v-else-if="selectNode.type === 'VARIABLE_ASSIGNER'"
        v-model="formData"
        :node-id="selectNode.id"
      />
      <div v-else class="wf-config-section">
        <div class="wf-config-section-title">此节点暂无配置项</div>
        <div class="wf-config-hint">
          节点类型 <code>{{ selectNode.type }}</code> 的配置表单尚未实现。
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 让内嵌的 antd input 在 header 里视觉一致 */
:deep(.wf-config-header-title-input .ant-input) {
  padding: 0 !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  color: var(--wf-config-text) !important;
}

/* 左边缘拖拽手柄：完全嵌在面板内 6px 触发区，中央一条 2px 竖线
 * hover / 拖动时点亮为节点主题色。父容器 overflow: hidden，因此不能用负 left。*/
.wf-config-resize {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 6px;
  z-index: 20;
  cursor: col-resize;
  user-select: none;
  touch-action: none;
}

.wf-config-resize-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 2px;
  width: 2px;
  background: transparent;
  transition: background 0.15s ease;
}

.wf-config-resize:hover .wf-config-resize-bar,
.wf-config-resize-active .wf-config-resize-bar {
  background: var(--wf-accent, #6366f1);
}
</style>
