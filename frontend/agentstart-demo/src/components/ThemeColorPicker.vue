<script setup lang="ts">
import { UiButton, UiInput, UiLabel, UiPopover, UiPopoverContent, UiPopoverTrigger } from 'vue-agent-start'
import { useThemeColor } from '../composables/useThemeColor'
const { color, presets, setColor } = useThemeColor()
</script>
<template>
  <UiPopover>
    <UiPopoverTrigger as-child>
      <UiButton variant="outline" size="icon-sm" aria-label="主题颜色" title="主题颜色"><span class="theme-color-dot" :style="{ backgroundColor: color }" /></UiButton>
    </UiPopoverTrigger>
    <UiPopoverContent class="theme-color-panel" align="end">
      <UiLabel for="theme-color-popover">主题颜色</UiLabel>
      <div class="theme-color-grid">
        <UiButton v-for="c in presets" :key="c" variant="outline" size="icon-sm" :aria-label="`主题颜色 ${c}`"
          :aria-pressed="color.toLowerCase() === c.toLowerCase()" :style="{ backgroundColor: c }" @click="setColor(c)">
          {{ color.toLowerCase() === c.toLowerCase() ? '✓' : '' }}
        </UiButton>
      </div>
      <UiInput id="theme-color-popover" :model-value="color" maxlength="7" aria-label="自定义颜色" @update:model-value="setColor(String($event))" />
    </UiPopoverContent>
  </UiPopover>
</template>
<style>
.theme-color-dot { width: 16px; height: 16px; border-radius: 50%; }
.theme-color-panel { display: grid; gap: 12px; width: 236px; }
.theme-color-grid { display: grid; grid-template-columns: repeat(5, 32px); gap: 8px; }
</style>
