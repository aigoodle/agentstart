<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import { Button, Modal } from '../../ui';
import type {
  ChannelConnection,
  ChannelConversation,
  ChannelConversationSummary,
  ChannelDefinition,
  ChannelEvent,
  ChannelAttachment,
} from '../types';
import ChannelIcon from './ChannelIcon.vue';

const props = withDefaults(
  defineProps<{
    open: boolean;
    channel?: ChannelDefinition;
    /** 当前通道下的账号连接，用于筛选 */
    connections: ChannelConnection[];
    /** 父级加载的全量消息，抽屉内按通道过滤展示 */
    events: ChannelEvent[];
    autoRefresh?: boolean;
    client: AgentStartClient;
    tenantId?: string;
  }>(),
  { autoRefresh: true },
);

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'update:autoRefresh', v: boolean): void;
  (e: 'refresh'): void;
  (e: 'eventsChanged'): void;
}>();

const connectionFilter = ref('');
const platformKey = computed(() =>
  String(props.channel?.metadata?.platformId ?? props.channel?.channelId ?? '').toLowerCase(),
);
const supportsDeliveryReceipts = computed(
  () => props.channel?.capabilities?.deliveryReceipts === true,
);
const handoffOpen = ref(false);
const handoffTarget = ref<ChannelEvent>();
const handoffNote = ref('等待客服处理');
const handoffSaving = ref(false);
const handoffError = ref('');
const replyOpen = ref(false);
const replyTarget = ref<ChannelEvent>();
const replyContent = ref('');
const replySaving = ref(false);
const replyError = ref('');
const replyIdempotencyKey = ref('');
const replyAttachmentType = ref<'IMAGE' | 'AUDIO' | 'VIDEO' | 'FILE'>('IMAGE');
const replyAttachmentUrl = ref('');
const activeView = ref<'conversations' | 'messages'>('conversations');
const conversations = ref<ChannelConversation[]>([]);
const conversationSummary = ref<ChannelConversationSummary>();
const conversationsLoading = ref(false);
const conversationsLoadingMore = ref(false);
const conversationNextCursor = ref<string>();
const conversationHasMore = ref(false);
const selectedConversationId = ref('');
const conversationError = ref('');
const timelineEvents = ref<ChannelEvent[]>([]);
const timelineLoading = ref(false);
const timelineLoadingMore = ref(false);
const timelineNextCursor = ref<string>();
const timelineHasMore = ref(false);
const timelineError = ref('');
const assignmentGroup = ref('');
const noteOpen = ref(false);
const noteTarget = ref<ChannelConversation>();
const noteContent = ref('');
const noteSaving = ref(false);
let conversationRequestGeneration = 0;
let timelineRequestGeneration = 0;

watch(
  () => [props.open, props.channel?.provider, props.channel?.channelId] as const,
  ([open, provider, channelId], previous) => {
    const channelChanged = !previous || provider !== previous[1] || channelId !== previous[2];
    if (channelChanged) {
      connectionFilter.value = '';
      selectedConversationId.value = '';
      resetTimeline();
    }
    if (open) void loadConversations(false);
  },
);

watch(connectionFilter, () => {
  if (selectedConversationId.value) {
    selectedConversationId.value = '';
    resetTimeline();
    activeView.value = 'conversations';
  }
});

async function loadConversations(append = false) {
  if (!props.open) return;
  const api = props.client.channels as any;
  if (typeof api.listChannelConversations !== 'function' || typeof api.getChannelConversationSummary !== 'function') {
    activeView.value = 'messages';
    return;
  }
  if (append && (!conversationHasMore.value || conversationsLoadingMore.value)) return;
  if (append) conversationsLoadingMore.value = true;
  else conversationsLoading.value = true;
  const requestGeneration = ++conversationRequestGeneration;
  conversationError.value = '';
  try {
    const pagePromise = typeof api.pageChannelConversations === 'function'
      ? api.pageChannelConversations({
        provider: props.channel?.provider,
        channelId: props.channel?.channelId,
        cursor: append ? conversationNextCursor.value : undefined,
        limit: 50,
      })
      : api.listChannelConversations({ limit: 200 }).then((items: ChannelConversation[]) => ({
        items, nextCursor: undefined, hasMore: false,
      }));
    const [page, summary] = await Promise.all([
      pagePromise,
      append ? Promise.resolve(conversationSummary.value) : api.getChannelConversationSummary(),
    ]);
    if (requestGeneration !== conversationRequestGeneration) return;
    if (append) {
      const known = new Set(conversations.value.map((row) => row.id));
      conversations.value = [...conversations.value,
        ...page.items.filter((row: ChannelConversation) => !known.has(row.id))];
    } else {
      conversations.value = page.items;
    }
    conversationNextCursor.value = page.nextCursor;
    conversationHasMore.value = page.hasMore === true;
    conversationSummary.value = summary;
  } catch (e: any) {
    if (requestGeneration === conversationRequestGeneration) {
      conversationError.value = e?.message ?? '会话加载失败';
    }
  } finally {
    if (requestGeneration === conversationRequestGeneration) {
      if (append) conversationsLoadingMore.value = false;
      else conversationsLoading.value = false;
    }
  }
}

function loadMoreConversations() {
  void loadConversations(true);
}

function refreshAll() {
  emit('refresh');
  void loadConversations(false);
  if (selectedConversationId.value) void loadTimeline(false);
}

const filteredConversations = computed(() => conversations.value.filter((row) =>
  row.provider === props.channel?.provider && row.channelId === props.channel?.channelId
  && (!connectionFilter.value || row.connectionId === connectionFilter.value)));

const visibleEvents = computed(() => selectedConversationId.value ? timelineEvents.value : props.events);

const filtered = computed(() => {
  const channel = props.channel;
  if (!channel) return [];
  return visibleEvents.value.filter(
    (event) =>
      event.provider === channel.provider &&
      event.channelId === channel.channelId &&
      (!connectionFilter.value || event.connectionId === connectionFilter.value) &&
      (!selectedConversationId.value || event.conversationId === selectedConversationId.value),
  );
});

function replySource(event: ChannelEvent) {
  if (!event.replyToEventId) return undefined;
  return visibleEvents.value.find((item) => item.id === event.replyToEventId);
}

function hasLinkedReply(eventId: string) {
  return visibleEvents.value.some((item) => item.replyToEventId === eventId);
}

function isAgentReply(event: ChannelEvent) {
  return event.senderType === 'AGENT' || event.status === 'AGENT_REPLIED' || event.status === 'AGENT_RETRIED';
}

function outboundStatus(event: ChannelEvent) {
  if (event.status === 'PENDING') return '等待发送';
  if (event.status === 'SENDING') return '正在发送';
  if (event.status === 'SENT') {
    return supportsDeliveryReceipts.value ? '已发送 · 等待送达回执' : '已发送 · 平台无送达回执';
  }
  if (event.status === 'DELIVERED') return '已送达';
  if (event.status === 'FAILED') return `发送失败${event.attempts ? ` · 第 ${event.attempts} 次` : ''}`;
  if (isAgentReply(event)) return 'Agent 已回复';
  return event.status;
}

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `reply-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

async function retryEvent(event: ChannelEvent) {
  await props.client.channels.retryChannelEvent(event.id, props.tenantId);
  emit('eventsChanged');
}

function selectConversation(row: ChannelConversation) {
  selectedConversationId.value = row.conversationId;
  activeView.value = 'messages';
  void loadTimeline(false);
}

function resetTimeline() {
  timelineRequestGeneration++;
  timelineEvents.value = [];
  timelineNextCursor.value = undefined;
  timelineHasMore.value = false;
  timelineError.value = '';
  timelineLoading.value = false;
  timelineLoadingMore.value = false;
}

async function loadTimeline(append = false) {
  if (!props.open || !selectedConversationId.value) return;
  const api = props.client.channels as any;
  if (typeof api.pageChannelEvents !== 'function') return;
  if (append && (!timelineHasMore.value || timelineLoadingMore.value)) return;
  if (append) timelineLoadingMore.value = true;
  else timelineLoading.value = true;
  const requestGeneration = ++timelineRequestGeneration;
  const conversationId = selectedConversationId.value;
  timelineError.value = '';
  try {
    const page = await api.pageChannelEvents({
      connectionId: connectionFilter.value || undefined,
      conversationId,
      cursor: append ? timelineNextCursor.value : undefined,
      limit: 50,
    });
    if (requestGeneration !== timelineRequestGeneration || conversationId !== selectedConversationId.value) return;
    if (append) {
      const known = new Set(timelineEvents.value.map((event) => event.id));
      timelineEvents.value = [...timelineEvents.value,
        ...page.items.filter((event: ChannelEvent) => !known.has(event.id))];
    } else {
      timelineEvents.value = page.items;
    }
    timelineNextCursor.value = page.nextCursor;
    timelineHasMore.value = page.hasMore === true;
  } catch (e: any) {
    if (requestGeneration === timelineRequestGeneration) {
      timelineError.value = e?.message ?? '消息时间线加载失败';
    }
  } finally {
    if (requestGeneration === timelineRequestGeneration) {
      if (append) timelineLoadingMore.value = false;
      else timelineLoading.value = false;
    }
  }
}

function clearConversationFilter() {
  selectedConversationId.value = '';
  resetTimeline();
}

function loadMoreTimeline() {
  void loadTimeline(true);
}

async function claimConversation(row: ChannelConversation) {
  conversationError.value = '';
  try {
    await props.client.channels.claimChannelConversation(row.id, row.lockVersion, row.assignmentGroup);
    await loadConversations(false);
  } catch (e: any) { conversationError.value = e?.message ?? '接管失败，请刷新后重试'; }
}

async function resumeBot(row: ChannelConversation) {
  conversationError.value = '';
  try {
    await props.client.channels.resumeChannelConversationBot(row.id, row.lockVersion);
    await loadConversations(false);
  } catch (e: any) { conversationError.value = e?.message ?? '恢复机器人失败'; }
}

async function closeConversation(row: ChannelConversation) {
  conversationError.value = '';
  try {
    await props.client.channels.closeChannelConversation(row.id, row.lockVersion);
    await loadConversations(false);
  } catch (e: any) { conversationError.value = e?.message ?? '关闭会话失败'; }
}

function openNote(row: ChannelConversation) {
  noteTarget.value = row; noteContent.value = ''; noteOpen.value = true;
}

async function submitNote() {
  if (!noteTarget.value || !noteContent.value.trim()) return;
  noteSaving.value = true; conversationError.value = '';
  try {
    await props.client.channels.addChannelConversationNote(noteTarget.value.id, noteContent.value.trim());
    noteOpen.value = false; noteTarget.value = undefined; emit('eventsChanged');
  } catch (e: any) { conversationError.value = e?.message ?? '内部备注保存失败'; }
  finally { noteSaving.value = false; }
}

function conversationStatus(row: ChannelConversation) {
  return ({ BOT_ACTIVE: '机器人处理中', WAITING_HUMAN: '待人工', HUMAN_ACTIVE: '人工处理中', CLOSED: '已关闭' } as Record<string, string>)[row.status] ?? row.status;
}

function openHandoff(event: ChannelEvent) {
  handoffTarget.value = event;
  handoffNote.value = '等待客服处理';
  handoffError.value = '';
  assignmentGroup.value = '';
  handoffOpen.value = true;
}

function closeHandoff() {
  if (handoffSaving.value) return;
  handoffOpen.value = false;
  handoffTarget.value = undefined;
}

async function submitHandoff() {
  if (!handoffTarget.value) return;
  handoffSaving.value = true;
  handoffError.value = '';
  try {
    await props.client.channels.handoffChannelEvent(
      handoffTarget.value.id,
      handoffNote.value.trim() || undefined,
      props.tenantId,
      assignmentGroup.value.trim() || undefined,
    );
    handoffOpen.value = false;
    handoffTarget.value = undefined;
    emit('eventsChanged');
    await loadConversations(false);
  } catch (e: any) {
    handoffError.value = e?.message ?? '提交人工接管失败';
  } finally {
    handoffSaving.value = false;
  }
}

function openReply(event: ChannelEvent) {
  replyTarget.value = event;
  replyContent.value = '';
  replyIdempotencyKey.value = newIdempotencyKey();
  replyAttachmentType.value = 'IMAGE';
  replyAttachmentUrl.value = '';
  replyError.value = '';
  replyOpen.value = true;
}

function closeReply() {
  if (replySaving.value) return;
  replyOpen.value = false;
  replyTarget.value = undefined;
}

async function submitReply() {
  if (!replyTarget.value || (!replyContent.value.trim() && !replyAttachmentUrl.value.trim())) return;
  replySaving.value = true;
  replyError.value = '';
  try {
    await props.client.channels.replyChannelEvent(
      replyTarget.value.id,
      {
        content: replyContent.value.trim(),
        messageType: replyAttachmentUrl.value.trim() ? replyAttachmentType.value : 'TEXT',
        attachments: replyAttachmentUrl.value.trim() ? [{
          type: replyAttachmentType.value,
          url: replyAttachmentUrl.value.trim(),
        } satisfies ChannelAttachment] : [],
        idempotencyKey: replyIdempotencyKey.value,
      },
      props.tenantId,
    );
    replyOpen.value = false;
    replyTarget.value = undefined;
    emit('eventsChanged');
    if (selectedConversationId.value) void loadTimeline(false);
  } catch (e: any) {
    replyError.value = e?.message ?? '消息发送失败';
  } finally {
    replySaving.value = false;
  }
}

const formatTime = (value?: string) => (value ? new Date(value).toLocaleString() : '-');
</script>

<template>
  <Transition name="cmd">
    <div v-if="open" class="cmd-mask" @click.self="emit('update:open', false)">
      <aside class="cmd-drawer" @click.stop>
        <header class="cmd-header">
          <div class="cmd-header-info">
            <ChannelIcon
              class="cmd-icon"
              :platform-id="platformKey"
              :channel-id="channel?.channelId"
              :metadata="channel?.metadata"
              :name="channel?.name"
              :size="40"
              :radius="10"
            />
            <div class="cmd-header-text">
              <b>消息观察 · {{ channel?.name }}</b>
              <small>入站消息、路由结果与 Agent 回复</small>
            </div>
          </div>
          <div class="cmd-tools">
            <select v-model="connectionFilter" class="cmd-select">
              <option value="">全部账号</option>
              <option v-for="row in connections" :key="row.id" :value="row.id">
                {{ row.name }}
              </option>
            </select>
            <label class="cmd-auto">
              <input
                :checked="autoRefresh"
                type="checkbox"
                @change="
                  emit('update:autoRefresh', ($event.target as HTMLInputElement).checked)
                "
              />
              自动刷新
            </label>
            <Button @click="refreshAll">刷新</Button>
          </div>
          <button class="cmd-close" @click="emit('update:open', false)">✕</button>
        </header>

        <nav class="cmd-view-tabs">
          <button :class="{ active: activeView === 'conversations' }" @click="activeView = 'conversations'">会话队列</button>
          <button :class="{ active: activeView === 'messages' }" @click="activeView = 'messages'">消息时间线</button>
          <span v-if="conversationSummary">
            待人工 {{ conversationSummary.waitingHuman }} · 处理中 {{ conversationSummary.humanActive }} ·
            未读 {{ conversationSummary.unread }}<template v-if="conversationSummary.slaBreached"> · 超时 {{ conversationSummary.slaBreached }}</template>
          </span>
        </nav>

        <main v-if="activeView === 'conversations'" class="cmd-main cmd-conversation-list">
          <div v-if="conversationError" class="cmd-error">{{ conversationError }}</div>
          <div v-if="conversationsLoading" class="cmd-empty">正在加载会话…</div>
          <div v-else-if="!filteredConversations.length" class="cmd-empty">该通道暂无会话</div>
          <article v-for="row in filteredConversations" :key="row.id" class="cmd-conversation" :class="{ breached: row.slaBreached }">
            <header>
              <div><b>{{ row.ownerId || row.accountId }}</b><small>{{ row.conversationId }}</small></div>
              <em :class="`status-${row.status.toLowerCase()}`">{{ conversationStatus(row) }}</em>
            </header>
            <p>{{ row.lastMessagePreview || '[非文本消息]' }}</p>
            <div class="cmd-conversation-meta">
              <span>{{ formatTime(row.lastMessageAt) }}</span>
              <span v-if="row.assigneeName || row.assigneeId">处理人：{{ row.assigneeName || row.assigneeId }}</span>
              <span v-if="row.assignmentGroup">组：{{ row.assignmentGroup }}</span>
              <span v-if="row.agentId">Agent：{{ row.agentId }}</span>
              <span v-if="row.agentVersionId">版本：{{ row.agentVersionId }}</span>
              <span v-if="row.agentRouteReason">路由：{{ row.agentRouteReason }} · 策略 v{{ row.routingPolicyVersion }}</span>
              <span v-if="row.unreadCount">未读 {{ row.unreadCount }}</span>
              <span v-if="row.slaBreached" class="is-breached">SLA 已超时</span>
            </div>
            <footer>
              <button class="cmd-op" @click="selectConversation(row)">查看消息</button>
              <button v-if="row.status === 'WAITING_HUMAN'" class="cmd-op is-reply" @click="claimConversation(row)">接管</button>
              <button v-if="row.agentPaused && row.status !== 'CLOSED'" class="cmd-op" @click="resumeBot(row)">恢复机器人</button>
              <button v-if="row.status !== 'CLOSED'" class="cmd-op" @click="openNote(row)">内部备注</button>
              <button v-if="row.status !== 'CLOSED'" class="cmd-op" @click="closeConversation(row)">关闭</button>
            </footer>
          </article>
          <button
            v-if="conversationHasMore"
            class="cmd-btn cmd-load-more"
            :disabled="conversationsLoadingMore"
            @click="loadMoreConversations"
          >
            {{ conversationsLoadingMore ? '正在加载…' : '加载更多会话' }}
          </button>
        </main>

        <main v-else class="cmd-main">
          <button v-if="selectedConversationId" class="cmd-clear-filter" @click="clearConversationFilter">清除会话筛选</button>
          <div v-if="timelineError" class="cmd-error">{{ timelineError }}</div>
          <div v-if="selectedConversationId && timelineLoading" class="cmd-empty">正在加载完整消息时间线…</div>
          <div v-else-if="!filtered.length" class="cmd-empty">该通道暂无消息</div>
          <article v-for="event in filtered" :key="event.id" class="cmd-msg">
            <header>
              <b>{{ event.ownerId || event.accountId }}</b>
              <span>{{ formatTime(event.eventTime || event.createdAt) }}</span>
              <button v-if="event.direction === 'INBOUND' && event.status === 'ERROR'" class="cmd-op" @click="retryEvent(event)">
                重试并回送
              </button>
              <button v-if="event.direction === 'INBOUND' && event.senderId" class="cmd-op is-reply" @click="openReply(event)">
                回复消息
              </button>
              <button v-if="event.direction === 'INBOUND' && event.status !== 'HANDOFF'" class="cmd-op" @click="openHandoff(event)">
                转人工处理
              </button>
              <em :class="event.handled ? 'is-handled' : ''">
                {{ event.direction === 'INTERNAL' ? '内部备注' : event.direction === 'OUTBOUND' ? outboundStatus(event) : event.status === 'HANDOFF' ? '待人工处理' : event.handled ? 'Agent 已处理' : '仅接收' }}
              </em>
            </header>
            <div v-if="event.direction === 'INBOUND'" class="cmd-bubble is-inbound">
              <small>外部用户 {{ event.senderId || '未知用户' }}</small>
              <p>{{ event.content || '[非文本消息]' }}</p>
              <div v-if="event.attachments?.length" class="cmd-attachments">
                <template v-for="(attachment, index) in event.attachments" :key="`${event.id}-${index}`">
                  <img v-if="attachment.type === 'IMAGE' && attachment.url" :src="attachment.url" :alt="attachment.name || '图片'" loading="lazy" />
                  <audio v-else-if="attachment.type === 'AUDIO' && attachment.url" :src="attachment.url" controls preload="none"></audio>
                  <video v-else-if="attachment.type === 'VIDEO' && attachment.url" :src="attachment.url" controls preload="metadata"></video>
                  <a v-else-if="attachment.url" :href="attachment.url" target="_blank" rel="noopener noreferrer">{{ attachment.name || '查看附件' }}</a>
                  <span v-else>{{ attachment.name || attachment.type }}</span>
                </template>
              </div>
            </div>
            <div v-else-if="event.direction === 'OUTBOUND'" class="cmd-bubble is-outbound">
              <small>
                {{ isAgentReply(event) ? 'Agent 回复' : event.senderType === 'ADMIN' ? '管理员回复' : '员工回复' }}
                <template v-if="event.senderActorId"> · {{ event.senderActorId }}</template>
                <template v-if="event.platformMessageId"> · 平台消息 {{ event.platformMessageId }}</template>
                <template v-if="event.durationMs != null"> · {{ event.durationMs }} ms</template>
              </small>
              <blockquote v-if="replySource(event)" class="cmd-reply-ref">
                <span>回复消息</span>
                {{ replySource(event)?.content || '[非文本消息]' }}
              </blockquote>
              <p>{{ event.content }}</p>
              <div v-if="event.attachments?.length" class="cmd-attachments">
                <a v-for="(attachment, index) in event.attachments" :key="`${event.id}-out-${index}`" :href="attachment.url" target="_blank" rel="noopener noreferrer">{{ attachment.name || attachment.type }}</a>
              </div>
            </div>
            <div v-else class="cmd-bubble is-internal">
              <small>内部备注<template v-if="event.senderActorId"> · {{ event.senderActorId }}</template></small>
              <p>{{ event.content }}</p>
            </div>
            <div v-if="event.replyContent && !hasLinkedReply(event.id)" class="cmd-bubble is-outbound">
              <small>Agent 回复 · {{ event.durationMs ?? 0 }} ms</small>
              <p>{{ event.replyContent }}</p>
            </div>
            <div v-if="event.errorMessage" class="cmd-error">{{ event.errorMessage }}</div>
          </article>
          <button
            v-if="selectedConversationId && timelineHasMore"
            class="cmd-btn cmd-load-more cmd-load-more-messages"
            :disabled="timelineLoadingMore"
            @click="loadMoreTimeline"
          >
            {{ timelineLoadingMore ? '正在加载…' : '加载更早消息' }}
          </button>
        </main>
      </aside>

      <Modal :open="replyOpen" class="cmd-dialog" centered :width="560" :mask-closable="!replySaving" :keyboard="!replySaving" :closable="!replySaving" @cancel="closeReply">
            <template #title>
              <div class="as-modal-heading">
                <div id="reply-title" class="as-modal-title">回复外部用户</div>
                <div class="as-modal-subtitle">通过 {{ channel?.name }} 账号发送给 {{ replyTarget?.senderId }}</div>
              </div>
            </template>
            <main>
              <div v-if="replyTarget?.content" class="cmd-original-message">
                <small>用户消息</small><p>{{ replyTarget.content }}</p>
              </div>
              <label class="cmd-form-field">
                回复内容
                <textarea v-model="replyContent" rows="5" maxlength="2000" autofocus placeholder="请输入要发送给用户的消息"></textarea>
                <small>{{ replyContent.length }}/2000 · 提交后先进入可靠发送队列，可查看发送与重试状态。</small>
              </label>
              <div class="cmd-rich-row">
                <label class="cmd-form-field">
                  附件类型
                  <select v-model="replyAttachmentType">
                    <option value="IMAGE">图片</option><option value="AUDIO">语音</option>
                    <option value="VIDEO">视频</option><option value="FILE">文件</option>
                  </select>
                </label>
                <label class="cmd-form-field is-grow">
                  附件 URL（可选）
                  <input v-model="replyAttachmentUrl" type="url" maxlength="2048" placeholder="https://…" />
                  <small>由宿主对象存储提供可访问 URL；附件和文字会进入同一可靠发送队列。</small>
                </label>
              </div>
              <div v-if="replyError" class="cmd-form-error">{{ replyError }}</div>
            </main>
            <template #footer><div class="cmd-dialog-footer">
              <Button :disabled="replySaving" @click="closeReply">取消</Button>
              <Button
                type="primary"
                :loading="replySaving"
                :disabled="!replyContent.trim() && !replyAttachmentUrl.trim()"
                @click="submitReply"
              >
                加入发送队列
              </Button>
            </div></template>
      </Modal>

      <Modal :open="handoffOpen" class="cmd-dialog" centered :width="560" :mask-closable="!handoffSaving" :keyboard="!handoffSaving" :closable="!handoffSaving" @cancel="closeHandoff">
            <template #title>
              <div class="as-modal-heading">
                <div id="handoff-title" class="as-modal-title">转人工处理</div>
                <div class="as-modal-subtitle">{{ handoffTarget?.ownerId || handoffTarget?.accountId }} · {{ channel?.name }}</div>
              </div>
            </template>
            <main>
              <div class="cmd-handoff-notice">
                该操作只将消息标记为待人工处理并记录备注，不会发送消息。如需回复用户，请使用“回复消息”。
              </div>
              <label class="cmd-form-field">
                接管备注
                <textarea v-model="handoffNote" rows="4" placeholder="例如：已转交客服组，预计 10 分钟内处理"></textarea>
                <small>可填写处理人、处理原因或后续安排。</small>
              </label>
              <label class="cmd-form-field">
                处理组（可选）
                <input v-model="assignmentGroup" maxlength="128" placeholder="例如：售后支持组" />
              </label>
              <div v-if="handoffError" class="cmd-form-error">{{ handoffError }}</div>
            </main>
            <template #footer><div class="cmd-dialog-footer">
              <Button :disabled="handoffSaving" @click="closeHandoff">取消</Button>
              <Button
                type="primary"
                :loading="handoffSaving"
                @click="submitHandoff"
              >
                确认转人工
              </Button>
            </div></template>
      </Modal>

      <Modal v-model:open="noteOpen" class="cmd-dialog" centered :width="560" :mask-closable="!noteSaving" :keyboard="!noteSaving" :closable="!noteSaving">
            <template #title>
              <div class="as-modal-heading">
                <div class="as-modal-title">添加内部备注</div>
                <div class="as-modal-subtitle">仅企业内部可见，不会发送给外部用户</div>
              </div>
            </template>
            <main><label class="cmd-form-field">备注内容<textarea v-model="noteContent" rows="5" maxlength="2000" placeholder="记录处理过程、判断或交接信息"></textarea></label></main>
            <template #footer><div class="cmd-dialog-footer"><Button :disabled="noteSaving" @click="noteOpen = false">取消</Button><Button type="primary" :loading="noteSaving" :disabled="!noteContent.trim()" @click="submitNote">保存内部备注</Button></div></template>
      </Modal>
    </div>
  </Transition>
</template>

<style scoped>
/* -------- 遮罩与左侧宽抽屉容器 -------- */
.cmd-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 1100;
}
.cmd-drawer {
  position: absolute;
  left: 0;
  top: 0;
  width: min(760px, 96vw);
  height: 100%;
  display: grid;
  grid-template-rows: auto auto 1fr;
  background: #f9fafb;
  box-shadow: 6px 0 22px rgba(15, 23, 42, 0.14);
}
:global(.dark) .cmd-drawer {
  background: #171717;
}

/* 进出场动画（从左滑入） */
.cmd-enter-active,
.cmd-leave-active {
  transition: opacity 0.18s ease;
}
.cmd-enter-active .cmd-drawer,
.cmd-leave-active .cmd-drawer {
  transition: transform 0.18s ease;
}
.cmd-enter-from,
.cmd-leave-to {
  opacity: 0;
}
.cmd-enter-from .cmd-drawer,
.cmd-leave-to .cmd-drawer {
  transform: translateX(-48px);
}

/* -------- 头部 -------- */
.cmd-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.cmd-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  margin-right: auto;
}
.cmd-icon {
  flex-shrink: 0;
}
.cmd-header-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.cmd-header-text b {
  font-size: 15px;
  color: #111827;
}
.cmd-header-text small {
  font-size: 12px;
  color: #9ca3af;
}
.cmd-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cmd-select {
  padding: 7px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  background: #fff;
  color: #111827;
  cursor: pointer;
}
.cmd-auto {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}
.cmd-auto input {
  accent-color: #6366f1;
}
.cmd-close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  font-size: 14px;
  color: #9ca3af;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.cmd-close:hover {
  background: #f3f4f6;
  color: #111827;
}
:global(.dark) .cmd-header {
  background: #1f1f1f;
  border-bottom-color: #2d2d2d;
}
:global(.dark) .cmd-header-text b {
  color: #f3f4f6;
}
:global(.dark) .cmd-select {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .cmd-auto {
  color: #9ca3af;
}
:global(.dark) .cmd-close:hover {
  background: #2d2d2d;
  color: #f3f4f6;
}

/* -------- 主体 -------- */
.cmd-main {
  padding: 18px 20px;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 12px;
}
.cmd-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

/* 消息卡片 */
.cmd-msg {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.cmd-msg > header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #6b7280;
  font-size: 12px;
}
.cmd-msg > header b {
  color: #111827;
  font-size: 13px;
}
.cmd-msg > header em {
  margin-left: auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  font-style: normal;
  white-space: nowrap;
}
.cmd-msg > header em.is-handled {
  color: #047857;
  background: #ecfdf5;
}
.cmd-op {
  border: 0;
  background: none;
  padding: 0;
  font-size: 12px;
  color: #4f46e5;
  cursor: pointer;
}
.cmd-op:hover {
  text-decoration: underline;
}
.cmd-op.is-reply { font-weight: 600; color: #047857; }

/* 气泡 */
.cmd-bubble {
  max-width: 76%;
  margin-top: 12px;
  padding: 10px 13px;
  border-radius: 10px;
}
.cmd-bubble small {
  color: #6b7280;
  font-size: 11px;
}
.cmd-bubble p {
  margin: 5px 0 0;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
.cmd-reply-ref {
  display: grid;
  gap: 3px;
  margin: 7px 0 8px;
  padding: 7px 9px;
  color: #6b7280;
  background: rgba(255, 255, 255, 0.65);
  border-left: 3px solid #10b981;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cmd-reply-ref span { color: #047857; font-weight: 600; }
.cmd-bubble.is-inbound {
  background: #f3f4f6;
}
.cmd-bubble.is-outbound {
  margin-left: auto;
  background: #ecfdf5;
}
.cmd-error {
  margin-top: 10px;
  font-size: 12px;
  color: #b91c1c;
}
:global(.dark) .cmd-msg {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
:global(.dark) .cmd-msg > header {
  color: #9ca3af;
}
:global(.dark) .cmd-msg > header b {
  color: #f3f4f6;
}
:global(.dark) .cmd-msg > header em {
  background: #2d2d2d;
  color: #d1d5db;
}
:global(.dark) .cmd-msg > header em.is-handled {
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
}
:global(.dark) .cmd-op {
  color: #818cf8;
}
:global(.dark) .cmd-bubble small {
  color: #9ca3af;
}
:global(.dark) .cmd-bubble.is-inbound {
  background: #2d2d2d;
}
:global(.dark) .cmd-bubble.is-outbound {
  background: rgba(16, 185, 129, 0.12);
}
:global(.dark) .cmd-reply-ref { color: #9ca3af; background: rgba(0, 0, 0, 0.18); }
:global(.dark) .cmd-error {
  color: #f87171;
}
@media (max-width: 640px) {
  .cmd-bubble {
    max-width: 100%;
  }
}

/* -------- 按钮 -------- */
.cmd-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1250;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.5);
}
.cmd-dialog {
  width: min(520px, 96vw);
  overflow: hidden;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
}
.cmd-dialog > header,
.cmd-dialog > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
}
.cmd-dialog > header { border-bottom: 1px solid #e5e7eb; }
.cmd-dialog main { display: grid; gap: 16px; }
.cmd-dialog-footer { display: flex; justify-content: flex-end; gap: 12px; width: 100%; }
.cmd-dialog > footer { justify-content: flex-end; border-top: 1px solid #e5e7eb; }
.cmd-handoff-notice {
  padding: 11px 13px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
}
.cmd-original-message {
  padding: 11px 13px;
  color: #374151;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.cmd-original-message small { color: #9ca3af; font-size: 11px; }
.cmd-original-message p { margin: 5px 0 0; font-size: 13px; white-space: pre-wrap; word-break: break-word; }
.cmd-form-field { display: grid; gap: 7px; color: #374151; font-size: 13px; font-weight: 500; }
.cmd-form-field textarea {
  box-sizing: border-box;
  width: 100%;
  resize: vertical;
  padding: 10px 12px;
  color: #111827;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font: inherit;
  line-height: 1.5;
}
.cmd-form-field textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.14);
}
.cmd-form-field small { color: #9ca3af; font-size: 11px; font-weight: 400; }
.cmd-form-error { color: #b91c1c; font-size: 12px; }
:global(.dark) .cmd-dialog { background: #1f1f1f; }
:global(.dark) .cmd-dialog > header,
:global(.dark) .cmd-dialog > footer { border-color: #2d2d2d; }
:global(.dark) .cmd-form-field { color: #d1d5db; }
:global(.dark) .cmd-form-field textarea { color: #f3f4f6; background: #2d2d2d; border-color: #3d3d3d; }
:global(.dark) .cmd-handoff-notice { color: #fbbf24; background: rgba(245, 158, 11, 0.12); border-color: rgba(245, 158, 11, 0.3); }
:global(.dark) .cmd-original-message { color: #d1d5db; background: #171717; border-color: #3d3d3d; }
.cmd-attachments { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.cmd-attachments img,
.cmd-attachments video { max-width: min(320px, 100%); max-height: 240px; border-radius: 8px; object-fit: contain; }
.cmd-attachments audio { width: min(320px, 100%); }
.cmd-attachments a,
.cmd-attachments span { padding: 6px 9px; color: #4f46e5; background: #eef2ff; border-radius: 6px; font-size: 12px; }
.cmd-btn {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.cmd-btn.is-primary { color: #fff; background: #4f46e5; border-color: #4f46e5; }
.cmd-btn.is-primary:hover { background: #4338ca; }
.cmd-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.cmd-btn:hover {
  background: #f3f4f6;
}
.cmd-view-tabs { display: flex; align-items: center; gap: 8px; padding: 9px 20px; background: #fff; border-bottom: 1px solid #e5e7eb; }
.cmd-view-tabs button { padding: 6px 10px; color: #6b7280; background: transparent; border: 0; border-radius: 6px; cursor: pointer; }
.cmd-view-tabs button.active { color: #4338ca; background: #eef2ff; font-weight: 600; }
.cmd-view-tabs span { margin-left: auto; color: #6b7280; font-size: 12px; }
.cmd-conversation-list { display: grid; align-content: start; gap: 10px; }
.cmd-conversation { padding: 14px; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; }
.cmd-conversation.breached { border-color: #fca5a5; }
.cmd-conversation > header { display: flex; justify-content: space-between; gap: 12px; }
.cmd-conversation > header div { display: grid; gap: 3px; min-width: 0; }
.cmd-conversation > header small { overflow: hidden; color: #9ca3af; text-overflow: ellipsis; }
.cmd-conversation > header em { align-self: start; padding: 3px 7px; color: #4f46e5; background: #eef2ff; border-radius: 999px; font-size: 11px; font-style: normal; }
.cmd-conversation > header em.status-waiting_human { color: #b45309; background: #fffbeb; }
.cmd-conversation > header em.status-human_active { color: #047857; background: #ecfdf5; }
.cmd-conversation > header em.status-closed { color: #6b7280; background: #f3f4f6; }
.cmd-conversation > p { margin: 10px 0; color: #374151; font-size: 13px; }
.cmd-conversation-meta { display: flex; flex-wrap: wrap; gap: 10px; color: #9ca3af; font-size: 11px; }
.cmd-conversation-meta .is-breached { color: #dc2626; font-weight: 600; }
.cmd-conversation > footer { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 12px; }
.cmd-clear-filter { margin-bottom: 10px; padding: 5px 9px; color: #4338ca; background: #eef2ff; border: 0; border-radius: 6px; cursor: pointer; }
.cmd-bubble.is-internal { margin: 8px 0; padding: 10px 12px; color: #78350f; background: #fffbeb; border: 1px dashed #fbbf24; border-radius: 8px; }
.cmd-form-field input { padding: 9px 11px; color: #111827; background: #fff; border: 1px solid #d1d5db; border-radius: 8px; font: inherit; }
.cmd-form-field select { padding: 9px 11px; color: #111827; background: #fff; border: 1px solid #d1d5db; border-radius: 8px; font: inherit; }
.cmd-rich-row { display: flex; align-items: start; gap: 12px; }
.cmd-rich-row .is-grow { flex: 1; }
:global(.dark) .cmd-view-tabs, :global(.dark) .cmd-conversation { background: #1f1f1f; border-color: #2d2d2d; }
:global(.dark) .cmd-conversation > p { color: #d1d5db; }
:global(.dark) .cmd-form-field input { color: #f3f4f6; background: #2d2d2d; border-color: #3d3d3d; }
:global(.dark) .cmd-form-field select { color: #f3f4f6; background: #2d2d2d; border-color: #3d3d3d; }
:global(.dark) .cmd-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .cmd-btn:hover {
  background: #3d3d3d;
}
</style>
