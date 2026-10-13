/**
 * 宿主通讯桥的 React 适配 hooks。
 * 逻辑层见 `./host-bridge`，这里只把订阅包成 React 状态。
 */
import { useEffect, useState } from 'react';

import type { HostContext } from './host-bridge';
import {
  getDocked,
  getHostContext,
  subscribeCommand,
  subscribeContext,
  subscribeDock,
  subscribeOpen,
} from './host-bridge';

/** 订阅宿主下发的上下文（token / 用户 / 主题 / 语言） */
export function useHostContext(): HostContext | null {
  const [ctx, setCtx] = useState<HostContext | null>(getHostContext());
  useEffect(() => subscribeContext(setCtx), []);
  return ctx;
}

/** 是否处于深色主题（跟随宿主下发的 theme；独立打开时默认浅色） */
export function useIsDark(): boolean {
  const ctx = useHostContext();
  return ctx?.theme === 'dark';
}

/** 订阅气泡面板的开合状态（可用于打开时聚焦输入框等） */
export function useHostOpen(): boolean {
  const [open, setOpen] = useState(false);
  useEffect(() => subscribeOpen(setOpen), []);
  return open;
}

/** 订阅侧边停靠状态（按宿主回执同步停靠按钮的高亮） */
export function useHostDock(): boolean {
  const [dock, setDock] = useState(getDocked());
  useEffect(() => subscribeDock(setDock), []);
  return dock;
}

/** 订阅宿主下发的通用指令 */
export function useHostCommand(handler: (payload: unknown) => void) {
  useEffect(() => subscribeCommand(handler), [handler]);
}
