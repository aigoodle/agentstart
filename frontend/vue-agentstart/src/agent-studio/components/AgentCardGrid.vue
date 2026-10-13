<script setup lang="ts">
/**
 * AgentCardGrid — Dify-style card grid of agents. Card actions (chat, edit,
 * share, delete) are emitted as events so the host owns the surface.
 *
 * Includes a "+ 新建" placeholder card that emits `create` — the host is
 * expected to open a CreateAppModal.
 */
import type { AgentEntity } from '../types';

interface Props {
  agents: AgentEntity[];
  /** Show the leading "+ 新建" placeholder card. Default true. */
  showCreateCard?: boolean;
  /** Disable clicking any card (e.g. when a required model isn't configured). */
  disabledReason?: string;
}

const props = withDefaults(defineProps<Props>(), {
  showCreateCard: true,
});

const emit = defineEmits<{
  (e: 'create'): void;
  (e: 'chat', a: AgentEntity): void;
  (e: 'edit', a: AgentEntity): void;
  (e: 'share', a: AgentEntity): void;
  (e: 'delete', a: AgentEntity): void;
}>();

const ICONS = ['🤖', '💬', '🧠', '🎯', '🛠', '📊', '💡', '⚡', '🔎', '📎'];
const BGS = [
  '#FEF3F2',
  '#EEF4FF',
  '#EFFDF4',
  '#FFF4ED',
  '#F0F9FF',
  '#FEF6EE',
  '#FDF2FA',
  '#F0FDF9',
];
function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = Math.trunc((h << 5) - h + s.charCodeAt(i));
  return Math.abs(h);
}
function iconOf(a: AgentEntity) {
  return ICONS[hashCode(a.id || a.name) % ICONS.length];
}
function bgOf(a: AgentEntity) {
  return BGS[hashCode((a.id || a.name) + '.bg') % BGS.length];
}
function strategyLabel(s?: string): string {
  if (s === 'REACT') return 'ReAct';
  if (s === 'FUNCTION_CALLING') return 'Function Calling';
  if (s === 'PLAN_EXECUTE') return 'Plan & Execute';
  return s ?? '';
}
function fromNow(iso?: string): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86_400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86_400 * 30) return `${Math.floor(diff / 86_400)} 天前`;
  return new Date(iso).toLocaleDateString();
}
</script>

<template>
  <div class="as-grid">
    <div
      v-if="showCreateCard"
      class="as-card as-card-new"
      :class="{ 'as-card-disabled': !!disabledReason }"
      :title="disabledReason"
      @click="!disabledReason && emit('create')"
    >
      <div class="as-new-inner">
        <div class="as-new-plus">+</div>
        <div class="as-new-text">新建应用</div>
        <div class="as-new-sub">选择类型 · 配置模型 · 直接对话</div>
      </div>
    </div>

    <div
      v-for="a in agents"
      :key="a.id"
      class="as-card"
      @click="emit('chat', a)"
    >
      <div class="as-header">
        <div class="as-icon" :style="{ background: bgOf(a) }">{{ iconOf(a) }}</div>
        <div class="as-title-wrap">
          <div class="as-title" :title="a.name">{{ a.name }}</div>
          <div class="as-meta">
            <span class="as-strategy">{{ strategyLabel(a.strategy) }}</span>
            <span>· {{ fromNow(a.updatedAt) || '刚刚' }}</span>
          </div>
        </div>
      </div>

      <div class="as-desc" :title="a.instructions || ''">
        {{ a.instructions || '暂无 Instructions' }}
      </div>

      <div class="as-spacer" />

      <div class="as-footer">
        <button class="as-action" @click.stop="emit('edit', a)">编辑</button>
        <button class="as-action" @click.stop="emit('share', a)">分享</button>
        <button class="as-action as-action-danger" @click.stop="emit('delete', a)">
          删除
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.as-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.as-card {
  position: relative;
  height: 200px;
  padding: 14px 16px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.as-card:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.as-card-new {
  border: 1.5px dashed #c7d2fe;
  background: linear-gradient(135deg, #f5f9ff 0%, #f0f5ff 100%);
}
.as-card-new:hover {
  border-color: #6366f1;
  background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
}
.as-card-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.as-new-inner {
  margin: auto 0;
  text-align: center;
  color: #6366f1;
}
.as-new-plus {
  font-size: 42px;
  line-height: 1;
  font-weight: 200;
}
.as-new-text {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 500;
}
.as-new-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
.as-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.as-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 22px;
  flex-shrink: 0;
}
.as-title-wrap {
  min-width: 0;
  flex: 1;
}
.as-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.as-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
}
.as-strategy {
  padding: 1px 6px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
}
.as-desc {
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.as-spacer {
  flex: 1;
}
.as-footer {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  border-top: 1px solid #f3f4f6;
  padding-top: 8px;
}
.as-action {
  padding: 2px 8px;
  font-size: 12px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #4338ca;
  cursor: pointer;
}
.as-action:hover {
  background: #eef2ff;
}
.as-action-danger {
  color: #dc2626;
}
.as-action-danger:hover {
  background: #fef2f2;
}
</style>
