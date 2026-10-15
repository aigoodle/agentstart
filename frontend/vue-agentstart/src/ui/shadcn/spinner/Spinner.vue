<script setup lang="ts">
import type { HTMLAttributes } from 'vue';
import { Loader2Icon } from '@lucide/vue';
import { cn } from '../utils';

const props = defineProps<{ class?: HTMLAttributes['class'] }>();
</script>

<template>
  <Loader2Icon data-slot="spinner" role="status" aria-label="加载中"
    :class="cn('size-4 animate-spin motion-reduce:animate-none', props.class)" />
</template>
