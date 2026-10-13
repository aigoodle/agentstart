import { AbstractChatProvider, XRequest } from '@ant-design/x-sdk';
import type {
  ChatProviderConfig,
  DefaultMessageInfo,
  SSEFields,
  TransformMessage,
  XModelMessage,
  XRequestOptions,
} from '@ant-design/x-sdk';
import { buildBusDataInputs } from './host-bus-data';
import { buildPageContextInputs } from './host-page-context';
import { checkChatResponse } from './chat-response';
import { getHostAuthHeaders, readHostSession } from './host-session-storage';
import { getHostContext, notifyHost } from './host-bridge';

/** 读取 iframe URL 查询参数，缺失/异常时返回 undefined */
function getUrlParam(name: string): string | undefined {
  try {
    return new URLSearchParams(window.location.search).get(name)?.trim() || undefined;
  } catch {
    return undefined;
  }
}

/**
 * 调试参数存储键：spring-agent-boot 的 ChatIframePanel 会把用户填写的
 * START 变量 / Agent 变量写到 localStorage[DEBUG_INPUTS_KEY]，每次发送前
 * 读出来铺到 inputs 顶层。URL 参数缺失时禁用，避免读到无关 key。
 */
const DEBUG_INPUTS_KEY = getUrlParam('debugInputsKey');
/**
 * spring-agent-boot 扩展调试参数 —— AppDesignDrawer 打开时把这些注入到
 * iframe URL：
 *   • appId       → X-App-Id 请求头（配合 Bearer 双保险；后端日志/devtools 眼见为实）
 *   • workflowId  → X-Workflow-Id 请求头（QA 想跑指定 workflow 版本时用）
 *   • debug       → X-Debug-Mode 请求头（true 强制路由到草稿，不管 apps.workflow_id）
 * 生产 chat 页 / 分享嵌入不传这些，走后端默认（Bearer + apps.workflow_id）。
 */
const HOST_APP_ID = getUrlParam('appId');
const HOST_APP_CODE =
  getUrlParam('appCode') ||
  (import.meta.env.VITE_AGENT_APP_CODE as string | undefined)?.trim() ||
  undefined;
const HOST_WORKFLOW_ID = getUrlParam('workflowId');
const HOST_ACCESS_MODE = getUrlParam('accessMode');
const HOST_TIMEOUT_MS = getUrlParam('timeoutMs');
export const CHAT_LABEL =
  getUrlParam('label') ||
  (import.meta.env.VITE_CHAT_LABEL as string | undefined) ||
  'AI 助手';

const API_BASE = '/chatapi/agent-start';
const ENDPOINT =
  HOST_ACCESS_MODE === 'debug' && HOST_APP_ID
    ? `${API_BASE}/console/apps/${encodeURIComponent(HOST_APP_ID)}/debug/chat/completions`
    : HOST_APP_ID
      ? `${API_BASE}/internal/apps/${encodeURIComponent(HOST_APP_ID)}/chat/completions`
      : HOST_APP_CODE
        ? `${API_BASE}/internal/apps/by-code/${encodeURIComponent(HOST_APP_CODE)}/chat/completions`
      : `${API_BASE}/v1/chat/completions`;

const USER_KEY = 'antd-react-chat:chat-user';

function resolveUser(): string {
  const fromEnv = (import.meta.env.VITE_CHAT_USER as string | undefined)?.trim();
  if (fromEnv) return fromEnv;
  try {
    const cached = window.localStorage.getItem(USER_KEY);
    if (cached) return cached;
    const generated = `chat-user-${Math.random().toString(36).slice(2, 10)}-${Date.now().toString(36)}`;
    window.localStorage.setItem(USER_KEY, generated);
    return generated;
  } catch {
    return 'chat-user-anonymous';
  }
}

/**
 * 读取 spring-agent-boot ChatIframePanel 写入的调试参数。
 *   - localStorage[debugInputsKey] = JSON `Record<varName, unknown>`
 *   - URL 未带 debugInputsKey → 返回 {}
 *   - 解析失败 → 返回 {}（避免脏数据阻塞会话）
 */
function readDebugInputs(): Record<string, unknown> {
  if (!DEBUG_INPUTS_KEY) return {};
  try {
    const raw = window.localStorage.getItem(DEBUG_INPUTS_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    // 过滤 undefined / null / '' —— 让 Chat 用变量默认值而不是空串
    const cleaned: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (v === undefined || v === null) continue;
      if (typeof v === 'string' && v.trim() === '') continue;
      cleaned[k] = v;
    }
    return cleaned;
  } catch {
    return {};
  }
}

type ChatSSEChunk = Partial<Record<SSEFields, string>>;

export interface ChatInput {
  query?: string;
  inputs?: Record<string, unknown>;
  response_mode?: 'streaming' | 'blocking';
  user?: string;
  conversation_id?: string;
  files?: unknown[];
  auto_generate_name?: boolean;
  messages?: XModelMessage[];
  /**
   * spring-agent-boot 扩展 —— 显式路由目标。Chat 官方无此字段，后端
   * ChatController 会把它们和 X-App-Id / X-Workflow-Id / X-Debug-Mode 头合并。
   */
  app_id?: string;
  workflow_id?: string;
  debug?: boolean;
  /** spring-agent-start extension: active Agent run deadline. */
  timeout_ms?: number;
  model?: string;
  stream?: boolean;
  data?: Record<string, unknown>;
}

interface ChatProviderOptions {
  conversationKey: string;
  getConversationId: () => string;
  setConversationId: (id: string) => void;
}

// 新会话首次响应结束（Chat 已生成会话标题）时的通知机制，
// 供侧栏订阅以重新拉取会话列表 / 刷新标题。
export interface ChatConversationCreatedEvent {
  /** useXChat 使用的本地会话 key（新建会话时为时间戳，非 UUID） */
  conversationKey: string;
  /** Chat 服务端真实会话 ID */
  conversationId: string;
}

type ChatConversationCreatedListener = (event: ChatConversationCreatedEvent) => void;

const conversationCreatedListeners = new Set<ChatConversationCreatedListener>();

export interface AgentApprovalRequiredEvent {
  runId: string;
  conversationId?: string;
  approvalId: string;
  toolName: string;
  toolInput: string;
}

export interface AgentResumeResponse {
  runId: string;
  conversationId?: string;
  status: 'COMPLETED' | 'AWAITING_APPROVAL' | 'FAILED' | 'MAX_ITERATIONS';
  text?: string;
  error?: string;
  pendingApproval?: { approvalId: string; toolName: string; toolInput: string };
}

type AgentApprovalRequiredListener = (event: AgentApprovalRequiredEvent) => void;
const approvalRequiredListeners = new Set<AgentApprovalRequiredListener>();

export function onAgentApprovalRequired(listener: AgentApprovalRequiredListener): () => void {
  approvalRequiredListeners.add(listener);
  return () => approvalRequiredListeners.delete(listener);
}

function emitAgentApprovalRequired(event: AgentApprovalRequiredEvent): void {
  approvalRequiredListeners.forEach((listener) => listener(event));
  notifyHost('agent:approval-required', event);
}

export async function resolveAgentApproval(
  event: AgentApprovalRequiredEvent,
  decision: 'APPROVE' | 'DENY',
): Promise<AgentResumeResponse> {
  const response = await fetch(
    `${API_BASE}/agent-runs/${encodeURIComponent(event.runId)}/resume`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...getHostAuthHeaders(),
      },
      body: JSON.stringify({ approvalId: event.approvalId, decision }),
    },
  );
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  const envelope = (await response.json()) as { code?: string; message?: string; data?: unknown };
  if (envelope.code && envelope.code !== 'ok') {
    throw new Error(envelope.message ?? envelope.code);
  }
  notifyHost('agent:approval-resolved', { ...event, decision });
  return envelope.data as AgentResumeResponse;
}

export function onChatConversationCreated(
  listener: ChatConversationCreatedListener,
): () => void {
  conversationCreatedListeners.add(listener);
  return () => {
    conversationCreatedListeners.delete(listener);
  };
}

function emitChatConversationCreated(event: ChatConversationCreatedEvent): void {
  conversationCreatedListeners.forEach((listener) => {
    try {
      listener(event);
    } catch (err) {
      console.warn('[Chat] onChatConversationCreated 监听器异常', err);
    }
  });
}

class ChatProvider extends AbstractChatProvider<XModelMessage, ChatInput, ChatSSEChunk> {
  private readonly conversationKey: string;
  private readonly getConversationId: () => string;
  private readonly setConversationId: (id: string) => void;
  /** 仅在新建会话首次响应结束时通知一次 */
  private notifiedConversationCreated = false;
  private streamFailed = false;

  constructor(
    config: ChatProviderConfig<ChatInput, ChatSSEChunk, XModelMessage>,
    options: ChatProviderOptions,
  ) {
    super(config);
    this.conversationKey = options.conversationKey;
    this.getConversationId = options.getConversationId;
    this.setConversationId = options.setConversationId;
  }

  /**
   * 新建会话首次响应结束时通知一次：此时 Chat 已落地会话并生成标题。
   * 仅对本地 key（非 UUID）的新会话触发，已存在会话（UUID key）不重复通知。
   */
  private maybeNotifyConversationCreated(): void {
    if (this.notifiedConversationCreated) return;
    if (isServerConversationId(this.conversationKey)) return;
    const conversationId = this.getConversationId();
    if (!conversationId) return;
    this.notifiedConversationCreated = true;
    emitChatConversationCreated({
      conversationKey: this.conversationKey,
      conversationId,
    });
  }

  transformParams(
    requestParams: Partial<ChatInput>,
    options: XRequestOptions<ChatInput, ChatSSEChunk, XModelMessage>,
  ): ChatInput {
    this.streamFailed = false;
    // useXChat 会先通过 transformLocalMessage 把本次用户消息写入内部 store，
    // 再调用 transformParams。新版 @ant-design/x-sdk 不保证此时仍把 messages
    // 原样放在 requestParams 中；OpenAIChatProvider 的标准实现同样从
    // getMessages() 读取完整会话。保留 requestParams 作为初始化时序的兜底。
    const storedMessages = this.getMessages?.() ?? [];
    const messages =
      storedMessages.length > 0 ? storedMessages : (requestParams.messages ?? []);
    const conversationId = this.getConversationId();
    // 每次请求读取最新宿主上下文；业务资料进入 data，token 仅用于认证头。
    const session = readHostSession();
    const sessionInputs = session
      ? {
          app_name: session.app_name,
          nickName: session.nickName,
          path: session.path,
          platform: session.platform,
          user_id: session.user_id,
        }
      : {};
    // AI 上下文合并槽 (bus_data): 各业务模块的数据 + 字段说明 (desc) 一起投喂给 Chat.
    // 与 sessionInputs 平级铺到 inputs 顶层, 见 host-bus-data.ts.
    const busDataInputs = buildBusDataInputs();
    // 当前活跃页面协议：字段定义、实时值、可调用的回写工具。
    const pageContextInputs = buildPageContextInputs();
    // 调试参数：spring-agent-boot 的 ChatIframePanel 用户填写的 START 变量。
    // 每次请求都重新读一遍 localStorage，用户改一个字段即时生效，无需刷 iframe。
    const debugInputs = readDebugInputs();
    return {
      ...(options?.params ?? {}),
      model: 'spring-agent',
      messages,
      stream: true,
      data: {
        ...sessionInputs,
        ...(getHostContext()?.route ? { route: JSON.stringify(getHostContext()!.route) } : {}),
        ...busDataInputs,
        ...pageContextInputs,
        ...debugInputs,
        ...(requestParams.inputs ?? {}),
      },
      conversation_id: conversationId || '',
      ...(requestParams.timeout_ms != null
        ? { timeout_ms: requestParams.timeout_ms }
        : HOST_TIMEOUT_MS && Number.isFinite(Number(HOST_TIMEOUT_MS))
          ? { timeout_ms: Math.max(1, Number(HOST_TIMEOUT_MS)) }
          : {}),
      ...(HOST_WORKFLOW_ID ? { workflow_id: HOST_WORKFLOW_ID } : {}),
    };
  }

  transformLocalMessage(requestParams: Partial<ChatInput>): XModelMessage[] {
    return requestParams.messages ?? [];
  }

  transformMessage(info: TransformMessage<XModelMessage, ChatSSEChunk>): XModelMessage {
    const { originMessage, chunk } = info;
    const previousContent =
      typeof originMessage?.content === 'string' ? originMessage.content : '';

    // The SDK calls once more with no chunk when the HTTP stream closes.
    // A successful transport can still contain only whitespace or lifecycle events.
    if (info.status === 'success' && !chunk) {
      this.maybeNotifyConversationCreated();
      return {
        role: 'assistant',
        content: previousContent.trim()
          ? previousContent
          : '服务似乎没有答复，请稍后再试。',
      };
    }

    const rawData = chunk?.data;
    if (!rawData || rawData.trim() === '' || rawData.trim() === '[DONE]') {
      return { role: 'assistant', content: previousContent };
    }

    try {
      const evt = JSON.parse(rawData) as {
        event?: string;
        answer?: string;
        conversation_id?: string;
        message?: string;
        message_id?: string;
        task_id?: string;
        workflow_run_id?: string;
        code?: string;
        status?: number | string;
        error?: string | { message?: string };
        outputs?: { answer?: string };
        choices?: Array<{
          delta?: { role?: string; content?: string };
          finish_reason?: string | null;
        }>;
        data?: {
          conversation_id?: string;
          answer?: string;
          run_id?: string;
          approval?: {
            approvalId?: string;
            toolName?: string;
            toolInput?: string;
          };
          reasons?: Array<{
            node_title?: string;
            form_token?: string;
            type?: string;
            expiration_time?: number;
          }>;
          status?: string;
          error?: string | { message?: string };
          outputs?: { answer?: string };
        };
      };

      const openAIDelta = evt.choices?.[0]?.delta?.content;
      if (openAIDelta) {
        return {
          role: 'assistant',
          content: `${previousContent}${openAIDelta}`,
        };
      }

      const eventName = evt.event ?? chunk?.event;

      // iframe 宿主的执行过程面板需要消费原始工作流事件。聊天正文只处理
      // 文本事件，但节点事件也必须经 host bridge 转发，否则时间线会一直为空。
      if (eventName && [
        'workflow_started', 'node_started', 'node_finished',
        'workflow_finished', 'chat_finished', 'message', 'agent_message',
        'answer_delta', 'message_replace', 'error',
      ].includes(eventName)) {
        const nested = evt.data && typeof evt.data === 'object' ? evt.data : {};
        notifyHost('workflow:trace', {
          event: eventName,
          data: { ...evt, ...nested },
        });
      }

      // 聊天主流事件携带 conversation_id 时落地；TTS / ping 自己的会话上下文跳过
      const cid = evt.conversation_id ?? evt.data?.conversation_id;
      if (
        cid &&
        eventName !== 'tts_message' &&
        eventName !== 'tts_message_end' &&
        eventName !== 'ping'
      ) {
        this.setConversationId(cid);
      }

      switch (eventName) {
        case 'message':
        case 'agent_message':
          return {
            role: 'assistant',
            content: `${previousContent}${evt.answer ?? ''}`,
          };
        case 'message_replace':
          return { role: 'assistant', content: evt.answer ?? '' };
        case 'message_end':
        case 'chat_finished':
        case 'workflow_finished': {
          this.maybeNotifyConversationCreated();
          const status = evt.status ?? evt.data?.status;
          const error = evt.error ?? evt.data?.error;
          if (String(status).toLowerCase() === 'failed' || error) {
            if (this.streamFailed) return { role: 'assistant', content: previousContent };
            this.streamFailed = true;
            const detail = typeof error === 'string' ? error : error?.message;
            return { role: 'assistant', content: `${previousContent}\n\n请求失败：${detail || evt.message || '工作流执行失败'}`.trim() };
          }
          const answer = evt.outputs?.answer ?? evt.data?.outputs?.answer;
          return { role: 'assistant', content: previousContent.trim() ? previousContent : (answer ?? previousContent) };
        }
        case 'human_input_required': {
          const title = evt.data?.reasons?.[0]?.node_title ?? '人工输入节点';
          const hint = `\n\n> ⏸ 工作流暂停，等待 **${title}** 的人工输入（form_token 已就绪，可调用 /v1/form/human_input/{token} 提交）。`;
          return { role: 'assistant', content: `${previousContent}${hint}` };
        }
        case 'workflow_paused': {
          // 已经在上面写入 conversation_id；这里只追加占位提示（如果之前没有内容）
          if (previousContent) return { role: 'assistant', content: previousContent };
          return {
            role: 'assistant',
            content: '> ⏸ 工作流已暂停，等待人工输入。',
          };
        }
        case 'agent_approval_required': {
          const approval = evt.data?.approval;
          const runId = evt.data?.run_id;
          if (runId && approval?.approvalId && approval.toolName) {
            emitAgentApprovalRequired({
              runId,
              conversationId: cid,
              approvalId: approval.approvalId,
              toolName: approval.toolName,
              toolInput: approval.toolInput ?? '{}',
            });
            const hint = `\n\n> Agent 请求执行工具 **${approval.toolName}**，正在等待人工审批。`;
            return { role: 'assistant', content: `${previousContent}${hint}` };
          }
          return { role: 'assistant', content: previousContent };
        }
        case 'error': {
          if (this.streamFailed) return { role: 'assistant', content: previousContent };
          this.streamFailed = true;
          const msg = evt.message || `Chat 流式响应错误 (${evt.code ?? evt.status ?? 'unknown'})`;
          return { role: 'assistant', content: `${previousContent}\n\n请求失败：${msg}`.trim() };
        }
        default:
          // ping / workflow_started / node_started / node_finished
          // / tts_message / tts_message_end / message_file 等事件不影响文本内容
          return { role: 'assistant', content: previousContent };
      }
    } catch (error) {
      console.error('Chat transformMessage parse error', error, rawData);
      return { role: 'assistant', content: `${previousContent}\n\n响应数据解析失败，请重试。`.trim() };
    }
  }
}

interface ProviderCacheEntry {
  provider: ChatProvider;
  conversationId: string;
}

const providerCaches = new Map<string, ProviderCacheEntry>();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 会话 key 是否为 Chat 服务端会话 ID（UUID 形如），用于区分本地引导项与真实会话 */
export function isServerConversationId(conversationKey: string): boolean {
  return UUID_RE.test(conversationKey);
}

export function providerFactory(conversationKey: string): ChatProvider {
  const cached = providerCaches.get(conversationKey);
  if (cached) return cached.provider;

  // 若 conversationKey 形如 UUID，则视为 Chat 已存在会话 ID，预置进去以续聊
  const initialConversationId = UUID_RE.test(conversationKey) ? conversationKey : '';
  const entry: ProviderCacheEntry = {
    conversationId: initialConversationId,
    provider: undefined as unknown as ChatProvider,
  };

  const provider = new ChatProvider(
    {
      request: XRequest<ChatInput, ChatSSEChunk>(ENDPOINT, {
        manual: true,
        middlewares: {
          onResponse: checkChatResponse,
          onRequest: async (url, requestOptions) => {
            const headers = new Headers(requestOptions.headers as HeadersInit);
            headers.delete('Authorization');
            const authorization = getHostAuthHeaders().Authorization;
            if (authorization) headers.set('Authorization', authorization);
            return [url, { ...requestOptions, headers: Object.fromEntries(headers.entries()) }];
          },
        },
        headers: {
          'Content-Type': 'application/json',
          ...(HOST_ACCESS_MODE === 'debug' && HOST_WORKFLOW_ID
            ? { 'X-Workflow-Id': HOST_WORKFLOW_ID }
            : {}),
          // 宿主项目登录态: Authorization = "Bearer <jwt>"
          // 与主项目 axios 拦截器同一契约, 后端自研网关直接读。
          // 请求中间件会在每次发送时刷新认证头，避免沿用过期 token。
          ...getHostAuthHeaders(),
        },
        // response_mode 由 transformParams 注入
      }),
    },
    {
      conversationKey,
      getConversationId: () => entry.conversationId,
      setConversationId: (id: string) => {
        entry.conversationId = id;
      },
    },
  );

  entry.provider = provider;
  providerCaches.set(conversationKey, entry);
  return provider;
}

export function clearProvider(conversationKey: string): void {
  providerCaches.delete(conversationKey);
}

// =====================================================================
//  文件上传：POST /chatapi/agent-start/files/upload
// =====================================================================

export interface ChatUploadedFile {
  id: string;
  name: string;
  size: number;
  extension: string;
  mime_type: string;
  created_by: number | string;
  created_at: number;
}

export type ChatFileType = 'document' | 'image' | 'audio' | 'video' | 'custom';

export type ChatFileRef =
  | { type: ChatFileType; transfer_method: 'remote_url'; url: string }
  | { type: ChatFileType; transfer_method: 'local_file'; upload_file_id: string };

const IMAGE_EXT = ['png', 'jpg', 'jpeg', 'webp', 'gif', 'svg'];
const AUDIO_EXT = ['mp3', 'm4a', 'wav', 'webm', 'mpga'];
const VIDEO_EXT = ['mp4', 'mov', 'mpeg', 'webm'];
const DOC_EXT = [
  'txt', 'md', 'markdown', 'mdx', 'pdf', 'html', 'xlsx', 'xls', 'vtt',
  'properties', 'doc', 'docx', 'csv', 'eml', 'msg', 'pptx', 'ppt', 'xml', 'epub',
];

export function inferChatFileType(meta: Pick<ChatUploadedFile, 'extension' | 'mime_type'>): ChatFileType {
  const ext = (meta.extension || '').toLowerCase().replace(/^\./, '');
  const mime = (meta.mime_type || '').toLowerCase();
  if (mime.startsWith('image/') || IMAGE_EXT.includes(ext)) return 'image';
  if (mime.startsWith('audio/') || AUDIO_EXT.includes(ext)) return 'audio';
  if (mime.startsWith('video/') || VIDEO_EXT.includes(ext)) return 'video';
  if (DOC_EXT.includes(ext)) return 'document';
  return 'custom';
}

export function toLocalFileRef(
  uploaded: ChatUploadedFile,
  type?: ChatFileType,
): ChatFileRef {
  return {
    type: type ?? inferChatFileType(uploaded),
    transfer_method: 'local_file',
    upload_file_id: uploaded.id,
  };
}

export function toRemoteFileRef(url: string, type: ChatFileType = 'image'): ChatFileRef {
  return { type, transfer_method: 'remote_url', url };
}

// =====================================================================
//  标准会话历史 API（spring-agent-start completion + web）
// =====================================================================

interface ApiEnvelope<T> {
  code?: string;
  message?: string;
  data?: T;
}

export interface ChatConversation {
  conversationId: string;
  name?: string | null;
  userId?: string | null;
  firstMessage?: string | null;
  updatedAt?: string | null;
  pinned?: boolean;
}

export interface ChatHistoryMessage {
  role?: 'SYSTEM' | 'USER' | 'ASSISTANT' | 'TOOL' | string | null;
  content?: string | null;
}

function requireAppId(): string {
  if (HOST_APP_ID) return HOST_APP_ID;
  throw new Error('缺少 appId，无法加载聊天记录');
}

async function readApiData<T>(response: Response, action: string): Promise<T> {
  const raw = await response.text();
  let envelope: ApiEnvelope<T> | undefined;
  try {
    envelope = raw ? (JSON.parse(raw) as ApiEnvelope<T>) : undefined;
  } catch {
    // 后续统一生成包含原始响应的错误。
  }
  if (!response.ok || !envelope || (envelope.code && envelope.code !== 'ok')) {
    throw new Error(
      `${action}失败 (${response.status}): ${envelope?.message || raw || response.statusText}`,
    );
  }
  return envelope.data as T;
}

export async function listChatConversations(
  options: { limit?: number; signal?: AbortSignal } = {},
): Promise<ChatConversation[]> {
  const appId = requireAppId();
  const response = await fetch(`${API_BASE}/conversations`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...getHostAuthHeaders(),
    },
    body: JSON.stringify({ appId, limit: options.limit ?? 50 }),
    signal: options.signal,
  });
  return readApiData<ChatConversation[]>(response, '会话列表加载');
}

export async function deleteChatConversation(
  conversationId: string,
  options: { signal?: AbortSignal } = {},
): Promise<void> {
  const appId = requireAppId();
  const response = await fetch(
    `${API_BASE}/apps/${encodeURIComponent(appId)}/conversations/${encodeURIComponent(conversationId)}`,
    {
      method: 'DELETE',
      headers: { Accept: 'application/json', ...getHostAuthHeaders() },
      signal: options.signal,
    },
  );
  await readApiData<null>(response, '删除会话');
}

function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        reject(new DOMException('Aborted', 'AbortError'));
      },
      { once: true },
    );
  });
}

/**
 * 读取指定会话当前的标题（name）。Chat 在首次响应后异步生成标题，
 * 这里轮询会话列表若干次，直到拿到非空标题或重试用尽。
 */
export async function getChatConversationName(
  conversationId: string,
  options: { retries?: number; delayMs?: number; signal?: AbortSignal } = {},
): Promise<string | null> {
  const retries = Math.max(1, options.retries ?? 3);
  for (let attempt = 0; attempt < retries; attempt += 1) {
    const conversations = await listChatConversations({
      limit: 100,
      signal: options.signal,
    });
    const name = conversations.find((c) => c.conversationId === conversationId)?.name?.trim();
    if (name) return name;
    if (attempt < retries - 1) await delay(options.delayMs ?? 800, options.signal);
  }
  return null;
}

// =====================================================================
//  会话历史消息：POST /chat/conversations/{appId}/{conversationId}/messages
// =====================================================================
export async function loadChatHistory(
  conversationKey: string,
  options: { limit?: number; signal?: AbortSignal } = {},
): Promise<DefaultMessageInfo<XModelMessage>[] | null> {
  if (!isServerConversationId(conversationKey)) return null;
  const appId = requireAppId();
  const response = await fetch(
    `${API_BASE}/conversations/messages`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...getHostAuthHeaders(),
      },
      body: JSON.stringify({
        appId,
        conversationId: conversationKey,
        limit: options.limit ?? 100,
      }),
      signal: options.signal,
    },
  );
  const messages = await readApiData<ChatHistoryMessage[]>(response, '历史消息加载');
  return messages
    .filter((item) => item.content != null && item.role !== 'SYSTEM')
    .map((item, index) => ({
      id: `${conversationKey}-${index}`,
      message: {
        role: item.role === 'USER' ? 'user' : 'assistant',
        content: item.content ?? '',
      },
      status: 'success' as const,
    }));
}

export async function uploadChatFile(file: File, user?: string): Promise<ChatUploadedFile> {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('user', user ?? resolveUser());

  const resp = await fetch(`${API_BASE}/files/upload`, {
    method: 'POST',
    headers: getHostAuthHeaders(),
    body: fd,
  });

  if (!resp.ok) {
    let detail = '';
    try {
      detail = await resp.text();
    } catch {
      /* noop */
    }
    throw new Error(`Chat 上传失败 (${resp.status}): ${detail || resp.statusText}`);
  }
  return (await resp.json()) as ChatUploadedFile;
}
