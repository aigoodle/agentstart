<script setup lang="ts">
/**
 * DocumentTable — the 文档 tab of the dataset detail (screenshot 4).
 *
 * Top toolbar: 全部 filter dropdown, search input, sort dropdown, 元数据 button,
 * "+ 添加文件" primary button.
 * Body: table with # / 名称 / 分段模式 / 字数 / 召回次数 / 上传时间 / 状态 /
 * 操作 (toggle + ⋯ menu).
 * Footer: pagination (« 1/1 » and 10/25/50 page-size selector).
 */
import { computed, ref } from 'vue';

import type { DocumentRow } from '../types';

interface Props {
  documents: DocumentRow[];
  loading?: boolean;
  page?: number;
  pageSize?: number;
  total?: number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  page: 1,
  pageSize: 10,
});

const emit = defineEmits<{
  (e: 'add-file'): void;
  (e: 'open', doc: DocumentRow): void;
  (e: 'toggle-enabled', doc: DocumentRow, next: boolean): void;
  (e: 'delete', doc: DocumentRow): void;
  (e: 'reparse', doc: DocumentRow): void;
  (e: 'view-parsed', doc: DocumentRow): void;
  (e: 'edit-metadata'): void;
  (e: 'update:page', p: number): void;
  (e: 'update:pageSize', s: number): void;
}>();

const filter = ref<'ALL' | 'AVAILABLE' | 'FAILED'>('ALL');
const search = ref('');
const sort = ref<'-uploaded' | 'name' | 'uploaded'>('-uploaded');

const filtered = computed(() => {
  let out = [...props.documents];
  if (filter.value !== 'ALL') out = out.filter((d) => d.status === filter.value);
  const q = search.value.trim().toLowerCase();
  if (q) out = out.filter((d) => d.name.toLowerCase().includes(q));
  if (sort.value === 'name') out.sort((a, b) => a.name.localeCompare(b.name));
  else {
    out.sort((a, b) => {
      const ta = a.uploadedAt ? +new Date(a.uploadedAt) : 0;
      const tb = b.uploadedAt ? +new Date(b.uploadedAt) : 0;
      return sort.value === 'uploaded' ? ta - tb : tb - ta;
    });
  }
  return out;
});

const total = computed(() => props.total ?? filtered.value.length);
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / props.pageSize)),
);

function goPage(p: number) {
  emit('update:page', Math.max(1, Math.min(p, totalPages.value)));
}

const openMenuFor = ref<null | string>(null);

function toggle(d: DocumentRow) {
  emit('toggle-enabled', d, !d.enabled);
}
function fmt(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function fmtNum(n?: number): string {
  if (n == null) return '';
  if (n >= 10_000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}
function statusColor(s: DocumentRow['status']): string {
  if (s === 'AVAILABLE') return '#059669';
  if (s === 'FAILED') return '#dc2626';
  return '#0284c7';
}
function statusLabel(s: DocumentRow['status'], enabled: boolean): string {
  if (s === 'FAILED') return '失败';
  if (s === 'PROCESSING') return '处理中';
  return enabled ? '可用' : '已停用';
}
</script>

<template>
  <div class="kh-doctab">
    <!-- Intro text -->
    <div class="kh-doctab-intro">
      知识库的所有文件都在这里显示，可通过 API 引用或在应用中被智能体检索。
      <a href="#" class="kh-doctab-more">了解更多 ↗</a>
    </div>

    <!-- toolbar -->
    <div class="kh-doctab-toolbar">
      <div class="kh-doctab-left">
        <select v-model="filter" class="kh-doctab-select">
          <option value="ALL">全部</option>
          <option value="AVAILABLE">可用</option>
          <option value="FAILED">失败</option>
        </select>
        <div class="kh-doctab-search">
          <span class="kh-doctab-search-icon">🔍</span>
          <input
            v-model="search"
            class="kh-doctab-search-input"
            placeholder="搜索"
          />
        </div>
        <select v-model="sort" class="kh-doctab-select">
          <option value="-uploaded">排序：上传时间 ↓</option>
          <option value="uploaded">排序：上传时间 ↑</option>
          <option value="name">排序：名称</option>
        </select>
      </div>
      <div class="kh-doctab-right">
        <button class="kh-btn kh-btn-secondary" @click="emit('edit-metadata')">
          ≡ 元数据
        </button>
        <button class="kh-btn kh-btn-primary" @click="emit('add-file')">
          + 添加文件
        </button>
      </div>
    </div>

    <!-- table -->
    <div class="kh-doctab-table" :aria-busy="loading">
      <div class="kh-doctab-thead">
        <div class="kh-col kh-col-check">
          <input type="checkbox" />
        </div>
        <div class="kh-col kh-col-num">#</div>
        <div class="kh-col kh-col-name">名称</div>
        <div class="kh-col kh-col-mode">分段模式</div>
        <div class="kh-col kh-col-num2">字数</div>
        <div class="kh-col kh-col-num2">召回次数</div>
        <div class="kh-col kh-col-time">上传时间 ↓</div>
        <div class="kh-col kh-col-status">状态</div>
        <div class="kh-col kh-col-actions">操作</div>
      </div>

      <div v-if="loading" class="kh-doctab-loading" role="status">
        <span class="kh-doctab-loading-spinner" aria-hidden="true" />
        <span>加载中...</span>
      </div>
      <div v-if="!loading && filtered.length === 0" class="kh-doctab-empty">
        📄 还没有文档。点击右上角"添加文件"上传。
      </div>

      <div
        v-for="(d, i) in filtered"
        :key="d.id"
        class="kh-doctab-row"
        @click="emit('open', d)"
      >
        <div class="kh-col kh-col-check" @click.stop>
          <input type="checkbox" />
        </div>
        <div class="kh-col kh-col-num">{{ i + 1 }}</div>
        <div class="kh-col kh-col-name">
          <span class="kh-doc-icon">📄</span>
          <span class="kh-doc-name">{{ d.name }}</span>
        </div>
        <div class="kh-col kh-col-mode">
          <span class="kh-mode-chip">{{ d.chunkMode ?? '通用' }}</span>
          <small v-if="d.parserName" class="kh-parser-chip">{{ d.parserName }} · {{ d.blockCount ?? 0 }} blocks</small>
        </div>
        <div class="kh-col kh-col-num2">{{ fmtNum(d.wordCount) }}</div>
        <div class="kh-col kh-col-num2">{{ d.hitCount ?? 0 }}</div>
        <div class="kh-col kh-col-time">{{ fmt(d.uploadedAt) }}</div>
        <div class="kh-col kh-col-status">
          <span class="kh-status-dot" :style="{ background: statusColor(d.status) }" />
          <span>{{ statusLabel(d.status, d.enabled) }}</span>
        </div>
        <div class="kh-col kh-col-actions" @click.stop>
          <label class="kh-switch" :title="d.enabled ? '已启用 · 点击停用' : '已停用 · 点击启用'">
            <input
              type="checkbox"
              :checked="d.enabled"
              @change="toggle(d)"
            />
            <span class="kh-switch-slider" />
          </label>
          <button
            class="kh-more-btn"
            @click="openMenuFor = openMenuFor === d.id ? null : d.id"
          >
            ⋯
          </button>
          <div
            v-if="openMenuFor === d.id"
            class="kh-more-menu"
            @click.stop
          >
            <div class="kh-more-item" @click="emit('open', d); openMenuFor = null">
              查看片段
            </div>
            <div v-if="d.parserName" class="kh-more-item" @click="emit('reparse', d); openMenuFor = null">
              重新解析原文件
            </div>
            <div v-if="d.parserName" class="kh-more-item" @click="emit('view-parsed', d); openMenuFor = null">
              查看解析结构
            </div>
            <div class="kh-more-item kh-more-danger" @click="emit('delete', d); openMenuFor = null">
              删除
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- footer pagination -->
    <div class="kh-doctab-foot">
      <div class="kh-doctab-pager">
        <button class="kh-pager-btn" @click="goPage(page - 1)">‹</button>
        <span class="kh-pager-page">{{ page }} / {{ totalPages }}</span>
        <button class="kh-pager-btn" @click="goPage(page + 1)">›</button>
      </div>
      <div class="kh-pager-center">{{ page }}</div>
      <div class="kh-pager-sizes">
        <button
          v-for="s in [10, 25, 50]"
          :key="s"
          class="kh-pager-size"
          :class="{ 'kh-pager-size-active': pageSize === s }"
          @click="emit('update:pageSize', s)"
        >
          {{ s }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kh-doctab {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.kh-doctab-intro {
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
  margin-bottom: 12px;
  line-height: 1.5;
}
.kh-doctab-more {
  color: var(--kh-color-primary);
  text-decoration: none;
}

/* Toolbar */
.kh-doctab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.kh-doctab-left {
  display: flex;
  gap: 8px;
  align-items: center;
}
.kh-doctab-right {
  display: flex;
  gap: 8px;
}
.kh-doctab-select {
  padding: 6px 24px 6px 10px;
  border: 1px solid var(--kh-input-border);
  border-radius: 6px;
  background: var(--kh-input-bg);
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  outline: none;
}
.kh-doctab-select:focus {
  border-color: var(--kh-color-primary);
}
.kh-doctab-search {
  position: relative;
}
.kh-doctab-search-icon {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--kh-color-text-muted);
}
.kh-doctab-search-input {
  padding: 6px 10px 6px 26px;
  border: 1px solid var(--kh-input-border);
  border-radius: 6px;
  background: var(--kh-input-bg);
  color: var(--kh-color-text-primary);
  font-size: 12px;
  outline: none;
  min-width: 200px;
}
.kh-doctab-search-input:focus {
  border-color: var(--kh-color-primary);
}

.kh-btn {
  padding: 6px 12px;
  border: 1px solid var(--kh-color-border);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.kh-btn-primary {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  border-color: var(--kh-color-primary);
  box-shadow: 0 2px 4px rgba(79, 70, 229, 0.25);
}
.kh-btn-primary:hover {
  transform: translateY(-1px);
}
.kh-btn-secondary {
  background: var(--kh-color-surface);
  color: var(--kh-color-text-secondary);
}
.kh-btn-secondary:hover {
  background: var(--kh-color-surface-hover);
}

/* Table */
.kh-doctab-table {
  position: relative;
  flex: 1;
  border: 1px solid var(--kh-color-border);
  border-radius: 8px;
  overflow: auto;
  background: var(--kh-color-surface);
}
.kh-doctab-loading {
  position: absolute;
  inset: 40px 0 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: color-mix(in srgb, var(--kh-color-surface) 82%, transparent);
  color: var(--kh-color-text-tertiary);
  font-size: 12px;
  backdrop-filter: blur(1px);
}
.kh-doctab-loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--kh-color-border);
  border-top-color: var(--kh-color-primary);
  border-radius: 50%;
  animation: kh-doctab-spin 0.7s linear infinite;
}
@keyframes kh-doctab-spin {
  to { transform: rotate(360deg); }
}
.kh-doctab-thead,
.kh-doctab-row {
  display: grid;
  grid-template-columns: 32px 32px 2fr 120px 80px 90px 140px 80px 100px;
  align-items: center;
  padding: 0 10px;
  gap: 0;
}
.kh-doctab-thead {
  height: 40px;
  background: var(--kh-color-surface-sunken);
  border-bottom: 1px solid var(--kh-color-border);
  font-size: 11px;
  color: var(--kh-color-text-tertiary);
  font-weight: 500;
}
.kh-doctab-row {
  height: 44px;
  border-bottom: 1px solid var(--kh-color-divider);
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  transition: background 0.15s;
}
.kh-doctab-row:hover {
  background: var(--kh-color-surface-hover);
}
.kh-col {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-col-num {
  color: var(--kh-color-text-muted);
}
.kh-col-name {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kh-doc-icon {
  color: var(--kh-color-primary);
}
.kh-doc-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-mode-chip {
  padding: 2px 8px;
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary);
  border-radius: 4px;
  font-size: 11px;
}
.kh-parser-chip { display: block; margin-top: 3px; color: var(--kh-color-text-tertiary); font-size: 10px; }
.kh-col-status {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kh-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}
.kh-col-actions {
  position: relative;
  display: flex;
  gap: 6px;
  align-items: center;
}

/* Switch */
.kh-switch {
  position: relative;
  display: inline-block;
  width: 32px;
  height: 18px;
  cursor: pointer;
}
.kh-switch input {
  display: none;
}
.kh-switch-slider {
  position: absolute;
  inset: 0;
  background: #cbd5e1;
  border-radius: 9px;
  transition: background 0.15s;
}
.kh-switch-slider::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.15s;
}
.kh-switch input:checked + .kh-switch-slider {
  background: #4f46e5;
}
.kh-switch input:checked + .kh-switch-slider::before {
  transform: translateX(14px);
}

.kh-more-btn {
  padding: 0 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  font-size: 16px;
  color: var(--kh-color-text-tertiary);
  cursor: pointer;
  line-height: 1;
}
.kh-more-btn:hover {
  background: var(--kh-color-surface-hover);
}
.kh-more-menu {
  position: absolute;
  right: 0;
  top: 24px;
  z-index: 20;
  min-width: 140px;
  padding: 4px;
  background: var(--kh-color-surface-raised);
  border: 1px solid var(--kh-color-border);
  border-radius: 6px;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.1);
}
.kh-more-item {
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
}
.kh-more-item:hover {
  background: var(--kh-color-surface-hover);
}
.kh-more-danger {
  color: #dc2626;
}

.kh-doctab-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--kh-color-text-muted);
  font-size: 12px;
}

/* Footer */
.kh-doctab-foot {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 12px 4px 0;
  gap: 12px;
}
.kh-doctab-pager {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
}
.kh-pager-btn {
  padding: 2px 8px;
  border: 1px solid var(--kh-color-border);
  border-radius: 4px;
  background: var(--kh-color-surface);
  cursor: pointer;
  color: var(--kh-color-text-tertiary);
}
.kh-pager-btn:hover {
  background: var(--kh-color-surface-hover);
}
.kh-pager-page {
  min-width: 40px;
  text-align: center;
}
.kh-pager-center {
  text-align: center;
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
}
.kh-pager-sizes {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.kh-pager-size {
  padding: 2px 8px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: var(--kh-color-text-muted);
  cursor: pointer;
  border-radius: 4px;
}
.kh-pager-size:hover {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.kh-pager-size-active {
  background: #4f46e5;
  color: #fff;
}
</style>
