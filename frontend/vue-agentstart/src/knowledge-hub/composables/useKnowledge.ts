/**
 * useKnowledge — legacy read-only knowledge API kept for back-compat.
 * Doesn't take a hard dep on axios / the host app's request layer so the
 * module can be pulled into any Vue 3 app.
 *
 * New code should prefer `createAgentStartClient().knowledge`. This
 * composable now delegates to a shared client instance; the historical
 * module-level setters ({@link setKnowledgeApiBase}, {@link setKnowledgeHeaders})
 * still work and recreate the shared client on change.
 *
 * Failing responses throw so callers can catch / toast as they see fit.
 */
import { createAgentStartClient, type AgentStartClient } from '../../client';
import type { Dataset, RetrieveRequest, RetrievedSegment } from '../types';

/**
 * 追加到每个请求的 header。传函数会在每次请求前重新求值，方便宿主接入
 * pinia store 里的 access-token —— 登录/退出时不用重挂 composable。
 */
export type HeadersLike =
  | Record<string, string>
  | (() => Promise<Record<string, string>> | Record<string, string>);

let apiBase = '/api';
let headersProvider: () => Promise<Record<string, string>> | Record<string, string> = () => ({});
let shared: AgentStartClient | null = null;

function client(): AgentStartClient {
  if (!shared) {
    shared = createAgentStartClient({
      baseUrl: apiBase,
      headers: () => headersProvider(),
    });
  }
  return shared;
}

export function setKnowledgeApiBase(base: string) {
  apiBase = base.replace(/\/+$/, '');
  shared = null;
}

/**
 * 设置每次请求都会拼上的 header（如 Authorization）。示例：
 *   setKnowledgeHeaders(() => ({
 *     Authorization: `Bearer ${useAccessStore().accessToken}`,
 *   }));
 */
export function setKnowledgeHeaders(headers: HeadersLike) {
  headersProvider = typeof headers === 'function' ? headers : () => headers;
  shared = null;
}

export function useKnowledge() {
  return {
    listDatasets: (tenantId?: string) =>
      client()
        .knowledge.listDatasets(tenantId)
        .then((rows) => rows as unknown as Dataset[]),
    getDataset: (id: string) =>
      client().knowledge.getDataset(id).then((r) => r as unknown as Dataset),
    retrieve: (datasetId: string, req: RetrieveRequest) =>
      client()
        .knowledge.retrieve(datasetId, req)
        .then((rows) => rows as unknown as RetrievedSegment[]),
  };
}
