/**
 * Retrieval / hit-testing types.
 */

export type RetrievalMethod = 'FULL_TEXT' | 'HYBRID' | 'VECTOR';

export interface RetrieveRequest {
  query: string;
  method?: RetrievalMethod;
  topK?: number;
  scoreThreshold?: number;
  vectorWeight?: number;
  rerankEnabled?: boolean;
  rerankModelId?: string;
  rerankPoolSize?: number;
  maxChunksPerDocument?: number;
  metadataFilter?: Record<string, unknown>;
}

/** Row in the retrieval result list — from HybridRetriever. */
export interface RetrievedSegment {
  segmentId: string;
  datasetId: string;
  documentId: string;
  position: number;
  content: string;
  parentContent?: string;
  vectorScore: number;
  keywordScore: number;
  score: number;
  metadata?: Record<string, unknown>;
}

/** One row in the RecallTestingPanel result pane. Simpler than RetrievedSegment. */
export interface RecallHit {
  segmentId: string;
  content: string;
  position?: number;
  score: number;
  documentName?: string;
  documentId?: string;
  blockType?: string;
  heading?: string;
  metadata?: Record<string, unknown>;
  vectorScore?: number;
  keywordScore?: number;
}

/** Historical recall query row shown in the "记录" list. */
export interface RecentQuery {
  id: string;
  query: string;
  method: string;
  hitCount: number;
  at: string;
}

export type RagQueryStrategy = 'DIRECT' | 'MULTI_QUERY' | 'HYDE' | 'STEP_BACK' | 'DECOMPOSITION';
export type RagRetrievalChannel = 'VECTOR' | 'SPARSE' | 'RAPTOR' | 'GRAPH' | 'MULTIMODAL';
export type RagFusionMethod = 'NONE' | 'RRF' | 'WEIGHTED_SCORE';

export interface RagPolicy {
  queryPlanning: { strategy: RagQueryStrategy; modelId?: string; maxQueries: number; includeOriginal: boolean };
  retrieval: { channels: RagRetrievalChannel[]; candidateLimitPerQuery: number; parallelism: number; metadataFilter?: Record<string, unknown> };
  fusion: { method: RagFusionMethod; rrfK: number; channelWeights?: Partial<Record<RagRetrievalChannel, number>> };
  rerank: { enabled: boolean; provider?: string; model?: string; candidateLimit: number; topN: number; timeout?: string; failOpen: boolean };
  context: { parentExpansion: boolean; neighborWindow: number; deduplicate: boolean; compression: boolean; compressionProvider?: string; maxTokens: number };
  budget: { maxQueries: number; maxRetrievalRounds: number; maxLlmCalls: number; maxCandidates: number; maxEvidenceItems: number; timeout?: string };
}

export interface RagEvidence {
  datasetId: string; documentId: string; segmentId: string; position?: number;
  content: string; context?: string; retrievalScore: number; fusionScore?: number;
  rerankScore?: number; matchedQueryIds: string[]; metadata?: Record<string, unknown>;
}

export interface RagResult {
  originalQuery: string;
  queryPlan: { originalQuery: string; queries: Array<{ id: string; query: string; type: string; weight: number }> };
  evidence: RagEvidence[];
  stageLatencyMillis: Record<string, number>;
}

export interface RagCapabilities {
  queryStrategies: RagQueryStrategy[];
  retrievalChannels: RagRetrievalChannel[];
  fusionMethods: RagFusionMethod[];
}

export const defaultRagPolicy = (): RagPolicy => ({
  queryPlanning: { strategy: 'DIRECT', maxQueries: 1, includeOriginal: true },
  retrieval: { channels: ['VECTOR'], candidateLimitPerQuery: 30, parallelism: 4 },
  fusion: { method: 'NONE', rrfK: 60 },
  rerank: { enabled: false, provider: 'score', candidateLimit: 30, topN: 8, failOpen: true },
  context: { parentExpansion: true, neighborWindow: 0, deduplicate: true, compression: false, maxTokens: 6000 },
  budget: { maxQueries: 4, maxRetrievalRounds: 1, maxLlmCalls: 2, maxCandidates: 100, maxEvidenceItems: 12 },
});
