<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { computed, defineEmits, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

import { Background } from '@vue-flow/background';
import { MarkerType, useVueFlow, VueFlow } from '@vue-flow/core';
import { MiniMap } from '@vue-flow/minimap';

import { publishWorkflow, runWorkflow, saveWorkflow } from '@/adapter/backend';
import { useWorkflowStore } from '@/stores/workflow';
import langUtils from '@/utils/langUtils';
import nodeCardForm from '@/workflow/utils/node_card_form';
import workflow_utils from '@/workflow/utils/workflow_utils';
import { validateWorkflowGraph } from '@/workflow/utils/graph_validator';
import { useWorkflowGraph } from './composables/useWorkflowGraph';

import CustomEdge from './CustomEdge.vue';
import Icon from './Icon.vue';
import AgentNode from './nodes/AgentNode.vue';
import AnswerNode from './nodes/AnswerNode.vue';
import ClassifierNode from './nodes/ClassifierNode.vue';
import CodeNode from './nodes/CodeNode.vue';
import ConditionNode from './nodes/ConditionNode.vue';
import DocumentExtractorNode from './nodes/DocumentExtractorNode.vue';
import EndNode from './nodes/EndNode.vue';
import FileUploadNode from './nodes/FileUploadNode.vue';
import HttpNode from './nodes/HttpNode.vue';
import DurableWaitNode from './nodes/DurableWaitNode.vue';
import IterationNode from './nodes/IterationNode.vue';
import KnowledgeRetrievalNode from './nodes/KnowledgeRetrievalNode.vue';
import ListOperatorNode from './nodes/ListOperatorNode.vue';
import LLMNode from './nodes/LLMNode.vue';
import LoopNode from './nodes/LoopNode.vue';
import ParameterExtractorNode from './nodes/ParameterExtractorNode.vue';
import ServiceApiNode from './nodes/ServiceApiNode.vue';
import ConnectorNode from './nodes/ConnectorNode.vue';
import VideoGenerationNode from './nodes/VideoGenerationNode.vue';
import ScheduleTriggerNode from './nodes/ScheduleTriggerNode.vue';
import StartNode from './nodes/StartNode.vue';
import TemplateNode from './nodes/TemplateNode.vue';
import UserInputNode from './nodes/UserInputNode.vue';
import VariableAssignerNode from './nodes/VariableAssignerNode.vue';
import VariableNode from './nodes/VariableNode.vue';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
import '@vue-flow/node-resizer/dist/style.css';
import './nodes/_node-base.css';

const props = defineProps({
  mode: {
    type: String,
    default: () => '',
  },
  /** 只读模式：禁用节点拖拽、连线、删除、编辑；隐藏顶部工具栏和悬浮工具栏 */
  readonly: {
    type: Boolean,
    default: false,
  },
  /**
   * 所属应用 id —— 走 BackendAdapter 保存/发布时必须携带的字段。backend
   * WorkflowService.save() 会把 workflows 行的 PK pin 到 appId 上，所以
   * 同一 app 反复保存都命中同一行（Dify draft-per-app 语义）。
   * 独立宿主（例如 /agent-flow 站点级别的 playground）应该在挂载前生成一个
   * scratch UUID 传进来 —— 空值会导致宿主 adapter 抛错。
   */
  appId: {
    type: String,
    default: '',
  },
  /**
   * 嵌入宿主模式 —— 由 AppDesignDrawer 这类宿主容器传入。宿主自己已经在
   * 抽屉右上角提供了 保存 / 发布 / 关闭 按钮，这里把画布顶部工具栏里同名的
   * 三颗按钮（试运行 / 保存 / 发布）隐藏，只保留撤销 / 重做 图标按钮，避免
   * 重复操作和视觉噪音。独立宿主（standalone 页面）不传，工具栏保留全部按钮。
   */
  embedded: {
    type: Boolean,
    default: false,
  },
});

/** 画布右键上下文菜单状态 */
const contextMenu = ref<{
  visible: boolean;
  x: number;
  y: number;
  nodeId: string | null;
} | null>({ visible: false, x: 0, y: 0, nodeId: null });

const emit = defineEmits([
  'nodeClick',
  'nodeDelete',
  'run',
  'save',
  'publish',
]);

const {
  addNodes,
  removeNodes,
  updateNode,
  updateEdge,
  addEdges,
  findNode,
  getNodes,
  getEdges,
  zoomIn,
  zoomOut,
  fitView,
  setViewport,
  getViewport,
  updateNodeInternals,
} = useVueFlow();

const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);
const selectedNode = ref(null);
const selectedEdge = ref(null);
const hoveredNode = ref(null);
const hoveredEdge = ref(null);
const nodeCounter = ref(0);
const showNodeSelector = ref(false);
const nodeSelectorPosition = ref({ x: 0, y: 0 });
const sourceNodeForConnection = ref(null);
const sourceHandleId = ref(null);
const hoveredNodeType = ref(null);

// 历史记录管理
const history = ref([]);
const historyIndex = ref(-1);
const maxHistorySize = 50;

const canUndo = computed(() => historyIndex.value > 0);
const canRedo = computed(() => historyIndex.value < history.value.length - 1);
const workflowStore = useWorkflowStore();
useWorkflowGraph(getNodes, getEdges);

// 节点类型配置 —— 每个 `type` 字符串与 backend `NodeType` 枚举（UPPER_SNAKE）
// 保持一致，同时也是 FlowDesigner 里 `<template #node-{type}>` 注册的 slot 名。
// 设计器独有的 USER_INPUT / FILE_UPLOAD / LOOP 没有严格的
// backend 对应节点，backend 的 NodeType.fromJson 会把它们映射到最接近的引擎
// 节点（前三者 → START，LOOP → ITERATION），保证保存的图仍能跑起来。
const nodeCategories = ref([
  {
    title: '输入',
    icon: 'input',
    nodes: [
      {
        type: 'START',
        label: '开始',
        icon: 'start',
        description: '工作流开始节点',
      },
      {
        type: 'USER_INPUT',
        label: '用户输入',
        icon: 'user',
        description: '用户输入节点',
      },
      {
        type: 'FILE_UPLOAD',
        label: '文件上传',
        icon: 'file',
        description: '文件上传节点',
      },
    ],
  },
  {
    title: 'AI 模型',
    icon: 'ai',
    nodes: [
      { type: 'LLM', label: 'LLM', icon: 'llm', description: '大语言模型节点' },
      { type: 'AGENT', label: 'Agent', icon: 'agent', description: '智能体' },
      {
        type: 'KNOWLEDGE_RETRIEVAL',
        label: '知识检索',
        icon: 'knowledge',
        description: '知识库检索节点',
      },
      {
        type: 'QUESTION_CLASSIFIER',
        label: '分类器',
        icon: 'classifier',
        description: '文本分类节点',
      },
    ],
  },
  {
    title: '逻辑',
    icon: 'logic',
    nodes: [
      {
        type: 'IF_ELSE',
        label: '条件分支',
        icon: 'condition',
        description: '条件判断分支节点',
      },
      {
        type: 'LOOP',
        label: '循环',
        icon: 'loop',
        description: '循环执行节点',
      },
      {
        type: 'ITERATION',
        label: '迭代',
        icon: 'loop',
        description: '对列表变量执行子图',
      },
      {
        type: 'LIST_OPERATOR',
        label: '列表操作',
        icon: 'variable',
        description: '列表过滤/映射/排序',
      },
      {
        type: 'VARIABLE_AGGREGATOR',
        label: '变量聚合',
        icon: 'variable',
        description: '变量聚合节点',
      },
      {
        type: 'PARAMETER_EXTRACTOR',
        label: '参数提取',
        icon: 'variable',
        description: '从文本抽取结构化参数',
      },
      {
        type: 'VARIABLE_ASSIGNER',
        label: '变量赋值',
        icon: 'variable',
        description: '写回会话/环境变量',
      },
      {
        type: 'HUMAN_INPUT',
        label: '人工输入',
        icon: 'user',
        description: '持久化等待人工补充信息',
      },
      {
        type: 'APPROVAL',
        label: '审批',
        icon: 'user',
        description: '持久化等待审批决定',
      },
      {
        type: 'WAIT_EVENT',
        label: '等待事件',
        icon: 'user',
        description: '通过 correlation key 等待外部事件',
      },
      {
        type: 'SLEEP_UNTIL',
        label: '定时等待',
        icon: 'schedule',
        description: '释放线程并在指定时间恢复',
      },
      {
        type: 'DOCUMENT_EXTRACTOR',
        label: '文档提取',
        icon: 'file',
        description: '从文件解析文本',
      },
    ],
  },
  {
    title: '工具',
    icon: 'tools',
    nodes: [
      {
        type: 'HTTP_REQUEST',
        label: 'HTTP 请求',
        icon: 'http',
        description: 'HTTP 请求节点',
      },
      {
        type: 'SERVICE_API',
        label: '服务接口',
        icon: 'service',
        description: '调用内部服务接口（免鉴权）',
      },
      {
        type: 'CONNECTOR',
        label: '连接器',
        icon: 'connector',
        description: '调用已安装的外部连接器',
      },
      {
        type: 'SCHEDULE_TRIGGER',
        label: '定时任务',
        icon: 'schedule',
        description: '按指定时间触发目标工作流',
      },
      {
        type: 'CODE',
        label: '代码执行',
        icon: 'code',
        description: '代码执行节点',
      },
      {
        type: 'TEMPLATE_TRANSFORM',
        label: '模板转换',
        icon: 'template',
        description: '模板转换节点',
      },
    ],
  },
  {
    title: '输出',
    icon: 'output',
    nodes: [
      {
        type: 'ANSWER',
        label: '直接回复',
        icon: 'answer',
        description: '直接回复节点',
      },
      {
        type: 'END',
        label: '结束',
        icon: 'end',
        description: '工作流结束节点',
      },
    ],
  },
]);

const initialNodes = [
  {
    id: '1',
    type: 'START',
    position: { x: 100, y: 100 },
    data: {
      label: '开始',
      description: '工作流开始节点',
      mode: '',
    },
  },
  {
    id: '2',
    type: 'LLM',
    position: { x: 500, y: 100 },
    data: {
      label: 'LLM',
      description: '大语言模型节点',
      config: {
        model: 'gpt-3.5-turbo',
        temperature: 0.7,
        max_tokens: 1000,
      },
    },
  },
  {
    id: '3',
    type: 'ANSWER',
    position: { x: 900, y: 100 },
    data: {
      label: '直接回复',
      description: '直接回复用户',
      config: {},
    },
  },
];

const initialEdges = [
  {
    id: 'e1-2',
    source: '1',
    target: '2',
    animated: true,
    style: { stroke: '#94a3b8', strokeWidth: 2 },
    type: 'custom',
    markerEnd: MarkerType.ArrowClosed,
  },
  {
    id: 'e2-3',
    source: '2',
    target: '3',
    animated: true,
    style: { stroke: '#94a3b8', strokeWidth: 2 },
    type: 'custom',
    markerEnd: MarkerType.ArrowClosed,
  },
];
const defaultViewport: any = { x: 0, y: 0, zoom: 0.8 };

function initGraph() {
  setTimeout(() => {
    let edgesList = [];
    let nodeList = [];
    if (props.mode === 'WORKFLOW') {
      // 新建工作流默认给出 开始 -> LLM -> 结束 三节点，避免画布空白
      const start = langUtils.clone(initialNodes[0]);
      start.data.mode = props.mode;
      const llm = langUtils.clone(initialNodes[1]);
      const end = {
        id: '3',
        type: 'END',
        position: { x: 900, y: 100 },
        data: {
          label: '结束',
          description: '工作流结束节点',
        },
      };
      nodeList = [start, llm, end];
      edgesList = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          type: 'custom',
          markerEnd: MarkerType.ArrowClosed,
        },
        {
          id: 'e2-3',
          source: '2',
          target: '3',
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          type: 'custom',
          markerEnd: MarkerType.ArrowClosed,
        },
      ];
    } else if (props.mode === 'CHATFLOW') {
      // Chatflow 默认给出 开始 -> LLM -> 直接回复 三节点。ANSWER 节点的语义
      // 是"把上游文本作为最终回复输出"，backend AnswerNodeExecutor 输出
      // {answer: ...}，WorkflowChatGenerator.extractAnswer() 读到后按 SSE
      // message 事件推给前端。
      const start = langUtils.clone(initialNodes[0]);
      start.data.mode = props.mode;
      const llm = langUtils.clone(initialNodes[1]);
      const answer = {
        id: '3',
        type: 'ANSWER',
        position: { x: 900, y: 100 },
        data: {
          label: '直接回复',
          description: '把 LLM 结果作为最终回复返回',
          // Seed 一个默认模板，引用 LLM 节点的输出。用户可以在节点配置里
          // 覆写为任意 {{#节点id.字段#}} 组合的话术。
          answer: '{{#2.text#}}',
          config: {},
        },
      };
      nodeList = [start, llm, answer];
      edgesList = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          type: 'custom',
          markerEnd: MarkerType.ArrowClosed,
        },
        {
          id: 'e2-3',
          source: '2',
          target: '3',
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          type: 'custom',
          markerEnd: MarkerType.ArrowClosed,
        },
      ];
    } else {
      nodeList = langUtils.clone(initialNodes);
      edgesList = langUtils.clone(initialEdges);
    }
    workflow_utils.initNodeDefaultData(nodeList);
    nodes.value = nodeList;
    edges.value = edgesList;
    nodeCounter.value = nodeList.length;
    setViewport(defaultViewport);
    resetGraph();
    // When FlowDesigner mounts inside a drawer / modal, vue-flow's
    // fit-view-on-init sees the initial (empty) nodes list and never re-fits
    // once we push the 3 defaults. Force a fitView so the canonical seed is
    // actually visible instead of parked at (0,0) with zoom 0.8.
    setTimeout(() => {
      try {
        fitView({ padding: 0.2 });
      } catch {
        // vue-flow may not be fully mounted — no-op is fine.
      }
    }, 30);
  }, 10);
}

function resetGraph() {
  const graph = { nodes: getNodes.value, edges: getEdges.value };
  workflowStore.setGraph(graph);
}

function reloadGraph(graph: any) {
  if (!graph) {
    initGraph();
    return;
  }
  // `[]` is truthy in JS — a saved-but-empty graph must fall through to
  // initGraph() too, otherwise the canvas would render permanently blank on
  // reopen.
  if (Array.isArray(graph.nodes) && graph.nodes.length > 0) {
    // Merge in default per-type form data so persisted (creation-seed or
    // externally-produced) graphs get the same node.data shape that
    // `initGraph()` sets up. Without this, LLMNode / KnowledgeRetrievalNode /
    // etc. may render with missing config fields.
    workflow_utils.initNodeDefaultData(graph.nodes);
    nodes.value = graph.nodes;
    edges.value = dedupeEdges(graph.edges ?? []);
    nodeCounter.value = graph.nodes.length;
    // Persisted graphs from before viewport was tracked won't carry one —
    // fall back to a full {x,y,zoom} shape so vue-flow's setViewport doesn't
    // dereference `undefined.x`.
    const persistedViewport = graph.viewport;
    resetGraph();
    // Restore the exact saved pan + zoom after VueFlow has rendered the nodes.
    // Only legacy graphs without viewport metadata should be auto-fitted.
    setTimeout(() => {
      try {
        if (persistedViewport) setViewport(persistedViewport);
        else fitView({ padding: 0.2 });
      } catch {
        // vue-flow may not be fully mounted yet — no-op is fine.
      }
    }, 30);
  } else {
    initGraph();
  }
}

/** 在画布空白处添加节点 —— 从 API 层被调用，非主路径 */
function addNode(type) {
  const newNodeId = langUtils.getId('node');
  const newNode = {
    id: newNodeId,
    type,
    position: findFreePosition(),
    data: {
      label: getDefaultLabel(type),
      description: getDefaultDescription(type),
      ...getDefaultConfig(type),
    },
  };
  addNodes([newNode]);
  saveToHistory();
  resetGraph();
}

/** 找一个不与已有节点重叠的空白位置 */
function findFreePosition(): { x: number; y: number } {
  const NODE_W = 300;
  const NODE_H = 180;
  const START_X = 200;
  const START_Y = 200;
  const STEP_X = 350;
  const STEP_Y = 220;

  const occupied = (x: number, y: number) =>
    nodes.value.some((n) => {
      const nx = n.position?.x ?? 0;
      const ny = n.position?.y ?? 0;
      return (
        Math.abs(nx - x) < NODE_W * 0.6 &&
        Math.abs(ny - y) < NODE_H * 0.6
      );
    });

  // 按网格扫，行优先
  for (let row = 0; row < 20; row++) {
    for (let col = 0; col < 20; col++) {
      const x = START_X + col * STEP_X;
      const y = START_Y + row * STEP_Y;
      if (!occupied(x, y)) return { x, y };
    }
  }
  return { x: START_X, y: START_Y };
}

function getDefaultLabel(type) {
  const labels = {
    START: '开始',
    USER_INPUT: '用户输入',
    FILE_UPLOAD: '文件上传',
    LLM: 'LLM',
    AGENT: 'Agent',
    KNOWLEDGE_RETRIEVAL: '知识检索',
    QUESTION_CLASSIFIER: '问题分类',
    IF_ELSE: '条件分支',
    LOOP: '循环',
    ITERATION: '迭代',
    LIST_OPERATOR: '列表操作',
    VARIABLE_AGGREGATOR: '变量聚合',
    VARIABLE_ASSIGNER: '变量赋值',
    PARAMETER_EXTRACTOR: '参数提取',
    DOCUMENT_EXTRACTOR: '文档提取',
    HUMAN_INPUT: '人工介入',
    APPROVAL: '审批',
    WAIT_EVENT: '等待事件',
    SLEEP_UNTIL: '定时等待',
    HTTP_REQUEST: 'HTTP 请求',
    SERVICE_API: '服务接口',
    CONNECTOR: '连接器',
    VIDEO_GENERATION: '视频生成',
    SCHEDULE_TRIGGER: '定时任务',
    CODE: '代码执行',
    TEMPLATE_TRANSFORM: '模板转换',
    ANSWER: '直接回复',
    END: '结束',
  };
  return labels[type] || '节点';
}

function getDefaultDescription(type) {
  const descriptions = {
    START: '工作流开始节点',
    USER_INPUT: '用户输入节点',
    FILE_UPLOAD: '文件上传节点',
    LLM: '大语言模型节点',
    AGENT: 'Agent 智能体',
    KNOWLEDGE_RETRIEVAL: '知识库检索节点',
    QUESTION_CLASSIFIER: '文本分类节点',
    IF_ELSE: '条件判断分支节点',
    LOOP: '循环执行节点',
    ITERATION: '对列表变量的每一项执行子图',
    LIST_OPERATOR: '对列表变量做过滤/映射/排序等操作',
    VARIABLE_AGGREGATOR: '变量聚合节点',
    VARIABLE_ASSIGNER: '把上游变量写回到会话/环境变量',
    PARAMETER_EXTRACTOR: '从上游文本中抽取结构化参数',
    DOCUMENT_EXTRACTOR: '从文件变量中提取文本内容',
    HUMAN_INPUT: '暂停工作流等待人工审批或补充信息',
    APPROVAL: '暂停工作流并等待审批决定',
    WAIT_EVENT: '等待匹配 correlation key 的外部事件',
    SLEEP_UNTIL: '持久化等待并在指定时间恢复',
    HTTP_REQUEST: 'HTTP 请求节点',
    SERVICE_API: '调用内部服务接口（免鉴权）',
    CONNECTOR: '调用消息通道或自定义 Connector',
    VIDEO_GENERATION: '选择视频模型，持久化等待生成结果',
    SCHEDULE_TRIGGER: '按指定时间创建任务并触发目标工作流',
    CODE: '代码执行节点',
    TEMPLATE_TRANSFORM: '模板转换节点',
    ANSWER: '直接回复节点',
    END: '工作流结束节点',
  };
  return descriptions[type] || '节点描述';
}

function getDefaultConfig(type) {
  const configs = nodeCardForm;
  return langUtils.clone(configs[type]) || {};
}

function getNodeTypeLabel(type) {
  return getDefaultLabel(type);
}

function onNodesChange(changes: any[]) {
  for (const change of changes) {
    if (change.type === 'remove') {
      emit('nodeDelete', change.id);
    }
  }
}

function onEdgesChange(_changes) {
  // 由 vue-flow 内部处理，我们的自定义副作用（如保存历史）在 add/remove 处显式触发
}

function onConnect(params) {
  // Edge IDs differ between imported graphs and new connections. Compare the
  // executable route, preserving different branch handles to the same target.
  if (getEdges.value.some((edge) =>
    edge.source === params.source && edge.target === params.target &&
    (edge.sourceHandle ?? '') === (params.sourceHandle ?? ''),
  )) return;
  const newEdge = {
    id: `e${params.source}-${params.target}-${Date.now()}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    animated: true,
    style: {
      stroke: '#94a3b8',
      strokeWidth: 2,
    },
    type: 'custom',
    markerEnd: MarkerType.ArrowClosed,
  };
  addEdges([newEdge]);
  saveToHistory();
  resetGraph();
}

function onNodeClick(event) {
  selectedNode.value = event.node;
  // 不更新节点的选中状态，避免置灰效果
  emit('nodeClick', event.node);
}

function onNodeMouseEnter(event) {
  hoveredNode.value = event.node;
  // 高亮相关连接线
  highlightConnectedEdges(event.node.id, true);
}

function onNodeMouseLeave(event) {
  hoveredNode.value = null;
  // 取消高亮连接线
  highlightConnectedEdges(event.node.id, false);
}

function highlightConnectedEdges(nodeId, highlight) {
  edges.value = edges.value.map((edge) => {
    const isConnected = edge.source === nodeId || edge.target === nodeId;
    const isSelected = selectedEdge.value && selectedEdge.value.id === edge.id;

    return {
      ...edge,
      style: {
        ...edge.style,
        stroke: isSelected
          ? '#1d4ed8'
          : highlight && isConnected
            ? '#3b82f6'
            : '#6366f1',
        strokeWidth: isSelected ? 4 : highlight && isConnected ? 3 : 2,
      },
      animated: !isSelected, // Disable animation for selected edges
    };
  });
}

function onEdgeClick(event) {
  // Toggle selected edge
  if (selectedEdge.value && selectedEdge.value.id === event.edge.id) {
    // Deselect if clicking the same edge
    selectedEdge.value = null;
  } else {
    // Select new edge
    selectedEdge.value = event.edge;
  }

  // Update edges styling based on selection
  updateEdgeStyles();
}

function updateEdgeStyles() {
  edges.value = edges.value.map((edge) => {
    const isSelected = selectedEdge.value && selectedEdge.value.id === edge.id;

    return {
      ...edge,
      style: {
        ...edge.style,
        stroke: isSelected ? '#1d4ed8' : '#6366f1',
        strokeWidth: isSelected ? 4 : 2,
      },
      animated: !isSelected, // Disable animation for selected edges to make them solid
    };
  });
}

function onPaneClick() {
  selectedNode.value = null;
  selectedEdge.value = null;
  // 不需要更新节点状态，避免置灰效果
  updateEdgeStyles();
}

function updateNodeData(nodeData) {
  if (selectedNode.value) {
    updateNode(selectedNode.value.id, nodeData);
    selectedNode.value = { ...selectedNode.value, ...nodeData };
  }
}

function deleteNode() {
  if (selectedNode.value) {
    deleteNodeById(selectedNode.value.id);
  }
}

/** 按 id 删除节点：同时清理相关的边，start 节点保护 */
function deleteNodeById(nodeId) {
  if (!nodeId) return;
  const node = findNode(nodeId);
  if (!node) return;
  if (node.type === 'START') {
    langUtils.message({ code: 1, message: '开始节点不可删除' });
    return;
  }
  // 先删所有连着的边
  const relatedEdges = edges.value.filter(
    (e) => e.source === nodeId || e.target === nodeId,
  );
  if (relatedEdges.length > 0) {
    edges.value = edges.value.filter(
      (e) => e.source !== nodeId && e.target !== nodeId,
    );
  }
  removeNodes([nodeId]);
  if (selectedNode.value?.id === nodeId) {
    selectedNode.value = null;
    emit('nodeDelete', nodeId);
  }
  saveToHistory();
  resetGraph();
}

/** 按 id 复制节点：偏移 40px 生成新 id 的克隆 */
function duplicateNodeById(nodeId) {
  const original = findNode(nodeId);
  if (!original) return;
  if (original.type === 'START') {
    langUtils.message({ code: 1, message: '开始节点不可复制' });
    return;
  }
  const newId = langUtils.getId(original.type);
  const cloned = langUtils.clone(original);
  cloned.id = newId;
  cloned.data = { ...cloned.data, id: newId };
  cloned.position = {
    x: (original.position?.x || 0) + 40,
    y: (original.position?.y || 0) + 40,
  };
  cloned.selected = false;
  addNodes([cloned]);
  saveToHistory();
  resetGraph();
}

const running = ref(false);
const saving = ref(false);
const publishing = ref(false);

/** 校验工作流图；返回错误信息数组，为空数组表示通过 */
function validateWorkflow(): string[] {
  return validateWorkflowGraph({ nodes: nodes.value, edges: edges.value })
    .map((issue) => issue.message);
}

function getFlowInfo() {
  return {
    nodes: nodes.value,
    edges: dedupeEdges(edges.value),
    viewport: getViewport(),
  };
}

/** 清理旧版本设计器遗留的完全重复连线；不同分支 handle 仍保留。 */
function dedupeEdges(input: any[]): any[] {
  const seen = new Set<string>();
  return (input ?? []).filter((edge) => {
    const key = `${edge?.source ?? ''}\0${edge?.target ?? ''}\0${edge?.sourceHandle ?? ''}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** 顶部工具栏「试运行」：走 BackendAdapter.runWorkflow */
async function onRun() {
  if (running.value) return;
  const errors = validateWorkflow();
  if (errors.length > 0) {
    langUtils.message({ code: 1, message: `无法试运行：${errors.join('；')}` });
    return;
  }
  running.value = true;
  try {
    const graph = getFlowInfo();
    const res: any = await runWorkflow({ graph });
    langUtils.message(res?.data ?? { code: 0, message: '已提交试运行' });
    emit('run', { graph, response: res });
  } catch (err: any) {
    langUtils.message({ code: 1, message: err?.message || '试运行失败' });
  } finally {
    running.value = false;
  }
}

/**
 * 顶部工具栏「保存草稿」：走 BackendAdapter.saveWorkflow —— 必须携带 appId，
 * backend 会把 workflows 行 PK pin 到 appId，保证同一 app 反复保存都命中同一行。
 * 宿主组件（例如 AppDesignDrawer）负责把 :app-id 传下来；本组件不再自作聪明
 * 生成 UUID —— appId 缺失就直接向上抛，交由宿主处理。
 */
async function onSaveDraft() {
  if (saving.value) return;
  if (!props.appId) {
    langUtils.message({
      code: 1,
      message: '缺少 appId：请在宿主上传入 :app-id',
    });
    return;
  }
  saving.value = true;
  try {
    const graph = getFlowInfo();
    const res: any = await saveWorkflow({ appId: props.appId, graph });
    langUtils.message(res?.data ?? { code: 0, message: '已保存' });
    emit('save', { graph, response: res });
  } catch (err: any) {
    langUtils.message({ code: 1, message: err?.message || '保存失败' });
  } finally {
    saving.value = false;
  }
}

/** 顶部工具栏「发布」：走 BackendAdapter.publishWorkflow */
async function onPublish() {
  if (publishing.value) return;
  if (!props.appId) {
    langUtils.message({
      code: 1,
      message: '缺少 appId：请在宿主上传入 :app-id',
    });
    return;
  }
  const errors = validateWorkflow();
  if (errors.length > 0) {
    langUtils.message({
      code: 1,
      message: `无法发布：${errors.join('；')}`,
    });
    return;
  }
  publishing.value = true;
  try {
    const graph = getFlowInfo();
    const res: any = await publishWorkflow({ appId: props.appId, graph });
    langUtils.message(res?.data ?? { code: 0, message: '已发布' });
    emit('publish', { graph, response: res });
  } catch (err: any) {
    langUtils.message({ code: 1, message: err?.message || '发布失败' });
  } finally {
    publishing.value = false;
  }
}

function onConnectionPlusClick(event, sourceNodeId, handleId = null) {
  event.stopPropagation();

  // 直接获取点击位置的坐标
  const rect = event.target.getBoundingClientRect();
  const canvasRect = document
    .querySelector('.center-canvas')
    .getBoundingClientRect();

  // 面板大小估算
  const panelWidth = 280;
  const panelHeight = 400;

  // 使用点击位置的x坐标作为面板左侧位置
  let x = rect.left - canvasRect.left + rect.width / 2;

  // 使用点击位置的y坐标作为面板垂直中心位置
  let y = rect.top - canvasRect.top + rect.height / 2 - panelHeight / 2;

  // 确保面板不超出右边界
  if (x + panelWidth > canvasRect.width - 20) {
    x = canvasRect.width - panelWidth - 20;
  }

  // 确保面板不超出左边界
  if (x < 20) {
    x = 20;
  }

  // 确保面板不超出下边界
  if (y + panelHeight > canvasRect.height - 20) {
    y = canvasRect.height - panelHeight - 20;
  }

  // 确保面板不超出上边界
  if (y < 20) {
    y = 20;
  }

  nodeSelectorPosition.value = { x, y };
  sourceNodeForConnection.value = sourceNodeId;
  sourceHandleId.value = handleId; // 保存源连接点ID
  showNodeSelector.value = true;
}

function onNodeSelectorSelect(nodeType) {
  if (sourceNodeForConnection.value) {
    // Normal node creation (existing logic)
    const sourceNode = nodes.value.find(
      (n) => n.id === sourceNodeForConnection.value,
    );
    if (sourceNode) {
      const newNodeId = langUtils.getId(nodeType);

      // 从 sourceNode 右侧开始扫描，找到第一个不与已有节点重叠的位置
      const preferred = {
        x: (sourceNode.position?.x ?? 0) + 350,
        y: sourceNode.position?.y ?? 0,
      };
      const isOccupied = (x: number, y: number) =>
        nodes.value.some((n) => {
          if (n.id === sourceNode.id) return false;
          const nx = n.position?.x ?? 0;
          const ny = n.position?.y ?? 0;
          return Math.abs(nx - x) < 200 && Math.abs(ny - y) < 120;
        });
      let position = { ...preferred };
      if (isOccupied(position.x, position.y)) {
        // 上下扫
        for (let offset = 220; offset < 2000; offset += 220) {
          if (!isOccupied(preferred.x, preferred.y - offset)) {
            position = { x: preferred.x, y: preferred.y - offset };
            break;
          }
          if (!isOccupied(preferred.x, preferred.y + offset)) {
            position = { x: preferred.x, y: preferred.y + offset };
            break;
          }
        }
      }

      const newNode = {
        id: newNodeId,
        type: nodeType,
        position,
        data: {
          id: newNodeId,
          label: getDefaultLabel(nodeType),
          description: getDefaultDescription(nodeType),
          ...getDefaultConfig(nodeType),
        },
      };

      // 添加节点
      addNodes([newNode]);

      // 创建连接
      // 关键点：
      //   1. edge.id 里带上 sourceHandle，避免"同一个 source node、不同 case 分支"
      //      的多条边生成同一个 id（旧的 `e{src}-{tgt}` 会撞 id，VueFlow 后加入的边
      //      会静默复用先前那条的 sourceHandle，视觉上就变成"从上一个 case 处出线"）。
      //   2. 明确写死 targetHandle: null（不是 undefined），让 VueFlow 用目标节点默认
      //      的 target handle，而不是漂到未命名/最近的一个 handle 上。
      const sourceHandleForEdge = sourceHandleId.value ?? null;
      const newEdge = {
        id: `e${sourceNodeForConnection.value}${sourceHandleForEdge ? `.${sourceHandleForEdge}` : ''}-${newNodeId}`,
        source: sourceNodeForConnection.value,
        target: newNodeId,
        sourceHandle: sourceHandleForEdge,
        targetHandle: null,
        animated: true,
        style: { stroke: '#94a3b8', strokeWidth: 2 },
        type: 'custom',
        markerEnd: MarkerType.ArrowClosed,
      };

      // addNodes 是同步入 store，但新节点的 handle DOM 尚未挂载 —— 此时 addEdges
      // 会用 VueFlow 里"没这个 handle"的 fallback 位置绘制边（对 condition 这种
      // 多 handle 节点，fallback 常常落在节点顶部/最靠近上级节点的锚点，看起来就
      // 像新边被拉回上级去了）。等一个 tick 让新节点 DOM 挂载，然后 addEdges，
      // 最后再显式 updateNodeInternals 让 source（可能是多 handle 的条件节点）与
      // target 都重新测一遍 bounding rect。
      const sourceIdForEdge = sourceNodeForConnection.value;
      nextTick(() => {
        addEdges([newEdge]);
        updateNodeInternals([sourceIdForEdge, newNodeId]);
      });
      saveToHistory();
    }
  }

  // 关闭选择器
  closeNodeSelector();

  setTimeout(() => {
    resetGraph();
  }, 200);
}

function closeNodeSelector() {
  showNodeSelector.value = false;
  sourceNodeForConnection.value = null;
  sourceHandleId.value = null;
  hoveredNodeType.value = null;
  nodeSearchQuery.value = '';
}

function onNodeItemMouseEnter(event, nodeData) {
  hoveredNodeType.value = {
    ...nodeData,
    position: {
      x: event.currentTarget.offsetLeft,
      y: event.currentTarget.offsetTop,
    },
  };
}

function onNodeItemMouseLeave() {
  hoveredNodeType.value = null;
}

/** ------ 节点选择面板：搜索 + 拖动 ------ */
const nodeSearchQuery = ref('');

const filteredCategories = computed(() => {
  const q = nodeSearchQuery.value.trim().toLowerCase();
  if (!q) return nodeCategories.value;
  return nodeCategories.value
    .map((cat) => ({
      ...cat,
      nodes: cat.nodes.filter(
        (n: any) =>
          n.label.toLowerCase().includes(q) ||
          (n.description || '').toLowerCase().includes(q) ||
          n.type.toLowerCase().includes(q),
      ),
    }))
    .filter((cat) => cat.nodes.length > 0);
});

let dragState: {
  startX: number;
  startY: number;
  origX: number;
  origY: number;
} | null = null;

function onSelectorHeaderMouseDown(e: MouseEvent) {
  dragState = {
    startX: e.clientX,
    startY: e.clientY,
    origX: nodeSelectorPosition.value.x,
    origY: nodeSelectorPosition.value.y,
  };
  window.addEventListener('mousemove', onSelectorDragMove);
  window.addEventListener('mouseup', onSelectorDragEnd);
  e.preventDefault();
}

function onSelectorDragMove(e: MouseEvent) {
  if (!dragState) return;
  const dx = e.clientX - dragState.startX;
  const dy = e.clientY - dragState.startY;
  nodeSelectorPosition.value = {
    x: dragState.origX + dx,
    y: dragState.origY + dy,
  };
}

function onSelectorDragEnd() {
  dragState = null;
  window.removeEventListener('mousemove', onSelectorDragMove);
  window.removeEventListener('mouseup', onSelectorDragEnd);
}

function getNodeColor(node) {
  const colors = {
    start: '#10b981',
    llm: '#6366f1',
    knowledge_retrieval: '#8b5cf6',
    condition: '#f59e0b',
    http: '#06b6d4',
    code: '#84cc16',
    answer: '#f97316',
    end: '#ef4444',
  };
  return colors[node.type] || '#6b7280';
}

// 历史记录功能
function saveToHistory() {
  const currentState = {
    nodes: JSON.parse(JSON.stringify(nodes.value)),
    edges: JSON.parse(JSON.stringify(edges.value)),
  };

  // 如果当前不在历史记录末尾，删除后续记录
  if (historyIndex.value < history.value.length - 1) {
    history.value = history.value.slice(0, historyIndex.value + 1);
  }

  history.value.push(currentState);

  // 限制历史记录大小
  if (history.value.length > maxHistorySize) {
    history.value.shift();
  } else {
    historyIndex.value++;
  }
}

function undo() {
  if (canUndo.value) {
    historyIndex.value--;
    const state = history.value[historyIndex.value];
    nodes.value = JSON.parse(JSON.stringify(state.nodes));
    edges.value = JSON.parse(JSON.stringify(state.edges));
  }
}

function redo() {
  if (canRedo.value) {
    historyIndex.value++;
    const state = history.value[historyIndex.value];
    nodes.value = JSON.parse(JSON.stringify(state.nodes));
    edges.value = JSON.parse(JSON.stringify(state.edges));
  }
}

/**
 * 节点自动排列（分层 Sugiyama 风格）
 * 步骤：
 *   1. 分层：按最长路径把节点分到不同 rank（rank 越大越靠右）；
 *      强制 start 落到 rank 0、end 落到最右 rank。
 *   2. 减少交叉：多轮 barycenter（正向按父节点平均序、反向按子节点平均序）。
 *   3. 定位：每层 X 由前一层最大宽度 + 水平 gap 决定；
 *      每层内节点先按 order 排开，再取父节点平均 Y 作为初始 Y，
 *      最后按顺序做重叠推挤（下一节点若与前一节点重叠则往下推）。
 * 目标：连线尽量水平、不交叉；同层节点严格不重叠。
 */
function arrangeNodes() {
  const list = nodes.value;
  if (!list || list.length === 0) return;

  const H_GAP = 80; // 层与层之间水平 gap
  const V_GAP = 40; // 同层节点之间最小垂直 gap
  const START_X = 100;
  const BASELINE_Y = 200;
  const DEFAULT_W = 300;
  const DEFAULT_H = 140;

  const nodeMap = new Map();
  const outgoing = new Map();
  const incoming = new Map();
  list.forEach((n) => {
    nodeMap.set(n.id, n);
    outgoing.set(n.id, []);
    incoming.set(n.id, []);
  });
  edges.value.forEach((e) => {
    if (nodeMap.has(e.source) && nodeMap.has(e.target)) {
      outgoing.get(e.source).push(e.target);
      incoming.get(e.target).push(e.source);
    }
  });

  const widthOf = (id) => nodeMap.get(id)?.dimensions?.width || nodeMap.get(id)?.width || DEFAULT_W;
  const heightOf = (id) => nodeMap.get(id)?.dimensions?.height || nodeMap.get(id)?.height || DEFAULT_H;

  // 1. 分层：最长路径（有环时以 visiting 集合断环）
  const rank = new Map();
  const computeRank = (id, visiting) => {
    if (rank.has(id)) return rank.get(id);
    if (visiting.has(id)) return 0;
    visiting.add(id);
    const parents = incoming.get(id) || [];
    let r = 0;
    parents.forEach((p) => {
      r = Math.max(r, computeRank(p, visiting) + 1);
    });
    visiting.delete(id);
    rank.set(id, r);
    return r;
  };
  list.forEach((n) => computeRank(n.id, new Set()));

  // 强制 start 到 rank 0，end 到最右 rank
  const startNode = list.find((n) => n.type === 'START');
  if (startNode) rank.set(startNode.id, 0);
  let maxR = 0;
  rank.forEach((r) => {
    if (r > maxR) maxR = r;
  });
  const endNode = list.find((n) => n.type === 'END');
  if (endNode) rank.set(endNode.id, Math.max(maxR, rank.get(endNode.id) ?? 0));
  rank.forEach((r) => {
    if (r > maxR) maxR = r;
  });

  // 分组
  const ranks = new Map(); // rank -> [nodeId]
  list.forEach((n) => {
    const r = rank.get(n.id) ?? 0;
    if (!ranks.has(r)) ranks.set(r, []);
    ranks.get(r).push(n.id);
  });
  const rankKeys = [...ranks.keys()].sort((a, b) => a - b);

  // 每层初始按原 y 排序（尽量保留用户当前顺序意图）
  ranks.forEach((ids) => {
    ids.sort((a, b) => (nodeMap.get(a).position?.y ?? 0) - (nodeMap.get(b).position?.y ?? 0));
  });

  // 2. Barycenter 迭代减少交叉
  const orderInRank = new Map();
  const rebuildOrder = () => {
    orderInRank.clear();
    ranks.forEach((ids) => ids.forEach((id, i) => orderInRank.set(id, i)));
  };
  rebuildOrder();

  const barySort = (ids, neighborsFn) => {
    const scored = ids.map((id) => {
      const nbrs = neighborsFn(id).filter((n) => orderInRank.has(n));
      const bary = nbrs.length === 0
        ? (orderInRank.get(id) ?? 0)
        : nbrs.reduce((s, n) => s + orderInRank.get(n), 0) / nbrs.length;
      return { id, bary };
    });
    scored.sort((a, b) => a.bary - b.bary);
    return scored.map((s) => s.id);
  };

  for (let iter = 0; iter < 8; iter++) {
    for (let i = 1; i < rankKeys.length; i++) {
      const r = rankKeys[i];
      ranks.set(r, barySort(ranks.get(r), (id) => incoming.get(id) || []));
    }
    rebuildOrder();
    for (let i = rankKeys.length - 2; i >= 0; i--) {
      const r = rankKeys[i];
      ranks.set(r, barySort(ranks.get(r), (id) => outgoing.get(id) || []));
    }
    rebuildOrder();
  }

  // 3. 定位：先算每层 X，再按父节点平均 Y 定 Y，最后推挤解重叠
  const rankX = new Map();
  let currentX = START_X;
  for (const r of rankKeys) {
    rankX.set(r, currentX);
    const maxW = Math.max(...ranks.get(r).map((id) => widthOf(id)));
    currentX += maxW + H_GAP;
  }

  const positioned = new Map(); // nodeId -> { x, y, w, h }

  for (const r of rankKeys) {
    const ids = ranks.get(r);
    const x = rankX.get(r);
    // 先算每个节点的目标 Y（父节点中心平均，无父就落 BASELINE）
    const targets = ids.map((id) => {
      const h = heightOf(id);
      const parents = (incoming.get(id) || []).filter((p) => positioned.has(p));
      let centerY;
      if (parents.length > 0) {
        const sum = parents.reduce((s, p) => {
          const pp = positioned.get(p);
          return s + pp.y + pp.h / 2;
        }, 0);
        centerY = sum / parents.length;
      } else {
        centerY = BASELINE_Y + h / 2;
      }
      return { id, w: widthOf(id), h, topY: centerY - h / 2 };
    });

    // 按目标 Y 排序（严格保持在 barycenter 计算好的 order 之内会更少交叉，
    // 但目标 Y 排序在多数情况下与 order 一致，且能让连线更水平）
    targets.sort((a, b) => a.topY - b.topY);

    // 顺序推挤：如果当前节点顶部小于上一个底部 + V_GAP，则下推
    let prevBottom = -Infinity;
    for (const t of targets) {
      if (t.topY < prevBottom + V_GAP) t.topY = prevBottom + V_GAP;
      positioned.set(t.id, { x, y: t.topY, w: t.w, h: t.h });
      prevBottom = t.topY + t.h;
    }
  }

  // 4. 全局向 BASELINE_Y 居中（把当前 y 平均值移到 BASELINE_Y 附近，视觉更居中）
  const ys = [...positioned.values()].map((p) => p.y + p.h / 2);
  if (ys.length > 0) {
    const avg = ys.reduce((s, y) => s + y, 0) / ys.length;
    const dy = BASELINE_Y - avg;
    positioned.forEach((p) => (p.y += dy));
  }

  // 5. 写回
  list.forEach((n) => {
    const p = positioned.get(n.id);
    if (p) n.position = { x: p.x, y: p.y };
  });

  saveToHistory();
}

/** 供宿主页面（外部 NodeConfigCard）回写节点数据用 */
function patchNodeData(nodeId, dataPatch) {
  if (!nodeId || !dataPatch) return;
  const node = findNode(nodeId);
  if (!node) return;
  const merged = { ...node.data, ...dataPatch };
  updateNode(nodeId, { data: merged });
  if (selectedNode.value?.id === nodeId) {
    selectedNode.value = { ...selectedNode.value, data: merged };
  }
}

/** 右键点节点：弹出上下文菜单 */
function onNodeContextMenu(event: any) {
  if (props.readonly) return;
  event?.event?.preventDefault?.();
  const nativeEvent = event?.event as MouseEvent;
  contextMenu.value = {
    visible: true,
    x: nativeEvent.clientX,
    y: nativeEvent.clientY,
    nodeId: event?.node?.id ?? null,
  };
  selectedNode.value = event?.node ?? null;
}

function closeContextMenu() {
  if (contextMenu.value) contextMenu.value.visible = false;
}

function ctxDuplicate() {
  if (contextMenu.value?.nodeId) duplicateNodeById(contextMenu.value.nodeId);
  closeContextMenu();
}

function ctxCopy() {
  if (contextMenu.value?.nodeId) copyNodeById(contextMenu.value.nodeId);
  closeContextMenu();
}

function ctxPaste() {
  pasteNode();
  closeContextMenu();
}

function ctxDelete() {
  if (contextMenu.value?.nodeId) deleteNodeById(contextMenu.value.nodeId);
  closeContextMenu();
}

function ctxRun() {
  emit('run', { graph: getFlowInfo(), nodeId: contextMenu.value?.nodeId });
  closeContextMenu();
}

/** 内部节点剪贴板（不同 FlowDesigner 实例互不干扰） */
const clipboardNode = ref<any>(null);

function copyNodeById(nodeId: string) {
  const node = findNode(nodeId);
  if (!node) return;
  clipboardNode.value = langUtils.clone({
    type: node.type,
    data: node.data,
    position: node.position,
  });
  langUtils.message({ code: 0, message: '已复制节点' });
}

function pasteNode(atPosition?: { x: number; y: number }) {
  if (!clipboardNode.value) return;
  const src = clipboardNode.value;
  const newId = langUtils.getId(src.type);
  const pos = atPosition ?? {
    x: (src.position?.x ?? 200) + 40,
    y: (src.position?.y ?? 200) + 40,
  };
  const newNode = {
    id: newId,
    type: src.type,
    position: pos,
    data: { ...langUtils.clone(src.data), id: newId },
    selected: false,
  };
  addNodes([newNode]);
  saveToHistory();
  resetGraph();
}

/** 键盘快捷键：Del 删除、Ctrl+Z 撤销、Ctrl+Shift+Z / Ctrl+Y 重做 */
function onKeydown(e: KeyboardEvent) {
  // 用户在输入框里打字时忽略
  const tag = (e.target as HTMLElement)?.tagName?.toLowerCase();
  if (
    tag === 'input' ||
    tag === 'textarea' ||
    (e.target as HTMLElement)?.isContentEditable
  ) {
    return;
  }
  const mod = e.ctrlKey || e.metaKey;
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedNode.value) {
    e.preventDefault();
    deleteNodeById(selectedNode.value.id);
  } else if (mod && e.key.toLowerCase() === 'z' && !e.shiftKey) {
    e.preventDefault();
    undo();
  } else if (
    (mod && e.key.toLowerCase() === 'y') ||
    (mod && e.shiftKey && e.key.toLowerCase() === 'z')
  ) {
    e.preventDefault();
    redo();
  } else if (mod && e.key.toLowerCase() === 'd' && selectedNode.value) {
    e.preventDefault();
    duplicateNodeById(selectedNode.value.id);
  } else if (mod && e.key.toLowerCase() === 'c' && selectedNode.value) {
    e.preventDefault();
    copyNodeById(selectedNode.value.id);
  } else if (mod && e.key.toLowerCase() === 'v' && clipboardNode.value) {
    e.preventDefault();
    pasteNode();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));

defineExpose({
  getFlowInfo,
  reloadGraph,
  patchNodeData,
  deleteNodeById,
  duplicateNodeById,
  copyNodeById,
  pasteNode,
  undo,
  redo,
  validateWorkflow,
});
</script>

<template>
  <div class="dify-flow-designer" :class="{ 'wf-readonly': readonly }">
    <div class="main-layout">
      <!-- 中间流程设计区域 -->
      <div class="center-canvas">
        <!-- 顶部悬浮工具栏：撤销 / 重做 / 试运行 / 保存 / 发布 -->
        <div v-if="!readonly" class="wf-top-toolbar">
          <div class="wf-top-toolbar-group">
            <a-tooltip title="撤销 (Ctrl+Z)">
              <button
                class="wf-top-btn"
                :disabled="!canUndo"
                @click="undo"
              >
                <Icon name="undo" />
              </button>
            </a-tooltip>
            <a-tooltip title="重做 (Ctrl+Y)">
              <button
                class="wf-top-btn"
                :disabled="!canRedo"
                @click="redo"
              >
                <Icon name="redo" />
              </button>
            </a-tooltip>
          </div>
          <!-- 试运行 / 保存 / 发布 —— 只在独立宿主模式显示。抽屉宿主自己已经在
               右上角提供了同名按钮，这里就不重复展示，避免用户不知道点哪一个。 -->
          <template v-if="!embedded">
            <span class="wf-top-toolbar-divider" />
            <div class="wf-top-toolbar-group">
              <a-tooltip title="试运行">
                <a-button size="small" :loading="running" @click="onRun">
                  <template #icon>▶</template>
                  试运行
                </a-button>
              </a-tooltip>
              <a-tooltip title="保存草稿">
                <a-button size="small" :loading="saving" @click="onSaveDraft">
                  保存
                </a-button>
              </a-tooltip>
              <a-tooltip title="发布上线">
                <a-button
                  type="primary"
                  size="small"
                  :loading="publishing"
                  @click="onPublish"
                >
                  发布
                </a-button>
              </a-tooltip>
            </div>
          </template>
        </div>

        <!-- 底部状态栏 -->
        <div v-if="!readonly" class="wf-bottom-status">
          <span class="wf-status-item">
            <span class="wf-status-label">节点</span>
            <span class="wf-status-value">{{ nodes.length }}</span>
          </span>
          <span class="wf-status-item">
            <span class="wf-status-label">连线</span>
            <span class="wf-status-value">{{ edges.length }}</span>
          </span>
          <span class="wf-status-item">
            <span class="wf-status-label">缩放</span>
            <span class="wf-status-value">
              {{ Math.round((getViewport().zoom || 1) * 100) }}%
            </span>
          </span>
        </div>

        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="defaultViewport"
          :min-zoom="0.1"
          :max-zoom="2"
          :snap-to-grid="true"
          :snap-grid="[16, 16]"
          :nodes-draggable="!readonly"
          :nodes-connectable="!readonly"
          :elements-selectable="!readonly"
          fit-view-on-init
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
          @connect="onConnect"
          @node-click="onNodeClick"
          @node-context-menu="onNodeContextMenu"
          @node-mouse-enter="onNodeMouseEnter"
          @node-mouse-leave="onNodeMouseLeave"
          @edge-click="onEdgeClick"
          @pane-click="(e) => { closeContextMenu(); onPaneClick(); }"
          class="dify-flow"
        >
          <!-- 节点模板 —— slot 名与 backend NodeType 枚举 / 存储层 n.type
               保持一致（UPPER_SNAKE），画布 / 存储 / 引擎单一标识贯穿。 -->
          <template #node-START="props">
            <StartNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-USER_INPUT="props">
            <UserInputNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-FILE_UPLOAD="props">
            <FileUploadNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-LLM="props">
            <LLMNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-AGENT="props">
            <AgentNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-KNOWLEDGE_RETRIEVAL="props">
            <KnowledgeRetrievalNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-QUESTION_CLASSIFIER="props">
            <ClassifierNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-IF_ELSE="props">
            <ConditionNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-LOOP="props">
            <LoopNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-VARIABLE_AGGREGATOR="props">
            <VariableNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-HTTP_REQUEST="props">
            <HttpNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-SERVICE_API="props">
            <ServiceApiNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-CONNECTOR="props">
            <ConnectorNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-VIDEO_GENERATION="props">
            <VideoGenerationNode v-bind="props" @connection-plus-click="onConnectionPlusClick" @duplicate="duplicateNodeById" @delete="deleteNodeById" />
          </template>
          <template #node-SCHEDULE_TRIGGER="props">
            <ScheduleTriggerNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-CODE="props">
            <CodeNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-TEMPLATE_TRANSFORM="props">
            <TemplateNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-ANSWER="props">
            <AnswerNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-END="props">
            <EndNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-ITERATION="props">
            <IterationNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-PARAMETER_EXTRACTOR="props">
            <ParameterExtractorNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-LIST_OPERATOR="props">
            <ListOperatorNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-DOCUMENT_EXTRACTOR="props">
            <DocumentExtractorNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-HUMAN_INPUT="props">
            <DurableWaitNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>
          <template #node-APPROVAL="props"><DurableWaitNode v-bind="props" @connection-plus-click="onConnectionPlusClick" @duplicate="duplicateNodeById" @delete="deleteNodeById" /></template>
          <template #node-WAIT_EVENT="props"><DurableWaitNode v-bind="props" @connection-plus-click="onConnectionPlusClick" @duplicate="duplicateNodeById" @delete="deleteNodeById" /></template>
          <template #node-SLEEP_UNTIL="props"><DurableWaitNode v-bind="props" @connection-plus-click="onConnectionPlusClick" @duplicate="duplicateNodeById" @delete="deleteNodeById" /></template>
          <template #node-VARIABLE_ASSIGNER="props">
            <VariableAssignerNode
              v-bind="props"
              @connection-plus-click="onConnectionPlusClick"
              @duplicate="duplicateNodeById"
              @delete="deleteNodeById"
            />
          </template>

          <!-- 背景 -->
          <Background pattern-color="#e5e7eb" :gap="16" />

          <!-- 缩放控件（右下角悬浮） -->
          <div class="wf-zoom-cluster">
            <a-tooltip title="放大">
              <button class="wf-zoom-btn" @click="zoomIn">＋</button>
            </a-tooltip>
            <a-tooltip title="缩小">
              <button class="wf-zoom-btn" @click="zoomOut">－</button>
            </a-tooltip>
            <a-tooltip title="适应画布">
              <button class="wf-zoom-btn" @click="fitView">
                <Icon name="fit" />
              </button>
            </a-tooltip>
            <a-tooltip title="自动排布">
              <button class="wf-zoom-btn" @click="arrangeNodes">
                <Icon name="arrange" />
              </button>
            </a-tooltip>
          </div>

          <!-- 小地图 -->
          <MiniMap
            :node-color="getNodeColor"
            mask-color="rgba(15,23,42,0.06)"
            class="wf-minimap"
            position="bottom-right"
          />

          <!-- 自定义边类型 -->
          <template #edge-custom="edgeProps">
            <CustomEdge
              v-bind="edgeProps"
            />
          </template>
        </VueFlow>

        <!-- 节点右键上下文菜单 -->
        <div
          v-if="contextMenu && contextMenu.visible"
          class="wf-context-menu"
          :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
          @click.stop
        >
          <div class="wf-context-menu-item" @click="ctxRun">
            运行到此节点
          </div>
          <div class="wf-context-menu-divider" />
          <div class="wf-context-menu-item" @click="ctxCopy">
            复制
            <span class="wf-context-menu-shortcut">Ctrl+C</span>
          </div>
          <div
            class="wf-context-menu-item"
            :class="{ 'wf-context-menu-item-disabled': !clipboardNode }"
            @click="clipboardNode && ctxPaste()"
          >
            粘贴
            <span class="wf-context-menu-shortcut">Ctrl+V</span>
          </div>
          <div class="wf-context-menu-item" @click="ctxDuplicate">
            复制副本
            <span class="wf-context-menu-shortcut">Ctrl+D</span>
          </div>
          <div class="wf-context-menu-divider" />
          <div
            class="wf-context-menu-item wf-context-menu-item-danger"
            @click="ctxDelete"
          >
            删除节点
            <span class="wf-context-menu-shortcut">Del</span>
          </div>
        </div>
      </div>

      <!-- 右侧配置面板 -->
      <!--      <div class="right-panel" v-if="selectedNode">
        <div class="panel-header">
          <h3>{{ getNodeTypeLabel(selectedNode.type) }}</h3>
          <button @click="selectedNode = null" class="close-btn">
            <Icon name="close" />
          </button>
        </div>
        <div class="panel-content">
          <NodeConfigCard
            :node="selectedNode"
            @update="updateNodeData"
            @delete="deleteNode"
          />
        </div>
      </div>-->
    </div>

    <!-- 节点选择器面板 -->
    <div
      v-if="showNodeSelector"
      class="wf-selector-container"
      :style="{
        left: `${nodeSelectorPosition.x}px`,
        top: `${nodeSelectorPosition.y}px`,
      }"
    >
      <!-- 左侧节点列表 -->
      <div class="wf-selector-panel">
        <!-- 可拖动的头部 -->
        <div class="wf-selector-header" @mousedown="onSelectorHeaderMouseDown">
          <div class="wf-selector-drag-handle" title="按住可拖动">
            <span class="wf-selector-drag-dots"></span>
          </div>
          <a-input
            v-model:value="nodeSearchQuery"
            placeholder="搜索节点"
            size="small"
            allow-clear
            style="flex: 1"
            @mousedown.stop
            @keydown.stop
          />
          <button
            class="wf-selector-close-btn"
            title="关闭"
            @click.stop="closeNodeSelector"
          >
            <Icon name="close" />
          </button>
        </div>
        <div class="wf-selector-content">
          <div
            v-if="filteredCategories.length === 0"
            class="wf-selector-empty"
          >
            没有匹配的节点
          </div>
          <div
            v-for="category in filteredCategories"
            :key="category.title"
            class="wf-selector-category"
          >
            <div class="wf-selector-category-title">
              <Icon :name="category.icon" />
              <span>{{ category.title }}</span>
            </div>
            <div class="wf-selector-nodes">
              <div
                v-for="node in category.nodes"
                :key="node.type"
                class="wf-selector-node"
                @click="onNodeSelectorSelect(node.type)"
                @mouseenter="onNodeItemMouseEnter($event, node)"
                @mouseleave="onNodeItemMouseLeave"
              >
                <div
                  class="wf-selector-node-icon"
                  :class="`icon-${node.type}`"
                >
                  <Icon :name="node.icon" />
                </div>
                <div class="wf-selector-node-label">{{ node.label }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧预览卡片 -->
      <div
        v-if="hoveredNodeType"
        class="wf-selector-preview"
        :style="{
          top: `${hoveredNodeType.position?.y || 0}px`,
        }"
      >
        <div class="wf-selector-preview-header">
          <div
            class="wf-selector-node-icon"
            :class="`icon-${hoveredNodeType.type}`"
          >
            <Icon :name="hoveredNodeType.icon" />
          </div>
          <div class="wf-selector-preview-title">
            {{ hoveredNodeType.label }}
          </div>
        </div>
        <div class="wf-selector-preview-desc">
          {{ hoveredNodeType.description }}
        </div>
      </div>
    </div>

    <!-- 遮罩层 -->
    <div
      v-if="showNodeSelector"
      class="wf-selector-overlay"
      @click="closeNodeSelector"
    ></div>
  </div>
</template>

<style scoped>
.dify-flow-designer {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 顶部工具栏 */
.top-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  height: 60px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.toolbar-left,
.toolbar-center,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-run {
  background: #10b981;
  color: white;
}

.btn-run:hover {
  background: #059669;
}

.btn-debug {
  background: #f59e0b;
  color: white;
}

.btn-debug:hover {
  background: #d97706;
}

.btn-publish {
  background: #6366f1;
  color: white;
}

.btn-publish:hover {
  background: #4f46e5;
}

.btn-save {
  background: #e5e7eb;
  color: #374151;
}

.btn-save:hover {
  background: #d1d5db;
}

.btn-share {
  background: #3b82f6;
  color: white;
}

.btn-share:hover {
  background: #2563eb;
}

/* 主布局 */
.main-layout {
  display: flex;
  flex: 1;
  min-height: 0; /* 允许在列 flex 中被压缩，防止子级 100% 高度被撑满溢出 */
  overflow: hidden;
}

/* 左侧面板 */
.left-panel {
  width: 280px;
  background: white;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.node-categories {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.category-section {
  margin-bottom: 14px;
}

.category-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  margin-bottom: 12px;
}

.node-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  /* border: 1px solid #e5e7eb; */
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
}

.node-item:hover {
  border-color: #6366f1;
  background: #f8fafc;
}

.node-item span {
  font-size: 14px;
  color: #374151;
}

/* 中间画布 */
.center-canvas {
  flex: 1;
  position: relative;
  background: #f9fafb;
}

.dify-flow {
  width: 100%;
  height: 100%;
  background: #f8fafc;
}

/* 顶部悬浮工具栏（保存 / 发布 / 试运行 / 撤销 / 重做） */
.wf-top-toolbar {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(8px);
}

.wf-top-toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wf-top-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #4b5563;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.wf-top-btn:hover:not(:disabled) {
  background: #f3f4f6;
  color: #1f2937;
}

.wf-top-btn:disabled {
  color: #d1d5db;
  cursor: not-allowed;
}

.wf-top-btn :deep(.icon) {
  width: 16px;
  height: 16px;
}

.wf-top-toolbar-divider {
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: #e5e7eb;
}

/* 底部状态栏 */
.wf-bottom-status {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 5px 10px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(6px);
  font-size: 11px;
}

.wf-status-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wf-status-label {
  color: #9ca3af;
}

.wf-status-value {
  font-weight: 600;
  color: #4b5563;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

/* 节点右键上下文菜单 */
.wf-context-menu {
  position: fixed;
  z-index: 20;
  min-width: 160px;
  padding: 4px 0;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.wf-context-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 8px 12px;
  font-size: 13px;
  color: #1f2937;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-context-menu-item:hover {
  background: #f3f4f6;
}

.wf-context-menu-item-danger {
  color: #dc2626;
}

.wf-context-menu-item-danger:hover {
  background: #fef2f2;
}

.wf-context-menu-item-disabled {
  color: #d1d5db;
  cursor: not-allowed;
}

.wf-context-menu-item-disabled:hover {
  background: transparent;
}

.wf-context-menu-divider {
  height: 1px;
  margin: 4px 0;
  background: #f3f4f6;
}

.wf-context-menu-shortcut {
  color: #9ca3af;
  font-size: 12px;
}

/* 遗留：.right-panel / .panel-header / .close-btn 相关 CSS 已随内嵌右侧面板一起移除，
 * 右侧配置面板现在由宿主页面控制，不再是 FlowDesigner 内部结构 */

/* Vue Flow 样式覆盖 */
.dify-flow :deep(.vue-flow__background) {
  /*background-color: #1f1f1f;*/
}

.dify-flow :deep(.vue-flow__controls) {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.dify-flow :deep(.vue-flow__minimap) {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.dify-flow :deep(.vue-flow__edge) {
  z-index: 1000;
}

.dify-flow :deep(.vue-flow__edge-path) {
  stroke: #6366f1;
  stroke-width: 2;
  fill: none;
}

.dify-flow :deep(.vue-flow__edge.animated .vue-flow__edge-path) {
  stroke-dasharray: 5;
  animation: dashdraw 0.5s linear infinite;
}

.dify-flow :deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke: #4f46e5;
  stroke-width: 3;
}

.dify-flow :deep(.vue-flow__connection-line) {
  stroke: #6366f1;
  stroke-width: 2;
  stroke-dasharray: 5, 5;
}

.dify-flow :deep(.vue-flow__handle) {
  width: 12px;
  height: 12px;
  border: 2px solid #ffffff;
  border-radius: 50%;
}

.dify-flow :deep(.vue-flow__handle.connectingfrom) {
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.3);
}

.dify-flow :deep(.vue-flow__handle.valid) {
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.3);
}

@keyframes dashdraw {
  to {
    stroke-dashoffset: -10;
  }
}

/* 节点选择器面板样式 */
.wf-selector-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.15);
  z-index: 9999;
}

.wf-selector-container {
  position: fixed;
  display: flex;
  gap: 8px;
  z-index: 10000;
}

.wf-selector-panel {
  display: flex;
  flex-direction: column;
  width: 280px;
  max-height: min(70vh, 600px);
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.2);
  overflow: hidden;
}

.wf-selector-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: #f9fafb;
  border-bottom: 1px solid #f0f0f0;
  cursor: grab;
  user-select: none;
}

.wf-selector-header:active {
  cursor: grabbing;
}

.wf-selector-drag-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 14px;
  height: 20px;
  color: #9ca3af;
}

.wf-selector-drag-dots {
  position: relative;
  width: 6px;
  height: 10px;
  background:
    radial-gradient(circle at 25% 25%, currentColor 1px, transparent 1.5px),
    radial-gradient(circle at 75% 25%, currentColor 1px, transparent 1.5px),
    radial-gradient(circle at 25% 75%, currentColor 1px, transparent 1.5px),
    radial-gradient(circle at 75% 75%, currentColor 1px, transparent 1.5px);
  background-size: 100% 100%;
}

.wf-selector-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 24px;
  height: 24px;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-selector-close-btn:hover {
  background: #e5e7eb;
  color: #1f2937;
}

.wf-selector-content {
  flex: 1;
  min-height: 0;
  padding: 8px 10px;
  overflow-y: auto;
}

.wf-selector-empty {
  padding: 20px 8px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

.wf-selector-category {
  margin-bottom: 12px;
}

.wf-selector-category:last-child {
  margin-bottom: 0;
}

.wf-selector-category-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.wf-selector-nodes {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wf-selector-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.wf-selector-node:hover {
  background: #eef2ff;
}

.wf-selector-node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 6px;
}

.wf-selector-node-icon :deep(.icon) {
  width: 14px;
  height: 14px;
}

.wf-selector-node-label {
  font-size: 13px;
  color: #1f2937;
}

.wf-selector-preview {
  position: absolute;
  left: 288px;
  width: 260px;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  transform: translateY(-8px);
  pointer-events: none;
}

.wf-selector-preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.wf-selector-preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.wf-selector-preview-desc {
  font-size: 12px;
  line-height: 1.4;
  color: #6b7280;
}

/* 彩色图标样式 —— class 名对齐当前 UPPER_SNAKE 的 node.type
 * (模板里是 `icon-${node.type}`)，调色板沿用旧版本，避免面板被显示成一片黑。 */
.icon-START {
  background: #dcfce7;
  color: #16a34a;
}
.icon-USER_INPUT {
  background: #dbeafe;
  color: #2563eb;
}
.icon-FILE_UPLOAD {
  background: #f3e8ff;
  color: #9333ea;
}
.icon-LLM {
  background: #dbeafe;
  color: #2563eb;
}
.icon-AGENT {
  background: #ede9fe;
  color: #7c3aed;
}
.icon-KNOWLEDGE_RETRIEVAL {
  background: #f3e8ff;
  color: #9333ea;
}
.icon-QUESTION_CLASSIFIER {
  background: #fef3c7;
  color: #d97706;
}
.icon-IF_ELSE {
  background: #fef3c7;
  color: #d97706;
}
.icon-LOOP {
  background: #ecfdf5;
  color: #059669;
}
.icon-ITERATION {
  background: #fce7f3;
  color: #db2777;
}
.icon-VARIABLE_AGGREGATOR {
  background: #e0f2fe;
  color: #0891b2;
}
.icon-VARIABLE_ASSIGNER {
  background: #d1fae5;
  color: #059669;
}
.icon-PARAMETER_EXTRACTOR {
  background: #fef3c7;
  color: #d97706;
}
.icon-LIST_OPERATOR {
  background: #cffafe;
  color: #0891b2;
}
.icon-DOCUMENT_EXTRACTOR {
  background: #cffafe;
  color: #0891b2;
}
.icon-HUMAN_INPUT {
  background: #fef3c7;
  color: #f59e0b;
}
.icon-HTTP_REQUEST {
  background: #e0f2fe;
  color: #0891b2;
}
.icon-SERVICE_API {
  background: #ccfbf1;
  color: #0d9488;
}
.icon-SCHEDULE_TRIGGER {
  background: #fef3c7;
  color: #d97706;
}
.icon-CODE {
  background: #f0fdf4;
  color: #16a34a;
}
.icon-TEMPLATE_TRANSFORM {
  background: #fef2f2;
  color: #dc2626;
}
.icon-ANSWER {
  background: #fff7ed;
  color: #ea580c;
}
.icon-END {
  background: #fef2f2;
  color: #dc2626;
}

/* 滚动条样式 */
.wf-selector-content::-webkit-scrollbar {
  width: 6px;
}

.wf-selector-content::-webkit-scrollbar-track {
  background: transparent;
}

.wf-selector-content::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.wf-selector-content::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

/* 保留旧滚动条选择器以向后兼容（如仍有历史遗留） */
.selector-content::-webkit-scrollbar {
  width: 6px;
}

.selector-content::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 3px;
}

.selector-content::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.selector-content::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* 右侧预览卡片样式 */
.node-preview-card {
  position: absolute;
  left: 240px;
  width: 220px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 10001;
  pointer-events: none;
}

/* 右下角缩放/操作控件 */
.wf-zoom-cluster {
  position: absolute;
  right: 12px;
  bottom: 130px; /* 让出小地图空间 */
  z-index: 8;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(6px);
}

.wf-zoom-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #4b5563;
  font-size: 14px;
  line-height: 1;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.wf-zoom-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.wf-zoom-btn :deep(.icon) {
  width: 14px;
  height: 14px;
}

/* 小地图容器 */
:deep(.vue-flow__minimap.wf-minimap) {
  right: 12px !important;
  bottom: 12px !important;
  width: 160px !important;
  height: 100px !important;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff !important;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.preview-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  flex-shrink: 0;
}

.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.preview-description {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

/* Workflow designer dark surfaces. Keep the canvas slightly darker than nodes. */
:global(.dark) .dify-flow-designer,
:global([data-theme='dark']) .dify-flow-designer {
  --wf-designer-bg: #061629;
  --wf-designer-surface: #081e36;
  --wf-designer-surface-hover: #0b2947;
  --wf-designer-border: #29445f;
  --wf-designer-text: #e7eef7;
  --wf-designer-muted: #8da3bb;
}

:global(.dark) .dify-flow-designer .center-canvas,
:global(.dark) .dify-flow-designer .dify-flow,
:global([data-theme='dark']) .dify-flow-designer .center-canvas,
:global([data-theme='dark']) .dify-flow-designer .dify-flow {
  background: var(--wf-designer-bg);
}

:global(.dark) .dify-flow-designer :is(.wf-top-toolbar, .wf-bottom-status, .wf-context-menu, .wf-selector-panel, .wf-selector-preview, .node-preview-card, .wf-zoom-cluster),
:global([data-theme='dark']) .dify-flow-designer :is(.wf-top-toolbar, .wf-bottom-status, .wf-context-menu, .wf-selector-panel, .wf-selector-preview, .node-preview-card, .wf-zoom-cluster) {
  color: var(--wf-designer-text);
  background: color-mix(in srgb, var(--wf-designer-surface) 96%, transparent);
  border-color: var(--wf-designer-border);
  box-shadow: 0 8px 24px rgb(0 0 0 / 30%);
}

:global(.dark) .dify-flow-designer :is(.wf-selector-header, .wf-top-btn:hover:not(:disabled), .wf-context-menu-item:hover, .wf-selector-node:hover, .wf-selector-close-btn:hover, .wf-zoom-btn:hover),
:global([data-theme='dark']) .dify-flow-designer :is(.wf-selector-header, .wf-top-btn:hover:not(:disabled), .wf-context-menu-item:hover, .wf-selector-node:hover, .wf-selector-close-btn:hover, .wf-zoom-btn:hover) {
  color: var(--wf-designer-text);
  background: var(--wf-designer-surface-hover);
}

:global(.dark) .dify-flow-designer :is(.wf-top-btn, .wf-status-value, .wf-context-menu-item, .wf-selector-node-label, .wf-selector-preview-title, .preview-title, .wf-zoom-btn),
:global([data-theme='dark']) .dify-flow-designer :is(.wf-top-btn, .wf-status-value, .wf-context-menu-item, .wf-selector-node-label, .wf-selector-preview-title, .preview-title, .wf-zoom-btn) {
  color: var(--wf-designer-text);
}

:global(.dark) .dify-flow-designer :is(.wf-status-label, .wf-context-menu-shortcut, .wf-selector-category-title, .wf-selector-preview-desc, .preview-description),
:global([data-theme='dark']) .dify-flow-designer :is(.wf-status-label, .wf-context-menu-shortcut, .wf-selector-category-title, .wf-selector-preview-desc, .preview-description) {
  color: var(--wf-designer-muted);
}

:global(.dark) .dify-flow-designer :is(.wf-top-toolbar-divider, .wf-context-menu-divider),
:global([data-theme='dark']) .dify-flow-designer :is(.wf-top-toolbar-divider, .wf-context-menu-divider) {
  background: var(--wf-designer-border);
}

:global(.dark) .dify-flow-designer :deep(.vue-flow__handle),
:global([data-theme='dark']) .dify-flow-designer :deep(.vue-flow__handle) {
  border-color: #081e36;
}

:global(.dark) .dify-flow-designer :deep(.vue-flow__minimap.wf-minimap),
:global([data-theme='dark']) .dify-flow-designer :deep(.vue-flow__minimap.wf-minimap) {
  background: #081e36 !important;
  border-color: var(--wf-designer-border);
}
</style>
