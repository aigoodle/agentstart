/**
 * `AppStudioApi` — the callback bag a host app supplies to
 * {@code <AppDesignDrawer :api="...">}. The drawer's built-in panels
 * ({@code LogAnnotationPanel}, {@code MonitorPanel}, …) call these functions
 * for every backend I/O; the plugin never imports the host's HTTP client
 * directly.
 *
 * <p>Rationale: keeps the plugin a self-contained npm package. A consumer
 * just implements this interface once against their axios/fetch stack and
 * every panel wires up — no more copying panel implementations around.</p>
 *
 * <p>Every method returns a {@link Promise}; the plugin awaits and handles
 * loading / error UI centrally. A missing implementation (e.g. no annotation
 * backend yet) can just resolve to an empty array — the panel degrades to an
 * empty-state placeholder, no crash.</p>
 */

// ---------------------------------------------------------------------------
// Domain types — mirrors the shapes the plugin panels need to render. Kept
// intentionally minimal so a host adapting to a different backend only has
// to supply these fields.
// ---------------------------------------------------------------------------

/** A single row in a chat conversation history. */
export interface StudioHistoryMessage {
  role: 'ASSISTANT' | 'SYSTEM' | 'USER';
  content: string;
  createdAt?: string;
}

/** One row in the "对话" tab table. */
export interface StudioConversationSummary {
  conversationId: string;
  /**
   * 会话所有者标识。后端来自 conversations.from_end_user_id（用户侧终端 id）
   * 或 from_account_id（控制台账号）；两者皆无为 null，UI 展示为 "—"。
   */
  userId?: null | string;
  firstMessage?: string;
  updatedAt?: string;
  messageCount?: number;
}

/** A saved QA-pair annotation (Dify's 标注回复). */
export interface StudioAnnotation {
  id: string;
  appId?: string;
  question?: string;
  content?: string;
  enabled?: boolean;
  hitCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** Payload for create/update annotation. */
export interface StudioAnnotationRequest {
  question: string;
  content: string;
  enabled?: boolean;
}

/** Per-app conversation/message metrics powering the "监测" tab tiles. */
export interface StudioAppMetrics {
  appId: string;
  totalConversations: number;
  totalMessages: number;
  userMessages?: number;
  assistantMessages?: number;
  avgInteractionsPerConversation: number;
  lastActivityAt?: string;
}

/** Rolled-up LLM usage. */
export interface StudioLlmUsageStats {
  calls: number;
  errors: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  costMicros: number;
  avgLatencyMs: number;
}

/** A single LLM call record in the recent-calls table. */
export interface StudioLlmCallRecord {
  id: string;
  provider?: string;
  model?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  costMicros?: number;
  latencyMs?: number;
  success?: boolean;
  createdAt?: string;
}

export type StudioLlmTrendRange = 'HOUR' | 'DAY' | 'WEEK';

/** One zero-filled time bucket returned by the LLMOps monitoring API. */
export interface StudioLlmTrendPoint {
  bucketStart: string;
  calls: number;
  errors: number;
  totalTokens: number;
  costMicros: number;
  avgLatencyMs: number;
}

/**
 * One row in the "API 密钥" table under the 访问 API tab. Matches the shape
 * exposed by the backend {@code /api/agent-start/apps/{appId}/api-tokens} endpoints —
 * List responses contain only a masked token. The create response contains the
 * full value exactly once so the caller can copy and store it securely.
 */
export interface StudioApiKey {
  id: string;
  appId?: string;
  name?: string;
  /** {@code app} / {@code dataset} — narrow it later if we support dataset keys. */
  type?: string;
  /** Full opaque value only on create; masked (`app-...xxxx`) on subsequent reads. */
  token: string;
  createdAt?: string;
  lastUsedAt?: string;
}

export interface StudioWorkflowAppOption {
  appId: string;
  workflowId: string;
  name: string;
  icon?: string;
  iconBackground?: string;
}

// ---------------------------------------------------------------------------
// The callback bag itself.
// ---------------------------------------------------------------------------

/**
 * Every field is optional so a host can wire only the panels they need. A
 * panel whose API method is missing renders as read-only / empty.
 */
export interface AppStudioApi {
  /** Execute the current unsaved workflow graph for the workflow debug panel. */
  runWorkflowGraph?: (payload: Record<string, unknown>) => Promise<unknown>;
  /**
   * SSE 流式试运行工作流（`POST /workflows/run-graph/stream`）。返回原始
   * `Response`，调试面板用 `readSseEvents` 逐节点消费；`opts.signal` 用于
   * 中途取消。未实现时面板自动回退到 `runWorkflowGraph` 一次性执行。
   */
  runWorkflowGraphStream?: (
    payload: Record<string, unknown>,
    opts?: { signal?: AbortSignal },
  ) => Promise<Response | null>;
  cancelWorkflowRun?: (runId: string, reason?: string) => Promise<unknown>;
  pauseWorkflowRun?: (runId: string, reason?: string) => Promise<unknown>;
  resumeWorkflowRun?: (runId: string) => Promise<unknown>;
  signalWorkflowRun?: (runId: string, request: Record<string, unknown>) => Promise<unknown>;
  /** Published workflow applications visible to the current tenant. */
  listPublishedWorkflowOptions?: () => Promise<StudioWorkflowAppOption[]>;
  // ── Conversation history / logs tab ────────────────────────────────────
  listConversations?: (appId: string, limit?: number) => Promise<StudioConversationSummary[]>;
  fetchHistory?: (
    appId: string,
    conversationId: string,
    limit?: number,
  ) => Promise<StudioHistoryMessage[]>;

  // ── Annotation tab ─────────────────────────────────────────────────────
  listAnnotations?: (appId: string) => Promise<StudioAnnotation[]>;
  createAnnotation?: (
    appId: string,
    req: StudioAnnotationRequest,
  ) => Promise<StudioAnnotation>;
  updateAnnotation?: (
    appId: string,
    id: string,
    req: StudioAnnotationRequest,
  ) => Promise<StudioAnnotation>;
  deleteAnnotation?: (appId: string, id: string) => Promise<void>;

  // ── Monitor tab ────────────────────────────────────────────────────────
  fetchAppMetrics?: (appId: string) => Promise<StudioAppMetrics>;
  fetchLlmUsage?: () => Promise<StudioLlmUsageStats>;
  fetchRecentLlmCalls?: (limit?: number) => Promise<StudioLlmCallRecord[]>;
  fetchLlmTrend?: (range: StudioLlmTrendRange) => Promise<StudioLlmTrendPoint[]>;

  // ── 访问 API tab — API-key management ─────────────────────────────────
  /**
   * List every API key attached to {@code appId}. Called on tab open. Returns
   * an empty array when the host hasn't wired the endpoint yet — the manager
   * degrades to the "尚未生成" empty state.
   */
  listApiKeys?: (appId: string) => Promise<StudioApiKey[]>;
  /**
   * Mint a new key. The response is expected to include the full token value
   * so the UI can show it — subsequent list calls also include the value
   * (this is an internal console, matching Dify's UX).
   */
  createApiKey?: (appId: string, name?: string) => Promise<StudioApiKey>;
  /** Rename an existing key. Optional — the manager hides the rename button when absent. */
  renameApiKey?: (
    appId: string,
    id: string,
    name: string,
  ) => Promise<StudioApiKey>;
  /** Delete a key permanently. */
  deleteApiKey?: (appId: string, id: string) => Promise<void>;
}
