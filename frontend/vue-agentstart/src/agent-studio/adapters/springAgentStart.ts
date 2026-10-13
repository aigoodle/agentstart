/**
 * createAgentStudioSpringBackend — returns the full callback bag the two
 * top-level agent pages (AgentAppsPage / AgentChatPage) need, backed by the
 * spring-agent-web-starter REST endpoints under {@code baseUrl}.
 *
 *   import {
 *     AgentAppsPage,
 *     createAgentStudioSpringBackend,
 *   } from 'vue-agent-start';
 *
 *   <AgentAppsPage :api="createAgentStudioSpringBackend({ baseUrl: '/api' })" />
 *
 * The bag is a superset of {@link AppStudioApi} (drawer / annotation / monitor
 * / api-key panels) with the extra CRUD + streaming methods the list page uses.
 * Hosts that want a totally custom backend can implement {@link AgentStudioApi}
 * directly instead.
 *
 * Implementation note (0.2): now a thin wrapper over the unified
 * {@link createAgentStartClient}. This also FIXED a long-standing drift: the
 * old hand-rolled version omitted the `/agent-start` controller namespace
 * (hitting `/api/agents` while every other adapter hit
 * `/api/agent-start/agents`). The backend mounts every controller under
 * `CONTROLLER_PATH_PREFIX = "/agent-start"`
 * (SpringAgentWebAutoConfiguration#addPathPrefix), so requests go to
 * `${baseUrl}/agent-start/...` like all the other adapters.
 */
import {
  createAgentStartClient,
  type AgentStartClient,
  type RunGraphRequest,
} from '../../client';
import type { AppStudioApi } from '../api';
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
  WorkflowEntityLite,
  WorkflowGraphLike,
} from './types';

// Re-export the wire types so existing `import { AgentEntity } from
// '…/adapters/springAgentStart'` style imports keep compiling.
export type {
  AgentEntity,
  AgentHistoryMessage,
  AgentStrategy,
  AgentToolView,
  AppMode,
  ChatRequest,
  ConversationSummary,
  CreateAgentRequest,
  DatasetLite,
  ModelLite,
  ToolLite,
  WorkflowEntityLite,
  WorkflowGraphLike,
} from './types';

/**
 * Full API bag consumed by AgentAppsPage / AgentChatPage. Extends
 * {@link AppStudioApi} so an instance passed to the page also drives the
 * drawer's built-in panels.
 */
export interface AgentStudioApi extends AppStudioApi {
  // agent CRUD
  listAgents(): Promise<AgentEntity[]>;
  getAgent(id: string): Promise<AgentEntity>;
  createAgent(req: CreateAgentRequest): Promise<AgentEntity>;
  updateAgent(id: string, req: CreateAgentRequest): Promise<AgentEntity>;
  deleteAgent(id: string): Promise<void>;

  // chat runtime
  fetchAgentTools(id: string): Promise<AgentToolView[]>;
  chatStream(id: string, req: ChatRequest): Promise<Response>;
  fetchConversations(agentId: string, limit?: number): Promise<ConversationSummary[]>;
  fetchHistoryMessages(
    agentId: string,
    conversationId: string,
    limit?: number,
  ): Promise<AgentHistoryMessage[]>;

  // drawer dropdown data
  listDatasets(): Promise<DatasetLite[]>;
  listModels(type?: string): Promise<ModelLite[]>;
  listTools(): Promise<ToolLite[]>;

  // workflow draft/publish (chatflow / workflow modes)
  getWorkflowDraft(appId: string): Promise<WorkflowEntityLite>;
  saveWorkflowDraft(appId: string, graph: WorkflowGraphLike): Promise<WorkflowEntityLite>;
  publishWorkflowDraft(appId: string): Promise<WorkflowEntityLite>;

  /** Raw base URL — components may append their own path (e.g. copy-curl). */
  baseUrl: string;
}

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

type FetchLike = typeof fetch;
type HeadersProvider = () =>
  | Record<string, string>
  | Promise<Record<string, string>>;

export interface AgentStudioAdapterOptions {
  /**
   * Host proxy prefix. Default: `/api`. The `/agent-start` controller
   * namespace is appended automatically (pass `namespace: ''` to disable).
   */
  baseUrl?: string;
  /** Escape hatch for hosts whose gateway strips the namespace. */
  namespace?: string;
  fetch?: FetchLike;
  headers?: HeadersProvider;
  onError?(msg: string): void;
  onSuccess?(msg: string): void;
  timeoutMs?: number;
}

export function createAgentStudioSpringBackend(
  opts: AgentStudioAdapterOptions = {},
): AgentStudioApi {
  const client: AgentStartClient = createAgentStartClient({
    baseUrl: opts.baseUrl,
    namespace: opts.namespace,
    fetch: opts.fetch,
    headers: opts.headers,
    onError: opts.onError,
    onSuccess: opts.onSuccess,
    timeoutMs: opts.timeoutMs,
  });

  return {
    // Back-compat: the old bag exposed the proxy base WITHOUT the namespace
    // (hosts used it for copy-curl display). Keep that contract.
    baseUrl: client.proxyBase,

    // ---- agent CRUD --------------------------------------------------------
    listAgents: () => client.agents.list(),
    listPublishedWorkflowOptions: () =>
      client.agents.listPublishedWorkflowOptions(),
    getAgent: (id) => client.agents.get(id),
    createAgent: (req) => client.agents.create(req),
    updateAgent: (id, req) => client.agents.update(id, req),
    deleteAgent: (id) => client.agents.remove(id),

    // ---- chat runtime ------------------------------------------------------
    fetchAgentTools: (id) => client.agents.listAgentTools(id),
    chatStream: (id, req) => client.agents.chatStream(id, req),
    fetchConversations: (agentId, limit = 100) =>
      client.agents.listConversations(agentId, limit),
    fetchHistoryMessages: (agentId, conversationId, limit = 500) =>
      client.agents.listHistoryMessages(agentId, conversationId, limit),

    // ---- drawer dropdown data ---------------------------------------------
    listDatasets: () => client.agents.listDatasetsLite().catch(() => []),
    listModels: (type = 'LLM') => client.agents.listModelsLite(type).catch(() => []),
    listTools: () => client.agents.listToolsLite().catch(() => []),

    // ---- workflow draft/publish -------------------------------------------
    getWorkflowDraft: (appId) => client.workflows.getDraft(appId),
    saveWorkflowDraft: (appId, graph) => client.workflows.saveDraft(appId, graph),
    publishWorkflowDraft: (appId) => client.workflows.publishDraft(appId),

    // ---- workflow execution (debug panel) ---------------------------------
    // 一次性试运行：此前缺失，宿主用本工厂时调试面板执行是 no-op。
    runWorkflowGraph: (payload) =>
      client.workflows.runGraph(payload as RunGraphRequest),
    // SSE 流式试运行：返回原始 Response，面板逐节点消费。
    runWorkflowGraphStream: (payload, opts) =>
      client.workflows.runGraphStream(payload as RunGraphRequest, opts),

    // ---- AppStudioApi bag (drawer panels) ---------------------------------
    listConversations: (appId, limit = 100) =>
      client.agents.listStudioConversations(appId, limit),
    fetchHistory: (appId, conversationId, limit = 500) =>
      client.agents.listStudioHistory(appId, conversationId, limit),

    listAnnotations: (appId) => client.agents.listAnnotations(appId),
    createAnnotation: (appId, req) => client.agents.createAnnotation(appId, req),
    updateAnnotation: (appId, id, req) => client.agents.updateAnnotation(appId, id, req),
    deleteAnnotation: (appId, id) => client.agents.deleteAnnotation(appId, id),

    fetchAppMetrics: (appId) => client.agents.fetchAppMetrics(appId),
    fetchLlmUsage: () => client.agents.fetchLlmUsage(),
    fetchRecentLlmCalls: (limit = 50) => client.agents.fetchRecentLlmCalls(limit),
    fetchLlmTrend: (range) => client.agents.fetchLlmTrend(range),

    listApiKeys: (appId) => client.agents.listApiKeys(appId),
    createApiKey: (appId, name) => client.agents.createApiKey(appId, name),
    renameApiKey: (appId, id, name) => client.agents.renameApiKey(appId, id, name),
    deleteApiKey: (appId, id) => client.agents.deleteApiKey(appId, id),
  };
}
