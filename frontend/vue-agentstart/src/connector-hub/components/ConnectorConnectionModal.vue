<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import { Modal } from '../../ui';
import type {
  ConnectorConnection,
  ConnectorDefinition,
  ConnectorInstallation,
} from '../types';
import JsonSchemaForm from '../../ui/components/JsonSchemaForm.vue';
import { parseJsonSchema } from '../types';
import { splitPluginConfiguration } from '../schema-ui';

const props = defineProps<{
  open: boolean;
  client: AgentStartClient;
  connector?: ConnectorDefinition;
  installation?: ConnectorInstallation;
  connection?: ConnectorConnection;
  tenantId?: string;
}>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const name = ref('');
const credentials = ref<Record<string, unknown>>({});
const config = ref<Record<string, unknown>>({});
const saving = ref(false);
const testing = ref(false);
const testMessage = ref('');
const error = ref('');
const isPlugin = computed(() => props.connector?.metadata?.kind === 'PLUGIN');
const pluginSchemas = computed(() => splitPluginConfiguration(parseJsonSchema(props.connector?.configurationSchema)));

watch(
  () => [props.open, props.connection?.id],
  () => {
    name.value =
      props.connection?.name ?? `${props.connector?.name ?? 'Connector'} 连接`;
    credentials.value = {};
    config.value = {};
    error.value = '';
    testMessage.value = '';
  },
  { immediate: true },
);

async function save() {
  if (!props.installation) return;
  saving.value = true;
  try {
    await props.client.connectors.saveConnection({
      id: props.connection?.id,
      tenantId: props.tenantId,
      installationId: props.installation.id,
      name: name.value,
      credentials: Object.keys(credentials.value).length
        ? credentials.value
        : undefined,
      config: Object.keys(config.value).length ? config.value : undefined,
    });
    emit('saved');
    emit('update:open', false);
  } catch (e: any) {
    error.value = e?.message ?? '保存失败';
  } finally {
    saving.value = false;
  }
}

async function testConnection() {
  if (!props.connection) return;
  testing.value = true;
  try {
    const result = await props.client.connectors.testConnection(
      props.connection.id,
      props.tenantId,
    );
    testMessage.value = result.message;
  } catch (e: any) {
    error.value = e?.message ?? '验证失败';
  } finally {
    testing.value = false;
  }
}

async function remove() {
  if (!props.connection || !confirm(`删除连接「${props.connection.name}」？`)) {
    return;
  }
  await props.client.connectors.deleteConnection(
    props.connection.id,
    props.tenantId,
  );
  emit('saved');
  emit('update:open', false);
}
</script>

<template>
  <Modal
    :open="open"
    class="ccm-modal"
    centered
    :width="620"
    :title="`配置连接 · ${connector?.name ?? ''}`"
    :mask-closable="!saving"
    :keyboard="!saving"
    :closable="!saving"
    @cancel="emit('update:open', false)"
  >
        <main class="ccm-main">
          <label class="ccm-field">
            <span>连接名称</span>
            <input v-model="name" class="ccm-input" />
          </label>

          <div class="ccm-group">
            <h4>凭证</h4>
            <p>已保存的密码不会回显；留空不会覆盖原凭证。</p>
            <JsonSchemaForm
              v-model="credentials"
              :schema="isPlugin ? pluginSchemas.credentials : connector?.configurationSchema"
              :allow-advanced="!isPlugin"
              :ui-schema="connector?.metadata?.configurationUiSchema"
            />
          </div>

          <div class="ccm-group">
            <h4>附加配置（可选）</h4>
            <JsonSchemaForm v-model="config" :schema="isPlugin ? pluginSchemas.configuration : undefined"
              :allow-advanced="!isPlugin" :ui-schema="connector?.metadata?.configurationUiSchema" />
          </div>

          <p v-if="testMessage" class="ccm-note is-success">
            {{ testMessage }}
          </p>
          <p v-if="error" class="ccm-note is-danger">{{ error }}</p>
        </main>

        <template #footer>
        <footer class="ccm-footer">
          <button
            v-if="connection"
            class="ccm-btn ccm-btn-danger"
            @click="remove"
          >
            删除
          </button>
          <span class="ccm-footer-spacer" />
          <button
            v-if="connection"
            class="ccm-btn"
            :disabled="testing"
            @click="testConnection"
          >
            {{ testing ? '验证中…' : '验证已保存配置' }}
          </button>
          <button class="ccm-btn" @click="emit('update:open', false)">
            取消
          </button>
          <button
            class="ccm-btn ccm-btn-primary"
            :disabled="saving || !name"
            @click="save"
          >
            {{ saving ? '保存中…' : '保存连接' }}
          </button>
        </footer>
        </template>
  </Modal>
</template>

<style scoped>
/* -------- 遮罩与弹窗容器（对齐 ProviderCredentialModal） -------- */
.ccm-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}
.ccm-modal { max-height: 88vh; }
:global(.dark) .ccm-modal {
  background: #1f1f1f;
}

/* 进出场动画 */
.ccm-enter-active,
.ccm-leave-active {
  transition: opacity 0.18s ease;
}
.ccm-enter-active .ccm-modal,
.ccm-leave-active .ccm-modal {
  transition: transform 0.18s ease;
}
.ccm-enter-from,
.ccm-leave-to {
  opacity: 0;
}
.ccm-enter-from .ccm-modal,
.ccm-leave-to .ccm-modal {
  transform: translateY(12px) scale(0.98);
}

/* -------- 头部 / 底部 -------- */
.ccm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid #e5e7eb;
}
.ccm-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
.ccm-close {
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
.ccm-close:hover {
  background: #f3f4f6;
  color: #111827;
}
.ccm-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #e5e7eb;
}
.ccm-footer-spacer {
  flex: 1;
}
:global(.dark) .ccm-header,
:global(.dark) .ccm-footer {
  border-color: #2d2d2d;
}
:global(.dark) .ccm-title {
  color: #f3f4f6;
}
:global(.dark) .ccm-close:hover {
  background: #2d2d2d;
  color: #f3f4f6;
}

/* -------- 主体 -------- */
.ccm-main {
  flex: 1;
  overflow: auto;
  padding: 18px 20px;
  display: grid;
  gap: 18px;
}
.ccm-field {
  display: grid;
  gap: 7px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.ccm-input {
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.ccm-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.ccm-group {
  display: grid;
  gap: 8px;
}
.ccm-group h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.ccm-group p {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}
:global(.dark) .ccm-field {
  color: #d1d5db;
}
:global(.dark) .ccm-input {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .ccm-group h4 {
  color: #d1d5db;
}
:global(.dark) .ccm-group p {
  color: #9ca3af;
}

/* -------- 提示（成功 / 错误） -------- */
.ccm-note {
  margin: 0;
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 8px;
}
.ccm-note.is-success {
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}
.ccm-note.is-danger {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
:global(.dark) .ccm-note.is-success {
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
  border-color: rgba(16, 185, 129, 0.35);
}
:global(.dark) .ccm-note.is-danger {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.15);
  border-color: rgba(220, 38, 38, 0.35);
}

/* -------- 按钮 -------- */
.ccm-btn {
  padding: 7px 16px;
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
.ccm-btn:hover {
  background: #f3f4f6;
}
.ccm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ccm-btn-primary {
  color: #fff;
  background: #6366f1;
  border-color: #6366f1;
}
.ccm-btn-primary:hover {
  background: #4f46e5;
}
.ccm-btn-danger {
  color: #dc2626;
  border-color: #fecaca;
}
.ccm-btn-danger:hover {
  background: #fef2f2;
}
:global(.dark) .ccm-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .ccm-btn:hover {
  background: #3d3d3d;
}
:global(.dark) .ccm-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
}
:global(.dark) .ccm-btn-primary:hover {
  background: #818cf8;
}
:global(.dark) .ccm-btn-danger {
  color: #f87171;
  border-color: rgba(220, 38, 38, 0.4);
}
:global(.dark) .ccm-btn-danger:hover {
  background: rgba(220, 38, 38, 0.15);
}
</style>
