<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import { Button, Drawer, Modal } from '../../ui';
import type { ChannelConnection, ChannelDefinition } from '../types';
import ChannelIcon from './ChannelIcon.vue';
import JsonSchemaForm from '../../ui/components/JsonSchemaForm.vue';
import NativeChannelSetupGuide from './NativeChannelSetupGuide.vue';
import { accountModelOf, connectionOwnerType } from '../channel-manifest';

const props = defineProps<{
    open: boolean;
    channel?: ChannelDefinition;
    /** 当前通道下已配置的账号（父级已按通道过滤） */
    connections: ChannelConnection[];
    client: AgentStartClient;
    tenantId?: string;
  }>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const form = ref({
  ownerId: '',
  name: '',
  enabled: true,
});
const credentials = ref<Record<string, unknown>>({});
const channelConfig = ref<Record<string, unknown>>({});
const configuredSecretFields = ref<string[]>([]);
const editingId = ref<string>();
const formOpen = ref(false);
const saving = ref(false);
const installing = ref(false);
const error = ref('');
const notice = ref('');
const platformKey = computed(() =>
  String(props.channel?.metadata?.platformId ?? props.channel?.channelId ?? '').toLowerCase(),
);
const accountModel = computed(() => accountModelOf(props.channel));
const isTenantAccount = computed(() => accountModel.value.scope === 'TENANT');
const canCreate = computed(() =>
  accountModel.value.instancePolicy !== 'SINGLE' || props.connections.length === 0,
);

function linkOf(field: 'homepageUrl' | 'sourceUrl') {
  const value = props.channel?.metadata?.[field];
  return typeof value === 'string' && /^https?:\/\//.test(value) ? value : '';
}

function blankForm() {
  form.value = { ownerId: '', name: '', enabled: true };
  credentials.value = {};
  channelConfig.value = {};
  configuredSecretFields.value = [];
  editingId.value = undefined;
}

function openCreate() {
  blankForm();
  error.value = '';
  notice.value = '';
  formOpen.value = true;
}

function closeForm() {
  formOpen.value = false;
  blankForm();
}

async function fillFrom(row: ChannelConnection) {
  formOpen.value = true;
  editingId.value = row.id;
  form.value = {
    ownerId: row.ownerId,
    name: row.name,
    enabled: row.desiredStatus === 'ACTIVE',
  };
  credentials.value = {};
  channelConfig.value = {};
  configuredSecretFields.value = [];
  error.value = '';
  try {
    const editable = await props.client.channels.getChannelConnectionConfiguration(
      row.id,
      props.tenantId,
    );
    credentials.value = { ...editable.credentials };
    channelConfig.value = { ...editable.config };
    configuredSecretFields.value = [...editable.configuredSecretFields];
  } catch (e: any) {
    error.value = e?.message ?? '读取账号配置失败';
  }
}

// 打开抽屉 / 切换通道时重置表单
watch(
  () => [props.open, props.channel],
  ([open]) => {
    if (!open) return;
    error.value = '';
    notice.value = '';
    blankForm();
    formOpen.value = false;
  },
  { immediate: true },
);

async function save() {
  const channel = props.channel;
  if (!channel) return;
  saving.value = true;
  error.value = '';
  try {
    await props.client.channels.saveChannelConnection({
      id: editingId.value,
      tenantId: props.tenantId,
      ownerType: connectionOwnerType(accountModel.value),
      ownerId: accountModel.value.ownerRequired ? form.value.ownerId : undefined,
      provider: channel.provider,
      channelId: channel.channelId,
      name: form.value.name,
      credentials: Object.keys(credentials.value).length ? credentials.value : undefined,
      config: Object.keys(channelConfig.value).length ? channelConfig.value : undefined,
      enabled: form.value.enabled,
    });
    emit('saved');
    formOpen.value = false;
  } catch (e: any) {
    error.value = e?.message ?? '保存失败';
  } finally {
    saving.value = false;
  }
}

async function test(row: ChannelConnection) {
  error.value = '';
  try {
    await props.client.channels.testChannelConnection(row.id, props.tenantId);
    notice.value = `账号「${row.name}」连通性测试通过`;
    emit('saved');
  } catch (e: any) {
    error.value = e?.message ?? '测试失败';
  }
}

async function remove(row: ChannelConnection) {
  if (!window.confirm(`删除 ${row.name}？`)) return;
  await props.client.channels.deleteChannelConnection(row.id, props.tenantId);
  emit('saved');
}

async function install() {
  const channel = props.channel;
  if (!channel) return;
  error.value = `请在后端引入 ${channel.channelId} 原生渠道(Channel)Starter 后重启服务`;
}

const callbackIssueOf = (row: ChannelConnection) =>
  row.runtimeMetadata?.callbackWorkerRunning === false;
const callbackBacklogOf = (row: ChannelConnection) => {
  const value = Number(row.runtimeMetadata?.pendingInboundCallbacks ?? 0);
  return Number.isFinite(value) && value > 0 ? value : 0;
};
const rowNeedsCallback = (row: ChannelConnection) =>
  ['webhook'].includes(props.channel?.channelId ?? '')
  || String(row.runtimeMetadata?.transport ?? '').toLowerCase() === 'webhook';
const webhookReady = (row: ChannelConnection) =>
  rowNeedsCallback(row)
  && row.desiredStatus === 'ACTIVE'
  && ['RUNNING', 'ONLINE'].includes(row.runtimeStatus)
  && !callbackIssueOf(row);
const operationalStatusOf = (row: ChannelConnection) =>
  callbackIssueOf(row)
    ? `${row.runtimeStatus} · 入站回调异常`
    : row.lastError
      ? `${row.runtimeStatus} · ${row.lastError}`
      : webhookReady(row)
        ? '已就绪 · 等待平台回调'
        : row.runtimeStatus;
const accountOnline = (row: ChannelConnection) =>
  (row.runtimeStatus === 'ONLINE' || webhookReady(row)) && !callbackIssueOf(row);
const callbackPathOf = (row: ChannelConnection) =>
  `${props.client.rootUrl}/channel-events/native/${props.channel?.channelId}/${row.runtimeAccountId || row.id}`;

async function copyCallbackPath(row: ChannelConnection) {
  const path = callbackPathOf(row);
  try {
    await navigator.clipboard.writeText(`${window.location.origin}${path}`);
    notice.value = `已复制账号「${row.name}」的完整回调地址`;
  } catch {
    notice.value = `回调路径：${path}`;
  }
}
</script>

<template>
  <Drawer
    :open="open"
    class="cad-drawer"
    placement="left"
    :width="600"
    :body-style="{ padding: 0 }"
    @update:open="emit('update:open', $event)"
  >
        <template #title>
          <div class="cad-header-info">
            <ChannelIcon
              class="cad-icon"
              :platform-id="platformKey"
              :channel-id="channel?.channelId"
              :metadata="channel?.metadata"
              :name="channel?.name"
              :size="40"
              :radius="10"
            />
            <div class="cad-header-text">
              <b>{{ channel?.name }} 账号接入</b>
              <small>{{ channel?.provider }} · {{ channel?.version || '-' }}</small>
            </div>
          </div>
        </template>

        <main class="cad-main">
          <div v-if="error" class="cad-alert">{{ error }}</div>
          <div v-else-if="notice" class="cad-note">{{ notice }}</div>

          <!-- 未安装：引导安装 -->
          <template v-if="!channel?.installed">
            <p class="cad-desc">{{ channel?.description }}</p>
            <div class="cad-install">
              <p>该原生通道 Starter 尚未启用，引入后即可在此配置账号并接收消息。</p>
              <button
                class="cad-btn cad-btn-primary"
                :disabled="installing"
                @click="install"
              >
                {{ installing ? '检查中…' : '查看启用方式' }}
              </button>
              <div class="cad-links">
                <a
                  v-if="linkOf('homepageUrl')"
                  :href="linkOf('homepageUrl')"
                  target="_blank"
                  rel="noopener"
                >
                  官方详情 ↗
                </a>
                <a
                  v-if="linkOf('sourceUrl')"
                  :href="linkOf('sourceUrl')"
                  target="_blank"
                  rel="noopener"
                >
                  源码 ↗
                </a>
              </div>
            </div>
          </template>

          <!-- 已安装：账号列表 + 表单 -->
          <template v-else>
            <section class="cad-section">
              <div class="cad-section-head">
                <h3>已配置账号</h3>
                <span class="cad-count">{{ connections.length }}</span>
                <Button v-if="canCreate" type="primary" class="cad-add" @click="openCreate">+ 添加账号</Button>
              </div>
              <div v-if="!connections.length" class="cad-empty">尚未配置账号</div>
              <div v-for="row in connections" :key="row.id" class="cad-row">
                <div class="cad-row-main">
                  <b>{{ row.name }}</b>
                  <small>
                    {{ row.ownerId || (row.ownerType === 'TENANT' ? '租户级账号' : '未指定所有者') }} · {{ row.runtimeAccountId || '无运行账号' }}
                  </small>
                </div>
                <span
                  :class="['cad-status', accountOnline(row) ? 'is-online' : 'is-off']"
                  :title="String(row.runtimeMetadata?.callbackWorkerError ?? row.lastError ?? '')"
                >
                  <i :class="['cad-dot', accountOnline(row) ? 'is-on' : 'is-off']"></i>
                  {{ operationalStatusOf(row) }}
                </span>
                <span v-if="callbackBacklogOf(row)" class="cad-tag is-warn">
                  待回调 {{ callbackBacklogOf(row) }}
                </span>
                <span class="cad-tag">{{ row.ownerType === 'TENANT' ? '企业账号' : '个人账号' }}</span>
                <span :class="['cad-tag', { 'is-warn': !row.credentialsConfigured }]">
                  {{ row.credentialsConfigured ? '凭证已配置' : '缺少凭证' }}
                </span>
                <code v-if="rowNeedsCallback(row)" class="cad-callback">
                  {{ callbackPathOf(row) }}
                </code>
                <div class="cad-row-actions">
                  <button v-if="rowNeedsCallback(row)" class="cad-link" @click="copyCallbackPath(row)">复制回调地址</button>
                  <button class="cad-link" @click="fillFrom(row)">编辑</button>
                  <button class="cad-link" @click="test(row)">测试</button>
                  <button class="cad-link is-danger" @click="remove(row)">删除</button>
                </div>
              </div>
            </section>

            <Modal
              :open="formOpen"
              class="cad-form-dialog"
              centered
              :width="560"
              :mask-closable="!saving"
              :keyboard="!saving"
              :closable="!saving"
              @cancel="closeForm"
            >
                  <template #title>
                    <div class="as-modal-heading">
                      <div class="as-modal-title">{{ editingId ? '编辑账号' : '添加账号' }}</div>
                      <div class="as-modal-subtitle">{{ channel?.name }} · {{ channel?.provider }}</div>
                    </div>
                  </template>
                  <div class="cad-form-body">
                    <div v-if="error" class="cad-alert">{{ error }}</div>
                    <NativeChannelSetupGuide
                      :channel-id="channel?.channelId"
                      :account-id="editingId"
                    />
                    <div class="cad-fields">
                <label v-if="accountModel.ownerRequired">
                  {{ accountModel.ownerLabel }}
                  <input v-model.trim="form.ownerId" required :placeholder="accountModel.ownerPlaceholder" />
                </label>
                <label>
                  连接名称
                  <input
                    v-model.trim="form.name"
                    required
                    :placeholder="`例如 ${channel?.name} 工作账号`"
                  />
                </label>
                    </div>

                    <div v-if="isTenantAccount && accountModel.identityBridge?.enabled" class="cad-note">
                      <b>运行时身份关联</b><br />
                      {{ accountModel.identityBridge.description || `该企业账号接入后，可将${accountModel.identityBridge.externalIdentityLabel || '渠道用户'}关联到${accountModel.identityBridge.enterpriseIdentityLabel || '企业员工'}。` }}
                    </div>

                    <fieldset>
                <legend>账号凭证</legend>
                <p>凭证会加密保存且不会回显，编辑时留空表示不修改。</p>
                <JsonSchemaForm
                  v-model="credentials"
                  :schema="channel?.credentialSchema"
                  :ui-schema="channel?.uiSchema"
                  :configured-secret-fields="configuredSecretFields"
                />
                    </fieldset>

                    <fieldset>
                <legend>通道配置</legend>
                <JsonSchemaForm
                  v-model="channelConfig"
                  :schema="channel?.configurationSchema"
                  :ui-schema="channel?.uiSchema"
                />
                    </fieldset>
                  </div>
                  <template #footer>
                  <div class="cad-footer">
                    <label class="cad-enable"><input v-model="form.enabled" type="checkbox" />启用消息接收</label>
                    <div class="cad-footer-actions">
                      <Button @click="closeForm">取消</Button>
                      <Button
                        type="primary"
                        :loading="saving"
                        :disabled="(accountModel.ownerRequired && !form.ownerId) || !form.name"
                        @click="save"
                      >
                        {{ editingId ? '保存修改' : '创建连接' }}
                      </Button>
                    </div>
                  </div>
                  </template>
            </Modal>
          </template>
        </main>
  </Drawer>
</template>

<style scoped>
/* -------- 头部 -------- */
.cad-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.cad-icon {
  flex-shrink: 0;
}
.cad-header-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.cad-header-text b {
  font-size: 15px;
  color: #111827;
}
.cad-header-text small {
  font-size: 12px;
  color: #9ca3af;
}
:global(.dark) .cad-header-text b {
  color: #f3f4f6;
}

/* -------- 主体 -------- */
.cad-main {
  padding: 20px;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 18px;
}
.cad-desc {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}
:global(.dark) .cad-desc {
  color: #9ca3af;
}

/* 提示条 */
.cad-alert {
  padding: 10px 14px;
  font-size: 13px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
}
.cad-note {
  padding: 10px 14px;
  font-size: 13px;
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
}
:global(.dark) .cad-alert {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.15);
  border-color: rgba(220, 38, 38, 0.35);
}
:global(.dark) .cad-note {
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
  border-color: rgba(16, 185, 129, 0.35);
}

/* 未安装引导 */
.cad-install {
  display: grid;
  gap: 12px;
  justify-items: start;
  padding: 16px;
  border: 1px dashed #d1d5db;
  border-radius: 10px;
  font-size: 13px;
  color: #6b7280;
}
.cad-install p {
  margin: 0;
}
.cad-hint {
  color: #6b7280;
}
.cad-links {
  display: flex;
  gap: 14px;
}
.cad-links a {
  font-size: 13px;
  color: #2563eb;
  text-decoration: none;
}
.cad-links a:hover {
  text-decoration: underline;
}
:global(.dark) .cad-install {
  border-color: #3d3d3d;
  color: #9ca3af;
}
:global(.dark) .cad-hint {
  color: #9ca3af;
}
:global(.dark) .cad-links a {
  color: #818cf8;
}

/* -------- 区块 -------- */
.cad-section {
  display: grid;
  gap: 10px;
}
.cad-wide { grid-column: 1 / -1; }
.cad-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cad-add { margin-left: auto; }
.cad-section h3,
.cad-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.cad-count {
  padding: 1px 8px;
  font-size: 11px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 10px;
}
:global(.dark) .cad-section h3,
:global(.dark) .cad-section-title {
  color: #d1d5db;
}
:global(.dark) .cad-count {
  background: #2d2d2d;
  color: #9ca3af;
}

/* 账号行 */
.cad-empty {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
}
.cad-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.cad-row-main {
  flex: 1;
  display: grid;
  gap: 2px;
  min-width: 0;
}
.cad-row-main b {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}
.cad-row-main small {
  font-size: 12px;
  color: #9ca3af;
}
.cad-callback {
  flex-basis: 100%;
  padding: 5px 8px;
  overflow-wrap: anywhere;
  font-size: 11px;
  color: #475569;
  background: #f8fafc;
  border-radius: 5px;
}
.cad-row-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}
.cad-link {
  border: 0;
  background: none;
  padding: 0;
  font-size: 12px;
  color: #4f46e5;
  cursor: pointer;
}
.cad-link:hover {
  text-decoration: underline;
}
.cad-link.is-danger {
  color: #dc2626;
}
:global(.dark) .cad-empty {
  border-color: #3d3d3d;
}
:global(.dark) .cad-row {
  background: #2d2d2d;
  border-color: #3d3d3d;
}
:global(.dark) .cad-row-main b {
  color: #f3f4f6;
}
:global(.dark) .cad-callback {
  color: #cbd5e1;
  background: #242424;
}
:global(.dark) .cad-link {
  color: #818cf8;
}
:global(.dark) .cad-link.is-danger {
  color: #f87171;
}

/* 账号运行状态 */
.cad-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #92400e;
  white-space: nowrap;
}
.cad-status.is-online {
  color: #047857;
}
.cad-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cad-dot.is-on {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
}
.cad-dot.is-off {
  background: #d1d5db;
}

/* 账号标签：路由 / 凭证 / 回调积压 */
.cad-tag {
  padding: 3px 9px;
  font-size: 11px;
  color: #374151;
  background: #f3f4f6;
  border-radius: 10px;
  white-space: nowrap;
}
.cad-tag.is-warn {
  color: #b45309;
  background: #fffbeb;
}
:global(.dark) .cad-status {
  color: #fbbf24;
}
:global(.dark) .cad-status.is-online {
  color: #34d399;
}
:global(.dark) .cad-dot.is-off {
  background: #4b5563;
}
:global(.dark) .cad-tag {
  background: #2d2d2d;
  color: #d1d5db;
}
:global(.dark) .cad-tag.is-warn {
  color: #fbbf24;
  background: rgba(180, 83, 9, 0.15);
}

/* -------- 表单 -------- */
.cad-form-dialog {
  width: min(720px, 96vw);
  max-height: min(820px, 92vh);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.25);
}
.cad-form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid #e5e7eb;
}
.cad-form-body { display: grid; gap: 18px; padding: 20px; overflow: auto; }
:global(.dark) .cad-form-dialog { background: #1f1f1f; }
:global(.dark) .cad-form-header { border-color: #2d2d2d; }
.cad-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.cad-fields label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #374151;
}
.cad-fields input,
.cad-fields select {
  box-sizing: border-box;
  width: 100%;
  padding: 9px 11px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  background: #fff;
  color: #111827;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.cad-fields input:focus,
.cad-fields select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
fieldset {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 9px;
}
legend {
  padding: 0 7px;
  font-weight: 600;
  font-size: 13px;
}
fieldset > p {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}
:global(.dark) .cad-fields label {
  color: #d1d5db;
}
:global(.dark) .cad-fields input,
:global(.dark) .cad-fields select {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) fieldset {
  border-color: #3d3d3d;
}
:global(.dark) fieldset > p {
  color: #9ca3af;
}
@media (max-width: 640px) {
  .cad-fields {
    grid-template-columns: 1fr;
  }
}

/* -------- 底部 -------- */
.cad-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #e5e7eb;
}
.cad-enable {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #374151;
}
.cad-enable input {
  accent-color: #6366f1;
}
.cad-footer-actions {
  display: flex;
  gap: 8px;
}
:global(.dark) .cad-footer {
  border-top-color: #2d2d2d;
}
:global(.dark) .cad-enable {
  color: #d1d5db;
}

/* -------- 按钮 -------- */
.cad-btn {
  padding: 7px 14px;
  font-size: 13px;
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
.cad-btn:hover {
  background: #f3f4f6;
}
.cad-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cad-btn-primary {
  color: #fff;
  background: #6366f1;
  border-color: #6366f1;
}
.cad-btn-primary:hover {
  background: #4f46e5;
}
:global(.dark) .cad-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .cad-btn:hover {
  background: #3d3d3d;
}
:global(.dark) .cad-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
}
:global(.dark) .cad-btn-primary:hover {
  background: #818cf8;
}
</style>
