<script setup lang="ts">
/**
 * AgentChatPage — 独立会话页。
 *
 * 用法：
 *   <AgentChatPage :agent-id="id" api-base="/api" />
 *
 * 组件内部完成：查询 agent 元数据、拉工具列表、加载/切换会话、SSE 流式发送、
 * 变量输入侧栏、curl 快捷复制、失败重试。宿主只需给 agentId + apiBase。
 */
import { computed, nextTick, onMounted, ref } from 'vue';

import {
  Button,
  Card,
  Dropdown,
  Empty,
  Input,
  Menu,
  MenuItem,
  message,
  Tag,
} from '../../ui';

import { readSseEvents } from '../../client';
import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import {
  type AgentEntity,
  type AgentStudioApi,
  type AgentToolView,
  createAgentStudioSpringBackend,
} from '../adapters/springAgentStart';

interface Props {
  /** 应用 id — 必传 (路由参数直接传进来即可)。 */
  agentId: string;
  /** 后端 base URL, 默认 `/api`, 忽略 `api` 覆写. */
  apiBase?: string;
  /** Extra headers; a function is evaluated again before every request. */
  headers?: AgentStartHeaders;
  /** 完整 AgentStudioApi 覆写内建 adapter。 */
  api?: AgentStudioApi;
}
const props = withDefaults(defineProps<Props>(), {
  api: undefined,
});

const globalConfig = useAgentStartConfig();
const resolvedApiBase = computed(
  () => props.apiBase ?? globalConfig.apiBase ?? '/api',
);

const backend = computed<AgentStudioApi>(() =>
  props.api ?? createAgentStudioSpringBackend({
    baseUrl: resolvedApiBase.value,
    headers: () => mergeAgentStartHeaders(globalConfig.headers, props.headers),
  }),
);
// Shim for the copy-curl button — still hard-codes localhost:18090 in the
// original view; keep the same behaviour but expose apiBase for override.
const curlBaseUrl = computed(() => {
  try {
    const u = new URL(resolvedApiBase.value, window.location.origin);
    return u.origin + u.pathname.replace(/\/+$/, '');
  } catch {
    return `http://localhost:18090${resolvedApiBase.value}`;
  }
});

const agentId = computed(() => props.agentId);
const getAgent = (id: string) => backend.value.getAgent(id);
const fetchAgentTools = (id: string) => backend.value.fetchAgentTools(id);
const fetchConversations = (id: string) => backend.value.fetchConversations(id);
const fetchHistory = (id: string, cid: string) =>
  backend.value.fetchHistoryMessages(id, cid);
const chatStream = (id: string, req: any) => backend.value.chatStream(id, req);

/** localStorage key for "which conversation was this agent last using". */
const storageKey = computed(() => `spring-agent:chat:${agentId.value}:conversation`);
/** localStorage key for the list of known conversations for this agent. */
const listKey = computed(() => `spring-agent:chat:${agentId.value}:conversations`);

/** Loaded list of past conversations (id + first user query as preview). */
const conversations = ref<Array<{ id: string; preview: string }>>([]);

async function loadKnownConversations() {
  // Prefer the server list (survives cache clears + different browsers); fall back
  // to localStorage when the backend has never seen this agent's conversations.
  try {
    const server = await fetchConversations(agentId.value);
    if (server.length > 0) {
      conversations.value = server.map((c) => ({
        id: c.conversationId,
        preview: c.firstMessage ?? '',
      }));
      return;
    }
  } catch {
    // fall through to localStorage
  }
  try {
    const raw = localStorage.getItem(listKey.value);
    conversations.value = raw ? JSON.parse(raw) : [];
  } catch {
    conversations.value = [];
  }
}

function rememberConversation(id: string, firstMessage: string) {
  const preview = firstMessage.slice(0, 40) || id.slice(0, 8);
  const existing = conversations.value.find((c) => c.id === id);
  if (existing) {
    existing.preview = existing.preview || preview;
  } else {
    conversations.value = [{ id, preview }, ...conversations.value].slice(0, 20);
  }
  localStorage.setItem(listKey.value, JSON.stringify(conversations.value));
}

const agent = ref<AgentEntity | null>(null);

/** Parsed Dify-parity suggested-question chips shown on the empty conversation. */
const suggestedQuestions = computed<string[]>(() => {
  const raw = agent.value?.suggestedQuestionsJson;
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((q) => typeof q === 'string' && q.trim().length > 0) : [];
  } catch {
    return [];
  }
});
const agentTools = ref<AgentToolView[]>([]);
const messages = ref<
  Array<{
    role: 'user' | 'assistant';
    content: string;
    steps?: Array<Record<string, unknown>>;
    failed?: boolean;
    /** For retry: the user query that produced this failed assistant reply. */
    sourceQuery?: string;
  }>
>([]);

const input = ref('');
const sending = ref(false);
const conversationId = ref<string | undefined>(undefined);
const scroller = ref<HTMLElement | null>(null);

// -------- variables sidebar (for {{#user.xxx#}} style templates) --------
const variables = ref<Array<{ key: string; value: string }>>([
  { key: '', value: '' },
]);
const variablesKey = computed(
  () => `spring-agent:chat:${agentId.value}:variables`,
);
// Persist per-agent so switching agents remembers each one's inputs.
function saveVariables() {
  const map: Record<string, string> = {};
  for (const v of variables.value) {
    if (v.key) map[v.key] = v.value;
  }
  localStorage.setItem(variablesKey.value, JSON.stringify(map));
}
function loadVariables() {
  try {
    const raw = localStorage.getItem(variablesKey.value);
    if (raw) {
      const map = JSON.parse(raw) as Record<string, string>;
      const arr = Object.entries(map).map(([k, v]) => ({ key: k, value: v }));
      variables.value = arr.length > 0 ? arr : [{ key: '', value: '' }];
    }
  } catch {
    // ignore malformed storage
  }
}
function addVariable() {
  variables.value.push({ key: '', value: '' });
}
function removeVariable(idx: number) {
  variables.value.splice(idx, 1);
  if (variables.value.length === 0) addVariable();
  saveVariables();
}
function variablesMap(): Record<string, string> {
  const map: Record<string, string> = {};
  for (const v of variables.value) {
    if (v.key) map[v.key] = v.value;
  }
  return map;
}
function copyCurl() {
  const body = {
    query: input.value || '<your query here>',
    conversationId: conversationId.value,
    variables: variablesMap(),
  };
  const cmd =
    `curl -X POST ${curlBaseUrl.value}/agents/${agentId.value}/chat \\\n` +
    `  -H "Content-Type: application/json" \\\n` +
    `  -d '${JSON.stringify(body).replace(/'/g, `'\\''`)}'`;
  navigator.clipboard.writeText(cmd).then(
    () => message.success('已复制 curl 命令'),
    () => message.error('复制失败，请手动选择'),
  );
}

onMounted(async () => {
  agent.value = await getAgent(agentId.value);
  fetchAgentTools(agentId.value)
    .then((t) => {
      agentTools.value = t;
    })
    .catch(() => {
      agentTools.value = [];
    });
  await loadKnownConversations();
  loadVariables();
  // Recover the last conversation from storage, then load its history.
  const stored = localStorage.getItem(storageKey.value);
  if (stored) {
    conversationId.value = stored;
    await loadHistory();
  }
});

async function switchTo(id: string) {
  conversationId.value = id;
  localStorage.setItem(storageKey.value, id);
  messages.value = [];
  await loadHistory();
}

async function loadHistory() {
  if (!conversationId.value) return;
  try {
    const history = await fetchHistory(agentId.value, conversationId.value);
    messages.value = history.map((h) => ({
      role: h.role === 'USER' ? 'user' : 'assistant',
      content: h.content,
    }));
    scrollBottom();
  } catch {
    // History fetch is best-effort — a fresh conversation should also work.
  }
}

async function scrollBottom() {
  await nextTick();
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight });
}

async function send(overrideText?: string) {
  const text = overrideText ?? input.value.trim();
  if (!text) return;
  if (!overrideText) input.value = '';
  messages.value.push({ role: 'user', content: text });
  const asst = {
    role: 'assistant' as const,
    content: '',
    steps: [] as any[],
    sourceQuery: text,
    failed: false,
  };
  messages.value.push(asst);
  scrollBottom();

  sending.value = true;
  try {
    saveVariables();
    const res = await chatStream(agentId.value, {
      query: text,
      conversationId: conversationId.value,
      variables: variablesMap(),
    });
    if (!res.ok || !res.body) {
      throw new Error(`HTTP ${res.status}`);
    }
    for await (const { event, data } of readSseEvents(res)) {
      try {
        const parsed = JSON.parse(data);
        if (event === 'step') {
          asst.steps!.push(parsed);
          asst.content = livePreview(asst.steps!);
        } else if (event === 'result') {
          asst.content = parsed.text ?? '(无内容)';
          asst.steps = parsed.steps ?? asst.steps;
          conversationId.value = parsed.conversationId;
          if (parsed.conversationId) {
            localStorage.setItem(storageKey.value, parsed.conversationId);
            rememberConversation(parsed.conversationId, text);
          }
        } else if (event === 'error') {
          asst.content = `❌ ${parsed.message ?? '未知错误'}`;
          asst.failed = true;
        }
      } catch {
        // ignore parse error
      }
      scrollBottom();
    }
  } catch (e: any) {
    asst.content = `❌ ${e?.message ?? e}`;
    asst.failed = true;
    message.error(String(e));
  } finally {
    sending.value = false;
    scrollBottom();
  }
}

async function retry(m: { sourceQuery?: string }) {
  if (!m.sourceQuery) return;
  // Drop the failed assistant reply + its user pair so we don't leave stale bubbles.
  // The user query is always the message just before the assistant one.
  const failedIdx = messages.value.findIndex((x) => x === (m as any));
  if (failedIdx >= 1) {
    messages.value.splice(failedIdx - 1, 2);
  }
  await send(m.sourceQuery);
}

function newConversation() {
  conversationId.value = undefined;
  messages.value = [];
  localStorage.removeItem(storageKey.value);
}

/**
 * 边跑边显示最新一步的实际内容，而不是"思考中... N 步"。
 * 优先级：最后一步的 thought → observation → action → 兜底 kind。
 */
function livePreview(steps: Array<Record<string, any>>): string {
  if (steps.length === 0) return '思考中...';
  const last = steps[steps.length - 1] ?? {};
  const kind = String(last.kind ?? '').toUpperCase();
  const truncate = (s: string) => (s.length > 200 ? s.slice(0, 200) + '…' : s);
  if (last.thought) return `💭 ${truncate(String(last.thought))}`;
  if (last.observation) return `👀 ${truncate(String(last.observation))}`;
  if (last.action) return `🔧 调用工具 ${last.action}(${truncate(String(last.actionInput ?? ''))})`;
  return `${kind || 'STEP'} · 第 ${steps.length} 步...`;
}

// Sample placeholder token shown in the variables sidebar. Assembled here so
// the template's `{{ }}` compiler doesn't try to eat the inner `{{`.
const SAMPLE_VAR_TOKEN = '{' + '{#user.xxx#}' + '}';
</script>

<template>
  <div class="agent-start-chat-page">
    <div class="agent-start-chat-page__header">
      <div class="agent-start-chat-page__title">{{ agent?.name ?? '对话' }}</div>
      <div v-if="agent?.instructions" class="agent-start-chat-page__desc">
        {{ agent.instructions }}
      </div>
    </div>
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-4">
      <Card class="xl:col-span-3">
        <!-- Tools this agent can call — helps the user know what to ask -->
        <div
          v-if="agentTools.length > 0"
          class="mb-2 flex flex-wrap items-center gap-1 text-xs"
        >
          <span class="text-gray-500">🔧 可用工具:</span>
          <Tag
            v-for="t in agentTools"
            :key="t.name"
            color="processing"
            :title="t.description"
            style="cursor: help"
          >
            {{ t.name }}
          </Tag>
        </div>
        <div class="mb-3 flex items-center justify-between border-b pb-2">
          <div class="flex items-center gap-2 text-sm text-gray-500">
            conversation:
            <Tag v-if="conversationId">{{ conversationId.slice(0, 8) }}</Tag>
            <Tag v-else color="default">新会话</Tag>
          </div>
          <div class="flex items-center gap-2">
            <Button size="small" @click="copyCurl">复制 curl</Button>
            <Dropdown v-if="conversations.length > 0" trigger="click">
              <Button size="small">
                切换会话 ({{ conversations.length }})
              </Button>
              <template #overlay>
                <Menu>
                  <MenuItem
                    v-for="c in conversations"
                    :key="c.id"
                    @click="switchTo(c.id)"
                  >
                    <div class="text-xs text-gray-500">
                      {{ c.id.slice(0, 8) }}
                    </div>
                    <div class="max-w-[300px] truncate text-sm">
                      {{ c.preview }}
                    </div>
                  </MenuItem>
                </Menu>
              </template>
            </Dropdown>
            <Button size="small" @click="newConversation">新会话</Button>
          </div>
        </div>

      <div
        ref="scroller"
        class="mb-3 h-[520px] overflow-y-auto rounded border border-gray-100 p-3"
      >
        <!-- Dify-parity welcome: opening statement + suggested question chips -->
        <div
          v-if="messages.length === 0 && (agent?.openingStatement || suggestedQuestions.length > 0)"
          class="mb-4"
        >
          <div
            v-if="agent?.openingStatement"
            class="mb-3 rounded-lg border border-indigo-100 bg-indigo-50 p-3 text-sm"
          >
            <div class="mb-1 flex items-center gap-2 text-xs font-medium text-indigo-700">
              <Tag color="green" style="margin: 0">Agent</Tag>
              <span>开场白</span>
            </div>
            <div class="whitespace-pre-wrap text-gray-800">
              {{ agent.openingStatement }}
            </div>
          </div>
          <div
            v-if="suggestedQuestions.length > 0"
            class="flex flex-wrap gap-2"
          >
            <button
              v-for="q in suggestedQuestions"
              :key="q"
              type="button"
              class="rounded-full border border-gray-200 bg-white px-3 py-1 text-sm text-gray-700 hover:border-indigo-400 hover:bg-indigo-50"
              @click="input = q; send();"
            >
              💬 {{ q }}
            </button>
          </div>
        </div>
        <Empty
          v-if="messages.length === 0 && !agent?.openingStatement && suggestedQuestions.length === 0"
          description="发第一条消息试试"
        />
        <div v-for="(m, i) in messages" :key="i" class="mb-4">
          <div class="mb-1 text-xs font-medium">
            <Tag v-if="m.role === 'user'" color="blue">用户</Tag>
            <Tag v-else color="green">Agent</Tag>
          </div>
          <div class="whitespace-pre-wrap text-sm">{{ m.content }}</div>
          <div v-if="m.failed" class="mt-1">
            <Button size="small" @click="retry(m)">重试</Button>
          </div>
          <details v-if="m.role === 'assistant' && m.steps && m.steps.length > 0" class="mt-2">
            <summary class="cursor-pointer text-xs text-gray-500">
              思维链 · {{ m.steps.length }} 步
            </summary>
            <div
              v-for="(s, si) in m.steps"
              :key="si"
              class="mt-1 rounded bg-gray-50 p-2 text-xs"
            >
              <div v-if="(s as any).thought"><b>Thought:</b> {{ (s as any).thought }}</div>
              <div v-if="(s as any).action"><b>Action:</b> {{ (s as any).action }}</div>
              <div v-if="(s as any).toolName"><b>Tool:</b> {{ (s as any).toolName }}({{ (s as any).toolArgsJson }})</div>
              <div v-if="(s as any).observation"><b>Observation:</b> {{ (s as any).observation }}</div>
            </div>
          </details>
        </div>
      </div>

        <div class="flex gap-2">
          <Input.TextArea
            v-model:value="input"
            :rows="2"
            placeholder="输入问题，Ctrl+Enter 发送"
            @press-enter.ctrl="send()"
          />
          <Button type="primary" :loading="sending" @click="send()">
            发送
          </Button>
        </div>
      </Card>

      <!-- Variables sidebar: for prompts that use {{#user.xxx#}} placeholders -->
      <Card title="变量" :body-style="{ padding: '12px 12px 8px' }">
        <div class="mb-2 text-xs text-gray-500">
          Instructions 里的 <code>{{ SAMPLE_VAR_TOKEN }}</code>
          占位符会用这里的键值渲染。持久化到本地。
        </div>
        <div
          v-for="(v, idx) in variables"
          :key="idx"
          class="mb-2 flex items-center gap-1"
        >
          <Input
            v-model:value="v.key"
            placeholder="key"
            size="small"
            style="width: 40%"
            @blur="saveVariables"
          />
          <Input
            v-model:value="v.value"
            placeholder="value"
            size="small"
            style="flex: 1"
            @blur="saveVariables"
          />
          <Button type="text" size="small" danger @click="removeVariable(idx)">
            ×
          </Button>
        </div>
        <Button size="small" block @click="addVariable">+ 添加变量</Button>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.agent-start-chat-page {
  padding: 16px;
}
.agent-start-chat-page__header {
  margin-bottom: 16px;
}
.agent-start-chat-page__title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}
.agent-start-chat-page__desc {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
:global(.dark) .agent-start-chat-page__title {
  color: #f3f4f6;
}
:global(.dark) .agent-start-chat-page__desc {
  color: #9ca3af;
}
</style>
