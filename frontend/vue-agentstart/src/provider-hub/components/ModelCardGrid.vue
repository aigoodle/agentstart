<script setup lang="ts">
/**
 * ModelCardGrid — Dify-style card grid of already-configured models, grouped
 * by provider. Each card has a hover "⋯" menu wired via events so the host app
 * decides which ops it wants (edit credentials / set default / delete / test).
 */
import { computed, ref } from 'vue';

import { Card } from '../../ui';
import {
  modelTypeColor,
  modelTypeLabel,
  useProviderHub,
} from '../composables/useProviderHub';
import type { ModelEntity, ModelTestResult, ProviderView } from '../types';
import ProviderIcon from './ProviderIcon.vue';

interface Props {
  models: ModelEntity[];
  providers: ProviderView[];
  /** When true, expose an inline "🔍 测试" button on every card. */
  showTestButton?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showTestButton: true,
});

const emit = defineEmits<{
  (e: 'editCredentials', model: ModelEntity): void;
  (e: 'setDefault', model: ModelEntity): void;
  (e: 'delete', model: ModelEntity): void;
  (e: 'test', model: ModelEntity, result: ModelTestResult): void;
}>();

const { testModel } = useProviderHub();

// Per-model test result cache keyed by model id. Persists for the lifetime of
// the component so a user can eyeball recent latencies without re-testing.
const testResults = ref<
  Record<string, { ok: boolean; text: string; ts: number }>
>({});
const testingId = ref<null | string>(null);

async function runTest(m: ModelEntity) {
  testingId.value = m.id;
  try {
    const r = await testModel(m.id);
    const badge = r.ok ? '✓' : '✗';
    const suffix = r.ok
      ? r.kind === 'embedding'
        ? `dim ${r.dimensions}`
        : (r.snippet ?? 'ok').slice(0, 40)
      : (r.error ?? 'failed');
    testResults.value[m.id] = {
      ok: !!r.ok,
      text: `${badge} ${r.latencyMs}ms · ${suffix}`,
      ts: Date.now(),
    };
    emit('test', m, r);
  } catch (e: any) {
    const txt = `✗ ${e?.message ?? e}`;
    testResults.value[m.id] = { ok: false, text: txt, ts: Date.now() };
    emit('test', m, { ok: false, latencyMs: 0, error: String(e) });
  } finally {
    testingId.value = null;
  }
}

const grouped = computed(() => {
  const acc: Record<string, ModelEntity[]> = {};
  for (const m of props.models) {
    (acc[m.providerName] ??= []).push(m);
  }
  return acc;
});

const providersWithModels = computed(() =>
  Object.keys(grouped.value).sort(),
);

function providerLabel(name: string): string {
  return props.providers.find((p) => p.name === name)?.label ?? name;
}
</script>

<template>
  <div>
    <div
      v-for="provName in providersWithModels"
      :key="provName"
      class="ph-group"
    >
      <div class="ph-group-header">
        <ProviderIcon :name="provName" :size="28" />
        <div class="ph-group-name">{{ providerLabel(provName) }}</div>
        <div class="ph-group-count">
          {{ grouped[provName]?.length ?? 0 }} 个模型
        </div>
      </div>
      <div class="ph-model-grid">
        <Card variant="management"
          v-for="m in grouped[provName] ?? []"
          :key="m.id"
          class="ph-model-card"
          :title="m.modelName"
          interactive
        >
          <template #icon>
            <ProviderIcon :name="m.providerName" :size="40" />
          </template>

          <template #subtitle>
            <span
              class="ph-type-tag"
              :data-color="modelTypeColor(m.modelType)"
            >
              {{ modelTypeLabel(m.modelType) }}
            </span>
            <span v-if="m.isDefault" class="ph-default-tag">默认</span>
          </template>

          <template #description>
            <span
              v-if="testResults[m.id]"
              :class="testResults[m.id]!.ok ? 'ph-ok' : 'ph-fail'"
            >
              {{ testResults[m.id]!.text }}
            </span>
            <span v-else>{{ providerLabel(m.providerName) }} · 凭证加密存储</span>
          </template>

          <template #meta>
            <span class="ph-status">
              {{ m.enabled ? '✅ 已启用' : '⛔ 已停用' }}
            </span>
          </template>

          <template #actions>
            <button
              v-if="showTestButton"
              class="ph-btn"
              :disabled="testingId === m.id"
              @click.stop="runTest(m)"
            >
              {{ testingId === m.id ? '测试中...' : '🔍 测试' }}
            </button>
            <button
              class="ph-btn"
              @click.stop="emit('editCredentials', m)"
            >
              编辑凭证
            </button>
            <button
              v-if="!m.isDefault"
              class="ph-btn"
              @click.stop="emit('setDefault', m)"
            >
              设为默认
            </button>
            <button
              class="ph-btn ph-btn-danger"
              @click.stop="emit('delete', m)"
            >
              删除
            </button>
          </template>
        </Card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ph-group {
  margin-bottom: 20px;
}
.ph-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.ph-group-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 16px;
  flex-shrink: 0;
}
.ph-group-name {
  font-size: 15px;
  font-weight: 500;
  color: #111827;
}
.ph-group-count {
  padding: 1px 8px;
  font-size: 11px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
}
.ph-model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.ph-model-card {
}
/* Card footer: status on the left, action buttons on the right,
   anchored to the bottom of the card. */
.ph-model-card:deep(.as-management-card__footer) {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 10px;
}
.ph-model-card:deep(.as-management-card__actions) {
  display: inline-flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin: 0;
  padding: 0;
  border: 0;
  flex: none;
}
.ph-model-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
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
.ph-title-wrap {
  min-width: 0;
  flex: 1;
}
.ph-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ph-tags {
  margin-top: 4px;
  display: flex;
  gap: 4px;
}
.ph-type-tag {
  padding: 1px 6px;
  font-size: 11px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
}
.ph-type-tag[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.ph-type-tag[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.ph-default-tag {
  padding: 1px 6px;
  font-size: 11px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1e40af;
}
.ph-desc {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7280;
  min-height: 18px;
}
.ph-ok {
  color: #059669;
  font-weight: 500;
}
.ph-fail {
  color: #dc2626;
  font-weight: 500;
}
.ph-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f3f4f6;
  padding-top: 8px;
}
.ph-status {
  font-size: 11px;
  color: #6b7280;
}
.ph-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.ph-btn {
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #4338ca;
  cursor: pointer;
}
.ph-btn:hover {
  background: #eef2ff;
}
.ph-btn:disabled {
  color: #9ca3af;
  cursor: not-allowed;
}
.ph-btn-danger {
  color: #dc2626;
}
.ph-btn-danger:hover {
  background: #fef2f2;
}
</style>
