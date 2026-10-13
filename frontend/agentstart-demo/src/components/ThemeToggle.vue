<script setup lang="ts">
import { useTheme } from '../composables/useTheme'

const { isDark, toggle } = useTheme()
</script>

<template>
  <button
    class="hud-theme-toggle"
    type="button"
    :title="isDark ? '切换到明亮模式' : '切换到暗黑模式'"
    :aria-label="isDark ? '切换到明亮模式' : '切换到暗黑模式'"
    @click="toggle"
  >
    <span class="hud-theme-track">
      <span class="hud-theme-icons">
        <!-- 月亮（暗黑模式图标） -->
        <svg
          class="hud-theme-icon moon"
          viewBox="0 0 1024 1024"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <path
            fill="currentColor"
            d="M512 160c-19.2 0-38 1.6-56.4 4.4C602.8 206 704 332.4 704 480c0 176.8-143.2 320-320 320c-90.8 0-173.2-38-231.6-98.8C188 774.4 261.6 832 352 860.4c18.4 2.8 37.2 4.4 56.4 4.4c229.6 0 416-186.4 416-416S741.6 160 512 160z"
          />
        </svg>
        <!-- 太阳（明亮模式图标） -->
        <svg
          class="hud-theme-icon sun"
          viewBox="0 0 1024 1024"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <path
            fill="currentColor"
            d="M512 168c-16.8 0-33.2 1.4-49.2 4C340 192.8 244 286 220 408c-2.6 16-4 32.4-4 49.2c0 6.8.6 13.6 1 20.4c2.4 36.4 10.4 71.2 23.2 104c44 113.2 153.6 194 283.2 194c13.2 0 26.2-1 39-2.8C726 749.2 848 612 848 457.2c0-13.2-1-26.2-2.8-39C821.6 254.8 684.4 128 512 128c-13.2 0-26.2 1-39 2.8C486 168.4 499 168 512 168zM512 96c17.6 0 32 14.4 32 32v64c0 17.6-14.4 32-32 32s-32-14.4-32-32v-64c0-17.6 14.4-32 32-32zm0 768c17.6 0 32 14.4 32 32v64c0 17.6-14.4 32-32 32s-32-14.4-32-32v-64c0-17.6 14.4-32 32-32zM96 512c0-17.6 14.4-32 32-32h64c17.6 0 32 14.4 32 32s-14.4 32-32 32h-64c-17.6 0-32-14.4-32-32zm768 0c0-17.6 14.4-32 32-32h64c17.6 0 32 14.4 32 32s-14.4 32-32 32h-64c-17.6 0-32-14.4-32-32zM249.2 249.2c12.4-12.4 32.8-12.4 45.2 0l45.2 45.2c12.4 12.4 12.4 32.8 0 45.2s-32.8 12.4-45.2 0l-45.2-45.2c-12.4-12.4-12.4-32.8 0-45.2zm542.4 542.4c12.4-12.4 32.8-12.4 45.2 0l45.2 45.2c12.4 12.4 12.4 32.8 0 45.2s-32.8 12.4-45.2 0l-45.2-45.2c-12.4-12.4-12.4-32.8 0-45.2zM249.2 774.8c-12.4-12.4-12.4-32.8 0-45.2l45.2-45.2c12.4-12.4 32.8-12.4 45.2 0s12.4 32.8 0 45.2l-45.2 45.2c-12.4 12.4-32.8 12.4-45.2 0zm542.4-542.4c-12.4-12.4-12.4-32.8 0-45.2l45.2-45.2c12.4-12.4 32.8-12.4 45.2 0s12.4 32.8 0 45.2l-45.2 45.2c-12.4 12.4-32.8 12.4-45.2 0z"
          />
        </svg>
      </span>
      <span class="hud-theme-thumb" :class="{ light: !isDark }"></span>
    </span>
    <span class="hud-theme-label">
      {{ isDark ? 'DARK' : 'LIGHT' }}
    </span>
  </button>
</template>

<style scoped>
.hud-theme-toggle {
  display: flex;
  flex: 0 0 92px;
  gap: 8px;
  justify-content: center;
  align-items: center;
  width: 92px;
  height: 30px;
  padding: 0 10px 0 6px;
  font-family: inherit;
  color: #b8e8ff;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.45);
  border: 1px solid rgba(0, 204, 255, 0.28);
  border-radius: 7px;
  outline: none;
  transition: all 0.2s ease;
}

.hud-theme-toggle:focus-visible {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.18);
}

.hud-theme-toggle:hover {
  border-color: rgba(0, 224, 255, 0.75);
  box-shadow: 0 0 10px rgba(0, 204, 255, 0.3);
  color: #eafcff;
}

.hud-theme-track {
  position: relative;
  width: 36px;
  height: 18px;
  border-radius: 10px;
  background: linear-gradient(90deg, #003b66, #0077aa);
  border: 1px solid rgba(0, 224, 255, 0.4);
  box-shadow: inset 0 0 6px rgba(0, 204, 255, 0.35);
  transition: background 0.25s ease;
}

html[data-theme='light'] .hud-theme-track {
  background: linear-gradient(90deg, #7ec8e8, #ffd27a);
}

.hud-theme-icons {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 3px;
  pointer-events: none;
}

.hud-theme-icon {
  width: 10px;
  height: 10px;
  transition: opacity 0.2s ease, color 0.2s ease;
}

.hud-theme-icon.moon {
  color: #d4f1ff;
  opacity: 1;
}

.hud-theme-icon.sun {
  color: #ffd27a;
  opacity: 0.45;
}

html[data-theme='light'] .hud-theme-icon.moon {
  opacity: 0.45;
}

html[data-theme='light'] .hud-theme-icon.sun {
  opacity: 1;
  color: #ff9d2a;
}

.hud-theme-thumb {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #00e0ff;
  box-shadow: 0 0 8px rgba(0, 224, 255, 0.9);
  transition:
    transform 0.25s cubic-bezier(0.4, 0, 0.2, 1),
    background 0.25s ease,
    box-shadow 0.25s ease;
}

.hud-theme-thumb.light {
  transform: translateX(18px);
  background: #ffb84d;
  box-shadow: 0 0 8px rgba(255, 157, 42, 0.9);
}

.hud-theme-label {
  display: inline-block;
  width: 34px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: currentColor;
  text-shadow: 0 0 8px rgba(0, 224, 255, 0.4);
  text-align: center;
}

html[data-theme='light'] .hud-theme-label {
  color: #2a5a78;
  text-shadow: none;
}

html[data-theme='light'] .hud-theme-toggle {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 140, 200, 0.3);
  color: #2a5a78;
}

html[data-theme='light'] .hud-theme-toggle:hover {
  border-color: rgba(0, 140, 200, 0.6);
  box-shadow: 0 0 10px rgba(0, 140, 200, 0.15);
}

html[data-visual='normal'] .hud-theme-toggle {
  color: rgba(255, 255, 255, 0.78);
  background: #1f1f1f;
  border-color: #424242;
  box-shadow: none;
}

html[data-visual='normal'] .hud-theme-toggle:hover {
  color: #69a9ff;
  border-color: #1677ff;
  box-shadow: none;
}

html[data-theme='light'][data-visual='normal'] .hud-theme-toggle {
  color: #434343;
  background: #ffffff;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .hud-theme-toggle:hover {
  color: #1677ff;
  border-color: #1677ff;
}
</style>
