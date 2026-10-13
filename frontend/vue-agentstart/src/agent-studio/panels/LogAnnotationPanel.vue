<script setup lang="ts">
/**
 * LogAnnotationPanel — content for {@code AppDesignDrawer}'s "日志与标注" tab.
 *
 * Two sub-tabs:
 *   • 日志 — conversation list for this app; click a row to see the raw
 *     USER/ASSISTANT messages in a right-side drawer.
 *   • 标注 — CRUD table of QA pairs (Dify's "标注回复"); add / edit via modal,
 *     toggle enabled inline, delete with confirm.
 *
 * All backend I/O goes through {@link AppStudioApi} so a host doesn't have
 * to re-implement the panel — it just supplies the callback bag. Any API
 * method the host omits is treated as "not supported" and the corresponding
 * tab / action degrades to an empty-state placeholder.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  Button,
  Drawer,
  Empty,
  Form,
  FormItem,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tabs,
  TabPane,
  Tag,
  Textarea,
  message,
} from '../../ui';

import type {
  AppStudioApi,
  StudioAnnotation,
  StudioConversationSummary,
  StudioHistoryMessage,
} from '../api';

interface Props {
  app?: { id: string; name: string } | null;
  api: AppStudioApi;
}
const props = defineProps<Props>();

const activeTab = ref<'annotations' | 'logs'>('logs');

// ---- 日志 tab -----------------------------------------------------------
const conversations = ref<StudioConversationSummary[]>([]);
const logsLoading = ref(false);

// 后端返回 LocalDateTime.toString()（{@code 2025-11-28T14:32:15.123}），
// 直接给单元格会因为默认 wrap 换行成两行。压成 {@code YYYY-MM-DD HH:mm}
// 让常用列宽下 nowrap 也不溢出；秒/毫秒精度在"日志"表格里没实际价值。
function formatUpdatedAt(raw?: null | string): string {
  if (!raw) return '—';
  return raw.slice(0, 16).replace('T', ' ');
}

const logColumns = [
  { title: '会话 ID', dataIndex: 'conversationId', ellipsis: true, width: 200 },
  { title: '用户 ID', dataIndex: 'userId', key: 'userId', ellipsis: true, width: 180 },
  { title: '首条消息', dataIndex: 'firstMessage', ellipsis: true, minWidth: 220 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' as const },
];

async function reloadLogs() {
  if (!props.app?.id || !props.api.listConversations) return;
  logsLoading.value = true;
  try {
    conversations.value = await props.api.listConversations(props.app.id, 100);
  } catch {
    conversations.value = [];
  } finally {
    logsLoading.value = false;
  }
}

// Log drilldown drawer — click a conversation row to see its raw messages.
const logDrawerOpen = ref(false);
const logDrawerTitle = ref('');
const logMessages = ref<StudioHistoryMessage[]>([]);
const logHistoryLoading = ref(false);

async function openLogDetail(row: StudioConversationSummary) {
  if (!props.app?.id || !props.api.fetchHistory) return;
  logDrawerOpen.value = true;
  logDrawerTitle.value = `会话 · ${row.conversationId.slice(0, 8)}…`;
  logHistoryLoading.value = true;
  try {
    logMessages.value = await props.api.fetchHistory(
      props.app.id,
      row.conversationId,
      500,
    );
  } catch (e: any) {
    message.error(e?.message ?? '加载失败');
    logMessages.value = [];
  } finally {
    logHistoryLoading.value = false;
  }
}

// ---- 标注 tab -----------------------------------------------------------
const annotations = ref<StudioAnnotation[]>([]);
const annLoading = ref(false);

const annColumns = [
  { title: '提问', dataIndex: 'question', ellipsis: true },
  { title: '回答', dataIndex: 'content', ellipsis: true },
  { title: '命中次数', dataIndex: 'hitCount', width: 100 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 140 },
];

async function reloadAnnotations() {
  if (!props.app?.id || !props.api.listAnnotations) return;
  annLoading.value = true;
  try {
    annotations.value = await props.api.listAnnotations(props.app.id);
  } catch {
    annotations.value = [];
  } finally {
    annLoading.value = false;
  }
}

// Add/edit modal.
const annModalOpen = ref(false);
const annModalTitle = ref('');
const annForm = reactive({
  id: null as string | null,
  question: '',
  content: '',
  enabled: true,
});
const annSaving = ref(false);

function openAdd() {
  annForm.id = null;
  annForm.question = '';
  annForm.content = '';
  annForm.enabled = true;
  annModalTitle.value = '添加标注';
  annModalOpen.value = true;
}

function openEdit(row: StudioAnnotation) {
  annForm.id = row.id;
  annForm.question = row.question ?? '';
  annForm.content = row.content ?? '';
  annForm.enabled = row.enabled ?? true;
  annModalTitle.value = '编辑标注';
  annModalOpen.value = true;
}

async function submitAnnotation() {
  if (!props.app?.id) return;
  if (!annForm.question.trim() || !annForm.content.trim()) {
    message.warning('请填写提问和回答');
    return;
  }
  annSaving.value = true;
  try {
    const payload = {
      question: annForm.question.trim(),
      content: annForm.content.trim(),
      enabled: annForm.enabled,
    };
    if (annForm.id && props.api.updateAnnotation) {
      await props.api.updateAnnotation(props.app.id, annForm.id, payload);
      message.success('已更新');
    } else if (!annForm.id && props.api.createAnnotation) {
      await props.api.createAnnotation(props.app.id, payload);
      message.success('已添加');
    }
    annModalOpen.value = false;
    await reloadAnnotations();
  } catch (e: any) {
    message.error(e?.message ?? '保存失败');
  } finally {
    annSaving.value = false;
  }
}

async function toggleEnabled(row: StudioAnnotation, next: boolean) {
  if (!props.app?.id || !props.api.updateAnnotation) return;
  try {
    await props.api.updateAnnotation(props.app.id, row.id, {
      question: row.question ?? '',
      content: row.content ?? '',
      enabled: next,
    });
    row.enabled = next;
    message.success(next ? '已启用' : '已停用');
  } catch (e: any) {
    message.error(e?.message ?? '更新失败');
  }
}

async function removeAnnotation(row: StudioAnnotation) {
  if (!props.app?.id || !props.api.deleteAnnotation) return;
  try {
    await props.api.deleteAnnotation(props.app.id, row.id);
    message.success('已删除');
    await reloadAnnotations();
  } catch (e: any) {
    message.error(e?.message ?? '删除失败');
  }
}

// ---- Life cycle ---------------------------------------------------------
watch(
  () => props.app?.id,
  () => {
    if (activeTab.value === 'logs') reloadLogs();
    else reloadAnnotations();
  },
);
watch(activeTab, (v) => {
  if (v === 'logs' && conversations.value.length === 0) reloadLogs();
  if (v === 'annotations' && annotations.value.length === 0) reloadAnnotations();
});
onMounted(() => {
  reloadLogs();
});

const disabled = computed(() => !props.app?.id);
</script>

<template>
  <div class="log-annot">
    <Tabs v-model:active-key="activeTab">
      <TabPane key="logs" tab="日志">
        <div class="log-annot-hint">
          日志记录了应用的运行情况，包括用户的输入和 AI 的回复。
        </div>
        <div class="log-annot-toolbar">
          <span class="log-annot-count">共 {{ conversations.length }} 个会话</span>
          <Button size="small" :disabled="disabled" @click="reloadLogs">
            🔄 刷新
          </Button>
        </div>
        <Table
          :columns="logColumns"
          :data-source="conversations"
          :loading="logsLoading"
          :pagination="{ pageSize: 20 }"
          :scroll="{ x: 900 }"
          size="small"
          row-key="conversationId"
          :locale="{ emptyText: '暂无对话' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userId'">
              <span
                v-if="record.userId"
                class="log-annot-userid"
                :title="record.userId"
              >
                {{ record.userId }}
              </span>
              <span v-else class="log-annot-muted">—</span>
            </template>
            <template v-else-if="column.key === 'updatedAt'">
              <span class="log-annot-nowrap">{{ formatUpdatedAt(record.updatedAt) }}</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a
                class="log-annot-link"
                @click="openLogDetail(record as StudioConversationSummary)"
              >
                查看
              </a>
            </template>
          </template>
        </Table>
      </TabPane>

      <TabPane key="annotations" tab="标注" force-render>
        <div class="log-annot-hint">
          标注即用户手工维护的问答对。当用户提问命中一条已启用的标注时，应用会直接返回标注中的回答，跳过 LLM。
        </div>
        <div class="log-annot-toolbar">
          <span class="log-annot-count">共 {{ annotations.length }} 条</span>
          <Space>
            <Button size="small" :disabled="disabled" @click="reloadAnnotations">
              🔄 刷新
            </Button>
            <Button
              type="primary"
              size="small"
              :disabled="disabled"
              @click="openAdd"
            >
              + 添加标注
            </Button>
          </Space>
        </div>
        <Table
          :columns="annColumns"
          :data-source="annotations"
          :loading="annLoading"
          :pagination="{ pageSize: 20 }"
          size="small"
          row-key="id"
          :locale="{ emptyText: '暂无标注' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'enabled'">
              <Switch
                :checked="!!record.enabled"
                size="small"
                @change="(v: any) => toggleEnabled(record as StudioAnnotation, !!v)"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <Space>
                <a
                  class="log-annot-link"
                  @click="openEdit(record as StudioAnnotation)"
                >
                  编辑
                </a>
                <Popconfirm
                  title="删除后不可恢复"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="removeAnnotation(record as StudioAnnotation)"
                >
                  <a class="log-annot-link log-annot-link-danger">删除</a>
                </Popconfirm>
              </Space>
            </template>
          </template>
        </Table>
      </TabPane>
    </Tabs>

    <!-- Log drilldown drawer -->
    <Drawer
      v-model:open="logDrawerOpen"
      :title="logDrawerTitle"
      width="640"
      placement="right"
    >
      <div v-if="logHistoryLoading">加载中…</div>
      <Empty
        v-else-if="logMessages.length === 0"
        description="该会话没有消息"
      />
      <div v-else class="log-annot-messages">
        <div
          v-for="(m, i) in logMessages"
          :key="i"
          class="log-annot-msg"
          :class="`log-annot-msg-${m.role.toLowerCase()}`"
        >
          <Tag
            :color="m.role === 'USER' ? 'blue' : m.role === 'SYSTEM' ? 'default' : 'green'"
          >
            {{ m.role }}
          </Tag>
          <div class="log-annot-msg-body">{{ m.content }}</div>
        </div>
      </div>
    </Drawer>

    <!-- Add / edit annotation modal -->
    <Modal
      v-model:open="annModalOpen"
      :mask-closable="false"
      :title="annModalTitle"
      :confirm-loading="annSaving"
      ok-text="保存"
      cancel-text="取消"
      width="640px"
      @ok="submitAnnotation"
    >
      <Form :model="annForm" layout="vertical">
        <FormItem label="提问" required>
          <Textarea
            v-model:value="annForm.question"
            :rows="2"
            placeholder="用户可能输入的问题"
          />
        </FormItem>
        <FormItem label="回答" required>
          <Textarea
            v-model:value="annForm.content"
            :rows="4"
            placeholder="命中该问题时返回的回答"
          />
        </FormItem>
        <FormItem label="启用">
          <Switch v-model:checked="annForm.enabled" />
          <span class="log-annot-inline-hint">
            {{ annForm.enabled ? '已启用 · 命中时立即返回' : '未启用 · 保留但不参与匹配' }}
          </span>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
.log-annot {
  padding: 12px 20px 20px;
  background: #fff;
}
.log-annot-hint {
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border-left: 3px solid #6366f1;
  border-radius: 4px;
}
.log-annot-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.log-annot-count {
  font-size: 12px;
  color: #94a3b8;
}
.log-annot-link {
  color: #4338ca;
  cursor: pointer;
}
.log-annot-link:hover {
  text-decoration: underline;
}
.log-annot-link-danger {
  color: #dc2626;
}
.log-annot-nowrap {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: #475569;
  font-size: 12px;
}
.log-annot-userid {
  font-family: 'JetBrains Mono', 'SFMono-Regular', ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #334155;
}
.log-annot-muted {
  color: #cbd5e1;
}
.log-annot-messages {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.log-annot-msg {
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.log-annot-msg-user {
  background: #eef4ff;
}
.log-annot-msg-assistant {
  background: #f0fdf4;
}
.log-annot-msg-body {
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: #0f172a;
  flex: 1;
}
.log-annot-inline-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #64748b;
}
</style>
