/**
 * 宿主应用 ↔ 聊天页（iframe）通讯桥
 * --------------------------------------------------
 * 本聊天页被宿主用 <iframe src="/chat/"> 嵌入，悬浮气泡里展示。
 * 宿主装载器就是本项目的 `public/chat-frame.js`；vben 侧仅保留业务上下文适配。
 *
 * postMessage 协议（信封：{ channel, type, payload }）：
 *   子→父：chat:ready（就绪握手）/ chat:close（请求关闭气泡）/ chat:event（业务通知）
 *   父→子：host:context（token/用户/主题/语言）/ host:open / host:close / host:command
 *
 * 安全：收消息用 `event.source === window.parent` 校验来源；
 * 发消息的 targetOrigin 先取 referrer，收到首条消息后用其 origin 收敛。
 */

export const HOST_CHANNEL = 'boot-school-chat';

export interface HostUser {
  avatar?: string;
  realName?: string;
  roles?: string[];
  userId?: string;
  username?: string;
}

export interface AssistantCapabilityConfig {
  color?: 'rose' | 'sky' | 'teal' | 'violet';
  description?: string;
  icon?: 'chart' | 'flow' | 'help' | 'rocket';
  prompt?: string;
  title: string;
}

export interface AssistantHomeConfig {
  capabilities?: AssistantCapabilityConfig[];
  capabilitiesTitle?: string;
  description?: string;
  footer?: string;
  subtitle?: string;
  suggestions?: string[];
  suggestionsTitle?: string;
  title?: string;
}

export interface AssistantConfig {
  home?: AssistantHomeConfig;
  title?: string;
}

export interface HostContext {
  assistant?: AssistantConfig | null;
  app_name?: string | null;
  path?: string;
  platform?: string;
  route?: Record<string, unknown> | null;
  busData?: Record<string, unknown> | null;
  page?: Record<string, unknown> | null;
  locale: string;
  theme: 'dark' | 'light';
  token: null | string;
  user: HostUser | null;
}

interface Envelope {
  channel: string;
  payload?: unknown;
  type: string;
}

/** 是否处于宿主 iframe 中（独立打开 /chat/ 时为 false，通讯能力退化为空操作） */
export const isEmbedded =
  typeof window !== 'undefined' && window.parent !== window;

/** 只有 chat-frame SDK 管理的 iframe 要等待握手；控制台调试 iframe 保留独立模式。 */
export const isHostManaged = isEmbedded && new URLSearchParams(window.location.search).get('hostBridge') === '1';

let parentOrigin = '*';
let context: HostContext | null = null;
let opened = false;
let docked = false;
let inited = false;

const contextSubs = new Set<(ctx: HostContext | null) => void>();
const openSubs = new Set<(open: boolean) => void>();
const dockSubs = new Set<(docked: boolean) => void>();
const commandSubs = new Set<(payload: unknown) => void>();

function initParentOrigin() {
  if (typeof document === 'undefined' || !document.referrer) {
    return;
  }
  try {
    parentOrigin = new URL(document.referrer).origin;
  } catch {
    parentOrigin = '*';
  }
}

/** 向宿主发送一条消息 */
export function sendToHost(type: string, payload?: unknown) {
  if (!isEmbedded) {
    return;
  }
  const message: Envelope = { channel: HOST_CHANNEL, type, payload };
  window.parent.postMessage(message, parentOrigin);
}

/** 请求宿主关闭气泡面板 */
export function requestClose() {
  sendToHost('chat:close');
}

/** 请求宿主进入/退出侧边停靠（压缩主应用、面板固定右侧满高） */
export function requestDock(next: boolean) {
  sendToHost('chat:dock', { docked: next });
}

/** 将 iframe 标题栏的拖拽坐标转发给宿主，由宿主移动整个悬浮窗口。 */
export function requestWindowDrag(
  phase: 'start' | 'move' | 'end',
  point: { screenX: number; screenY: number },
) {
  sendToHost('chat:window-drag', { phase, ...point });
}

/** 向宿主发送业务通知（如未读数、标题变化等） */
export function notifyHost(name: string, data?: unknown) {
  sendToHost('chat:event', { name, data });
}

/** 回写当前页面的业务槽；宿主校验当前路由后存储并回发快照。 */
export function updateHostData(name: string, data: unknown, desc = '') {
  sendToHost('chat:bus_data', { routePath: context?.path, slot: { name, data, desc } });
}

function handleMessage(event: MessageEvent) {
  if (event.source !== window.parent) {
    return;
  }
  if (parentOrigin !== '*' && event.origin !== parentOrigin) return;
  const data = event.data as Envelope | null;
  if (!data || data.channel !== HOST_CHANNEL || !data.type) {
    return;
  }
  // 用首条可信消息的 origin 收敛 targetOrigin
  if (parentOrigin === '*' && event.origin) {
    parentOrigin = event.origin;
  }
  switch (data.type) {
    case 'host:bus_data': {
      const payload = data.payload as { routePath?: string; data?: Record<string, unknown> | null } | null;
      if (context && payload && payload.routePath === context.path) {
        context = { ...context, busData: payload.data ?? null };
      }
      break;
    }
    case 'host:command': {
      commandSubs.forEach((fn) => fn(data.payload));
      break;
    }
    case 'host:context': {
      context = (data.payload as HostContext) ?? null;
      contextSubs.forEach((fn) => fn(context));
      break;
    }
    case 'host:open':
    case 'host:close': {
      opened = data.type === 'host:open';
      openSubs.forEach((fn) => fn(opened));
      break;
    }
    case 'host:dock': {
      docked = !!(data.payload as { docked?: boolean } | undefined)?.docked;
      dockSubs.forEach((fn) => fn(docked));
      break;
    }
    default: {
      break;
    }
  }
}

/**
 * 初始化通讯桥：注册监听并向宿主发送 chat:ready。
 * 在应用入口调用一次即可（重复调用安全）。
 */
export function initHostBridge() {
  if (inited || !isEmbedded) {
    return;
  }
  inited = true;
  initParentOrigin();
  window.addEventListener('message', handleMessage);
  // 通知宿主聊天页已就绪 → 宿主随后回发 host:context
  sendToHost('chat:ready');
}

export function getHostContext(): HostContext | null {
  return context;
}

export function subscribeContext(fn: (ctx: HostContext | null) => void) {
  contextSubs.add(fn);
  fn(context);
  return () => {
    contextSubs.delete(fn);
  };
}

export function subscribeOpen(fn: (open: boolean) => void) {
  openSubs.add(fn);
  return () => {
    openSubs.delete(fn);
  };
}

export function getDocked(): boolean {
  return docked;
}

export function subscribeDock(fn: (docked: boolean) => void) {
  dockSubs.add(fn);
  return () => {
    dockSubs.delete(fn);
  };
}

export function subscribeCommand(fn: (payload: unknown) => void) {
  commandSubs.add(fn);
  return () => {
    commandSubs.delete(fn);
  };
}
