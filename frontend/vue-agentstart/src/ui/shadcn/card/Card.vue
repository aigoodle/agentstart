<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { cn } from "../utils"

const props = defineProps<{
  class?: HTMLAttributes["class"]
}>()
</script>

<template>
  <div
data-slot="card"     :class="
      cn(
        'flex flex-col rounded-xl border bg-card text-card-foreground shadow',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
