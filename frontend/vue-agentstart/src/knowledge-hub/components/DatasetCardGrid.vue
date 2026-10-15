<script setup lang="ts">
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator } from '../../ui/shadcn/dropdown-menu';

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
import { computed, ref } from 'vue';

import { Button, Card, Tag } from '../../ui';
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

</script>

<template>
  <div class="kh-cards">
    <!-- +新建 -->
    <Card
      v-if="showCreateCard"
      variant="create"
      title="新建知识库"
      subtitle="导入文档 / 上传文件 / 空知识库"
      interactive
      :disabled="!!disabledReason"
      @click="emit('create')"
    ></Card>

    <Card variant="management"
      v-for="d in filtered"
      :key="d.id"
      class="kh-card"
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
        <Tag :color="d.indexingTechnique === 'ECONOMY' ? undefined : 'blue'">
          {{ d.indexingTechnique === 'ECONOMY' ? '经济' : '高质量' }}
        </Tag>
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

      <template #badge>
        <DropdownMenu :open="openMenuId === d.id" @update:open="openMenuId = $event ? d.id : null">
          <DropdownMenuTrigger as-child>
            <Button type="text" size="small" :aria-label="`${d.name} 更多操作`" @click.stop>⋯</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" @click.stop>
            <DropdownMenuItem @select="onMenuOpen(d)">📂 {{ t('card.open') }}</DropdownMenuItem>
            <DropdownMenuItem @select="onMenuSettings(d)">⚙ {{ t('card.settings') }}</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem class="text-destructive" @select="onMenuDelete(d)">🗑 {{ t('card.delete') }}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.kh-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.kh-card-icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 8px; font-size: 22px; }
.kh-card-foot-item { display: inline-flex; align-items: center; gap: 4px; }
</style>
