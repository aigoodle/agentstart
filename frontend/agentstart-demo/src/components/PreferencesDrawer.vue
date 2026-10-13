<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useTheme } from '../composables/useTheme'
import { useThemeColor } from '../composables/useThemeColor'
import { useVisualMode } from '../composables/useVisualMode'

const open = ref(false)

const { theme, isDark, set: setTheme } = useTheme()
const { isTech, set: setVisual } = useVisualMode()
const { color, presets, setColor } = useThemeColor()

// 系统主题检测
const systemDark = ref(
  typeof window !== 'undefined'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : true,
)

onMounted(() => {
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const handler = (e: MediaQueryListEvent) => (systemDark.value = e.matches)
  mq.addEventListener('change', handler)
  onBeforeUnmount(() => mq.removeEventListener('change', handler))
})

// 当前主题模式：'dark' | 'light' | 'system'
const themeMode = computed<'dark' | 'light' | 'system'>({
  get() {
    // 如果当前主题与系统主题一致，认为是 system
    return isDark.value === systemDark.value ? 'system' : theme.value
  },
  set(mode) {
    if (mode === 'system') {
      setTheme(systemDark.value ? 'dark' : 'light')
    } else {
      setTheme(mode)
    }
  },
})

function toggle() {
  open.value = !open.value
}

function close() {
  open.value = false
}

/** 点击遮罩层关闭 */
function onMaskClick(e: MouseEvent) {
  // 只有点击遮罩层本身（不是抽屉内容）才关闭
  if (e.target === e.currentTarget) {
    close()
  }
}

// ESC 关闭
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && open.value) {
    close()
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

// 调试：监听颜色变化
watch(color, (newColor) => {
  console.log('[PreferencesDrawer] Theme color changed to:', newColor)
})
</script>

<template>
  <div class="pref-drawer-root">
    <!-- 齿轮触发按钮 -->
    <button
      class="pref-gear-btn"
      type="button"
      title="偏好设置"
      aria-label="偏好设置"
      :class="{ active: open }"
      @click="toggle"
    >
      <svg viewBox="0 0 1024 1024" width="16" height="16" aria-hidden="true">
        <path
          fill="currentColor"
          d="M512 661.3c-82.5 0-149.3-66.9-149.3-149.3S429.5 362.7 512 362.7 661.3 429.5 661.3 512 594.5 661.3 512 661.3zm0-234.6c-47 0-85.3 38.3-85.3 85.3s38.3 85.3 85.3 85.3 85.3-38.3 85.3-85.3-38.3-85.3-85.3-85.3z"
        />
        <path
          fill="currentColor"
          d="M924.8 625.7l-65.5-56c3.1-26.1 4.8-52.7 4.8-79.7s-1.7-53.6-4.8-79.7l65.5-56c10.1-8.6 13.8-22.6 8.9-34.8l-0.9-2.2c-18.1-44.1-44.9-84.1-79.7-117.9l-1.8-1.8c-10.5-10.3-25.4-13.8-38.2-8.5l-81.3 33.5c-33.5-26.1-70.5-46.5-110.2-60.2l-15.3-85c-2.3-13.1-12.7-23.3-25.8-25.1l-2.4-0.3c-47-5.9-95.1-5.9-142.1 0l-2.4 0.3c-13.1 1.8-23.5 12-25.8 25.1l-15.3 85c-39.7 13.7-76.7 34.1-110.2 60.2l-81.3-33.5c-12.8-5.3-27.7-1.8-38.2 8.5l-1.8 1.8c-34.8 33.8-61.6 73.8-79.7 117.9l-0.9 2.2c-4.9 12.2-1.2 26.2 8.9 34.8l65.5 56c-3.1 26.1-4.8 52.7-4.8 79.7s1.7 53.6 4.8 79.7l-65.5 56c-10.1 8.6-13.8 22.6-8.9 34.8l0.9 2.2c18.1 44.1 44.9 84.1 79.7 117.9l1.8 1.8c10.5 10.3 25.4 13.8 38.2 8.5l81.3-33.5c33.5 26.1 70.5 46.5 110.2 60.2l15.3 85c2.3 13.1 12.7 23.3 25.8 25.1l2.4 0.3c47 5.9 95.1 5.9 142.1 0l2.4-0.3c13.1-1.8 23.5-12 25.8-25.1l15.3-85c39.7-13.7 76.7-34.1 110.2-60.2l81.3 33.5c12.8 5.3 27.7 1.8 38.2-8.5l1.8-1.8c34.8-33.8 61.6-73.8 79.7-117.9l0.9-2.2c4.9-12.2 1.2-26.2-8.9-34.8zM512 736c-123.7 0-224-100.3-224-224s100.3-224 224-224 224 100.3 224 224-100.3 224-224 224z"
        />
      </svg>
    </button>

    <!-- 遮罩层 + 抽屉面板 -->
    <Teleport to="body">
      <Transition name="pref-drawer-fade">
        <div
          v-if="open"
          class="pref-drawer-mask"
          @click="onMaskClick"
        >
          <Transition name="pref-drawer-slide">
            <div v-if="open" ref="drawerRef" class="pref-drawer" @click.stop>
              <div class="pref-drawer-header">
                <h3>偏好设置</h3>
                <button
                  class="pref-drawer-close"
                  type="button"
                  title="关闭"
                  @click="close"
                >
                  <svg viewBox="0 0 1024 1024" width="18" height="18" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M563.8 512l262.5-312.9c4.4-5.2 0.7-13.1-6.1-13.1h-79.8c-4.7 0-9.2 2.1-12.3 5.7L512 442.2 295.9 191.7c-3-3.6-7.5-5.7-12.3-5.7H203.8c-6.8 0-10.5 7.9-6.1 13.1L460.2 512 197.7 824.9c-4.4 5.2-0.7 13.1 6.1 13.1h79.8c4.7 0 9.2-2.1 12.3-5.7L512 581.8l216.1 250.5c3 3.6 7.5 5.7 12.3 5.7h79.8c6.8 0 10.5-7.9 6.1-13.1L563.8 512z"
                    />
                  </svg>
                </button>
              </div>

              <div class="pref-drawer-body">
                <!-- 主题模式 -->
                <section class="pref-section">
                  <h4 class="pref-section-title">主题模式</h4>
                  <div class="pref-theme-toggle">
                    <button
                      type="button"
                      class="pref-theme-card"
                      :class="{ active: themeMode === 'light' }"
                      @click="themeMode = 'light'"
                    >
                      <svg viewBox="0 0 1024 1024" width="28" height="28" aria-hidden="true">
                        <path
                          fill="currentColor"
                          d="M512 168c-16.8 0-33.2 1.4-49.2 4C340 192.8 244 286 220 408c-2.6 16-4 32.4-4 49.2c0 6.8 0.6 13.6 1 20.4c2.4 36.4 10.4 71.2 23.2 104c44 113.2 153.6 194 283.2 194c13.2 0 26.2-1 39-2.8C726 749.2 848 612 848 457.2c0-13.2-1-26.2-2.8-39C821.6 254.8 684.4 128 512 128c-13.2 0-26.2 1-39 2.8C486 168.4 499 168 512 168zM512 96c17.6 0 32 14.4 32 32v64c0 17.6-14.4 32-32 32s-32-14.4-32-32v-64c0-17.6 14.4-32 32-32zm0 768c17.6 0 32 14.4 32 32v64c0 17.6-14.4 32-32 32s-32-14.4-32-32v-64c0-17.6 14.4-32 32-32zM96 512c0-17.6 14.4-32 32-32h64c17.6 0 32 14.4 32 32s-14.4 32-32 32h-64c-17.6 0-32-14.4-32-32zm768 0c0-17.6 14.4-32 32-32h64c17.6 0 32 14.4 32 32s-14.4 32-32 32h-64c-17.6 0-32-14.4-32-32zM249.2 249.2c12.4-12.4 32.8-12.4 45.2 0l45.2 45.2c12.4 12.4 12.4 32.8 0 45.2s-32.8 12.4-45.2 0l-45.2-45.2c-12.4-12.4-12.4-32.8 0-45.2zm542.4 542.4c12.4-12.4 32.8-12.4 45.2 0l45.2 45.2c12.4 12.4 12.4 32.8 0 45.2s-32.8 12.4-45.2 0l-45.2-45.2c-12.4-12.4-12.4-32.8 0-45.2zM249.2 774.8c-12.4-12.4-12.4-32.8 0-45.2l45.2-45.2c12.4-12.4 32.8-12.4 45.2 0s12.4 32.8 0 45.2l-45.2 45.2c-12.4 12.4-32.8 12.4-45.2 0zm542.4-542.4c-12.4-12.4-12.4-32.8 0-45.2l45.2-45.2c12.4-12.4 32.8-12.4 45.2 0s12.4 32.8 0 45.2l-45.2 45.2c-12.4 12.4-32.8 12.4-45.2 0z"
                        />
                      </svg>
                      <span>明亮</span>
                    </button>
                    <button
                      type="button"
                      class="pref-theme-card"
                      :class="{ active: themeMode === 'dark' }"
                      @click="themeMode = 'dark'"
                    >
                      <svg viewBox="0 0 1024 1024" width="28" height="28" aria-hidden="true">
                        <path
                          fill="currentColor"
                          d="M512 160c-19.2 0-38 1.6-56.4 4.4C602.8 206 704 332.4 704 480c0 176.8-143.2 320-320 320c-90.8 0-173.2-38-231.6-98.8C188 774.4 261.6 832 352 860.4c18.4 2.8 37.2 4.4 56.4 4.4c229.6 0 416-186.4 416-416S741.6 160 512 160z"
                        />
                      </svg>
                      <span>暗黑</span>
                    </button>
                    <button
                      type="button"
                      class="pref-theme-card"
                      :class="{ active: themeMode === 'system' }"
                      @click="themeMode = 'system'"
                    >
                      <svg viewBox="0 0 1024 1024" width="28" height="28" aria-hidden="true">
                        <path
                          fill="currentColor"
                          d="M924.8 625.7l-65.5-56c3.1-26.1 4.8-52.7 4.8-79.7s-1.7-53.6-4.8-79.7l65.5-56c10.1-8.6 13.8-22.6 8.9-34.8l-0.9-2.2c-18.1-44.1-44.9-84.1-79.7-117.9l-1.8-1.8c-10.5-10.3-25.4-13.8-38.2-8.5l-81.3 33.5c-33.5-26.1-70.5-46.5-110.2-60.2l-15.3-85c-2.3-13.1-12.7-23.3-25.8-25.1l-2.4-0.3c-47-5.9-95.1-5.9-142.1 0l-2.4 0.3c-13.1 1.8-23.5 12-25.8 25.1l-15.3 85c-39.7 13.7-76.7 34.1-110.2 60.2l-81.3-33.5c-12.8-5.3-27.7-1.8-38.2 8.5l-1.8 1.8c-34.8 33.8-61.6 73.8-79.7 117.9l-0.9 2.2c-4.9 12.2-1.2 26.2 8.9 34.8l65.5 56c-3.1 26.1-4.8 52.7-4.8 79.7s1.7 53.6 4.8 79.7l-65.5 56c-10.1 8.6-13.8 22.6-8.9 34.8l0.9 2.2c18.1 44.1 44.9 84.1 79.7 117.9l1.8 1.8c10.5 10.3 25.4 13.8 38.2 8.5l81.3-33.5c33.5 26.1 70.5 46.5 110.2 60.2l15.3 85c2.3 13.1 12.7 23.3 25.8 25.1l2.4 0.3c47 5.9 95.1 5.9 142.1 0l2.4-0.3c13.1-1.8 23.5-12 25.8-25.1l15.3-85c39.7-13.7 76.7-34.1 110.2-60.2l81.3 33.5c12.8 5.3 27.7 1.8 38.2-8.5l1.8-1.8c34.8-33.8 61.6-73.8 79.7-117.9l0.9-2.2c4.9-12.2 1.2-26.2-8.9-34.8zM512 736c-123.7 0-224-100.3-224-224s100.3-224 224-224 224 100.3 224 224-100.3 224-224 224z"
                        />
                      </svg>
                      <span>跟随系统</span>
                    </button>
                  </div>
                </section>

                <!-- 视觉模式 -->
                <section class="pref-section">
                  <h4 class="pref-section-title">视觉风格</h4>
                  <div class="pref-visual-toggle">
                    <button
                      type="button"
                      class="pref-visual-card"
                      :class="{ active: isTech }"
                      @click="setVisual('tech')"
                    >
                      <div class="pref-visual-preview tech"></div>
                      <span>科技模式</span>
                    </button>
                    <button
                      type="button"
                      class="pref-visual-card"
                      :class="{ active: !isTech }"
                      @click="setVisual('normal')"
                    >
                      <div class="pref-visual-preview normal"></div>
                      <span>普通模式</span>
                    </button>
                  </div>
                </section>

                <!-- 主题颜色 -->
                <section class="pref-section">
                  <h4 class="pref-section-title">
                    主题颜色
                    <span class="pref-color-hex">{{ color.toUpperCase() }}</span>
                  </h4>
                  <div class="pref-color-grid">
                    <button
                      v-for="c in presets"
                      :key="c"
                      type="button"
                      class="pref-color-btn"
                      :class="{ active: color.toLowerCase() === c.toLowerCase() }"
                      :title="c"
                      :style="{ background: c }"
                      @click="setColor(c)"
                    >
                      <svg
                        v-if="color.toLowerCase() === c.toLowerCase()"
                        class="pref-color-check"
                        viewBox="0 0 1024 1024"
                        width="16"
                        height="16"
                        aria-hidden="true"
                      >
                        <path
                          fill="currentColor"
                          d="M910.5 226.5L397.4 739.6c-14.7 14.7-38.7 14.7-53.4 0L150.5 546.1c-14.7-14.7-14.7-38.7 0-53.4s38.7-14.7 53.4 0l166.8 166.8L857 173.1c14.7-14.7 38.7-14.7 53.4 0s14.8 38.7 0.1 53.4z"
                        />
                      </svg>
                    </button>
                  </div>
                  <div class="pref-color-custom">
                    <label class="pref-color-custom-label">自定义颜色</label>
                    <div class="pref-color-custom-row">
                      <span
                        class="pref-color-custom-preview"
                        :style="{ background: color }"
                      ></span>
                      <input
                        type="color"
                        class="pref-color-native-input"
                        :value="color"
                        @input="setColor(($event.target as HTMLInputElement).value)"
                      />
                      <span class="pref-color-hex-input">{{ color.toUpperCase() }}</span>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.pref-drawer-root {
  position: relative;
  display: inline-flex;
  margin-left: 4px;
}

/* ── 齿轮按钮 ── */
.pref-gear-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  font-family: inherit;
  color: #b8e8ff;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.45);
  border: 1px solid rgba(0, 204, 255, 0.28);
  border-radius: 7px;
  outline: none;
  transition: all 0.2s ease;
}

.pref-gear-btn:focus-visible {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.18);
}

.pref-gear-btn:hover,
.pref-gear-btn.active {
  border-color: rgba(0, 224, 255, 0.75);
  box-shadow: 0 0 10px rgba(0, 204, 255, 0.3);
  color: #eafcff;
}

.pref-gear-btn svg {
  transition: transform 0.3s ease;
}

.pref-gear-btn.active svg {
  transform: rotate(90deg);
}

/* ── 遮罩层 ── */
.pref-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(2px);
}

/* ── 抽屉面板 ── */
.pref-drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 2001;
  width: 320px;
  height: 100vh;
  background: rgba(3, 18, 38, 0.98);
  border-left: 1px solid rgba(0, 224, 255, 0.5);
  box-shadow:
    -12px 0 40px rgba(0, 0, 0, 0.7),
    0 0 20px rgba(0, 204, 255, 0.2);
  backdrop-filter: blur(16px);
  display: flex;
  flex-direction: column;
}

.pref-drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 204, 255, 0.2);
}

.pref-drawer-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #00e0ff;
  letter-spacing: 1px;
  text-shadow: 0 0 10px rgba(0, 224, 255, 0.6);
}

.pref-drawer-close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: #78a8d0;
  cursor: pointer;
  background: transparent;
  border: 1px solid rgba(0, 204, 255, 0.2);
  border-radius: 5px;
  outline: none;
  transition: all 0.15s ease;
}

.pref-drawer-close:hover {
  color: #eafcff;
  border-color: rgba(0, 224, 255, 0.6);
  background: rgba(0, 204, 255, 0.08);
}

.pref-drawer-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 204, 255, 0.25) transparent;
}

/* ── 分区 ── */
.pref-section {
  margin-bottom: 28px;
}

.pref-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #78a8d0;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.pref-color-hex {
  font-family: var(--hud-font-mono, monospace);
  font-size: 11px;
  color: #b8e8ff;
  letter-spacing: 0.5px;
  text-transform: none;
}

/* ── 主题模式卡片 ── */
.pref-theme-toggle {
  display: flex;
  gap: 12px;
}

.pref-theme-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  padding: 16px 12px;
  font-family: inherit;
  font-size: 12px;
  color: #9cc8e8;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.3);
  border: 1px solid rgba(0, 204, 255, 0.2);
  border-radius: 8px;
  outline: none;
  transition: all 0.18s ease;
}

.pref-theme-card svg {
  flex-shrink: 0;
  transition: all 0.18s ease;
}

.pref-theme-card:hover {
  border-color: rgba(0, 224, 255, 0.5);
  background: rgba(0, 140, 220, 0.1);
}

.pref-theme-card.active {
  color: #eafcff;
  border-color: rgba(0, 224, 255, 0.7);
  background: rgba(0, 204, 255, 0.12);
  box-shadow:
    0 0 12px rgba(0, 204, 255, 0.35),
    inset 0 0 10px rgba(0, 160, 255, 0.1);
  text-shadow: 0 0 8px rgba(0, 224, 255, 0.6);
}

.pref-theme-card.active svg {
  filter: drop-shadow(0 0 6px rgba(0, 224, 255, 0.8));
}

/* ── 视觉模式卡片 ── */
.pref-visual-toggle {
  display: flex;
  gap: 12px;
}

.pref-visual-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  font-family: inherit;
  font-size: 12px;
  color: #9cc8e8;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.3);
  border: 1px solid rgba(0, 204, 255, 0.2);
  border-radius: 8px;
  outline: none;
  transition: all 0.18s ease;
}

.pref-visual-card:hover {
  border-color: rgba(0, 224, 255, 0.5);
  background: rgba(0, 140, 220, 0.1);
}

.pref-visual-card.active {
  color: #eafcff;
  border-color: rgba(0, 224, 255, 0.7);
  background: rgba(0, 204, 255, 0.12);
  box-shadow:
    0 0 12px rgba(0, 204, 255, 0.35),
    inset 0 0 10px rgba(0, 160, 255, 0.1);
  text-shadow: 0 0 8px rgba(0, 224, 255, 0.6);
}

.pref-visual-preview {
  height: 48px;
  border-radius: 5px;
  border: 1px solid rgba(0, 204, 255, 0.15);
}

.pref-visual-preview.tech {
  background:
    linear-gradient(135deg, rgba(0, 224, 255, 0.15), rgba(0, 128, 255, 0.08)),
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 8px,
      rgba(0, 204, 255, 0.08) 8px,
      rgba(0, 204, 255, 0.08) 9px
    ),
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent 8px,
      rgba(0, 204, 255, 0.08) 8px,
      rgba(0, 204, 255, 0.08) 9px
    );
  box-shadow: inset 0 0 8px rgba(0, 204, 255, 0.2);
}

.pref-visual-preview.normal {
  background: #1f1f1f;
  border-color: #424242;
}

/* ── 颜色网格 ── */
.pref-color-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.pref-color-btn {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  padding: 0;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 8px;
  outline: none;
  transition: all 0.15s ease;
}

.pref-color-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.3);
}

.pref-color-btn.active {
  border-color: rgba(255, 255, 255, 0.9);
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.2),
    0 0 14px currentColor;
}

.pref-color-check {
  color: #fff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.5));
}

/* ── 自定义颜色 ── */
.pref-color-custom {
  padding-top: 14px;
  border-top: 1px solid rgba(0, 204, 255, 0.15);
}

.pref-color-custom-label {
  display: block;
  margin-bottom: 10px;
  font-size: 11px;
  color: #78a8d0;
  letter-spacing: 1px;
}

.pref-color-custom-row {
  position: relative;
  display: flex;
  gap: 10px;
  align-items: center;
}

.pref-color-custom-preview {
  position: relative;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 6px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
  pointer-events: none;
}

.pref-color-native-input {
  position: absolute;
  left: 0;
  width: 28px;
  height: 28px;
  padding: 0;
  opacity: 0;
  cursor: pointer;
  border: none;
}

.pref-color-hex-input {
  font-family: var(--hud-font-mono, monospace);
  font-size: 13px;
  color: #b8e8ff;
  letter-spacing: 0.5px;
}

/* ── 遮罩层淡入动画 ── */
.pref-drawer-fade-enter-active,
.pref-drawer-fade-leave-active {
  transition: opacity 0.28s ease;
}

.pref-drawer-fade-enter-from,
.pref-drawer-fade-leave-to {
  opacity: 0;
}

/* ── 抽屉滑入动画 ── */
.pref-drawer-slide-enter-active,
.pref-drawer-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.pref-drawer-slide-enter-from,
.pref-drawer-slide-leave-to {
  transform: translateX(100%);
}

/* ── 明亮模式 ── */
html[data-theme='light'] .pref-gear-btn {
  color: #2a5a78;
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 140, 200, 0.3);
}

html[data-theme='light'] .pref-gear-btn:hover,
html[data-theme='light'] .pref-gear-btn.active {
  border-color: rgba(0, 140, 200, 0.6);
  box-shadow: 0 0 10px rgba(0, 140, 200, 0.15);
}

html[data-theme='light'] .pref-drawer {
  background: #ffffff;
  border-left-color: rgba(0, 140, 200, 0.35);
  box-shadow: -12px 0 40px rgba(0, 0, 0, 0.12);
}

html[data-theme='light'] .pref-drawer-header {
  border-bottom-color: rgba(0, 140, 200, 0.2);
}

html[data-theme='light'] .pref-drawer-header h3 {
  color: #007aa8;
  text-shadow: none;
}

html[data-theme='light'] .pref-drawer-close {
  color: #5a7b93;
  border-color: rgba(0, 140, 200, 0.2);
}

html[data-theme='light'] .pref-drawer-close:hover {
  color: #24475c;
  border-color: rgba(0, 140, 200, 0.5);
  background: rgba(0, 140, 200, 0.05);
}

html[data-theme='light'] .pref-section-title {
  color: #5a7b93;
}

html[data-theme='light'] .pref-color-hex {
  color: #24475c;
}

html[data-theme='light'] .pref-theme-card {
  color: #24475c;
  background: rgba(255, 255, 255, 0.5);
  border-color: rgba(0, 140, 200, 0.2);
}

html[data-theme='light'] .pref-theme-card:hover {
  background: rgba(0, 140, 200, 0.06);
}

html[data-theme='light'] .pref-theme-card.active {
  color: #00698f;
  border-color: rgba(0, 160, 220, 0.55);
  background: rgba(0, 170, 230, 0.12);
  box-shadow: 0 0 10px rgba(0, 160, 220, 0.18);
  text-shadow: none;
}

html[data-theme='light'] .pref-visual-card {
  color: #24475c;
  background: rgba(255, 255, 255, 0.5);
  border-color: rgba(0, 140, 200, 0.2);
}

html[data-theme='light'] .pref-visual-card:hover {
  background: rgba(0, 140, 200, 0.06);
}

html[data-theme='light'] .pref-visual-card.active {
  color: #00698f;
  border-color: rgba(0, 160, 220, 0.55);
  background: rgba(0, 170, 230, 0.12);
  box-shadow: 0 0 10px rgba(0, 160, 220, 0.18);
  text-shadow: none;
}

html[data-theme='light'] .pref-visual-preview.normal {
  background: #ffffff;
  border-color: #d9d9d9;
}

html[data-theme='light'] .pref-color-custom {
  border-top-color: rgba(0, 140, 200, 0.15);
}

html[data-theme='light'] .pref-color-custom-label {
  color: #5a7b93;
}

html[data-theme='light'] .pref-color-hex-input {
  color: #24475c;
}

html[data-theme='light'] .pref-color-btn.active {
  border-color: #262626;
}

/* ── 普通视觉模式 ── */
html[data-visual='normal'] .pref-gear-btn {
  color: rgba(255, 255, 255, 0.78);
  background: #1f1f1f;
  border-color: #424242;
  box-shadow: none;
}

html[data-visual='normal'] .pref-gear-btn:hover,
html[data-visual='normal'] .pref-gear-btn.active {
  color: #69a9ff;
  border-color: #1677ff;
  box-shadow: none;
}

html[data-visual='normal'] .pref-drawer {
  background: #1f1f1f;
  border-left-color: #424242;
  box-shadow: -12px 0 40px rgba(0, 0, 0, 0.5);
  backdrop-filter: none;
}

html[data-visual='normal'] .pref-drawer-header {
  border-bottom-color: #303030;
}

html[data-visual='normal'] .pref-drawer-header h3 {
  color: rgba(255, 255, 255, 0.88);
  text-shadow: none;
}

html[data-visual='normal'] .pref-drawer-close {
  color: rgba(255, 255, 255, 0.58);
  border-color: #424242;
}

html[data-visual='normal'] .pref-section-title {
  color: rgba(255, 255, 255, 0.65);
}

html[data-visual='normal'] .pref-color-hex {
  color: rgba(255, 255, 255, 0.85);
}

html[data-visual='normal'] .pref-theme-card {
  color: rgba(255, 255, 255, 0.72);
  background: #141414;
  border-color: #303030;
}

html[data-visual='normal'] .pref-theme-card.active {
  color: #1677ff;
  border-color: #1677ff;
  background: rgba(22, 119, 255, 0.12);
  box-shadow: none;
  text-shadow: none;
}

html[data-visual='normal'] .pref-visual-card {
  color: rgba(255, 255, 255, 0.72);
  background: #141414;
  border-color: #303030;
}

html[data-visual='normal'] .pref-visual-card.active {
  color: #1677ff;
  border-color: #1677ff;
  background: rgba(22, 119, 255, 0.12);
  box-shadow: none;
  text-shadow: none;
}

html[data-visual='normal'] .pref-color-custom {
  border-top-color: #303030;
}

html[data-visual='normal'] .pref-color-custom-label {
  color: rgba(255, 255, 255, 0.58);
}

html[data-theme='light'][data-visual='normal'] .pref-gear-btn {
  color: #434343;
  background: #ffffff;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .pref-gear-btn:hover,
html[data-theme='light'][data-visual='normal'] .pref-gear-btn.active {
  color: #1677ff;
  border-color: #1677ff;
}

html[data-theme='light'][data-visual='normal'] .pref-drawer {
  background: #ffffff;
  border-left-color: #e5e7eb;
  box-shadow: -12px 0 40px rgba(0, 0, 0, 0.1);
}

html[data-theme='light'][data-visual='normal'] .pref-drawer-header {
  border-bottom-color: #f0f0f0;
}

html[data-theme='light'][data-visual='normal'] .pref-drawer-header h3 {
  color: #262626;
}

html[data-theme='light'][data-visual='normal'] .pref-drawer-close {
  color: #8c8c8c;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .pref-section-title {
  color: #595959;
}

html[data-theme='light'][data-visual='normal'] .pref-theme-card {
  color: #434343;
  background: #fafafa;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .pref-theme-card.active {
  color: #1677ff;
  border-color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
}

html[data-theme='light'][data-visual='normal'] .pref-visual-card {
  color: #434343;
  background: #fafafa;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .pref-visual-card.active {
  color: #1677ff;
  border-color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
}

html[data-theme='light'][data-visual='normal'] .pref-color-custom {
  border-top-color: #f0f0f0;
}

/* ── 明亮模式遮罩层 ── */
html[data-theme='light'] .pref-drawer-mask {
  background: rgba(0, 0, 0, 0.25);
}

/* ── 普通视觉模式遮罩层 ── */
html[data-visual='normal'] .pref-drawer-mask {
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: none;
}
</style>
