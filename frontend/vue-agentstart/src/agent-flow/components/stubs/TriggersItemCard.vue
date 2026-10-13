<script setup lang="ts">
import { computed, onMounted, ref, watch, watchEffect } from 'vue';
import { ApiOutlined, ClockCircleOutlined, MessageOutlined, ThunderboltOutlined } from '@ant-design/icons-vue';

import { createAgentStartClient } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import type { ChannelConnection, ChannelDefinition } from '../../../channel-hub/types';

const formState: any = defineModel();
const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(global.headers),
});
const channels = ref<ChannelDefinition[]>([]);
const connections = ref<ChannelConnection[]>([]);
const connectionsLoaded = ref(false);
const connectorLoading = ref(false);
const connectorError = ref('');

const TRIGGERS = [
  { key: 'http', label: 'HTTP 请求', icon: ApiOutlined, color: '#06b6d4' },
  { key: 'schedule', label: '定时', icon: ClockCircleOutlined, color: '#f59e0b' },
  { key: 'webhook', label: 'Webhook', icon: ThunderboltOutlined, color: '#8b5cf6' },
  { key: 'connector', label: '消息连接器', icon: MessageOutlined, color: '#ec4899' },
];

const enabled = computed({
  get: () => formState.value?.triggersEnabled ?? false,
  set: (value: boolean) => (formState.value.triggersEnabled = value),
});

function ensureTrigger() {
  if (!formState.value.triggers) formState.value.triggers = {};
  const trigger = formState.value.triggers;
  trigger.type ??= 'http';
  trigger.name ??= '工作流定时任务';
  trigger.targetWorkflowId ??= '';
  trigger.scheduleType ??= 'CRON';
  trigger.recurrence ??= 'DAILY';
  trigger.time ??= '08:00';
  trigger.weekDay ??= 1;
  trigger.dayOfMonth ??= 1;
  trigger.timeZone ??= 'Asia/Shanghai';
  trigger.payloadJson ??= '{}';
  trigger.provider ??= '';
  trigger.channelId ??= '';
  trigger.channelName ??= '';
  trigger.connectionId ??= '';
  trigger.connectionName ??= '';
  trigger.accountScope ??= trigger.connectionId ? 'SPECIFIC' : 'ALL';
  trigger.messageTypes ??= [];
  trigger.replyMode ??= 'ASYNC';
  return trigger;
}

const currentType = computed({
  get: () => ensureTrigger().type,
  set: (value: string) => (ensureTrigger().type = value),
});

function generatedCron(): string {
  const trigger = ensureTrigger();
  const [hour = '8', minute = '0'] = String(trigger.time || '08:00').split(':');
  if (trigger.recurrence === 'WEEKLY') return `0 ${Number(minute)} ${Number(hour)} * * ${trigger.weekDay}`;
  if (trigger.recurrence === 'MONTHLY') return `0 ${Number(minute)} ${Number(hour)} ${trigger.dayOfMonth} * *`;
  if (trigger.recurrence === 'CUSTOM') return trigger.expression || '';
  return `0 ${Number(minute)} ${Number(hour)} * * *`;
}

watchEffect(() => {
  const trigger = ensureTrigger();
  if (trigger.type === 'schedule' && trigger.scheduleType === 'CRON') {
    trigger.expression = generatedCron();
  }
});

const schedulePreview = computed(() => {
  const trigger = ensureTrigger();
  if (trigger.scheduleType === 'ONCE') return trigger.runAt ? `一次：${trigger.runAt}` : '请选择执行时间';
  return `Cron：${generatedCron()}（${trigger.timeZone}）`;
});

const installedChannels = computed(() => channels.value.filter((channel) =>
  channel.enabled && channel.runtimeStatus !== 'DISABLED'));
const selectedChannel = computed(() => installedChannels.value.find((channel) =>
  channel.provider === ensureTrigger().provider && channel.channelId === ensureTrigger().channelId));
const availableConnections = computed(() => connections.value.filter((connection) =>
  connection.provider === ensureTrigger().provider
  && connection.channelId === ensureTrigger().channelId
  && connection.desiredStatus === 'ACTIVE'));
const selectedConnection = computed(() => availableConnections.value.find((connection) =>
  connection.id === ensureTrigger().connectionId));
const accountScope = computed({
  get: () => ensureTrigger().accountScope,
  set: (value: string) => {
    ensureTrigger().accountScope = value;
    if (value === 'ALL') {
      ensureTrigger().connectionId = '';
      ensureTrigger().connectionName = '';
    }
  },
});
watch(() => [ensureTrigger().provider, ensureTrigger().channelId], () => {
  const trigger = ensureTrigger();
  trigger.channelName = selectedChannel.value?.name ?? '';
  if (connectionsLoaded.value && trigger.connectionId && !selectedConnection.value) {
    trigger.connectionId = '';
    trigger.connectionName = '';
    trigger.accountScope = 'ALL';
  }
}, { immediate: true });
watch(() => ensureTrigger().connectionId, () => {
  ensureTrigger().connectionName = selectedConnection.value?.name ?? '';
});

function selectChannel(value: string) {
  const [provider, ...parts] = value.split(':');
  const trigger = ensureTrigger();
  trigger.provider = provider;
  trigger.channelId = parts.join(':');
  trigger.connectionId = '';
  trigger.connectionName = '';
  trigger.accountScope = 'ALL';
}

async function loadMessageConnectors() {
  connectorLoading.value = true;
  connectorError.value = '';
  try {
    const [loadedChannels, loadedConnections] = await Promise.all([
      client.channels.listChannels(),
      client.channels.listChannelConnections(),
    ]);
    channels.value = loadedChannels;
    connections.value = loadedConnections;
    connectionsLoaded.value = true;
    const trigger = ensureTrigger();
    if (trigger.connectionId && !selectedConnection.value) {
      trigger.connectionId = '';
      trigger.connectionName = '';
      trigger.accountScope = 'ALL';
    } else if (trigger.connectionId) {
      trigger.connectionName = selectedConnection.value?.name ?? '';
    }
  } catch (error: any) {
    connectorError.value = error?.message ?? '加载消息连接器失败';
  } finally {
    connectorLoading.value = false;
  }
}

onMounted(loadMessageConnectors);
</script>

<template>
  <div class="wf-triggers">
    <div class="wf-triggers-toggle">
      <span>启用触发器</span>
      <a-switch v-model:checked="enabled" size="small" />
    </div>

    <div v-if="enabled" class="wf-triggers-types">
      <button
        v-for="item in TRIGGERS"
        :key="item.key"
        type="button"
        class="wf-triggers-type"
        :class="{ 'is-active': currentType === item.key }"
        :style="{ '--tc': item.color }"
        @click="currentType = item.key"
      >
        <component :is="item.icon" class="wf-triggers-type-icon" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <div v-if="enabled && currentType === 'schedule'" class="wf-schedule-form">
      <label>任务名称<a-input v-model:value="formState.triggers.name" /></label>
      <label>执行工作流 ID<a-input v-model:value="formState.triggers.targetWorkflowId" placeholder="可选择当前或另一个已发布工作流" /></label>
      <label>计划类型
        <a-select v-model:value="formState.triggers.scheduleType">
          <a-select-option value="CRON">周期任务</a-select-option>
          <a-select-option value="ONCE">一次性任务</a-select-option>
        </a-select>
      </label>

      <template v-if="formState.triggers.scheduleType === 'CRON'">
        <label>周期
          <a-select v-model:value="formState.triggers.recurrence">
            <a-select-option value="DAILY">每天</a-select-option>
            <a-select-option value="WEEKLY">每周</a-select-option>
            <a-select-option value="MONTHLY">每月</a-select-option>
            <a-select-option value="CUSTOM">自定义 Cron</a-select-option>
          </a-select>
        </label>
        <label v-if="formState.triggers.recurrence !== 'CUSTOM'">执行时间
          <a-input v-model:value="formState.triggers.time" type="time" />
        </label>
        <label v-if="formState.triggers.recurrence === 'WEEKLY'">星期
          <a-select v-model:value="formState.triggers.weekDay">
            <a-select-option v-for="(name, index) in ['周日','周一','周二','周三','周四','周五','周六']" :key="index" :value="index">{{ name }}</a-select-option>
          </a-select>
        </label>
        <label v-if="formState.triggers.recurrence === 'MONTHLY'">每月日期
          <a-input-number v-model:value="formState.triggers.dayOfMonth" :min="1" :max="31" />
        </label>
        <label v-if="formState.triggers.recurrence === 'CUSTOM'">Cron 表达式
          <a-input v-model:value="formState.triggers.expression" placeholder="0 0 8 * * *" />
        </label>
      </template>
      <label v-else>执行时间
        <a-input v-model:value="formState.triggers.runAt" type="datetime-local" />
      </label>

      <label>时区<a-input v-model:value="formState.triggers.timeZone" placeholder="Asia/Shanghai" /></label>
      <label>继续会话 ID（可选）<a-input v-model:value="formState.triggers.conversationId" placeholder="定时执行时继续原多轮会话" /></label>
      <label>工作流输入 JSON<a-textarea v-model:value="formState.triggers.payloadJson" :rows="3" placeholder='{"schoolId":"001"}' /></label>
      <div class="wf-schedule-preview">{{ schedulePreview }}</div>
      <div class="wf-schedule-note">保存/发布后由后端持久化调度；集群部署时同一周期只会被一个节点抢占执行。</div>
    </div>

    <div v-else-if="enabled && currentType === 'connector'" class="wf-connector-form">
      <div v-if="connectorLoading" class="wf-connector-status">正在加载消息连接器…</div>
      <div v-else-if="connectorError" class="wf-connector-error">
        {{ connectorError }}
        <a-button size="small" type="link" @click="loadMessageConnectors">重试</a-button>
      </div>

      <label>消息连接器
        <a-select
          :value="formState.triggers.provider && formState.triggers.channelId ? `${formState.triggers.provider}:${formState.triggers.channelId}` : undefined"
          placeholder="请选择已接入的消息平台"
          show-search
          :filter-option="(input: string, option: any) => String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())"
          @change="selectChannel"
        >
          <a-select-option
            v-for="channel in installedChannels"
            :key="`${channel.provider}:${channel.channelId}`"
            :value="`${channel.provider}:${channel.channelId}`"
            :label="`${channel.name} ${channel.provider}`"
          >
            {{ channel.name }}（{{ channel.provider }}）
          </a-select-option>
        </a-select>
      </label>

      <div v-if="!connectorLoading && !connectorError && installedChannels.length === 0" class="wf-connector-warning">
        暂无已安装并启用的消息连接器，请先在连接器中心完成接入。
      </div>

      <template v-if="formState.triggers.provider && formState.triggers.channelId">
        <label>账户接入方式
          <a-select v-model:value="accountScope">
            <a-select-option value="ALL">全部账户</a-select-option>
            <a-select-option value="SPECIFIC">指定账户</a-select-option>
          </a-select>
        </label>

        <label v-if="accountScope === 'SPECIFIC'">接入账户
          <a-select
            v-model:value="formState.triggers.connectionId"
            placeholder="请选择已接入且启用的账户"
            show-search
            :filter-option="(input: string, option: any) => String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())"
          >
            <a-select-option
              v-for="connection in availableConnections"
              :key="connection.id"
              :value="connection.id"
              :label="connection.name"
            >
              {{ connection.name }}
            </a-select-option>
          </a-select>
        </label>

        <div v-if="accountScope === 'SPECIFIC' && availableConnections.length === 0" class="wf-connector-warning">
          当前消息连接器暂无已启用的接入账户，请先在连接器中心添加并启用账户。
        </div>
      </template>

      <label>接收的消息类型
        <a-select v-model:value="formState.triggers.messageTypes" mode="multiple" placeholder="全部消息类型">
          <a-select-option v-for="type in ['TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'FILE', 'RICH_TEXT']" :key="type" :value="type">{{ type }}</a-select-option>
        </a-select>
      </label>

      <label>工作流回复方式
        <a-select v-model:value="formState.triggers.replyMode">
          <a-select-option value="ASYNC">异步回复原会话（推荐）</a-select-option>
          <a-select-option value="NONE">不自动回复</a-select-option>
        </a-select>
      </label>

      <div class="wf-connector-note">
        {{ accountScope === 'SPECIFIC'
          ? '仅所选账户收到的消息会触发此工作流；异步回复会返回该账户的原会话。'
          : '该消息连接器下任一已启用账户收到消息都会触发此工作流；异步回复会返回原账户和原会话。' }}
      </div>
    </div>

    <div v-else-if="enabled" class="wf-triggers-hint">
      {{ currentType === 'webhook' ? 'Webhook 的路径和密钥由发布接口生成。' : 'HTTP 触发后会把请求体作为开始节点输入。' }}
    </div>
  </div>
</template>

<style scoped>
.wf-triggers { display: flex; flex-direction: column; gap: 8px; }
.wf-triggers-toggle { display:flex; align-items:center; justify-content:space-between; padding:6px 10px; background:#f9fafb; border-radius:6px; font-size:12px; color:#4b5563; }
.wf-triggers-types { display:grid; grid-template-columns:repeat(2,1fr); gap:6px; }
.wf-triggers-type { display:flex; flex-direction:column; align-items:center; gap:4px; padding:10px 6px; background:#fff; border:1px solid #e5e7eb; border-radius:6px; cursor:pointer; color:#4b5563; font-size:11px; }
.wf-triggers-type:hover,.wf-triggers-type.is-active { border-color:var(--tc); color:var(--tc); }
.wf-triggers-type.is-active { background:color-mix(in srgb,var(--tc) 10%,transparent); }
.wf-triggers-type-icon { font-size:18px; color:var(--tc); }
.wf-schedule-form { display:grid; gap:9px; padding:10px; border:1px solid #fde68a; border-radius:7px; background:#fffbeb; }
.wf-schedule-form label { display:grid; gap:4px; font-size:11px; color:#4b5563; }
.wf-schedule-form :deep(.ant-select),.wf-schedule-form :deep(.ant-input-number) { width:100%; }
.wf-schedule-preview { padding:7px 9px; border-radius:5px; background:#fff; color:#92400e; font:11px ui-monospace,monospace; overflow-wrap:anywhere; }
.wf-schedule-note,.wf-triggers-hint { padding:6px 10px; font-size:11px; color:#92400e; background:#fef3c7; border-radius:4px; }
.wf-connector-form { display:grid; gap:9px; padding:10px; border:1px solid #fbcfe8; border-radius:7px; background:#fdf2f8; }
.wf-connector-form label { display:grid; gap:4px; font-size:11px; color:#4b5563; }
.wf-connector-form :deep(.ant-select) { width:100%; }
.wf-connector-status { color:#6b7280; font-size:11px; }
.wf-connector-error,.wf-connector-warning { padding:7px 9px; border-radius:5px; background:#fff1f2; color:#be123c; font-size:11px; }
.wf-connector-note { padding:7px 9px; border-radius:5px; background:#fff; color:#9d174d; font-size:11px; line-height:1.5; }
</style>
