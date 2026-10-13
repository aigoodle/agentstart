/**
 * `client.agents` — agent CRUD + chat runtime + the per-app studio endpoints
 * (conversations, annotations, metrics, api-tokens) and `/llmops`.
 */
import type {
  StudioAnnotation,
  StudioAnnotationRequest,
  StudioApiKey,
  StudioAppMetrics,
  StudioConversationSummary,
  StudioHistoryMessage,
  StudioLlmCallRecord,
  StudioLlmTrendPoint,
  StudioLlmTrendRange,
  StudioLlmUsageStats,
} from '../agent-studio/api/types';
import type {
  AgentEntity,
  AgentHistoryMessage,
  AgentToolView,
  ChatRequest,
  ConversationSummary,
  CreateAgentRequest,
  DatasetLite,
  ModelLite,
  ToolLite,
} from '../agent-studio/adapters/types';
import { qs, type HttpCore } from './core';

export interface AppPermissionSettings {
  mode: 'ALL' | 'RESTRICTED';
  grants: Array<{ type: 'USER' | 'ROLE' | 'DEPARTMENT'; subjectId: string; includeDescendants: boolean }>;
}

export interface AgentsNamespace {
  getPermissions(id: string): Promise<AppPermissionSettings>;
  updatePermissions(id: string, settings: AppPermissionSettings): Promise<AppPermissionSettings>;
  // ---- agent CRUD
  list(): Promise<AgentEntity[]>;
  /** GET /apps/selectors/workflows — published workflow apps for target selectors. */
  listPublishedWorkflowOptions(): Promise<WorkflowAppOption[]>;
  get(id: string): Promise<AgentEntity>;
  create(req: CreateAgentRequest): Promise<AgentEntity>;
  update(id: string, req: CreateAgentRequest): Promise<AgentEntity>;
  remove(id: string): Promise<void>;
  listVersions(id: string): Promise<AgentVersion[]>;
  publishVersion(id: string, summary?: string): Promise<AgentVersion>;
  rollbackVersion(id: string, versionId: string, summary?: string): Promise<AgentVersion>;
  disableVersion(id: string, versionId: string): Promise<AgentVersion>;
  listRuntimes(): Promise<AgentRuntimeCapability[]>;

  // ---- chat runtime
  /** GET /agents/{id}/tools — the tool views the debug panel lists. */
  listAgentTools(id: string): Promise<AgentToolView[]>;
  /**
   * POST /agents/{id}/chat/stream — resolves the raw SSE `Response`; the
   * caller owns body streaming.
   */
  chatStream(id: string, req: ChatRequest): Promise<Response>;
  /** Administrator-only mutable draft preview. */
  previewStream(id: string, req: ChatRequest): Promise<Response>;
  /** POST /chat/conversations/{agentId}. */
  listConversations(agentId: string, limit?: number): Promise<ConversationSummary[]>;
  /** POST /chat/conversations/{agentId}/{conversationId}/messages. */
  listHistoryMessages(
    agentId: string,
    conversationId: string,
    limit?: number,
  ): Promise<AgentHistoryMessage[]>;

  // ---- drawer dropdown data (studio list endpoints)
  listDatasetsLite(): Promise<DatasetLite[]>;
  listModelsLite(type?: string): Promise<ModelLite[]>;
  listToolsLite(): Promise<ToolLite[]>;

  // ---- conversations / logs (studio panels, AppStudioApi shape)
  listStudioConversations(
    appId: string,
    limit?: number,
  ): Promise<StudioConversationSummary[]>;
  listStudioHistory(
    appId: string,
    conversationId: string,
    limit?: number,
  ): Promise<StudioHistoryMessage[]>;

  // ---- annotations
  listAnnotations(appId: string): Promise<StudioAnnotation[]>;
  createAnnotation(appId: string, req: StudioAnnotationRequest): Promise<StudioAnnotation>;
  updateAnnotation(
    appId: string,
    id: string,
    req: StudioAnnotationRequest,
  ): Promise<StudioAnnotation>;
  deleteAnnotation(appId: string, id: string): Promise<void>;

  // ---- monitor
  fetchAppMetrics(appId: string): Promise<StudioAppMetrics>;
  fetchLlmUsage(): Promise<StudioLlmUsageStats>;
  fetchRecentLlmCalls(limit?: number): Promise<StudioLlmCallRecord[]>;
  fetchLlmTrend(range: StudioLlmTrendRange): Promise<StudioLlmTrendPoint[]>;

  // ---- api tokens
  listApiKeys(appId: string): Promise<StudioApiKey[]>;
  createApiKey(appId: string, name?: string): Promise<StudioApiKey>;
  renameApiKey(appId: string, id: string, name: string): Promise<StudioApiKey>;
  deleteApiKey(appId: string, id: string): Promise<void>;
}

export interface WorkflowAppOption {
  appId: string;
  workflowId: string;
  name: string;
  icon?: string;
  iconBackground?: string;
  inputVariables?: Array<Record<string, unknown>>;
}

export interface AgentVersion {
  id: string;
  tenantId: string;
  appId: string;
  versionNumber: number;
  status: 'ACTIVE' | 'SUPERSEDED' | 'DISABLED';
  changeSummary?: string;
  publishedBy?: string;
  publishedAt: string;
  rollbackFromVersionId?: string;
  runtimeType?: string;
  runtimeRef?: string;
}

export interface AgentRuntimeCapability { type: string; nativeRuntime: boolean }

export function createAgentsNamespace(core: HttpCore): AgentsNamespace {
  function annotations(appId: string): string {
    return `/apps/${encodeURIComponent(appId)}/annotations`;
  }
  function apiTokens(appId: string): string {
    return `/apps/${encodeURIComponent(appId)}/api-tokens`;
  }

  return {
    getPermissions: (id) => core.request<AppPermissionSettings>(`/apps/${encodeURIComponent(id)}/permissions`),
    updatePermissions: (id, settings) => core.request<AppPermissionSettings>(`/apps/${encodeURIComponent(id)}/permissions`, {
      method: 'PUT', body: JSON.stringify(settings),
    }),
    list: () => core.request<AgentEntity[]>('/agents'),
    listPublishedWorkflowOptions: () =>
      core.request<WorkflowAppOption[]>('/apps/selectors/workflows'),
    get: (id) => core.request<AgentEntity>(`/agents/${encodeURIComponent(id)}`),
    create: (req) =>
      core.request<AgentEntity>('/agents', {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    update: (id, req) =>
      core.request<AgentEntity>(`/agents/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(req),
      }),
    remove: (id) =>
      core.request<void>(`/agents/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    listVersions: (id) =>
      core.request<AgentVersion[]>(`/agents/${encodeURIComponent(id)}/versions`),
    publishVersion: (id, summary) =>
      core.request<AgentVersion>(`/agents/${encodeURIComponent(id)}/versions/publish`, {
        method: 'POST', body: JSON.stringify({ summary }),
      }),
    rollbackVersion: (id, versionId, summary) =>
      core.request<AgentVersion>(`/agents/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/rollback`, {
        method: 'POST', body: JSON.stringify({ summary }),
      }),
    disableVersion: (id, versionId) =>
      core.request<AgentVersion>(`/agents/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/disable`, { method: 'POST' }),
    listRuntimes: () => core.request<AgentRuntimeCapability[]>('/agent-runtimes'),

    listAgentTools: (id) =>
      core.request<AgentToolView[]>(`/agents/${encodeURIComponent(id)}/tools`),
    chatStream: (id, req) =>
      core.raw(`/agents/${encodeURIComponent(id)}/chat/stream`, {
        method: 'POST',
        headers: { Accept: 'text/event-stream' },
        body: JSON.stringify(req),
      }),
    previewStream: (id, req) =>
      core.raw(`/agents/${encodeURIComponent(id)}/chat/preview/stream`, {
        method: 'POST',
        headers: { Accept: 'text/event-stream' },
        body: JSON.stringify(req),
      }),
    listConversations: (agentId, limit = 100) =>
      core.request<ConversationSummary[]>(
        `/chat/conversations/${encodeURIComponent(agentId)}`,
        { method: 'POST', body: JSON.stringify({ limit }) },
      ),
    listHistoryMessages: (agentId, conversationId, limit = 500) =>
      core.request<AgentHistoryMessage[]>(
        `/chat/conversations/${encodeURIComponent(agentId)}/${encodeURIComponent(conversationId)}/messages`,
        { method: 'POST', body: JSON.stringify({ limit }) },
      ),

    listDatasetsLite: () => core.request<DatasetLite[]>('/datasets'),
    listModelsLite: (type = 'LLM') =>
      core.request<ModelLite[]>(`/models${qs({ type })}`),
    listToolsLite: () => core.request<ToolLite[]>('/tools'),

    listStudioConversations: (appId, limit = 100) =>
      core.request<StudioConversationSummary[]>(
        `/chat/conversations/${encodeURIComponent(appId)}`,
        { method: 'POST', body: JSON.stringify({ limit }) },
      ),
    listStudioHistory: (appId, conversationId, limit = 500) =>
      core.request<StudioHistoryMessage[]>(
        `/chat/conversations/${encodeURIComponent(appId)}/${encodeURIComponent(conversationId)}/messages`,
        { method: 'POST', body: JSON.stringify({ limit }) },
      ),

    listAnnotations: (appId) => core.request<StudioAnnotation[]>(annotations(appId)),
    createAnnotation: (appId, req) =>
      core.request<StudioAnnotation>(annotations(appId), {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    updateAnnotation: (appId, id, req) =>
      core.request<StudioAnnotation>(`${annotations(appId)}/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(req),
      }),
    deleteAnnotation: (appId, id) =>
      core.request<void>(`${annotations(appId)}/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      }),

    fetchAppMetrics: (appId) =>
      core.request<StudioAppMetrics>(`/apps/${encodeURIComponent(appId)}/metrics`),
    fetchLlmUsage: () => core.request<StudioLlmUsageStats>('/llmops/total'),
    fetchRecentLlmCalls: (limit = 50) =>
      core.request<StudioLlmCallRecord[]>(`/llmops/recent${qs({ limit })}`),
    fetchLlmTrend: (range) =>
      core.request<StudioLlmTrendPoint[]>(`/llmops/trend${qs({ range })}`),

    listApiKeys: (appId) => core.request<StudioApiKey[]>(apiTokens(appId)),
    createApiKey: (appId, name) =>
      core.request<StudioApiKey>(apiTokens(appId), {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
    renameApiKey: (appId, id, name) =>
      core.request<StudioApiKey>(`${apiTokens(appId)}/${encodeURIComponent(id)}/rename`, {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
    deleteApiKey: (appId, id) =>
      core.request<void>(`${apiTokens(appId)}/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      }),
  };
}
