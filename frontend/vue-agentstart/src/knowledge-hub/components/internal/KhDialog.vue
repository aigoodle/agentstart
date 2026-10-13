<script setup lang="ts">
/**
 * KhDialog — the module's internal modal primitive. Confirm + prompt in one.
 *
 * No dependency on antd / any UI kit. Consumers who want a custom look can
 * override the CSS variables (--kh-color-primary, etc.) or replace the whole
 * component by installing a similarly-shaped one via the Vue app's global
 * registration.
 */
import { computed, nextTick, ref, watch } from 'vue';

import { Modal } from '../../../ui';
import { useKhI18n } from '../../i18n';

interface Props {
  open: boolean;
  title?: string;
  content?: string;
  /** When set, adds a text input pre-filled with this value. */
  promptDefault?: string;
  promptPlaceholder?: string;
  okText?: string;
  cancelText?: string;
  danger?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'confirm', promptValue?: string): void;
  (e: 'cancel'): void;
}>();

const { t } = useKhI18n();

// Seed the initial value from `promptDefault` so a component that mounts
// already-open (e.g. controlled by a `v-if="condition"` outside) shows the
// default rather than an empty input.
const inputValue = ref(props.promptDefault ?? '');
const inputRef = ref<HTMLInputElement | null>(null);

watch(
  () => props.open,
  async (v) => {
    if (v) {
      // Re-open resets the input to the current default and refocuses.
      inputValue.value = props.promptDefault ?? '';
      await nextTick();
      inputRef.value?.focus();
      inputRef.value?.select();
    }
  },
  { immediate: true },
);

const hasPrompt = computed(() => props.promptDefault !== undefined);

function onOk() {
  emit('confirm', hasPrompt.value ? inputValue.value : undefined);
  emit('update:open', false);
}
function onCancel() {
  emit('cancel');
  emit('update:open', false);
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && (!hasPrompt.value || inputValue.value.trim())) {
    e.preventDefault();
    onOk();
  } else if (e.key === 'Escape') {
    e.preventDefault();
    onCancel();
  }
}
</script>

<template>
  <Modal
    :open="open"
    class="khd-panel"
    centered
    :width="440"
    :title="title"
    :footer="false"
    @cancel="onCancel"
    @keydown="onKey"
  >
        <div v-if="content" class="khd-content">{{ content }}</div>
        <input
          v-if="hasPrompt"
          ref="inputRef"
          v-model="inputValue"
          class="khd-input"
          :placeholder="promptPlaceholder"
        />
        <div class="khd-actions">
          <button class="khd-btn khd-btn-secondary" @click="onCancel">
            {{ cancelText ?? t('common.cancel') }}
          </button>
          <button
            class="khd-btn"
            :class="danger ? 'khd-btn-danger' : 'khd-btn-primary'"
            :disabled="hasPrompt && !inputValue.trim()"
            @click="onOk"
          >
            {{ okText ?? t('common.ok') }}
          </button>
        </div>
  </Modal>
</template>

<style scoped>
.khd-mask {
  position: fixed;
  inset: 0;
  z-index: var(--kh-z-modal);
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}
.khd-panel {
  background: var(--kh-color-surface);
}
.khd-panel :deep(.as-modal__body) {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding: 22px 24px 18px;
}
.khd-title {
  font-size: var(--kh-fs-3xl);
  font-weight: 600;
  color: var(--kh-color-text-primary);
}
.khd-content {
  font-size: var(--kh-fs-lg);
  color: var(--kh-color-text-secondary);
  line-height: 1.55;
}
.khd-input {
  padding: 8px 12px;
  font-size: var(--kh-fs-lg);
  border: 1px solid var(--kh-input-border);
  border-radius: var(--kh-input-radius);
  outline: none;
  background: var(--kh-input-bg);
  color: var(--kh-color-text-primary);
  font-family: inherit;
}
.khd-input:focus {
  border-color: var(--kh-color-primary);
  box-shadow: var(--kh-focus-ring);
}
.khd-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--kh-space-2);
  margin-top: var(--kh-space-2);
}
.khd-btn {
  padding: 6px 18px;
  border: none;
  border-radius: var(--kh-radius-sm);
  font-size: var(--kh-fs-lg);
  font-weight: 500;
  cursor: pointer;
  transition:
    background var(--kh-tx-fast),
    transform 0.05s ease;
}
.khd-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.khd-btn-primary {
  background: var(--kh-color-primary);
  color: var(--kh-color-primary-contrast);
}
.khd-btn-primary:hover:not(:disabled) {
  background: var(--kh-color-primary-strong);
}
.khd-btn-danger {
  background: var(--kh-color-danger);
  color: var(--kh-color-primary-contrast);
}
.khd-btn-danger:hover:not(:disabled) {
  opacity: 0.9;
}
.khd-btn-secondary {
  background: var(--kh-color-surface-hover);
  color: var(--kh-color-text-secondary);
}
.khd-btn-secondary:hover {
  background: var(--kh-color-border);
}
</style>
