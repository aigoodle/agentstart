/**
 * createAgentStartClient — the single SDK every Vue component in this package
 * talks to the spring-agent-start backend through.
 *
 *   import { createAgentStartClient } from 'vue-agent-start';
 *
 *   const client = createAgentStartClient({
 *     baseUrl: '/api',                          // host proxy prefix
 *     getAccessToken: () => accessStore.token,  // → Authorization: Bearer …
 *     getTenant: () => accessStore.tenantId,    // optional catalog hint; not an auth boundary
 *     onUnauthorized: () => router.push('/login'),
 *   });
 *
 *   client.models      // /models — installed-model registry, defaults, params
 *   client.providers   // /model-providers — gallery, credentials, catalog
 *   client.knowledge   // /datasets — documents, segments, retrieval
 *   client.agents      // /agents, /chat, /apps/{id}/*, /llmops
 *   client.workflows   // /apps/{id}/workflow/*, /workflows, /node-types
 *   client.runs        // /agent-runs — durable runs + event polling
 *
 * Components never concatenate `/agent-start` themselves — the namespace lives
 * here (see {@link AgentStartClientOptions.namespace}). Legacy factories
 * (`createSpringAgentStartAdapter`, `useProviderHub`, …) are now thin
 * back-compat wrappers around this client.
 *
 * Framework-neutral: constructing a client touches no DOM, so it is safe in
 * SSR and in Node-based hosts.
 */
import { createAgentsNamespace, type AgentsNamespace } from './agents';
import {
  AgentStartError,
  createHttpCore,
  qs,
  type AgentStartClientOptions,
  type ExtraRequestOptions,
  type FetchLike,
  type HeadersProvider,
  type HttpCore,
  type MaybePromise,
  type UnauthorizedInfo,
  type RequestCompletedEvent,
} from './core';
import {
  createKnowledgeNamespace,
  type KnowledgeNamespace,
} from './knowledge';
import { createModelsNamespace, type ModelsNamespace } from './models';
import { createProvidersNamespace, type ProvidersNamespace } from './providers';
import { createRunsNamespace, type RunsNamespace } from './runs';
import { createWorkflowsNamespace, type WorkflowsNamespace } from './workflows';
import { createTriggersNamespace, type TriggersNamespace } from './triggers';
import { createToolsNamespace, type ToolsNamespace } from './tools';
import { createSkillsNamespace, type SkillsNamespace } from './skills';
import { createConnectorsNamespace, type ConnectorsNamespace } from './connectors';
import { createChannelsNamespace, type ChannelsNamespace } from './channels';
import { readSseEvents, type SseEvent } from './sse';

export interface AgentStartClient {
  /** Resolved proxy prefix (trailing slashes stripped). */
  readonly proxyBase: string;
  /** Controller namespace, default `/agent-start`. */
  readonly namespace: string;
  /** `${proxyBase}${namespace}` — the root every path is relative to. */
  readonly rootUrl: string;
  /** The options the client was created with. */
  readonly options: Readonly<AgentStartClientOptions>;

  /** Envelope-unwrapping JSON request. */
  request<T>(path: string, init?: RequestInit, opts?: ExtraRequestOptions): Promise<T>;
  /** Raw `Response` (SSE streams etc.). */
  raw(path: string, init?: RequestInit, opts?: ExtraRequestOptions): Promise<Response>;
  /** Multipart upload with envelope response. */
  upload<T>(path: string, form: FormData, opts?: ExtraRequestOptions): Promise<T>;
  /** Current tenant id, when a `getTenant` provider is configured. */
  tenant(): Promise<string | undefined>;

  readonly models: ModelsNamespace;
  readonly providers: ProvidersNamespace;
  readonly knowledge: KnowledgeNamespace;
  readonly agents: AgentsNamespace;
  readonly workflows: WorkflowsNamespace;
  readonly runs: RunsNamespace;
  readonly triggers: TriggersNamespace;
  readonly connectors: ConnectorsNamespace;
  /** Long-running message gateways and their configured accounts. */
  readonly channels: ChannelsNamespace;
  readonly tools: ToolsNamespace;
  readonly skills: SkillsNamespace;
}

export function createAgentStartClient(
  options: AgentStartClientOptions = {},
): AgentStartClient {
  const core: HttpCore = createHttpCore(options);
  return {
    proxyBase: core.proxyBase,
    namespace: core.namespace,
    rootUrl: core.rootUrl,
    options: core.options,
    request: (path, init, opts) => core.request(path, init, opts),
    raw: (path, init, opts) => core.raw(path, init, opts),
    upload: (path, form, opts) => core.upload(path, form, opts),
    tenant: () => core.tenant(),
    models: createModelsNamespace(core),
    providers: createProvidersNamespace(core),
    knowledge: createKnowledgeNamespace(core),
    agents: createAgentsNamespace(core),
    workflows: createWorkflowsNamespace(core),
    runs: createRunsNamespace(core),
    triggers: createTriggersNamespace(core),
    connectors: createConnectorsNamespace(core),
    channels: createChannelsNamespace(core),
    tools: createToolsNamespace(core),
    skills: createSkillsNamespace(core),
  };
}

// ---------------------------------------------------------------------------
// Re-exports — the client module is the public home of the SDK types.
// ---------------------------------------------------------------------------
export { AgentStartError, qs, readSseEvents };
export type {
  AgentStartClientOptions,
  ExtraRequestOptions,
  FetchLike,
  HeadersProvider,
  HttpCore,
  MaybePromise,
  SseEvent,
  UnauthorizedInfo,
  RequestCompletedEvent,
};
export type { ConnectorsNamespace } from './connectors';
export type { ChannelsNamespace } from './channels';
export type { SkillEntity, SaveSkillRequest, SkillStatus, SkillsNamespace } from './skills';
export type {
  McpServerCatalogItem,
  SaveCustomToolRequest,
  ToolCatalogItem,
  ToolsNamespace,
} from './tools';
export * from '../connector-hub/types';
export * from '../channel-hub/types';
export type { ModelsNamespace, ListModelsOptions } from './models';
export type { ProvidersNamespace, PredefinedModelPayload } from './providers';
export type {
  KnowledgeNamespace,
  DatasetWire,
  DocumentWire,
  SegmentWire,
  RetrieveHitWire,
  RecallHistoryWire,
  IndexVersionWire,
  IngestionJobWire,
  RetrievalEvaluationReportWire,
} from './knowledge';
export type { AgentsNamespace, AppPermissionSettings } from './agents';
export type {
  WorkflowsNamespace,
  WorkflowEntityWire,
  CreateWorkflowRequest,
  RunGraphRequest,
  RunGraphResultWire,
  NodeTypeMetaWire,
  WorkflowExampleWire,
} from './workflows';
export type {
  RunsNamespace,
  AgentRunStatus,
  AgentRunSnapshot,
  AgentRunEvent,
  AgentRunResponse,
  AgentRunWatchOptions,
} from './runs';
export type {
  TriggersNamespace,
  TriggerType,
  TriggerScheduleConfig,
  CreateTriggerRequest as CreateScheduledTriggerRequest,
  TriggerWire,
  TriggerPage,
  TriggerPageOptions,
  TriggerInvocationWire,
} from './triggers';
