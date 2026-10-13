/**
 * vue-agent-start — unified UI kit for spring-agent-start.
 *
 * All source lives under this one module. It absorbs what used to be four
 * separate packages, now organised as sub-folders under `./`:
 *
 *   • provider-hub  — model provider gallery + credential mgmt + shared picker
 *   • knowledge-hub — dataset CRUD + document ingest + recall testing
 *   • agent-studio  — app list + Dify-parity 编排/API/日志/监测 editor
 *   • agent-flow    — visual workflow / DAG designer for LLM nodes
 *
 * Style side-effects (knowledge-hub tokens, etc.) are imported eagerly so
 * callers don't have to remember a separate `import 'vue-agent-start/style.css'`.
 */

// -----------------------------------------------------------------------------
// Style tokens — the only side-effect import.
// -----------------------------------------------------------------------------
import './style.css';

export * from './ui';

export {
  AgentStartPlugin,
  mergeAgentStartHeaders,
  setAgentStartAppearance,
  useAgentStartConfig,
} from './config';
export type {
  AgentStartAppearanceOptions,
  AgentStartHeaders,
  AgentStartPluginOptions,
  AgentStartTheme,
  AgentStartVisual,
} from './config';

// -----------------------------------------------------------------------------
// client — the unified SDK every component talks to the backend through.
// Prefer createAgentStartClient in new code; the per-module factories below
// (createSpringAgentStartAdapter, useProviderHub, …) are back-compat wrappers.
// -----------------------------------------------------------------------------
export {
  AgentStartError,
  createAgentStartClient,
  qs,
  readSseEvents,
} from './client';
export type {
  AgentRunEvent,
  AgentRunResponse,
  AgentRunSnapshot,
  AgentRunStatus,
  AgentRunWatchOptions,
  AgentStartClient,
  AgentStartClientOptions,
  ChannelsNamespace,
  AgentsNamespace,
  DatasetWire,
  DocumentWire,
  FetchLike,
  HeadersProvider,
  HttpCore,
  KnowledgeNamespace,
  MaybePromise,
  ModelsNamespace,
  ProvidersNamespace,
  RunsNamespace,
  SegmentWire,
  SseEvent,
  UnauthorizedInfo,
  WorkflowsNamespace,
} from './client';
export {
  AgentStartClientKey,
  installAgentStartClient,
  provideAgentStartClient,
  useAgentStartClient,
} from './client/vue';

// -----------------------------------------------------------------------------
// provider-hub — model provider mgmt + shared model picker
// -----------------------------------------------------------------------------
export { default as CredentialForm } from './provider-hub/components/CredentialForm.vue';
export { default as DefaultModelsPanel } from './provider-hub/components/DefaultModelsPanel.vue';
export { default as ModelCardGrid } from './provider-hub/components/ModelCardGrid.vue';
export { default as ModelParameterDrawer } from './provider-hub/components/ModelParameterDrawer.vue';
export { default as ModelPickerPopover } from './provider-hub/components/ModelPickerPopover.vue';
export { default as ProviderApp } from './provider-hub/components/ProviderApp.vue';
export { default as ProviderCredentialModal } from './provider-hub/components/ProviderCredentialModal.vue';
export { default as ProviderGallery } from './provider-hub/components/ProviderGallery.vue';
export { default as ProviderHubShell } from './provider-hub/components/ProviderHubShell.vue';
export { default as ProviderIcon } from './provider-hub/components/ProviderIcon.vue';

export * from './provider-hub/composables/useProviderHub';
export * from './provider-hub/types';

// All three composables (useProviderHub / useKnowledge / useAgentStudio)
// export a structurally identical `HeadersLike`. Re-export it explicitly once
// so the `export *` trio below doesn't trip TS2308 (ambiguous star export).
export type { HeadersLike } from './provider-hub/composables/useProviderHub';

export * from './connector-hub';
export * from './channel-hub';
export * from './plugin-hub';
export * from './mcp-hub';
export * from './skill-hub';
export * from './tool-hub';
export * from './trigger-hub';

// -----------------------------------------------------------------------------
// knowledge-hub — dataset UI kit
// -----------------------------------------------------------------------------
export { default as KnowledgeApp } from './knowledge-hub/components/KnowledgeApp.vue';
export { default as KnowledgeHubApp } from './knowledge-hub/components/KnowledgeHubApp.vue';
export { default as CreateDatasetWizard } from './knowledge-hub/components/CreateDatasetWizard.vue';
export { default as DatasetCardGrid } from './knowledge-hub/components/DatasetCardGrid.vue';
export { default as DatasetDetailDrawer } from './knowledge-hub/components/DatasetDetailDrawer.vue';
export { default as DatasetSettingsPanel } from './knowledge-hub/components/DatasetSettingsPanel.vue';
export { default as DatasetSidebar } from './knowledge-hub/components/DatasetSidebar.vue';
export { default as DocumentChunksView } from './knowledge-hub/components/DocumentChunksView.vue';
export { default as DocumentTable } from './knowledge-hub/components/DocumentTable.vue';
export { default as RecallTestingPanelV2 } from './knowledge-hub/components/RecallTestingPanelV2.vue';

// Legacy widgets still used by /workflow KNOWLEDGE_RETRIEVAL node
export { default as DatasetPicker } from './knowledge-hub/components/DatasetPicker.vue';
export { default as DatasetPickerModal } from './knowledge-hub/components/DatasetPickerModal.vue';
export { default as HitTestingPanel } from './knowledge-hub/components/HitTestingPanel.vue';
export { default as RetrievedList } from './knowledge-hub/components/RetrievedList.vue';

export { createSpringAgentStartAdapter } from './knowledge-hub/adapters/springAgentStart';
export * from './knowledge-hub/composables/useKnowledge';
export * from './knowledge-hub/types';

// Shared retrieval-config UI — reusable outside the dataset drawer.
export { default as RetrievalConfigPopover } from './knowledge-hub/components/RetrievalConfigPopover.vue';
export { default as RetrievalMethodPicker } from './knowledge-hub/components/RetrievalMethodPicker.vue';

// -----------------------------------------------------------------------------
// agent-studio — app list + Dify-parity editor
// -----------------------------------------------------------------------------
export { default as AgentAppsPage } from './agent-studio/components/AgentAppsPage.vue';
export { default as AgentCardGrid } from './agent-studio/components/AgentCardGrid.vue';
export { default as AgentChatPage } from './agent-studio/components/AgentChatPage.vue';
export { default as AppDesignDrawer } from './agent-studio/components/AppDesignDrawer.vue';
export { default as CreateAppModal } from './agent-studio/components/CreateAppModal.vue';

export { default as AgentApiDocs } from './agent-studio/components/AgentApiDocs.vue';
export { default as AgentDebugPanel } from './agent-studio/components/AgentDebugPanel.vue';
export { default as AgentLogsPanel } from './agent-studio/components/AgentLogsPanel.vue';
export { default as AgentMonitorPanel } from './agent-studio/components/AgentMonitorPanel.vue';
export { default as AgentRunTimeline } from './agent-studio/components/AgentRunTimeline.vue';
export { default as AgentOrchestrate } from './agent-studio/components/AgentOrchestrate.vue';
export { default as AgentPromptEditor } from './agent-studio/components/AgentPromptEditor.vue';
export { default as AgentStudioShell } from './agent-studio/components/AgentStudioShell.vue';
export { default as AgentToolsPanel } from './agent-studio/components/AgentToolsPanel.vue';
export { default as AgentVariablesPanel } from './agent-studio/components/AgentVariablesPanel.vue';
export { default as SparkChart } from './agent-studio/components/SparkChart.vue';

export * from './agent-studio/composables/useAgentStudio';
export * from './agent-studio/types';
export * from './agent-studio/agent-run';

// AgentStudioApi adapter used by AgentChatPage —— 只导 factory + 接口,
// 避免 adapter 里重复定义的 AgentEntity/AgentStrategy/... 与 types.ts 冲突。
export { createAgentStudioSpringBackend } from './agent-studio/adapters/springAgentStart';
export type { AgentStudioApi } from './agent-studio/adapters/springAgentStart';

// AppStudioApi + built-in panels (LogAnnotation, Monitor, DrawerFlowDesigner)
// — a host can supply just the api callback bag and drop the drawer into a
// page without importing individual panels.
export * from './agent-studio/api';
export { default as LogAnnotationPanel } from './agent-studio/panels/LogAnnotationPanel.vue';
export { default as MonitorPanel } from './agent-studio/panels/MonitorPanel.vue';
export { default as DrawerFlowDesigner } from './agent-studio/panels/DrawerFlowDesigner.vue';

// -----------------------------------------------------------------------------
// agent-flow — visual workflow designer
//
// Also re-exports the Vue plugin as a named `AgentFlowPlugin` — callers that
// used `import AgentFlow from '@agent-start/agent-flow'; app.use(AgentFlow, …)`
// switch to `import { AgentFlowPlugin } from 'vue-agent-start'; app.use(…)`.
// -----------------------------------------------------------------------------
import FlowDesigner from './agent-flow/workflow/FlowDesigner.vue';
import NodeConfig from './agent-flow/workflow/NodeConfig.vue';
import NodeConfigCard from './agent-flow/workflow/NodeConfigCard.vue';
import CustomEdge from './agent-flow/workflow/CustomEdge.vue';
import NodeHandle from './agent-flow/workflow/NodeHandle.vue';
import Icon from './agent-flow/workflow/Icon.vue';

import { useWorkflowStore } from './agent-flow/stores/workflow';
import { provideBackend, type BackendAdapter } from './agent-flow/adapter/backend';

import nodeCatalog from './agent-flow/workflow/utils/node_config';
import nodeCardForm from './agent-flow/workflow/utils/node_card_form';
import workflowUtils from './agent-flow/workflow/utils/workflow_utils';
import { AgentStartUi } from './ui';

export {
  FlowDesigner,
  NodeConfig,
  NodeConfigCard,
  CustomEdge,
  NodeHandle,
  Icon,
  useWorkflowStore,
  provideBackend,
  nodeCatalog,
  nodeCardForm,
  workflowUtils,
};

export type { BackendAdapter };

export interface AgentFlowInstallOptions {
  backend?: BackendAdapter;
  registerGlobal?: boolean;
}

/** Vue plugin install for the workflow designer. */
export const AgentFlowPlugin = {
  install(app: import('vue').App, options: AgentFlowInstallOptions = {}) {
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
