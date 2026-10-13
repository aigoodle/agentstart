<script setup lang="ts">
/**
 * ModelPickerPopover — 通用「模型设置」面板。
 *
 * 一个组件同时服务三处：
 *   1. 应用设计右上角的模型选择（agent-studio）
 *   2. 工作流 LLM / Agent / QuestionClassifier / ParameterExtractor 节点的
 *      模型选择（agent-flow）
 *   3. 任何需要「模型 + 参数微调」组合的地方
 *
 * 交互（对齐 Dify 「模型设置」样式）：
 *
 *   触发块 —— 一个"普通的块"（非下拉框样式），单行显示 provider icon +
 *   模型名 + provider 标签；点击弹出 popover。调用方可通过 #trigger 插槽
 *   完全自定义外观。
 *
 *   Popover 内容（外层"模型设置"面板）：
 *     ┌─────────────────────────────────────────┐
 *     │ 模型设置                             × │
 *     │ ┌ 🔷 qwen3.7-plus [CHAT] ⚙︎        ▼┐ │  ← 模型卡片，点击展开二级下拉
 *     │ 参数                                    │
 *     │ ○ 温度       ⓘ  ━━━●━━━ [0.3 ]        │
 *     │ ○ 最大标记   ⓘ  ━━━━━━● [8192]        │
 *     │ ... (更多参数)                          │
 *     └─────────────────────────────────────────┘
 *
 *   二级下拉（点击模型卡片时展开）—— 搜索框 + 分组模型列表；选中一条模型后
 *   只关闭二级下拉，"模型设置"外层面板保持打开，方便继续调参。
 *
 * 数据源默认走 useProviderHub().listModelsGroupedByType()，即 provider-hub
 * 已经暴露的 /models/grouped-by-type — 和"系统默认模型"面板同一份数据，保证
 * 全站分组、启用状态一致。宿主项目也可以通过 `groups` prop 直接注入。
 *
 * v-model 载荷至少 { providerName, modelName, modelType, modelProvider }；同
 * 时兼容 workflow 历史 { provider, modelName, mode, completionParams } 形状：
 * 组件读取时两套字段都识别，回填时保留所有原有键 —— 调用方无需做任何形状转
 * 换。历史上把 provider::model::type 合成到 `modelId` 的做法已废弃 —— 组件不
 * 再写入该字段，遇到旧的合成值会顺手清掉，避免节点/应用配置里再出现无意义
 * 的合成 id。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Popover, Segmented, Spin } from '../../ui';
import {
  CheckOutlined,
  CloseOutlined,
  DownOutlined,
  QuestionCircleOutlined,
  RightOutlined,
  RobotOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue';

import {
  mergeAgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import {
  modelTypeColor,
  modelTypeLabel,
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
  /** 二级"模型下拉"高度上限。 */
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
  /** 二级下拉底部「模型供应商设置」入口，方便直接跳到 provider hub 设置页。 */
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
const modelDropdownOpen = ref(false);
const loading = ref(false);
const searchQuery = ref('');
const collapsed = ref<Set<string>>(new Set());
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

/** 搜索命中模型 / provider / description。空关键字直接返回全部。 */
const filteredGroups = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return groups.value;
  return groups.value
    .map((p) => {
      const hits = p.modelList.filter((m) => {
        return (
          m.modelName.toLowerCase().includes(q) ||
          (p.label ?? p.provider).toLowerCase().includes(q) ||
          (p.description ?? '').toLowerCase().includes(q)
        );
      });
      return { ...p, modelList: hits };
    })
    .filter((p) => p.modelList.length > 0);
});

const hasResult = computed(() =>
  filteredGroups.value.some((g) => g.modelList.length > 0),
);

function isCollapsed(p: GroupedProviderView): boolean {
  return collapsed.value.has(p.provider);
}

function toggleCollapse(p: GroupedProviderView) {
  const next = new Set(collapsed.value);
  if (next.has(p.provider)) next.delete(p.provider);
  else next.add(p.provider);
  collapsed.value = next;
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
  // 只关掉二级下拉，外层"模型设置"面板保持打开
  modelDropdownOpen.value = false;
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

watch(open, (v) => {
  if (!v) {
    // 关闭外层时同时把二级下拉收起来，避免下次打开还残留
    modelDropdownOpen.value = false;
    return;
  }
  if (!props.groups) loadGroups();
});

watch(modelDropdownOpen, (v) => {
  if (!v) return;
  if (!props.groups) loadGroups();
  // 打开二级下拉时展开当前选中的分组
  if (currentProvider.value) {
    const next = new Set(collapsed.value);
    next.delete(currentProvider.value);
    collapsed.value = next;
  }
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
    overlay-class-name="ph-model-picker-popover"
    :get-popup-container="popupContainer"
  >
    <template #content>
      <div
        class="ph-mp hud-panel"
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
          <button
            type="button"
            class="ph-mp-close"
            aria-label="关闭"
            @click="closePanel"
          >
            <CloseOutlined />
          </button>
        </div>

        <div class="ph-mp-scroll">
          <!-- 模型卡片 —— 点击展开二级下拉 -->
          <Popover
            v-model:open="modelDropdownOpen"
            class="ph-mp-popover"
            trigger="click"
            placement="bottomLeft"
            match-trigger-width
            :flip-on-overflow="false"
            overlay-class-name="ph-model-picker-popover"
            :get-popup-container="popupContainer"
          >
            <template #content>
              <div
                class="ph-mp-dropdown hud-panel"
                :style="{
                  width: '100%',
                }"
              >
                <div class="ph-mp-search">
                  <SearchOutlined class="ph-mp-search-icon" />
                  <input
                    v-model="searchQuery"
                    class="ph-mp-search-input"
                    placeholder="搜索模型"
                    @keydown.stop
                  />
                </div>

                <div
                  class="ph-mp-body"
                  :style="{
                    maxHeight:
                      typeof bodyMaxHeight === 'number'
                        ? `${bodyMaxHeight}px`
                        : bodyMaxHeight,
                  }"
                >
                  <Spin :spinning="loading">
                    <div
                      v-if="!loading && groups.length === 0"
                      class="ph-mp-empty"
                    >
                      <RobotOutlined class="ph-mp-empty-icon" />
                      <div>暂无可用模型</div>
                      <div class="ph-mp-empty-hint">
                        请先在"模型供应商"里配置并启用
                      </div>
                    </div>

                    <div v-else-if="!hasResult" class="ph-mp-empty">
                      <SearchOutlined class="ph-mp-empty-icon" />
                      <div>没有匹配的模型</div>
                    </div>

                    <div v-else class="ph-mp-groups">
                      <div
                        v-for="p in filteredGroups"
                        :key="p.id"
                        class="ph-mp-group"
                      >
                        <div
                          class="ph-mp-group-header"
                          @click="toggleCollapse(p)"
                        >
                          <RightOutlined
                            v-if="isCollapsed(p)"
                            class="ph-mp-fold"
                          />
                          <DownOutlined v-else class="ph-mp-fold" />
                          <ProviderIcon :name="p.provider" :size="18" />
                          <span class="ph-mp-group-name">
                            {{ p.label || p.provider }}
                          </span>
                          <span
                            class="ph-mp-item-tag"
                            :data-color="modelTypeColor(modelType)"
                          >
                            {{ modelTypeLabel(modelType) }}
                          </span>
                        </div>

                        <div v-if="!isCollapsed(p)" class="ph-mp-group-body">
                          <div
                            v-for="m in p.modelList"
                            :key="m.id"
                            class="ph-mp-item"
                            :class="{
                              'is-active':
                                currentProvider === m.providerName &&
                                currentModelName === m.modelName,
                            }"
                            @click="selectModel(p, m)"
                          >
                            <ProviderIcon :name="m.providerName" :size="16" />
                            <span class="ph-mp-item-name" :title="m.modelName">
                              {{ m.modelName }}
                            </span>
                            <CheckOutlined
                              v-if="
                                currentProvider === m.providerName &&
                                currentModelName === m.modelName
                              "
                              class="ph-mp-item-check"
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  </Spin>
                </div>

                <div
                  class="ph-mp-dropdown-footer"
                  @click="emit('openProviderSettings')"
                >
                  <SettingOutlined />
                  <span>模型供应商设置</span>
                </div>
              </div>
            </template>

            <div
              class="ph-mp-model-card"
              :class="{
                'is-active': modelDropdownOpen,
                'has-value': !!currentModelName,
              }"
            >
              <div class="ph-mp-model-card-icon">
                <ProviderIcon
                  v-if="currentProvider"
                  :name="currentProvider"
                  :size="20"
                />
                <RobotOutlined v-else />
              </div>
              <span
                v-if="currentModelName"
                class="ph-mp-model-card-name"
                :title="currentModelName"
              >
                {{ currentModelName }}
              </span>
              <span v-else class="ph-mp-model-card-placeholder">
                请选择模型
              </span>
              <span
                v-if="currentModelName"
                class="ph-mp-item-tag"
                :data-color="modelTypeColor(current.modelType || modelType)"
              >
                {{ modelTypeLabel(current.modelType || modelType) }}
              </span>
              <SettingOutlined class="ph-mp-model-card-cog" />
              <DownOutlined class="ph-mp-model-card-arrow" />
            </div>
          </Popover>

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
                  :auto-size="{ minRows: 2, maxRows: 6 }"
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
/* Popover 内容会 Teleport 到 body，不能用 scoped。 */
.ph-model-picker-popover .ant-popover-inner,
.ph-model-picker-popover .ant-popover-inner-content {
  padding: 0;
  border-radius: 12px;
  overflow: hidden;
}

/* Model pickers used inside the full-screen app-design drawer are teleported
 * to body.  The drawer root sits at z-index 1100, so the default popover layer
 * (1050) would open behind it and make the trigger appear unresponsive. */
.ph-model-picker-popover {
  z-index: 1200 !important;
}

.ph-model-picker-popover.as-popover__panel {
  padding: 0;
  border-radius: 12px;
  background: transparent;
  box-shadow: none;
}

/* Tailwind-backed Ant compatibility layer renders the panel below the trigger
 * instead of teleporting it. Keep the same edge treatment in both runtimes. */
.ph-mp-popover > .as-popover__panel {
  padding: 0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.ph-mp {
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  max-width: 92vw;
  max-height: 84vh;
  border: 1px solid #d9dee8;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

/* The model selector inside the settings surface is a field, not an inline
 * action. Make both the compatibility-popover anchor and its card consume the
 * full content width. */
.ph-mp .ph-mp-popover {
  display: flex;
  width: 100%;
}

/* 顶部标题栏 */
.ph-mp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 10px;
}
.ph-mp-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.ph-mp-close {
  padding: 0;
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
  width: 24px;
  height: 24px;
}
.ph-mp-close:hover {
  background: #f3f4f6;
  color: #4b5563;
}

/* 模型卡片（点击展开二级下拉） */
.ph-mp-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 18px 4px;
  overflow-y: auto;
}
.ph-mp-scroll::-webkit-scrollbar {
  width: 6px;
}
.ph-mp-scroll::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}
.ph-mp-model-card {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.ph-mp-model-card:hover {
  border-color: #a5b4fc;
}
.ph-mp-model-card.is-active {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.ph-mp-model-card.has-value {
  background: #fafbff;
}
.ph-mp-model-card-icon {
  flex: none;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6366f1;
}
.ph-mp-model-card-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-model-card-placeholder {
  flex: 1;
  font-size: 13px;
  color: #9ca3af;
}
.ph-mp-model-card-cog {
  color: #94a3b8;
  font-size: 12px;
}
.ph-mp-model-card-arrow {
  color: #94a3b8;
  font-size: 10px;
  transition: transform 0.15s;
}
.ph-mp-model-card.is-active .ph-mp-model-card-arrow {
  transform: rotate(180deg);
  color: #6366f1;
}

/* 二级下拉 */
.ph-mp-dropdown {
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  max-width: 92vw;
  border: 1px solid #d9dee8;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.14);
  overflow: hidden;
}
.ph-mp-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  background: #f9fafb;
}
.ph-mp-search-icon {
  color: #9ca3af;
}
.ph-mp-search-input {
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 13px;
  color: #1f2937;
}
.ph-mp-search-input::placeholder {
  color: #9ca3af;
}
.ph-mp-body {
  overflow-y: auto;
  padding: 6px 4px;
}
.ph-mp-body::-webkit-scrollbar {
  width: 6px;
}
.ph-mp-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}
.ph-mp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 16px;
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}
.ph-mp-empty-icon {
  font-size: 28px;
  opacity: 0.35;
}
.ph-mp-empty-hint {
  font-size: 11px;
  color: #cbd5e1;
}
.ph-mp-groups {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ph-mp-group + .ph-mp-group {
  margin-top: 2px;
  padding-top: 4px;
  border-top: 1px solid #f3f4f6;
}
.ph-mp-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  cursor: pointer;
  border-radius: 6px;
  user-select: none;
  transition: background 0.12s;
}
.ph-mp-group-header:hover {
  background: #f9fafb;
}
.ph-mp-fold {
  font-size: 10px;
  color: #9ca3af;
}
.ph-mp-group-name {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-group-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0 6px 22px;
}
.ph-mp-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.12s;
}
.ph-mp-item:hover {
  background: #f3f4f6;
}
.ph-mp-item.is-active {
  background: #eef2ff;
}
.ph-mp-item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas,
    'Liberation Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-item.is-active .ph-mp-item-name {
  color: #4338ca;
  font-weight: 500;
}
.ph-mp-item-tag {
  flex: none;
  padding: 1px 6px;
  font-size: 10px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  letter-spacing: 0.3px;
}
.ph-mp-item-tag[data-color='green'] {
  background: #ecfdf5;
  color: #059669;
}
.ph-mp-item-tag[data-color='orange'] {
  background: #fff7ed;
  color: #c2410c;
}
.ph-mp-item-tag[data-color='purple'] {
  background: #faf5ff;
  color: #7c3aed;
}
.ph-mp-item-check {
  color: #4338ca;
  font-size: 12px;
}
.ph-mp-dropdown-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-top: 1px solid #f0f0f0;
  color: #4338ca;
  font-size: 12px;
  cursor: pointer;
  background: #fafbff;
}
.ph-mp-dropdown-footer:hover {
  background: #eef2ff;
}

/* The dropdown is teleported into the host app. In Vben's dark theme the
 * Ant Popover surface is dark, so the light-theme slate text above becomes
 * effectively invisible even though the model rows are present. */
.dark .ph-mp-search {
  background: #18181b;
  border-bottom-color: #3f3f46;
}
.dark .ph-mp-search-input,
.dark .ph-mp-group-name,
.dark .ph-mp-item-name {
  color: #f4f4f5;
}
.dark .ph-mp-group + .ph-mp-group {
  border-top-color: #3f3f46;
}
.dark .ph-mp-group-header:hover,
.dark .ph-mp-item:hover {
  background: #27272a;
}
.dark .ph-mp-item.is-active {
  background: #312e81;
}
.dark .ph-mp-item.is-active .ph-mp-item-name {
  color: #e0e7ff;
}
.dark .ph-mp-dropdown-footer {
  border-top-color: #3f3f46;
  background: #18181b;
  color: #a5b4fc;
}
.dark .ph-mp-dropdown-footer:hover {
  background: #27272a;
}

/* 参数区 */
.ph-mp-params-block {
  padding-top: 4px;
}
.ph-mp-params-title {
  padding: 4px 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.ph-mp-params {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ph-mp-param {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ph-mp-param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}
.ph-mp-param-switch {
  flex: none;
}
.ph-mp-param-name {
  flex: none;
  width: 88px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-param-hint {
  color: #94a3b8;
  font-size: 12px;
  cursor: help;
}
.ph-mp-param-slider {
  flex: 1;
  min-width: 0;
  margin: 0 !important;
}
.ph-mp-param-num {
  flex: none;
  width: 88px;
  margin-left: auto;
}
.ph-mp-param-seg {
  margin-left: auto;
}
.ph-mp-param.is-block .ph-mp-param-row {
  min-height: unset;
}
.ph-mp-param-textarea {
  margin-left: 32px;
  font-size: 12px !important;
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
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.ph-mp-trigger:hover {
  border-color: #a5b4fc;
  background: #fafbff;
}
.ph-mp-trigger.is-active {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.ph-mp-trigger.has-value {
  background: #fafbff;
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
  color: #6366f1;
}
.ph-mp-trigger-name {
  flex: none;
  max-width: 55%;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-trigger-sub {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-mp-trigger-placeholder {
  flex: 1;
  font-size: 13px;
  color: #9ca3af;
}
.ph-mp-trigger-arrow {
  flex: none;
  font-size: 10px;
  color: #9ca3af;
  transition: transform 0.15s;
}
.ph-mp-trigger.is-active .ph-mp-trigger-arrow {
  transform: rotate(180deg);
  color: #6366f1;
}
</style>
