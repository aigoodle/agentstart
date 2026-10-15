<script setup lang="ts">
import { ref } from 'vue'
import {
  UiButton, UiInput, UiLabel, UiSeparator, UiSheet, UiSheetContent,
  UiSheetDescription, UiSheetHeader, UiSheetTitle,
} from 'vue-agent-start'
import { useTheme, type ThemeMode } from '../composables/useTheme'
import { useThemeColor } from '../composables/useThemeColor'
import { useVisualMode } from '../composables/useVisualMode'

defineOptions({ inheritAttrs: false })
const open = ref(false)
const { mode: themeMode, set: setTheme } = useTheme()
const { isTech, set: setVisual } = useVisualMode()
const { color, presets, setColor } = useThemeColor()
const modes: { value: ThemeMode; label: string }[] = [
  { value: 'light', label: '明亮' }, { value: 'dark', label: '暗黑' }, { value: 'system', label: '跟随系统' },
]
</script>

<template>
  <UiButton v-bind="$attrs" variant="ghost" size="icon-sm" aria-label="偏好设置" title="偏好设置" @click="open = true">⚙</UiButton>
  <UiSheet v-model:open="open">
    <UiSheetContent class="preferences-sheet" overlay-class="z-[5999]">
      <UiSheetHeader>
        <UiSheetTitle>偏好设置</UiSheetTitle>
        <UiSheetDescription>选择主题模式、视觉风格和主题颜色。</UiSheetDescription>
      </UiSheetHeader>
      <section class="preferences-section">
        <UiLabel>主题模式</UiLabel>
        <div class="preferences-options">
          <UiButton v-for="item in modes" :key="item.value" :variant="themeMode === item.value ? 'default' : 'outline'"
            :aria-pressed="themeMode === item.value" @click="setTheme(item.value)">{{ item.label }}</UiButton>
        </div>
      </section>
      <UiSeparator />
      <section class="preferences-section">
        <UiLabel>视觉风格</UiLabel>
        <div class="preferences-options">
          <UiButton :variant="isTech ? 'default' : 'outline'" :aria-pressed="isTech" @click="setVisual('tech')">科技模式</UiButton>
          <UiButton :variant="!isTech ? 'default' : 'outline'" :aria-pressed="!isTech" @click="setVisual('normal')">普通模式</UiButton>
        </div>
      </section>
      <UiSeparator />
      <section class="preferences-section">
        <UiLabel for="theme-color">主题颜色</UiLabel>
        <div class="preferences-colors">
          <UiButton v-for="c in presets" :key="c" variant="outline" size="icon-sm" :aria-label="`主题颜色 ${c}`"
            :aria-pressed="color.toLowerCase() === c.toLowerCase()" :style="{ backgroundColor: c, color: '#fff' }"
            @click="setColor(c)">{{ color.toLowerCase() === c.toLowerCase() ? '✓' : '' }}</UiButton>
        </div>
        <div class="preferences-color-custom">
          <UiInput id="theme-color" type="color" :model-value="color" aria-label="自定义主题颜色" @update:model-value="setColor(String($event))" />
          <UiInput :model-value="color" aria-label="主题颜色十六进制值" maxlength="7" @update:model-value="setColor(String($event))" />
        </div>
      </section>
    </UiSheetContent>
  </UiSheet>
</template>

<style>
.preferences-sheet { z-index: 6000; width: min(380px, 100vw); overflow-y: auto; }
.preferences-section { display: grid; gap: 12px; padding: 24px 0; }
.preferences-options { display: flex; gap: 8px; flex-wrap: wrap; }
.preferences-colors { display: grid; grid-template-columns: repeat(5, 32px); gap: 12px; }
.preferences-color-custom { display: grid; grid-template-columns: 48px 1fr; gap: 8px; }
</style>
