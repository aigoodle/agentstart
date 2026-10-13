/**
 * HTTP core of the unified agent-start client.
 *
 * Every Vue component and legacy adapter in this package funnels through
 * {@link createHttpCore}. It owns the pieces that used to be copy-pasted five
 * times (knowledge adapter, useKnowledge, useProviderHub, agent-studio
 * adapter, agent-run client):
 *
 *   • URL composition — `${baseUrl}${namespace}` (default `/api/agent-start`),
 *   • the spring-agent `{ code, message, data, traceId? }` envelope,
 *   • per-request auth/tenant/extra headers (lazy, evaluated on every call),
 *   • timeouts via AbortController,
 *   • 401 → `onUnauthorized` hook,
 *   • uniform error surfacing via {@link AgentStartError}.
 *
 * The core is framework-neutral: no Vue imports, no module-level mutable
 * state, no DOM access at import time — safe to construct in SSR.
 */

export type MaybePromise<T> = Promise<T> | T;

export type FetchLike = typeof fetch;

export type HeadersProvider = () => MaybePromise<Record<string, string>>;

export interface AgentStartClientOptions {
  /**
   * The host's proxy prefix. Default `/api`. Pass `''` when the browser talks
   * to the backend directly. Trailing slashes are stripped.
   */
  baseUrl?: string;
  /**
   * Controller namespace appended to `baseUrl`. spring-agent-start mounts
   * every controller under `/agent-start`
   * (`SpringAgentWebAutoConfiguration.CONTROLLER_PATH_PREFIX`), which is the
   * default. Hosts whose gateway strips the namespace can pass `''`.
   */
  namespace?: string;
  /** Custom fetch implementation (host axios-wrapper-as-fetch, undici, …). */
  fetch?: FetchLike;
  /**
   * Lazy access-token provider, evaluated on every request. A non-empty
   * return value becomes `Authorization: ${tokenType} ${token}`.
   */
  getAccessToken?: () => MaybePromise<string | null | undefined>;
  /** Authorization scheme prefix. Default `Bearer`. */
  tokenType?: string;
  /**
   * Lazy tenant-id hint used by tenant-aware catalog calls. It is never an
   * authentication boundary: the backend must derive its trusted tenant from
   * the host's authenticated principal.
   */
  getTenant?: () => MaybePromise<string | null | undefined>;
  /**
   * Opt-in compatibility transport for legacy gateways that require a tenant
   * header. Disabled by default because a browser-provided header is forgeable.
   */
  sendTenantHeader?: boolean;
  /** Header name used only when `sendTenantHeader` is true. Default `X-Tenant-Id`. */
  tenantHeader?: string;
  /** Escape hatch: extra headers merged into every request (lazy). */
  headers?: HeadersProvider;
  /** Fired on HTTP 401 before the request rejects. */
  onUnauthorized?: (info: UnauthorizedInfo) => void;
  /** Fired with the human-readable message whenever a request fails. */
  onError?: (msg: string) => void;
  /** Available to adapters that want to toast adapter-level successes. */
  onSuccess?: (msg: string) => void;
  /** Low-level observability hook for host tracing/metrics. Never changes request behavior. */
  onRequestCompleted?: (event: RequestCompletedEvent) => void;
  /** Default request timeout. Default: 60_000 ms. */
  timeoutMs?: number;
  /** Timeout for multipart uploads. Default: 300_000 ms. */
  uploadTimeoutMs?: number;
}

export interface RequestCompletedEvent {
  method: string;
  path: string;
  url: string;
  status?: number;
  durationMs: number;
  success: boolean;
  errorCode?: string;
}

export interface UnauthorizedInfo {
  /** Full request URL. */
  url: string;
  status: number;
}

/** Error thrown for every failed request (HTTP error or envelope code ≠ ok). */
export class AgentStartError extends Error {
  /** HTTP status, when one was received. */
  readonly status?: number;
  /** Envelope `code` (`'NETWORK'`, `'TIMEOUT'`, … for local failures). */
  readonly code?: string;
  /** Backend correlation id, when the envelope carried one. */
  readonly traceId?: string;

  constructor(
    message: string,
    init: { code?: string; status?: number; traceId?: string } = {},
  ) {
    super(message);
    this.name = 'AgentStartError';
    this.status = init.status;
    this.code = init.code;
    this.traceId = init.traceId;
  }
}

interface Envelope<T> {
  code: string;
  message?: string;
  data: T;
  traceId?: string;
}

export interface ExtraRequestOptions {
  /** Override the client-level timeout for this call. */
  timeoutMs?: number;
  /** Caller-provided abort signal (wins over the timeout timer). */
  signal?: AbortSignal;
  /** Set false to skip JSON-body defaults (e.g. multipart uploads). */
  json?: boolean;
}

export interface HttpCore {
  /** Resolved proxy prefix (options.baseUrl, trailing slashes stripped). */
  readonly proxyBase: string;
  /** Controller namespace (options.namespace). */
  readonly namespace: string;
  /** `${proxyBase}${namespace}` — every request path is relative to this. */
  readonly rootUrl: string;

  /**
   * Envelope-unwrapping JSON request. Resolves with `data`, rejects with
   * {@link AgentStartError} on any failure.
   */
  request<T>(
    path: string,
    init?: RequestInit,
    opts?: ExtraRequestOptions,
  ): Promise<T>;

  /**
   * Like {@link request} but resolves the raw `Response` — for SSE streams
   * and other non-envelope payloads. HTTP errors still reject.
   */
  raw(path: string, init?: RequestInit, opts?: ExtraRequestOptions): Promise<Response>;

  /**
   * Multipart upload that expects an envelope response (returns `data`).
   * Never sets Content-Type — the browser supplies the multipart boundary.
   */
  upload<T>(
    path: string,
    form: FormData,
    opts?: ExtraRequestOptions,
  ): Promise<T>;

  /** Current tenant id via `getTenant`, or undefined. */
  tenant(): Promise<string | undefined>;

  /** Options the core was created with (read-only view for adapters). */
  readonly options: Readonly<AgentStartClientOptions>;
}

/**
 * Builds a query string from a params object, skipping null/undefined/''
 * values. Returns `''` or `'?a=1&b=2'`.
 */
export function qs(params: Record<string, string | number | undefined | null>): string {
  const parts: string[] = [];
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === '') continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  }
  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

function stripTrailingSlashes(value: string): string {
  return value.replace(/\/+$/, '');
}

export function createHttpCore(options: AgentStartClientOptions = {}): HttpCore {
  const proxyBase = stripTrailingSlashes(options.baseUrl ?? '/api');
  const namespace = stripTrailingSlashes(options.namespace ?? '/agent-start');
  const rootUrl = `${proxyBase}${namespace}`;
  const doFetch: FetchLike = options.fetch ?? ((...args) => fetch(...args));
  const defaultTimeoutMs = options.timeoutMs ?? 60_000;
  const uploadTimeoutMs = options.uploadTimeoutMs ?? 300_000;

  async function resolveHeaders(): Promise<Record<string, string>> {
    const headers: Record<string, string> = {};
    if (options.getAccessToken) {
      const token = await options.getAccessToken();
      if (token) {
        headers.Authorization = `${options.tokenType ?? 'Bearer'} ${token}`;
      }
    }
    if (options.getTenant && options.sendTenantHeader) {
      const tenantId = await options.getTenant();
      if (tenantId) {
        headers[options.tenantHeader ?? 'X-Tenant-Id'] = tenantId;
      }
    }
    if (options.headers) {
      Object.assign(headers, await options.headers());
    }
    return headers;
  }

  function composeSignal(
    callerSignal: AbortSignal | undefined,
    timeoutMs: number,
  ): { signal: AbortSignal; dispose: () => void } {
    if (callerSignal) {
      // Caller owns cancellation (e.g. the runs watch loop) — don't race it
      // with a timer.
      return { signal: callerSignal, dispose: () => undefined };
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    return { signal: controller.signal, dispose: () => clearTimeout(timer) };
  }

  async function errorMessage(res: Response): Promise<string> {
    let msg = `${res.status} ${res.statusText}`;
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) msg = body.message;
    } catch {
      // non-JSON error body — keep the status line
    }
    return msg;
  }

  async function execute(
    path: string,
    init: RequestInit,
    opts: ExtraRequestOptions | undefined,
    timeoutMs: number,
  ): Promise<Response> {
    const url = `${rootUrl}${path.startsWith('/') ? '' : '/'}${path}`;
    const startedAt = Date.now();
    let status: number | undefined;
    let success = false;
    let errorCode: string | undefined;
    const { signal, dispose } = composeSignal(opts?.signal, opts?.timeoutMs ?? timeoutMs);
    try {
      const isForm = typeof FormData !== 'undefined' && init.body instanceof FormData;
      const headers: Record<string, string> = {
        ...(opts?.json === false || isForm ? {} : { 'Content-Type': 'application/json' }),
        ...(await resolveHeaders()),
        ...((init.headers as Record<string, string> | undefined) ?? {}),
      };
      let res: Response;
      try {
        res = await doFetch(url, { ...init, headers, signal });
      } catch (err) {
        const aborted =
          (err instanceof Error && err.name === 'AbortError') || signal.aborted;
        const msg = aborted ? 'Request aborted' : 'Network error';
        const wrapped = new AgentStartError(msg, {
          code: aborted ? 'ABORTED' : 'NETWORK',
        });
        errorCode = wrapped.code;
        options.onError?.(msg);
        throw wrapped;
      }
      status = res.status;
      if (res.status === 401) {
        options.onUnauthorized?.({ url, status: 401 });
      }
      if (!res.ok) {
        const msg = await errorMessage(res);
        options.onError?.(msg);
        errorCode = `HTTP_${res.status}`;
        throw new AgentStartError(msg, { status: res.status });
      }
      success = true;
      return res;
    } finally {
      dispose();
      options.onRequestCompleted?.({
        method: init.method ?? 'GET', path, url, status,
        durationMs: Date.now() - startedAt, success, errorCode,
      });
    }
  }

  const core: HttpCore = {
    proxyBase,
    namespace,
    rootUrl,
    options,

    async request<T>(path: string, init: RequestInit = {}, opts?: ExtraRequestOptions) {
      const res = await execute(path, init, opts, defaultTimeoutMs);
      const env = (await res.json()) as Envelope<T>;
      if (env.code !== 'ok') {
        const msg = env.message ?? env.code;
        options.onError?.(msg);
        throw new AgentStartError(msg, { code: env.code, traceId: env.traceId });
      }
      return env.data;
    },

    async raw(path: string, init: RequestInit = {}, opts?: ExtraRequestOptions) {
      return execute(path, init, opts, defaultTimeoutMs);
    },

    async upload<T>(path: string, form: FormData, opts?: ExtraRequestOptions) {
      const res = await execute(
        path,
        { method: 'POST', body: form },
        { ...opts, json: false, timeoutMs: opts?.timeoutMs ?? uploadTimeoutMs },
        uploadTimeoutMs,
      );
      const env = (await res.json()) as Envelope<T>;
      if (env.code !== 'ok') {
        const msg = env.message ?? env.code;
        options.onError?.(msg);
        throw new AgentStartError(msg, { code: env.code, traceId: env.traceId });
      }
      return env.data;
    },

    async tenant() {
      if (!options.getTenant) return undefined;
      const value = await options.getTenant();
      return value ?? undefined;
    },
  };

  return core;
}
