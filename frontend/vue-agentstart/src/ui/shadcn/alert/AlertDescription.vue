<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { cn } from "../utils"

const props = defineProps<{
  class?: HTMLAttributes["class"]
}>()
</script>

<template>
  <div data-slot="alert-description" :class="cn('text-sm [&_p]:leading-relaxed', props.class)">
    <slot />
  </div>
</template>
