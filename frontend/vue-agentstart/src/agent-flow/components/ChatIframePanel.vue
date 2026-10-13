<script setup lang="ts">
/**
 * ChatIframePanel — 通用聊天调试壳
 * --------------------------------------------------
 * 承载对同源 / 跨域部署的 antd-react-chat 项目 iframe：
 *   - 顶部 header：标题 + 变量按钮（气泡展开填写调试参数）+ 关闭
 *   - 变量填写：写入 localStorage，chat 项目 provider 在每次发消息前读取，
 *     参数即时生效（无需 postMessage 时序保障）
 *   - 上下文推送：chat 就绪后经 postMessage 下发 host:context / host:command
 *
 * 组件不写死任何 URL / apiKey，全部由宿主注入，方便工作流、Agent、独立
 * playground 各自复用。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import type { ChatDebugVariable } from './chat-iframe-types';

interface Props {
  /** iframe 基础 URL，例如 `/chat/` 或 `http://localhost:5073/chat/` */
  src: string;
  /** 顶部标题，默认「调试与预览」 */
  title?: string;
  /** 追加到 iframe URL 的查询参数（difyApiKey / label 等）；空值/undefined 自动过滤 */
  params?: Record<string, boolean | number | string | null | undefined>;
  /** 待填写的调试变量清单；空数组 → 隐藏"变量"入口 */
  variables?: ChatDebugVariable[];
  /**
   * 会话隔离 key，用于给 localStorage 加命名空间；不填自动生成。
   * chat 项目通过 URL 参数 `debugInputsKey` 拿到全路径，直接读该 key。
   */
  sessionKey?: string;
  /** chat 就绪后 postMessage 下发的上下文（token/user/theme 等业务字段） */
  context?: Record<string, unknown>;
  /** 是否显示关闭按钮 */
  closable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '调试与预览',
  params: () => ({}),
  variables: () => [],
  context: () => ({}),
  closable: true,
});

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'ready'): void;
  (e: 'inputs-change', inputs: Record<string, unknown>): void;
  (e: 'event', payload: { name: string; data?: unknown }): void;
}>();

const CHANNEL = 'boot-school-chat';
const INPUTS_STORAGE_PREFIX = 'spring-agent-chat-inputs:';

/** 会话隔离 key —— 组件生命周期内固定，用于 localStorage 命名空间 */
const sessionId = ref(
  props.sessionKey ||
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`,
);
const inputsStorageKey = computed(
  () => `${INPUTS_STORAGE_PREFIX}${sessionId.value}`,
);

/** 变量值：{ varName: value }，写入 localStorage 供 chat provider 读取 */
const inputValues = ref<Record<string, unknown>>({});
/** 变量气泡卡是否展开 */
const showVarPanel = ref(false);

const iframeRef = ref<HTMLIFrameElement | null>(null);
const iframeLoaded = ref(false);
const chatReady = ref(false);

// ---------------------------------------------------------------------------
// URL 组装：把 params + inputsStorageKey 一起拼上去。
// ---------------------------------------------------------------------------
const iframeSrc = computed(() => {
  try {
    // SSR guard: no window on the server, resolve against `undefined` so a
    // relative `src` simply falls through to the string-concat fallback.
    const base = typeof window !== 'undefined' ? window.location.href : undefined;
    const url = new URL(props.src, base);
    for (const [k, v] of Object.entries(props.params ?? {})) {
      if (v === undefined || v === null || v === '') continue;
      url.searchParams.set(k, String(v));
    }
    url.searchParams.set('debugInputsKey', inputsStorageKey.value);
    return url.toString();
  } catch {
    // 兜底：src 不是合法 URL 时按字符串拼接
    const sep = props.src.includes('?') ? '&' : '?';
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(props.params ?? {})) {
      if (v === undefined || v === null || v === '') continue;
      qs.set(k, String(v));
    }
    qs.set('debugInputsKey', inputsStorageKey.value);
    return `${props.src}${sep}${qs.toString()}`;
  }
});

const targetOrigin = computed(() => {
  try {
    const base = typeof window !== 'undefined' ? window.location.href : undefined;
    return new URL(props.src, base).origin;
  } catch {
    return '*';
  }
});

// ---------------------------------------------------------------------------
// 变量表单：默认值初始化 & 持久化到 localStorage
// ---------------------------------------------------------------------------
function coerceDefault(v: ChatDebugVariable): unknown {
  if (v.defaultValue === undefined || v.defaultValue === null) {
    return typeInitial(v.type);
  }
  const t = (v.type || 'string').toLowerCase();
  if (t === 'number') {
    const n = Number(v.defaultValue);
    return Number.isFinite(n) ? n : '';
  }
  if (t === 'boolean') return !!v.defaultValue;
  if (t === 'object' || t === 'array') {
    if (typeof v.defaultValue === 'string') {
      try {
        return JSON.parse(v.defaultValue);
      } catch {
        return v.defaultValue;
      }
    }
    return v.defaultValue;
  }
  return String(v.defaultValue);
}

function typeInitial(type?: string): unknown {
  const t = (type || 'string').toLowerCase();
  if (t === 'number') return '';
  if (t === 'boolean') return false;
  if (t === 'object') return {};
  if (t === 'array') return [];
  return '';
}

function initFromVariables(vars: ChatDebugVariable[]) {
  const next: Record<string, unknown> = {};
  for (const v of vars) {
    if (!v?.name) continue;
    // 保留用户已经填过的值，只补新增字段的默认值
    next[v.name] =
      v.name in inputValues.value ? inputValues.value[v.name] : coerceDefault(v);
  }
  inputValues.value = next;
  persistInputs();
}

function persistInputs() {
  try {
    window.localStorage.setItem(
      inputsStorageKey.value,
      JSON.stringify(inputValues.value),
    );
  } catch {
    /* localStorage 不可用则退化为仅当前会话生效 */
  }
  emit('inputs-change', { ...inputValues.value });
  // chat 已就绪时同步 push 一次，避免用户在 chat 页做了缓存
  pushInputsToChat();
}

/** 校验 required 是否都填了；返回缺失的 label 列表 */
const missingRequired = computed(() => {
  return (props.variables ?? [])
    .filter((v) => {
      if (!v.required) return false;
      const val = inputValues.value[v.name];
      if (val === undefined || val === null) return true;
      if (typeof val === 'string' && val.trim() === '') return true;
      return false;
    })
    .map((v) => v.label || v.name);
});

/** 展示在按钮上的"未填必填数" */
const requiredMissingCount = computed(() => missingRequired.value.length);

function onValueChange(name: string, value: unknown) {
  inputValues.value = { ...inputValues.value, [name]: value };
  persistInputs();
}

function onJsonBlur(name: string, raw: string) {
  const trimmed = raw.trim();
  if (!trimmed) {
    onValueChange(name, undefined);
    return;
  }
  try {
    onValueChange(name, JSON.parse(trimmed));
  } catch {
    // 解析失败保留原字符串，用户下次修改再试
    onValueChange(name, raw);
  }
}

function resetAll() {
  const next: Record<string, unknown> = {};
  for (const v of props.variables ?? []) {
    if (!v?.name) continue;
    next[v.name] = coerceDefault(v);
  }
  inputValues.value = next;
  persistInputs();
}

// ---------------------------------------------------------------------------
// postMessage 通讯（复用 chat 项目已用的 boot-school-chat 信封）
// ---------------------------------------------------------------------------
function postToChat(type: string, payload?: unknown) {
  const win = iframeRef.value?.contentWindow;
  if (!win) return;
  win.postMessage({ channel: CHANNEL, type, payload }, targetOrigin.value);
}

function pushContextToChat() {
  if (!chatReady.value) return;
  postToChat('host:context', props.context ?? {});
}

/** chat 已就绪时把最新变量值以 host:command 形式再推一次（storage 已同步，这里主要为 UI 层预热） */
function pushInputsToChat() {
  if (!chatReady.value) return;
  postToChat('host:command', {
    type: 'set-inputs',
    inputs: { ...inputValues.value },
  });
}

function onMessage(event: MessageEvent) {
  const src = iframeRef.value?.contentWindow;
  if (!src || event.source !== src) return;
  const data = event.data as { channel?: string; type?: string; payload?: unknown } | null;
  if (!data || data.channel !== CHANNEL || !data.type) return;
  switch (data.type) {
    case 'chat:ready':
      chatReady.value = true;
      pushContextToChat();
      pushInputsToChat();
      emit('ready');
      break;
    case 'chat:close':
      emit('close');
      break;
    case 'chat:event': {
      const payload = data.payload as { name?: unknown; data?: unknown } | null;
      if (payload && typeof payload.name === 'string') {
        emit('event', { name: payload.name, data: payload.data });
      }
      break;
    }
    default:
      break;
  }
}

function onIframeLoad() {
  iframeLoaded.value = true;
}

function reload() {
  chatReady.value = false;
  iframeLoaded.value = false;
  if (iframeRef.value) {
    iframeRef.value.src = iframeSrc.value;
  }
}

// 组件挂载：绑定监听 + 用初始 variables 建仓。
// 必须放在 onMounted 里 —— setup() 在 SSR 时也会执行，顶层直接
// window.addEventListener 会在服务端渲染时抛 ReferenceError；
// initFromVariables 会写 localStorage，同样只在浏览器里有意义。
onMounted(() => {
  window.addEventListener('message', onMessage);
  initFromVariables(props.variables ?? []);
});

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage);
  // 清理该会话的 localStorage 输入，避免残留污染下一次挂载
  try {
    window.localStorage.removeItem(inputsStorageKey.value);
  } catch {
    /* noop */
  }
});

// props.variables 变化（宿主重新收集了 START 节点变量） → 用新 schema 合并
watch(
  () => props.variables,
  (next) => {
    initFromVariables(next ?? []);
  },
  { deep: true },
);

// props.context 变化 → 立即推给 chat
watch(
  () => props.context,
  () => {
    pushContextToChat();
  },
  { deep: true },
);

// src / params 变化 → 重新加载 iframe（用户切换调试目标应用时有用）
watch(
  () => iframeSrc.value,
  async () => {
    await nextTick();
    reload();
  },
);

defineExpose({
  reload,
  getInputs: () => ({ ...inputValues.value }),
  setInput: onValueChange,
  postMessage: postToChat,
});
</script>

<template>
  <div class="cip-root">
    <!-- 头部：标题 + 变量入口 + 关闭 -->
    <div class="cip-head">
      <div class="cip-head-title" :title="title">
        <span class="cip-dot" />
        {{ title }}
      </div>
      <div class="cip-head-actions">
        <button
          v-if="(variables?.length ?? 0) > 0"
          type="button"
          class="cip-var-btn"
          :class="{ 'cip-var-btn-open': showVarPanel, 'cip-var-btn-warn': requiredMissingCount > 0 }"
          :title="requiredMissingCount > 0 ? `${requiredMissingCount} 个必填项待填` : '编辑调试参数'"
          @click="showVarPanel = !showVarPanel"
        >
          <span class="cip-var-btn-icon" aria-hidden="true">◆</span>
          <span>变量</span>
          <span
            v-if="variables.length > 0"
            class="cip-var-btn-badge"
            :class="{ 'cip-var-btn-badge-warn': requiredMissingCount > 0 }"
          >
            {{ requiredMissingCount > 0 ? requiredMissingCount : variables.length }}
          </span>
        </button>
        <button
          type="button"
          class="cip-icon-btn"
          title="刷新会话"
          @click="reload"
        >
          ↻
        </button>
        <button
          v-if="closable"
          type="button"
          class="cip-icon-btn"
          title="关闭"
          @click="emit('close')"
        >
          ✕
        </button>
      </div>
    </div>

    <!-- 变量填写卡（气泡样式，浮在 iframe 上） -->
    <div
      v-if="showVarPanel && variables.length > 0"
      class="cip-var-panel hud-panel"
    >
      <div class="cip-var-panel-head">
        <span class="cip-var-panel-title">调试参数</span>
        <div class="cip-var-panel-actions">
          <button
            type="button"
            class="cip-link-btn"
            title="重置为默认值"
            @click="resetAll"
          >
            重置
          </button>
          <button
            type="button"
            class="cip-icon-btn cip-icon-btn-sm"
            title="收起"
            @click="showVarPanel = false"
          >
            ✕
          </button>
        </div>
      </div>
      <div v-if="missingRequired.length > 0" class="cip-var-warn">
        必填项未填：{{ missingRequired.join('、') }}
      </div>
      <div class="cip-var-body">
        <div v-for="v in variables" :key="v.name" class="cip-var-row">
          <label class="cip-var-label" :title="v.description">
            {{ v.label || v.name }}
            <span v-if="v.required" class="cip-var-required">*</span>
            <span class="cip-var-type">{{ (v.type || 'string').toLowerCase() }}</span>
          </label>
          <!-- string / 未识别类型 → 单行输入 -->
          <template v-if="!v.type || ['string', 'file'].includes(v.type.toLowerCase())">
            <input
              class="cip-input"
              :value="(inputValues[v.name] as string) ?? ''"
              :placeholder="v.description || v.name"
              @input="(e) => onValueChange(v.name, (e.target as HTMLInputElement).value)"
            />
          </template>
          <!-- number -->
          <template v-else-if="v.type.toLowerCase() === 'number'">
            <input
              type="number"
              class="cip-input"
              :value="(inputValues[v.name] as number) ?? ''"
              :placeholder="v.description"
              @input="(e) => onValueChange(v.name, Number((e.target as HTMLInputElement).value))"
            />
          </template>
          <!-- boolean -->
          <template v-else-if="v.type.toLowerCase() === 'boolean'">
            <label class="cip-switch">
              <input
                type="checkbox"
                :checked="!!inputValues[v.name]"
                @change="(e) => onValueChange(v.name, (e.target as HTMLInputElement).checked)"
              />
              <span>{{ inputValues[v.name] ? '是' : '否' }}</span>
            </label>
          </template>
          <!-- object / array → JSON textarea -->
          <template v-else>
            <textarea
              class="cip-textarea"
              rows="3"
              :value="
                typeof inputValues[v.name] === 'string'
                  ? (inputValues[v.name] as string)
                  : JSON.stringify(inputValues[v.name] ?? (v.type.toLowerCase() === 'array' ? [] : {}), null, 2)
              "
              :placeholder="`JSON ${v.type}`"
              @blur="(e) => onJsonBlur(v.name, (e.target as HTMLTextAreaElement).value)"
            />
          </template>
        </div>
      </div>
      <div class="cip-var-panel-foot">
        参数会自动同步到聊天窗口，无需保存
      </div>
    </div>

    <!-- iframe 主体 -->
    <div class="cip-body">
      <iframe
        ref="iframeRef"
        class="cip-iframe"
        :src="iframeSrc"
        title="调试聊天"
        allow="microphone; clipboard-write"
        @load="onIframeLoad"
      />
      <div v-if="!iframeLoaded" class="cip-loading">
        <div class="cip-spinner"><span /><span /></div>
        <div class="cip-loading-text">聊天窗口加载中</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cip-root {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}
.cip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
  background: #fff;
  flex-shrink: 0;
}
.cip-head-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  flex-shrink: 0;
}
.cip-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.cip-var-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.cip-var-btn:hover {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4338ca;
}
.cip-var-btn-open {
  background: #eef2ff;
  border-color: #a5b4fc;
  color: #4338ca;
}
.cip-var-btn-warn {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}
.cip-var-btn-icon {
  color: #6366f1;
  font-size: 10px;
}
.cip-var-btn-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #e0e7ff;
  color: #4338ca;
  font-size: 10px;
  line-height: 1;
}
.cip-var-btn-badge-warn {
  background: #fee2e2;
  color: #b91c1c;
}
.cip-icon-btn {
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
}
.cip-icon-btn:hover {
  background: #f1f5f9;
  color: #475569;
}
.cip-icon-btn-sm {
  width: 22px;
  height: 22px;
  font-size: 12px;
}

/* 变量气泡卡：浮在 iframe 上方，右上角伸出，尾巴指向按钮 */
.cip-var-panel {
  position: absolute;
  top: 46px;
  right: 12px;
  z-index: 5;
  width: min(360px, calc(100% - 24px));
  max-height: min(60vh, 480px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 10px 32px rgba(15, 23, 42, 0.14),
    0 2px 6px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  animation: cip-pop-in 0.16s ease-out;
}
@keyframes cip-pop-in {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
.cip-var-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.cip-var-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.cip-var-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.cip-link-btn {
  padding: 2px 6px;
  border: none;
  background: transparent;
  color: #4338ca;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
}
.cip-link-btn:hover {
  background: #eef2ff;
}
.cip-var-warn {
  padding: 6px 12px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 11px;
  border-bottom: 1px solid #fee2e2;
}
.cip-var-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px 4px;
}
.cip-var-row {
  padding: 8px 0;
  border-bottom: 1px dashed #f1f5f9;
}
.cip-var-row:last-child {
  border-bottom: none;
}
.cip-var-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #475569;
}
.cip-var-required {
  color: #ef4444;
}
.cip-var-type {
  margin-left: auto;
  padding: 0 6px;
  background: #f1f5f9;
  color: #94a3b8;
  border-radius: 999px;
  font-size: 10px;
  line-height: 16px;
}
.cip-input,
.cip-textarea {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.cip-input:focus,
.cip-textarea:focus {
  border-color: #6366f1;
  background: #fff;
}
.cip-textarea {
  resize: vertical;
  min-height: 60px;
  font-family: 'Menlo', 'Consolas', monospace;
}
.cip-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}
.cip-var-panel-foot {
  padding: 8px 12px;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 11px;
}

/* iframe 主体 */
.cip-body {
  flex: 1;
  min-height: 0;
  position: relative;
  background: #f8fafc;
}
.cip-iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}
.cip-loading {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #f8fafc;
}
.cip-spinner {
  position: relative;
  width: 36px;
  height: 36px;
}
.cip-spinner span {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid transparent;
  border-top-color: #6366f1;
  animation: cip-spin 1s linear infinite;
}
.cip-spinner span:nth-child(2) {
  inset: 6px;
  border-top-color: #a855f7;
  animation-duration: 1.4s;
  animation-direction: reverse;
}
.cip-loading-text {
  color: #94a3b8;
  font-size: 12px;
}
@keyframes cip-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
