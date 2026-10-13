<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { useThemeColor } from '../composables/useThemeColor'

const { color, presets, defaultColor, setColor, reset } = useThemeColor()

const open = ref(false)
const rootRef = ref<HTMLElement>()

function toggle() {
  open.value = !open.value
}

/** 点击外部关闭 */
function onDocClick(e: MouseEvent) {
  if (!rootRef.value?.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onDocClick, true))
onBeforeUnmount(() =>
  document.removeEventListener('mousedown', onDocClick, true),
)

function onCustomInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  if (/^#[0-9a-fA-F]{6}$/.test(v)) setColor(v)
}

function isActive(hex: string): boolean {
  return color.value.toLowerCase() === hex.toLowerCase()
}

const isDefault = () =>
  color.value.toLowerCase() === defaultColor.toLowerCase()
</script>

<template>
  <div ref="rootRef" class="theme-color-picker">
    <button
      class="theme-color-btn"
      type="button"
      title="主题颜色"
      aria-label="主题颜色"
      :class="{ active: open }"
      @click="toggle"
    >
      <span class="theme-color-swatch" :style="{ background: color }"></span>
      <span class="theme-color-caret">
        <svg viewBox="0 0 1024 1024" width="10" height="10" aria-hidden="true">
          <path
            fill="currentColor"
            d="M512 714.7l-395.8-396c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0L512 624.1l350.5-350.7c12.5-12.5 32.8-12.5 45.3 0s12.5 32.8 0 45.3l-395.8 396z"
          />
        </svg>
      </span>
    </button>

    <Transition name="theme-color-pop">
      <div v-if="open" class="theme-color-panel">
        <div class="theme-color-panel-head">主题颜色</div>

        <div class="theme-color-grid">
          <button
            v-for="c in presets"
            :key="c"
            type="button"
            class="theme-color-swatch-btn"
            :class="{ active: isActive(c) }"
            :title="c"
            :style="{ background: c }"
            @click="setColor(c)"
          >
            <svg
              v-if="isActive(c)"
              class="theme-color-check"
              viewBox="0 0 1024 1024"
              width="14"
              height="14"
              aria-hidden="true"
            >
              <path
                fill="currentColor"
                d="M910.5 226.5L397.4 739.6c-14.7 14.7-38.7 14.7-53.4 0L150.5 546.1c-14.7-14.7-14.7-38.7 0-53.4s38.7-14.7 53.4 0l166.8 166.8L857 173.1c14.7-14.7 38.7-14.7 53.4 0s14.8 38.7 0.1 53.4z"
              />
            </svg>
          </button>
        </div>

        <div class="theme-color-custom">
          <label class="theme-color-custom-label">自定义</label>
          <div class="theme-color-custom-row">
            <span
              class="theme-color-custom-preview"
              :style="{ background: color }"
            ></span>
            <input
              type="color"
              class="theme-color-native-input"
              :value="color"
              @input="onCustomInput"
            />
            <span class="theme-color-hex">{{ color.toUpperCase() }}</span>
          </div>
        </div>

        <button
          v-if="!isDefault()"
          type="button"
          class="theme-color-reset"
          @click="reset()"
        >
          重置为默认
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.theme-color-picker {
  position: relative;
  display: inline-flex;
  margin-left: 4px;
}

/* ── 触发按钮 ── */
.theme-color-btn {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  font-family: inherit;
  color: #b8e8ff;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.45);
  border: 1px solid rgba(0, 204, 255, 0.28);
  border-radius: 7px;
  outline: none;
  transition: all 0.2s ease;
}

.theme-color-btn:focus-visible {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.18);
}

.theme-color-btn:hover,
.theme-color-btn.active {
  border-color: rgba(0, 224, 255, 0.75);
  box-shadow: 0 0 10px rgba(0, 204, 255, 0.3);
  color: #eafcff;
}

.theme-color-swatch {
  width: 14px;
  height: 14px;
  flex: none;
  border-radius: 3px;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.15) inset,
    0 0 6px rgba(0, 204, 255, 0.5);
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.theme-color-caret {
  display: inline-flex;
  color: currentColor;
  opacity: 0.7;
  transition: transform 0.2s ease;
}

.theme-color-btn.active .theme-color-caret {
  transform: rotate(180deg);
}

/* ── 弹出面板 ── */
.theme-color-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 100;
  width: 236px;
  padding: 14px;
  background: rgba(3, 18, 38, 0.97);
  border: 1px solid rgba(0, 224, 255, 0.5);
  border-radius: 10px;
  box-shadow:
    0 12px 36px rgba(0, 0, 0, 0.65),
    0 0 18px rgba(0, 204, 255, 0.22);
  backdrop-filter: blur(14px);
}

.theme-color-panel::before {
  content: '';
  position: absolute;
  top: -6px;
  right: 18px;
  width: 12px;
  height: 12px;
  background: rgba(3, 18, 38, 0.97);
  border-top: 1px solid rgba(0, 224, 255, 0.5);
  border-left: 1px solid rgba(0, 224, 255, 0.5);
  transform: rotate(45deg);
  pointer-events: none;
}

.theme-color-panel-head {
  margin-bottom: 12px;
  font-size: 11px;
  font-weight: 600;
  color: #78a8d0;
  letter-spacing: 2px;
  text-transform: uppercase;
}

/* ── 预设色网格 ── */
.theme-color-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.theme-color-swatch-btn {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  padding: 0;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 6px;
  transition: all 0.15s ease;
  outline: none;
}

.theme-color-swatch-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
}

.theme-color-swatch-btn.active {
  border-color: rgba(255, 255, 255, 0.9);
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.2),
    0 0 12px currentColor;
}

.theme-color-check {
  color: #fff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.5));
}

/* ── 自定义颜色 ── */
.theme-color-custom {
  margin-bottom: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 204, 255, 0.15);
}

.theme-color-custom-label {
  display: block;
  margin-bottom: 8px;
  font-size: 10.5px;
  color: #78a8d0;
  letter-spacing: 1.5px;
}

.theme-color-custom-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.theme-color-custom-preview {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 4px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
  transition: background 0.2s ease;
}

.theme-color-native-input {
  position: absolute;
  width: 22px;
  height: 22px;
  padding: 0;
  opacity: 0;
  cursor: pointer;
  border: none;
}

/* 让原生 input 叠在 preview 上，点击 preview 实际点击的是 input */
.theme-color-custom-preview {
  position: relative;
  pointer-events: none;
}

.theme-color-custom-row {
  position: relative;
}

.theme-color-native-input {
  left: 0;
  cursor: pointer;
}

.theme-color-hex {
  font-family: var(--hud-font-mono, monospace);
  font-size: 12px;
  color: #b8e8ff;
  letter-spacing: 0.5px;
}

/* ── 重置按钮 ── */
.theme-color-reset {
  display: block;
  width: 100%;
  padding: 6px 0;
  font-family: inherit;
  font-size: 11px;
  color: #78a8d0;
  letter-spacing: 1px;
  cursor: pointer;
  background: transparent;
  border: 1px dashed rgba(0, 204, 255, 0.25);
  border-radius: 5px;
  outline: none;
  transition: all 0.15s ease;
}

.theme-color-reset:hover {
  color: #b8e8ff;
  border-color: rgba(0, 224, 255, 0.6);
  background: rgba(0, 204, 255, 0.05);
}

/* ── 弹出动画 ── */
.theme-color-pop-enter-active,
.theme-color-pop-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.theme-color-pop-enter-from,
.theme-color-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
}

/* ── 明亮模式 ── */
html[data-theme='light'] .theme-color-btn {
  color: #2a5a78;
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 140, 200, 0.3);
}

html[data-theme='light'] .theme-color-btn:hover,
html[data-theme='light'] .theme-color-btn.active {
  border-color: rgba(0, 140, 200, 0.6);
  box-shadow: 0 0 10px rgba(0, 140, 200, 0.15);
}

html[data-theme='light'] .theme-color-panel {
  background: #ffffff;
  border-color: rgba(0, 140, 200, 0.35);
  box-shadow:
    0 12px 36px rgba(0, 0, 0, 0.12),
    0 0 0 1px rgba(0, 140, 200, 0.1);
}

html[data-theme='light'] .theme-color-panel::before {
  background: #ffffff;
  border-color: rgba(0, 140, 200, 0.35);
}

html[data-theme='light'] .theme-color-panel-head {
  color: #5a7b93;
}

html[data-theme='light'] .theme-color-swatch-btn.active {
  border-color: #262626;
}

html[data-theme='light'] .theme-color-custom {
  border-top-color: rgba(0, 140, 200, 0.12);
}

html[data-theme='light'] .theme-color-custom-label {
  color: #5a7b93;
}

html[data-theme='light'] .theme-color-hex {
  color: #24475c;
}

html[data-theme='light'] .theme-color-reset {
  color: #5a7b93;
  border-color: rgba(0, 140, 200, 0.25);
}

html[data-theme='light'] .theme-color-reset:hover {
  color: #24475c;
  border-color: rgba(0, 140, 200, 0.55);
  background: rgba(0, 140, 200, 0.04);
}

/* ── 普通视觉模式 ── */
html[data-visual='normal'] .theme-color-btn {
  color: rgba(255, 255, 255, 0.78);
  background: #1f1f1f;
  border-color: #424242;
  box-shadow: none;
}

html[data-visual='normal'] .theme-color-btn:hover,
html[data-visual='normal'] .theme-color-btn.active {
  color: #69a9ff;
  border-color: #1677ff;
  box-shadow: none;
}

html[data-visual='normal'] .theme-color-swatch {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

html[data-visual='normal'] .theme-color-panel {
  background: #1f1f1f;
  border-color: #424242;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
  backdrop-filter: none;
}

html[data-visual='normal'] .theme-color-panel::before {
  background: #1f1f1f;
  border-color: #424242;
}

html[data-visual='normal'] .theme-color-custom {
  border-top-color: #303030;
}

html[data-visual='normal'] .theme-color-reset {
  border-color: #424242;
}

html[data-theme='light'][data-visual='normal'] .theme-color-btn {
  color: #434343;
  background: #ffffff;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .theme-color-btn:hover,
html[data-theme='light'][data-visual='normal'] .theme-color-btn.active {
  color: #1677ff;
  border-color: #1677ff;
}

html[data-theme='light'][data-visual='normal'] .theme-color-panel {
  background: #ffffff;
  border-color: #e5e7eb;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

html[data-theme='light'][data-visual='normal'] .theme-color-panel::before {
  background: #ffffff;
  border-color: #e5e7eb;
}

html[data-theme='light'][data-visual='normal'] .theme-color-custom {
  border-top-color: #f0f0f0;
}

html[data-theme='light'][data-visual='normal'] .theme-color-reset {
  border-color: #d9d9d9;
}
</style>
