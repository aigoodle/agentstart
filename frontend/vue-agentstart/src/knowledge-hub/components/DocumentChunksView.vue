<script setup lang="ts">
/**
 * DocumentChunksView — chunks browser for a single document (screenshot 7).
 *
 * Header: doc name + enabled toggle + more menu.
 * Left main: filter dropdown + search + view-toggle, then a paginated grid of
 *   chunk cards each showing SEG-NN tag, char count, hit count, content, tags.
 * Right metadata pane (collapsible-ish): 元数据 header with 开放智能标签 button;
 *   文档信息 (name/kind/size/upload/parse/embed dates), 技术参数 (chunk mode,
 *   max size, char count, chunk count, avg time, tokens).
 */
import { computed, ref } from 'vue';

import type { Chunk, DocMetadata } from '../types';

interface Props {
  documentName: string;
  documentEnabled: boolean;
  chunks: Chunk[];
  metadata?: DocMetadata;
  loading?: boolean;
  /** If false, the "+ 添加分段" button is hidden (hub has no appendSegment). */
  canAppend?: boolean;
  /** 1-indexed page number the parent is currently displaying. */
  page?: number;
  pageSize?: number;
  /** Server-reported total number of segments in the document. */
  total?: number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  metadata: () => ({}),
  canAppend: true,
  page: 1,
  pageSize: 20,
  total: 0,
});

const emit = defineEmits<{
  (e: 'toggle-doc-enabled', v: boolean): void;
  (e: 'edit-chunk', c: Chunk): void;
  (e: 'toggle-chunk', c: Chunk, next: boolean): void;
  (e: 'add-chunk'): void;
  (e: 'delete-chunk', c: Chunk): void;
  (e: 'update:page', p: number): void;
  (e: 'update:pageSize', s: number): void;
}>();

const search = ref('');
const filter = ref<'ALL' | 'DISABLED' | 'ENABLED'>('ALL');
const showMeta = ref(true);

/**
 * Ids of chunks the user asked to see in full. Client-side only.
 *
 * `allExpanded` is a separate toggle so "全部展开" doesn't need to enumerate
 * every id up-front (which is expensive on large docs) — when it is on, we
 * treat every card as expanded regardless of the set. Toggling an individual
 * card while `allExpanded` is on flips it back to false + seeds `expanded`
 * with everything currently visible so the interaction stays intuitive.
 */
const expanded = ref<Set<string>>(new Set());
const allExpanded = ref(false);
function isExpanded(id: string): boolean {
  return allExpanded.value || expanded.value.has(id);
}
function toggleExpand(id: string) {
  if (allExpanded.value) {
    // switch to per-card mode with everything but this one still expanded
    const next = new Set(filtered.value.map((c) => c.id));
    next.delete(id);
    expanded.value = next;
    allExpanded.value = false;
    return;
  }
  if (expanded.value.has(id)) expanded.value.delete(id);
  else expanded.value.add(id);
  expanded.value = new Set(expanded.value);
}
function expandAll() {
  allExpanded.value = true;
  expanded.value = new Set();
}
function collapseAll() {
  allExpanded.value = false;
  expanded.value = new Set();
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return props.chunks.filter((c) => {
    if (filter.value === 'ENABLED' && !c.enabled) return false;
    if (filter.value === 'DISABLED' && c.enabled) return false;
    if (q && !c.content.toLowerCase().includes(q)) return false;
    return true;
  });
});

/**
 * Rough cutoff — anything longer than this needs the "展开" affordance. Kept in
 * sync with the CSS `.kh-chunk-body:not(.kh-chunk-body-open)` max-height so the
 * button doesn't appear on chunks that already fit.
 */
const COLLAPSED_CHAR_THRESHOLD = 260;
function needsToggle(c: Chunk): boolean {
  return (c.content?.length ?? 0) > COLLAPSED_CHAR_THRESHOLD;
}

function docToggle(next: boolean) {
  emit('toggle-doc-enabled', next);
}

function fmtNum(n?: number): string {
  if (n == null) return '';
  if (n >= 10_000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}

// ---- pagination
const totalPages = computed(() =>
  Math.max(1, Math.ceil((props.total || props.chunks.length) / props.pageSize)),
);
function goPage(p: number) {
  const next = Math.max(1, Math.min(p, totalPages.value));
  if (next !== props.page) emit('update:page', next);
}
function changePageSize(s: number) {
  if (s !== props.pageSize) emit('update:pageSize', s);
}
</script>

<template>
  <div class="kh-chunks">
    <!-- Body: chunks + metadata pane (no separate action bar — everything
         above the list lives in the compact toolbar row inside .kh-chunks-main
         so the chunks start right at the top of the drawer content). -->
    <div class="kh-chunks-body">
      <!-- LEFT: chunks -->
      <div class="kh-chunks-main">
        <div class="kh-chunks-toolbar">
          <div class="kh-chunks-toolbar-left">
            <span class="kh-chunks-count">
              {{ total || chunks.length }} 分段
              <span v-if="metadata.totalChars">
                · {{ fmtNum(metadata.totalChars) }} 字符
                · 平均分段大小 {{ Math.round(metadata.avgChunkChars ?? 0) }}
              </span>
            </span>
          </div>
          <div class="kh-chunks-toolbar-right">
            <select v-model="filter" class="kh-doctab-select">
              <option value="ALL">全部</option>
              <option value="ENABLED">已启用</option>
              <option value="DISABLED">已停用</option>
            </select>
            <div class="kh-doctab-search">
              <span class="kh-doctab-search-icon">🔍</span>
              <input
                v-model="search"
                class="kh-doctab-search-input"
                placeholder="搜索"
              />
            </div>
            <!-- Text-style bulk actions — kept compact per user request so
                 they don't visually compete with the primary "+ 添加分段" CTA. -->
            <button class="kh-chunks-link" @click="expandAll">全部展开</button>
            <span class="kh-chunks-link-sep">·</span>
            <button class="kh-chunks-link" @click="collapseAll">全部收起</button>
            <button
              v-if="canAppend"
              class="kh-btn kh-btn-primary"
              @click="emit('add-chunk')"
            >
              + 添加分段
            </button>
            <label
              class="kh-switch"
              :title="documentEnabled ? '已启用 · 点击停用' : '已停用 · 点击启用'"
            >
              <input
                type="checkbox"
                :checked="documentEnabled"
                @change="(e) => docToggle((e.target as HTMLInputElement).checked)"
              />
              <span class="kh-switch-slider" />
            </label>
            <span
              class="kh-chunks-status"
              :style="{ color: documentEnabled ? '#059669' : '#dc2626' }"
            >
              {{ documentEnabled ? '可用' : '已停用' }}
            </span>
          </div>
        </div>

        <div v-if="loading" class="kh-chunks-empty">加载中...</div>
        <div v-else-if="filtered.length === 0" class="kh-chunks-empty">
          没有匹配的分段
        </div>

        <div class="kh-chunk-grid">
          <div
            v-for="c in filtered"
            :key="c.id"
            class="kh-chunk-card"
            @click="emit('edit-chunk', c)"
          >
            <div class="kh-chunk-head">
              <span class="kh-chunk-tag">
                SEG-{{ String(c.position + 1).padStart(2, '0') }}
              </span>
              <span class="kh-chunk-meta">
                {{ c.charCount ?? c.content.length }} 字符
              </span>
              <span class="kh-chunk-meta">
                {{ c.hitCount ?? 0 }} 命中数
              </span>
              <span class="kh-chunk-flex" />
              <!-- Inline expand affordance — sits with the "已启用"/字符 metadata
                   so it doesn't eat a full row per card. Only shown when the
                   body would actually be clipped. -->
              <button
                v-if="needsToggle(c)"
                class="kh-chunk-expand-inline"
                :title="isExpanded(c.id) ? '收起' : '展开'"
                @click.stop="toggleExpand(c.id)"
              >
                {{ isExpanded(c.id) ? '收起 ▲' : '展开 ▼' }}
              </button>
              <span
                v-if="c.enabled"
                class="kh-chunk-badge kh-chunk-badge-on"
              >
                已启用
              </span>
              <span v-else class="kh-chunk-badge kh-chunk-badge-off">
                已停用
              </span>
            </div>
            <div
              class="kh-chunk-body"
              :class="{ 'kh-chunk-body-open': isExpanded(c.id) }"
            >
              {{ c.content }}
            </div>
            <div
              v-if="c.keywords && c.keywords.length > 0"
              class="kh-chunk-tags"
            >
              <span
                v-for="k in c.keywords.slice(0, 6)"
                :key="k"
                class="kh-chunk-keyword"
              >
                {{ k }}
              </span>
            </div>
          </div>
        </div>

        <!-- Pagination footer -->
        <div v-if="total > 0" class="kh-chunks-foot">
          <div class="kh-chunks-pager">
            <button
              class="kh-pager-btn"
              :disabled="page <= 1"
              @click="goPage(page - 1)"
            >
              ‹
            </button>
            <span class="kh-pager-page">{{ page }} / {{ totalPages }}</span>
            <button
              class="kh-pager-btn"
              :disabled="page >= totalPages"
              @click="goPage(page + 1)"
            >
              ›
            </button>
          </div>
          <div class="kh-pager-info">共 {{ total }} 条</div>
          <div class="kh-pager-sizes">
            <span class="kh-pager-sizes-label">每页</span>
            <button
              v-for="s in [10, 20, 50]"
              :key="s"
              class="kh-pager-size"
              :class="{ 'kh-pager-size-active': pageSize === s }"
              @click="changePageSize(s)"
            >
              {{ s }}
            </button>
          </div>
        </div>
      </div>

      <!-- RIGHT: metadata pane -->
      <div v-if="showMeta" class="kh-chunks-meta">
        <div class="kh-meta-header">
          <div class="kh-meta-title-row">
            <span class="kh-meta-title">元数据</span>
            <button class="kh-meta-collapse" @click="showMeta = false">›</button>
          </div>
          <div class="kh-meta-sub">
            元数据能对文档进行结构化描述、辅助过滤检索、支持精细化数据管理。
          </div>
          <button class="kh-meta-btn">开放智能标签</button>
        </div>

        <div class="kh-meta-group">
          <div class="kh-meta-group-title">文档信息</div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">原始文件名</span>
            <span class="kh-meta-val">{{ metadata.fileName ?? '-' }}</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">上传日期</span>
            <span class="kh-meta-val">{{ metadata.uploadedAt ?? '-' }}</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">最后更新</span>
            <span class="kh-meta-val">{{ metadata.embeddedAt ?? metadata.parsedAt ?? '-' }}</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">数据来源</span>
            <span class="kh-meta-val">{{ metadata.kind ?? '文本上传' }}</span>
          </div>
        </div>

        <div class="kh-meta-group">
          <div class="kh-meta-group-title">技术参数</div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">分段模式</span>
            <span class="kh-meta-val">{{ metadata.chunkMode ?? '自定义' }}</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">分段最大长度</span>
            <span class="kh-meta-val">{{ metadata.chunkMaxSize ?? '-' }}</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">字符数</span>
            <span class="kh-meta-val">{{ fmtNum(metadata.totalChars) }} characters</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">平均分段大小</span>
            <span class="kh-meta-val">
              {{ metadata.totalChunks }} paragraphs
            </span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">平均嵌入时间</span>
            <span class="kh-meta-val">{{ metadata.avgEmbedMs ?? '-' }} ms</span>
          </div>
          <div class="kh-meta-row">
            <span class="kh-meta-key">嵌入耗费</span>
            <span class="kh-meta-val">
              {{ fmtNum(metadata.totalTokens) }} tokens
            </span>
          </div>
        </div>
      </div>
      <button
        v-else
        class="kh-meta-toggle"
        @click="showMeta = true"
      >
        ‹ 元数据
      </button>
    </div>
  </div>
</template>

<style scoped>
.kh-chunks {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.kh-chunks-status {
  font-size: 12px;
}

.kh-btn {
  padding: 5px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}
.kh-btn-secondary:hover {
  background: #f8fafc;
}
.kh-btn-primary {
  border-color: transparent;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  box-shadow: 0 2px 4px rgba(79, 70, 229, 0.25);
}
.kh-btn-primary:hover {
  transform: translateY(-1px);
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
/* Body — starts flush with the drawer topbar (no wasted whitespace on top). */
.kh-chunks-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
  padding: 8px 20px 16px;
  overflow: hidden;
}

/* Main pane */
.kh-chunks-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}
.kh-chunks-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.kh-chunks-count {
  font-size: 12px;
  color: #64748b;
}
.kh-chunks-toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.kh-doctab-select {
  padding: 5px 22px 5px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  outline: none;
  cursor: pointer;
}
.kh-doctab-select:focus {
  border-color: #6366f1;
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
  color: #94a3b8;
}
.kh-doctab-search-input {
  padding: 5px 10px 5px 26px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  outline: none;
  width: 160px;
}
.kh-doctab-search-input:focus {
  border-color: #6366f1;
}
.kh-chunks-empty {
  padding: 40px 20px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}

.kh-chunk-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.kh-chunk-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.kh-chunk-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.06);
}
.kh-chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 8px;
}
.kh-chunk-tag {
  padding: 2px 6px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 4px;
  font-weight: 600;
  font-size: 10px;
}
.kh-chunk-meta {
  color: #94a3b8;
  font-size: 10px;
}
.kh-chunk-flex {
  flex: 1;
}
.kh-chunk-badge {
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 10px;
}
.kh-chunk-badge-on {
  background: #dcfce7;
  color: #059669;
}
.kh-chunk-badge-off {
  background: #fef2f2;
  color: #dc2626;
}
.kh-chunk-body {
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 5.4em;
  overflow: hidden;
  position: relative;
}
.kh-chunk-body:not(.kh-chunk-body-open)::after {
  content: '…';
  position: absolute;
  right: 0;
  bottom: 0;
  padding-left: 12px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), #fff 40%);
  color: #94a3b8;
}
.kh-chunk-body-open {
  max-height: none;
  overflow: visible;
}
.kh-chunk-expand-inline {
  padding: 0 6px;
  border: none;
  background: transparent;
  font-size: 10px;
  color: #4338ca;
  cursor: pointer;
  border-radius: 4px;
  line-height: 1.4;
}
.kh-chunk-expand-inline:hover {
  background: #eef2ff;
}

/* Text-style bulk actions in the toolbar row */
.kh-chunks-link {
  padding: 2px 4px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: #4338ca;
  cursor: pointer;
  border-radius: 4px;
}
.kh-chunks-link:hover {
  background: #eef2ff;
  text-decoration: underline;
}
.kh-chunks-link-sep {
  color: #cbd5e1;
  font-size: 12px;
  user-select: none;
}
.kh-chunk-tags {
  margin-top: 8px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.kh-chunk-keyword {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 10px;
}

/* Meta pane */
.kh-chunks-meta {
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
  overflow-y: auto;
  min-width: 0;
}
.kh-meta-header {
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 12px;
}
.kh-meta-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kh-meta-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.kh-meta-collapse {
  padding: 0 6px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
}
.kh-meta-sub {
  margin-top: 4px;
  font-size: 10px;
  color: #94a3b8;
  line-height: 1.4;
}
.kh-meta-btn {
  margin-top: 6px;
  padding: 4px 10px;
  border: 1px solid #6366f1;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  cursor: pointer;
}

.kh-meta-group {
  margin-top: 10px;
}
.kh-meta-group-title {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #475569;
}
.kh-meta-row {
  display: grid;
  grid-template-columns: 90px 1fr;
  padding: 4px 0;
  font-size: 11px;
}
.kh-meta-key {
  color: #94a3b8;
}
.kh-meta-val {
  color: #334155;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kh-meta-toggle {
  align-self: flex-start;
  padding: 4px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
  writing-mode: vertical-rl;
  text-orientation: mixed;
}

/* Pagination footer */
.kh-chunks-foot {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 10px 4px 0;
  border-top: 1px solid #f1f5f9;
  margin-top: 4px;
}
.kh-chunks-pager {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}
.kh-pager-btn {
  padding: 2px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  color: #64748b;
}
.kh-pager-btn:hover:not(:disabled) {
  background: #f1f5f9;
}
.kh-pager-btn:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
}
.kh-pager-page {
  min-width: 60px;
  text-align: center;
}
.kh-pager-info {
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}
.kh-pager-sizes {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #94a3b8;
}
.kh-pager-sizes-label {
  margin-right: 4px;
}
.kh-pager-size {
  padding: 2px 8px;
  border: none;
  background: transparent;
  font-size: 11px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 4px;
}
.kh-pager-size:hover {
  background: #f1f5f9;
  color: #475569;
}
.kh-pager-size-active {
  background: #4f46e5;
  color: #fff;
}
</style>
