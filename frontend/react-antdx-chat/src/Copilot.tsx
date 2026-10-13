import {
  ApartmentOutlined,
  BarChartOutlined,
  CloseOutlined,
  CommentOutlined,
  CopyOutlined,
  DeleteOutlined,
  LayoutOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { Actions, Bubble, Conversations, Sender, Welcome, XProvider } from '@ant-design/x';
import type { BubbleListProps } from '@ant-design/x';
import type { BubbleListRef } from '@ant-design/x/es/bubble';
import XMarkdown from '@ant-design/x-markdown';
import { Button, Popover, Space } from 'antd';
import type { GetRef } from 'antd';
import { clsx } from 'clsx';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import locale from './_utils/locale';
import ActBtn from './components/ActBtn';
import AiBotIcon from './components/AiBotIcon';
import AssistantThinking from './components/AssistantThinking';
import AgentApprovalCard from './components/AgentApprovalCard';
import BuildPanel from './components/BuildPanel';
import type { AssistantCapabilityConfig, AssistantHomeConfig } from './lib/host-bridge';
import { isEmbedded, requestClose, requestDock, requestWindowDrag } from './lib/host-bridge';
import { useChat } from './lib/useChat';
import { useHostContext, useHostDock, useIsDark } from './lib/useHostBridge';

// ====== 能力卡片 / 推荐提问 (面向 PC 后台业务) ======
type CapColor = {
  bg: string;
  bgDark: string;
  iconBg: string;
  iconColor: string;
  ringDark: string;
};

const CAP_PALETTES: Record<string, CapColor> = {
  sky: {
    bg: 'linear-gradient(135deg,#f0f9ff 0%,#e0f2fe 100%)',
    bgDark: 'linear-gradient(135deg,rgba(56,189,248,0.12) 0%,rgba(59,130,246,0.10) 100%)',
    iconBg: '#ffffff',
    iconColor: '#0ea5e9',
    ringDark: 'rgba(56,189,248,0.25)',
  },
  violet: {
    bg: 'linear-gradient(135deg,#faf5ff 0%,#ede9fe 100%)',
    bgDark: 'linear-gradient(135deg,rgba(167,139,250,0.14) 0%,rgba(139,92,246,0.10) 100%)',
    iconBg: '#ffffff',
    iconColor: '#8b5cf6',
    ringDark: 'rgba(167,139,250,0.25)',
  },
  teal: {
    bg: 'linear-gradient(135deg,#f0fdfa 0%,#ccfbf1 100%)',
    bgDark: 'linear-gradient(135deg,rgba(20,184,166,0.14) 0%,rgba(13,148,136,0.10) 100%)',
    iconBg: '#ffffff',
    iconColor: '#14b8a6',
    ringDark: 'rgba(20,184,166,0.25)',
  },
  rose: {
    bg: 'linear-gradient(135deg,#fff1f2 0%,#ffe4e6 100%)',
    bgDark: 'linear-gradient(135deg,rgba(244,114,182,0.14) 0%,rgba(225,29,72,0.10) 100%)',
    iconBg: '#ffffff',
    iconColor: '#f43f5e',
    ringDark: 'rgba(244,114,182,0.25)',
  },
};

type Capability = {
  name: string;
  desc: string;
  icon: React.ReactNode;
  palette: CapColor;
  prompt: string;
};

const CAPABILITIES: Capability[] = [
  {
    name: '系统使用',
    desc: '菜单功能讲解',
    icon: <QuestionCircleOutlined />,
    palette: CAP_PALETTES.sky,
    prompt: '我是新用户, 请简单介绍这个系统的主要菜单和功能模块。',
  },
  {
    name: '业务流程',
    desc: '一图看懂业务',
    icon: <ApartmentOutlined />,
    palette: CAP_PALETTES.violet,
    prompt: '请帮我梳理一遍客户从注册到完成首次业务办理的完整流程。',
  },
  {
    name: '数据查询',
    desc: '统计看板指引',
    icon: <BarChartOutlined />,
    palette: CAP_PALETTES.teal,
    prompt: '管理者想查看本周核心指标, 该打开哪个工作台? 数据怎么解读?',
  },
  {
    name: '操作演练',
    desc: '一步步教学',
    icon: <RocketOutlined />,
    palette: CAP_PALETTES.rose,
    prompt: '请一步步教我如何在系统里发布一条带阅读统计的通知。',
  },
];

const CAP_ICONS = {
  chart: <BarChartOutlined />,
  flow: <ApartmentOutlined />,
  help: <QuestionCircleOutlined />,
  rocket: <RocketOutlined />,
};

function capabilityFromConfig(item: AssistantCapabilityConfig, index: number): Capability {
  const paletteNames = ['sky', 'violet', 'teal', 'rose'] as const;
  const color = item.color && CAP_PALETTES[item.color]
    ? item.color
    : paletteNames[index % paletteNames.length];
  const icon = item.icon && CAP_ICONS[item.icon] ? CAP_ICONS[item.icon] : CAP_ICONS.help;
  return {
    name: item.title,
    desc: item.description ?? '',
    icon,
    palette: CAP_PALETTES[color],
    prompt: item.prompt ?? item.title,
  };
}

const ALL_SUGGESTIONS = [
  '业务计划如何提交审核并发布?',
  '账单怎么批量生成并发送提醒?',
  '日程冲突怎么自动检测和处理?',
  '请假审批流程怎么发起?',
  '系统的基础参数在哪里配置?',
  '成员工作量统计在哪里查看?',
  '通知公告发布后怎么看签收率?',
  '用户画像在哪个菜单查看?',
  '运营大屏和决策大屏怎么打开?',
  '访客白名单和批次有什么区别?',
  '监控设备如何接入系统?',
  '评价体系怎么配置评分和徽章?',
];

function pickSome<T>(arr: T[], n: number): T[] {
  const copy = [...arr];
  const out: T[] = [];
  while (out.length < n && copy.length) {
    const idx = Math.floor(Math.random() * copy.length);
    out.push(copy.splice(idx, 1)[0]);
  }
  return out;
}

const RECENT_ICON_POOL = ['📐', '📝', '🎯', '🧪', '📚', '🎨', '🧮', '🤖', '🔬', '🪐'];

// XMarkdown 自定义标签：把流式返回的 <welcome data-icon="..." title="..." data-description="..."/> 映射到 @ant-design/x 的 Welcome
// html-react-parser 会把 HTML 属性按小写/带连字符原样透传，因此这里从 props 里读 kebab-case key
type WelcomeMdProps = {
  title?: React.ReactNode;
  'data-icon'?: string;
  'data-description'?: React.ReactNode;
  children?: React.ReactNode;
};
const WelcomeMd: React.FC<WelcomeMdProps> = (props) => {
  const iconUrl = props['data-icon'];
  return (
    <Welcome
      variant="borderless"
      icon={
        iconUrl ? (
          <img
            src={iconUrl}
            alt=""
            style={{ width: 48, height: 48, objectFit: 'contain' }}
          />
        ) : undefined
      }
      title={props.title}
      description={props['data-description']}
      extra={props.children}
    />
  );
};

const X_MARKDOWN_COMPONENTS = {
  welcome: WelcomeMd,
  'act-btn': ActBtn,
  'build-panel': BuildPanel,
} as unknown as React.ComponentProps<typeof XMarkdown>['components'];

/**
 * LLM 有时会写成 `<act_btn>` / `<build_panel>` (下划线) —— 但 HTML5 tag 名规范
 * /DOMPurify/流式未闭合检测的正则都只认 `[a-zA-Z0-9-]`，下划线会让整段标签被丢弃。
 * 这里在渲染前统一把带下划线写法归一到带连字符的形式，两种写法都能识别。
 */
function normalizeCustomTags(html: string): string {
  return html
    .replace(/<(\/?)act_btn/gi, '<$1act-btn')
    .replace(/<(\/?)build_panel/gi, '<$1build-panel');
}

// ====== 欢迎页 (会话为空时的丰富空状态) ======
type ConversationItem = {
  key: string | number;
  label?: React.ReactNode;
  timestamp?: number;
};

const WelcomeEmpty: React.FC<{
  isDark: boolean;
  conversations: ConversationItem[];
  onAsk: (q: string) => void;
  onUseCap: (c: Capability) => void;
  onPickHistory: (key: string | number) => void;
  config?: AssistantHomeConfig;
}> = ({ isDark, conversations, onAsk, onUseCap, onPickHistory, config }) => {
  const [seed, setSeed] = useState(0);
  const suggestionPool = config?.suggestions?.length ? config.suggestions : ALL_SUGGESTIONS;
  const configuredCapabilities = config?.capabilities;
  const suggestions = useMemo(() => {
    void seed;
    return pickSome(suggestionPool, 5);
  }, [seed, suggestionPool]);
  const capabilities = useMemo(
    () => configuredCapabilities?.length
      ? configuredCapabilities.map(capabilityFromConfig)
      : CAPABILITIES,
    [configuredCapabilities],
  );

  // 取最近 4 条历史会话作为「最近对话」展示
  const recent = useMemo(() => {
    return conversations.slice(0, 4).map((c, i) => ({
      key: c.key,
      title: typeof c.label === 'string' ? c.label : `会话 ${i + 1}`,
      icon: RECENT_ICON_POOL[i % RECENT_ICON_POOL.length],
    }));
  }, [conversations]);

  return (
    <div className="px-3 pt-3 pb-2">
      {/* ===== 顶部渐变 Banner ===== */}
      <div
        className={clsx(
          'relative overflow-hidden rounded-3xl px-4 py-5',
          isDark
            ? 'bg-[linear-gradient(135deg,rgba(59,130,246,0.18),rgba(139,92,246,0.18)_45%,rgba(244,114,182,0.16))]'
            : 'bg-[linear-gradient(135deg,#e0f2fe_0%,#ede9fe_50%,#fce7f3_100%)]',
        )}
      >
        {/* 装饰圆 */}
        <span
          className="pointer-events-none absolute rounded-full"
          style={{
            top: -40,
            right: -40,
            width: 140,
            height: 140,
            background: isDark ? 'rgba(56,189,248,0.18)' : 'rgba(56,189,248,0.22)',
          }}
        />
        <span
          className="pointer-events-none absolute rounded-full"
          style={{
            bottom: -32,
            left: -32,
            width: 110,
            height: 110,
            background: isDark ? 'rgba(167,139,250,0.16)' : 'rgba(167,139,250,0.22)',
          }}
        />

        <div className="relative flex items-center gap-3">
          <div
            className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-white shadow-md"
            style={{ boxShadow: '0 6px 18px rgba(59,130,246,0.18)' }}
          >
            <AiBotIcon size={36} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-base font-bold text-slate-800 dark:text-zinc-100">
              {config?.title ?? '智能 AI 助手'}
            </div>
            <div className="mt-0.5 text-xs text-slate-500 dark:text-zinc-400">
              {config?.subtitle ?? '7×24 智能陪伴 · 系统使用顾问'}
            </div>
          </div>
        </div>

        <p className="relative mt-3 text-sm leading-relaxed text-slate-600 dark:text-zinc-300">
          {config?.description ?? '您好! 我是您的 AI 助手, 可以帮您答疑解惑、梳理业务、分析数据并提供操作指引。您想问什么呢?'}
        </p>
      </div>

      {/* ===== 能力卡片 ===== */}
      <div className="mt-3 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
        <div className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-slate-800 dark:text-zinc-100">
          <span className="inline-block h-3.5 w-1 rounded-full bg-gradient-to-b from-sky-400 to-violet-500" />
          {config?.capabilitiesTitle ?? 'AI 能力'}
        </div>
        <div className="grid grid-cols-2 gap-2.5">
          {capabilities.map((c) => (
            <button
              key={c.name}
              type="button"
              onClick={() => onUseCap(c)}
              className="group flex items-center gap-2.5 rounded-2xl p-2.5 text-left transition-transform hover:scale-[1.02] active:scale-[0.98]"
              style={{ background: isDark ? c.palette.bgDark : c.palette.bg }}
            >
              <span
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[18px] shadow-sm"
                style={{
                  background: isDark ? 'rgba(255,255,255,0.08)' : c.palette.iconBg,
                  color: c.palette.iconColor,
                  boxShadow: isDark ? `0 0 0 1px ${c.palette.ringDark}` : '0 2px 6px rgba(0,0,0,0.04)',
                }}
              >
                {c.icon}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-slate-800 dark:text-zinc-100">
                  {c.name}
                </span>
                <span className="mt-0.5 block truncate text-xs text-slate-500 dark:text-zinc-400">
                  {c.desc}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* ===== 猜你想问 ===== */}
      <div className="mt-3">
        <div className="mb-2 flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-sm font-semibold text-slate-800 dark:text-zinc-100">
            <span className="inline-block h-3.5 w-1 rounded-full bg-gradient-to-b from-emerald-400 to-teal-500" />
            {config?.suggestionsTitle ?? '猜你想问'}
          </div>
          <button
            type="button"
            onClick={() => setSeed((s) => s + 1)}
            className="flex items-center gap-1 text-xs text-slate-400 hover:text-sky-500 dark:text-zinc-500"
          >
            <ReloadOutlined />
            换一批
          </button>
        </div>
        <div className="flex flex-wrap gap-2">
          {suggestions.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => onAsk(q)}
              className={clsx(
                'rounded-full border px-3 py-1.5 text-xs transition-colors',
                isDark
                  ? 'border-zinc-700 bg-zinc-900/70 text-zinc-300 hover:border-sky-700 hover:text-sky-300'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-sky-300 hover:text-sky-600',
              )}
            >
              {q}
            </button>
          ))}
        </div>
      </div>

      {/* ===== 最近对话 ===== */}
      {recent.length > 0 && (
        <div className="mt-3 rounded-2xl border border-slate-100 bg-white p-3 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
          <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800 dark:text-zinc-100">
            <span className="inline-block h-3.5 w-1 rounded-full bg-gradient-to-b from-rose-400 to-orange-400" />
            最近对话
          </div>
          <div className="flex flex-col">
            {recent.map((h, i) => (
              <button
                key={String(h.key)}
                type="button"
                onClick={() => onPickHistory(h.key)}
                className={clsx(
                  'flex items-center gap-2.5 py-2 text-left transition-colors hover:bg-slate-50 dark:hover:bg-zinc-800/60',
                  i < recent.length - 1 && 'border-b border-dashed border-slate-100 dark:border-zinc-800',
                )}
              >
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-base"
                  style={{
                    background: isDark
                      ? 'linear-gradient(45deg,rgba(34,211,238,0.16),rgba(45,212,191,0.16))'
                      : 'linear-gradient(45deg,#cffafe,#ccfbf1)',
                  }}
                >
                  {h.icon}
                </span>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-zinc-200">
                  {h.title}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="mt-4 text-center text-[11px] text-slate-400 dark:text-zinc-500">
        {config?.footer ?? '✨ 点击任意能力卡片或问题, 即可开启对话'}
      </div>
    </div>
  );
};

const Copilot: React.FC = () => {
  const titleDragPointerRef = useRef<number | null>(null);
  const {
    contextHolder,
    conversations,
    curConversation,
    setCurConversation,
    messages,
    isRequesting,
    abort,
    onReload,
    sendMessage,
    newConversation,
    deleteConversation,
    pendingApproval,
    approvalResolving,
    decideApproval,
  } = useChat();

  const [historyOpen, setHistoryOpen] = useState(false);
  const docked = useHostDock();
  const isDark = useIsDark();
  const hostContext = useHostContext();
  const assistantConfig = hostContext?.assistant ?? undefined;
  const listRef = useRef<BubbleListRef>(null);
  const senderRef = useRef<GetRef<typeof Sender>>(null);

  useEffect(() => {
    senderRef.current?.focus({ cursor: 'end' });
  }, []);

  const submit = (val: string) => {
    if (sendMessage(val)) {
      listRef.current?.scrollTo({ top: 'bottom' });
      senderRef.current?.clear?.();
    }
  };

  const sendTitleDrag = (phase: 'start' | 'move' | 'end', event: React.PointerEvent) => {
    requestWindowDrag(phase, { screenX: event.screenX, screenY: event.screenY });
  };

  const onTitlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isEmbedded || docked || event.button !== 0) return;
    if ((event.target as Element).closest('button, a, input, [data-no-window-drag]')) return;
    event.preventDefault();
    titleDragPointerRef.current = event.pointerId;
    event.currentTarget.setPointerCapture(event.pointerId);
    sendTitleDrag('start', event);
  };

  const onTitlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (titleDragPointerRef.current !== event.pointerId) return;
    event.preventDefault();
    sendTitleDrag('move', event);
  };

  const finishTitleDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    if (titleDragPointerRef.current !== event.pointerId) return;
    titleDragPointerRef.current = null;
    sendTitleDrag('end', event);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  // 重试：找到该助手消息前最近的用户提问内容，重新发起 Chat 请求
  const userQueryFor = (id: string | number): string => {
    const idx = messages.findIndex((m) => m.id === id);
    for (let i = idx - 1; i >= 0; i -= 1) {
      if (messages[i].message.role === 'user') {
        const c = messages[i].message.content;
        return typeof c === 'string' ? c : String((c as { text?: string } | undefined)?.text ?? '');
      }
    }
    return '';
  };

  const roles: BubbleListProps['role'] = {
    // 助手：去掉头像，极浅灰底气泡。内容宽度顺其自然 —— 短答案 (如 "42")
    // 只包住文字，长答案 / 代码块 / 表格会自动撑开到最大宽度。之前写死
    // {@code width: '100%'} 让 "好的" 两个字的气泡也横跨整个对话区，视觉上
    // 像半屏空白灰条，这里去掉硬约束只保留 maxWidth 上限。
    assistant: {
      placement: 'start',
      variant: 'filled',
      styles: {
        content: {
          background: isDark ? '#262626' : '#f5f6f8',
          color: isDark ? '#e5e7eb' : '#1e293b',
          maxWidth: '100%',
        },
        footer: {
          marginBlockStart: 4,
        },
      },
      footer: (content, info) => {
        if (info.status === 'updating' || info.status === 'loading' || info.key == null) return null;
        const id = info.key;
        return (
          <Actions
            styles={{
              root: { color: isDark ? '#a3a3a3' : '#9ca3af' },
              item: { color: isDark ? '#a3a3a3' : '#9ca3af' },
            }}
            items={[
              {
                key: 'retry',
                label: locale.retry,
                icon: <ReloadOutlined />,
                onItemClick: () => onReload(id, { query: userQueryFor(id) }, { extraInfo: {} }),
              },
              {
                key: 'copy',
                actionRender: <Actions.Copy text={String(content)} icon={<CopyOutlined />} />,
              },
            ]}
          />
        );
      },
      contentRender: (content, info) => {
        const text = typeof content === 'string' ? content : String(content ?? '');
        // Chat emits {@code workflow_started} / {@code node_started} /
        // {@code ping} etc. BEFORE the LLM produces its first token — the
        // SDK sees them as "chunks", flips status from {@code loading} to
        // {@code updating}, but our transformer left {@code content} at ''
        // because those events carry no answer. Result: an empty grey bubble
        // for 1–3 s while the model warms up.
        //
        // Show the {@code AssistantThinking} placeholder as long as content
        // is still blank REGARDLESS of loading/updating status — as soon as
        // a real {@code message} chunk arrives {@code text.trim()} is
        // non-empty and XMarkdown takes over with the streaming animation.
        const isStreaming = info.status === 'updating' || info.status === 'loading';
        const noContentYet = text.trim() === '' || text === locale.noData;
        if (isStreaming && noContentYet) {
          return <AssistantThinking isDark={isDark} />;
        }
        return (
          <XMarkdown
            paragraphTag="div"
            className={isDark ? 'x-markdown-dark' : 'x-markdown-light'}
            components={X_MARKDOWN_COMPONENTS}
            streaming={{
              hasNextChunk: isStreaming,
              enableAnimation: true,
            }}
          >
            {normalizeCustomTags(text.replace(/\n\n/g, '<br/><br/>'))}
          </XMarkdown>
        );
      },
    },
    // 用户：去掉头像，用浅蓝底气泡与助手内容区分
    user: {
      placement: 'end',
      variant: 'filled',
      styles: {
        content: {
          background: isDark ? '#1e3a5f' : '#e0edff',
          color: isDark ? '#e5e7eb' : '#1e293b',
        },
      },
    },
  };

  return (
    <XProvider>
      {contextHolder}
      <div className="flex h-screen w-screen flex-col overflow-hidden bg-white dark:bg-[#141414]">
        {/* ============= 头部 ============= */}
        <div
          className={clsx(
            'flex h-[52px] shrink-0 items-center justify-between border-b border-slate-200 pr-2 pl-4 dark:border-zinc-800',
            isEmbedded && !docked && 'cursor-move touch-none select-none',
          )}
          onPointerDown={onTitlePointerDown}
          onPointerMove={onTitlePointerMove}
          onPointerUp={finishTitleDrag}
          onPointerCancel={finishTitleDrag}
          onLostPointerCapture={finishTitleDrag}
        >
          <div className="flex items-center gap-2 text-[15px] font-semibold text-slate-800 dark:text-zinc-100">
            <AiBotIcon size={26} />
            <span className="text-slate-800 dark:text-zinc-100">
              {assistantConfig?.title ?? locale.brandTitle}
            </span>
          </div>
          <Space size={0}>
            <Button
              type="text"
              icon={<PlusOutlined />}
              title={locale.newConversation}
              onClick={() => newConversation()}
            />
            <Popover
              open={historyOpen}
              onOpenChange={setHistoryOpen}
              trigger="click"
              placement="bottomRight"
              styles={{ content: { padding: 0 } }}
              content={
                <Conversations
                  className="copilot-scrollbar max-h-[480px] w-[280px] overflow-auto [&_.ant-conversations-list]:ps-0"
                  items={conversations.map((i) =>
                    i.key === curConversation
                      ? { ...i, label: `[${locale.curConversation}] ${i.label}` }
                      : i,
                  )}
                  activeKey={curConversation}
                  groupable
                  styles={{ item: { padding: '0 8px' } }}
                  onActiveChange={(val) => {
                    setCurConversation(String(val));
                    setHistoryOpen(false);
                  }}
                  menu={(conversation) => ({
                    items: [
                      {
                        label: locale.delete,
                        key: 'delete',
                        icon: <DeleteOutlined />,
                        danger: true,
                        onClick: () => {
                          void deleteConversation(String(conversation.key));
                        },
                      },
                    ],
                  })}
                />
              }
            >
              <Button type="text" icon={<CommentOutlined />} title={locale.curConversation} />
            </Popover>
            {isEmbedded && (
              <Button
                type={docked ? 'primary' : 'text'}
                icon={<LayoutOutlined />}
                title={docked ? locale.undock : locale.dock}
                onClick={() => requestDock(!docked)}
              />
            )}
            {isEmbedded && (
              <Button type="text" icon={<CloseOutlined />} onClick={() => requestClose()} />
            )}
          </Space>
        </div>

        {/* ============= 消息区 ============= */}
        <div
          className={clsx(
            'copilot-scrollbar min-h-0 flex-1 overflow-auto py-4',
            '[&_.ant-bubble-content-updating]:bg-[linear-gradient(90deg,#ff6b23_0%,#af3cb8_31%,#53b6ff_89%)]',
            '[&_.ant-bubble-content-updating]:bg-[length:100%_2px]',
            '[&_.ant-bubble-content-updating]:bg-no-repeat',
            '[&_.ant-bubble-content-updating]:bg-bottom',
            // AI（start 气泡）：body 层占满行宽当"轨道"（避免头像与气泡挤在一起），
            // 但 content 层 —— 也就是灰色底 —— 走 max-w-full 上限而不硬撑，
            // 短答案 "好的" 就只包住两个字，长答案 / 表格 / 代码块自动撑到可视区宽度。
            '[&_.ant-bubble-start]:w-full',
            '[&_.ant-bubble-start_.ant-bubble-body]:w-full',
            '[&_.ant-bubble-start_.ant-bubble-content]:max-w-full',
            '[&_.ant-bubble-start_.ant-bubble-content]:!w-fit',
          )}
        >
          {messages.length ? (
            <Bubble.List
              ref={listRef}
              style={{ height: '100%', paddingInline: 0 }}
              items={messages.map((i) => ({
                ...i.message,
                content:
                  typeof i.message.content === 'string'
                    ? i.message.content
                    : ((i.message.content as { text?: string } | undefined)?.text ?? ''),
                key: i.id,
                status: i.status,
                loading: i.status === 'loading',
                extraInfo: i.extraInfo,
              }))}
              role={roles}
            />
          ) : (
            <WelcomeEmpty
              isDark={isDark}
              conversations={conversations}
              onAsk={submit}
              onUseCap={(c) => submit(c.prompt)}
              onPickHistory={(key) => setCurConversation(String(key))}
              config={assistantConfig?.home}
            />
          )}
        </div>

        {/* ============= 输入区 ============= */}
        <div className="shrink-0 border-t border-slate-200 bg-white/80 p-3 backdrop-blur dark:border-zinc-800 dark:bg-[#141414]/80">
          <AgentApprovalCard
            approval={pendingApproval}
            loading={approvalResolving}
            onDecision={(decision) => void decideApproval(decision)}
          />
          <Sender
            ref={senderRef}
            key={curConversation}
            loading={isRequesting}
            placeholder={locale.placeholder}
            onSubmit={submit}
            onCancel={() => abort()}
            autoSize={{ minRows: 1, maxRows: 6 }}
          />
        </div>
      </div>
    </XProvider>
  );
};

export default Copilot;
