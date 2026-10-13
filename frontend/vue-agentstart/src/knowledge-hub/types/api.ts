/**
 * The API contract KnowledgeHubApp needs. Split into 5 sub-interfaces so a
 * host can implement a partial (e.g. a read-only viewer only needs
 * DatasetsReadApi + DocumentsReadApi + RetrievalApi).
 *
 * `KnowledgeHubApi` is a type union of every sub-interface — code that
 * accepts one can accept any richer implementation.
 */
import type { Chunk, DocMetadata, DocumentRow, ParsedDocument } from './document';
import type {
  DatasetCardItem,
  DatasetSummary,
  IndexingTechnique,
  ProcessRule,
  RetrievalConfig,
} from './dataset';
import type { RagCapabilities, RagPolicy, RagResult, RecallHit, RecentQuery, RetrieveRequest } from './retrieval';
import type { IndexVersionWire, IngestionJobWire, RetrievalEvaluationReportWire } from '../../client/knowledge';

// ------- datasets
export interface DatasetsApi {
  listDatasets(): Promise<DatasetCardItem[]>;
  getDataset(id: string): Promise<DatasetSummary>;
  createDataset(req: {
    name: string;
    description?: string;
    embeddingModelId?: string;
    indexingTechnique?: IndexingTechnique;
    processRule?: ProcessRule;
    retrievalConfig?: RetrievalConfig;
  }): Promise<DatasetCardItem>;
  updateDataset(
    id: string,
    patch: Partial<DatasetSummary> & Record<string, unknown>,
  ): Promise<DatasetSummary>;
  deleteDataset(id: string): Promise<void>;
}

// ------- documents
export interface DocumentsApi {
  listDocuments(datasetId: string): Promise<DocumentRow[]>;
  uploadDocument(datasetId: string, file: File): Promise<void>;
  deleteDocument(datasetId: string, docId: string): Promise<void>;
  setDocumentEnabled?(
    datasetId: string,
    docId: string,
    enabled: boolean,
  ): Promise<void>;
  getParsedDocument?(datasetId: string, docId: string): Promise<ParsedDocument>;
  reparseDocument?(datasetId: string, docId: string): Promise<DocumentRow>;
  /**
   * Preview how a file would be chunked under a given {@link ProcessRule}
   * without touching the DB or the vector store. Used by the wizard's
   * "文本分段与清洗" step so the preview reflects the real ingestion result
   * (same reader → same cleaner → same chunker as on save). Backends that
   * don't implement it get the wizard's mock preview as a fallback.
   *
   * @param file  raw file bytes the user just picked
   * @param rule  the currently-editing {@link ProcessRule}
   * @param limit max chunks to return; server may cap further
   */
  previewChunks?(
    file: File,
    rule: ProcessRule,
    limit?: number,
  ): Promise<ChunkPreview>;
}

/**
 * Response shape of {@link DocumentsApi.previewChunks}. {@code totalChunks}
 * is the full chunker output; {@code chunks} is truncated to {@code limit}.
 */
export interface ChunkPreview {
  parser?: string;
  mediaType?: string;
  pageCount?: number;
  blockCount?: number;
  warnings?: string[];
  totalChunks: number;
  chunks: Array<{ index: number; text: string; tokens: number; metadata?: Record<string, unknown> }>;
}

// ------- segments
export interface SegmentsApi {
  /**
   * Paged fetch of a document's segments. `page` is 1-indexed; when omitted the
   * host implementation is expected to return the first page with a sensible
   * default page size. The total comes from {@link DocMetadata#totalChunks} —
   * kept out of this response to avoid an extra count query on hot paths.
   */
  listSegments(
    datasetId: string,
    docId: string,
    page?: number,
    pageSize?: number,
  ): Promise<Chunk[]>;
  loadDocumentMetadata(datasetId: string, docId: string): Promise<DocMetadata>;
  updateSegment(
    datasetId: string,
    docId: string,
    segId: string,
    content: string,
  ): Promise<Chunk>;
  deleteSegment(datasetId: string, docId: string, segId: string): Promise<void>;
  setSegmentEnabled(
    datasetId: string,
    docId: string,
    segId: string,
    enabled: boolean,
  ): Promise<Chunk>;
  appendSegment?(
    datasetId: string,
    docId: string,
    content: string,
  ): Promise<Chunk>;
}

// ------- retrieval
export interface RetrievalApi {
  retrieve(
    datasetId: string,
    req: RetrieveRequest,
  ): Promise<RecallHit[]>;
  listRecallHistory(datasetId: string, limit?: number): Promise<RecentQuery[]>;
  retrieveRag?(datasetId: string, req: { query: string; conversationId?: string; policy?: RagPolicy; metadataFilter?: Record<string, unknown> }): Promise<RagResult>;
  getRagCapabilities?(): Promise<RagCapabilities>;
}

export interface RagOperationsApi {
  listIndexVersions?(datasetId: string): Promise<IndexVersionWire[]>;
  beginIndexVersion?(datasetId: string, request: Record<string, unknown>): Promise<IndexVersionWire>;
  activateIndexVersion?(datasetId: string, versionId: string): Promise<void>;
  evaluateRetrieval?(datasetId: string, request: Record<string, unknown>): Promise<RetrievalEvaluationReportWire>;
  listPoisonedIngestionJobs?(datasetId: string): Promise<IngestionJobWire[]>;
  replayIngestionJob?(datasetId: string, documentId: string): Promise<boolean>;
}

// ------- models
/**
 * Embedding-model list entry. {@code providerName} is optional but recommended —
 * when present, the settings panel groups the dropdown by provider and shows a
 * brand icon for each row. {@code providerLabel} is a display label; falls back
 * to {@code providerName} when omitted.
 */
export interface EmbeddingModelOption {
  id: string;
  label: string;
  providerName?: string;
  providerLabel?: string;
  /** Marks the tenant-default embedding model — the panel auto-selects it. */
  isDefault?: boolean;
}

export interface ModelsApi {
  listEmbeddingModels(): Promise<EmbeddingModelOption[]>;
  /**
   * Optional — list rerank models the wizard's 检索设置 offers. Hosts that don't
   * implement it get an empty dropdown (rerank stays off).
   */
  listRerankModels?(): Promise<Array<{ id: string; label: string }>>;
  /**
   * Optional — resolve the tenant-scoped default embedding model id (from
   * {@code /models/defaults} on the spring-agent-start backend). When present,
   * the settings panel pre-fills the picker if the dataset itself has none.
   */
  getDefaultEmbeddingModelId?(): Promise<null | string>;
}

// ------- side effects (all optional)
export interface KnowledgeHubHooks {
  /** Toast success. */
  onSuccess?(message: string): void;
  /** Toast failure. */
  onError?(message: string): void;
  /** Called by the sidebar's ⌘ 访问 API button. */
  onCopyApi?(datasetId: string): void;
  /**
   * Called when the built-in "先注册 Embedding 模型" empty-state nudge is
   * clicked. Hosts wire this to their model-management route so the user
   * can register an embedding model without leaving the flow. When omitted
   * the nudge card renders without a button.
   */
  onGoToEmbeddingSetup?(): void;
}

/**
 * The bag of async operations KnowledgeHubApp needs. Consumers implement this
 * shape (typically by wrapping their existing REST client — or by calling
 * `createSpringAgentStartAdapter` if their backend is spring-agent-start itself).
 */
export type KnowledgeHubApi = DatasetsApi &
  DocumentsApi &
  SegmentsApi &
  RetrievalApi &
  RagOperationsApi &
  ModelsApi &
  KnowledgeHubHooks;

/**
 * Subset used by DatasetDetailDrawer. Standalone (not `extends`) because it
 * exposes `loadDataset` + `uploadDocuments` (plural, batch semantic) rather
 * than the {@link DatasetsApi#getDataset} + {@link DocumentsApi#uploadDocument}
 * atoms. Hosts that only need the drawer implement this smaller surface.
 */
export interface DatasetDetailHub extends SegmentsApi, RetrievalApi, RagOperationsApi, ModelsApi {
  loadDataset(id: string): Promise<DatasetSummary>;
  updateDataset(
    id: string,
    patch: Partial<DatasetSummary> & Record<string, unknown>,
  ): Promise<DatasetSummary>;
  deleteDataset(id: string): Promise<void>;

  listDocuments(datasetId: string): Promise<DocumentRow[]>;
  uploadDocuments(datasetId: string, files: File[]): Promise<void>;
  deleteDocument(datasetId: string, docId: string): Promise<void>;
  setDocumentEnabled?(
    datasetId: string,
    docId: string,
    enabled: boolean,
  ): Promise<void>;
  /** Same shape as {@link DocumentsApi.getParsedDocument}. */
  getParsedDocument?(datasetId: string, docId: string): Promise<ParsedDocument>;
  /** Same shape as {@link DocumentsApi.reparseDocument}. */
  reparseDocument?(datasetId: string, docId: string): Promise<DocumentRow>;
  /**
   * Preview how a file would be chunked, for the add-documents wizard's
   * step-2 pane. Same shape as {@link DocumentsApi.previewChunks}. Optional —
   * when the host adapter omits it, the wizard falls back to a mock preview.
   */
  previewChunks?(
    file: File,
    rule: ProcessRule,
    limit?: number,
  ): Promise<ChunkPreview>;
  onCopyApi?(datasetId: string): void;
}
