/**
 * Wire types shared by the agent-studio adapter and the unified client
 * (`createAgentStartClient().agents` / `.workflows`). They mirror the shapes
 * emitted by spring-agent-web-starter — copies of what used to live under
 * `#/api/agent-start/*` in host apps.
 */

export type AgentStrategy = 'FUNCTION_CALLING' | 'PLAN_EXECUTE' | 'REACT';

export type AppMode =
  | 'agent'
  | 'chat'
  | 'chatflow'
  | 'completion'
  | 'workflow';

export interface AgentEntity {
  dataAccessMode?: 'ALL' | 'RESTRICTED';
  id: string;
  tenantId?: string;
  appCode?: string;
  visibility?: 'GLOBAL' | 'PRIVATE' | 'TENANT_LIST';
  name: string;
  description?: string;
  icon?: string;
  iconBackground?: string;
  mode?: AppMode;
  instructions?: string;
  openingStatement?: string;
  suggestedQuestionsJson?: string;
  datasetIdsJson?: string;
  retrievalConfigJson?: string;
  workflowId?: string;
  modelName?: string;
  modelProvider?: string;
  runtimeType?: 'NATIVE' | 'SPRING_AI_ALIBABA' | string;
  runtimeRef?: string;
  modelSettingsJson?: string;
  strategy?: AgentStrategy;
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
  appCode?: string;
  visibility?: 'GLOBAL' | 'PRIVATE' | 'TENANT_LIST';
  name: string;
  description?: string;
  icon?: string;
  iconBackground?: string;
  mode?: AppMode;
  instructions?: string;
  openingStatement?: string;
  suggestedQuestions?: string[];
  datasetIds?: string[];
  retrievalConfig?: Record<string, unknown>;
  modelName?: string;
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
  published?: boolean;
  modelSettings?: Record<string, unknown>;
}

export interface AgentToolView {
  name: string;
  description: string;
  inputSchema?: string;
  label?: string;
  category?: string;
  icon?: string;
  provider?: string;
  connectorId?: string;
  actionId?: string;
  riskLevel?: string;
  configured?: boolean;
}

export interface AgentHistoryMessage {
  role: 'ASSISTANT' | 'SYSTEM' | 'USER';
  content: string;
}

export interface ConversationSummary {
  conversationId: string;
  name?: string;
  userId?: null | string;
  firstMessage?: string;
  updatedAt?: string;
  pinned?: boolean;
}

export interface ChatRequest {
  query: string;
  conversationId?: string;
  variables?: Record<string, unknown>;
  /** Maximum active execution duration in milliseconds; omitted means server default/unlimited. */
  timeoutMillis?: number;
}

export interface DatasetLite {
  id: string;
  name: string;
  description?: string;
}

export interface ModelLite {
  id: string;
  providerName: string;
  modelName: string;
  modelType?: string;
  enabled?: boolean;
  isDefault?: boolean;
}

export interface ToolLite {
  name: string;
  description: string;
  label?: string;
  category?: string;
  icon?: string;
  provider?: string;
  connectorId?: string;
  actionId?: string;
  riskLevel?: string;
  configured?: boolean;
}

export interface WorkflowGraphLike {
  nodes: Array<Record<string, unknown>>;
  edges: Array<Record<string, unknown>>;
  [k: string]: unknown;
}

export interface WorkflowEntityLite {
  id: string;
  appId?: string;
  name?: string;
  mode?: string;
  graph?: WorkflowGraphLike | Record<string, unknown> | null;
  version?: string;
  published?: boolean;
  createdAt?: string;
  updatedAt?: string;
}
