<script setup lang="ts">
/**
 * 调试与预览 — right-hand panel from Dify's orchestrate tab. Host-owned messages
 * list; input events surface as `send(query)` so the host wires its SSE/stream.
 */
import { nextTick, ref, watch } from 'vue';

import type { StudioChatMessage } from '../types';

interface Props {
  messages: StudioChatMessage[];
  loading?: boolean;
  placeholder?: string;
  /** Text under the input — Dify shows "对话已开启" or similar. */
  hint?: string;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  placeholder: '和 Bot 聊天',
  hint: '功能已开启',
});

const emit = defineEmits<{
  (e: 'send', query: string): void;
  (e: 'restart'): void;
}>();

const input = ref('');
const scroller = ref<HTMLElement | null>(null);

async function scrollBottom() {
  await nextTick();
  scroller.value?.scrollTo({
    top: scroller.value.scrollHeight,
    behavior: 'smooth',
  });
}

watch(
  () => props.messages.length,
  () => {
    scrollBottom();
  },
);

function submit() {
  const q = input.value.trim();
  if (!q || props.loading) return;
  input.value = '';
  emit('send', q);
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    submit();
  }
}
</script>

<template>
  <div class="dbg-card">
    <div class="dbg-head">
      <div class="dbg-head-title">调试与预览</div>
      <button
        type="button"
        class="dbg-head-btn"
        title="清空会话重新开始"
        @click="emit('restart')"
      >
        ⟳
      </button>
    </div>

    <div ref="scroller" class="dbg-body">
      <div v-if="messages.length === 0" class="dbg-empty">
        <div class="dbg-empty-title">👋 开始一个对话</div>
        <div class="dbg-empty-sub">在下方输入内容测试你的智能体</div>
      </div>
      <div
        v-for="(m, i) in messages"
        :key="i"
        class="dbg-msg"
        :class="{ 'dbg-msg-user': m.role === 'user' }"
      >
        <div class="dbg-avatar" v-if="m.role === 'assistant'">🤖</div>
        <div class="dbg-bubble" :class="{ 'dbg-bubble-failed': m.failed }">
          <div class="dbg-bubble-body">{{ m.content || '思考中...' }}</div>
          <details
            v-if="m.role === 'assistant' && m.steps && m.steps.length > 0"
            class="dbg-steps"
          >
            <summary>思维链 · {{ m.steps.length }} 步</summary>
            <div
              v-for="(s, si) in m.steps"
              :key="si"
              class="dbg-step"
            >
              <div v-if="(s as any).thought">
                <b>Thought:</b> {{ (s as any).thought }}
              </div>
              <div v-if="(s as any).action">
                <b>Action:</b> {{ (s as any).action }}
              </div>
              <div v-if="(s as any).observation">
                <b>Observation:</b> {{ (s as any).observation }}
              </div>
            </div>
          </details>
        </div>
        <div class="dbg-avatar" v-if="m.role === 'user'">🧑</div>
      </div>
    </div>

    <div class="dbg-input-wrap">
      <div class="dbg-input-row">
        <textarea
          v-model="input"
          class="dbg-input"
          :placeholder="placeholder"
          rows="1"
          :disabled="loading"
          @keydown="onKeydown"
        />
        <button
          type="button"
          class="dbg-send"
          :disabled="!input.trim() || loading"
          @click="submit"
        >
          <span v-if="loading">…</span>
          <span v-else>➤</span>
        </button>
      </div>
      <div class="dbg-input-hint">
        <span class="dbg-hint-dot">●</span>
        {{ hint }}
        <button
          type="button"
          class="dbg-hint-link"
          @click="emit('restart')"
        >
          管理 →
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dbg-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}
.dbg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
}
.dbg-head-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.dbg-head-btn {
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 15px;
  cursor: pointer;
}
.dbg-head-btn:hover {
  background: #f1f5f9;
  color: #475569;
}
.dbg-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbff;
}
.dbg-empty {
  padding: 60px 20px;
  text-align: center;
  color: #94a3b8;
}
.dbg-empty-title {
  font-size: 15px;
  color: #64748b;
}
.dbg-empty-sub {
  margin-top: 4px;
  font-size: 12px;
}
.dbg-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  align-items: flex-start;
}
.dbg-msg-user {
  flex-direction: row-reverse;
}
.dbg-avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eef2ff;
  font-size: 15px;
}
.dbg-msg-user .dbg-avatar {
  background: #ede9fe;
}
.dbg-bubble {
  max-width: 78%;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  font-size: 13px;
  color: #0f172a;
  line-height: 1.6;
}
.dbg-msg-user .dbg-bubble {
  background: #4f46e5;
  color: #fff;
  border-color: #4f46e5;
}
.dbg-bubble-failed {
  background: #fef2f2;
  border-color: #fecaca;
  color: #991b1b;
}
.dbg-bubble-body {
  white-space: pre-wrap;
  word-break: break-word;
}
.dbg-steps {
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
}
.dbg-steps summary {
  cursor: pointer;
  padding: 2px 0;
}
.dbg-step {
  padding: 6px 8px;
  margin-top: 4px;
  background: #f8fafc;
  border-radius: 6px;
  color: #475569;
}
.dbg-input-wrap {
  padding: 10px 12px 12px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
}
.dbg-input-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 4px 6px 4px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}
.dbg-input-row:focus-within {
  border-color: #6366f1;
  background: #fff;
}
.dbg-input {
  flex: 1;
  min-height: 24px;
  max-height: 120px;
  padding: 6px 0;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  color: #0f172a;
  line-height: 1.5;
}
.dbg-input:disabled {
  opacity: 0.6;
}
.dbg-send {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.05s ease;
}
.dbg-send:hover:not(:disabled) {
  transform: scale(1.05);
}
.dbg-send:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
.dbg-input-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding-left: 4px;
  font-size: 11px;
  color: #94a3b8;
}
.dbg-hint-dot {
  color: #22c55e;
  font-size: 8px;
}
.dbg-hint-link {
  margin-left: auto;
  padding: 0;
  border: none;
  background: transparent;
  color: #4338ca;
  font-size: 11px;
  cursor: pointer;
}
.dbg-hint-link:hover {
  text-decoration: underline;
}
</style>
