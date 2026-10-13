<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
import JsonSchemaForm from '../../../ui/components/JsonSchemaForm.vue';
import type { JsonSchema } from '../../../connector-hub/types';
import { createAgentStartClient } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import PromptEditor from '@/components/PromptEditor.vue';

defineProps<{ nodeId?: string }>();
const form: any = defineModel();
form.value.model ??= {};
form.value.parameters ??= {};
const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: config.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(config.headers) });
const schema = ref<JsonSchema>();
const error = ref('');
const installationError = ref('');
const loading = ref(false);
let revision = 0;
const identity = () => JSON.stringify([form.value.model?.providerName, form.value.model?.modelName, form.value.model?.modelId]);
watch(identity, async (next, previous) => {
  const ticket = ++revision;
  schema.value = undefined;
  error.value = '';
  loading.value = false;
  if (previous !== undefined && next !== previous) form.value.parameters = {};
  if (!form.value.model?.modelName && !form.value.model?.modelId) return;
  loading.value = true;
  try {
    const result = await client.request<{ parameterSchema: JsonSchema }>('/video-models/capabilities', {
      method: 'POST', body: JSON.stringify(form.value.model),
    });
    if (ticket === revision) schema.value = result.parameterSchema;
  } catch (cause: any) {
    if (ticket === revision) error.value = cause?.message ?? '无法读取视频模型配置';
  } finally { if (ticket === revision) loading.value = false; }
}, { immediate: true });
onMounted(async () => {
  try {
    const installations = await client.connectors.listInstallations();
    if (!installations.some(item => item.provider === 'plugin' && item.connectorId === 'media.video' && item.enabled))
      installationError.value = '请先在插件管理中安装并启用“视频生成”。';
  } catch { installationError.value = '无法确认视频插件状态，请检查插件管理。'; }
});
</script>

<template>
  <section class="video-config">
    <p v-if="installationError" role="alert">{{ installationError }}</p>
    <h4>视频模型</h4>
    <WorkflowModelPicker v-model="form.model" model-type="VIDEO" :show-params="false" :auto-load-default="false" placeholder="选择已配置的视频模型" />
    <h4>视频提示词</h4>
    <PromptEditor v-model="form.prompt" :node-id="nodeId || ''" title="提示词" />
    <p v-if="loading">读取模型参数…</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="schema">
      <h4>生成参数</h4>
      <JsonSchemaForm v-model="form.parameters" :schema="schema" :node-id="nodeId" :allow-advanced="false" />
    </template>
    <p class="hint">生成期间流程会等待，完成后通过 result.data.videoUrl 输出视频地址。</p>
  </section>
</template>

<style scoped>
.video-config { display: grid; gap: 12px; }
h4, p { margin: 0; }
[role='alert'] { color: #b42318; }
.hint { color: #667085; font-size: 12px; }
</style>
