<script setup lang="ts">
import { Button as AsShadcnButton } from '../../ui/shadcn/button';
import { Input as AsShadcnInput } from '../../ui/shadcn/input';

/**
 * GroupedModelSelect — brand-icon dropdown that groups model options by
 * provider, with a search box and collapsible provider sections.
 *
 * The knowledge-hub Embedding-model picker was the first user of this pattern;
 * this component is the extracted, reusable form so 系统默认模型 (rerank / TTS
 * / speech-to-text rows), agent-studio model pickers, and anything else that
 * wants "brand-grouped model dropdown" can share the same UX.
 *
 * Contract:
 *   options: flat list of { id, label, providerName?, providerLabel?, isDefault? }
 *   modelValue: currently-selected id (v-model target)
 *   emit 'update:modelValue' + 'change' when the user picks an option
 *
 * Options without a `providerName` land in a single "其他" bucket so legacy
 * lists (that never carried provider metadata) still render.
 */
import { computed, ref } from 'vue';

import { Popover, PopoverTrigger, PopoverContent } from '../../ui/shadcn/popover';

import ProviderIcon from './ProviderIcon.vue';

export interface GroupedModelSelectOption {
  id: string;
  label: string;
  /** Provider name used to look up the brand icon + group by. */
  providerName?: string;
  /** Human-readable provider label. Falls back to providerName then "其他". */
  providerLabel?: string;
  /**
   * When true, the option is rendered with a "默认" badge. Purely decorative —
   * auto-selection behaviour is the caller's job (via modelValue seeding).
   */
  isDefault?: boolean;
}

interface Props {
  modelValue?: null | string;
  options: GroupedModelSelectOption[];
  /** Placeholder text when nothing is selected. */
  placeholder?: string;
  /** Fired into the search box's placeholder attribute. */
  searchPlaceholder?: string;
  /** Copy for the empty state when there are zero options. */
  emptyText?: string;
  /** Sub-hint under the empty-state icon. */
  emptyHint?: string;
  /** Disables the trigger. */
  disabled?: boolean;
  /**
   * Fallback icon (single char) rendered when the selected option has no
   * provider — e.g. legacy datasets whose embedding rows don't carry provider
   * metadata. Default: "⌬".
   */
  fallbackIcon?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择模型',
  searchPlaceholder: '搜索模型 / 供应商',
  emptyText: '暂无可用模型',
  emptyHint: '',
  disabled: false,
  fallbackIcon: '⌬',
});

const emit = defineEmits<{
  (e: 'change', v: null | string): void;
  (e: 'update:modelValue', v: null | string): void;
}>();

const open = ref(false);
const search = ref('');
const collapsed = ref<Set<string>>(new Set());
const rootRef = ref<HTMLElement | null>(null);

interface Group {
  providerName: string;
  providerLabel: string;
  models: GroupedModelSelectOption[];
}

const groups = computed<Group[]>(() => {
  const map = new Map<string, Group>();
  for (const m of props.options) {
    const pn = (m.providerName ?? '').trim() || '__other';
    const label = (m.providerLabel ?? m.providerName ?? '').trim() || '其他';
    let group = map.get(pn);
    if (!group) {
      group = { providerName: pn, providerLabel: label, models: [] };
      map.set(pn, group);
    }
    group.models.push(m);
  }
  return [...map.values()];
});

const filteredGroups = computed<Group[]>(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return groups.value;
  return groups.value
    .map((g) => ({
      ...g,
      models: g.models.filter((m) => {
        return (
          m.label.toLowerCase().includes(q) ||
          (m.providerLabel ?? m.providerName ?? '')
            .toLowerCase()
            .includes(q)
        );
      }),
    }))
    .filter((g) => g.models.length > 0);
});

const current = computed(() =>
  props.options.find((m) => m.id === props.modelValue),
);

function toggleGroup(name: string) {
  const next = new Set(collapsed.value);
  if (next.has(name)) next.delete(name);
  else next.add(name);
  collapsed.value = next;
}

function pick(m: GroupedModelSelectOption) {
  emit('update:modelValue', m.id);
  emit('change', m.id);
  open.value = false;
  search.value = '';
}

</script>

<template>
  <div ref="rootRef" class="gms" :class="{ 'is-disabled': disabled }">
    <Popover v-model:open="open">
    <PopoverTrigger as-child>
    <AsShadcnButton variant="outline"
      type="button"
      class="gms-trigger"
      :class="{ 'is-active': open, 'has-value': !!current }"
      :disabled="disabled"
    >
      <span class="gms-trigger-icon">
        <ProviderIcon
          v-if="current?.providerName"
          :name="current.providerName"
          :size="20"
        />
        <span v-else class="gms-trigger-icon-fallback">{{ fallbackIcon }}</span>
      </span>
      <span v-if="current" class="gms-trigger-name" :title="current.label">
        {{ current.label }}
      </span>
      <span v-else class="gms-trigger-placeholder" :title="placeholder">
        {{ placeholder }}
      </span>
      <span class="gms-trigger-caret">▾</span>
    </AsShadcnButton>
    </PopoverTrigger>

    <PopoverContent align="start" class="gms-panel" @open-auto-focus.prevent>
      <div class="gms-search">
        <AsShadcnInput v-model="search" class="h-8 text-xs" :placeholder="searchPlaceholder" @click.stop />
      </div>
      <div class="gms-body">
        <div v-if="filteredGroups.length === 0" class="gms-empty">
          <div class="gms-empty-icon">{{ fallbackIcon }}</div>
          <div>{{ emptyText }}</div>
          <div v-if="emptyHint" class="gms-empty-hint">{{ emptyHint }}</div>
        </div>
        <div
          v-for="g in filteredGroups"
          :key="g.providerName"
          class="gms-group"
        >
          <div
            class="gms-group-head"
            @click="toggleGroup(g.providerName)"
          >
            <span class="gms-fold">
              {{ collapsed.has(g.providerName) ? '▸' : '▾' }}
            </span>
            <ProviderIcon
              v-if="g.providerName !== '__other'"
              :name="g.providerName"
              :size="16"
            />
            <span v-else class="gms-group-icon">{{ fallbackIcon }}</span>
            <span class="gms-group-name">{{ g.providerLabel }}</span>
            <span class="gms-group-count">{{ g.models.length }}</span>
          </div>
          <div
            v-if="!collapsed.has(g.providerName)"
            class="gms-group-body"
          >
            <div
              v-for="m in g.models"
              :key="m.id"
              class="gms-item"
              :class="{ 'is-active': modelValue === m.id }"
              @click="pick(m)"
            >
              <ProviderIcon
                v-if="m.providerName"
                :name="m.providerName"
                :size="14"
              />
              <span class="gms-item-name" :title="m.label">
                {{ m.label }}
              </span>
              <span v-if="m.isDefault" class="gms-item-default">默认</span>
              <span v-if="modelValue === m.id" class="gms-item-check">✓</span>
            </div>
          </div>
        </div>
      </div>
    </PopoverContent>
    </Popover>
  </div>
</template>

<style scoped>
.gms {
  position: relative;
  width: 100%;
}
.gms.is-disabled {
  opacity: 0.6;
  pointer-events: none;
}
.gms-trigger {
  width: 100%;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--background);
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.gms-trigger:hover:not(:disabled) {
  border-color: var(--ring);
}
.gms-trigger.is-active {
  border-color: var(--ring);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ring) 18%, transparent);
}
.gms-trigger.has-value {
  background: var(--background);
}
.gms-trigger-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  flex-shrink: 0;
}
.gms-trigger-icon-fallback {
  font-size: 16px;
  color: var(--muted-foreground);
}
.gms-trigger-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.gms-trigger-placeholder {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--muted-foreground);
  text-align: left;
  /* Long copy (e.g. "暂无可用模型…") must never wrap the trigger to two
   * lines — truncate and expose the full text via the title attribute. */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gms-trigger-caret {
  font-size: 10px;
  color: var(--muted-foreground);
}
.gms-panel {
  width: var(--reka-popover-trigger-width);
  /* Narrow trigger cards (系统默认模型 grid) would squeeze the grouped
   * list / empty-state text into wraps — keep the panel readable instead. */
  min-width: min(260px, calc(100vw - 64px));
  z-index: 10000;
  padding: 6px;
  background: var(--popover);
  color: var(--popover-foreground);
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  max-height: 360px;
  display: flex;
  flex-direction: column;
}
.gms-search { padding: 4px 4px 8px; border-bottom: 1px solid var(--border); }
.gms-body {
  overflow-y: auto;
  padding: 4px 0;
  min-height: 60px;
}
.gms-empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--muted-foreground);
  font-size: 12px;
}
.gms-empty-icon {
  font-size: 26px;
  opacity: 0.4;
}
.gms-empty-hint {
  margin-top: 4px;
  font-size: 11px;
  color: #cbd5e1;
}
.gms-group + .gms-group {
  margin-top: 2px;
  padding-top: 2px;
  border-top: 1px solid var(--border);
}
.gms-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
}
.gms-group-head:hover {
  background: var(--muted);
}
.gms-fold {
  font-size: 10px;
  color: var(--muted-foreground);
  width: 12px;
  text-align: center;
}
.gms-group-icon {
  font-size: 14px;
  color: var(--muted-foreground);
}
.gms-group-name {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--foreground);
}
.gms-group-count {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--muted);
  color: var(--muted-foreground);
  font-size: 10px;
}
.gms-group-body {
  padding: 2px 0 4px 24px;
}
.gms-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s;
}
.gms-item:hover {
  background: var(--muted);
}
.gms-item.is-active {
  background: color-mix(in srgb, var(--primary) 12%, var(--popover));
}
.gms-item-name {
  flex: 1;
  font-size: 12px;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gms-item.is-active .gms-item-name {
  color: var(--primary);
  font-weight: 500;
}
.gms-item-default {
  padding: 1px 6px;
  border-radius: 4px;
  background: #ecfdf5;
  color: #059669;
  font-size: 10px;
  font-weight: 500;
}
.gms-item-check {
  color: var(--primary);
  font-size: 12px;
}
</style>
