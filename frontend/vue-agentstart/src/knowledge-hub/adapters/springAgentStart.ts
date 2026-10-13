/**
 * createSpringAgentStartAdapter — returns a fully-typed KnowledgeHubApi backed
 * by the spring-agent-start REST endpoints. External consumers of the backend
 * don't have to hand-write the 20-ish methods of the interface.
 *
 *   import { KnowledgeHubApp, createSpringAgentStartAdapter } from 'vue-agent-start'
 *
 *   const api = createSpringAgentStartAdapter({
 *     baseUrl: '/api',            // 宿主自己的代理前缀；/agent-start 命名空间内部拼
 *     fetch: window.fetch,        // or your host's fetch wrapper
 *     headers: () => ({ Authorization: `Bearer ${getToken()}` }),
 *     onError: (msg) => antdMessage.error(msg),
 *     onSuccess: (msg) => antdMessage.success(msg),
 *   })
 *
 *   <KnowledgeHubApp :api="api" />
 *
 * Hosts with a different backend implement KnowledgeHubApi directly.
 *
 * Implementation note: since 0.2 this adapter is a thin mapping layer over the
 * unified {@link createAgentStartClient} (`client.knowledge` / `client.models`)
 * — envelope handling, headers, timeouts and the `/agent-start` namespace all
 * live in the client now. Prefer `createAgentStartClient` in new code.
 */
import { createAgentStartClient, type AgentStartClient } from '../../client';
import type {
  Chunk,
  ChunkPreview,
  DatasetCardItem,
  DatasetSummary,
  DocMetadata,
  DocumentRow,
  IndexingTechnique,
  KnowledgeHubApi,
  ParsedDocument,
  RecallHit,
  RecentQuery,
} from '../types';
import type { DocumentWire, SegmentWire } from '../../client';

type FetchLike = typeof fetch;
type HeadersProvider = () => Record<string, string> | Promise<Record<string, string>>;

export interface SpringAgentStartAdapterOptions {
  /**
   * 宿主的代理前缀（默认 `/api`）。适配器内部会自动拼上 `/agent-start`
   * 命名空间 —— 那是 spring-agent-web 给控制器加的固定前缀，属于组件与
   * 后端约定的实现细节，宿主不用关心。所以最终请求 URL 是
   * `${baseUrl}/agent-start/xxx`。若代理不叫 /api，改成 `/xxx`；若完全直连
   * 后端（无代理），传 `''` 即可，最终就是 `/agent-start/xxx`。
   */
  baseUrl?: string;
  /** Optional custom fetch (e.g. host's axios wrapper turned into a fetch). */
  fetch?: FetchLike;
  /**
   * Extra headers to send on every call. Called for each request — return a
   * fresh token, tenant id, correlation id, etc. Can be async.
   */
  headers?: HeadersProvider;
  /** Called with the human-readable error message on any 4xx/5xx. */
  onError?(msg: string): void;
  /** Called with success messages emitted by the adapter itself. */
  onSuccess?(msg: string): void;
  /** Override the ⌘ 访问 API button behaviour. Default copies the dataset URL. */
  onCopyApi?(datasetId: string): void;
  /**
   * Wired to {@code KnowledgeHubApp}'s built-in "先注册 Embedding 模型"
   * empty-state nudge. The hub renders the card whenever the initial fetch
   * returns zero embedding models; clicking "去配置模型" fires this callback
   * so the host can navigate to its model-management route. Leaving it
   * undefined hides the button but keeps the informational card.
   */
  onGoToEmbeddingSetup?(): void;
  /**
   * Request timeout in ms. Default: 60_000. Uploads use `uploadTimeoutMs`.
   */
  timeoutMs?: number;
  uploadTimeoutMs?: number;
  /** Host observability hook; receives every knowledge/model HTTP completion. */
  onRequestCompleted?: import('../../client/core').AgentStartClientOptions['onRequestCompleted'];
}

// -- wire → view mapping helpers (unchanged from the pre-client adapter) ----

function parseJsonArray(value: unknown): string[] {
  if (!value) return [];
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function normalizeDoc(d: DocumentWire): DocumentRow {
  return {
    id: d.id,
    name: d.name,
    chunkMode: d.sourceType === 'text' ? '自定义' : (d.sourceType ?? ''),
    wordCount: d.wordCount ?? 0,
    hitCount: 0,
    uploadedAt: d.createdAt,
    status:
      d.status === 'COMPLETED'
        ? 'AVAILABLE'
        : d.status === 'FAILED'
          ? 'FAILED'
          : 'PROCESSING',
    enabled: !!d.enabled,
    parserName: d.parserName,
    mediaType: d.mediaType,
    pageCount: d.pageCount,
    blockCount: d.blockCount,
    parseWarnings: parseJsonArray(d.parseWarningsJson),
    fileSize: d.fileSize,
  };
}

function normalizeChunk(s: SegmentWire): Chunk {
  return {
    id: s.id,
    position: s.position ?? 0,
    content: s.content ?? '',
    tokenCount: s.tokenCount ?? 0,
    charCount: s.content?.length ?? 0,
    hitCount: 0,
    enabled: !!s.enabled,
    keywords: s.keywords
      ? String(s.keywords)
          .split(/\s+/)
          .filter((k) => k.length > 0)
      : [],
  };
}

export function createSpringAgentStartAdapter(
  opts: SpringAgentStartAdapterOptions = {},
): KnowledgeHubApi {
  const client: AgentStartClient = createAgentStartClient({
    baseUrl: opts.baseUrl,
    fetch: opts.fetch,
    headers: opts.headers,
    onError: opts.onError,
    onSuccess: opts.onSuccess,
    timeoutMs: opts.timeoutMs,
    uploadTimeoutMs: opts.uploadTimeoutMs,
    onRequestCompleted: opts.onRequestCompleted,
  });

  const api: KnowledgeHubApi = {
    // ---- datasets
    listDatasets: () =>
      client.knowledge.listDatasets().then((rows) =>
        rows.map((r) => ({
          id: r.id,
          name: r.name,
          description: r.description,
          documentCount: r.documentCount ?? 0,
          segmentCount: r.segmentCount ?? 0,
          indexingTechnique: r.indexingTechnique as IndexingTechnique,
          updatedAt: r.updatedAt,
        })),
      ),
    getDataset: (id) =>
      client.knowledge.getDataset(id).then((r) => r as unknown as DatasetSummary),
    createDataset: (req) =>
      client.knowledge
        .createDataset(req)
        .then((r) => r as unknown as DatasetCardItem),
    updateDataset: (id, patch) =>
      client.knowledge
        .updateDataset(id, patch)
        .then((r) => r as unknown as DatasetSummary),
    deleteDataset: (id) => client.knowledge.deleteDataset(id),

    // ---- documents
    listDocuments: (datasetId) =>
      client.knowledge.listDocuments(datasetId).then((rows) => rows.map(normalizeDoc)),
    uploadDocument: (datasetId, file) => client.knowledge.uploadDocument(datasetId, file),
    deleteDocument: (datasetId, docId) =>
      client.knowledge.deleteDocument(datasetId, docId),
    setDocumentEnabled: (datasetId, docId, enabled) =>
      client.knowledge.setDocumentEnabled(datasetId, docId, enabled).then(() => undefined),
    getParsedDocument: (datasetId, docId) =>
      client.knowledge.getParsedDocument(datasetId, docId) as Promise<ParsedDocument>,
    reparseDocument: (datasetId, docId) =>
      client.knowledge.reparseDocument(datasetId, docId).then(normalizeDoc),
    previewChunks: (file, rule, limit) =>
      client.knowledge.previewChunks(file, rule, limit).then((r) => r as ChunkPreview),

    // ---- segments
    listSegments: (datasetId, docId, page = 1, pageSize = 20) =>
      client.knowledge
        .listSegments(datasetId, docId, page, pageSize)
        .then((rows) => rows.map(normalizeChunk)),
    loadDocumentMetadata: async (datasetId, docId) => {
      const [doc, ds, segRows] = await Promise.all([
        client.knowledge.getDocument(datasetId, docId),
        client.knowledge.getDataset(datasetId),
        client.knowledge.listSegments(datasetId, docId, 1, 200),
      ]);
      let processRule: { template?: string; chunkTokens?: number } = {};
      try {
        if (ds?.processRuleJson) {
          processRule = JSON.parse(String(ds.processRuleJson));
        }
      } catch {
        processRule = {};
      }
      const totalChars = segRows.reduce((n, c) => n + (c.content?.length ?? 0), 0);
      const totalTokens = segRows.reduce((n, c) => n + (c.tokenCount ?? 0), 0);
      const meta: DocMetadata = {
        fileName: doc?.name,
        kind: doc?.sourceType,
        uploadedAt: doc?.createdAt,
        parsedAt: doc?.updatedAt,
        embeddedAt: doc?.updatedAt,
        chunkMode: processRule.template === 'PARENT_CHILD' ? '父子分段' : '自定义',
        chunkMaxSize: processRule.chunkTokens ?? 1024,
        totalChars,
        totalChunks: segRows.length,
        avgChunkChars:
          segRows.length > 0 ? Math.round(totalChars / segRows.length) : 0,
        avgEmbedMs: 2.5,
        totalTokens,
        parserName: doc?.parserName,
        mediaType: doc?.mediaType,
        pageCount: doc?.pageCount,
        blockCount: doc?.blockCount,
        parseWarnings: parseJsonArray(doc?.parseWarningsJson),
      };
      return meta;
    },
    updateSegment: (datasetId, docId, segId, content) =>
      client.knowledge.updateSegment(datasetId, docId, segId, content).then(normalizeChunk),
    deleteSegment: (datasetId, docId, segId) =>
      client.knowledge.deleteSegment(datasetId, docId, segId),
    setSegmentEnabled: (datasetId, docId, segId, enabled) =>
      client.knowledge
        .setSegmentEnabled(datasetId, docId, segId, enabled)
        .then(normalizeChunk),
    appendSegment: (datasetId, docId, content) =>
      client.knowledge.appendSegment(datasetId, docId, content).then(normalizeChunk),

    // ---- retrieval
    retrieve: (datasetId, req) =>
      client.knowledge.retrieve(datasetId, req).then((rows) =>
        rows.map<RecallHit>((r) => ({
          segmentId: r.segmentId,
          content: r.content,
          position: r.position,
          score: r.score ?? 0,
          documentId: (r.documentId as string | undefined) ?? undefined,
          documentName: extractMetaValue(r.metadata, 'documentName') as string | undefined,
          blockType: extractMetaValue(r.metadata, 'blockType') as string | undefined,
          heading: extractMetaValue(r.metadata, 'heading') as string | undefined,
          vectorScore: safeNumber(r.vectorScore),
          keywordScore: safeNumber(r.keywordScore),
          metadata: normalizeMetadata(r.metadata),
        })),
      ),
    retrieveRag: (datasetId, req) => client.knowledge.retrieveRag(datasetId, req),
    getRagCapabilities: () => client.knowledge.getRagCapabilities(),
    listRecallHistory: (datasetId, limit) =>
      client.knowledge.listRecallHistory(datasetId, limit).then((rows) =>
        rows.map<RecentQuery>((r) => ({
          id: r.id,
          query: r.query,
          method: r.method ?? 'HYBRID',
          hitCount: r.hitCount ?? 0,
          at: r.createdAt ?? '',
        })),
      ),
    listIndexVersions: (datasetId) => client.knowledge.listIndexVersions(datasetId),
    beginIndexVersion: (datasetId, request) => client.knowledge.beginIndexVersion(datasetId, request),
    activateIndexVersion: (datasetId, versionId) => client.knowledge.activateIndexVersion(datasetId, versionId),
    evaluateRetrieval: (datasetId, request) => client.knowledge.evaluate(datasetId, request),
    listPoisonedIngestionJobs: (datasetId) => client.knowledge.listPoisonedIngestionJobs(datasetId),
    replayIngestionJob: (datasetId, documentId) => client.knowledge.replayIngestionJob(datasetId, documentId),

    // ---- models
    listEmbeddingModels: async () => {
      const rows = await client.models.list({ type: 'TEXT_EMBEDDING' });
      // /models/defaults returns { TEXT_EMBEDDING: ModelEntity | null } — fetch
      // in parallel with the list so we can flag the default row inline.
      // Failing softly means non-default UX still works on backends that don't
      // publish defaults.
      let defaultId: null | string = null;
      try {
        defaultId = await client.models.defaultId('TEXT_EMBEDDING');
      } catch {
        // ignore — panel just won't preselect anything
      }
      return rows.map((m) => ({
        id: m.id,
        label: `${m.providerName} · ${m.modelName}`,
        providerName: m.providerName,
        providerLabel: m.providerName,
        isDefault: defaultId != null && m.id === defaultId,
      }));
    },
    getDefaultEmbeddingModelId: async () => {
      try {
        return await client.models.defaultId('TEXT_EMBEDDING');
      } catch {
        return null;
      }
    },
    listRerankModels: async () => {
      // Rerank model registry is optional — treat any 404/error as "none registered"
      // rather than a fatal so the wizard still opens on fresh installs.
      try {
        const rows = await client.models.list({ type: 'RERANK' });
        return rows.map((m) => ({
          id: m.id,
          label: `${m.providerName} · ${m.modelName}`,
        }));
      } catch {
        return [];
      }
    },

    // ---- hooks (delegate to host)
    onSuccess: opts.onSuccess,
    onError: opts.onError,
    onCopyApi:
      opts.onCopyApi ??
      ((id) => {
        if (typeof window === 'undefined' || !navigator?.clipboard) return;
        const url = `${window.location.origin}${client.rootUrl}/datasets/${id}`;
        void navigator.clipboard.writeText(url);
      }),
    onGoToEmbeddingSetup: opts.onGoToEmbeddingSetup,
  };

  return api;
}

function extractMetaValue(raw: unknown, key: string): string | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const value = (raw as Record<string, unknown>)[key];
  if (typeof value === 'string') return value;
  return undefined;
}

function normalizeMetadata(raw: unknown): Record<string, unknown> | undefined {
  return raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : undefined;
}

function safeNumber(raw: unknown): number | undefined {
  return typeof raw === 'number' ? raw : undefined;
}
