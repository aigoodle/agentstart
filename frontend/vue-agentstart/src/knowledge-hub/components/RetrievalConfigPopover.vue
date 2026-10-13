<script setup lang="ts">
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
 *     calc — no floating-ui dep just for one popover.
 *   • Click-outside and Escape close.
 *   • Local draft state so 取消 restores the pre-open config.
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

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

watch(
  () => props.open,
  (v) => {
    if (v) {
      snapshot.value = { ...props.modelValue };
      draft.value = { ...props.modelValue };
      nextTick(positionPopover);
    }
    // SSR guard: this watch is immediate, so it also fires during setup on
    // the server — where `document` does not exist (even the closed branch).
    if (typeof document === 'undefined') return;
    if (v) {
      document.addEventListener('mousedown', onDocMouseDown, true);
      document.addEventListener('keydown', onKeydown);
    } else {
      document.removeEventListener('mousedown', onDocMouseDown, true);
      document.removeEventListener('keydown', onKeydown);
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMouseDown, true);
  document.removeEventListener('keydown', onKeydown);
});

// ---- positioning
const popoverRef = ref<HTMLElement | null>(null);
const pos = ref<{ top: number; left: number }>({ top: 0, left: 0 });

function positionPopover() {
  // SSR guard: positioned via a nextTick from an immediate watch.
  if (typeof window === 'undefined' || !props.triggerEl) return;
  const r = props.triggerEl.getBoundingClientRect();
  const w = props.width;
  const gap = 6;
  const winW = window.innerWidth;
  const winH = window.innerHeight;
  let left =
    props.placement === 'bottom-start' ? r.left : r.right - w;
  // Clamp so it stays fully on-screen.
  left = Math.max(8, Math.min(left, winW - w - 8));
  let top = r.bottom + gap;
  // If not enough room below, flip above.
  if (top + 480 > winH && r.top > 480) {
    top = r.top - gap - 480;
  }
  pos.value = { top, left };
}

// ---- outside close
function onDocMouseDown(e: MouseEvent) {
  if (!props.open) return;
  const target = e.target as Node | null;
  if (
    popoverRef.value &&
    !popoverRef.value.contains(target) &&
    props.triggerEl &&
    !props.triggerEl.contains(target)
  ) {
    emit('update:open', false);
  }
}
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('update:open', false);
}

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

const style = computed(() => ({
  top: `${pos.value.top}px`,
  left: `${pos.value.left}px`,
  width: `${props.width}px`,
}));
</script>

<template>
  <Teleport to="body" :disabled="!open">
    <div
      v-if="open"
      ref="popoverRef"
      class="rcp"
      :style="style"
    >
      <div class="rcp-head">
        <div>
          <div class="rcp-title">检索设置</div>
          <div class="rcp-sub">了解更多关于检索方法。</div>
        </div>
        <button class="rcp-close" @click="cancel" aria-label="close">×</button>
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
        <button class="rcp-btn rcp-btn-secondary" @click="cancel">
          取消
        </button>
        <button class="rcp-btn rcp-btn-primary" @click="save">
          保存
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.rcp {
  position: fixed;
  z-index: 1500;
  max-height: 84vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}
.rcp-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
}
.rcp-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.rcp-sub {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}
.rcp-close {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.rcp-close:hover {
  background: #f1f5f9;
  color: #475569;
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
  color: #475569;
}
.rcp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #f1f5f9;
  background: #fafafa;
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
  background: #f1f5f9;
  color: #475569;
}
.rcp-btn-secondary:hover {
  background: #e2e8f0;
}
.rcp-btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  box-shadow: 0 2px 6px rgba(79, 70, 229, 0.25);
}
.rcp-btn-primary:hover {
  filter: brightness(1.05);
}
</style>
