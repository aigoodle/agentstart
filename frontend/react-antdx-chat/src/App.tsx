import {
  BookOutlined,
  BulbOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  FundProjectionScreenOutlined,
  HeartOutlined,
  OpenAIOutlined,
  ReadOutlined,
  SmileOutlined,
  SolutionOutlined,
  SoundOutlined,
  SyncOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Actions,
  Bubble,
  Conversations,
  Prompts,
  Sender,
  Welcome,
  XProvider,
} from '@ant-design/x';
import type { BubbleListProps, PromptsProps, SenderProps } from '@ant-design/x';
import type { BubbleListRef } from '@ant-design/x/es/bubble';
import XMarkdown from '@ant-design/x-markdown';
import { useXChat } from '@ant-design/x-sdk';
import { Avatar, Flex } from 'antd';
import type { GetRef } from 'antd';
import { clsx } from 'clsx';
import React, { useEffect, useRef, useState } from 'react';
import locale from './_utils/locale';
import AiBotIcon from './components/AiBotIcon';
import AgentApprovalCard from './components/AgentApprovalCard';
import { CHAT_LABEL } from './lib/provider';
import { useChat } from './lib/useChat';
import { useIsDark } from './lib/useHostBridge';

// ==================== Context ====================
const ChatContext = React.createContext<{
  onReload?: ReturnType<typeof useXChat>['onReload'];
}>({});

const slotConfig: SenderProps['slotConfig'] = [
  { type: 'text', value: locale.slotTextStart },
  {
    type: 'select',
    key: 'tone',
    props: { defaultValue: '简洁', options: ['简洁', '幽默', '严谨'] },
  },
  { type: 'text', value: locale.slotTextEnd },
];

// ==================== Welcome / Prompts data ====================
const ROLE_PROMPT_GROUPS: Array<{
  key: string;
  title: string;
  accent: string;
  items: NonNullable<PromptsProps['items']>;
}> = [
  {
    key: 'teacher',
    title: locale.promptsTeacherTitle,
    accent:
      'from-sky-50 to-blue-50 border-sky-100 dark:from-sky-950/40 dark:to-blue-950/40 dark:border-sky-900',
    items: [
      {
        key: 'teacher-1',
        icon: <BookOutlined className="text-sky-500" />,
        label: locale.promptTeacher_1_label,
        description: locale.promptTeacher_1_desc,
      },
      {
        key: 'teacher-2',
        icon: <ExperimentOutlined className="text-sky-500" />,
        label: locale.promptTeacher_2_label,
        description: locale.promptTeacher_2_desc,
      },
      {
        key: 'teacher-3',
        icon: <EditOutlined className="text-sky-500" />,
        label: locale.promptTeacher_3_label,
        description: locale.promptTeacher_3_desc,
      },
      {
        key: 'teacher-4',
        icon: <FundProjectionScreenOutlined className="text-sky-500" />,
        label: locale.promptTeacher_4_label,
        description: locale.promptTeacher_4_desc,
      },
    ],
  },
  {
    key: 'leader',
    title: locale.promptsLeaderTitle,
    accent:
      'from-violet-50 to-fuchsia-50 border-violet-100 dark:from-violet-950/40 dark:to-fuchsia-950/40 dark:border-violet-900',
    items: [
      {
        key: 'leader-1',
        icon: <FileTextOutlined className="text-violet-500" />,
        label: locale.promptLeader_1_label,
        description: locale.promptLeader_1_desc,
      },
      {
        key: 'leader-2',
        icon: <SoundOutlined className="text-violet-500" />,
        label: locale.promptLeader_2_label,
        description: locale.promptLeader_2_desc,
      },
      {
        key: 'leader-3',
        icon: <SolutionOutlined className="text-violet-500" />,
        label: locale.promptLeader_3_label,
        description: locale.promptLeader_3_desc,
      },
    ],
  },
  {
    key: 'student',
    title: locale.promptsStudentTitle,
    accent:
      'from-emerald-50 to-teal-50 border-emerald-100 dark:from-emerald-950/40 dark:to-teal-950/40 dark:border-emerald-900',
    items: [
      {
        key: 'student-1',
        icon: <ReadOutlined className="text-emerald-500" />,
        label: locale.promptStudent_1_label,
        description: locale.promptStudent_1_desc,
      },
      {
        key: 'student-2',
        icon: <BulbOutlined className="text-emerald-500" />,
        label: locale.promptStudent_2_label,
        description: locale.promptStudent_2_desc,
      },
      {
        key: 'student-3',
        icon: <ThunderboltOutlined className="text-emerald-500" />,
        label: locale.promptStudent_3_label,
        description: locale.promptStudent_3_desc,
      },
    ],
  },
  {
    key: 'parent',
    title: locale.promptsParentTitle,
    accent:
      'from-rose-50 to-orange-50 border-rose-100 dark:from-rose-950/40 dark:to-orange-950/40 dark:border-rose-900',
    items: [
      {
        key: 'parent-1',
        icon: <TeamOutlined className="text-rose-500" />,
        label: locale.promptParent_1_label,
        description: locale.promptParent_1_desc,
      },
      {
        key: 'parent-2',
        icon: <SmileOutlined className="text-rose-500" />,
        label: locale.promptParent_2_label,
        description: locale.promptParent_2_desc,
      },
      {
        key: 'parent-3',
        icon: <HeartOutlined className="text-rose-500" />,
        label: locale.promptParent_3_label,
        description: locale.promptParent_3_desc,
      },
    ],
  },
];

interface FooterProps {
  id?: string | number;
  content: string;
  status?: string;
}

const Footer: React.FC<FooterProps> = ({ id, content, status }) => {
  const context = React.useContext(ChatContext);
  const items = [
    {
      key: 'retry',
      label: locale.retry,
      icon: <SyncOutlined />,
      onItemClick: () => {
        if (id != null) {
          context.onReload?.(id, { userAction: 'retry' });
        }
      },
    },
    {
      key: 'copy',
      actionRender: <Actions.Copy text={content} />,
    },
  ];
  if (status === 'updating' || status === 'loading') return null;
  return <div className="flex">{id != null && <Actions items={items} />}</div>;
};

const AssistantAvatar = (
  <Avatar
    size={36}
    style={{
      background: '#ffffff',
      color: '#fff',
      boxShadow: '0 4px 12px rgba(139, 92, 246, 0.18)',
    }}
    icon={<AiBotIcon size={28} />}
  />
);

const UserAvatar = (
  <Avatar
    size={36}
    style={{
      background: 'linear-gradient(135deg, #64748b 0%, #334155 100%)',
      color: '#fff',
    }}
    icon={<UserOutlined />}
  />
);

const getRole = (markdownClass: string): BubbleListProps['role'] => ({
  assistant: {
    placement: 'start',
    avatar: AssistantAvatar,
    footer: (content, info) => (
      <Footer content={String(content)} status={info.status} id={info.key} />
    ),
    contentRender: (content, info) => {
      const text = typeof content === 'string' ? content : String(content ?? '');
      const normalized = text.replace(/\n\n/g, '<br/><br/>');
      return (
        <XMarkdown
          paragraphTag="div"
          className={markdownClass}
          streaming={{
            hasNextChunk: info.status === 'updating' || info.status === 'loading',
            enableAnimation: true,
          }}
        >
          {normalized}
        </XMarkdown>
      );
    },
  },
  user: { placement: 'end', avatar: UserAvatar },
});

function App() {
  const senderRef = useRef<GetRef<typeof Sender>>(null);
  const listRef = useRef<BubbleListRef>(null);

  const [deepThink, setDeepThink] = useState<boolean>(true);
  const isDark = useIsDark();
  // 推荐卡片内 Prompts 项的底色 / 描边在浅/深色下要分别给值（内联，不走 Tailwind class）
  const promptItemStyle = isDark
    ? { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }
    : { background: 'rgba(255,255,255,0.7)', border: '1px solid rgba(255,255,255,0.9)' };

  const {
    contextHolder,
    conversations,
    curConversation,
    setCurConversation,
    activeConversation,
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

  useEffect(() => {
    senderRef.current?.focus({ cursor: 'end' });
  }, []);

  return (
    <XProvider>
      {contextHolder}
      <ChatContext.Provider value={{ onReload }}>
        <div className="flex h-screen w-screen overflow-hidden bg-white dark:bg-[#141414]">
          {/* ============= Sidebar ============= */}
          <aside className="box-border flex h-full w-[280px] shrink-0 flex-col bg-slate-50 px-3 dark:bg-[#1f1f1f]">
            <div className="m-6 flex items-center gap-2 px-2">
              <AiBotIcon size={28} />
              <span className="text-base font-bold text-slate-800 dark:text-zinc-100">
                {locale.brandTitle}
              </span>
            </div>

            <Conversations
              creation={{
                onClick: () => {
                  newConversation();
                },
              }}
              items={conversations
                .map(({ key, label, ...other }) => ({
                  key,
                  label: key === activeConversation ? `[${locale.curConversation}]${label}` : label,
                  ...other,
                }))
                .sort(({ key }) => (key === activeConversation ? -1 : 0))}
              className="mt-3 flex-1 overflow-y-auto p-0 [&_.ant-conversations-list]:ps-0"
              activeKey={curConversation}
              onActiveChange={(val) => setCurConversation(String(val))}
              groupable
              styles={{ item: { padding: '0 8px' } }}
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

            {/*<div className="flex h-10 items-center justify-between border-t border-slate-200 text-xs text-slate-400">
              <span>阿里云百炼 · OpenAI 兼容模式</span>
            </div>*/}
          </aside>

          {/* ============= Chat panel ============= */}
          <div
            className={clsx(
              'box-border flex h-full w-[calc(100%-280px)] flex-col overflow-hidden',
              '[&_.ant-bubble-content-updating]:bg-[linear-gradient(90deg,#ff6b23_0%,#af3cb8_31%,#53b6ff_89%)]',
              '[&_.ant-bubble-content-updating]:bg-[length:100%_2px]',
              '[&_.ant-bubble-content-updating]:bg-no-repeat',
              '[&_.ant-bubble-content-updating]:bg-bottom',
            )}
          >
            {/* ---- Scrollable content area (welcome OR bubble list) ---- */}
            <div className="min-h-0 flex-1 overflow-y-auto">
              {messages.length === 0 ? (
                <div className="mx-auto w-full max-w-[940px] px-6 pt-10 pb-6">
                  <Welcome
                    variant="borderless"
                    icon={
                      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white shadow-md shadow-violet-100 dark:bg-zinc-900 dark:shadow-violet-950/40">
                        <AiBotIcon size={44} />
                      </div>
                    }
                    title={
                      <div className="text-2xl font-semibold text-slate-800 dark:text-zinc-100">
                        {locale.welcomeTitle}
                      </div>
                    }
                    description={
                      <div className="mt-1 max-w-2xl leading-relaxed text-slate-500 dark:text-zinc-400">
                        {locale.welcomeDescription}
                      </div>
                    }
                    extra={
                      <div className="mt-2 flex flex-wrap gap-2">
                        <span className="rounded-full bg-sky-50 px-3 py-1 text-xs text-sky-600 dark:bg-sky-950/50 dark:text-sky-300">
                          内容创作
                        </span>
                        <span className="rounded-full bg-violet-50 px-3 py-1 text-xs text-violet-600 dark:bg-violet-950/50 dark:text-violet-300">
                          数据分析
                        </span>
                        <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-300">
                          知识问答
                        </span>
                        <span className="rounded-full bg-rose-50 px-3 py-1 text-xs text-rose-600 dark:bg-rose-950/50 dark:text-rose-300">
                          工作提效
                        </span>
                      </div>
                    }
                  />

                  <div className="mt-2 text-sm font-medium text-slate-500 dark:text-zinc-400">
                    {locale.promptsRecommendTitle}
                  </div>

                  <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                    {ROLE_PROMPT_GROUPS.map((group) => (
                      <div
                        key={group.key}
                        className={clsx(
                          'rounded-2xl border bg-gradient-to-br p-4',
                          group.accent,
                        )}
                      >
                        <div className="mb-3 flex items-center justify-between">
                          <span className="text-sm font-semibold text-slate-700 dark:text-zinc-200">
                            {group.title}
                          </span>
                        </div>
                        <Prompts
                          vertical
                          items={group.items}
                          styles={{ item: promptItemStyle }}
                          onItemClick={({ data }) => {
                            const text =
                              typeof data.description === 'string'
                                ? data.description
                                : typeof data.label === 'string'
                                  ? data.label
                                  : '';
                            if (sendMessage(text)) {
                              listRef.current?.scrollTo({ top: 'bottom' });
                            }
                          }}
                        />
                      </div>
                    ))}
                  </div>

                  <div className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-400 dark:text-zinc-500">
                    <span>💡 提示</span>
                    <span>
                      可点击上方任意问题快速开启对话, 也可以在下方输入框中直接提问.
                    </span>
                  </div>
                </div>
              ) : (
                <div className="pt-4">
                  <Bubble.List
                    ref={listRef}
                    styles={{ root: { maxWidth: 940, marginBlockEnd: 24, marginInline: 'auto' } }}
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
                    role={getRole(isDark ? 'x-markdown-dark' : 'x-markdown-light')}
                  />
                </div>
              )}
            </div>

            {/* ---- Sender pinned to bottom ---- */}
            <div className="shrink-0 border-t border-slate-100 bg-white/80 px-4 py-3 backdrop-blur dark:border-zinc-800 dark:bg-[#141414]/80">
              <AgentApprovalCard
                approval={pendingApproval}
                loading={approvalResolving}
                onDecision={(decision) => void decideApproval(decision)}
              />
              <Sender
                suffix={false}
                ref={senderRef}
                key={curConversation}
                slotConfig={slotConfig}
                loading={isRequesting}
                onSubmit={(val) => {
                  if (sendMessage(val)) {
                    listRef.current?.scrollTo({ top: 'bottom' });
                    senderRef.current?.clear?.();
                  }
                }}
                onCancel={() => abort()}
                placeholder={locale.placeholder}
                footer={(actionNode) => (
                  <Flex justify="space-between" align="center">
                    <Flex gap="small" align="center">
                      <Sender.Switch
                        value={deepThink}
                        onChange={(checked) => setDeepThink(checked)}
                        icon={<OpenAIOutlined />}
                      >
                        {locale.deepThink}
                      </Sender.Switch>
                    </Flex>
                    <Flex align="center">{actionNode}</Flex>
                  </Flex>
                )}
                autoSize={{ minRows: 2, maxRows: 6 }}
                className="mx-auto w-full max-w-[940px]"
              />
              <div className="mx-auto mt-2 max-w-[940px] text-center text-xs text-slate-400 dark:text-zinc-500">
                内容由 AI 生成, 仅供参考, 请甄别后使用 · 由 {CHAT_LABEL} 提供能力
              </div>
            </div>
          </div>
        </div>
      </ChatContext.Provider>
    </XProvider>
  );
}

export default App;
