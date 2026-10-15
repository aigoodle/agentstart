<script setup lang="ts">
import { Card, Button, Tag } from '../../ui';

import { Button as AsShadcnButton } from '../../ui/shadcn/button';

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
  <div class="agent-card-grid">
    <Card v-if="showCreateCard" variant="create" title="新建应用" subtitle="选择类型 · 配置模型 · 直接对话" :disabled="!!disabledReason" @click="emit('create')" />
    <Card v-for="a in agents" :key="a.id" variant="management" :title="a.name" :description="a.instructions || '暂无 Instructions'" @click="emit('chat', a)">
      <template #icon>{{ iconOf(a) }}</template>
      <template #subtitle><Tag v-if="strategyLabel(a.strategy)" color="blue">{{ strategyLabel(a.strategy) }}</Tag><span>· {{ fromNow(a.updatedAt) || '刚刚' }}</span></template>
      <template #actions>
        <Button type="text" size="small" @click.stop="emit('edit', a)">编辑</Button>
        <Button type="text" size="small" @click.stop="emit('share', a)">分享</Button>
        <Button type="text" size="small" danger @click.stop="emit('delete', a)">删除</Button>
      </template>
    </Card>
  </div>
</template>
<style scoped>
.agent-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
</style>
