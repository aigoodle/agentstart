<script setup lang="ts">
import { Card, Tag } from '../../ui';

/**
 * ProviderGallery — Dify-style "provider catalog" grid: one card per supported
 * provider, click to start configuring a model against it.
 *
 * Emits `select` with the provider name. Consumers wire this to their own
 * add-model dialog.
 */
import { computed } from 'vue';

import { modelTypeColor, modelTypeLabel } from '../composables/useProviderHub';
import type { ProviderView } from '../types';
import ProviderIcon from './ProviderIcon.vue';

interface Props {
  providers: ProviderView[];
  /** Optional title above the grid. */
  title?: string;
  /** Show a badge with predefined model count. */
  showPresetCount?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '可用供应商',
  showPresetCount: true,
});

const emit = defineEmits<{
  (e: 'select', providerName: string): void;
}>();

const sorted = computed(() =>
  [...props.providers].sort((a, b) => a.label.localeCompare(b.label)),
);
</script>

<template>
  <div class="ph-gallery">
    <div v-if="title" class="ph-gallery-title">{{ title }}</div>
    <div class="ph-grid">
      <Card v-for="p in sorted" :key="p.name" variant="management" :title="p.label" :subtitle="p.name" :description="'选择并配置供应商模型'" @click="emit('select', p.name)">
        <template #icon><ProviderIcon :name="p.name" :size="40" /></template>
        <div class="ph-caps"><Tag v-for="t in p.supportedModelTypes" :key="t" :color="modelTypeColor(t)">{{ modelTypeLabel(t) }}</Tag></div>
        <template v-if="showPresetCount" #meta>{{ p.predefinedModels.length }} 个预设模型</template>
      </Card>
    </div>
  </div>
</template>
<style scoped>
.ph-gallery-title { margin-bottom: 8px; font-size: 13px; font-weight: 500; color: var(--muted-foreground); }
.ph-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.ph-caps { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
