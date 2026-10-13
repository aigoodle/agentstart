<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {
  createAgentStartClient,
  type CreateScheduledTriggerRequest,
  type TriggerInvocationWire,
  type TriggerWire
} from '../client';
import {useAgentStartClient} from '../client/vue';
import {mergeAgentStartHeaders, useAgentStartConfig} from '../config';
import {Button, Drawer, Modal, Table, message, type TableColumnType} from '../ui';

const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(global.headers)
});
const rows = ref<TriggerWire[]>([]);
const loading = ref(false);
const error = ref('');
const query = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const category = ref<'ALL' | 'APPLICATION' | 'USER'>('ALL');
const editorOpen = ref(false);
const saving = ref(false);
const historyOpen = ref(false);
const historyLoading = ref(false);
const current = ref<TriggerWire>();
const invocations = ref<TriggerInvocationWire[]>([]);
const configText = ref('{}');
const form = reactive<CreateScheduledTriggerRequest>({
  name: '',
  type: 'WEBHOOK',
  targetType: 'WORKFLOW',
  targetId: '',
  config: {},
  enabled: true
});
const columns: TableColumnType[] = [
  {title: '名称', dataIndex: 'name', key: 'name', width: 180},
  {title: '类型', dataIndex: 'type', key: 'type', width: 130},
  {title: '目标', dataIndex: 'targetId', key: 'target', width: 210},
  {title: '状态', dataIndex: 'enabled', key: 'enabled', width: 72, align: 'center'},
  {title: '触发次数', dataIndex: 'fireCount', key: 'fireCount', width: 82, align: 'center'},
  {title: '上次触发', dataIndex: 'lastFireAt', key: 'lastFireAt', width: 165},
  {title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 165},
  {title: '操作', key: 'actions', width: 160}
];
const pageCount = () => Math.max(1, Math.ceil(total.value / pageSize.value));

function flash(text: string) {
  message.success(text, 2.5);
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await client.triggers.page({
      page: page.value,
      pageSize: pageSize.value,
      keyword: query.value,
      category: category.value === 'ALL' ? undefined : category.value,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (e: any) {
    error.value = e?.message ?? '触发器加载失败';
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function changeCategory(value: 'ALL' | 'APPLICATION' | 'USER') {
  if (category.value === value) return;
  category.value = value;
  page.value = 1;
  load();
}

function changePage(next: number) {
  if (next < 1 || next > pageCount() || next === page.value) return;
  page.value = next;
  load();
}

function changePageSize() {
  page.value = 1;
  load();
}

function openCreate() {
  Object.assign(form, {name: '', type: 'WEBHOOK', targetType: 'WORKFLOW', targetId: '', config: {}, enabled: true});
  configText.value = '{}';
  editorOpen.value = true;
}

async function save() {
  if (!form.name.trim() || !form.targetId.trim()) return void (error.value = '请填写名称和目标 ID');
  let config: Record<string, unknown>;
  try {
    config = JSON.parse(configText.value || '{}');
  } catch {
    return void (error.value = '配置必须是合法 JSON');
  }
  saving.value = true;
  error.value = '';
  try {
    await client.triggers.create({...form, name: form.name.trim(), targetId: form.targetId.trim(), config});
    editorOpen.value = false;
    flash('触发器已创建');
    await load();
  } catch (e: any) {
    error.value = e?.message ?? '创建失败';
  } finally {
    saving.value = false;
  }
}

async function toggle(row: TriggerWire) {
  try {
    await client.triggers.setEnabled(row.id, !row.enabled);
    row.enabled = !row.enabled;
    flash(row.enabled ? '已启用' : '已停用');
  } catch (e: any) {
    error.value = e?.message ?? '状态更新失败';
  }
}

async function remove(row: TriggerWire) {
  if (!confirm(`确定删除触发器“${row.name}”吗？`)) return;
  try {
    await client.triggers.remove(row.id);
    if (rows.value.length === 1 && page.value > 1) page.value--;
    flash('已删除');
    await load();
  } catch (e: any) {
    error.value = e?.message ?? '删除失败';
  }
}

async function fire(row: TriggerWire) {
  try {
    await client.triggers.fire(row.id, {});
    flash('触发请求已提交');
  } catch (e: any) {
    error.value = e?.message ?? '触发失败';
  }
}

async function openHistory(row: TriggerWire) {
  current.value = row;
  historyOpen.value = true;
  await loadHistory();
}

async function loadHistory() {
  if (!current.value) return;
  historyLoading.value = true;
  try {
    invocations.value = await client.triggers.invocations(current.value.id);
  } catch (e: any) {
    error.value = e?.message ?? '历史加载失败';
  } finally {
    historyLoading.value = false;
  }
}

async function replay(item: TriggerInvocationWire) {
  try {
    await client.triggers.replay(item.id);
    flash('已提交重放');
    await loadHistory();
  } catch (e: any) {
    error.value = e?.message ?? '重放失败';
  }
}

function time(value?: string | null) {
  return value ? new Date(value).toLocaleString() : '—';
}

const triggerTypeLabels: Record<string, string> = {
  WEBHOOK: 'Webhook',
  CRON: '定时任务',
  EVENT: '业务事件',
  CHANNEL_MESSAGE: '消息监听',
  MANUAL: '手动触发',
};

function configOf(row: TriggerWire): Record<string, any> {
  try {
    return row.configJson ? JSON.parse(row.configJson) : (row.config ?? {});
  } catch {
    return row.config ?? {};
  }
}

function displayName(row: TriggerWire) {
  const config = configOf(row);
  if (row.type === 'CHANNEL_MESSAGE') {
    return config.connectionName || config.channelName || row.name;
  }
  return row.name;
}

function sourceText(row: TriggerWire) {
  const config = configOf(row);
  if (config.sourceWorkflowKey) return '工作流发布自动生成';
  return '手工创建';
}

onMounted(load);
</script>

<template>
  <div class="th">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px">⚡</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">触发器管理</div>
          <div class="as-page-subtitle">
            消息监听 · 定时任务 · Webhook · 业务事件
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <div class="th-search"><span>⌕</span><input v-model="query" placeholder="搜索名称、类型或目标"
                                                    @keyup.enter="search">
          <Button size="small" @click="search">查询</Button>
        </div>
        <Button :loading="loading" @click="load">↻ 刷新</Button>
        <Button type="primary" @click="openCreate">＋ 创建触发器</Button>
      </div>
    </div>
    <div v-if="error" class="th-alert"><b>操作失败</b><span>{{ error }}</span>
      <button @click="error=''">×</button>
    </div>
    <nav class="th-categories" aria-label="触发器分类">
      <Button size="small" :type="category === 'ALL' ? 'primary' : 'text'" @click="changeCategory('ALL')">全部触发器</Button>
      <Button size="small" :type="category === 'APPLICATION' ? 'primary' : 'text'" @click="changeCategory('APPLICATION')">应用触发器</Button>
      <Button size="small" :type="category === 'USER' ? 'primary' : 'text'" @click="changeCategory('USER')">用户触发器</Button>
    </nav>
    <Table :columns="columns" :data-source="rows" :loading="loading" row-key="id" :scroll="{x: 1170}">
      <template #emptyText>暂无触发器</template>
      <template #bodyCell="{column, record}">
        <div v-if="column.key === 'name'" class="th-cell-stack"><b>{{ displayName(record) }}</b><small>{{ sourceText(record) }} · {{ record.id }}</small></div>
        <span v-else-if="column.key === 'type'" class="th-tag">{{ triggerTypeLabels[record.type] || record.type }}</span>
        <div v-else-if="column.key === 'target'" class="th-cell-stack"><small>{{ record.targetType || 'WORKFLOW' }}</small><span>{{ record.targetId }}</span></div>
        <button v-else-if="column.key === 'enabled'" class="th-switch" :class="{on: record.enabled}" :aria-label="record.enabled ? '停用' : '启用'" @click="toggle(record)"><i></i></button>
        <template v-else-if="column.key === 'fireCount'">{{ record.fireCount ?? 0 }}</template>
        <template v-else-if="column.key === 'lastFireAt'">{{ time(record.lastFireAt) }}</template>
        <template v-else-if="column.key === 'createdAt'">{{ time(record.createdAt) }}</template>
        <div v-else-if="column.key === 'actions'" class="th-actions">
          <button @click="openHistory(record)">历史</button><button @click="fire(record)">触发</button><button class="danger" @click="remove(record)">删除</button>
        </div>
      </template>
    </Table>
    <template v-if="total">
      <div class="th-pagination"><span>共 {{ total }} 条</span><select v-model.number="pageSize"
                                                                       @change="changePageSize">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
      </select>
        <button :disabled="page===1" @click="changePage(page-1)">上一页</button>
        <b>{{ page }} / {{ pageCount() }}</b>
        <button :disabled="page===pageCount()" @click="changePage(page+1)">下一页</button>
      </div>
    </template>

    <Modal v-model:open="editorOpen" class="th-modal" centered :width="620" :footer="false" :mask-closable="!saving"
           :keyboard="!saving" :closable="!saving">
      <template #title>
        <div class="as-modal-heading">
          <div class="as-modal-title">创建触发器</div>
          <div class="as-modal-subtitle">保存后即可接收事件或按计划调度。</div>
        </div>
      </template>
      <form @submit.prevent="save">
        <label>名称<input v-model="form.name" required placeholder="订单创建后处理"/></label>
        <div class="th-form-row"><label>触发类型<select v-model="form.type">
          <option>WEBHOOK</option>
          <option>CRON</option>
          <option>EVENT</option>
          <option>CHANNEL_MESSAGE</option>
          <option>MANUAL</option>
        </select></label><label>目标类型<input v-model="form.targetType" placeholder="WORKFLOW"/></label></div>
        <label>目标 ID<input v-model="form.targetId" required placeholder="工作流或 Agent ID"/></label><label>配置
        JSON<textarea v-model="configText" rows="8" placeholder='{"path":"order-created"}'></textarea></label><label
          class="th-check"><input v-model="form.enabled" type="checkbox"> 创建后立即启用</label>
        <footer>
          <Button @click="editorOpen=false">取消</Button>
          <Button type="primary" :loading="saving" html-type="submit">创建</Button>
        </footer>
      </form>
    </Modal>
    <Drawer v-model:open="historyOpen" width="min(760px, 92vw)">
      <template #title>
        <div class="as-modal-heading">
          <div class="as-modal-title">{{ current?.name }} · 执行历史</div>
          <div class="as-modal-subtitle">失败任务可以使用原始参数重新执行。</div>
        </div>
      </template>
      <template #extra><Button size="small" :loading="historyLoading" @click="loadHistory">↻ 刷新</Button></template>
        <div v-if="historyLoading" class="th-empty">加载中…</div>
        <div v-else-if="!invocations.length" class="th-empty">尚无调用记录</div>
        <div v-else class="th-history">
          <article v-for="item in invocations" :key="item.id"><i :class="item.status.toLowerCase()"></i>
            <div><b>{{ item.source || 'manual' }}</b><small>{{ time(item.createdAt) }} · {{ item.id }}</small>
              <p v-if="item.error">{{ item.error }}</p></div>
            <span :class="item.status.toLowerCase()">{{ item.status }}</span>
            <button @click="replay(item)">重放</button>
          </article>
        </div>
    </Drawer>
  </div>
</template>

<style scoped>
.th {
  --panel: #fff;
  --soft: #f8fafc;
  --line: #e5e7eb;
  --text: #172033;
  --muted: #667085;
  --primary: #7c3aed;
  padding: 24px;
  color: var(--text)
}

:global(.dark) .th {
  --panel: #1f1f1f;
  --soft: #262626;
  --line: #353535;
  --text: #f5f5f5;
  --muted: #a3a3a3
}

.th button:not(.as-btn), .th input, .th select, .th textarea {
  font: inherit
}

.th button {
  cursor: pointer
}

.th-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  border: 1px solid #e9d5ff;
  border-radius: 16px;
  background: linear-gradient(120deg, #faf5ff, #fff 58%, #eff6ff)
}

:global(.dark) .th-hero {
  border-color: #4c3566;
  background: linear-gradient(120deg, #2d203b, #1f1f1f 58%, #172b36)
}

.th-kicker {
  color: var(--primary);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: .14em
}

.th h2 {
  margin: 5px 0 6px;
  font-size: 25px
}

.th h3, .th p {
  margin: 0
}

.th-hero p {
  color: var(--muted)
}

.th-primary {
  padding: 10px 16px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: var(--primary);
  font-weight: 650
}

.th-categories {
  display: flex;
  gap: 6px;
  margin: 18px 0 12px;
  padding: 4px;
  width: fit-content;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--soft)
}


.th-stats {
  display: grid;
  grid-template-columns:repeat(4, 1fr);
  margin: 18px 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel)
}

.th-stats div {
  display: grid;
  gap: 3px;
  padding: 16px 20px;
  border-right: 1px solid var(--line)
}

.th-stats div:last-child {
  border: 0
}

.th-stats b {
  font-size: 21px
}

.th-stats span {
  color: var(--muted);
  font-size: 12px
}

.th-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 20px 0 15px
}

.th-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(440px, 100%);
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--panel)
}

.th-search input {
  width: 100%;
  padding: 9px 0;
  border: 0;
  outline: 0;
  color: var(--text);
  background: transparent
}

.th-refresh, .th-card footer button, .th-drawer-tools button {
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 7px;
  color: var(--text);
  background: var(--panel)
}

.th-alert {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  padding: 11px 14px;
  border-radius: 8px
}

.th-alert {
  color: #b42318;
  background: #fef3f2
}

.th-alert span {
  flex: 1
}

.th-alert button {
  border: 0;
  background: transparent
}

.th-grid {
  display: grid;
  grid-template-columns:repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px
}

.th-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: var(--panel)
}

.th-card header {
  display: flex;
  align-items: center;
  gap: 11px
}

.th-card header > div:nth-child(2) {
  min-width: 0;
  flex: 1
}

.th-card h3 {
  font-size: 15px
}

.th-card code {
  color: var(--muted);
  font-size: 10px
}

.th-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 10px;
  color: var(--primary);
  background: #f3e8ff;
  font-size: 20px
}

.th-switch {
  width: 35px;
  height: 20px;
  padding: 2px;
  border: 0;
  border-radius: 99px;
  background: #d0d5dd
}

.th-switch i {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: .2s
}

.th-switch.on {
  background: #12b76a
}

.th-switch.on i {
  transform: translateX(15px)
}

.th-target {
  display: grid;
  gap: 4px;
  margin: 16px 0 !important;
  padding: 11px;
  border-radius: 8px;
  background: var(--soft)
}

.th-target span {
  color: var(--muted);
  font-size: 10px
}

.th-target b {
  font-size: 13px;
  overflow-wrap: anywhere
}

.th-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px
}

.th-meta span {
  padding: 3px 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--muted);
  font-size: 10px
}

.th-card footer {
  display: flex;
  justify-content: flex-end;
  gap: 7px;
  margin-top: 16px;
  padding-top: 13px;
  border-top: 1px solid var(--line)
}

.th-card footer .danger {
  color: #b42318
}

.th-empty {
  padding: 42px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  color: var(--muted);
  text-align: center;
  background: var(--soft)
}

.th-empty > div {
  font-size: 34px
}

.th-modal-root, .th-drawer-root {
  position: fixed;
  z-index: 1100;
  inset: 0;
  background: #0f172a73
}

.th-modal-root {
  display: grid;
  place-items: center;
  padding: 20px
}

.th-modal {
  width: min(620px, 100%);
  border-radius: 14px;
  background: var(--panel);
  box-shadow: 0 24px 70px #0004
}

.th-modal > header, .th-drawer > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line)
}

.th-drawer header p { margin-top: 4px; color: var(--muted); font-size: 12px }

.th-modal header button, .th-drawer header button {
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 22px
}

.th-modal form {
  display: grid;
  gap: 13px;
  padding: 18px 20px
}

.th-modal label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 600
}

.th-modal input, .th-modal select, .th-modal textarea {
  padding: 9px;
  border: 1px solid var(--line);
  border-radius: 7px;
  color: var(--text);
  background: var(--panel)
}

.th-form-row {
  display: grid;
  grid-template-columns:1fr 1fr;
  gap: 12px
}

.th-check {
  display: flex !important;
  grid-auto-flow: column;
  justify-content: start
}

.th-modal form > footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px
}

.th-modal form > footer button {
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 7px
}

.th-drawer-root {
  display: flex;
  justify-content: flex-end
}

.th-drawer {
  width: min(760px, 92vw);
  height: 100%;
  overflow: auto;
  background: var(--panel);
  box-shadow: -15px 0 45px #0002
}

.th-drawer-tools {
  display: flex;
  justify-content: flex-end;
  padding: 12px 18px
}

.th-history {
  display: grid;
  gap: 8px;
  padding: 0 18px 20px
}

.th-history article {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 9px
}

.th-history article > i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2e90fa
}

.th-history article > i.completed {
  background: #12b76a
}

.th-history article > i.failed {
  background: #f04438
}

.th-history article > div {
  display: grid;
  min-width: 0;
  flex: 1
}

.th-history small {
  color: var(--muted)
}

.th-history p {
  color: #b42318;
  font-size: 11px
}

.th-history article > span {
  padding: 3px 7px;
  border-radius: 5px;
  color: #175cd3;
  background: #eff8ff;
  font-size: 10px
}

.th-history article > span.completed {
  color: #027a48;
  background: #ecfdf3
}

.th-history article > span.failed {
  color: #b42318;
  background: #fef3f2
}

.th-history article > button {
  padding: 5px 9px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--text);
  background: var(--panel)
}

@media (max-width: 700px) {
  .th {
    padding: 14px
  }

  .th-hero {
    align-items: flex-start;
    flex-direction: column
  }

  .th-stats {
    grid-template-columns:repeat(2, 1fr)
  }

  .th-stats div:nth-child(2) {
    border-right: 0
  }

  .th-form-row {
    grid-template-columns:1fr
  }

  .th-grid {
    grid-template-columns:1fr
  }
}

.th-search button {
  align-self: stretch;
  padding: 0 14px;
  border: 0;
  border-left: 1px solid var(--line);
  color: var(--primary);
  background: var(--soft)
}

.th-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel)
}

.th-table {
  width: 100%;
  min-width: 1080px;
  border-collapse: collapse;
  font-size: 13px
}

.th-table th {
  padding: 11px 14px;
  color: var(--muted);
  background: var(--soft);
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap
}

.th-table td {
  padding: 13px 14px;
  border-top: 1px solid var(--line);
  vertical-align: middle
}

.th-table td:first-child, .th-table td:nth-child(3) {
  display: grid;
  gap: 3px;
  max-width: 230px
}

.th-table td small {
  color: var(--muted);
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.th-table td span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.th-tag {
  display: inline-block;
  padding: 3px 7px;
  border-radius: 5px;
  color: var(--primary);
  background: #f3e8ff;
  font-size: 10px;
  font-weight: 650
}

:global(.dark) .th-tag {
  background: #3b2851
}

.th-actions {
  display: flex;
  gap: 6px;
  white-space: nowrap
}

.th-actions button, .th-pagination button, .th-pagination select {
  padding: 6px 9px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--text);
  background: var(--panel)
}

.th-actions .danger {
  color: #b42318
}

.th-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 2px;
  color: var(--muted);
  font-size: 12px
}

.th-pagination button:disabled {
  cursor: not-allowed;
  opacity: .45
}

.th-pagination b {
  color: var(--text);
  font-weight: 500
}

.th-hero {
  align-items: center;
  padding: 14px 18px;
}

.th-hero > div:first-child {
  min-width: 240px;
}

.th-hero-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.th-hero-actions .th-search {
  width: clamp(240px, 26vw, 360px);
}

.th-hero-actions > button {
  flex: none;
  white-space: nowrap;
}

.th-hero h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.th-hero p {
  font-size: 13px;
}

.th-toolbar {
  margin: 14px 0 12px;
}

.th-search {
  width: min(560px, 100%);
  padding-right: 0;
}

.th-search button {
  min-width: 68px;
  padding: 0 16px;
  white-space: nowrap;
}

.th-cell-stack {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.th-cell-stack small,
.th-cell-stack span {
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.th :deep(.as-table) {
  font-size: 13px;
}

.th :deep(.as-table th),
.th :deep(.as-table td) {
  height: 54px;
  box-sizing: border-box;
}

@media (max-width: 1100px) {
  .th-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .th-hero-actions {
    width: 100%;
  }

  .th-hero-actions .th-search {
    width: auto;
    flex: 1;
  }
}
</style>
