<script setup lang="ts">
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

/**
 * RetrievalConfigPopover — floating card that wraps a
 * {@link RetrievalMethodPicker} for surfaces where a drawer would be too
 * heavy (recall testing's "混合检索" button, header quick-config etc.).
 *
 *   ┌─ trigger button ─┐         ┌───────────── popover ─────────────┐
 *   │  混合检索  ▾     │  ─────  │ 检索方法                            │
 *   └──────────────────┘         │ [向量] [全文] [混合]                │
 *                                │  ... TopK / Score / Rerank ...     │
 *                                │           [取消] [保存]              │
 *                                └────────────────────────────────────┘
 *
 * Behaviours:
 *   • Anchored beneath the {@code triggerEl} by a small `getBoundingClientRect()`
 *     calc — shadcn-vue / Reka UI collision handling.
 *   • Click-outside and Escape close.
 *   • Local draft state so 取消 restores the pre-open config.
 */
import { ref, watch } from 'vue';

import { Popover, PopoverContent, PopoverAnchor } from '../../ui/shadcn/popover';

import type {
  IndexingTechnique,
  RetrievalConfig,
} from '../types/dataset';
import RetrievalMethodPicker from './RetrievalMethodPicker.vue';

interface Props {
  open: boolean;
  /** Element the popover anchors to. */
  triggerEl?: HTMLElement | null;
  modelValue: RetrievalConfig;
  rerankModels?: Array<{ id: string; label: string }>;
  indexingTechnique?: IndexingTechnique;
  /** Popover width in px. Default: 380 (Dify parity). */
  width?: number;
  /** Which corner of the trigger the popover aligns to. */
  placement?: 'bottom-end' | 'bottom-start';
}
const props = withDefaults(defineProps<Props>(), {
  triggerEl: null,
  rerankModels: () => [],
  width: 380,
  placement: 'bottom-end',
});
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'update:modelValue', v: RetrievalConfig): void;
  /**
   * Fired when the user hits "保存". Popover doesn't do the persistence
   * itself — the parent's own PUT / state-flip handler does — this just
   * signals "commit whatever's in modelValue".
   */
  (e: 'save', v: RetrievalConfig): void;
}>();

/** Snapshot on open so 取消 can restore. */
const snapshot = ref<RetrievalConfig | null>(null);
/** Local draft; picker v-models this so it doesn't touch the parent's ref
 *  until 保存 is clicked. */
const draft = ref<RetrievalConfig>({ ...props.modelValue });

watch(() => props.open, open => {
  if (open) {
    snapshot.value = { ...props.modelValue };
    draft.value = { ...props.modelValue };
  }
}, { immediate: true });

// ---- actions
function cancel() {
  if (snapshot.value) draft.value = { ...snapshot.value };
  emit('update:open', false);
}
function save() {
  emit('update:modelValue', { ...draft.value });
  emit('save', { ...draft.value });
  emit('update:open', false);
}

</script>

<template>
  <Popover :open="open" @update:open="emit('update:open', $event)">
    <PopoverAnchor :reference="triggerEl ?? undefined" style="display: none" />
    <PopoverContent class="rcp" side="bottom" :align="placement === 'bottom-start' ? 'start' : 'end'"
      :side-offset="6" :collision-padding="8" :style="{ width: width + 'px', padding: 0 }">
      <div class="rcp-head">
        <div>
          <div class="rcp-title">检索设置</div>
          <div class="rcp-sub">了解更多关于检索方法。</div>
        </div>
        <AsShadcnButton variant="ghost" class="rcp-close" @click="cancel" aria-label="close">×</AsShadcnButton>
      </div>

      <div class="rcp-body">
        <div class="rcp-section-label">检索方法</div>
        <RetrievalMethodPicker
          v-model="draft"
          :rerank-models="rerankModels"
          :indexing-technique="indexingTechnique"
          variant="compact"
        />
      </div>

      <div class="rcp-actions">
        <AsShadcnButton variant="ghost" class="rcp-btn rcp-btn-secondary" @click="cancel">
          取消
        </AsShadcnButton>
        <AsShadcnButton variant="ghost" class="rcp-btn rcp-btn-primary" @click="save">
          保存
        </AsShadcnButton>
      </div>
    </PopoverContent>
  </Popover>
</template>

<style>
.rcp {
  position: relative;
  z-index: 10000;
  max-height: 84vh;
  display: flex;
  flex-direction: column;
  background: var(--kh-color-surface);
  border: 1px solid var(--kh-color-border);
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}
.rcp-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kh-color-surface-hover);
}
.rcp-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.rcp-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--kh-color-text-muted);
}
.rcp-close {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--kh-color-text-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.rcp-close:hover {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.rcp-body {
  padding: 12px 16px;
  overflow-y: auto;
  flex: 1;
}
.rcp-section-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-color-text-secondary);
}
.rcp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--kh-color-surface-hover);
  background: var(--kh-color-surface-sunken);
}
.rcp-btn {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.rcp-btn-secondary {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.rcp-btn-secondary:hover {
  background: var(--kh-color-border);
}
.rcp-btn-primary {
  background: linear-gradient(135deg, var(--kh-color-primary) 0%, var(--kh-color-primary-strong) 100%);
  color: var(--kh-color-primary-contrast);
  box-shadow: 0 2px 6px color-mix(in srgb, var(--kh-color-primary-strong) 25%, transparent);
}
.rcp-btn-primary:hover {
  filter: brightness(1.05);
}
</style>
