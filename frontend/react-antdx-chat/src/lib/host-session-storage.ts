/**
 * iframe 使用 host:context 的最新会话信息，不跨域读取宿主 storage。
 * 独立聊天页保留本地会话快照读取。宿主统一入口为 utils/ai-assistant.ts。
 */

import { getHostContext, isHostManaged } from './host-bridge';

export const SESSION_STORAGE_KEY = 'boot-school-ai-session';

export interface HostSession {
  app_name: string | null;
  nickName: string | null;
  path: string | null;
  platform: string;
  token: string | null;
  user_id: string | null;
}

/**
 * 嵌入模式读取消息快照，独立模式读取本地快照；未就绪返回 null。
 */
export function readHostSession(): HostSession | null {
  if (typeof window === 'undefined') return null;
  if (isHostManaged) {
    const context = getHostContext();
    if (!context) return null;
    return {
      app_name: context.app_name ?? null,
      nickName: context.user?.realName ?? null,
      user_id: context.user?.userId ?? null,
      path: context.path ?? null,
      platform: context.platform ?? 'admin',
      token: context.token,
    };
  }
  try {
    const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<HostSession> | null;
    if (!parsed || typeof parsed !== 'object') return null;
    return {
      app_name: parsed.app_name ?? null,
      nickName: parsed.nickName ?? null,
      path: parsed.path ?? null,
      platform: parsed.platform ?? 'admin',
      token: parsed.token ?? null,
      user_id: parsed.user_id ?? null,
    };
  } catch {
    return null;
  }
}

/**
 * 出站请求要带的宿主认证头。
 * -------------------------------------------------------------------
 * 自研 Agent 网关使用标准 Authorization: Bearer <jwt>。
 * ai-assistant.ts 已补齐 Bearer 前缀；未取得登录上下文时返回空对象。
 */
export function getHostAuthHeaders(): Record<string, string> {
  const session = readHostSession();
  if (!session?.token) return {};
  return { Authorization: session.token };
}
