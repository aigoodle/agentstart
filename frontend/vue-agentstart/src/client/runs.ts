/**
 * `client.runs` — the durable `/agent-runs` execution API: snapshot reads,
 * event polling, approval resume, cancel, and an async-generator `watch`.
 *
 * Framework-neutral and SSR-safe (no DOM at import time), so Node hosts can
 * drive runs too.
 */
import type { HttpCore } from './core';

/** Stable runtime protocol exposed by spring-agent-start's /agent-runs API. */
export type AgentRunStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMED_OUT'
  | 'MAX_ITERATIONS';

export interface AgentRunSnapshot {
  runId: string;
  tenantId: string;
  agentId: string;
  conversationId?: string;
  status: AgentRunStatus;
  definitionJson?: string;
  requestJson?: string;
  responseJson?: string;
  error?: string;
  version: number;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AgentRunEvent {
  eventId: string;
  runId: string;
  sequence: number;
  type: string;
  payloadJson?: string;
  createdAt?: string;
}

export interface AgentRunResponse {
  runId: string;
  conversationId?: string;
  status: 'COMPLETED' | 'AWAITING_APPROVAL' | 'FAILED' | 'MAX_ITERATIONS';
  text?: string;
  error?: string;
  pendingApproval?: { approvalId: string; toolName: string; toolInput: string };
  pendingApprovals?: Array<{ approvalId: string; toolName: string; toolInput: string }>;
}

export interface AgentRunWatchOptions {
  afterSequence?: number;
  limit?: number;
  pollIntervalMs?: number;
  signal?: AbortSignal;
}

const TERMINAL_STATUSES = new Set<AgentRunStatus>([
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'TIMED_OUT',
  'MAX_ITERATIONS',
]);

export interface RunsNamespace {
  get(runId: string): Promise<AgentRunSnapshot>;
  events(runId: string, afterSequence?: number, limit?: number): Promise<AgentRunEvent[]>;
  resume(
    runId: string,
    approvalId: string,
    decision: 'APPROVE' | 'DENY',
  ): Promise<AgentRunResponse>;
  resumeMany(
    runId: string,
    decisions: Record<string, 'APPROVE' | 'DENY'>,
  ): Promise<AgentRunResponse>;
  cancel(runId: string): Promise<AgentRunSnapshot>;
  /** Polls durable events in sequence order until aborted or terminal. */
  watch(
    runId: string,
    options?: AgentRunWatchOptions,
  ): AsyncGenerator<AgentRunEvent[], AgentRunSnapshot>;
  /** Resolves on a terminal state or WAITING_APPROVAL, whichever first. */
  waitForSettled(runId: string, options?: AgentRunWatchOptions): Promise<AgentRunSnapshot>;
}

function clampLimit(limit: number | undefined): number {
  return Math.min(1000, Math.max(1, limit ?? 200));
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason ?? new DOMException('Aborted', 'AbortError'));
      return;
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal?.reason ?? new DOMException('Aborted', 'AbortError'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

export function createRunsNamespace(core: HttpCore): RunsNamespace {
  function base(runId: string): string {
    return `/agent-runs/${encodeURIComponent(runId)}`;
  }

  async function get(runId: string): Promise<AgentRunSnapshot> {
    return core.request<AgentRunSnapshot>(base(runId));
  }

  async function events(
    runId: string,
    afterSequence = 0,
    limit?: number,
  ): Promise<AgentRunEvent[]> {
    const q = `?afterSequence=${Math.max(0, afterSequence)}&limit=${clampLimit(limit)}`;
    return core.request<AgentRunEvent[]>(`${base(runId)}/events${q}`);
  }

  async function* watch(
    runId: string,
    options: AgentRunWatchOptions = {},
  ): AsyncGenerator<AgentRunEvent[], AgentRunSnapshot> {
    let sequence = Math.max(0, options.afterSequence ?? 0);
    const interval = Math.max(100, options.pollIntervalMs ?? 1_000);
    while (true) {
      if (options.signal?.aborted) {
        throw options.signal.reason ?? new DOMException('Aborted', 'AbortError');
      }
      const batch = await core.request<AgentRunEvent[]>(
        `${base(runId)}/events?afterSequence=${sequence}&limit=${clampLimit(options.limit)}`,
        undefined,
        { signal: options.signal },
      );
      if (batch.length > 0) {
        sequence = Math.max(sequence, ...batch.map((e) => e.sequence));
        yield batch;
      }
      const snapshot = await core.request<AgentRunSnapshot>(base(runId), undefined, {
        signal: options.signal,
      });
      if (TERMINAL_STATUSES.has(snapshot.status) || snapshot.status === 'WAITING_APPROVAL') {
        return snapshot;
      }
      await sleep(interval, options.signal);
    }
  }

  async function waitForSettled(
    runId: string,
    options?: AgentRunWatchOptions,
  ): Promise<AgentRunSnapshot> {
    const iterator = watch(runId, options);
    while (true) {
      const next = await iterator.next();
      if (next.done) return next.value;
    }
  }

  return {
    get,
    events,
    resume: (runId, approvalId, decision) =>
      core.request<AgentRunResponse>(`${base(runId)}/resume`, {
        method: 'POST',
        body: JSON.stringify({ approvalId, decision }),
      }),
    resumeMany: (runId, decisions) => {
      if (!Object.keys(decisions).length) throw new Error('At least one approval decision is required');
      return core.request<AgentRunResponse>(`${base(runId)}/resume`, {
        method: 'POST',
        body: JSON.stringify({ decisions }),
      });
    },
    cancel: (runId) =>
      core.request<AgentRunSnapshot>(`${base(runId)}/cancel`, { method: 'POST' }),
    watch,
    waitForSettled,
  };
}
