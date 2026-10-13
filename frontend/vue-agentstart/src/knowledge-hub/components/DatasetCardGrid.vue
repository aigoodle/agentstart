<script setup lang="ts">
/**
 * DatasetCardGrid — Dify-parity knowledge-base list card grid.
 *
 * Emits `create` and `open(dataset)` — the host renders whichever surface it
 * wants for those (KnowledgeHubApp uses the wizard + drawer). Header slot
 * carries the search + filter row so hosts can extend if needed.
 *
 * Delete lives inside a "⋯" popup menu in the card footer (not a top-right X)
 * — destructive actions should require intent, not a mis-hover.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { Card } from '../../ui';
import { useKhI18n } from '../i18n';
import type { DatasetCardItem } from '../types';

interface Props {
  datasets: DatasetCardItem[];
  /** Keyword filter (v-model'd from the parent search input). */
  keyword?: string;
  /** Show the leading "+新建" placeholder card. */
  showCreateCard?: boolean;
  disabledReason?: string;
}

const props = withDefaults(defineProps<Props>(), {
  keyword: '',
  showCreateCard: true,
});

const emit = defineEmits<{
  (e: 'create'): void;
  (e: 'open', dataset: DatasetCardItem): void;
  (e: 'settings', dataset: DatasetCardItem): void;
  (e: 'delete', dataset: DatasetCardItem): void;
}>();

const { t } = useKhI18n();

const ICONS = ['📙', '📗', '📘', '📕', '📓', '📔', '📒', '🗂', '📚', '🧭'];
// Background colors now use CSS variables for automatic dark mode support
const BG_COUNT = 8;
function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = Math.trunc((h << 5) - h + s.charCodeAt(i));
  return Math.abs(h);
}
function iconOf(d: DatasetCardItem) {
  return ICONS[hashCode(d.id || d.name) % ICONS.length];
}
function bgOf(d: DatasetCardItem) {
  const idx = (hashCode((d.id || d.name) + '.bg') % BG_COUNT) + 1;
  return `var(--kh-card-icon-bg-${idx})`;
}
function fromNow(iso?: string): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86_400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86_400 * 30) return `${Math.floor(diff / 86_400)} 天前`;
  return new Date(iso).toLocaleDateString();
}

const filtered = computed(() => {
  const q = (props.keyword ?? '').trim().toLowerCase();
  if (!q) return props.datasets;
  return props.datasets.filter(
    (d) =>
      d.name.toLowerCase().includes(q) ||
      (d.description ?? '').toLowerCase().includes(q),
  );
});

// -------- popup menu state
const openMenuId = ref<string | null>(null);
function toggleMenu(id: string) {
  openMenuId.value = openMenuId.value === id ? null : id;
}
function closeMenu() {
  openMenuId.value = null;
}
function onMenuOpen(d: DatasetCardItem) {
  closeMenu();
  emit('open', d);
}
function onMenuSettings(d: DatasetCardItem) {
  closeMenu();
  emit('settings', d);
}
function onMenuDelete(d: DatasetCardItem) {
  closeMenu();
  emit('delete', d);
}

// Close on any outside click / Esc — the menu itself stops propagation.
function onDocClick() {
  if (openMenuId.value !== null) closeMenu();
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && openMenuId.value !== null) closeMenu();
}
onMounted(() => {
  document.addEventListener('click', onDocClick);
  document.addEventListener('keydown', onKey);
});
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick);
  document.removeEventListener('keydown', onKey);
});
</script>

<template>
  <div class="kh-cards">
    <!-- +新建 -->
    <Card
      v-if="showCreateCard"
      variant="management"
      class="kh-create-card"
      interactive
      :disabled="!!disabledReason"
      :title="disabledReason"
      @click="emit('create')"
    >
      <div class="as-create-card__inner">
        <div class="as-create-card__plus">+</div>
        <div class="as-create-card__title">新建知识库</div>
        <div class="as-create-card__subtitle">导入文档 / 上传文件 / 空知识库</div>
      </div>
    </Card>

    <Card variant="management"
      v-for="d in filtered"
      :key="d.id"
      class="kh-card"
      :class="{ 'kh-card-active': openMenuId === d.id }"
      :title="d.name"
      :description="d.description || '暂无描述'"
      @click="emit('open', d)"
    >
      <template #icon>
        <div class="kh-card-icon" :style="{ background: bgOf(d) }">
          {{ iconOf(d) }}
        </div>
      </template>

      <template #subtitle>
        <span
          class="kh-card-tag"
          :data-tone="d.indexingTechnique === 'ECONOMY' ? 'default' : 'blue'"
        >
          {{ d.indexingTechnique === 'ECONOMY' ? '经济' : '高质量' }}
        </span>
        <span>· {{ fromNow(d.updatedAt) || '刚刚' }}</span>
      </template>

      <template #meta>
        <span class="kh-card-foot-item" :title="`${d.documentCount ?? 0} 个文档`">
          📄 {{ d.documentCount ?? 0 }}
        </span>
        <span class="kh-card-foot-item" :title="`${d.segmentCount ?? 0} 个片段`">
          🧩 {{ d.segmentCount ?? 0 }}
        </span>
      </template>

      <template #actions>
        <div class="kh-card-menu-wrap" @click.stop>
          <button
            type="button"
            class="kh-card-more"
            :class="{ 'is-open': openMenuId === d.id }"
            :aria-haspopup="'menu'"
            :aria-expanded="openMenuId === d.id"
            :title="t('card.more')"
            @click="toggleMenu(d.id)"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <circle cx="3" cy="8" r="1.4" fill="currentColor" />
              <circle cx="8" cy="8" r="1.4" fill="currentColor" />
              <circle cx="13" cy="8" r="1.4" fill="currentColor" />
            </svg>
          </button>
          <div
            v-if="openMenuId === d.id"
            class="kh-card-menu"
            role="menu"
          >
            <button
              type="button"
              class="kh-card-menu-item"
              role="menuitem"
              @click="onMenuOpen(d)"
            >
              <span class="kh-card-menu-ico">📂</span>{{ t('card.open') }}
            </button>
            <button
              type="button"
              class="kh-card-menu-item"
              role="menuitem"
              @click="onMenuSettings(d)"
            >
              <span class="kh-card-menu-ico">⚙️</span>{{ t('card.settings') }}
            </button>
            <div class="kh-card-menu-divider" />
            <button
              type="button"
              class="kh-card-menu-item kh-card-menu-item-danger"
              role="menuitem"
              @click="onMenuDelete(d)"
            >
              <span class="kh-card-menu-ico">🗑</span>{{ t('card.delete') }}
            </button>
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.kh-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--kh-space-4);
}
.kh-card {
  position: relative;
  /* right padding reserves space for the corner "⋯" menu overlay
     so the title / subtitle truncate before running under it. */
  padding-right: 36px;
  cursor: pointer;
  /* menu opens upward from the footer; its top edge escapes the padding
     box but the card's 16px top padding keeps it inside the border box. */
  overflow: hidden;
}
/* Card's footer (#meta + #actions) must sit at the bottom of the
   fixed-height card, with the meta portion taking the role of the old
   .kh-card-foot and the actions portion hosting the "⋯" menu in-flow so
   the menu popup anchors to it correctly. */
.kh-card:deep(.as-management-card__footer) {
  font-size: var(--kh-fs-md);
  color: var(--kh-color-text-tertiary);
}
/* Actions slot keeps the menu-wrap in-flow so the popup anchors to it. */
.kh-card:deep(.as-management-card__actions) {
  display: inline-flex;
  margin: 0;
  padding: 0;
  border: 0;
  flex: none;
}
.kh-card-active {
  border-color: color-mix(in srgb, var(--as-primary) 60%, var(--as-card-border));
  box-shadow: var(--as-card-shadow-hover);
}
.kh-card-head {
  display: flex;
  gap: var(--kh-space-3);
  align-items: flex-start;
}
.kh-card-icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--kh-radius-md);
  font-size: 22px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}
:global(.dark) .kh-card-icon {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}
.kh-card-title-wrap {
  min-width: 0;
  flex: 1;
}
.kh-card-title {
  font-size: var(--kh-fs-2xl);
  font-weight: 600;
  color: var(--kh-color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: 0.1px;
}
.kh-card-meta {
  margin-top: 4px;
  font-size: var(--kh-fs-md);
  color: var(--kh-color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.kh-card-tag {
  padding: 1px 6px;
  border-radius: var(--kh-radius-xs);
  background: var(--kh-color-info-soft);
  color: var(--kh-color-info);
  font-size: var(--kh-fs-xs);
}
.kh-card-tag[data-tone='default'] {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.kh-card-desc {
  margin-top: 10px;
  font-size: var(--kh-fs-lg);
  color: var(--kh-color-text-tertiary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kh-card-spacer {
  flex: 1;
}
.kh-card-foot {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  font-size: var(--kh-fs-md);
  color: var(--kh-color-text-tertiary);
  border-top: 1px solid var(--kh-color-divider);
  padding-top: var(--kh-space-2);
}
.kh-card-foot-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.kh-card-foot-fill {
  flex: 1;
}

/* "⋯" menu trigger — sits in the footer, right-aligned. */
.kh-card-menu-wrap {
  position: relative;
  display: inline-flex;
}
.kh-card-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: var(--kh-radius-sm);
  background: transparent;
  color: var(--kh-color-text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition:
    background var(--kh-tx-fast),
    color var(--kh-tx-fast),
    opacity var(--kh-tx-fast);
}
.kh-card:hover .kh-card-more,
.kh-card-more.is-open {
  opacity: 1;
}
.kh-card-more:hover,
.kh-card-more.is-open {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-primary);
}

/* Popup menu — anchored to the ⋯ button, opens upward so it doesn't clip. */
.kh-card-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 6px);
  min-width: 148px;
  padding: 4px;
  background: var(--kh-color-surface-raised);
  border: 1px solid var(--kh-color-border);
  border-radius: var(--kh-radius-md);
  box-shadow: var(--kh-shadow-lg);
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 1px;
  animation: kh-menu-in 0.12s ease-out;
}
@keyframes kh-menu-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.kh-card-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: transparent;
  border: none;
  border-radius: var(--kh-radius-sm);
  font-size: var(--kh-fs-lg);
  color: var(--kh-color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: background var(--kh-tx-fast), color var(--kh-tx-fast);
}
.kh-card-menu-item:hover {
  background: var(--kh-color-surface-hover);
}
.kh-card-menu-ico {
  font-size: 14px;
  line-height: 1;
  width: 16px;
  display: inline-flex;
  justify-content: center;
}
.kh-card-menu-item-danger {
  color: var(--kh-color-danger);
}
.kh-card-menu-item-danger:hover {
  background: var(--kh-color-danger-soft);
}
.kh-card-menu-divider {
  height: 1px;
  margin: 4px 2px;
  background: var(--kh-color-divider);
}
</style>
