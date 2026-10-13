<script setup lang="ts">
/**
 * Shared surface for demo routes that mount a full management-page component.
 * Keeping the shell here prevents every route from drifting in padding,
 * border, theme and scrolling behaviour.
 */
withDefaults(
  defineProps<{
    fill?: boolean
  }>(),
  {
    fill: false,
  },
)
</script>

<template>
  <div :class="[fill ? 'view-fill' : 'view-embed', 'hud-panel', 'hud-embed', 'demo-page-surface']">
    <slot />
  </div>
</template>

<style scoped>
.demo-page-surface {
  min-width: 0;
}
</style>
