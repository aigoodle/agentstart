/**
 * Dataset-level types. Mirror the spring-agent-web JSON contract, but the
 * module has no hard dependency on that backend — any REST source that returns
 * these shapes works.
 */

export type IndexingTechnique = 'ECONOMY' | 'HIGH_QUALITY';

/**
 * Slim dataset view suitable for the card grid. Only the visual fields are
 * required — a partial-shape backend still works, the card just shows less.
 */
export interface DatasetCardItem {
  id: string;
  name: string;
  description?: string;
  documentCount?: number;
  segmentCount?: number;
  indexingTechnique?: IndexingTechnique;
  updatedAt?: string;
}

/**
 * Full dataset shape for the detail view. Optional JSON-blob fields
 * (`retrievalConfigJson`, `processRuleJson`) are strings so the module can
 * parse them defensively without coupling to a specific config schema.
 */
export interface DatasetSummary {
  id: string;
  name: string;
  description?: string;
  documentCount?: number;
  segmentCount?: number;
  indexingTechnique?: IndexingTechnique;
  embeddingModelId?: string;
  retrievalConfigJson?: string;
  processRuleJson?: string;
}

/** Legacy alias kept for backwards compat with older Dify-style flows. */
export interface Dataset extends DatasetSummary {
  tenantId?: string;
}

/** Navigation tabs of the dataset detail view. */
export type DatasetTab = 'documents' | 'recall' | 'operations' | 'settings';

// --------- process rule (chunking) -----------------------------------------

export type ChunkingTemplate =
  | 'NAIVE'
  | 'PARENT_CHILD'
  | 'QA'
  | 'MARKDOWN'
  | 'STRUCTURE_AWARE'
  | 'ONE';

export type ParentMode = 'PARAGRAPH' | 'FULL_DOC';

/**
 * Wire shape of the backend `ProcessRule`. Only fields the wizard writes are
 * enumerated — hosts can send extras and the backend just ignores them.
 */
export interface ProcessRule {
  template?: ChunkingTemplate;
  chunkTokens?: number;
  overlapTokens?: number;
  protectStructuredBlocks?: boolean;
  includeHeadingContext?: boolean;
  parentChunkTokens?: number;
  parentMode?: ParentMode;
  separators?: string[];
  removeExtraWhitespace?: boolean;
  removeUrlsEmails?: boolean;
}

// --------- retrieval config -------------------------------------------------
// Note: RetrievalMethod itself lives in ./retrieval to keep the retrieval module
// self-contained; we import it here just for RetrievalConfig.
import type { RetrievalMethod } from './retrieval';
import type { RagPolicy } from './retrieval';

/** Wire shape of the backend `RetrievalConfig`. */
export interface RetrievalConfig {
  ragPolicy?: RagPolicy;
  method?: RetrievalMethod;
  topK?: number;
  scoreThreshold?: number;
  vectorWeight?: number;
  fusionMethod?: 'WEIGHTED_SCORE' | 'RECIPROCAL_RANK';
  rrfK?: number;
  recallMultiplier?: number;
  maxChunksPerDocument?: number;
  neighborWindow?: number;
  queryExpansionEnabled?: boolean;
  maxQueryVariants?: number;
  rerankEnabled?: boolean;
  rerankModelId?: string;
  rerankerName?: string;
  rerankPoolSize?: number;
}
