<script setup lang="ts">
import { Switch as ASwitch, Tooltip as ATooltip, Slider as ASlider, InputNumber as AInputNumber, Segmented as ASegmented, Textarea as ATextarea } from '../../ui';


import GroupedModelSelect from './GroupedModelSelect.vue';
import { Button } from '../../ui/shadcn/button';

/**
 * Model settings popover shared by application and workflow configuration.
 * Uses GroupedModelSelect (also used by tenant defaults) for the floating
 * searchable provider list, keeping the parameter form stationary.
 * Supports both SelectedModel and legacy workflow model shapes.
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Popover, Segmented } from '../../ui';
import {
  DownOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue';

import {
  mergeAgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import {
  setProviderHubApiBase,
  setProviderHubHeaders,
  useProviderHub,
} from '../composables/useProviderHub';
import type {
  GroupedModelView,
  GroupedProviderView,
  ModelEntity,
  ModelType,
  SelectedModel,
} from '../types';
import ProviderIcon from './ProviderIcon.vue';

interface Props {
  modelValue?: SelectedModel;
  /** 模型类型过滤，默认 LLM。传其它类型（TEXT_EMBEDDING / RERANK …）时组件通用。 */
  modelType?: ModelType;
  /** 直接注入分组数据；提供时组件不再自行请求。 */
  groups?: GroupedProviderView[];
  /** 后端多租户场景下的 tenantId，仅在自行加载数据时透传。 */
  tenantId?: string;
  /** 触发按钮的占位文案。 */
  placeholder?: string;
  /** 气泡卡片宽度。 */
  width?: number | string;
  /** 二级"模型列表"高度上限。 */
  bodyMaxHeight?: number | string;
  /** Popover placement passthrough. */
  placement?:
    | 'bottom'
    | 'bottomLeft'
    | 'bottomRight'
    | 'top'
    | 'topLeft'
    | 'topRight';
  /** Select-like layout: keep the popup below and exactly as wide as its trigger. */
  matchTriggerWidth?: boolean;
  /** 禁用触发。 */
  disabled?: boolean;
  /** 是否展示"参数"区域，默认展示。TEXT_EMBEDDING 等场景可传 false 隐藏。 */
  showParams?: boolean;
  /** 面板标题（顶部大字），默认「模型设置」。 */
  title?: string;
  /**
   * 未选中时是否自动加载并回填「系统默认模型」（按 `modelType` 匹配租户默认）。
   * 默认开启 —— 打开一个新建应用/新节点时，触发块直接展示默认 LLM，用户仍可再点开切换。
   */
  autoLoadDefault?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelType: 'LLM',
  placement: 'bottomLeft',
  width: 460,
  bodyMaxHeight: 340,
  placeholder: '点击选择模型',
  showParams: true,
  title: '模型设置',
  autoLoadDefault: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: SelectedModel): void;
  (
    e: 'change',
    v: SelectedModel,
    provider: GroupedProviderView,
    model: GroupedModelView,
  ): void;
  /** 底部「多个模型进行调试」的点击回调 —— 触发后调用方自行处理。 */
  (e: 'multiModelDebug'): void;
  /** 浮动列表底部「模型供应商设置」入口，方便直接跳到 provider hub 设置页。 */
  (e: 'openProviderSettings'): void;
}>();

// The picker is also rendered directly inside workflow node cards, without a
// surrounding ProviderApp. Initialise the shared provider client here so the
// first grouped-by-type/defaults request receives the host's Authorization
// header and API base instead of using the unauthenticated defaults.
const globalConfig = useAgentStartConfig();
setProviderHubApiBase(globalConfig.apiBase ?? '/api');
setProviderHubHeaders(() => mergeAgentStartHeaders(globalConfig.headers));

const { listDefaults, listModelsGroupedByType } = useProviderHub();

const open = ref(false);
const loading = ref(false);
const internalGroups = ref<GroupedProviderView[]>([]);

const groups = computed<GroupedProviderView[]>(
  () => props.groups ?? internalGroups.value,
);

/** 兼容读取：新 shape 用 providerName，workflow 老 shape 用 provider。 */
const current = computed<SelectedModel>(() => props.modelValue ?? {});
const currentProvider = computed(
  () => current.value.providerName ?? current.value.provider ?? '',
);
const currentModelName = computed(() => current.value.modelName ?? '');

/** 命中当前选中项所在的 provider + model，用于触发块 & 模型卡片展示。 */
const currentMeta = computed<null | {
  provider: GroupedProviderView;
  model: GroupedModelView;
}>(() => {
  const pn = currentProvider.value;
  const mn = currentModelName.value;
  if (!pn || !mn) return null;
  for (const p of groups.value) {
    const m = p.modelList.find(
      (x) => x.providerName === pn && x.modelName === mn,
    );
    if (m) return { provider: p, model: m };
  }
  return null;
});

const modelOptions = computed(() => groups.value.flatMap(provider => provider.modelList.map(model => ({
  id: JSON.stringify([model.providerName, model.modelName]),
  label: model.modelName,
  providerName: model.providerName,
  providerLabel: provider.label || provider.provider,
}))));
const selectedModelKey = computed(() => JSON.stringify([currentProvider.value, currentModelName.value]));
function pickModel(key: string | null) {
  for (const provider of groups.value) {
    const model = provider.modelList.find(item => JSON.stringify([item.providerName, item.modelName]) === key);
    if (model) { selectModel(provider, model); return; }
  }
}

/** ------- 参数 ------- */
type ParamType = 'boolean' | 'float' | 'int' | 'json' | 'text';

interface ParamCfg {
  key: string;
  label: string;
  hint: string;
  type: ParamType;
  min?: number;
  max?: number;
  step?: number;
  slider?: boolean;
  placeholder?: string;
  value: boolean | number | string;
  enable: boolean;
}

/**
 * 参数定义 —— 对齐 Dify「模型设置」参数集合。是否 slider 由 `slider` 决定；
 * 布尔用 True / False segmented；text / json 用 textarea。
 */
function makeParamConfig(): Record<string, ParamCfg> {
  return {
    temperature: {
      key: 'temperature',
      label: '温度',
      hint: '越高越发散，0-2',
      type: 'float',
      min: 0,
      max: 2,
      step: 0.1,
      slider: true,
      value: 0.3,
      enable: false,
    },
    max_tokens: {
      key: 'max_tokens',
      label: '最大标记',
      hint: '单次生成的 token 上限',
      type: 'int',
      min: 1,
      max: 32_000,
      step: 1,
      slider: true,
      value: 8192,
      enable: false,
    },
    top_p: {
      key: 'top_p',
      label: 'Top P',
      hint: '核采样阈值 0-1',
      type: 'float',
      min: 0,
      max: 1,
      step: 0.05,
      slider: true,
      value: 0.8,
      enable: false,
    },
    top_k: {
      key: 'top_k',
      label: '取样数量',
      hint: '每一步只在概率最高的前 K 个候选里取样',
      type: 'int',
      min: 0,
      max: 100,
      step: 1,
      slider: true,
      value: 0,
      enable: false,
    },
    seed: {
      key: 'seed',
      label: '随机种子',
      hint: '固定随机种子以复现结果',
      type: 'int',
      min: 0,
      max: 9_999_999_999,
      step: 1,
      slider: false,
      value: 1234,
      enable: false,
    },
    repetition_penalty: {
      key: 'repetition_penalty',
      label: '重复惩罚',
      hint: '>1 惩罚已出现的 token，降低重复',
      type: 'float',
      min: 0,
      max: 2,
      step: 0.05,
      slider: false,
      value: 1.1,
      enable: false,
    },
    enable_web_search: {
      key: 'enable_web_search',
      label: '联网搜索',
      hint: '允许模型在生成前发起联网检索',
      type: 'boolean',
      value: false,
      enable: false,
    },
    enable_thinking: {
      key: 'enable_thinking',
      label: '思考模式',
      hint: '开启后模型会先输出思考过程',
      type: 'boolean',
      value: false,
      enable: false,
    },
    thinking_budget: {
      key: 'thinking_budget',
      label: '思考长度限制',
      hint: '思考过程最多消耗的 token',
      type: 'int',
      min: 0,
      max: 32_000,
      step: 1,
      slider: false,
      value: 2048,
      enable: false,
    },
    response_format: {
      key: 'response_format',
      label: '回复格式',
      hint: '例如 text / json_object / json_schema，或直接写 schema 描述',
      type: 'text',
      placeholder: '例如 text 或 json_object',
      value: '',
      enable: false,
    },
    extra_headers: {
      key: 'extra_headers',
      label: '额外请求头',
      hint: 'JSON 字符串，会追加到底层 HTTP 请求头',
      type: 'json',
      placeholder: '{"X-Trace-Id": "abc"}',
      value: '',
      enable: false,
    },
  };
}

const paramConfig = reactive<Record<string, ParamCfg>>(makeParamConfig());

function syncParamsFromValue() {
  const cp = current.value.completionParams || {};
  for (const key of Object.keys(paramConfig)) {
    const cfg = paramConfig[key];
    if (!cfg) continue;
    if (Object.prototype.hasOwnProperty.call(cp, key)) {
      cfg.enable = true;
      const raw = cp[key];
      if (raw !== undefined) cfg.value = raw as ParamCfg['value'];
    } else {
      cfg.enable = false;
    }
  }
}

function collectParams(): Record<string, boolean | number | string> {
  const out: Record<string, boolean | number | string> = {};
  for (const cfg of Object.values(paramConfig)) {
    if (cfg.enable) out[cfg.key] = cfg.value;
  }
  return out;
}

/**
 * 组装对外抛出的 v-model 载荷 —— 保留原来 value 上所有键，覆盖新的
 * providerName / modelName / modelType / modelProvider 以及 completionParams。
 * 同时同步老 shape 的 provider / mode 让 workflow 侧序列化仍旧能吃到熟悉的
 * 键；不再写入历史的 modelId 合成字段 —— 应用与工作流节点保存的都是
 * (modelProvider, modelName) 两列。
 */
function buildPayload(
  next: {
    modelName: string;
    modelType: ModelType;
    mode?: string;
    providerName: string;
  } | null,
): SelectedModel {
  const params = collectParams();
  const base: SelectedModel = { ...(props.modelValue ?? {}) };
  // Legacy composite id like "provider::model::type" is meaningless now — drop
  // it so re-saving a node/app cleans up historical values.
  if (typeof base.modelId === 'string' && base.modelId.includes('::')) {
    delete base.modelId;
  }
  if (next) {
    base.providerName = next.providerName;
    base.modelProvider = next.providerName;
    base.modelName = next.modelName;
    base.modelType = next.modelType;
    // 老字段同步，保证 formState.model 序列化后仍是 workflow 熟悉的形状
    base.provider = next.providerName;
    if (next.mode !== undefined) base.mode = next.mode;
  }
  if (Object.keys(params).length > 0) base.completionParams = params;
  else delete base.completionParams;
  return base;
}

function selectModel(p: GroupedProviderView, m: GroupedModelView) {
  const payload = buildPayload({
    providerName: m.providerName,
    modelName: m.modelName,
    modelType: m.modelType,
    mode: (m as any).mode,
  });
  emit('update:modelValue', payload);
  emit('change', payload, p, m);
}

function onParamsChange() {
  emit('update:modelValue', buildPayload(null));
}

async function loadGroups() {
  if (props.groups) return;
  loading.value = true;
  try {
    const all = await listModelsGroupedByType(props.tenantId);
    internalGroups.value = all[props.modelType] ?? [];
  } catch {
    internalGroups.value = [];
  } finally {
    loading.value = false;
  }
}

/** 当前 v-model 是否已经指向一个具体模型。 */
function hasSelection(sel: SelectedModel | undefined): boolean {
  if (!sel) return false;
  const pn = sel.providerName ?? sel.provider;
  return !!(pn && sel.modelName);
}

/**
 * 没选中模型时，读取租户「系统默认模型」并回填当前 modelType 对应的那一条。
 * 命中即向上抛 update:modelValue —— 调用方（AppDesignDrawer / 各类节点）
 * 会把它写进 form 里，页面再次打开时就不会又出现"请选择模型"占位。
 */
async function loadDefaultIfEmpty() {
  if (!props.autoLoadDefault) return;
  if (hasSelection(props.modelValue)) return;
  try {
    const defaults = await listDefaults(props.tenantId);
    // 用户可能在等待期间已经手动选过 —— 再判一次避免覆盖。
    if (hasSelection(props.modelValue)) return;
    const def = defaults[props.modelType] as ModelEntity | undefined;
    if (!def?.providerName || !def.modelName) return;
    emit(
      'update:modelValue',
      buildPayload({
        providerName: def.providerName,
        modelName: def.modelName,
        modelType: def.modelType ?? props.modelType,
      }),
    );
  } catch {
    // 默认模型接口失败不影响手动选择，静默即可。
  }
}

onMounted(() => {
  syncParamsFromValue();
  if (!props.groups) loadGroups();
  loadDefaultIfEmpty();
});

watch(
  () => [props.tenantId, props.modelType],
  () => {
    if (!props.groups) loadGroups();
    loadDefaultIfEmpty();
  },
);

watch(
  () => props.modelValue,
  () => syncParamsFromValue(),
  { deep: true },
);

watch(open, (value) => {
  if (value && !props.groups) loadGroups();
});

function toggleOpen() {
  if (props.disabled) return;
  open.value = !open.value;
}

function closePanel() {
  open.value = false;
}

/**
 * antd Popover mount point. Kept as a script function (not an inline template
 * lambda) so the bare `document` global isn't looked up on the component
 * instance under strict template type-checking.
 */
function popupContainer(trigger: HTMLElement): HTMLElement {
  return trigger.parentElement ?? document.body;
}

defineExpose({
  open: () => {
    open.value = true;
  },
  close: closePanel,
  refresh: loadGroups,
});
</script>

<template>
  <Popover
    v-model:open="open"
    class="ph-mp-popover"
    :placement="placement"
    :match-trigger-width="matchTriggerWidth"
    :flip-on-overflow="!matchTriggerWidth"
    trigger="click"
    overlay-class-name="ph-model-picker-popover hud-panel hud-popover border-0 rounded-none shadow-none"
    :get-popup-container="popupContainer"
  >
    <template #content>
      <div
        class="ph-mp"
        :style="{
          width: matchTriggerWidth
            ? '100%'
            : typeof width === 'number'
              ? `${width}px`
              : width,
        }"
      >
        <!-- 顶部标题栏 -->
        <div class="ph-mp-header">
          <span class="ph-mp-title">{{ title }}</span>
        </div>

        <div class="ph-mp-scroll">
          <GroupedModelSelect
            class="ph-mp-selector"
            :model-value="selectedModelKey"
            :options="modelOptions"
            :loading="loading"
            :body-max-height="bodyMaxHeight"
            placeholder="请选择模型"
            @change="pickModel"
          >
            <template #footer>
              <Button variant="ghost" size="sm" class="w-full justify-start" @click="emit('openProviderSettings')">
                <SettingOutlined />模型供应商设置
              </Button>
            </template>
          </GroupedModelSelect>

          <!-- 参数区 -->
          <div v-if="showParams" class="ph-mp-params-block">
            <div class="ph-mp-params-title">参数</div>
            <div class="ph-mp-params">
              <div
                v-for="cfg in paramConfig"
                :key="cfg.key"
                class="ph-mp-param"
                :class="{ 'is-block': cfg.type === 'text' || cfg.type === 'json' }"
              >
                <div class="ph-mp-param-row">
                  <a-switch
                    v-model:checked="cfg.enable"
                    size="small"
                    class="ph-mp-param-switch"
                    @change="onParamsChange"
                  />
                  <span class="ph-mp-param-name">{{ cfg.label }}</span>
                  <a-tooltip :title="cfg.hint" placement="top">
                    <QuestionCircleOutlined class="ph-mp-param-hint" />
                  </a-tooltip>

                  <template v-if="cfg.type === 'float' || cfg.type === 'int'">
                    <a-slider
                      v-if="cfg.slider"
                      v-model:value="cfg.value as number"
                      :min="cfg.min"
                      :max="cfg.max"
                      :step="cfg.step"
                      :disabled="!cfg.enable"
                      class="ph-mp-param-slider"
                      @afterChange="onParamsChange"
                    />
                    <a-input-number
                      v-model:value="cfg.value as number"
                      :min="cfg.min"
                      :max="cfg.max"
                      :step="cfg.step"
                      :disabled="!cfg.enable"
                      size="small"
                      class="ph-mp-param-num"
                      @change="onParamsChange"
                    />
                  </template>

                  <a-segmented
                    v-else-if="cfg.type === 'boolean'"
                    v-model:value="cfg.value as boolean"
                    :options="[
                      { label: 'True', value: true },
                      { label: 'False', value: false },
                    ]"
                    :disabled="!cfg.enable"
                    class="ph-mp-param-seg"
                    @change="onParamsChange"
                  />
                </div>

                <a-textarea
                  v-if="cfg.type === 'text' || cfg.type === 'json'"
                  v-model:value="cfg.value as string"
                  :disabled="!cfg.enable"
                  :placeholder="cfg.placeholder"
                  :auto-size="{ minRows: 2 }"
                  class="ph-mp-param-textarea"
                  @change="onParamsChange"
                />
              </div>
            </div>
          </div>
        </div>

      </div>
    </template>

    <slot
      name="trigger"
      :open="open"
      :toggle="toggleOpen"
      :current="current"
      :meta="currentMeta"
      :disabled="disabled"
    >
      <div
        class="ph-mp-trigger"
        :class="{
          'is-active': open,
          'has-value': !!currentModelName,
          'is-disabled': disabled,
        }"
      >
        <div class="ph-mp-trigger-icon">
          <ProviderIcon
            v-if="currentProvider"
            :name="currentProvider"
            :size="22"
          />
          <RobotOutlined v-else />
        </div>
        <span
          v-if="currentModelName"
          class="ph-mp-trigger-name"
          :title="currentModelName"
        >
          {{ currentModelName }}
        </span>
        <span
          v-if="currentModelName"
          class="ph-mp-trigger-sub"
          :title="currentMeta?.provider.label || currentProvider"
        >
          {{ currentMeta?.provider.label || currentProvider }}
        </span>
        <span v-if="!currentModelName" class="ph-mp-trigger-placeholder">
          {{ placeholder }}
        </span>
        <DownOutlined class="ph-mp-trigger-arrow" />
      </div>
    </slot>
  </Popover>
</template>

<style>
/* Popover owns the only surface and outer spacing. */
.ph-model-picker-popover.as-popover-popup {
  z-index: 10010 !important;
  width: max-content;
  max-width: calc(100vw - 20px);
  padding: 12px;
}

.ph-mp {
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  max-width: calc(100vw - 46px);
  max-height: 84vh;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  overflow: hidden;
}

/* 顶部标题栏 */
.ph-mp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
}
.ph-mp-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--foreground);
}
/* 模型卡片（点击展开浮动列表） */
.ph-mp-scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: none;
}
.ph-mp-scroll::-webkit-scrollbar {
  display: none;
}
.ph-mp-selector { margin-bottom: 8px; }

/* 参数区 */
.ph-mp-params-block {
  padding-top: 2px;
}
.ph-mp-params-title {
  padding: 2px 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--foreground);
}
.ph-mp-params {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ph-mp-param {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.ph-mp-param-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 22px;
}
.ph-mp-param-switch {
  flex: none;
}
.ph-mp-param-name {
  flex: none;
  width: 76px;
  font-size: 12px;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-param-hint {
  color: var(--muted-foreground);
  font-size: 11px;
  cursor: help;
}
.ph-mp-param-slider {
  flex: 1;
  min-width: 0;
  margin: 0 !important;
}
.ph-mp-param-num {
  flex: none;
  width: 72px;
  height: 26px;
  padding: 2px 6px;
  font-size: 11px;
  margin-left: auto;
}
.ph-mp-param-seg {
  margin-left: auto;
}
.ph-mp-param-seg [data-slot="toggle-group-item"] {
  height: 26px;
  min-width: 30px;
  padding: 0 6px;
  font-size: 11px;
}
.ph-mp-param.is-block .ph-mp-param-row {
  min-height: unset;
}
.ph-mp-param-textarea {
  box-sizing: border-box;
  width: calc(100% - 32px);
  min-width: 0;
  max-width: calc(100% - 32px);
  margin-left: 32px;
  overflow: hidden;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  resize: none;
  min-height: 44px;
  padding: 4px 6px;
  line-height: 1.4;
  font-size: 11px !important;
}

</style>

<style scoped>
.ph-mp-popover {
  width: 100%;
}

.ph-mp-trigger {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  min-height: 36px;
  background: var(--background);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.ph-mp-trigger:hover {
  border-color: var(--ring);
  background: var(--background);
}
.ph-mp-trigger.is-active {
  border-color: var(--ring);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}
.ph-mp-trigger.has-value {
  background: var(--background);
}
.ph-mp-trigger.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.ph-mp-trigger-icon {
  flex: none;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
}
.ph-mp-trigger-name {
  flex: none;
  max-width: 55%;
  font-size: 13px;
  font-weight: 600;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-trigger-sub {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-trigger-placeholder {
  flex: 1;
  font-size: 13px;
  color: var(--muted-foreground);
}
.ph-mp-trigger-arrow {
  flex: none;
  font-size: 10px;
  color: var(--muted-foreground);
  transition: transform 0.15s;
}
.ph-mp-trigger.is-active .ph-mp-trigger-arrow {
  transform: rotate(180deg);
  color: var(--primary);
}
</style>
