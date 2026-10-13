<script setup lang="ts">
/**
 * ApiKeyManager — Dify-style "API 密钥" modal.
 *
 * Layout mirrors https://cloud.dify.ai/app/<id>/develop → 「API 密钥」按钮
 *   ┌ API 密钥 ─────────────────────── ✕ ┐
 *   │ 如果不想你的 API 被滥用，请保护好…    │
 *   │                                       │
 *   │ 密钥              创建时间   最后使用  │
 *   │ app...xxxxxxxxxxx 2026-06-14 …        │
 *   │ ...                                   │
 *   │                                       │
 *   │                        [创建密钥]     │
 *   └───────────────────────────────────────┘
 *
 * The panel is presentation-only: it emits {@code list} / {@code create} /
 * {@code delete} to the host (which turns them into REST calls). The host
 * passes results back via the {@code keys} prop.
 *
 * Key masking follows Dify: show the 3-char prefix + "..." + last 20 chars.
 * The full token is available only for the freshly-created row. Reloaded rows
 * are masked by the server and intentionally cannot be copied as credentials.
 */
import { computed, ref } from 'vue';
import { CopyOutlined, DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue';
import {
  Button,
  Empty,
  message,
  Modal,
  Popconfirm,
  Spin,
  Table,
  Tooltip,
} from '../../ui';

import type { StudioApiKey } from '../api/types';

interface Props {
  /** v-model:open. */
  open: boolean;
  /** Rows fetched by the host. */
  keys: StudioApiKey[];
  /** Loading state for the list — shows a Spin over the table. */
  loading?: boolean;
  /** Creating state — disables the "创建密钥" button. */
  creating?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  creating: false,
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'create'): void;
  (e: 'delete', row: StudioApiKey): void;
  (e: 'refresh'): void;
}>();

const columns = [
  { title: '密钥', dataIndex: 'token', key: 'token', width: '38%' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: '22%' },
  { title: '最后使用', dataIndex: 'lastUsedAt', key: 'lastUsedAt', width: '22%' },
  { title: '', key: 'actions', width: '18%', align: 'right' as const },
];

/**
 * Show the first 3 chars + "..." + last 20 so a user can still identify a
 * token at a glance without exposing enough entropy to be useful in a
 * screenshot. Matches Dify's rendering.
 */
function mask(token: string | undefined): string {
  if (!token) return '';
  if (token.length <= 24) return token;
  return `${token.slice(0, 3)}...${token.slice(-20)}`;
}

function canCopy(token: string | undefined): boolean {
  return !!token && !token.includes('...');
}

async function copy(text: string) {
  if (!text) return;
  try {
    if (navigator?.clipboard) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    message.success('已复制');
  } catch {
    message.error('复制失败');
  }
}

function onCreate() {
  emit('create');
}

function onDelete(row: StudioApiKey) {
  emit('delete', row);
}

function close() {
  emit('update:open', false);
}

const dataSource = computed(() => props.keys ?? []);
</script>

<template>
  <Modal
    :open="open"
    title="API 密钥"
    :footer="null"
    :width="720"
    :destroy-on-close="true"
    :mask-closable="false"
    @update:open="(v: boolean) => emit('update:open', v)"
    @cancel="close"
  >
    <div class="akm-tip">
      <ExclamationCircleOutlined class="akm-tip-icon" />
      <span>
        如果不想你的 API 被滥用，请保护好你的 API Key
        <span class="akm-tip-smile">:)</span>
        最佳实践是避免在前端代码中明文引用。
      </span>
    </div>

    <Spin :spinning="loading">
      <Table
        :columns="columns"
        :data-source="dataSource"
        :pagination="false"
        row-key="id"
        size="middle"
        class="akm-table"
      >
        <template #emptyText>
          <Empty description="尚未生成 API 密钥" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'token'">
            <div class="akm-token-cell">
              <Tooltip :title="canCopy(record.token) ? '新密钥，仅本次可复制' : '密钥已隐藏，无法再次查看'" placement="topLeft">
                <span class="akm-token-mono">{{ mask(record.token) }}</span>
              </Tooltip>
              <button
                v-if="canCopy(record.token)"
                type="button"
                class="akm-icon-btn"
                title="复制"
                @click="copy(record.token)"
              >
                <CopyOutlined />
              </button>
            </div>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            <span class="akm-dim">{{ record.createdAt || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'lastUsedAt'">
            <span class="akm-dim">{{ record.lastUsedAt || '从未使用' }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <Popconfirm
              title="删除后使用该密钥的调用会立刻失效，确定继续？"
              ok-text="删除"
              cancel-text="取消"
              placement="topRight"
              @confirm="onDelete(record as StudioApiKey)"
            >
              <Button type="link" danger size="small">
                <DeleteOutlined />
                <span>删除</span>
              </Button>
            </Popconfirm>
          </template>
        </template>
      </Table>
    </Spin>

    <div class="akm-footer">
      <Button type="primary" :loading="creating" @click="onCreate">
        创建密钥
      </Button>
    </div>
  </Modal>
</template>

<style scoped>
.akm-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  border-radius: 6px;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}
.akm-tip-icon {
  margin-top: 2px;
  color: #d97706;
}
.akm-tip-smile {
  color: #d97706;
  font-weight: 600;
}
.akm-token-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.akm-token-mono {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #0f172a;
}
.akm-icon-btn {
  border: none;
  background: transparent;
  padding: 2px 4px;
  cursor: pointer;
  color: #64748b;
  border-radius: 4px;
}
.akm-icon-btn:hover {
  background: #f1f5f9;
  color: #4338ca;
}
.akm-dim {
  color: #64748b;
  font-size: 12px;
}
.akm-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
