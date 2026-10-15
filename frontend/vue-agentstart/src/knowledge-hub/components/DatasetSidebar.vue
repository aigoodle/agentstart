<script setup lang="ts">
import { Popover, PopoverTrigger, PopoverContent } from '../../ui/shadcn/popover';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

/**
 * DatasetSidebar — left rail shared across the four dataset-detail screens
 * (screenshots 4-7). Dataset icon + name + description at the top, 4 nav
 * items in the middle, doc/word count footer + Access API link at the bottom.
 *
 * The identity block is a hoverable card; a top-right `⋯` trigger opens a
 * basic-info popover (Dify-parity with AppDesignDrawer's info card) that
 * emits `edit-info` / `duplicate` / `export` / `delete` — actual mutation is
 * delegated to the host so the sidebar stays presentational.
 */
import { computed, ref } from 'vue';

import type { DatasetTab } from '../types';

interface Props {
  dataset: {
    id: string;
    name: string;
    description?: string;
    icon?: string;
    iconBg?: string;
    documentCount?: number;
    segmentCount?: number;
    /** Optional — displayed as a meta row in the popover if provided. */
    createdAt?: string | number | Date;
    /** Optional — indexing technique badge (HIGH_QUALITY / ECONOMY). */
    indexingTechnique?: string;
  };
  active: DatasetTab;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'nav', tab: DatasetTab): void;
  (e: 'copy-api'): void;
  /** Open the "编辑基本信息" surface — host owns the actual edit modal. */
  (e: 'edit-info', payload: { datasetId: string }): void;
  (e: 'duplicate', payload: { datasetId: string }): void;
  (e: 'export', payload: { datasetId: string }): void;
  (e: 'delete', payload: { datasetId: string }): void;
}>();

const NAV: Array<{ id: DatasetTab; icon: string; label: string }> = [
  { id: 'documents', icon: '📄', label: '文档' },
  { id: 'operations', icon: '◫', label: 'RAG 运行' },
  { id: 'settings', icon: '⚙', label: '设置' },
];

const icon = computed(() => props.dataset.icon || '📙');
const iconBg = computed(() => props.dataset.iconBg || '#FFEAD5');

const techniqueLabel = computed(() => {
  const t = props.dataset.indexingTechnique;
  if (!t) return null;
  return t === 'ECONOMY' ? '经济' : '高质量';
});

const createdAgo = computed(() => {
  const ts = props.dataset.createdAt;
  if (!ts) return null;
  const d = new Date(ts);
  const t = d.getTime();
  if (Number.isNaN(t)) return String(ts);
  const diff = Date.now() - t;
  if (diff < 60_000) return '刚刚';
  const mins = Math.floor(diff / 60_000);
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(diff / 3_600_000);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(diff / 86_400_000);
  if (days < 30) return `${days} 天前`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} 个月前`;
  return `${Math.floor(months / 12)} 年前`;
});

// ---------------------------------------------------------------------------
// Basic-info popover — same UX as AppDesignDrawer's info card. Trigger sits
// in the top-right of the head card and only appears on hover / when open.
// ---------------------------------------------------------------------------
const infoOpen = ref(false);
const headRef = ref<HTMLElement | null>(null);

function closeInfo() {
  infoOpen.value = false;
}

function onEdit() {
  emit('edit-info', { datasetId: props.dataset.id });
  closeInfo();
}
function onDuplicate() {
  emit('duplicate', { datasetId: props.dataset.id });
  closeInfo();
}
function onExport() {
  emit('export', { datasetId: props.dataset.id });
  closeInfo();
}
function onDelete() {
  emit('delete', { datasetId: props.dataset.id });
  closeInfo();
}

</script>

<template>
  <div class="kh-side">
    <!-- header: dataset identity — hoverable card, ⋯ opens basic info popover -->
    <div
      ref="headRef"
      class="kh-side-head"
      :class="{ 'kh-side-head-active': infoOpen }"
    >
      <Popover v-model:open="infoOpen"><PopoverTrigger as-child><AsShadcnButton variant="ghost"
        type="button"
        class="kh-side-trigger"
        :class="{ 'kh-side-trigger-open': infoOpen }"
        :title="infoOpen ? '收起基本信息' : '展开基本信息'"
      >
        ⋯
      </AsShadcnButton></PopoverTrigger><PopoverContent side="right" align="start" :side-offset="12" class="kh-info-panel as-info-popover" @click.stop>
        <!-- Header: icon + name + type chip -->
        <div class="kh-ip-head">
          <div class="kh-ip-icon" :style="{ background: iconBg }">
            {{ icon }}
          </div>
          <div class="kh-ip-title-wrap">
            <div class="kh-ip-title">{{ dataset.name }}</div>
            <div class="kh-ip-chips">
              <span class="kh-ip-chip">知识库</span>
              <span v-if="techniqueLabel" class="kh-ip-chip kh-ip-chip-muted">
                {{ techniqueLabel }}
              </span>
            </div>
          </div>
        </div>

        <!-- Quick action tiles -->
        <div class="kh-ip-actions">
          <AsShadcnButton variant="ghost" class="kh-ip-action" @click="onEdit">
            <span class="kh-ip-action-icon">✎</span>
            <span>编辑信息</span>
          </AsShadcnButton>
          <AsShadcnButton variant="ghost" class="kh-ip-action" @click="onDuplicate">
            <span class="kh-ip-action-icon">⧉</span>
            <span>复制</span>
          </AsShadcnButton>
          <AsShadcnButton variant="ghost" class="kh-ip-action" @click="onExport">
            <span class="kh-ip-action-icon">⇩</span>
            <span>导出</span>
          </AsShadcnButton>
          <AsShadcnButton variant="ghost" class="kh-ip-action kh-ip-action-danger" @click="onDelete">
            <span class="kh-ip-action-icon">🗑</span>
            <span>删除</span>
          </AsShadcnButton>
        </div>

        <!-- Description section -->
        <section v-if="dataset.description" class="kh-ip-section">
          <div class="kh-ip-section-head">
            <span class="kh-ip-section-icon kh-ip-section-icon-desc">📝</span>
            <span class="kh-ip-section-title">描述</span>
          </div>
          <div class="kh-ip-desc-text">{{ dataset.description }}</div>
        </section>

        <!-- Stats section -->
        <section class="kh-ip-section">
          <div class="kh-ip-section-head">
            <span class="kh-ip-section-icon kh-ip-section-icon-stat">📊</span>
            <span class="kh-ip-section-title">统计</span>
          </div>
          <div class="kh-ip-stats">
            <div class="kh-ip-stat">
              <div class="kh-ip-stat-val">{{ dataset.documentCount ?? 0 }}</div>
              <div class="kh-ip-stat-lbl">文档</div>
            </div>
            <div class="kh-ip-stat">
              <div class="kh-ip-stat-val">{{ dataset.segmentCount ?? 0 }}</div>
              <div class="kh-ip-stat-lbl">关联应用</div>
            </div>
            <div v-if="createdAgo" class="kh-ip-stat">
              <div class="kh-ip-stat-val">{{ createdAgo }}</div>
              <div class="kh-ip-stat-lbl">创建于</div>
            </div>
          </div>
        </section>

        <!-- ID / API section -->
        <section class="kh-ip-section">
          <div class="kh-ip-section-head">
            <span class="kh-ip-section-icon kh-ip-section-icon-api">🔌</span>
            <span class="kh-ip-section-title">API</span>
          </div>
          <div class="kh-ip-section-label">数据集 ID</div>
          <div class="kh-ip-url-row">
            <span class="kh-ip-url" :title="dataset.id">{{ dataset.id }}</span>
            <AsShadcnButton variant="ghost"
              class="kh-ip-icon-btn"
              title="复制 API 基址"
              @click="emit('copy-api')"
            >
              ⎘
            </AsShadcnButton>
          </div>
          <div class="kh-ip-links">
            <AsShadcnButton variant="ghost" class="kh-ip-link" @click="emit('copy-api')">
              🔑 API 密钥
            </AsShadcnButton>
            <AsShadcnButton variant="ghost" class="kh-ip-link">📄 查看 API 文档</AsShadcnButton>
          </div>
        </section>
      </PopoverContent></Popover>

      <div class="kh-side-icon" :style="{ background: iconBg }">
        {{ icon }}
      </div>
      <div class="kh-side-name" :title="dataset.name">{{ dataset.name }}</div>
      <div v-if="dataset.description" class="kh-side-desc" :title="dataset.description">
        {{ dataset.description }}
      </div>

      <!-- Basic-info popover -->

    </div>

    <!-- nav items -->
    <div class="kh-side-nav">
      <AsShadcnButton variant="ghost"
        v-for="n in NAV"
        :key="n.id"
        class="kh-side-nav-item justify-start text-left"
        :class="{ 'kh-side-nav-active': (active === 'recall' ? 'operations' : active) === n.id }"
        @click="emit('nav', n.id)"
      >
        <span class="kh-side-nav-icon">{{ n.icon }}</span>
        <span>{{ n.label }}</span>
      </AsShadcnButton>
    </div>

    <!-- footer: counts + api -->
    <div class="kh-side-foot">
      <div class="kh-side-counts">
        <div class="kh-side-count">
          <div class="kh-side-count-val">{{ dataset.documentCount ?? 0 }}</div>
          <div class="kh-side-count-lbl">文档</div>
        </div>
        <div class="kh-side-count">
          <div class="kh-side-count-val">{{ dataset.segmentCount ?? 0 }}</div>
          <div class="kh-side-count-lbl">片段</div>
        </div>
      </div>
      <AsShadcnButton variant="ghost" class="kh-side-api" @click="emit('copy-api')">
        ⌘ 访问 API
      </AsShadcnButton>
    </div>
  </div>
</template>

<style scoped>
.kh-side {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 10px 10px 16px;
  background: var(--kh-color-surface);
  border-right: 1px solid var(--kh-color-divider);
  height: 100%;
  /* Note: overflow is intentionally visible so the basic-info popover can
   * escape to the right. Scrolling for a long nav is handled by
   * `.kh-side-nav` which owns its own overflow-y. */
  overflow: visible;
}

/* ---- Header — hoverable card with a top-right ⋯ trigger ---- */
.kh-side-head {
  position: relative;
  margin-bottom: 6px;
  padding: 14px 12px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.kh-side-head:hover {
  background: var(--kh-color-surface-sunken);
  border-color: var(--kh-color-border);
}
.kh-side-head:hover .kh-side-trigger {
  opacity: 1;
}
.kh-side-head-active {
  background: var(--kh-color-primary-soft);
  border-color: color-mix(in srgb, var(--kh-color-primary) 38%, transparent);
  box-shadow: 0 1px 2px color-mix(in srgb, var(--kh-color-primary) 8%, transparent);
}
.kh-side-head-active .kh-side-trigger {
  opacity: 1;
  color: var(--kh-color-primary-strong);
  background: color-mix(in srgb, var(--kh-color-primary) 20%, transparent);
}
.kh-side-trigger {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--kh-color-text-muted);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease;
  z-index: 2;
}
.kh-side-trigger:hover {
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary-strong);
}
.kh-side-trigger-open {
  opacity: 1;
}

.kh-side-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 26px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}
.kh-side-name {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kh-side-desc {
  margin-top: 4px;
  font-size: 11px;
  color: var(--kh-color-text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---- Basic-info popover (mirrors AppDesignDrawer's brand panel) ---- */
.kh-info-panel { display: flex; flex-direction: column; gap: 12px; text-align: left; }
@keyframes kh-info-fade {
  from {
    opacity: 0;
    transform: translateX(-4px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* Header inside panel */
.kh-ip-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 2px 2px 12px;
  border-bottom: 1px solid var(--kh-color-divider);
}
.kh-ip-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}
.kh-ip-title-wrap {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kh-ip-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kh-ip-chips {
  display: inline-flex;
  gap: 4px;
  flex-wrap: wrap;
}
.kh-ip-chip {
  padding: 1px 7px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--kh-color-primary-strong);
  background: var(--kh-color-primary-soft);
  border-radius: 4px;
  line-height: 1.5;
}
.kh-ip-chip-muted {
  color: var(--kh-color-text-tertiary);
  background: var(--kh-color-surface-hover);
  letter-spacing: 0;
}

/* Quick action tiles */
.kh-ip-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.kh-ip-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 4px;
  border: 1px solid var(--kh-color-divider);
  background: var(--kh-color-surface-sunken);
  border-radius: 10px;
  color: var(--kh-color-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}
.kh-ip-action:hover {
  border-color: color-mix(in srgb, var(--kh-color-primary) 38%, transparent);
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary-strong);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px color-mix(in srgb, var(--kh-color-primary) 10%, transparent);
}
.kh-ip-action-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary-strong);
  font-size: 12px;
  line-height: 1;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.kh-ip-action:hover .kh-ip-action-icon {
  background: var(--kh-color-primary-strong);
  color: var(--kh-color-primary-contrast);
}
.kh-ip-action-danger .kh-ip-action-icon {
  background: var(--kh-color-danger-soft);
  color: var(--kh-color-danger);
}
.kh-ip-action-danger:hover {
  border-color: color-mix(in srgb, var(--kh-color-danger) 28%, transparent);
  background: var(--kh-color-danger-soft);
  color: var(--kh-color-danger);
}
.kh-ip-action-danger:hover .kh-ip-action-icon {
  background: var(--kh-color-danger);
  color: #fff;
}

/* Section cards */
.kh-ip-section {
  padding: 12px;
  background: var(--kh-color-surface-sunken);
  border: 1px solid var(--kh-color-divider);
  border-radius: 10px;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.kh-ip-section:hover {
  background: var(--kh-color-surface-sunken);
  border-color: var(--kh-color-border);
}
.kh-ip-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.kh-ip-section-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 12px;
  flex-shrink: 0;
}
.kh-ip-section-icon-desc {
  background: var(--kh-color-warning-soft);
  color: var(--kh-color-warning);
}
.kh-ip-section-icon-stat {
  background: var(--kh-color-info-soft);
  color: var(--kh-color-info);
}
.kh-ip-section-icon-api {
  background: color-mix(in srgb, var(--kh-color-primary) 20%, transparent);
  color: var(--kh-color-primary-strong);
}
.kh-ip-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-ip-section-label {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--kh-color-text-tertiary);
}
.kh-ip-desc-text {
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Stats grid */
.kh-ip-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.kh-ip-stat {
  padding: 8px 4px;
  text-align: center;
  background: var(--kh-color-surface);
  border: 1px solid var(--kh-color-divider);
  border-radius: 8px;
}
.kh-ip-stat-val {
  font-size: 14px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
  line-height: 1.2;
}
.kh-ip-stat-lbl {
  margin-top: 2px;
  font-size: 10px;
  color: var(--kh-color-text-muted);
}

/* URL row + link pills */
.kh-ip-url-row {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 5px 6px 5px 10px;
  background: var(--kh-color-surface);
  border: 1px solid var(--kh-color-border);
  border-radius: 8px;
  margin-bottom: 10px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.kh-ip-url-row:hover {
  border-color: color-mix(in srgb, var(--kh-color-primary) 38%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--kh-color-primary) 6%, transparent);
}
.kh-ip-url {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--kh-color-text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kh-ip-icon-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--kh-color-text-muted);
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;
}
.kh-ip-icon-btn:hover {
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary-strong);
}
.kh-ip-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kh-ip-link {
  padding: 4px 10px;
  border: 1px solid var(--kh-color-border);
  background: var(--kh-color-surface);
  border-radius: 999px;
  color: var(--kh-color-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}
.kh-ip-link:hover {
  background: var(--kh-color-primary-soft);
  border-color: color-mix(in srgb, var(--kh-color-primary) 38%, transparent);
  color: var(--kh-color-primary-strong);
}

/* Nav */
.kh-side-nav {
  padding: 12px 4px 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-top: 1px solid var(--kh-color-divider);
}
.kh-side-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.kh-side-nav-item:hover {
  background: var(--kh-color-surface-sunken);
}
.kh-side-nav-active {
  background: var(--kh-color-primary-soft);
  color: var(--kh-color-primary-strong);
  font-weight: 600;
}
.kh-side-nav-icon {
  font-size: 15px;
  width: 18px;
  text-align: center;
}

/* Footer */
.kh-side-foot {
  padding-top: 14px;
  border-top: 1px solid var(--kh-color-divider);
}
.kh-side-counts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
}
.kh-side-count {
  padding: 6px 4px;
  text-align: center;
}
.kh-side-count-val {
  font-size: 15px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.kh-side-count-lbl {
  margin-top: 2px;
  font-size: 10px;
  color: var(--kh-color-text-muted);
}
.kh-side-api {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--kh-color-border);
  border-radius: 6px;
  background: var(--kh-color-surface);
  font-size: 12px;
  color: var(--kh-color-text-secondary);
  cursor: pointer;
  transition: background 0.15s;
}
.kh-side-api:hover {
  background: var(--kh-color-surface-sunken);
}
</style>

<style scoped>
.kh-side-nav-item { justify-content: flex-start; text-align: left; }
</style>
