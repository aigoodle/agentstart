/**
 * Mirrors the backend REST payloads (see spring-agent-web AgentController).
 */

export type AgentStrategy = 'FUNCTION_CALLING' | 'PLAN_EXECUTE' | 'REACT';

/**
 * The "app kind" the user picks from the CreateAppModal. Dify calls these
 * "application types" — each is really the same underlying agent runtime
 * plus a canned config.
 *
 * <ul>
 *   <li>{@code chatflow} — 支持记忆的多轮对话工作流 (Dify parity). Uses the
 *       visual designer like {@code workflow}, but every run is scoped to a
 *       conversation so memory + streaming work.</li>
 *   <li>{@code workflow} — 面向单任务的可视化编排工作流. One-shot DAG run,
 *       no built-in memory.</li>
 * </ul>
 */
export type AppType =
  | 'agent'
  | 'chatbot'
  | 'chatflow'
  | 'text-generator'
  | 'workflow';

export interface AgentEntity {
  dataAccessMode?: 'ALL' | 'RESTRICTED';
  id: string;
  tenantId: string;
  /** Stable code used by internal SaaS chat integrations. */
  appCode?: string;
  /** PRIVATE / TENANT_LIST / GLOBAL. */
  visibility?: 'GLOBAL' | 'PRIVATE' | 'TENANT_LIST';
  name: string;
  /** Card summary text (Dify parity). */
  description?: string;
  /** Emoji or icon key. */
  icon?: string;
  /** Hex background tint for the icon square, e.g. "#EEF4FF". */
  iconBackground?: string;
  /**
   * Dify-parity app mode — determines which design surface the drawer opens:
   *   agent | chat | completion  → orchestrate + preview
   *   workflow | chatflow        → visual DAG designer
   */
  mode?: 'agent' | 'chat' | 'chatflow' | 'completion' | 'workflow';
  instructions?: string;
  openingStatement?: string;
  suggestedQuestionsJson?: string;
  datasetIdsJson?: string;
  /**
   * JSON blob controlling *how* retrieval runs against the attached datasets —
   * top-k, method (VECTOR/FULL_TEXT/HYBRID), rerank, etc. Independent of
   * {@link datasetIdsJson} which is just the list of dataset ids.
   */
  retrievalConfigJson?: string;
  /**
   * FK to `workflows.id` — the draft workflow's id, which equals the app id
   * by invariant. Never carries the graph itself; fetch it via the draft API.
   */
  workflowId?: string;
  /** Plain vendor model name (e.g. {@code qwen3.6-plus}). */
  modelName?: string;
  /** Provider key that owns {@link modelName} (e.g. {@code qwen}). */
  modelProvider?: string;
  runtimeType?: 'NATIVE' | 'SPRING_AI_ALIBABA' | string;
  runtimeRef?: string;
  /**
   * Serialised runtime overrides — the 模型设置 drawer payload
   * ({@code temperature}, {@code topP}, {@code maxTokens},
   * {@code thinkingMode}, …). Mirror of {@code app_model_configs.configs}
   * populated by the backend on read paths so the drawer's picker can
   * hydrate its parameter panel without a second request.
   */
  modelSettingsJson?: string;
  strategy: AgentStrategy;
  toolNamesJson?: string;
  skillIdsJson?: string;
  approvalToolsJson?: string;
  delegateAgentIdsJson?: string;
  maxIterations?: number;
  memoryEnabled?: boolean;
  memoryWindow?: number;
  published?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAgentRequest {
  tenantId?: string;
  name: string;
  instructions?: string;
  /** Plain vendor model name (e.g. {@code qwen3.6-plus}). */
  modelName?: string;
  /** Provider key that owns {@link modelName} (e.g. {@code qwen}). */
  modelProvider?: string;
  runtimeType?: 'NATIVE' | 'SPRING_AI_ALIBABA' | string;
  runtimeRef?: string;
  strategy?: AgentStrategy;
  toolNames?: string[];
  skillIds?: string[];
  approvalRequiredTools?: string[];
  delegateAgentIds?: string[];
  maxIterations?: number;
  memoryEnabled?: boolean;
  memoryWindow?: number;
}

/** Describes one of the picker cards in {@link CreateAppModal}. */
export interface AppTypeDescriptor {
  id: AppType;
  title: string;
  description: string;
  /** Emoji / short glyph used as the card's icon. */
  icon: string;
  /** Solid background color for the icon square. */
  iconBg: string;
  /**
   * A brief hint about the resulting agent's default config — e.g. which
   * strategy / tools it starts with.
   */
  hint: string;
  /** SVG preview markup (rendered as v-html). */
  previewSvg: string;
}

// ---------------------------------------------------------------------------
// Editor-page (Dify "编排/API/日志/监测") view types.
//
// Kept intentionally close to Dify's data shape so the panels can render the
// same set of controls; the host is responsible for mapping these to whatever
// its backend actually stores.
// ---------------------------------------------------------------------------

export type StudioTab = 'orchestrate' | 'api' | 'logs' | 'monitor';

/** One item in the left-side vertical nav of the studio shell. */
export interface StudioNavItem {
  id: StudioTab;
  title: string;
  /** Emoji / glyph fallback when {@link iconComponent} is not provided. */
  icon: string;
  /**
   * Preferred: an Ant Design (or any Vue) icon component. When set the shell
   * renders it via {@code <component :is>} instead of the emoji string.
   */
  iconComponent?: any;
}

/** A variable exposed to the prompt (matches Dify's 变量 section). */
export type VariableType = 'paragraph' | 'select' | 'text';

export interface PromptVariable {
  key: string;
  label: string;
  type: VariableType;
  required?: boolean;
  options?: string[];
}

/**
 * Rich variable descriptor edited by {@link VariableEditorModal}. Mirrors the
 * shape used by the flow designer's StartNode input-variables so the two
 * surfaces feel identical to the user.
 */
export type AgentVariableType =
  | 'array'
  | 'boolean'
  | 'file'
  | 'number'
  | 'object'
  | 'string';

export interface AgentVariable {
  /** Stable id — timestamp-generated on create, preserved on edit. */
  id?: number | string;
  /** Code name used in prompt templates ({@code {{#user.name#}}}). */
  name: string;
  /** Display label shown in forms. Falls back to {@link name} if empty. */
  label?: string;
  type: AgentVariableType;
  required?: boolean;
  description?: string;
  /** Default value shown to the user; free-form string for the modal. */
  defaultValue?: string;
}

/** A tool listing in the tool picker + attached-tools row. */
export interface StudioTool {
  name: string;
  /** Human-friendly action label; name remains the stable persisted tool id. */
  label?: string;
  description?: string;
  /** Group used to bucket tools in the picker (built-in / marketplace / mcp). */
  category?: string;
  /** Icon or emoji shown next to the name. */
  icon?: string;
  /** Total installs — used by Dify to sort by popularity. Optional. */
  installs?: number;
  provider?: string;
  connectorId?: string;
  actionId?: string;
  riskLevel?: string;
  configured?: boolean;
}

/** An attached knowledge base (dataset) row. */
export interface StudioKnowledge {
  id: string;
  name: string;
  documentCount?: number;
}

/** One row in the model-select dropdown at the top of the orchestrate tab. */
export interface StudioModelOption {
  id: string;
  label: string;
  /** Optional provider hint (rendered as a subtle badge). */
  provider?: string;
}

/** Message shown in the right-hand 调试与预览 chat log. */
export interface StudioChatMessage {
  role: 'assistant' | 'user';
  content: string;
  failed?: boolean;
  steps?: Array<Record<string, unknown>>;
}

/** One row in the 日志与标注 table. */
export interface StudioLogEntry {
  id: string;
  title: string;
  endUser?: string;
  status: 'FAILED' | 'PENDING' | 'SUCCESS';
  messageCount?: number;
  userFeedback?: string;
  adminFeedback?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** A single stat card on the 监测 dashboard. */
export interface StudioMonitorMetric {
  id: string;
  title: string;
  /** Big current-value shown at the top of the card. */
  value: number | string;
  /** Optional units — rendered inline after the value (e.g. "Token/秒"). */
  unit?: string;
  /** Optional hint text under the value (e.g. "总计 Token · ~$0.2659"). */
  subtitle?: string;
  /** Series of Y-values used to render the sparkline. */
  series: number[];
  /** X-axis labels aligned with series. */
  labels?: string[];
  /** Stroke color for the sparkline. */
  color?: string;
}
