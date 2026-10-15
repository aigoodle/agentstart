<script setup lang="ts">
import { message } from '../../ui/message';
import { confirmAction } from '../../ui/components/modalService';
import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

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
const isPlugin = computed(() => props.connector?.metadata?.kind === 'PLUGIN');
const pluginSchemas = computed(() => splitPluginConfiguration(parseJsonSchema(props.connector?.configurationSchema)));

watch(
  () => [props.open, props.connection?.id],
  () => {
    name.value =
      props.connection?.name ?? `${props.connector?.name ?? 'Connector'} 连接`;
    credentials.value = {};
    config.value = {};
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
    message.error(e?.message ?? '保存失败');
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
    message[result.success ? 'success' : 'error'](result.message || (result.success ? '连接验证通过' : '连接验证失败'));
  } catch (e: any) {
    message.error(e?.message ?? '验证失败');
  } finally {
    testing.value = false;
  }
}

async function remove() {
  const connection = props.connection;
  if (!connection || !await confirmAction(`删除连接「${connection.name}」？`)) {
    return;
  }
  await props.client.connectors.deleteConnection(
    connection.id,
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
            <AsShadcnInput v-model="name" class="ccm-input" />
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
        </main>

        <template #footer>
          <AsShadcnButton variant="ghost"
            v-if="connection"
            class="ccm-btn ccm-btn-danger as-modal__footer-leading"
            @click="remove"
          >
            删除
          </AsShadcnButton>
          <AsShadcnButton variant="ghost"
            v-if="connection"
            class="ccm-btn"
            :disabled="testing"
            @click="testConnection"
          >
            {{ testing ? '验证中…' : '验证已保存配置' }}
          </AsShadcnButton>
          <AsShadcnButton variant="ghost" class="ccm-btn" @click="emit('update:open', false)">
            取消
          </AsShadcnButton>
          <AsShadcnButton variant="ghost"
            class="ccm-btn ccm-btn-primary"
            :disabled="saving || !name"
            @click="save"
          >
            {{ saving ? '保存中…' : '保存连接' }}
          </AsShadcnButton>
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
.dark .ccm-modal {
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
  border-bottom: 1px solid var(--border);
}
.ccm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--foreground);
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
  color: var(--muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.ccm-close:hover {
  background: var(--muted);
  color: var(--foreground);
}
.dark .ccm-header,
.dark .ccm-title {
  color: #f3f4f6;
}
.dark .ccm-close:hover {
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
  color: var(--foreground);
}
.ccm-input {
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
  color: var(--foreground);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.ccm-input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}
.ccm-group {
  display: grid;
  gap: 8px;
}
.ccm-group h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--foreground);
}
.ccm-group p {
  margin: 0;
  font-size: 12px;
  color: var(--muted-foreground);
}
.dark .ccm-field {
  color: var(--muted-foreground);
}
.dark .ccm-input {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.dark .ccm-group h4 {
  color: var(--muted-foreground);
}
.dark .ccm-group p {
  color: var(--muted-foreground);
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
.dark .ccm-note.is-success {
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
  border-color: rgba(16, 185, 129, 0.35);
}
.dark .ccm-note.is-danger {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.15);
  border-color: rgba(220, 38, 38, 0.35);
}

/* -------- 按钮 -------- */
.ccm-btn {
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--foreground);
  background: var(--background);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.ccm-btn:hover {
  background: var(--muted);
}
.ccm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ccm-btn-primary {
  color: var(--primary-foreground);
  background: var(--primary);
  border-color: var(--primary);
}
.ccm-btn-primary:hover {
  background: var(--primary);
}
.ccm-btn-danger {
  color: #dc2626;
  border-color: #fecaca;
}
.ccm-btn-danger:hover {
  background: #fef2f2;
}
.dark .ccm-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.dark .ccm-btn:hover {
  background: #3d3d3d;
}
.dark .ccm-btn-primary {
  background: var(--primary);
  border-color: var(--primary);
}
.dark .ccm-btn-primary:hover {
  background: var(--primary);
}
.dark .ccm-btn-danger {
  color: #f87171;
  border-color: rgba(220, 38, 38, 0.4);
}
.dark .ccm-btn-danger:hover {
  background: rgba(220, 38, 38, 0.15);
}
</style>
