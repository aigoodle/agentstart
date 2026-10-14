<script setup lang="ts">
/**
 * ProviderApp — 一体化模型供应商配置页。
 *
 * 单一 prop `apiBase` (默认 `/api`)：
 *   • 内部三段式壳层 `<ProviderHubShell>` 处理 "已装 / 待配置 / 可安装"；
 *   • 加载状态、错误 toast、删除确认、进阶已装模型面板全在内部；
 *   • 宿主只需一句 `<ProviderApp />` 即可完整落地模型配置能力。
 */
import { onMounted, ref } from 'vue';

import { message, Modal, Spin, Switch } from '../../ui';

import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import {
  setProviderHubApiBase,
  setProviderHubHeaders,
  useProviderHub,
} from '../composables/useProviderHub';
import type {
  ModelEntity,
  ModelTestResult,
  ProviderView,
} from '../types';
import ModelCardGrid from './ModelCardGrid.vue';
import ProviderHubShell from './ProviderHubShell.vue';

interface Props {
  /** Backend base URL, default `/api`. */
  apiBase?: string;
  /** Extra headers; a function is evaluated again before every request. */
  headers?: AgentStartHeaders;
  /**
   * 是否显示进阶面板 (已装模型逐条操作: 设为默认 / 测试 / 删除 / 覆写凭证)。
   * 默认展示; 若不需要传 `:enable-advanced="false"` 隐藏。
   */
  enableAdvanced?: boolean;
}
const props = withDefaults(defineProps<Props>(), {
  enableAdvanced: true,
});

const globalConfig = useAgentStartConfig();
const resolvedApiBase = props.apiBase ?? globalConfig.apiBase ?? '/api';

// Composable is module-scoped — flip the base URL before first fetch.
setProviderHubApiBase(resolvedApiBase);
setProviderHubHeaders(() =>
  mergeAgentStartHeaders(globalConfig.headers, props.headers),
);

const { listProviders, listModels, deleteModel, setDefault } = useProviderHub();

const providers = ref<ProviderView[]>([]);
const models = ref<ModelEntity[]>([]);
const loading = ref(false);
const showAdvanced = ref(false);

async function refresh() {
  loading.value = true;
  try {
    const [ps, ms] = await Promise.all([listProviders(), listModels()]);
    providers.value = ps;
    models.value = ms;
  } catch (e: any) {
    message.error(e?.message ?? '加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);

async function onSetDefault(m: ModelEntity) {
  await setDefault(m.id);
  message.success('已设为默认');
  await refresh();
}

async function onTestModel(_m: ModelEntity, r: ModelTestResult) {
  if (r.ok) message.success(`连接成功 · ${r.latencyMs}ms`);
  else message.error(`连接失败: ${r.error ?? 'unknown'}`);
}

function onDelete(m: ModelEntity) {
  Modal.confirm({
    maskClosable: false,
    title: '删除模型',
    content: `确认删除「${m.providerName} / ${m.modelName}」？依赖它的知识库/智能体运行时会失败。`,
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      await deleteModel(m.id);
      message.success('已删除');
      await refresh();
    },
  });
}

function onEditCredentials(m: ModelEntity) {
  Modal.info({
    maskClosable: false,
    title: '编辑模型级凭证',
    content:
      `此模型继承供应商级 API Key。如需覆写单条模型的 endpoint/dimensions，` +
      `请通过 API PUT /models/${m.id}/credentials 直接调用。` +
      `\n\n(该操作已从 UI 收起 —— 99% 的场景直接改供应商级凭证更合适)`,
  });
}
</script>

<template>
  <div class="agent-start-provider-app">
    <Spin :spinning="loading">
      <ProviderHubShell :models="models" @change="refresh" />

      <!-- 进阶: 已装模型的行级操作，默认收起 -->
      <div v-if="enableAdvanced && models.length > 0" class="mt-6">
        <div class="mb-2 flex items-center gap-2">
          <span class="text-sm font-medium">进阶：已装模型逐条管理</span>
          <Switch v-model:checked="showAdvanced" size="small" />
          <span class="text-xs text-gray-500">
            设为默认 / 测试 / 删除 / 覆写凭证
          </span>
        </div>
        <div v-if="showAdvanced">
          <ModelCardGrid
            :models="models"
            :providers="providers"
            @set-default="onSetDefault"
            @test="onTestModel"
            @delete="onDelete"
            @edit-credentials="onEditCredentials"
          />
        </div>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.agent-start-provider-app {
  /* 间距由内部 ProviderHubShell (.phs-root) 统一管理，外层不再叠加 padding */
  padding: 0;
}
</style>
