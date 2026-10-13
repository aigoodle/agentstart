/** 当前活跃页面协议读取：definition + data + tools。 */

import { getHostContext, isHostManaged } from './host-bridge';

export const PAGE_CONTEXT_KEY = 'page_context';

export interface PageContextSnapshot {
  sceneId: string;
  version: number;
  kind: 'drawer' | 'modal' | 'page';
  title: string;
  description?: string;
  routePath: string;
  definition: Record<string, unknown>;
  data: Record<string, unknown>;
  tools: unknown[];
  updatedAt: string;
}

/** 嵌入时使用宿主实时上下文；独立打开时回退到当前站点 localStorage。 */
export function readPageContext(): PageContextSnapshot | null {
  if (typeof window === 'undefined') return null;
  if (isHostManaged)
    return (getHostContext()?.page as PageContextSnapshot | null) ?? null;
  try {
    const raw = window.localStorage.getItem(PAGE_CONTEXT_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PageContextSnapshot | null;
    if (
      !parsed ||
      typeof parsed !== 'object' ||
      typeof parsed.sceneId !== 'string' ||
      !parsed.definition ||
      !parsed.data
    ) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** 每次请求调用，确保提交的是当前页面/当前弹窗的最新协议。 */
export function buildPageContextInputs(): Record<string, string> {
  const page = readPageContext();
  if (!page) return {};
  return {
    page_context: JSON.stringify(page),
    page_context_updated_at: page.updatedAt ?? '',
  };
}
