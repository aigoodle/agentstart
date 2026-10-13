<script setup lang="ts">
/**
 * 日志与标注 tab — table + filters. Matches Snipaste_09-01-27:
 *   - period picker + status filter + search
 *   - sortable table (标题 / 用户 / 状态 / 消息数 / 用户反馈 / 管理员反馈 / 更新时间 / 创建时间)
 *   - simple pagination footer (host-driven)
 *
 * The host owns the data. This panel is purely presentational.
 */
import { computed, ref } from 'vue';

import type { StudioLogEntry } from '../types';

interface Props {
  entries: StudioLogEntry[];
  total?: number;
  page?: number;
  pageSize?: number;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  total: undefined,
  page: 1,
  pageSize: 10,
  loading: false,
});

const emit = defineEmits<{
  (e: 'select', entry: StudioLogEntry): void;
  (e: 'change-page', page: number): void;
  (e: 'change-page-size', size: number): void;
  (e: 'change-period', period: string): void;
  (e: 'change-status', status: string): void;
  (e: 'change-search', q: string): void;
}>();

const period = ref('过去 7 天');
const statusFilter = ref('全部');
const search = ref('');
const sortKey = ref<'createdAt' | 'updatedAt'>('createdAt');

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return props.entries.filter((e) => {
    if (statusFilter.value !== '全部' && e.status !== statusFilter.value) {
      return false;
    }
    if (!q) return true;
    return (
      e.title.toLowerCase().includes(q) ||
      (e.endUser ?? '').toLowerCase().includes(q)
    );
  });
});

function statusClass(s: string) {
  return `logs-status logs-status-${s.toLowerCase()}`;
}

function fmt(iso?: string) {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

const totalPages = computed(() =>
  Math.max(1, Math.ceil((props.total ?? filtered.value.length) / props.pageSize)),
);
</script>

<template>
  <div class="logs">
    <header class="logs-top">
      <div>
        <h2 class="logs-top-title">日志</h2>
        <div class="logs-top-sub">
          日志记录了应用的运行情况，包括用户的输入和 AI 的回复。
        </div>
      </div>
    </header>

    <div class="logs-filters">
      <select
        class="logs-input"
        :value="period"
        @change="(e) => { period = (e.target as HTMLSelectElement).value; emit('change-period', period); }"
      >
        <option>过去 7 天</option>
        <option>过去 30 天</option>
        <option>全部时间</option>
      </select>
      <select
        class="logs-input"
        :value="statusFilter"
        @change="(e) => { statusFilter = (e.target as HTMLSelectElement).value; emit('change-status', statusFilter); }"
      >
        <option>全部</option>
        <option value="SUCCESS">成功</option>
        <option value="FAILED">失败</option>
        <option value="PENDING">处理中</option>
      </select>
      <input
        v-model="search"
        class="logs-input logs-search"
        placeholder="搜索..."
        @input="emit('change-search', search)"
      />
      <div class="logs-sort">
        <span class="logs-sort-label">排序:</span>
        <select
          class="logs-input"
          :value="sortKey"
          @change="(e) => (sortKey = (e.target as HTMLSelectElement).value as any)"
        >
          <option value="createdAt">创建时间</option>
          <option value="updatedAt">更新时间</option>
        </select>
      </div>
    </div>

    <div class="logs-table-wrap">
      <table class="logs-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>用户/终端用户</th>
            <th>状态</th>
            <th>消息数</th>
            <th>用户反馈</th>
            <th>管理员反馈</th>
            <th>更新时间</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="logs-tbody-empty">加载中...</td>
          </tr>
          <tr
            v-else-if="filtered.length === 0"
            class="logs-empty-row"
          >
            <td colspan="8" class="logs-tbody-empty">暂无日志</td>
          </tr>
          <tr
            v-for="e in filtered"
            :key="e.id"
            class="logs-row"
            @click="emit('select', e)"
          >
            <td class="logs-title-cell">
              <span class="logs-title-dot" />
              {{ e.title }}
            </td>
            <td class="logs-user-cell">{{ e.endUser ?? '-' }}</td>
            <td>
              <span :class="statusClass(e.status)">{{ e.status }}</span>
            </td>
            <td>{{ e.messageCount ?? '-' }}</td>
            <td>{{ e.userFeedback ?? 'N/A' }}</td>
            <td>{{ e.adminFeedback ?? 'N/A' }}</td>
            <td class="logs-date">{{ fmt(e.updatedAt) }}</td>
            <td class="logs-date">{{ fmt(e.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="logs-foot">
      <button
        type="button"
        class="logs-page-btn"
        :disabled="page <= 1"
        @click="emit('change-page', page - 1)"
      >
        ‹
      </button>
      <span class="logs-page-label">{{ page }} / {{ totalPages }}</span>
      <button
        type="button"
        class="logs-page-btn"
        :disabled="page >= totalPages"
        @click="emit('change-page', page + 1)"
      >
        ›
      </button>
      <span class="logs-page-sep">|</span>
      <select
        class="logs-input"
        :value="pageSize"
        @change="emit('change-page-size', Number(($event.target as HTMLSelectElement).value))"
      >
        <option :value="10">10</option>
        <option :value="25">25</option>
        <option :value="50">50</option>
      </select>
    </footer>
  </div>
</template>

<style scoped>
.logs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.logs-top {
  padding: 16px 24px 8px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.logs-top-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.logs-top-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}
.logs-filters {
  display: flex;
  gap: 8px;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #f1f5f9;
  align-items: center;
}
.logs-input {
  padding: 5px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #0f172a;
  outline: none;
}
.logs-input:focus {
  border-color: #6366f1;
}
.logs-search {
  min-width: 220px;
}
.logs-sort {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.logs-sort-label {
  font-size: 12px;
  color: #94a3b8;
}
.logs-table-wrap {
  flex: 1;
  overflow: auto;
  padding: 0 24px;
  background: #fff;
}
.logs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.logs-table th {
  text-align: left;
  padding: 8px 10px;
  color: #64748b;
  font-weight: 500;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  position: sticky;
  top: 0;
}
.logs-table td {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}
.logs-row {
  cursor: pointer;
  transition: background 0.15s;
}
.logs-row:hover {
  background: #f8fafc;
}
.logs-title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: #0f172a;
}
.logs-title-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #cbd5e1;
}
.logs-user-cell {
  font-family: ui-monospace, monospace;
  color: #64748b;
  font-size: 11px;
}
.logs-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}
.logs-status::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.logs-status-success {
  background: #dcfce7;
  color: #15803d;
}
.logs-status-success::before {
  background: #16a34a;
}
.logs-status-failed {
  background: #fee2e2;
  color: #b91c1c;
}
.logs-status-failed::before {
  background: #dc2626;
}
.logs-status-pending {
  background: #fef3c7;
  color: #a16207;
}
.logs-status-pending::before {
  background: #f59e0b;
}
.logs-date {
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
}
.logs-tbody-empty {
  padding: 40px !important;
  text-align: center;
  color: #94a3b8;
}
.logs-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 24px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
.logs-page-btn {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  color: #475569;
}
.logs-page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.logs-page-btn:hover:not(:disabled) {
  background: #f1f5f9;
}
.logs-page-label {
  padding: 0 6px;
  font-size: 12px;
  color: #64748b;
}
.logs-page-sep {
  margin: 0 6px;
  color: #cbd5e1;
}
</style>
