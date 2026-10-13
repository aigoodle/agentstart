import type { App } from 'vue';

import FlowDesigner from './workflow/FlowDesigner.vue';
import NodeConfig from './workflow/NodeConfig.vue';
import NodeConfigCard from './workflow/NodeConfigCard.vue';
import CustomEdge from './workflow/CustomEdge.vue';
import NodeHandle from './workflow/NodeHandle.vue';
import Icon from './workflow/Icon.vue';
import ChatIframePanel from './components/ChatIframePanel.vue';
import WorkflowDebugPanel from './components/WorkflowDebugPanel.vue';

import { useWorkflowStore } from './stores/workflow';
import { provideBackend, type BackendAdapter } from './adapter/backend';

import nodeCatalog from './workflow/utils/node_config';
import nodeCardForm from './workflow/utils/node_card_form';
import workflowUtils from './workflow/utils/workflow_utils';
import { validateWorkflowGraph } from './workflow/utils/graph_validator';
import { AgentStartUi } from '../ui';
// CSS is imported via src/style.css (shared across all entries)

export {
  FlowDesigner,
  NodeConfig,
  NodeConfigCard,
  CustomEdge,
  NodeHandle,
  Icon,
  ChatIframePanel,
  WorkflowDebugPanel,
  useWorkflowStore,
  provideBackend,
  nodeCatalog,
  nodeCardForm,
  workflowUtils,
  validateWorkflowGraph,
};
export type { WorkflowGraphIssue } from './workflow/utils/graph_validator';

export type { BackendAdapter } from './adapter/backend';
export type {
  ChatDebugVariable,
  ChatIframeConfig,
} from './components/chat-iframe-types';

export interface AgentFlowInstallOptions {
  /** 后端 API 适配器；不传则使用内置 no-op（所有请求返回空数据） */
  backend?: BackendAdapter;
  /** 全局注册核心组件（默认 true） */
  registerGlobal?: boolean;
}

/**
 * Vue 插件形式安装。
 *
 * @example
 *   import { createApp } from 'vue';
 *   import AgentFlow from '@agent-start/agent-flow';
 *   import '@agent-start/agent-flow/style.css';
 *
 *   const app = createApp(App);
 *   app.use(AgentFlow, {
 *     backend: {
 *       getCurrentModel: (t) => http.post(`/model/current/${t}`),
 *       getWorkflow: (q) => http.get('/workflow', { params: q }),
 *       publishWorkflow: (p) => http.post('/workflow/publish', p),
 *     },
 *   });
 */
export default {
  install(app: App, options: AgentFlowInstallOptions = {}) {
    app.use(AgentStartUi);
    if (options.backend) {
      provideBackend(app, options.backend);
    }
    if (options.registerGlobal !== false) {
      app.component('FlowDesigner', FlowDesigner);
      app.component('NodeConfigCard', NodeConfigCard);
    }
  },
};
