/**
 * `client.workflows` — workflow draft/publish for apps plus the standalone
 * workflow endpoints the visual designer's host glue used to hand-roll
 * (`/workflows`, `/workflows/run-graph`, `/node-types`, `/workflow-examples`).
 */
import type {
  WorkflowEntityLite,
  WorkflowGraphLike,
} from '../agent-studio/adapters/types';
import { qs, type ExtraRequestOptions, type HttpCore } from './core';

/** Wire shape of a standalone workflow row (list/create). */
export interface WorkflowEntityWire {
  id: string;
  appId?: string;
  name?: string;
  mode?: string;
  graph?: WorkflowGraphLike | Record<string, unknown> | null;
  version?: string;
  published?: boolean;
  createdAt?: string;
  updatedAt?: string;
  [k: string]: unknown;
}

/** POST /workflows body. */
export interface CreateWorkflowRequest {
  appId: string;
  name?: string;
  mode?: string;
  graph?: WorkflowGraphLike | Record<string, unknown>;
  [k: string]: unknown;
}

/** POST /workflows/run-graph body. */
export interface RunGraphRequest {
  graph: WorkflowGraphLike | Record<string, unknown>;
  /** Workflow START-node input values. */
  data?: Record<string, unknown>;
  /** @deprecated Use data. Retained for older callers. */
  inputs?: Record<string, unknown>;
  [k: string]: unknown;
}

/** Wire shape returned by /workflows/run-graph. */
export interface RunGraphResultWire {
  runId?: string;
  success?: boolean;
  status?: WorkflowRunStatus;
  error?: string;
  outputs?: Record<string, unknown>;
  steps?: Array<Record<string, unknown>>;
  waitingNodeId?: string;
  waitRequest?: WorkflowWaitRequestWire;
  [k: string]: unknown;
}

export type WorkflowRunStatus =
  | 'RUNNING' | 'PAUSING' | 'PAUSED' | 'CANCELLING' | 'CANCELLED'
  | 'TIMED_OUT' | 'SUCCEEDED' | 'FAILED' | 'WAITING';

export type WorkflowWaitType = 'HUMAN_INPUT' | 'APPROVAL' | 'WAIT_EVENT' | 'SLEEP_UNTIL';

export interface WorkflowWaitRequestWire {
  type: WorkflowWaitType;
  correlationKey?: string;
  inputSchema?: Record<string, unknown>;
  expiresAt?: string;
  wakeAt?: string;
  resumeToken: string;
}

export interface WorkflowSignalRequest {
  resumeToken: string;
  eventId: string;
  payload?: Record<string, unknown>;
}
export interface WorkflowSignalResultWire {
  accepted: boolean;
  duplicate: boolean;
  runResult?: RunGraphResultWire;
}

/** Node-type metadata row from /node-types. */
export interface NodeTypeMetaWire {
  type: string;
  [k: string]: unknown;
}

/** Example workflow row from /workflow-examples. */
export interface WorkflowExampleWire {
  id?: string;
  name?: string;
  graph?: WorkflowGraphLike | Record<string, unknown>;
  [k: string]: unknown;
}

export interface WorkflowsNamespace {
  // ---- app-scoped draft/publish (agent-studio drawer)
  /** GET /apps/{appId}/workflow/draft. */
  getDraft(appId: string): Promise<WorkflowEntityLite>;
  /** PUT /apps/{appId}/workflow/draft. */
  saveDraft(appId: string, graph: WorkflowGraphLike): Promise<WorkflowEntityLite>;
  /** POST /apps/{appId}/workflow/publish. */
  publishDraft(appId: string): Promise<WorkflowEntityLite>;
  /** GET /apps/{appId}/workflows — draft plus immutable published snapshots. */
  listByApp(appId: string): Promise<WorkflowEntityWire[]>;
  /** POST /apps/{appId}/workflow/restore/{snapshotId}. */
  restoreSnapshot(appId: string, snapshotId: string): Promise<WorkflowEntityLite>;

  // ---- standalone workflow registry (visual designer)
  /** GET /workflows. */
  list(query?: Record<string, string | number | undefined>): Promise<WorkflowEntityWire[]>;
  /** POST /workflows — save/upsert (backend requires appId). */
  create(req: CreateWorkflowRequest): Promise<WorkflowEntityWire>;
  /** POST /workflows/run-graph — dry-run a graph without persisting. */
  runGraph(req: RunGraphRequest): Promise<RunGraphResultWire>;
  /**
   * POST /workflows/run-graph/stream — dry-run a graph and stream each node's
   * result as it completes. Resolves the raw SSE `Response`; the caller owns
   * body streaming (iterate with `readSseEvents`). Pass `opts.signal` to abort
   * mid-run — once the headers arrive no timeout covers the body, so
   * cancellation is only possible through the signal.
   */
  runGraphStream(req: RunGraphRequest, opts?: ExtraRequestOptions): Promise<Response>;
  /** POST /workflow-runs/{runId}/cancel. */
  cancelRun(runId: string, reason?: string): Promise<boolean>;
  /** POST /workflow-runs/{runId}/pause. */
  pauseRun(runId: string, reason?: string): Promise<boolean>;
  /** POST /workflow-runs/{runId}/resume. */
  resumeRun(runId: string): Promise<RunGraphResultWire>;
  /** Resume a durable wait by run id. eventId makes repeated callbacks idempotent. */
  signalRun(runId: string, req: WorkflowSignalRequest): Promise<WorkflowSignalResultWire>;
  /** Resume a durable wait by its correlation key. */
  signalEvent(correlationKey: string, req: WorkflowSignalRequest): Promise<WorkflowSignalResultWire>;
  /** GET /node-types — engine node metadata. */
  listNodeTypes(): Promise<NodeTypeMetaWire[]>;
  /** GET /workflow-examples. */
  listExamples(): Promise<WorkflowExampleWire[]>;
}

export function createWorkflowsNamespace(core: HttpCore): WorkflowsNamespace {
  return {
    getDraft: (appId) =>
      core.request<WorkflowEntityLite>(
        `/apps/${encodeURIComponent(appId)}/workflow/draft`,
      ),
    saveDraft: (appId, graph) =>
      core.request<WorkflowEntityLite>(
        `/apps/${encodeURIComponent(appId)}/workflow/draft`,
        { method: 'PUT', body: JSON.stringify({ graph }) },
      ),
    publishDraft: (appId) =>
      core.request<WorkflowEntityLite>(
        `/apps/${encodeURIComponent(appId)}/workflow/publish`,
        { method: 'POST', body: JSON.stringify({}) },
      ),
    listByApp: (appId) =>
      core.request<WorkflowEntityWire[]>(
        `/apps/${encodeURIComponent(appId)}/workflows`,
      ),
    restoreSnapshot: (appId, snapshotId) =>
      core.request<WorkflowEntityLite>(
        `/apps/${encodeURIComponent(appId)}/workflow/restore/${encodeURIComponent(snapshotId)}`,
        { method: 'POST' },
      ),

    list: (query) => core.request<WorkflowEntityWire[]>(`/workflows${qs(query ?? {})}`),
    create: (req) =>
      core.request<WorkflowEntityWire>('/workflows', {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    runGraph: (req) =>
      core.request<RunGraphResultWire>('/workflows/run-graph', {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    runGraphStream: (req, opts) =>
      core.raw(
        '/workflows/run-graph/stream',
        {
          method: 'POST',
          headers: { Accept: 'text/event-stream' },
          body: JSON.stringify(req),
        },
        opts,
      ),
    cancelRun: (runId, reason) =>
      core.request<boolean>(`/workflow-runs/${encodeURIComponent(runId)}/cancel`, {
        method: 'POST', body: JSON.stringify(reason ? { reason } : {}),
      }),
    pauseRun: (runId, reason) =>
      core.request<boolean>(`/workflow-runs/${encodeURIComponent(runId)}/pause`, {
        method: 'POST', body: JSON.stringify(reason ? { reason } : {}),
      }),
    resumeRun: (runId) =>
      core.request<RunGraphResultWire>(`/workflow-runs/${encodeURIComponent(runId)}/resume`, {
        method: 'POST', body: JSON.stringify({}),
      }),
    signalRun: (runId, req) =>
      core.request<WorkflowSignalResultWire>(`/workflow-runs/${encodeURIComponent(runId)}/signal`, {
        method: 'POST', body: JSON.stringify(req),
      }),
    signalEvent: (correlationKey, req) =>
      core.request<WorkflowSignalResultWire>(`/workflow-events/${encodeURIComponent(correlationKey)}`, {
        method: 'POST', body: JSON.stringify(req),
      }),
    listNodeTypes: () => core.request<NodeTypeMetaWire[]>('/node-types'),
    listExamples: () => core.request<WorkflowExampleWire[]>('/workflow-examples'),
  };
}
