<script setup lang="ts">
/**
 * 极简的 SvgIcon 兜底实现。
 * 宿主应用通常有自己的 icon 方案（iconify / svg-sprite），
 * 建议通过 provide 覆盖 <SvgIcon /> 或在使用侧用 slot 替换。
 */
defineProps<{ name?: string; size?: number | string; color?: string }>();
</script>

<template>
  <span
    class="agent-flow-svg-icon"
    :style="{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: (typeof size === 'number' ? `${size}px` : size) || '1em',
      height: (typeof size === 'number' ? `${size}px` : size) || '1em',
      color: color,
    }"
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      width="100%"
      height="100%"
      fill="currentColor"
    >
      <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="1.5" />
    </svg>
  </span>
</template>
