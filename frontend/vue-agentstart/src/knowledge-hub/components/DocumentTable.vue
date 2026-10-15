<script setup lang="ts">
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../../ui/shadcn/table';
import { Switch as UiSwitch } from '../../ui/shadcn/switch';
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '../../ui/shadcn/dropdown-menu';
import { ShadcnBoolean as AsShadcnBoolean } from '../../ui/components/ShadcnBoolean';

import { ShadcnSelect as AsShadcnSelect } from '../../ui/components/ShadcnSelect';
import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

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
  if (s === 'AVAILABLE') return 'var(--kh-color-success)';
  if (s === 'FAILED') return 'var(--kh-color-danger)';
  return 'var(--kh-color-info)';
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
        <AsShadcnSelect v-model="filter" class="kh-doctab-select">
          <option value="ALL">全部</option>
          <option value="AVAILABLE">可用</option>
          <option value="FAILED">失败</option>
        </AsShadcnSelect>
        <div class="kh-doctab-search">
          <span class="kh-doctab-search-icon">🔍</span>
          <AsShadcnInput
            v-model="search"
            class="kh-doctab-search-input"
            placeholder="搜索"
          />
        </div>
        <AsShadcnSelect v-model="sort" class="kh-doctab-select">
          <option value="-uploaded">排序：上传时间 ↓</option>
          <option value="uploaded">排序：上传时间 ↑</option>
          <option value="name">排序：名称</option>
        </AsShadcnSelect>
      </div>
      <div class="kh-doctab-right">
        <AsShadcnButton variant="outline" size="sm" @click="emit('edit-metadata')">
          ≡ 元数据
        </AsShadcnButton>
        <AsShadcnButton variant="default" size="sm" @click="emit('add-file')">
          + 添加文件
        </AsShadcnButton>
      </div>
    </div>

    <!-- table -->
    <div class="kh-doctab-table" :aria-busy="loading"><Table class="min-w-[900px]">
      <TableHeader><TableRow>
        <TableHead class="kh-col kh-col-check">
          <AsShadcnBoolean type="checkbox" />
        </TableHead>
        <TableHead class="kh-col kh-col-num">#</TableHead>
        <TableHead class="kh-col kh-col-name">名称</TableHead>
        <TableHead class="kh-col kh-col-mode">分段模式</TableHead>
        <TableHead class="kh-col kh-col-num2">字数</TableHead>
        <TableHead class="kh-col kh-col-num2">召回次数</TableHead>
        <TableHead class="kh-col kh-col-time">上传时间 ↓</TableHead>
        <TableHead class="kh-col kh-col-status">状态</TableHead>
        <TableHead class="kh-col kh-col-actions">操作</TableHead>
      </TableRow></TableHeader><TableBody>

      <TableRow v-if="loading" role="status"><TableCell :colspan="9" class="h-24 text-center text-muted-foreground">
        <span class="kh-doctab-loading-spinner" aria-hidden="true" />
        <span>加载中...</span>
      </TableCell></TableRow>
      <TableRow v-if="!loading && filtered.length === 0"><TableCell :colspan="9" class="h-24 text-center text-muted-foreground">
        📄 还没有文档。点击右上角"添加文件"上传。
      </TableCell></TableRow>

      <TableRow
        v-for="(d, i) in filtered"
        :key="d.id"
        class="kh-doctab-row"
        @click="emit('open', d)"
      >
        <TableCell class="kh-col kh-col-check" @click.stop>
          <AsShadcnBoolean type="checkbox" />
        </TableCell>
        <TableCell class="kh-col kh-col-num">{{ i + 1 }}</TableCell>
        <TableCell class="kh-col kh-col-name">
          <span class="kh-doc-icon">📄</span>
          <span class="kh-doc-name">{{ d.name }}</span>
        </TableCell>
        <TableCell class="kh-col kh-col-mode">
          <span class="kh-mode-chip">{{ d.chunkMode ?? '通用' }}</span>
          <small v-if="d.parserName" class="kh-parser-chip">{{ d.parserName }} · {{ d.blockCount ?? 0 }} blocks</small>
        </TableCell>
        <TableCell class="kh-col kh-col-num2">{{ fmtNum(d.wordCount) }}</TableCell>
        <TableCell class="kh-col kh-col-num2">{{ d.hitCount ?? 0 }}</TableCell>
        <TableCell class="kh-col kh-col-time">{{ fmt(d.uploadedAt) }}</TableCell>
        <TableCell class="kh-col kh-col-status">
          <span class="kh-status-dot" :style="{ background: statusColor(d.status) }" />
          <span>{{ statusLabel(d.status, d.enabled) }}</span>
        </TableCell>
        <TableCell class="kh-col kh-col-actions" @click.stop>
          <div class="flex items-center gap-2">
            <UiSwitch :model-value="d.enabled" :aria-label="`${d.name} 启用状态`" @update:model-value="toggle(d)" />
            <DropdownMenu>
              <DropdownMenuTrigger as-child><AsShadcnButton variant="ghost" size="icon-sm" :aria-label="`${d.name} 更多操作`">⋯</AsShadcnButton></DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @select="emit('open', d)">查看片段</DropdownMenuItem>
                <DropdownMenuItem v-if="d.parserName" @select="emit('reparse', d)">重新解析原文件</DropdownMenuItem>
                <DropdownMenuItem v-if="d.parserName" @select="emit('view-parsed', d)">查看解析结构</DropdownMenuItem>
                <DropdownMenuItem class="text-destructive" @select="emit('delete', d)">删除</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </TableCell>
      </TableRow>
    </TableBody></Table></div>

    <!-- footer pagination -->
    <div class="kh-doctab-foot">
      <div class="kh-doctab-pager">
        <AsShadcnButton variant="outline" size="sm" :disabled="page <= 1" @click="goPage(page - 1)">‹</AsShadcnButton>
        <span class="kh-pager-page">{{ page }} / {{ totalPages }}</span>
        <AsShadcnButton variant="outline" size="sm" :disabled="page >= totalPages" @click="goPage(page + 1)">›</AsShadcnButton>
      </div>
      <div class="kh-pager-center">{{ page }}</div>
      <div class="kh-pager-sizes">
        <AsShadcnButton :variant="pageSize === s ? 'default' : 'ghost'" size="sm"
          v-for="s in [10, 25, 50]"
          :key="s"
          @click="emit('update:pageSize', s)"
        >
          {{ s }}
        </AsShadcnButton>
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




.kh-col {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-col-num {
  color: var(--kh-color-text-muted);
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
  background: var(--kh-color-surface-sunken);
  color: var(--kh-color-text-tertiary);
  border: 1px solid var(--kh-color-border);
  border-radius: 4px;
  font-size: 11px;
}
.kh-parser-chip { display: block; margin-top: 3px; color: var(--kh-color-text-tertiary); font-size: 10px; }

.kh-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
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


.kh-pager-page {
  min-width: 40px;
  text-align: center;
}
.kh-pager-center {
  text-align: center;
  font-size: 12px;
  color: var(--kh-color-text-tertiary);
}




.kh-col-name { min-width: 220px; max-width: 340px; }
.kh-col-name .kh-doc-icon { margin-right: 6px; }
.kh-col-time { white-space: nowrap; }
.kh-doc-name { white-space: normal; overflow-wrap: anywhere; }
.kh-doctab-table { border-radius: var(--radius); border: 1px solid var(--border); }
.kh-pager-sizes { display: flex; gap: 4px; align-items: center; }
</style>
