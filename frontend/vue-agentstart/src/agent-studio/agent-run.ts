/**
 * createAgentRunClient — back-compat factory for the durable /agent-runs API.
 *
 * The implementation (types, polling `watch`, resume/cancel) now lives in the
 * unified client's `runs` namespace (see `src/client/runs.ts`); this module
 * re-exports the public types and wraps `createAgentStartClient` so existing
 * imports keep working:
 *
 *   import { createAgentRunClient } from 'vue-agent-start';
 *   const runs = createAgentRunClient({ baseUrl: '/api' });
 *   for await (const batch of runs.watch(runId)) { … }
 *
 * New code can use `createAgentStartClient(...).runs` directly.
 */
import {
  createAgentStartClient,
  type AgentRunEvent,
  type AgentRunResponse,
  type AgentRunSnapshot,
  type AgentRunStatus,
  type AgentRunWatchOptions,
} from '../client';

export type {
  AgentRunEvent,
  AgentRunResponse,
  AgentRunSnapshot,
  AgentRunStatus,
  AgentRunWatchOptions,
};

export interface AgentRunClient {
  get(runId: string): Promise<AgentRunSnapshot>;
  events(runId: string, afterSequence?: number, limit?: number): Promise<AgentRunEvent[]>;
  resume(runId: string, approvalId: string, decision: 'APPROVE' | 'DENY'): Promise<AgentRunResponse>;
  resumeMany?(runId: string, decisions: Record<string, 'APPROVE' | 'DENY'>): Promise<AgentRunResponse>;
  cancel(runId: string): Promise<AgentRunSnapshot>;
  /** Polls durable events in sequence order until aborted or the run becomes terminal. */
  watch(runId: string, options?: AgentRunWatchOptions): AsyncGenerator<AgentRunEvent[], AgentRunSnapshot>;
  /** Resolves for a terminal state or WAITING_APPROVAL, whichever occurs first. */
  waitForSettled(runId: string, options?: AgentRunWatchOptions): Promise<AgentRunSnapshot>;
}

export interface AgentRunClientOptions {
  /** Host proxy prefix; `/agent-start` is appended automatically. Default `/api`. */
  baseUrl?: string;
  fetch?: typeof fetch;
  headers?: () => Record<string, string> | Promise<Record<string, string>>;
}

/** Creates a framework-neutral client usable by Vue components or Node-based hosts. */
export function createAgentRunClient(options: AgentRunClientOptions = {}): AgentRunClient {
  const client = createAgentStartClient({
    baseUrl: options.baseUrl,
    fetch: options.fetch,
    headers: options.headers,
  });
  return client.runs;
}
