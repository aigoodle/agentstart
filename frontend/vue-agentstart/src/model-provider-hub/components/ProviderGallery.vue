<script setup lang="ts">
/**
 * ProviderGallery — Dify-style "provider catalog" grid: one card per supported
 * provider, click to start configuring a model against it.
 *
 * Emits `select` with the provider name. Consumers wire this to their own
 * add-model dialog.
 */
import { computed } from 'vue';

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
      <div
        v-for="p in sorted"
        :key="p.name"
        class="ph-card"
        :title="`点击添加 ${p.label} 模型`"
        @click="emit('select', p.name)"
      >
        <ProviderIcon :name="p.name" :size="40" />
        <div class="ph-body">
          <div class="ph-name">{{ p.label }}</div>
          <div class="ph-caps">
            <span
              v-for="t in p.supportedModelTypes"
              :key="t"
              class="ph-cap"
            >
              {{ t }}
            </span>
          </div>
          <div v-if="showPresetCount" class="ph-hint">
            {{ p.predefinedModels.length }} 个预设模型
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ph-gallery-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
}
.ph-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.ph-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}
.ph-card:hover {
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.06);
}
.ph-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 22px;
  flex-shrink: 0;
}
.ph-body {
  min-width: 0;
  flex: 1;
}
.ph-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.ph-caps {
  margin-top: 4px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.ph-cap {
  display: inline-block;
  padding: 1px 6px;
  font-size: 10px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
}
.ph-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
}
</style>
