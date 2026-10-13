<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import { createAgentStartClient } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import JsonSchemaForm from '../../../ui/components/JsonSchemaForm.vue';
import type { ConnectorConnection, ConnectorDefinition, ConnectorInstallation, JsonSchema } from '../../../connector-hub/types';
import { parseJsonSchema } from '../../../connector-hub/types';
import { schemaOutputFields } from '../../../connector-hub/schema-ui';
import VarInsertField from '@/workflow/VarInsertField.vue';
import VarSelectField from '@/workflow/VarSelectField.vue';
import PromptEditor from '@/components/PromptEditor.vue';

defineProps<{ nodeId?: string }>();
const form: any = defineModel();
// Backward-compatible migration: configured legacy connectors remain plugin
// Actions; an empty/new connector opens directly in the message-send workflow.
form.value.connectorMode ??= form.value.connectorId ? 'CONNECTOR_ACTION' : 'CHANNEL_MESSAGE';
form.value.channelSource ??= 'REPLY_TRIGGER';
// Migrate the earlier ambiguous value without breaking saved workflows.
if (form.value.channelSource === 'TRIGGER') form.value.channelSource = 'REPLY_TRIGGER';
form.value.messageType ??= 'TEXT';
form.value.inputField ??= 'input';
form.value.inputText ??= '';
form.value.connectionSource ??= form.value.connectionId ? 'SELECTED' : 'DEFAULT';
form.value.outputKey = 'result';
const connectorOutputChildren = [
  { type: 'boolean', name: 'success', value: true, label: '是否执行成功' },
  { type: 'object', name: 'data', value: {}, label: '业务返回数据' },
  { type: 'array', name: 'content', value: [], label: '文本或资源内容' },
  { type: 'object', name: 'metadata', value: {}, label: '执行元数据' },
  { type: 'string', name: 'provider', value: '', label: '连接器提供方' },
  { type: 'string', name: 'connectorId', value: '', label: '连接器 ID' },
  { type: 'string', name: 'actionId', value: '', label: 'Action ID' },
  { type: 'string', name: 'connectionId', value: '', label: '连接账号 ID' },
  { type: 'string', name: 'messageId', value: '', label: '渠道消息 ID' },
  { type: 'string', name: 'channelId', value: '', label: '消息渠道 ID' },
  { type: 'string', name: 'targetId', value: '', label: '接收目标 ID' },
  { type: 'string', name: 'conversationId', value: '', label: '会话 ID' },
];
if (!Array.isArray(form.value.output)) form.value.output = [];
if (!form.value.output[0]) form.value.output[0] = { type: 'object', name: form.value.outputKey || 'result', value: {}, label: '连接器执行结果' };
form.value.output[0].children = connectorOutputChildren;
const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(global.headers),
});
const connectors = ref<ConnectorDefinition[]>([]);
const installations = ref<ConnectorInstallation[]>([]);
const connections = ref<ConnectorConnection[]>([]);
const loading = ref(false);
const error = ref('');

const connector = computed(() => connectors.value.find((item) =>
  item.key.provider === form.value.provider && item.key.connectorId === form.value.connectorId));
const action = computed(() => connector.value?.actions.find((item) => item.id === form.value.actionId));
const actionSchema = computed(() => parseJsonSchema(action.value?.inputSchema));
watch(() => action.value?.outputSchema, (schema) => {
  form.value.output[0].children = connectorOutputChildren.map(field => field.name === 'data'
    ? { ...field, type: parseJsonSchema(schema).type ?? 'object', children: schemaOutputFields(parseJsonSchema(schema)) }
    : field);
}, { immediate: true });
const hasActionFields = computed(() => Object.keys(actionSchema.value.properties ?? {}).length > 0);
const actionConnectors = computed(() => connectors.value.filter((item) =>
  !item.capabilities?.length || item.capabilities.includes('ACTION')));
const availableConnections = computed(() => {
  const installation = installations.value.find((item) =>
    item.provider === form.value.provider && item.connectorId === form.value.connectorId);
  return connections.value.filter((item) => item.installationId === installation?.id);
});

function defaultsOf(schema: JsonSchema) {
  return Object.fromEntries(Object.entries(schema.properties ?? {})
    .filter(([, field]) => field.default !== undefined)
    .map(([name, field]) => [name, field.default]));
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    [connectors.value, installations.value, connections.value] = await Promise.all([
      client.connectors.list(),
      client.connectors.listInstallations(),
      client.connectors.listConnections(),
    ]);
    form.value.connectorName = connector.value?.name ?? '';
    form.value.actionName = action.value?.name ?? '';
    form.value.connectionName = connections.value.find((item) => item.id === form.value.connectionId)?.name ?? '';
    if (action.value && (!form.value.inputs || Object.keys(form.value.inputs).length === 0)) {
      form.value.inputs = defaultsOf(actionSchema.value);
    }
  } catch (cause: any) {
    error.value = cause?.message ?? '加载连接器失败';
  } finally {
    loading.value = false;
  }
}

watch(() => form.value.connectorId, () => {
  if (!connector.value?.actions.some((item) => item.id === form.value.actionId)) {
    form.value.actionId = '';
    form.value.inputs = {};
  }
  form.value.connectorName = connector.value?.name ?? '';
});
watch(() => form.value.actionId, (next, previous) => {
  form.value.actionName = action.value?.name ?? '';
  if (next !== previous) form.value.inputs = defaultsOf(actionSchema.value);
});
watch(() => form.value.connectionId, () => {
  form.value.connectionName = connections.value.find((item) => item.id === form.value.connectionId)?.name ?? '';
});
onMounted(load);
</script>

<template>
  <div class="cnc">
    <div v-if="loading">加载连接器…</div>
    <div v-if="error" class="error">{{ error }}</div>
    <section class="connector-kind-section">
      <h4>连接器类型</h4>
      <div class="connector-kind-grid">
        <button type="button" class="connector-kind-card" :class="{ active: form.connectorMode === 'CHANNEL_MESSAGE' }" @click="form.connectorMode = 'CHANNEL_MESSAGE'">
          <strong>消息连接器</strong><span>向 QQ Bot、企业微信等渠道发送消息</span>
        </button>
        <button type="button" class="connector-kind-card" :class="{ active: form.connectorMode === 'CONNECTOR_ACTION' }" @click="form.connectorMode = 'CONNECTOR_ACTION'">
          <strong>插件连接器</strong><span>调用邮件、日历或自定义插件 Action</span>
        </button>
      </div>
      <p>{{ form.connectorMode === 'CHANNEL_MESSAGE' ? '消息接收由开始节点触发器负责；当前节点只负责发送。' : '插件能力和消息账号相互独立，参数由插件 Action Schema 决定。' }}</p>
    </section>
    <template v-if="form.connectorMode !== 'CHANNEL_MESSAGE'">
    <label>连接器
      <select v-model="form.connectorId" @change="form.provider = ($event.target as HTMLSelectElement).selectedOptions[0]?.dataset.provider || ''">
        <option value="">请选择</option>
        <option v-for="item in actionConnectors" :key="`${item.key.provider}:${item.key.connectorId}`" :value="item.key.connectorId" :data-provider="item.key.provider">
          {{ item.name }}（{{ item.key.provider }}）
        </option>
      </select>
      <small v-if="connector?.metadata?.kind === 'PLUGIN'">{{ connector.metadata.runtime === 'JAVA' ? 'Java 插件' : '独立服务插件' }} · {{ connector.version }}</small>
    </label>
    <label>Action
      <select v-model="form.actionId" :disabled="!connector">
        <option value="">请选择</option>
        <option v-for="item in connector?.actions || []" :key="item.id" :value="item.id">{{ item.name }} · {{ item.riskLevel }}</option>
      </select>
    </label>
    <label>连接配置来源
      <select v-model="form.connectionSource">
        <option value="DEFAULT">使用运行时默认配置</option>
        <option value="SELECTED">选择已配置连接</option>
        <option value="DYNAMIC">从上游参数动态确定</option>
      </select>
    </label>
    <label v-if="form.connectionSource === 'SELECTED'">已配置连接
      <select v-model="form.connectionId">
        <option value="">请选择</option>
        <option v-for="item in availableConnections" :key="item.id" :value="item.id">{{ item.name }} · {{ item.status }}</option>
      </select>
      <small>节点只保存连接 ID；后台会携带当前租户和用户上下文，凭证保持加密存储且不会进入工作流参数。</small>
    </label>
    <label v-if="form.connectionSource === 'DYNAMIC'">连接 ID
      <VarSelectField v-model="form.connectionIdTemplate" :node-id="nodeId || ''" placeholder="选择一个上游 connectionId 参数" />
      <small>运行时解析连接 ID，适合同一个工作流由不同用户使用各自的连接账号。</small>
    </label>
    <section v-if="action">
      <h4>业务输入参数</h4>
      <p>字段由连接器提供方的 Action Schema 自动生成；字符串字段可填写 <code v-pre>{{#节点.字段#}}</code> 引用上游变量。</p>
      <JsonSchemaForm
        v-model="form.inputs"
        :schema="action.inputSchema"
        :ui-schema="action.metadata?.uiSchema"
        :allow-advanced="false"
        empty-text="此 Action 未提供可渲染的输入字段，请由连接器提供方补充 inputSchema.properties。"
        :node-id="nodeId || ''"
      />
      <template v-if="!hasActionFields">
        <label class="generic-input-label">输入内容
          <VarInsertField v-model="form.inputText" :node-id="nodeId || ''" placeholder="输入文本，或选择上游节点参数" />
          <small>适用于没有声明参数 Schema 的插件，使用方式与提示词一致。</small>
        </label>
        <label class="input-field-label">传递字段名
          <input v-model="form.inputField" placeholder="input">
          <small>默认传给插件的 <code>input</code> 字段；如果插件要求 query、content 或 message，请在这里填写。</small>
        </label>
        <small class="schema-diagnostic">Schema 来源：{{ connector?.key.provider }}/{{ connector?.key.connectorId }}/{{ action.id }}</small>
      </template>
    </section>
    <div v-if="action?.riskLevel === 'DESTRUCTIVE'" class="warning">高风险操作：运行前请确认权限和目标对象。</div>
    </template>

    <template v-else>
      <section class="message-route-section">
        <h4>发送方式</h4>
        <div class="message-route-list">
          <button type="button" class="message-route-card" :class="{ active: form.channelSource === 'REPLY_TRIGGER' }" @click="form.channelSource = 'REPLY_TRIGGER'">
            <span class="message-route-check">{{ form.channelSource === 'REPLY_TRIGGER' ? '✓' : '' }}</span>
            <span><strong>回复当前触发消息</strong><small>哪里来、回哪里；使用本次收到消息的机器人账号、会话和回复目标。</small></span>
          </button>
          <button type="button" class="message-route-card" :class="{ active: form.channelSource === 'DYNAMIC' }" @click="form.channelSource = 'DYNAMIC'">
            <span class="message-route-check">{{ form.channelSource === 'DYNAMIC' ? '✓' : '' }}</span>
            <span><strong>发送到其他消息渠道</strong><small>从上游参数取得连接器账号和接收目标，可发送到另一个机器人或渠道。</small></span>
          </button>
        </div>
        <p v-if="form.channelSource === 'REPLY_TRIGGER'">运行时读取 triggers.connectionId、message.replyTargetId 和 message.conversationId。</p>
        <p v-else>不绑定设计者的个人账号；后台会按本次运行用户和租户重新校验权限。</p>
      </section>
      <label v-if="form.channelSource === 'DYNAMIC'">连接器账号 ID
        <VarSelectField v-model="form.channelConnectionId" :node-id="nodeId || ''" placeholder="选择一个上游 connectionId 参数" />
        <small>工作流不保存个人账号；每次运行根据变量取得连接 ID，并由后台校验当前租户和用户是否有权使用。</small>
      </label>
      <label v-if="form.channelSource === 'DYNAMIC'">接收目标
        <VarInsertField v-model="form.targetId" :node-id="nodeId || ''" placeholder="用户/群 ID，可选择上游参数" />
      </label>
      <label v-if="form.channelSource === 'DYNAMIC'">会话 ID（可选）
        <VarInsertField v-model="form.channelConversationId" :node-id="nodeId || ''" placeholder="可选择上游 conversationId" />
      </label>
      <label>消息类型
        <a-select v-model:value="form.messageType" :options="['TEXT','IMAGE','AUDIO','VIDEO','FILE'].map(value => ({ value, label: value }))" />
      </label>
      <section class="send-content-section">
        <h4>发送内容</h4>
        <PromptEditor v-model="form.messageContent" title="发送内容" :node-id="nodeId || ''" placeholder="输入要发送的消息，使用 / 或点击变量选择上游参数" min-height="110px" max-height="260px" />
        <small>可以组合固定文字与“开始 → message → content”、LLM 或 Agent 输出。</small>
      </section>
      <div class="connector-tip">需要同时发 QQ、通知另一个机器人或发送 Email 时，请在流程中添加多个连接器节点；Email 使用“插件连接器”中的发送邮件 Action。</div>
    </template>
    <section class="output-contract fixed-output">
      <div class="output-title-row"><h4>固定输出</h4><code>result</code></div>
      <p v-if="form.connectorMode === 'CHANNEL_MESSAGE'">包含 success、messageId、connectionId、channelId、targetId、conversationId 和 metadata，可直接供下游节点选择。</p>
      <p v-else><code>result.success</code> 是否成功；<code>result.data</code> 业务数据；<code>result.content</code> 文本或资源；<code>result.metadata</code> 执行元数据。</p>
    </section>
  </div>
</template>

<style scoped>
.cnc { display:grid; gap:15px; }
.cnc label { display:grid; gap:6px; font-size:13px; }
.cnc label small { color:#6b7280; font-size:11px; line-height:1.5; }
.cnc select,.cnc input { border:1px solid #d1d5db; border-radius:6px; padding:8px; background: var(--wf-config-surface, #fff); }
.cnc section { display:grid; gap:8px; }
.cnc h4,.cnc p { margin:0; }
.cnc p { font-size:12px; color:#6b7280; line-height:1.5; }
.schema-diagnostic { color:#9ca3af; font:11px ui-monospace,monospace; }
.error { color:#dc2626; }
.warning { padding:9px; background:#fff7ed; color:#c2410c; border-radius:6px; }
.connector-kind-section { padding:12px; border:1px solid #e5e7eb; border-radius:10px; background: var(--wf-config-surface-hover, #f9fafb); }
.connector-kind-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.connector-kind-card { display:grid; gap:4px; min-height:76px; padding:11px; text-align:left; color:#475569; background: var(--wf-config-surface, #fff); border:1px solid #dbe2ea; border-radius:8px; cursor:pointer; }
.connector-kind-card strong { color:#1f2937; font-size:13px; }
.connector-kind-card span { font-size:11px; line-height:1.45; }
.connector-kind-card:hover { border-color:#a5b4fc; }
.connector-kind-card.active { border-color:#6366f1; background: color-mix(in srgb, var(--wf-accent, #6366f1) 14%, var(--wf-config-surface, #fff)); box-shadow:0 0 0 2px rgb(99 102 241 / 10%); }
.connector-kind-card.active strong { color:#4338ca; }
.message-route-section { display:grid; gap:9px; }
.message-route-list { display:grid; gap:8px; }
.message-route-card { display:grid; grid-template-columns:22px minmax(0,1fr); gap:8px; width:100%; padding:10px; text-align:left; color:#64748b; background: var(--wf-config-surface, #fff); border:1px solid #dbe2ea; border-radius:8px; cursor:pointer; }
.message-route-card:hover { border-color:#a5b4fc; }
.message-route-card.active { border-color:#6366f1; background: color-mix(in srgb, var(--wf-accent, #6366f1) 14%, var(--wf-config-surface, #fff)); }
.message-route-card strong,.message-route-card small { display:block; }
.message-route-card strong { margin-bottom:3px; color:#1f2937; font-size:12px; }
.message-route-card small { color:#64748b; font-size:11px; line-height:1.5; }
.message-route-check { display:grid; place-items:center; width:18px; height:18px; margin-top:1px; color:#fff; background:#e2e8f0; border-radius:50%; font-size:11px; }
.message-route-card.active .message-route-check { background:#6366f1; }
.connector-tip { padding:9px 10px; color:#475569; background: var(--wf-config-surface-hover, #f9fafb); border-radius:7px; font-size:11px; line-height:1.55; }
.generic-input-label,.input-field-label { margin-top:4px; }
.output-contract { padding:10px; border:1px solid #dbeafe; border-radius:8px; background:#eff6ff; }
.output-contract code { color:#1d4ed8; }
.fixed-output { margin-top:2px; }
.output-title-row { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.output-title-row code { padding:2px 7px; background:#dbeafe; border-radius:5px; font-size:11px; }
.send-content-section > small { color:#6b7280; font-size:11px; line-height:1.5; }
:global(.dark) .cnc select,:global(.dark) .cnc input { background:#27272a; color:#eee; border-color:#52525b; }
</style>
