import { useXChat, useXConversations } from '@ant-design/x-sdk';
import type { XModelMessage } from '@ant-design/x-sdk';
import { message } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import locale from '../_utils/locale';
import {
  clearProvider,
  deleteChatConversation,
  getChatConversationName,
  isServerConversationId,
  listChatConversations,
  loadChatHistory,
  onChatConversationCreated,
  onAgentApprovalRequired,
  providerFactory,
  resolveAgentApproval,
} from './provider';
import type { AgentApprovalRequiredEvent, ChatInput } from './provider';

// 新建会话先用本地临时 key（非 UUID，触发欢迎页）；首次响应后切换为真实标题。
export const newConversationKey = () =>
  `new-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

/**
 * 封装与 Chat 对接的全部聊天状态与操作，供全屏布局（App）与助手布局（Copilot）共用，
 * 保证两种界面共享同一套会话/历史/删除/标题刷新逻辑，互不分叉。
 */
export function useChat() {
  const { conversations, setConversations } = useXConversations({
    defaultConversations: [],
  });
  // 本地临时 key → Chat 真实会话 ID 的映射：保证新建会话首响后仍以原 key 高亮且可续聊
  const localKeyByConversationId = useRef<Map<string, string>>(new Map());
  const [curConversation, setCurConversation] = useState<string>(() => newConversationKey());
  const [activeConversation, setActiveConversation] = useState<string>();

  const { onRequest, messages, isRequesting, abort, onReload, setMessages } = useXChat<
    XModelMessage,
    XModelMessage,
    ChatInput
  >({
    provider: providerFactory(curConversation),
    conversationKey: curConversation,
    // 切换会话时按 conversationKey 懒加载服务端历史消息；
    // 本地新建会话（非 UUID）返回空，显示欢迎页。同一会话只加载一次（store 按 key 缓存）。
    defaultMessages: async (info?: { conversationKey?: string }) => {
      const key = String(info?.conversationKey ?? '');
      try {
        const history = await loadChatHistory(key);
        return history ?? [];
      } catch (err) {
        console.warn('[Chat] 历史消息加载失败', err);
        return [];
      }
    },
    requestPlaceholder: () => ({ role: 'assistant', content: locale.noData }),
    requestFallback: (_, { error, errorInfo, messageInfo }) => {
      if (error.name === 'AbortError') {
        const fallback =
          typeof messageInfo?.message?.content === 'string'
            ? messageInfo.message.content
            : locale.requestAborted;
        return { role: 'assistant', content: fallback };
      }
      return {
        role: 'assistant',
        content:
          (errorInfo as { error?: { message?: string } } | undefined)?.error?.message ??
          error.message ??
          locale.requestFailed,
      };
    },
  });

  const [messageApi, contextHolder] = message.useMessage();
  const [pendingApproval, setPendingApproval] = useState<AgentApprovalRequiredEvent | null>(null);
  const [approvalResolving, setApprovalResolving] = useState(false);

  useEffect(() => onAgentApprovalRequired(setPendingApproval), []);

  const decideApproval = useCallback(async (decision: 'APPROVE' | 'DENY') => {
    if (!pendingApproval || approvalResolving) return;
    setApprovalResolving(true);
    try {
      const response = await resolveAgentApproval(pendingApproval, decision);
      if (response.text) {
        setMessages((current) => [...current, {
          id: `agent-resume-${response.runId}-${Date.now()}`,
          status: response.status === 'FAILED' ? 'error' : 'success',
          message: { role: 'assistant', content: response.text ?? response.error ?? '' },
        }]);
      }
      if (response.status === 'AWAITING_APPROVAL' && response.pendingApproval) {
        setPendingApproval({
          runId: response.runId,
          conversationId: response.conversationId,
          ...response.pendingApproval,
        });
      } else {
        setPendingApproval(null);
      }
    } catch (error) {
      const text = error instanceof Error ? error.message : String(error);
      void messageApi.error(`审批失败：${text}`);
    } finally {
      setApprovalResolving(false);
    }
  }, [approvalResolving, messageApi, pendingApproval, setMessages]);

  // 从 Chat /conversations 拉取会话列表。已在本地激活的新会话用其本地 key 回填
  // （localKeyByConversationId），保证高亮与续聊连续。
  const refreshConversations = useCallback(
    async (signal?: AbortSignal) => {
      const records = await listChatConversations({ limit: 50, signal });
      const now = dayjs();
      const yesterday = now.subtract(1, 'day');
      const items = records.map((c) => {
        const updated = dayjs(c.updatedAt);
        const group = updated.isSame(now, 'day')
          ? locale.today
          : updated.isSame(yesterday, 'day')
            ? locale.yesterday
            : updated.format('YYYY-MM-DD');
        return {
          key: localKeyByConversationId.current.get(c.conversationId) ?? c.conversationId,
          label: c.name?.trim() || locale.newConversation,
          group,
        };
      });
      setConversations(items);
    },
    [setConversations],
  );

  // 首次挂载：加载服务端会话列表
  useEffect(() => {
    const ac = new AbortController();
    refreshConversations(ac.signal).catch((err: unknown) => {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      console.warn('[Chat] 会话列表加载失败', err);
    });
    return () => ac.abort();
  }, [refreshConversations]);

  // 新会话首次响应结束 → Chat 已落地会话并生成标题，等标题就绪后刷新整张列表
  useEffect(() => {
    const ac = new AbortController();
    const off = onChatConversationCreated(({ conversationKey, conversationId }) => {
      localKeyByConversationId.current.set(conversationId, conversationKey);
      getChatConversationName(conversationId, { signal: ac.signal })
        .then(() => refreshConversations(ac.signal))
        .catch((err: unknown) => {
          if (err instanceof DOMException && err.name === 'AbortError') return;
          console.warn('[Chat] 会话标题刷新失败', err);
        });
    });
    return () => {
      off();
      ac.abort();
    };
  }, [refreshConversations]);

  // 侧栏项 key 解析为 Chat 真实会话 ID：UUID key 即本身；本地 key 走反查映射
  const resolveConversationId = useCallback((key: string): string => {
    if (isServerConversationId(key)) return key;
    for (const [conversationId, localKey] of localKeyByConversationId.current) {
      if (localKey === key) return conversationId;
    }
    return key;
  }, []);

  // 发送消息：交给 Chat provider 流式请求，并把当前会话标记为活跃。返回是否真正发送。
  const sendMessage = useCallback(
    (val: string): boolean => {
      const text = val?.trim();
      if (!text) return false;
      onRequest({ messages: [{ role: 'user', content: text }] });
      setActiveConversation(curConversation);
      return true;
    },
    [onRequest, curConversation],
  );

  // 新建会话：当前会话为空则提示，否则切到新的本地临时 key（欢迎页）。返回是否新建。
  const newConversation = useCallback((): boolean => {
    if (messages.length === 0) {
      void messageApi.error(locale.itIsNowANewConversation);
      return false;
    }
    setCurConversation(newConversationKey());
    return true;
  }, [messages.length, messageApi]);

  // 删除会话：调用 Chat 删除接口 → 清理本地映射/provider 缓存 → 更新侧栏与当前选中
  const deleteConversation = useCallback(
    async (key: string) => {
      const conversationId = resolveConversationId(key);
      if (isServerConversationId(conversationId)) {
        try {
          await deleteChatConversation(conversationId);
        } catch (err) {
          console.warn('[Chat] 删除会话失败', err);
          void messageApi.error(locale.deleteFailed);
          return;
        }
      }
      localKeyByConversationId.current.delete(conversationId);
      clearProvider(key);
      setConversations(conversations.filter((item) => item.key !== key));
      setCurConversation((cur) => (cur === key ? newConversationKey() : cur));
      void messageApi.success(locale.deleteSuccess);
    },
    [resolveConversationId, messageApi, setConversations, conversations],
  );

  return {
    contextHolder,
    conversations,
    curConversation,
    setCurConversation,
    activeConversation,
    setActiveConversation,
    messages,
    isRequesting,
    abort,
    onReload,
    onRequest,
    messageApi,
    refreshConversations,
    resolveConversationId,
    sendMessage,
    newConversation,
    deleteConversation,
    pendingApproval,
    approvalResolving,
    decideApproval,
  };
}
