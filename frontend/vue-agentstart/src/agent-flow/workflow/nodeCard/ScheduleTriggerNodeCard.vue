<script setup lang="ts">
import { onMounted, ref, watchEffect } from 'vue';

import PromptEditor from '@/components/PromptEditor.vue';
import VariableSelector from '@/components/VariableSelector.vue';
import OutputItemCard from '@/workflow/OutputItemCard.vue';
import OutputStructureToggle from '@/workflow/OutputStructureToggle.vue';
import WfField from '@/workflow/WfField.vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
const props = defineProps<{
  nodeId?: string;
  currentAppId?: string;
  workflowOptionsLoader?: Function;
}>();
const formState: any = defineModel();
const workflowApps = ref<Array<{
  value: string;
  label: string;
  appId: string;
  icon?: string;
  iconBackground?: string;
  inputVariables?: Array<Record<string, unknown>>;
}>>([]);
const workflowLoading = ref(false);
const workflowLoadError = ref('');

function ensureData() {
  formState.value.name ||= '工作流定时任务';
  formState.value.targetWorkflowId ||= '';
  formState.value.targetWorkflowName ||= '';
  formState.value.targetWorkflowIcon ||= '';
  formState.value.targetWorkflowIconBackground ||= '';
  formState.value.model ||= { modelId: '', modelName: '', mode: 'chat', completionParams: {} };
  formState.value.inputVariableSelector ||= [];
  formState.value.extractionPrompt ||= {
    id: 'schedule_extraction_prompt',
    role: 'system',
    text: '',
  };
  if (typeof formState.value.schedule !== 'string') formState.value.schedule = '';
}
ensureData();
watchEffect(ensureData);

onMounted(async () => {
  if (!props.workflowOptionsLoader) {
    workflowLoadError.value = '宿主未配置工作流选择器接口';
    return;
  }
  workflowLoading.value = true;
  try {
    const apps = await props.workflowOptionsLoader() as Array<{
      appId: string;
      workflowId: string;
      name: string;
      icon?: string;
      iconBackground?: string;
      inputVariables?: Array<Record<string, unknown>>;
    }>;
    workflowApps.value = apps
      .filter((app) => app.appId !== props.currentAppId)
      .map((app) => ({
        value: app.workflowId,
        label: app.name,
        appId: app.appId,
        icon: app.icon,
        iconBackground: app.iconBackground,
        inputVariables: app.inputVariables || [],
      }));
    syncTargetWorkflow(formState.value.targetWorkflowId);
  } catch (error) {
    workflowLoadError.value = error instanceof Error ? error.message : '工作流应用加载失败';
  } finally {
    workflowLoading.value = false;
  }
});

function syncTargetWorkflow(workflowId?: string) {
  const selected = workflowApps.value.find((item) => item.value === workflowId);
  if (!selected) {
    if (!workflowId) {
      formState.value.targetWorkflowName = '';
      formState.value.targetWorkflowIcon = '';
      formState.value.targetWorkflowIconBackground = '';
      formState.value.targetWorkflowInputs = [];
    }
    return;
  }
  formState.value.targetWorkflowName = selected.label;
  formState.value.targetWorkflowIcon = selected.icon || '🧬';
  formState.value.targetWorkflowIconBackground = selected.iconBackground || '#FEF6EE';
  formState.value.targetWorkflowInputs = selected.inputVariables || [];
}
</script>

<template>
  <div class="wf-config-section">
    <WfField title="任务名称" required>
      <a-input v-model:value="formState.name" placeholder="例如：会话定时任务" />
      <div class="wf-help">这是节点名称；新增定时任务时，模型会根据事项和时间生成便于以后识别删除的任务名称。</div>
    </WfField>
    <WfField title="上下文内容" required>
      <VariableSelector
        v-model="formState.inputVariableSelector"
        :node-id="nodeId || ''"
        placeholder="选择用户描述或上游节点的文本输出"
      />
      <div class="wf-help">模型会结合当前用户已有任务，一次识别新增、修改或删除意图；修改和删除只能选择该用户自己的任务。</div>
    </WfField>
    <WfField title="参数提取模型" required>
      <WorkflowModelPicker
        v-model="formState.model"
        model-type="LLM"
        placeholder="点击选择 LLM 模型"
      />
    </WfField>
  </div>

  <PromptEditor
    v-if="formState.extractionPrompt"
    class="wf-config-prompt"
    title="提示词补充"
    :node-id="nodeId || ''"
    v-model="formState.extractionPrompt.text"
    placeholder="可选：补充业务规则，例如：未说明时间时默认上午 9 点"
  />

  <div class="wf-config-section">
    <WfField title="目标工作流" required>
      <a-select
        v-model:value="formState.targetWorkflowId"
        show-search
        allow-clear
        :loading="workflowLoading"
        :options="workflowApps"
        option-filter-prop="label"
        placeholder="选择当前租户下已发布的工作流应用"
        class="wf-full-width"
        @change="syncTargetWorkflow"
      >
        <template #option="option">
          <div class="workflow-option">
            <span
              class="workflow-option-icon"
              :style="{ background: option.iconBackground || '#FEF6EE' }"
            >{{ option.icon || '🧬' }}</span>
            <span class="workflow-option-name">{{ option.label }}</span>
          </div>
        </template>
      </a-select>
      <div v-if="workflowLoadError" class="wf-error">{{ workflowLoadError }}</div>
      <div v-else-if="!workflowLoading && workflowApps.length === 0" class="wf-help">当前租户下没有其他已发布的工作流应用。</div>
      <div v-else class="wf-help">新增时，提取出的 data 对象会作为开始节点输入传给该工作流；删除时不执行目标工作流。</div>
    </WfField>
    <WfField class="wf-output-field" title="输出变量" foldable :default-fold="false">
      <template #operations>
        <OutputStructureToggle v-model="formState" />
      </template>
      <OutputItemCard v-model="formState" :show-title="false" />
    </WfField>
  </div>
</template>

<style scoped>
.wf-full-width { width: 100%; }
.wf-help { margin-top: 6px; color: #9ca3af; font-size: 11px; line-height: 1.55; }
.wf-error { margin-top: 6px; color: #dc2626; font-size: 11px; }
.workflow-option { display: flex; align-items: center; gap: 12px; }
.workflow-option-icon { display: inline-flex; flex: none; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 6px; }
.workflow-option-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
