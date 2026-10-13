<script setup lang="ts">
import { useVisualMode } from '../composables/useVisualMode'

const { isTech, toggle } = useVisualMode()
</script>

<template>
  <button
    class="visual-mode-toggle"
    type="button"
    :title="isTech ? '切换到普通模式' : '切换到科技模式'"
    :aria-label="isTech ? '切换到普通模式' : '切换到科技模式'"
    @click="toggle"
  >
    <span class="visual-mode-icon" :class="{ tech: isTech }" aria-hidden="true"></span>
    <span class="visual-mode-label">{{ isTech ? '科技模式' : '普通模式' }}</span>
  </button>
</template>

<style scoped>
.visual-mode-toggle {
  display: flex;
  flex: 0 0 104px;
  gap: 7px;
  justify-content: center;
  align-items: center;
  width: 104px;
  height: 30px;
  padding: 0 10px;
  font-family: inherit;
  font-size: 10.5px;
  font-weight: 600;
  color: #b8e8ff;
  letter-spacing: 0.5px;
  cursor: pointer;
  background: rgba(0, 30, 60, 0.45);
  border: 1px solid rgba(0, 204, 255, 0.28);
  border-radius: 7px;
  outline: none;
  transition: all 0.2s ease;
}

.visual-mode-toggle:focus-visible {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.18);
}

.visual-mode-toggle:hover {
  color: #eafcff;
  border-color: rgba(0, 224, 255, 0.75);
  box-shadow: 0 0 10px rgba(0, 204, 255, 0.3);
}

.visual-mode-icon {
  width: 10px;
  height: 10px;
  flex: none;
  background: transparent;
  border: 1.5px solid currentColor;
  border-radius: 50%;
}

.visual-mode-icon.tech {
  background: #00e0ff;
  border-color: #00e0ff;
  border-radius: 2px;
  box-shadow: 0 0 6px rgba(0, 224, 255, 0.7);
}

.visual-mode-label {
  display: inline-block;
  width: 52px;
  white-space: nowrap;
  text-align: center;
}

html[data-theme='light'] .visual-mode-toggle {
  color: #2a5a78;
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(0, 140, 200, 0.3);
}

html[data-visual='normal'] .visual-mode-toggle {
  color: rgba(255, 255, 255, 0.78);
  background: #1f1f1f;
  border-color: #424242;
  border-radius: 6px;
  box-shadow: none;
}

html[data-visual='normal'] .visual-mode-toggle:hover {
  color: #69a9ff;
  border-color: #1677ff;
  box-shadow: none;
}

html[data-visual='normal'] .visual-mode-icon {
  color: rgba(255, 255, 255, 0.65);
}

html[data-theme='light'][data-visual='normal'] .visual-mode-toggle {
  color: #434343;
  background: #ffffff;
  border-color: #d9d9d9;
}

html[data-theme='light'][data-visual='normal'] .visual-mode-toggle:hover {
  color: #1677ff;
  border-color: #1677ff;
}

html[data-theme='light'][data-visual='normal'] .visual-mode-icon {
  color: #8c8c8c;
}
</style>
