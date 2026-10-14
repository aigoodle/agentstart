<script setup lang="ts">
/**
 * ModelParameterDrawer — Dify-parity per-model parameter panel.
 *
 * When a user clicks the ⚙ on a pill in ProviderHubShell we open this drawer,
 * which:
 *   1. GETs /models/{id}/parameters → server returns the {rules, values} pair.
 *   2. Renders one control per rule (slider for FLOAT, number for INT, switch
 *      for BOOLEAN, input for STRING). Falls back to sensible defaults.
 *   3. On "保存" PATCHes only the entries the user actually touched, so a saved
 *      partial change never wipes out unrelated params.
 *   4. Bottom actions cover the three lifecycle moves that used to be buried in
 *      the advanced panel: 设为默认 · 测试连接 · 删除模型.
 *
 * The drawer is intentionally standalone: it does NOT know about the shell's
 * catalog list state. Parent handles refresh via the emitted `saved`/`deleted`.
 */
import { computed, reactive, ref, watch } from 'vue';

import { Button } from '../../ui';
import { useProviderHub } from '../composables/useProviderHub';
import type {
  ModelEntity,
  ModelParameterRule,
  ModelTestResult,
  ProviderView,
} from '../types';

interface Props {
  open: boolean;
  model: ModelEntity | null;
  provider: ProviderView | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
  (e: 'deleted'): void;
  (e: 'defaulted'): void;
}>();

const {
  getModelParameters,
  saveModelParameters,
  setDefault,
  deleteModel,
  testModel,
} = useProviderHub();

const loading = ref(false);
const saving = ref(false);
const testing = ref(false);
const testResult = ref<ModelTestResult | null>(null);
const rules = ref<ModelParameterRule[]>([]);
const values = reactive<Record<string, boolean | null | number | string>>({});
// Track which fields the user has actually touched so we PATCH only those.
const touched = ref<Set<string>>(new Set());
const error = ref<string | null>(null);

// Where to inherit the rule set from when the drawer opens. The server already
// does this reconciliation but we mirror it locally so the drawer can be used
// standalone without a round-trip when the model has no saved parameters yet.
const inheritedRules = computed<ModelParameterRule[]>(() => {
  if (!props.model || !props.provider) return [];
  const preset = props.provider.predefinedModels.find(
    (p) =>
      p.model.toLowerCase() === props.model!.modelName.toLowerCase() &&
      p.modelType === props.model!.modelType,
  );
  return (
    preset?.parameterRules ??
    props.provider.defaultParameterRules?.[props.model.modelType] ??
    []
  );
});

async function load() {
  if (!props.model) return;
  loading.value = true;
  error.value = null;
  testResult.value = null;
  touched.value = new Set();
  // Clear stale values before merging fresh
  for (const key of Object.keys(values)) delete values[key];
  try {
    const data = await getModelParameters(props.model.id);
    rules.value = data.rules?.length ? data.rules : inheritedRules.value;
    // Seed: server's saved value → rule default → empty.
    for (const r of rules.value) {
      const stored = data.parameters?.[r.name];
      values[r.name] =
        stored !== undefined && stored !== null
          ? (stored as boolean | number | string)
          : ((r.defaultValue as boolean | number | string) ?? '');
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e);
    rules.value = inheritedRules.value;
    for (const r of rules.value) {
      values[r.name] = (r.defaultValue as boolean | number | string) ?? '';
    }
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.open, props.model?.id],
  ([o]) => {
    if (o) load();
  },
  { immediate: true },
);

function markTouched(name: string) {
  touched.value = new Set([...touched.value, name]);
}

function onNumberInput(rule: ModelParameterRule, raw: string) {
  markTouched(rule.name);
  if (raw === '' || raw === null) {
    values[rule.name] = '';
    return;
  }
  const n = Number(raw);
  if (!Number.isFinite(n)) return;
  values[rule.name] = n;
}

function onSliderInput(rule: ModelParameterRule, e: Event) {
  const v = Number((e.target as HTMLInputElement).value);
  markTouched(rule.name);
  values[rule.name] = v;
}

function onTextInput(rule: ModelParameterRule, e: Event) {
  markTouched(rule.name);
  values[rule.name] = (e.target as HTMLInputElement).value;
}

function onBoolChange(rule: ModelParameterRule, e: Event) {
  markTouched(rule.name);
  values[rule.name] = (e.target as HTMLInputElement).checked;
}

function reset(rule: ModelParameterRule) {
  markTouched(rule.name);
  // Setting to null explicitly on the server means "remove the override" — but
  // for a nicer UI we set it back to the rule default here and the save path
  // sends null only if the user hits "重置为默认" AND the default is undefined.
  values[rule.name] = (rule.defaultValue as boolean | number | string) ?? '';
}

async function save() {
  if (!props.model) return;
  const patch: Record<string, boolean | null | number | string> = {};
  for (const name of touched.value) {
    const v = values[name];
    // Empty string on a numeric rule → treat as "clear".
    const rule = rules.value.find((r) => r.name === name);
    if (rule && (rule.type === 'FLOAT' || rule.type === 'INT') && v === '') {
      patch[name] = null;
    } else {
      patch[name] = v;
    }
  }
  if (Object.keys(patch).length === 0) {
    emit('update:open', false);
    return;
  }
  saving.value = true;
  error.value = null;
  try {
    await saveModelParameters(props.model.id, patch);
    emit('saved');
    emit('update:open', false);
  } catch (e: any) {
    error.value = e?.message ?? String(e);
  } finally {
    saving.value = false;
  }
}

async function onSetDefault() {
  if (!props.model) return;
  await setDefault(props.model.id);
  emit('defaulted');
  emit('saved');
}

async function onDelete() {
  if (!props.model) return;
  if (
    !confirm(
      `确认删除「${props.model.providerName} / ${props.model.modelName}」？依赖它的知识库/智能体运行时会失败。`,
    )
  )
    return;
  await deleteModel(props.model.id);
  emit('deleted');
  emit('update:open', false);
}

async function onTest() {
  if (!props.model) return;
  testing.value = true;
  testResult.value = null;
  try {
    testResult.value = await testModel(props.model.id);
  } catch (e: any) {
    testResult.value = {
      ok: false,
      latencyMs: 0,
      error: e?.message ?? String(e),
    };
  } finally {
    testing.value = false;
  }
}

function stepFor(r: ModelParameterRule): number {
  if (r.step) return r.step;
  return r.type === 'INT' ? 1 : 0.1;
}
</script>

<template>
  <transition name="mpd-fade">
    <div v-if="open" class="mpd-mask" @click.self="emit('update:open', false)" />
  </transition>
  <transition name="mpd-slide">
    <aside v-if="open" class="mpd-drawer" role="dialog" aria-label="模型参数">
      <header class="mpd-head">
        <div class="mpd-title-block">
          <div class="mpd-title">模型参数</div>
          <div v-if="model" class="mpd-subtitle" :title="model.modelName">
            <span class="mpd-mono">{{ model.modelName }}</span>
            <span class="mpd-dot">·</span>
            <span>{{ model.modelType }}</span>
            <span
              v-if="model.isDefault"
              class="mpd-badge mpd-badge--default"
            >
              默认
            </span>
          </div>
        </div>
        <button class="mpd-close" @click="emit('update:open', false)">✕</button>
      </header>

      <div class="mpd-body">
        <div v-if="loading" class="mpd-loading">加载中…</div>
        <template v-else>
          <div v-if="rules.length === 0" class="mpd-empty">
            该模型没有可配置的参数。
          </div>
          <div v-for="r in rules" :key="r.name" class="mpd-field">
            <div class="mpd-label-row">
              <label class="mpd-label">
                <span>{{ r.label }}</span>
                <span class="mpd-mono mpd-key">{{ r.name }}</span>
                <span v-if="r.required" class="mpd-req">*</span>
              </label>
              <button
                v-if="touched.has(r.name)"
                class="mpd-reset"
                title="恢复默认"
                @click="reset(r)"
              >
                ↺
              </button>
            </div>

            <!-- FLOAT: slider + input number -->
            <template v-if="r.type === 'FLOAT'">
              <div class="mpd-slider-row">
                <input
                  type="range"
                  class="mpd-slider"
                  :min="r.min ?? 0"
                  :max="r.max ?? 1"
                  :step="stepFor(r)"
                  :value="Number(values[r.name] ?? r.defaultValue ?? 0)"
                  @input="(e) => onSliderInput(r, e)"
                />
                <input
                  type="number"
                  class="mpd-num"
                  :min="r.min"
                  :max="r.max"
                  :step="stepFor(r)"
                  :value="values[r.name]"
                  @input="(e) => onNumberInput(r, (e.target as HTMLInputElement).value)"
                />
              </div>
            </template>

            <!-- INT: number input only -->
            <template v-else-if="r.type === 'INT'">
              <input
                type="number"
                class="mpd-num mpd-num--wide"
                :min="r.min"
                :max="r.max"
                :step="stepFor(r)"
                :placeholder="r.placeholder"
                :value="values[r.name]"
                @input="(e) => onNumberInput(r, (e.target as HTMLInputElement).value)"
              />
            </template>

            <!-- BOOLEAN: switch -->
            <template v-else-if="r.type === 'BOOLEAN'">
              <label class="mpd-switch">
                <input
                  type="checkbox"
                  :checked="!!values[r.name]"
                  @change="(e) => onBoolChange(r, e)"
                />
                <span class="mpd-switch-slider" />
              </label>
            </template>

            <!-- STRING: text -->
            <template v-else>
              <input
                type="text"
                class="mpd-text"
                :placeholder="r.placeholder"
                :value="values[r.name]"
                @input="(e) => onTextInput(r, e)"
              />
            </template>

            <div v-if="r.help" class="mpd-help">{{ r.help }}</div>
          </div>

          <div v-if="error" class="mpd-error">{{ error }}</div>
          <div
            v-if="testResult"
            class="mpd-test-result"
            :class="{ 'mpd-test-result--fail': !testResult.ok }"
          >
            <template v-if="testResult.ok">
              ✓ 连接成功 · {{ testResult.latencyMs }}ms
              <span v-if="testResult.snippet">· “{{ testResult.snippet }}”</span>
              <span v-if="testResult.dimensions">
                · dim {{ testResult.dimensions }}
              </span>
            </template>
            <template v-else>
              ✗ 连接失败: {{ testResult.error ?? testResult.message }}
            </template>
          </div>
        </template>
      </div>

      <footer class="mpd-foot">
        <div class="mpd-foot-left">
          <Button
            type="text"
            :loading="testing"
            :disabled="!model"
            @click="onTest"
          >
            🔍 测试连接
          </Button>
          <Button
            v-if="model && !model.isDefault"
            type="text"
            @click="onSetDefault"
          >
            设为默认
          </Button>
          <Button
            type="text"
            danger
            :disabled="!model"
            @click="onDelete"
          >
            删除模型
          </Button>
        </div>
        <div class="mpd-foot-right">
          <Button @click="emit('update:open', false)">
            取消
          </Button>
          <Button
            type="primary"
            :loading="saving"
            :disabled="touched.size === 0"
            @click="save"
          >
            {{ touched.size === 0 ? '未修改' : `保存 ${touched.size} 项` }}
          </Button>
        </div>
      </footer>
    </aside>
  </transition>
</template>

<style scoped>
.mpd-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 950;
}
.mpd-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 460px;
  max-width: 92vw;
  background: #fff;
  box-shadow: -8px 0 24px rgba(15, 23, 42, 0.15);
  z-index: 951;
  display: flex;
  flex-direction: column;
}
:global(.dark) .mpd-drawer {
  background: #1a1a1a;
  color: #f3f4f6;
}

.mpd-head {
  padding: 14px 18px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
:global(.dark) .mpd-head {
  border-bottom-color: #2d2d2d;
}
.mpd-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
:global(.dark) .mpd-title {
  color: #f3f4f6;
}
.mpd-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.mpd-dot {
  color: #9ca3af;
}
.mpd-mono {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mpd-badge {
  padding: 0 6px;
  font-size: 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
}
.mpd-badge--default {
  background: #dbeafe;
  color: #1e40af;
}
.mpd-close {
  background: transparent;
  border: none;
  font-size: 16px;
  color: #6b7280;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  flex-shrink: 0;
}
.mpd-close:hover {
  background: #f3f4f6;
}

.mpd-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.mpd-loading,
.mpd-empty {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.mpd-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mpd-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mpd-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  min-width: 0;
}
:global(.dark) .mpd-label {
  color: #d1d5db;
}
.mpd-key {
  font-size: 10.5px;
  color: #9ca3af;
  font-weight: 400;
}
.mpd-req {
  color: #dc2626;
}
.mpd-reset {
  background: transparent;
  border: none;
  cursor: pointer;
  color: #6366f1;
  font-size: 13px;
  padding: 0 4px;
  border-radius: 4px;
}
.mpd-reset:hover {
  background: #eef2ff;
}
.mpd-help {
  font-size: 11.5px;
  color: #9ca3af;
  line-height: 1.5;
}

.mpd-slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mpd-slider {
  flex: 1;
  height: 4px;
  cursor: pointer;
}
.mpd-num {
  width: 70px;
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  text-align: right;
  background: #fff;
  color: #111827;
}
.mpd-num--wide {
  width: 100%;
  max-width: 220px;
  text-align: left;
}
:global(.dark) .mpd-num {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.mpd-num:focus {
  outline: none;
  border-color: #6366f1;
}

.mpd-text {
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
}
:global(.dark) .mpd-text {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.mpd-text:focus {
  outline: none;
  border-color: #6366f1;
}

.mpd-switch {
  position: relative;
  display: inline-block;
  width: 34px;
  height: 18px;
}
.mpd-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.mpd-switch-slider {
  position: absolute;
  inset: 0;
  background: #cbd5e1;
  border-radius: 999px;
  cursor: pointer;
  transition: 0.15s;
}
.mpd-switch-slider:before {
  position: absolute;
  content: '';
  height: 14px;
  width: 14px;
  left: 2px;
  bottom: 2px;
  background: #fff;
  border-radius: 50%;
  transition: 0.15s;
}
.mpd-switch input:checked + .mpd-switch-slider {
  background: #6366f1;
}
.mpd-switch input:checked + .mpd-switch-slider:before {
  transform: translateX(16px);
}

.mpd-error {
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  font-size: 12px;
}
.mpd-test-result {
  padding: 8px 12px;
  border-radius: 6px;
  background: #ecfdf5;
  color: #065f46;
  font-size: 12px;
  border: 1px solid #a7f3d0;
}
.mpd-test-result--fail {
  background: #fef2f2;
  color: #b91c1c;
  border-color: #fecaca;
}

.mpd-foot {
  padding: 12px 18px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
:global(.dark) .mpd-foot {
  border-top-color: #2d2d2d;
}
.mpd-foot-left,
.mpd-foot-right {
  display: flex;
  gap: 6px;
}
.mpd-btn {
  padding: 6px 12px;
  font-size: 12.5px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  cursor: pointer;
  white-space: nowrap;
}
.mpd-btn:hover:not(:disabled) {
  background: #f9fafb;
}
.mpd-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.mpd-btn--ghost {
  background: transparent;
}
.mpd-btn--primary {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}
.mpd-btn--primary:hover:not(:disabled) {
  background: #4f46e5;
}
.mpd-btn--danger {
  color: #dc2626;
}
.mpd-btn--danger:hover:not(:disabled) {
  background: #fef2f2;
}

.mpd-fade-enter-active,
.mpd-fade-leave-active {
  transition: opacity 0.18s ease;
}
.mpd-fade-enter-from,
.mpd-fade-leave-to {
  opacity: 0;
}
.mpd-slide-enter-active,
.mpd-slide-leave-active {
  transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.mpd-slide-enter-from,
.mpd-slide-leave-to {
  transform: translateX(100%);
}
</style>
