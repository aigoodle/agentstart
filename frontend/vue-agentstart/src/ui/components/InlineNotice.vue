<script setup lang="ts">
import { Alert, AlertDescription } from '../shadcn/alert';
withDefaults(defineProps<{ type?: 'error' | 'warning' | 'info' }>(), { type: 'error' });
</script>
<template>
  <Alert class="as-inline-notice" :variant="type === 'error' ? 'destructive' : 'default'">
    <AlertDescription><slot /></AlertDescription>
  </Alert>
</template>
<style>
[data-slot='alert'].as-inline-notice { padding: 12px 14px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--card); color: var(--foreground); font-size: 13px; line-height: 1.6; }
[data-slot='alert'].as-inline-notice:has([data-slot='alert-description']) { display: block; }
[data-slot='alert'].as-inline-notice.text-destructive { color: var(--destructive); border-color: color-mix(in srgb, var(--destructive) 35%, var(--border)); }
</style>
