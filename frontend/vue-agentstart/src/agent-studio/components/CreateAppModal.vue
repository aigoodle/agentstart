<script setup lang="ts">
/**
 * CreateAppModal — Dify-parity two-pane "create application" dialog.
 *
 * Layout:
 *
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │ 创建应用           从空白开始一个 AI 应用           ×        │
 *   ├────────────────────────────────┬─────────────────────────────┤
 *   │ 选择应用类型                    │  ┌─────────────────────┐    │
 *   │ ┌─────────┐ ┌─────────┐        │  │                     │    │
 *   │ │ chatflow│ │workflow │        │  │   应用类型缩略图      │    │
 *   │ └─────────┘ └─────────┘        │  │   (右侧随选中变化)   │    │
 *   │ 初学者场景 ▽                    │  │                     │    │
 *   │ ┌────┐┌────┐┌────┐             │  └─────────────────────┘    │
 *   │ │chat││agent││textg│           │                             │
 *   │ └────┘└────┘└────┘             │  标题                        │
 *   │ ── 分割线 ──                    │  用户视角的一句话说明         │
 *   │ 应用名称                        │                             │
 *   │ [                        ][🤖]  │                             │
 *   │ 描述                            │                             │
 *   │ [                            ]  │                             │
 *   │                                 │                             │
 *   │      取消    创建 ⌘↵            │                             │
 *   └────────────────────────────────┴─────────────────────────────┘
 *
 * Emits `create({appType, name, description, icon})` — parent decides where
 * to route: workflow → visual designer; others → open the model-picking form
 * with those fields preseeded.
 */
import { computed, ref, watch } from 'vue';

import { Button, Modal } from '../../ui';
import { APP_TYPES } from '../composables/useAgentStudio';
import type { AppType, AppTypeDescriptor } from '../types';

interface AppIcon {
  emoji: string;
  background: string;
}

interface Props {
  open: boolean;
  /** Whitelist a subset of app types to show. */
  allow?: AppType[];
}

const props = withDefaults(defineProps<Props>(), {
  allow: () => ['chatbot', 'agent', 'workflow', 'chatflow', 'text-generator'],
});

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (
    e: 'create',
    payload: {
      appType: AppType;
      name: string;
      description: string;
      icon: AppIcon;
    },
  ): void;
}>();

// -------- state --------
// Dify shows the "primary" pair (chatflow / workflow) as always-visible, and the
// beginner set (chatbot / agent / text-gen) behind a collapsible section.
const PRIMARY_TYPES: AppType[] = ['workflow', 'chatflow'];
const BEGINNER_TYPES: AppType[] = ['chatbot', 'agent', 'text-generator'];

const primaryCards = computed<AppTypeDescriptor[]>(() =>
  APP_TYPES.filter(
    (a) => PRIMARY_TYPES.includes(a.id) && props.allow.includes(a.id),
  ),
);
const beginnerCards = computed<AppTypeDescriptor[]>(() =>
  APP_TYPES.filter(
    (a) => BEGINNER_TYPES.includes(a.id) && props.allow.includes(a.id),
  ),
);

const selectedType = ref<AppType>('chatbot');
const isBeginnerExpanded = ref(true);

const currentDesc = computed<AppTypeDescriptor | undefined>(() =>
  APP_TYPES.find((a) => a.id === selectedType.value),
);

const name = ref('');
const description = ref('');
const icon = ref<AppIcon>({ emoji: '🤖', background: '#FFEAD5' });

// Small emoji + swatch palette; matches Dify's "app icon picker" light version.
const EMOJIS = ['🤖', '💬', '🧠', '📝', '🎯', '⚡', '🔧', '📊', '💡', '🚀', '📚', '🌐'];
const SWATCHES = [
  '#FFEAD5',
  '#EEF4FF',
  '#DCFCE7',
  '#FCE7F3',
  '#FEF3C7',
  '#EDE9FE',
  '#DBEAFE',
  '#FFE4E6',
];
const showIconPicker = ref(false);

// -------- lifecycle --------
watch(
  () => props.open,
  (v) => {
    if (v) {
      // Reset form each time the modal opens so a cancelled draft doesn't come back.
      name.value = '';
      description.value = '';
      icon.value = { emoji: '🤖', background: '#FFEAD5' };
      // Pre-select the first allowed type — beginners land on chatbot by default.
      if (props.allow.includes('chatbot')) selectedType.value = 'chatbot';
      else if (props.allow[0]) selectedType.value = props.allow[0];
      showIconPicker.value = false;
    }
  },
);

function close() {
  emit('update:open', false);
}

function submit() {
  if (!name.value.trim()) return;
  emit('create', {
    appType: selectedType.value,
    name: name.value.trim(),
    description: description.value.trim(),
    icon: { ...icon.value },
  });
  close();
}

function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault();
    submit();
  } else if (e.key === 'Escape') {
    close();
  }
}
</script>

<template>
  <Modal
    :open="open"
    class="as-modal-panel"
    centered
    width="min(1080px, 96vw)"
    @cancel="close"
    @keydown="onKeydown"
  >
    <template #title>
      <div class="as-modal-heading">
        <div class="as-modal-title">创建应用</div>
        <div class="as-modal-subtitle">从空白开始配置一个 AI 应用</div>
      </div>
    </template>

        <!-- Two-pane body -->
        <div class="as-body">
          <!-- LEFT: type tiles + form -->
          <div class="as-left">
            <div class="as-section-label">选择应用类型</div>

            <!-- Primary tiles (workflow, potentially chatflow if we add it) -->
            <div v-if="primaryCards.length > 0" class="as-tile-row">
              <button
                v-for="a in primaryCards"
                :key="a.id"
                class="as-tile"
                :class="{ 'as-tile-active': selectedType === a.id }"
                @click="selectedType = a.id"
              >
                <div class="as-tile-icon" :style="{ background: a.iconBg }">
                  {{ a.icon }}
                </div>
                <div class="as-tile-title">{{ a.title }}</div>
                <div class="as-tile-desc">{{ a.description }}</div>
              </button>
            </div>

            <!-- Beginner tiles: collapsible group -->
            <button
              v-if="beginnerCards.length > 0"
              type="button"
              class="as-expand-btn"
              @click="isBeginnerExpanded = !isBeginnerExpanded"
            >
              <span>初学者常用</span>
              <span class="as-caret" :class="{ 'as-caret-open': isBeginnerExpanded }">
                ›
              </span>
            </button>
            <div
              v-if="isBeginnerExpanded && beginnerCards.length > 0"
              class="as-tile-row as-tile-row-3"
            >
              <button
                v-for="a in beginnerCards"
                :key="a.id"
                class="as-tile"
                :class="{ 'as-tile-active': selectedType === a.id }"
                @click="selectedType = a.id"
              >
                <div class="as-tile-icon" :style="{ background: a.iconBg }">
                  {{ a.icon }}
                </div>
                <div class="as-tile-title">{{ a.title }}</div>
                <div class="as-tile-desc">{{ a.description }}</div>
              </button>
            </div>

            <div class="as-divider" />

            <!-- Form: name + icon (side by side) -->
            <div class="as-field">
              <label class="as-label">
                应用名称 <span class="as-req">*</span>
              </label>
              <div class="as-name-row">
                <input
                  v-model="name"
                  class="as-input"
                  placeholder="给你的应用起个名字"
                  maxlength="60"
                />
                <button
                  type="button"
                  class="as-icon-btn"
                  :style="{ background: icon.background }"
                  title="点击选择应用图标"
                  @click="showIconPicker = !showIconPicker"
                >
                  {{ icon.emoji }}
                </button>
              </div>
              <!-- Icon picker: emoji grid + swatch strip -->
              <div v-if="showIconPicker" class="as-icon-picker">
                <div class="as-emoji-grid">
                  <button
                    v-for="e in EMOJIS"
                    :key="e"
                    type="button"
                    class="as-emoji-btn"
                    :class="{ 'as-emoji-active': icon.emoji === e }"
                    @click="icon.emoji = e"
                  >
                    {{ e }}
                  </button>
                </div>
                <div class="as-swatch-row">
                  <button
                    v-for="s in SWATCHES"
                    :key="s"
                    type="button"
                    class="as-swatch"
                    :class="{ 'as-swatch-active': icon.background === s }"
                    :style="{ background: s }"
                    @click="icon.background = s"
                  />
                </div>
              </div>
            </div>

            <div class="as-field">
              <label class="as-label">
                描述 <span class="as-optional">（可选）</span>
              </label>
              <textarea
                v-model="description"
                class="as-textarea"
                placeholder="用一两句话说清这个应用要做什么"
                maxlength="255"
                rows="2"
              />
            </div>

          </div>

          <!-- RIGHT: preview pane -->
          <div class="as-right">
            <div class="as-preview-title">
              <div class="as-preview-eyebrow">应用预览</div>
              <div class="as-preview-heading">{{ currentDesc?.title }}</div>
              <div class="as-preview-desc">{{ currentDesc?.description }}</div>
            </div>
            <div class="as-preview-canvas" v-html="currentDesc?.previewSvg ?? ''" />
            <div class="as-preview-hint">{{ currentDesc?.hint }}</div>
          </div>
        </div>
        <template #footer>
          <Button @click="close">取消</Button>
          <Button
            type="primary"
            :disabled="!name.trim()"
            @click="submit"
          >
            <kbd class="as-kbd">↵</kbd>
            <span>创建</span>
          </Button>
        </template>
  </Modal>
</template>

<style scoped>
.as-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(2px);
}
.as-modal-panel {
  max-height: 92vh;
}
.as-modal-panel :deep(.as-modal__body) { padding: 0; }

/* Header */
.as-modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.as-close {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s;
}
.as-close:hover {
  background: #f1f5f9;
  color: #475569;
}

/* Two-pane body */
.as-body {
  display: flex;
  min-height: 560px;
  max-height: calc(92vh - 65px);
  overflow: hidden;
}
.as-left {
  flex: 1 1 520px;
  min-width: 460px;
  padding: 20px 24px 22px;
  overflow-y: auto;
  border-right: 1px solid #f1f5f9;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.as-right {
  flex: 1 1 480px;
  min-width: 420px;
  padding: 24px 28px;
  overflow-y: auto;
  background: linear-gradient(180deg, #fafbff 0%, #f4f6fd 100%);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Type-tile section */
.as-section-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.as-tile-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.as-tile-row-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.as-tile {
  position: relative;
  padding: 10px 12px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;
}
.as-tile:hover {
  border-color: #cbd5e1;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}
.as-tile-active {
  border-color: transparent;
  outline: 1.5px solid #6366f1;
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.15);
}
.as-tile-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 15px;
}
.as-tile-title {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.as-tile-desc {
  margin-top: 3px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Beginner expand toggle */
.as-expand-btn {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}
.as-caret {
  font-size: 14px;
  transition: transform 0.15s;
}
.as-caret-open {
  transform: rotate(90deg);
}

/* Divider */
.as-divider {
  height: 1px;
  margin: 2px 0;
  background: linear-gradient(90deg, transparent, #e2e8f0, transparent);
}

/* Form fields */
.as-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.as-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.as-req {
  color: #dc2626;
}
.as-optional {
  margin-left: 4px;
  font-weight: 400;
  font-size: 11px;
  color: #94a3b8;
}
.as-name-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.as-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  transition: border-color 0.15s ease;
}
.as-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.as-textarea {
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-family: inherit;
  resize: vertical;
  min-height: 60px;
  transition: border-color 0.15s ease;
}
.as-textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.as-icon-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 22px;
  cursor: pointer;
  transition: transform 0.15s ease;
}
.as-icon-btn:hover {
  transform: scale(1.05);
}

/* Icon picker inline panel */
.as-icon-picker {
  margin-top: 8px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}
.as-emoji-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 4px;
}
.as-emoji-btn {
  padding: 4px;
  font-size: 18px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease;
}
.as-emoji-btn:hover {
  background: #fff;
}
.as-emoji-active {
  background: #fff;
  border-color: #6366f1;
}
.as-swatch-row {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}
.as-swatch {
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 50%;
  border: 1.5px solid #fff;
  outline: 1px solid #e2e8f0;
  cursor: pointer;
  transition: transform 0.15s ease;
}
.as-swatch:hover {
  transform: scale(1.15);
}
.as-swatch-active {
  outline: 2px solid #6366f1;
}

.as-kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  min-width: 18px;
  height: 18px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  font-size: 10px;
  font-family: inherit;
}

/* Right pane preview */
.as-preview-eyebrow {
  font-size: 10px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.as-preview-heading {
  margin-top: 2px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.as-preview-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.as-preview-canvas {
  margin-top: 4px;
  padding: 8px;
  background:
    repeating-linear-gradient(
      135deg,
      transparent,
      transparent 4px,
      rgba(15, 23, 42, 0.04) 4px,
      rgba(15, 23, 42, 0.04) 5px
    ),
    #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  flex: 1;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.as-preview-canvas :deep(svg) {
  width: 100%;
  height: auto;
  max-height: 340px;
  display: block;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.1);
}
.as-preview-hint {
  padding: 8px 12px;
  font-size: 11px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.6);
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
}

:global(.dark .as-modal-panel .as-left) {
  border-color: var(--as-border-secondary);
}
:global(.dark .as-modal-panel .as-right) {
  background: linear-gradient(180deg, var(--as-bg) 0%, var(--as-bg-layout) 100%);
}
:global(.dark .as-modal-panel .as-section-label),
:global(.dark .as-modal-panel .as-label),
:global(.dark .as-modal-panel .as-tile-title),
:global(.dark .as-modal-panel .as-preview-heading) {
  color: var(--as-text);
}
:global(.dark .as-modal-panel .as-tile),
:global(.dark .as-modal-panel .as-input),
:global(.dark .as-modal-panel .as-textarea) {
  color: var(--as-text);
  background: var(--as-bg);
  border-color: var(--as-border);
}
:global(.dark .as-modal-panel .as-tile-desc),
:global(.dark .as-modal-panel .as-expand-btn),
:global(.dark .as-modal-panel .as-preview-desc),
:global(.dark .as-modal-panel .as-preview-hint) {
  color: var(--as-text-secondary);
}
:global(.dark .as-modal-panel .as-divider) {
  background: linear-gradient(90deg, transparent, var(--as-border), transparent);
}
:global(.dark .as-modal-panel .as-icon-picker) {
  background: var(--as-bg-layout);
  border-color: var(--as-border);
}
:global(.dark .as-modal-panel .as-emoji-btn:hover),
:global(.dark .as-modal-panel .as-emoji-active) {
  background: var(--as-bg);
}
:global(.dark .as-modal-panel .as-preview-canvas) {
  background:
    repeating-linear-gradient(135deg, transparent, transparent 4px, rgb(255 255 255 / 3%) 4px, rgb(255 255 255 / 3%) 5px),
    var(--as-bg-layout);
  border-color: var(--as-border);
}
:global(.dark .as-modal-panel .as-preview-canvas svg) {
  box-shadow: 0 6px 20px rgb(0 0 0 / 35%);
}
:global(.dark .as-modal-panel .as-preview-hint) {
  background: color-mix(in srgb, var(--as-bg) 76%, transparent);
  border-color: var(--as-border);
}
</style>
