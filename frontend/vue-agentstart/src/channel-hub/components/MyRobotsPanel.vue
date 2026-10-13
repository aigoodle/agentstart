<script setup lang="ts">
/**
 * 「我的机器人」面板 —— 卡片列表 + 定义/编辑/删除。
 *
 * 人员数据由宿主业务系统通过 `user` prop 注入(组件模块不产生人员数据);
 * `userId` / `tenantId` 仅参与请求,界面上任何位置都不渲染这两个字段。
 */
import { onMounted, ref } from 'vue';

import { Button, Card, message } from '../../ui';

import { createAgentStartClient, type AgentStartClient } from '../../client';
import { useAgentStartClient } from '../../client/vue';
import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import type { MyRobot, RobotUser } from '../types';
import type { ChannelDefinition } from '../types';
import RobotFormModal from './RobotFormModal.vue';

const props = defineProps<{
  apiBase?: string;
  headers?: AgentStartHeaders;
  tenantId?: string;
  client?: AgentStartClient;
  /** 宿主业务系统注入的当前人员信息。 */
  user?: RobotUser;
  /** 宿主可预加载目录；未提供时组件通过统一 SDK 获取。 */
  channels?: ChannelDefinition[];
}>();

const global = useAgentStartConfig();
const injected = useAgentStartClient();
const client =
  props.client ??
  injected ??
  createAgentStartClient({
    baseUrl: props.apiBase ?? global.apiBase ?? '/api',
    getTenant: () => props.tenantId,
    headers: () => mergeAgentStartHeaders(global.headers, props.headers),
  });

// -------- state
const robots = ref<MyRobot[]>([]);
const loading = ref(false);
const error = ref('');
const formOpen = ref(false);
const editing = ref<MyRobot>();
const availableChannels = ref<ChannelDefinition[]>(props.channels ?? []);

async function load() {
  if (!props.user?.userId) {
    error.value = '缺少当前人员信息,无法加载机器人列表';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const [connections, channels] = await Promise.all([
      client.channels.listChannelConnections(props.tenantId, props.user.userId),
      props.channels ? Promise.resolve(props.channels) : client.channels.listChannels(),
    ]);
    robots.value = connections as MyRobot[];
    availableChannels.value = channels.filter((channel) => channel.installed);
  } catch (e: any) {
    error.value = e?.message ?? '加载机器人列表失败';
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = undefined;
  formOpen.value = true;
}

function openEdit(robot: MyRobot) {
  editing.value = robot;
  formOpen.value = true;
}

async function remove(robot: MyRobot) {
  if (!confirm(`删除机器人「${robot.name}」?`)) return;
  try {
    await client.channels.deleteChannelConnection(robot.id, props.tenantId);
    message.success(`已删除机器人「${robot.name}」`);
    await load();
  } catch (e: any) {
    error.value = e?.message ?? '删除失败';
  }
}

function formatTime(value?: string) {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}

onMounted(load);
</script>

<template>
  <div class="mr-root">
    <header class="mr-toolbar">
      <div class="mr-toolbar-head">
        <div class="mr-toolbar-logo">🤖</div>
        <div class="mr-toolbar-text">
          <h2 class="mr-toolbar-title">我的机器人</h2>
          <p class="mr-toolbar-subtitle">
            绑定并维护你自己的消息通道账号；应用工作流在设计器中统一选择通道。
          </p>
        </div>
      </div>
      <div class="mr-toolbar-actions">
        <Button :disabled="loading" @click="load">刷新</Button>
        <Button type="primary" @click="openCreate">
          定义机器人
        </Button>
      </div>
    </header>

    <div v-if="error" class="mr-alert">{{ error }}</div>

    <div v-if="loading" class="mr-state">加载中…</div>
    <div v-else-if="robots.length === 0" class="mr-state">
      还没有机器人,点击右上角「定义机器人」创建第一个吧
    </div>

    <div v-else class="mr-grid">
      <Card variant="management"
        v-for="robot in robots"
        :key="robot.id"
        class="mr-card"
        :title="robot.name"
        :subtitle="`${robot.channelId} · 员工账号`"
        :description="robot.description || '暂无描述'"
      >
        <template #icon>
          <span
            class="mr-icon"
            :style="{ background: robot.iconBackground || '#FFEAD5' }"
          >
            {{ robot.icon || '🤖' }}
          </span>
        </template>

        <template v-if="robot.welcomeMessage" #default>
          <p class="mr-welcome">💬 {{ robot.welcomeMessage }}</p>
        </template>

        <template #meta>
          <span v-if="robot.createdByName">{{ robot.createdByName }}</span>
          <span v-if="robot.updatedAt">· {{ formatTime(robot.updatedAt) }}</span>
        </template>

        <template #actions>
          <Button @click="openEdit(robot)">编辑</Button>
          <Button danger @click="remove(robot)">
            删除
          </Button>
        </template>
      </Card>
    </div>

    <RobotFormModal
      v-model:open="formOpen"
      :client="client"
      :robot="editing"
      :user="user"
      :tenant-id="tenantId"
      :channels="availableChannels"
      @saved="load"
    />
  </div>
</template>

<style scoped>
/* 视觉语言对齐 ConnectorHubApp(ch-*),前缀 mr- */
.mr-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-sizing: border-box;
  padding: 20px 24px;
  color: #111827;
}
@media (max-width: 640px) {
  .mr-root {
    padding: 12px 16px;
  }
}
:global(.dark) .mr-root {
  color: #f3f4f6;
}

.mr-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.mr-toolbar-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.mr-toolbar-logo {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-size: 22px;
}
.mr-toolbar-text {
  min-width: 0;
}
.mr-toolbar-title {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
}
.mr-toolbar-subtitle {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: #6b7280;
}
.mr-toolbar-actions {
  display: flex;
  gap: 8px;
}
:global(.dark) .mr-toolbar {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
:global(.dark) .mr-toolbar-subtitle {
  color: #9ca3af;
}

.mr-alert {
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}
:global(.dark) .mr-alert {
  background: rgba(220, 38, 38, 0.12);
  border-color: rgba(220, 38, 38, 0.4);
  color: #fca5a5;
}

.mr-state {
  padding: 42px 0;
  text-align: center;
  color: #6b7280;
  font-size: 13.5px;
}
:global(.dark) .mr-state {
  color: #9ca3af;
}

.mr-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.mr-card {
  gap: 10px;
}
/* Card footer: meta on the left, action buttons on the right.
   Replicates the old .mr-card-footer layout (with the same border + spacing). */
.mr-card:deep(.as-management-card__footer) {
  gap: 8px;
}
.mr-card:deep(.as-management-card__actions) {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
  margin: 0;
  padding: 0;
  border: 0;
  flex: none;
}
.mr-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.mr-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 10px;
}
.mr-card-title {
  min-width: 0;
  display: grid;
  gap: 2px;
}
.mr-card-title b {
  font-size: 14.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mr-card-title small {
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:global(.dark) .mr-card-title small {
  color: #9ca3af;
}

.mr-desc {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mr-welcome {
  margin: 0;
  padding: 8px 10px;
  font-size: 12.5px;
  color: #374151;
  background: #f9fafb;
  border-radius: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
:global(.dark) .mr-desc {
  color: #d1d5db;
}
:global(.dark) .mr-welcome {
  color: #e5e7eb;
  background: #2a2a2a;
}

.mr-card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}
.mr-meta {
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mr-ops {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
:global(.dark) .mr-card-footer {
  border-color: #2d2d2d;
}

.mr-btn {
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.mr-btn:hover {
  background: #f9fafb;
}
.mr-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.mr-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}
.mr-btn-primary:hover {
  background: #4f52e8;
}
.mr-btn-danger {
  color: #dc2626;
  border-color: #fecaca;
}
.mr-btn-danger:hover {
  background: #fef2f2;
}
:global(.dark) .mr-btn {
  background: #2a2a2a;
  border-color: #3f3f3f;
  color: #e5e7eb;
}
:global(.dark) .mr-btn:hover {
  background: #333;
}
:global(.dark) .mr-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}
:global(.dark) .mr-btn-danger {
  color: #f87171;
  border-color: rgba(220, 38, 38, 0.4);
}
:global(.dark) .mr-btn-danger:hover {
  background: rgba(220, 38, 38, 0.12);
}
</style>
