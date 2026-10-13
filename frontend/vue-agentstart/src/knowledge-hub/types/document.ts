/**
 * Document + chunk (segment) types.
 */

/** One row in the DocumentTable. */
export interface DocumentRow {
  id: string;
  name: string;
  /** "自定义" / "父子分段" — display string, not enum. */
  chunkMode?: string;
  wordCount?: number;
  hitCount?: number;
  uploadedAt?: string;
  status: 'AVAILABLE' | 'FAILED' | 'PROCESSING';
  enabled: boolean;
  parserName?: string;
  mediaType?: string;
  pageCount?: number;
  blockCount?: number;
  parseWarnings?: string[];
  fileSize?: number;
}

/** One chunk shown in DocumentChunksView. */
export interface Chunk {
  id: string;
  position: number;
  content: string;
  tokenCount?: number;
  charCount?: number;
  hitCount?: number;
  enabled: boolean;
  keywords?: string[];
}

/** Right-side metadata pane of DocumentChunksView. */
export interface DocMetadata {
  fileName?: string;
  kind?: string;
  size?: string;
  uploadedAt?: string;
  parsedAt?: string;
  embeddedAt?: string;
  chunkMode?: string;
  chunkMaxSize?: number;
  totalChars?: number;
  totalChunks?: number;
  avgChunkChars?: number;
  avgEmbedMs?: number;
  totalTokens?: number;
  parserName?: string;
  mediaType?: string;
  pageCount?: number;
  blockCount?: number;
  parseWarnings?: string[];
}

export type ParsedBlockType = 'TITLE' | 'HEADING' | 'PARAGRAPH' | 'LIST_ITEM' | 'TABLE' | 'CODE' | 'IMAGE' | 'PAGE_BREAK';
export interface ParsedDocumentBlock { index: number; type: ParsedBlockType; text: string; page?: number; headingLevel?: number; headingPath?: string; metadata?: Record<string, unknown>; }
export interface ParsedDocument { filename?: string; parser?: string; mediaType?: string; title?: string; pageCount?: number; blocks: ParsedDocumentBlock[]; metadata?: Record<string, unknown>; warnings?: string[]; }
