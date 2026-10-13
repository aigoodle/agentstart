/**
 * @agent-start/agent-studio
 *
 * Reusable agent-studio component + composable kit for spring-agent-start,
 * modeled after Dify's application studio.
 *
 * ── Application list side ────────────────────────────────────────────────
 *   AgentCardGrid   — Dify-style grid of agents with a leading "+新建" card
 *                     + inline chat/edit/share/delete actions surfaced as events.
 *   CreateAppModal  — 4-way picker with SVG preview per app type (chatbot /
 *                     agent / workflow / text-generator). Emits `select` with
 *                     the chosen type + a canonical default-config hint.
 *
 * ── Application editor side (mirrors Dify's 编辑页) ──────────────────────
 *   AgentStudioShell     — left-nav shell (agent header + 编排/API/日志/监测)
 *   AgentOrchestrate     — 编排 tab (提示词/变量/知识库/元数据过滤/工具/视觉)
 *   AgentPromptEditor    — Prompt textarea + variable insertion popover
 *   AgentVariablesPanel  — Variable rows (key / label / type / required)
 *   AgentToolsPanel      — Tool list + Dify-style picker popover
 *   AgentDebugPanel      — Right-side 调试与预览 chat pane
 *   AgentApiDocs         — 访问 API tab (base URL + auth + endpoint list)
 *   AgentLogsPanel       — 日志与标注 tab (filterable table + pagination)
 *   AgentMonitorPanel    — 监测 tab (metric cards backed by SparkChart)
 *   SparkChart           — Dependency-free SVG area/line chart
 *
 *   useAgentStudio()     — thin fetch client over /api/agent-start/agents.
 *   APP_TYPES            — the picker cards' data (icon / svg / hint).
 *
 * Consumers alias it as `@agent-start/agent-studio` (see vite.config.mts).
 */

// Application list side
export { default as AgentCardGrid } from './components/AgentCardGrid.vue';
export { default as AppDesignDrawer } from './components/AppDesignDrawer.vue';
export { default as CreateAppModal } from './components/CreateAppModal.vue';

// Application editor side — the Dify studio
export { default as AgentApiDocs } from './components/AgentApiDocs.vue';
export { default as ApiKeyManager } from './components/ApiKeyManager.vue';
export { default as AgentDebugPanel } from './components/AgentDebugPanel.vue';
export { default as AgentLogsPanel } from './components/AgentLogsPanel.vue';
export { default as AgentMonitorPanel } from './components/AgentMonitorPanel.vue';
export { default as AgentOrchestrate } from './components/AgentOrchestrate.vue';
export { default as AgentPromptEditor } from './components/AgentPromptEditor.vue';
export { default as AgentStudioShell } from './components/AgentStudioShell.vue';
export { default as AgentToolsPanel } from './components/AgentToolsPanel.vue';
export { default as AgentVariablesPanel } from './components/AgentVariablesPanel.vue';
export { default as SparkChart } from './components/SparkChart.vue';

export * from './composables/useAgentStudio';
export * from './types';

// Panels used by AppDesignDrawer's built-in tab content. Re-exported so a
// host that wants finer-grained composition (embed a single panel in its own
// page) can pull them individually. AppStudioApi is the callback bag every
// panel is wired against.
export { default as DrawerFlowDesigner } from './panels/DrawerFlowDesigner.vue';
export { default as LogAnnotationPanel } from './panels/LogAnnotationPanel.vue';
export { default as MonitorPanel } from './panels/MonitorPanel.vue';
export * from './api';

// Top-level pages (app list + standalone chat) + run timeline
export { default as AgentAppsPage } from './components/AgentAppsPage.vue';
export { default as AgentChatPage } from './components/AgentChatPage.vue';
export { default as AgentRunTimeline } from './components/AgentRunTimeline.vue';

// Durable /agent-runs client factory (back-compat wrapper over client.runs)
export * from './agent-run';

// AgentStudioApi adapter used by AgentAppsPage / AgentChatPage —— 只导
// factory + 接口, 避免 adapter 里重复定义的 AgentEntity/AgentStrategy/...
// 与 types.ts 经 `export *` 产生歧义冲突。
export { createAgentStudioSpringBackend } from './adapters/springAgentStart';
export type {
  AgentStudioAdapterOptions,
  AgentStudioApi,
} from './adapters/springAgentStart';
