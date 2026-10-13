<script setup lang="ts">
/**
 * DefaultModelsPanel — Dify/reference-parity "system default models" strip.
 *
 * Layout is a row per model type (LLM / TEXT_EMBEDDING / RERANK / SPEECH2TEXT /
 * TTS). Each row is a single a-select whose options are grouped per provider,
 * mirroring the reference project's DefaultModel.vue: opt-group header shows
 * "{provider} - {description}", options list each enabled model with an icon
 * badge + a modelType tag.
 *
 * Auto-save on selection — no modal, no OK button. Sends
 * PUT /model-providers/{provider}/models/{model}/default?modelType=... which
 * writes to agent_tenant_default_model.
 */
import { computed, onMounted, ref, watch } from 'vue';

import {
  modelTypeColor,
  modelTypeLabel,
  useProviderHub,
} from '../composables/useProviderHub';
import type {
  GroupedProviderView,
  ModelEntity,
  ModelType,
} from '../types';
import GroupedModelSelect, {
  type GroupedModelSelectOption,
} from './GroupedModelSelect.vue';

interface Props {
  tenantId?: string;
  /**
   * Rows to render, in order. Each row maps to one a-select. Defaults follow
   * the reference project (adds SPEECH2TEXT alongside LLM/EMBED/RERANK/TTS).
   */
  types?: Array<{ desc: string; modelType: ModelType }>;
}

const props = withDefaults(defineProps<Props>(), {
  types: () => [
    { modelType: 'LLM', desc: '系统推理模型' },
    { modelType: 'VIDEO', desc: '视频生成模型' },
    { modelType: 'TEXT_EMBEDDING', desc: 'Embedding 模型' },
    { modelType: 'RERANK', desc: 'Rerank 模型' },
    { modelType: 'SPEECH2TEXT', desc: '语音转文本模型' },
    { modelType: 'TTS', desc: '文本转语音模型' },
  ],
});

const emit = defineEmits<{
  (e: 'change'): void;
}>();

const {
  listDefaults,
  listModelsGroupedByType,
  setModelDefaultByName,
} = useProviderHub();

/** provider list per model type — feeds each row's a-select opt-groups. */
const providerModelMap = ref<Record<string, GroupedProviderView[]>>({});
/** Currently-selected model.id per type. Composite `${provider}::${model}::${type}`. */
const selected = ref<Record<string, string | undefined>>({});
const loading = ref(false);
const savingType = ref<null | string>(null);

async function refresh() {
  loading.value = true;
  try {
    const [groups, defaults] = await Promise.all([
      listModelsGroupedByType(props.tenantId),
      listDefaults(props.tenantId),
    ]);
    providerModelMap.value = groups;

    // Seed the Select value from the tenant's saved defaults. The grouped
    // response uses composite ids; recompute the same shape from ModelEntity
    // so v-model matches an option exactly.
    const next: Record<string, string | undefined> = {};
    for (const row of props.types) {
      const t = row.modelType;
      const def = defaults[t] as ModelEntity | undefined;
      next[t] = def
        ? `${def.providerName}::${def.modelName}::${t}`
        : undefined;
    }
    selected.value = next;
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
watch(() => props.tenantId, refresh);

defineExpose({ refresh });

async function onChange(t: ModelType, value: string | undefined) {
  selected.value = { ...selected.value, [t]: value };
  if (!value) return;
  const [providerName, modelName, modelType] = value.split('::');
  if (!providerName || !modelName || !modelType) return;
  savingType.value = t;
  try {
    await setModelDefaultByName(
      providerName,
      modelName,
      modelType as ModelType,
      props.tenantId,
    );
    emit('change');
  } finally {
    savingType.value = null;
  }
}

/**
 * Flatten each type's grouped-by-provider view into the flat option list
 * {@link GroupedModelSelect} expects. We keep `providerLabel` verbose (name +
 * description) since it doubles as the group header for these dropdowns.
 */
function toOptions(
  providers: GroupedProviderView[],
): GroupedModelSelectOption[] {
  return providers.flatMap((p) =>
    p.modelList.map<GroupedModelSelectOption>((m) => ({
      id: m.id,
      label: m.modelName,
      providerName: p.provider,
      providerLabel:
        p.description && p.label
          ? `${p.label} — ${p.description}`
          : p.label || p.provider,
    })),
  );
}

const rows = computed(() =>
  props.types.map((r) => ({
    ...r,
    providers: providerModelMap.value[r.modelType] ?? [],
    options: toOptions(providerModelMap.value[r.modelType] ?? []),
    value: selected.value[r.modelType],
  })),
);
</script>

<template>
  <section class="dmp-root">
    <div class="dmp-title">
      <span>系统默认模型</span>
      <span class="dmp-title-hint">· 未指定模型时，智能体 / 知识库 / 工作流将使用</span>
    </div>

    <div class="dmp-grid">
      <div v-for="r in rows" :key="r.modelType" class="dmp-row">
        <div class="dmp-row-head">
          <span class="dmp-cap" :data-color="modelTypeColor(r.modelType)">
            {{ modelTypeLabel(r.modelType) }}
          </span>
          <span class="dmp-desc">{{ r.desc }}</span>
          <span v-if="savingType === r.modelType" class="dmp-saving">保存中…</span>
        </div>

        <GroupedModelSelect
          :model-value="r.value ?? null"
          :options="r.options"
          :disabled="savingType === r.modelType"
          :placeholder="r.providers.length === 0 ? '暂无可用模型' : '请选择模型'"
          :empty-text="`暂无可用的${modelTypeLabel(r.modelType)}模型`"
          empty-hint="请先在「模型供应商」里配置并启用"
          @update:model-value="(v) => onChange(r.modelType, v ?? undefined)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.dmp-root {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dmp-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: baseline;
  gap: 4px;
  /* On narrow viewports the hint drops to its own line instead of forcing
   * the title row to overflow. */
  flex-wrap: wrap;
}
:global(.dark) .dmp-title {
  color: #d1d5db;
}
.dmp-title-hint {
  font-weight: 400;
  color: #9ca3af;
  font-size: 12px;
}
.dmp-grid {
  display: grid;
  /* Evenly split the row across the configured types. minmax(180px, 1fr) keeps
   * all 5 cards on one row at ~960px+ viewports; below that they wrap gracefully
   * instead of stretching the last card. */
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.dmp-row {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
:global(.dark) .dmp-row {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
.dmp-row-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dmp-cap {
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  letter-spacing: 0.5px;
}
.dmp-cap[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.dmp-cap[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.dmp-cap[data-color='purple'] {
  background: #faf5ff;
  color: #7c3aed;
}
.dmp-desc {
  font-size: 12px;
  color: #6b7280;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:global(.dark) .dmp-desc {
  color: #9ca3af;
}
.dmp-saving {
  font-size: 11px;
  color: #6366f1;
}
</style>
