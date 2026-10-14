<script setup lang="ts">
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

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

function toggleOpen() {
  if (props.disabled) return;
  open.value = !open.value;
}

function onDocMousedown(e: MouseEvent) {
  if (!open.value) return;
  const el = rootRef.value;
  if (el && !el.contains(e.target as Node)) open.value = false;
}

onMounted(() => window.addEventListener('mousedown', onDocMousedown));
onBeforeUnmount(() => window.removeEventListener('mousedown', onDocMousedown));
</script>

<template>
  <div ref="rootRef" class="gms" :class="{ 'is-disabled': disabled }">
    <button
      type="button"
      class="gms-trigger"
      :class="{ 'is-active': open, 'has-value': !!current }"
      :disabled="disabled"
      @click="toggleOpen"
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
    </button>

    <div v-if="open" class="gms-panel" @click.stop>
      <div class="gms-search">
        <span class="gms-search-icon">🔍</span>
        <input
          v-model="search"
          class="gms-search-input"
          :placeholder="searchPlaceholder"
          @click.stop
        />
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
    </div>
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
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.gms-trigger:hover:not(:disabled) {
  border-color: #a5b4fc;
}
.gms-trigger.is-active {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.gms-trigger.has-value {
  background: #fafbff;
}
.gms-trigger-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6366f1;
  flex-shrink: 0;
}
.gms-trigger-icon-fallback {
  font-size: 16px;
  color: #94a3b8;
}
.gms-trigger-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.gms-trigger-placeholder {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #94a3b8;
  text-align: left;
  /* Long copy (e.g. "暂无可用模型…") must never wrap the trigger to two
   * lines — truncate and expose the full text via the title attribute. */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gms-trigger-caret {
  font-size: 10px;
  color: #94a3b8;
}
.gms-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  /* Narrow trigger cards (系统默认模型 grid) would squeeze the grouped
   * list / empty-state text into wraps — keep the panel readable instead. */
  min-width: min(260px, calc(100vw - 64px));
  z-index: 40;
  padding: 6px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  max-height: 360px;
  display: flex;
  flex-direction: column;
}
.gms-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 1px solid #f1f5f9;
  background: #f9fafb;
  border-radius: 6px 6px 0 0;
}
.gms-search-icon {
  font-size: 12px;
  color: #94a3b8;
}
.gms-search-input {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12px;
  color: #0f172a;
}
.gms-body {
  overflow-y: auto;
  padding: 4px 0;
  min-height: 60px;
}
.gms-empty {
  padding: 24px 12px;
  text-align: center;
  color: #94a3b8;
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
  border-top: 1px solid #f3f4f6;
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
  background: #f9fafb;
}
.gms-fold {
  font-size: 10px;
  color: #94a3b8;
  width: 12px;
  text-align: center;
}
.gms-group-icon {
  font-size: 14px;
  color: #94a3b8;
}
.gms-group-name {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
}
.gms-group-count {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
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
  background: #f3f4f6;
}
.gms-item.is-active {
  background: #eef2ff;
}
.gms-item-name {
  flex: 1;
  font-size: 12px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gms-item.is-active .gms-item-name {
  color: #4338ca;
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
  color: #4338ca;
  font-size: 12px;
}
</style>
