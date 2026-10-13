<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';
import { createAgentStartClient } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import MemoryWindow from '@/components/MemoryWindow.vue';
import PromptEditor from '@/components/PromptEditor.vue';
import ToolItemCard from '@/components/ToolItemCard.vue';
import McpServerChooser from '@/components/McpServerChooser.vue';
import OutputItemCard from '@/workflow/OutputItemCard.vue';
import WorkflowModelPicker from '@/workflow/WorkflowModelPicker.vue';
import SkillSelect from '../../../skill-hub/SkillSelect.vue';
import WfField from '@/workflow/WfField.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();
formState.value.runtimeType ??= 'NATIVE';
const runtimeTypes = ref(['NATIVE']);
const runtimeError = ref('');
const mcpChooserRef = ref();
formState.value.agentParameters ??= {};
formState.value.agentParameters.mcpServers ??= [];
formState.value.skillIds ??= [];
const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: config.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(config.headers) });
onMounted(async () => {
  try { runtimeTypes.value = (await client.agents.listRuntimes()).map(item => item.type); }
  catch { runtimeError.value = '无法加载运行方式，请确认后台已启动。'; }
});

function openMcpChooser() {
  mcpChooserRef.value?.showModal?.(formState.value.agentParameters.mcpServers.map((item: any) => item.id));
}
async function selectMcpServers(servers: any[]) {
  formState.value.agentParameters.mcpServers = servers;
  const selectedIds = new Set(servers.map(server => server.id));
  try {
    const catalog = await client.tools.list();
    const mcpTools = catalog.filter(tool => tool.mcpServerId && selectedIds.has(tool.mcpServerId));
    const regularTools = (formState.value.tools || []).filter((tool: any) => !tool.mcpServerId);
    formState.value.tools = [...regularTools, ...mcpTools.map(tool => ({ ...tool, enabled: true }))];
  } catch { /* The server selection remains saved; the tool picker can retry discovery. */ }
}
function removeMcpServer(index: number) {
  const [removed] = formState.value.agentParameters.mcpServers.splice(index, 1);
  formState.value.tools = (formState.value.tools || []).filter((tool: any) => tool.mcpServerId !== removed?.id);
}

const OUTPUT_DEFAULTS = [
  { name: 'text', type: 'string', label: '生成内容' },
  { name: 'files', type: 'file', label: 'Agent 生成的文件' },
  { name: 'json', type: 'array', label: 'Agent 生成的 JSON' },
];
const agentOutputState = { output: OUTPUT_DEFAULTS };
</script>

<template>
  <div class="wf-config-section">
    <WfField title="运行方式" required>
      <a-select v-model:value="formState.runtimeType" :options="runtimeTypes.map(value => ({ value, label: value === 'NATIVE' ? '内置 Agent' : value === 'PLUGIN' ? '插件 Agent' : value }))" style="width: 100%" />
      <small v-if="runtimeError">{{ runtimeError }}</small>
    </WfField>
    <WfField v-if="formState.runtimeType !== 'NATIVE'" title="运行资源" required>
      <a-input v-model:value="formState.runtimeRef" placeholder="插件 ID/动作 ID，例如 acme.video/plan" />
    </WfField>
  </div>
  <div v-if="formState.runtimeType === 'NATIVE'" class="wf-config-section">
    <WfField title="Agent 策略" required>
      <template #tooltip>
        推理循环的实现方式。ReAct 用观察-思考-行动多轮；FunctionCalling 由模型直接触发工具。
      </template>
      <a-select
        v-model:value="formState.agentStrategy"
        :options="workflow_utils.agentStrategy"
        style="width: 100%"
      />
    </WfField>

    <WfField title="模型" required>
      <WorkflowModelPicker
        v-model="formState.model"
        model-type="LLM"
        placeholder="点击选择 Agent 模型"
      />
    </WfField>
  </div>

  <div v-if="formState.runtimeType === 'NATIVE'" class="wf-config-section">
    <WfField title="工具">
      <template #tooltip>
        Agent 可调用的工具集合，运行时按模型返回决定调用哪个。
      </template>
      <ToolItemCard v-model="formState" />
    </WfField>

    <WfField title="Skill" is-subtitle>
      <template #tooltip>选择管理员发布的业务执行规范，运行时自动补充到系统提示词。</template>
      <SkillSelect v-model="formState.skillIds" />
    </WfField>

    <WfField title="MCP Server" is-subtitle>
      <template #tooltip>
        从已配置的 MCP 服务中选择，服务提供的工具会自动加入工具集。
      </template>
      <div class="agent-mcp-header"><span>已选择 {{ formState.agentParameters.mcpServers.length }} 个服务</span><a-button size="small" type="primary" :icon="h(PlusOutlined)" @click="openMcpChooser">选择</a-button></div>
      <div v-if="!formState.agentParameters.mcpServers.length" class="agent-mcp-empty">尚未添加 MCP 服务</div>
      <div v-else class="agent-mcp-list"><div v-for="(server, index) in formState.agentParameters.mcpServers" :key="server.id"><span><b>{{ server.name }}</b><small>{{ server.transport }}</small></span><a-button type="text" danger size="small" :icon="h(DeleteOutlined)" @click="removeMcpServer(index)" /></div></div>
      <McpServerChooser ref="mcpChooserRef" @submit="selectMcpServers" />
    </WfField>
  </div>

  <PromptEditor
    v-if="formState.runtimeType === 'NATIVE'"
    class="wf-config-prompt"
    title="SYSTEM"
    :node-id="nodeId"
    v-model="formState.systemPrompt.text"
  />

  <PromptEditor
    class="wf-config-prompt"
    title="USER"
    :node-id="nodeId"
    v-model="formState.userPrompt.text"
  />

  <div class="wf-config-section">
    <WfField title="对话记忆">
      <MemoryWindow v-model="formState.memory" />
    </WfField>

    <WfField title="最大迭代次数" is-subtitle>
      <template #tooltip>
        单次运行内 Agent 最多进行多少轮 think/act。防止死循环。
      </template>
      <div class="agent-slider-row">
        <a-slider
          v-model:value="formState.memory.windows"
          :step="1"
          :min="1"
          :max="20"
          style="flex: 1"
        />
        <a-input-number
          v-model:value="formState.memory.windows"
          :min="1"
          :max="20"
          size="small"
          style="width: 88px"
        />
      </div>
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField class="wf-output-field" title="输出变量" foldable default-fold>
      <OutputItemCard
        :model-value="agentOutputState"
        :show-title="false"
        readonly
      />
    </WfField>
  </div>
</template>

<style scoped>
.agent-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.agent-mcp-header{display:flex;align-items:center;justify-content:space-between;font-size:11px;color:#9ca3af}.agent-mcp-empty{margin-top:8px;padding:14px;border:1px dashed #e5e7eb;border-radius:6px;text-align:center;font-size:12px;color:#9ca3af}.agent-mcp-list{display:flex;flex-direction:column;gap:6px;margin-top:8px}.agent-mcp-list>div{display:flex;align-items:center;justify-content:space-between;padding:7px 9px;border:1px solid #e5e7eb;border-radius:6px}.agent-mcp-list span{display:flex;flex-direction:column}.agent-mcp-list small{font-size:10px;color:#9ca3af}

</style>
