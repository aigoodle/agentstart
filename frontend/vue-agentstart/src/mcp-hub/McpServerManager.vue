<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {BugOutlined, DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons-vue';
import {Alert, Button, Drawer, Empty, Form, FormItem, Input, Card, Modal, Select, Spin, Switch, Tag, Textarea, message} from '../ui';
import {createAgentStartClient, type ToolCatalogItem} from '../client';
import {useAgentStartClient} from '../client/vue';
import {mergeAgentStartHeaders, useAgentStartConfig} from '../config';
import type {McpServerConfig, SaveMcpServerConfig} from './types';

const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(global.headers),
});
const servers = ref<McpServerConfig[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const editing = ref(false);
const form = ref<SaveMcpServerConfig>(emptyForm());
const argsText = ref('');
const envText = ref('{}');
const detailServer = ref<McpServerConfig>();
const serverTools = ref<ToolCatalogItem[]>([]);
const toolsLoading = ref(false);
const toolsError = ref('');
const selectedTool = ref<ToolCatalogItem>();
const invokeArgs = ref('{}');
const invokeOutput = ref('');
const invoking = ref(false);
const testing = ref(false);
const testResult = ref<McpTestResult>();

interface McpTestResult {
  success: boolean;
  status: string;
  toolCount?: number;
  message: string;
  durationMs: number;
}

interface ToolParameter {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

function schemaOf(tool?: ToolCatalogItem): Record<string, any> {
  if (!tool?.inputSchema) return {};
  if (typeof tool.inputSchema === 'object') return tool.inputSchema as Record<string, any>;
  try {
    return JSON.parse(tool.inputSchema) as Record<string, any>;
  } catch {
    return {};
  }
}

function schemaType(schema: Record<string, any>): string {
  if (Array.isArray(schema.type)) return schema.type.join(' | ');
  if (schema.type === 'array') return `array<${schemaType(schema.items ?? {}) || 'unknown'}>`;
  if (schema.type) return String(schema.type);
  if (schema.properties) return 'object';
  return 'unknown';
}

function parametersOf(tool?: ToolCatalogItem): ToolParameter[] {
  const schema = schemaOf(tool);
  const required = new Set<string>(Array.isArray(schema.required) ? schema.required : []);
  return Object.entries(schema.properties ?? {}).map(([name, value]) => {
    const field = (value ?? {}) as Record<string, any>;
    return {
      name,
      type: schemaType(field),
      required: required.has(name),
      description: field.description || field.title || '暂无说明',
    };
  });
}

function exampleValue(schema: Record<string, any>): unknown {
  if (schema.default !== undefined) return schema.default;
  if (schema.example !== undefined) return schema.example;
  if (Array.isArray(schema.examples) && schema.examples.length) return schema.examples[0];
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0];
  const type = Array.isArray(schema.type) ? schema.type.find((value: string) => value !== 'null') : schema.type;
  if (type === 'integer' || type === 'number') return schema.minimum ?? 0;
  if (type === 'boolean') return false;
  if (type === 'array') return [];
  if (type === 'object' || schema.properties) {
    return Object.fromEntries(Object.entries(schema.properties ?? {}).map(([name, field]) =>
        [name, exampleValue((field ?? {}) as Record<string, any>)]));
  }
  return '';
}

function generateInvokeArgs(tool?: ToolCatalogItem) {
  const schema = schemaOf(tool);
  const value = schema.type === 'object' || schema.properties ? exampleValue(schema) : {};
  invokeArgs.value = JSON.stringify(value, null, 2);
}

const detailAddress = computed(() => detailServer.value?.transport !== 'STDIO'
    ? detailServer.value?.url
    : [detailServer.value?.command, ...(detailServer.value?.args || [])].filter(Boolean).join(' '));

function emptyForm(): SaveMcpServerConfig {
  return {name: '', transport: 'HTTP', enabled: true, url: '', command: '', args: [], env: {}};
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    servers.value = await client.request('/mcp-servers');
  } catch (exception: any) {
    error.value = exception?.message ?? 'MCP 配置加载失败';
  } finally {
    loading.value = false;
  }
}

function open(row?: McpServerConfig) {
  form.value = row ? {...row, args: row.args ?? [], env: {}} : emptyForm();
  argsText.value = (row?.args ?? []).join('\n');
  envText.value = '{}';
  error.value = '';
  editing.value = true;
}

function closeEditor() {
  if (!saving.value) editing.value = false;
}

async function save() {
  const name = form.value.name.trim();
  if (!name) return void message.warning('请输入 MCP 服务名称');
  if ((form.value.transport === 'HTTP' || form.value.transport === 'SSE') && !form.value.url?.trim()) return void message.warning('请输入 MCP 服务地址');
  if (form.value.transport === 'STDIO' && !form.value.command?.trim()) return void message.warning('请输入启动命令');
  let env: Record<string, string> = {};
  try {
    const parsed = JSON.parse(envText.value || '{}');
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error();
    env = parsed;
  } catch {
    return void message.error('环境变量必须是合法的 JSON 对象');
  }
  saving.value = true;
  error.value = '';
  try {
    const payload: SaveMcpServerConfig = {
      ...form.value, name, url: form.value.url?.trim(), command: form.value.command?.trim(),
      args: argsText.value.split('\n').map(value => value.trim()).filter(Boolean), env,
    };
    await client.request('/mcp-servers', {method: 'POST', body: JSON.stringify(payload)});
    editing.value = false;
    message.success(form.value.id ? 'MCP 服务已更新' : 'MCP 服务已创建');
    await load();
  } catch (exception: any) {
    error.value = exception?.message ?? '保存失败';
    message.error(error.value);
  } finally {
    saving.value = false;
  }
}

function remove(row: McpServerConfig) {
  Modal.confirm({
    title: '删除 MCP 服务', content: `确定删除“${row.name}”吗？此操作无法撤销。`,
    okText: '删除', cancelText: '取消', okButtonProps: {danger: true},
    async onOk() {
      try {
        await client.request(`/mcp-servers/${encodeURIComponent(row.id)}`, {method: 'DELETE'});
        message.success('MCP 服务已删除');
        await load();
      } catch (exception: any) {
        error.value = exception?.message ?? '删除失败';
        message.error(error.value);
        throw exception;
      }
    },
  });
}

async function test(row: McpServerConfig) {
  detailServer.value = row;
  testResult.value = undefined;
  toolsError.value = '';
  testing.value = true;
  try {
    const result = await client.request<McpTestResult>(`/mcp-servers/${encodeURIComponent(row.id)}/test`, {method: 'POST'});
    testResult.value = result;
    row.status = result.status;
    row.toolCount = result.toolCount;
    row.lastError = result.success ? undefined : result.message;
    if (result.success) await loadServerTools(row);
  } catch (exception: any) {
    testResult.value = {success: false, status: 'ERROR', message: exception?.message ?? '连接测试失败', durationMs: 0};
  } finally {
    testing.value = false;
  }
}

async function loadServerTools(row: McpServerConfig) {
  selectedTool.value = undefined;
  serverTools.value = [];
  toolsError.value = '';
  invokeOutput.value = '';
  toolsLoading.value = true;
  try {
    const catalog = await client.tools.list();
    serverTools.value = catalog.filter(tool => tool.mcpServerId === row.id || tool.provider === row.id);
    row.toolCount = serverTools.value.length;
    selectedTool.value = serverTools.value[0];
    generateInvokeArgs(selectedTool.value);
  } catch (exception: any) {
    toolsError.value = exception?.message ?? 'MCP 工具列表加载失败';
  } finally {
    toolsLoading.value = false;
  }
}

async function openDetail(row: McpServerConfig) {
  detailServer.value = row;
  testResult.value = undefined;
  invokeOutput.value = '';
  await loadServerTools(row);
}

function selectTool(tool: ToolCatalogItem) {
  selectedTool.value = tool;
  generateInvokeArgs(tool);
  invokeOutput.value = '';
}

async function invokeTool() {
  if (!selectedTool.value) return;
  let args: Record<string, unknown>;
  try {
    args = JSON.parse(invokeArgs.value || '{}');
    if (!args || Array.isArray(args) || typeof args !== 'object') throw new Error();
  } catch {
    return void message.error('调用参数必须是合法的 JSON 对象');
  }
  invoking.value = true;
  invokeOutput.value = '';
  try {
    const result = await client.tools.invoke(selectedTool.value.name, args);
    invokeOutput.value = typeof result === 'string' ? result : JSON.stringify(result, null, 2);
    message.success('工具调用成功');
  } catch (exception: any) {
    invokeOutput.value = `调用失败：${exception?.message ?? exception}`;
  } finally {
    invoking.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="mcp as-management">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px; font-weight: bold">M</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">MCP 服务</div>
          <div class="as-page-subtitle">
            Model Context Protocol · 工具发现与调用
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <Button type="primary" @click="open()">
          <PlusOutlined/>
          新增 MCP 服务
        </Button>
      </div>
    </div>
    <Alert v-if="error" class="mcp-alert" type="error" show-icon closable :message="error" @close="error = ''"/>
    <Spin :spinning="loading">
      <div v-if="!loading && !servers.length" class="as-management-empty">
        <Empty description="尚未配置 MCP 服务"/>
      </div>
      <div v-else class="mcp-list as-management-grid">
        <Card variant="management"
          v-for="server in servers"
          :key="server.id"
          :title="server.name"
          :subtitle="server.transport"
        >
          <template #icon>
            <span class="mcp-server-icon">M</span>
          </template>
          <template #description>
            <code class="mcp-address"
                  :title="server.transport !== 'STDIO' ? server.url : [server.command, ...(server.args || [])].join(' ')">{{
                server.transport !== 'STDIO' ? server.url : [server.command, ...(server.args || [])].join(' ')
              }}</code>
            <div class="mcp-status">
              <b :class="`status-${(server.status || 'unknown').toLowerCase()}`">{{ server.status || 'UNKNOWN' }}</b>
              <small>{{ server.toolCount ?? '待发现' }}{{ server.toolCount == null ? '' : ' 个工具' }}</small>
            </div>
            <div class="mcp-card-message">
              <p v-if="server.lastError" class="mcp-bad">{{ server.lastError }}</p>
              <p v-else>{{ server.enabled ? '服务已启用，可查看并调用其工具。' : '服务当前已停用。' }}</p>
            </div>
          </template>
          <template #actions>
            <div class="mcp-actions">
              <Button type="primary" size="small" @click="openDetail(server)">
                <BugOutlined/>
                查看与调试
              </Button>
              <Button size="small" @click="open(server)">
                <EditOutlined/>
                编辑
              </Button>
              <Button size="small" danger @click="remove(server)">
                <DeleteOutlined/>
                删除
              </Button>
            </div>
          </template>
        </Card>
      </div>
    </Spin>

    <Modal v-model:open="editing" :width="640" :mask-closable="false" :keyboard="!saving"
           :closable="!saving" :destroy-on-close="true" @cancel="closeEditor">
      <template #title>
        <div class="as-modal-heading">
          <div class="as-modal-title">{{ form.id ? '编辑 MCP 服务' : '新增 MCP 服务' }}</div>
          <div class="as-modal-subtitle">配置连接方式和运行参数，保存后即可在 Agent 与工作流中使用。</div>
        </div>
      </template>
      <Form layout="vertical" class="mcp-form" @submit.prevent="save">
        <FormItem label="服务名称" required><Input v-model:value="form.name" placeholder="例如 GitHub MCP"
                                                   :maxlength="80"/></FormItem>
        <FormItem label="传输方式" required><Select v-model:value="form.transport" :options="[
          { label: 'Streamable HTTP（/mcp）', value: 'HTTP' }, { label: 'HTTP + SSE（旧协议）', value: 'SSE' },
          { label: 'STDIO 本地进程', value: 'STDIO' },
        ]"/></FormItem>
        <FormItem v-if="form.transport === 'HTTP' || form.transport === 'SSE'" label="服务地址" required><Input v-model:value="form.url"
                                                                                    placeholder="https://example.com/mcp"/>
        </FormItem>
        <template v-else>
          <FormItem label="启动命令" required><Input v-model:value="form.command" placeholder="npx"/></FormItem>
          <FormItem label="命令参数" extra="每行填写一个参数">
            <Input.TextArea v-model:value="argsText" :rows="4"
                            placeholder="-y&#10;@modelcontextprotocol/server-filesystem"/>
          </FormItem>
          <FormItem label="环境变量 JSON" extra="环境变量仅传递给 MCP 子进程，列表接口不会返回其内容。">
            <Input.TextArea v-model:value="envText" class="mcp-code-input" :rows="5" placeholder='{"API_KEY":"..."}'/>
          </FormItem>
        </template>
        <FormItem label="启用状态">
          <div class="mcp-switch-row">
            <Switch v-model:checked="form.enabled"/>
            <span>{{ form.enabled ? '已启用' : '已停用' }}</span></div>
        </FormItem>
      </Form>
      <template #footer>
        <Button :disabled="saving" @click="closeEditor">取消</Button>
        <Button type="primary" :loading="saving" @click="save">保存</Button>
      </template>
    </Modal>

    <Drawer :open="!!detailServer" placement="left" width="min(1120px, 94vw)" destroy-on-close
            body-class="mcp-debug-drawer-body" :body-style="{ padding: '16px', overflow: 'hidden' }"
            @update:open="open => { if (!open) detailServer = undefined }">
      <template #title>
        <div class="mcp-detail-title"><span class="mcp-server-icon">M</span>
          <div><strong>{{ detailServer?.name }}</strong><small>{{ detailServer?.transport }} · {{
              detailAddress
            }}</small></div>
        </div>
      </template>
      <div class="mcp-debug-drawer-content">
        <div class="mcp-test-bar">
          <div><strong>连接诊断</strong><small>重新连接 MCP 服务并发现其工具，成功后可直接选择工具进行调用。</small></div>
          <Button :loading="testing" @click="detailServer && test(detailServer)">重新连接并发现工具</Button>
        </div>
        <Alert v-if="testResult" class="mcp-test-result" :type="testResult.success ? 'success' : 'error'" show-icon
               :message="`${testResult.message}（耗时 ${testResult.durationMs} ms）`"/>
        <Alert v-if="toolsError" class="mcp-test-result" type="error" show-icon :message="toolsError"/>
        <Spin :spinning="toolsLoading" class="mcp-debug-spin">
          <div v-if="!toolsLoading && serverTools.length" class="mcp-tool-browser">
          <aside class="mcp-tool-list">
            <div class="mcp-tool-list-head"><strong>可用工具</strong><span>{{ serverTools.length }}</span></div>
            <button v-for="tool in serverTools" :key="tool.name" type="button"
                    :class="{ active: selectedTool?.name === tool.name }" @click="selectTool(tool)">
              <span class="mcp-tool-glyph">⌘</span><span class="mcp-tool-copy"><b>{{ tool.label || tool.name }}</b>
                <small>{{ tool.name }}</small>
                <p :title="tool.description || '暂无工具说明'">{{ tool.description || '暂无工具说明' }}</p>
              </span>
            </button>
          </aside>
          <section v-if="selectedTool" class="mcp-tool-detail">
            <div class="mcp-tool-heading">
              <div><h3>{{ selectedTool.label || selectedTool.name }}</h3><code>{{ selectedTool.name }}</code></div>
              <Tag color="blue">MCP 工具</Tag>
            </div>
            <p class="mcp-tool-description">{{ selectedTool.description || '暂无工具说明' }}</p>
            <div class="mcp-section-title"><strong>参数说明</strong><span>{{
                parametersOf(selectedTool).length
              }} 个参数</span></div>
            <div v-if="parametersOf(selectedTool).length" class="mcp-parameter-table-wrap">
              <table class="mcp-parameter-table">
                <thead>
                <tr>
                  <th>参数</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>说明</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="parameter in parametersOf(selectedTool)" :key="parameter.name">
                  <td><code>{{ parameter.name }}</code></td>
                  <td>{{ parameter.type }}</td>
                  <td>{{ parameter.required ? '是' : '否' }}</td>
                  <td>{{ parameter.description }}</td>
                </tr>
                </tbody>
              </table>
            </div>
            <Empty v-else description="该工具不需要参数"/>
            <Form layout="vertical" class="mcp-invoke-form">
              <FormItem label="调用参数（JSON）" extra="已根据工具 JSON Schema 自动生成，可按实际业务值修改。">
                <Textarea v-model:value="invokeArgs" :rows="8"/>
              </FormItem>
              <div class="mcp-invoke-action">
                <Button @click="generateInvokeArgs(selectedTool)">重新生成参数</Button>
                <Button type="primary" :loading="invoking" @click="invokeTool">调用工具</Button>
              </div>
              <FormItem v-if="invokeOutput" label="调用结果">
                <pre class="mcp-output">{{ invokeOutput }}</pre>
              </FormItem>
            </Form>
          </section>
          </div>
          <Empty v-else-if="!toolsLoading && !toolsError" class="mcp-tools-empty"
                 description="该 MCP 服务尚未发现可用工具，请先测试连接"/>
        </Spin>
      </div>
    </Drawer>
  </div>
</template>

<style scoped>
@reference "tailwindcss/theme.css";

.mcp {
  color: var(--as-mgmt-text);
}

.mcp-page-header, .mcp-actions, .mcp-status, .mcp-card-head, .mcp-detail-title, .mcp-tool-heading, .mcp-section-title, .mcp-invoke-action {
  @apply flex items-center justify-between gap-3;
}

h2, h3, p {
  @apply m-0;
}

.mcp-page-tip {
  color: var(--as-mgmt-muted);
  font-size: 12px;
}

.mcp-alert {
  margin-top: 20px;
}

.mcp-empty {
  margin-top: 24px;
  padding: 48px 16px;
  border-radius: 10px;
  background: #fff;
}

.mcp-list {
  margin-top: 6px;
}

.mcp-list article {
  @apply min-h-[215px] overflow-hidden p-3.5;
}

.mcp-card-head {
  @apply min-w-0 justify-start;
}

.mcp-server-icon {
  @apply grid size-[34px] shrink-0 place-items-center rounded-[9px] bg-[#eef4ff] text-[15px] font-bold text-[#155eef];
}

.mcp-summary {
  @apply min-w-0 flex-1;
}

.mcp-summary h3 {
  @apply truncate text-sm;
}

.mcp-summary span {
  display: inline-block;
  margin-top: 2px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 10px;
}

.mcp-address {
  @apply mt-3 block truncate text-[11px] text-[#667085];
}

.mcp-status {
  margin-top: 12px;
}

.mcp-status b {
  color: #475467;
  font-size: 10px;
  font-weight: 600;
}

.mcp-status b::before {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: #98a2b3;
  content: '';
}

.mcp-status b.status-connected::before, .mcp-status b.status-configured::before {
  background: #12b76a;
}

.mcp-status small {
  color: #6b7280;
}

.mcp-card-message {
  min-height: 34px;
  margin: 7px 0;
  color: #98a2b3;
  font-size: 11px;
  line-height: 17px;
}

.mcp-bad {
  margin-top: 5px;
  color: #b91c1c;
}

.mcp-actions {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mcp-list :deep(.as-management-card__actions) {
  width: 100%;
}

.mcp-actions .as-btn {
  min-width: 0;
  padding-inline: 9px;
}

.mcp-actions :deep(.as-btn) {
  gap: 4px;
}

.mcp-form {
  padding-top: 8px;
}

.mcp-code-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.mcp-switch-row {
  @apply flex items-center gap-2.5 text-[#64748b];
}

.mcp-detail-title {
  @apply min-w-0 justify-start;
}

.mcp-detail-title strong, .mcp-detail-title small {
  @apply block;
}

.mcp-detail-title small {
  overflow: hidden;
  max-width: 760px;
  margin-top: 2px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mcp-debug-drawer-content {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.mcp-debug-drawer-content :deep(.mcp-debug-spin) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.mcp-debug-drawer-content :deep(.mcp-debug-spin > .as-spin) {
  z-index: 2;
}

.mcp-test-bar {
  @apply mb-3 flex items-center justify-between gap-4 rounded-[9px] border border-[#dbe5f5] bg-[#f7f9fc] px-3.5 py-3;
}

.mcp-test-bar strong, .mcp-test-bar small {
  display: block;
}

.mcp-test-bar strong {
  color: #344054;
  font-size: 13px;
}

.mcp-test-bar small {
  margin-top: 3px;
  color: #667085;
  font-size: 11px;
}

.mcp-test-result {
  margin-bottom: 12px;
}

.mcp-tool-browser {
  @apply grid overflow-hidden rounded-[10px] border border-[#e4e7ec];
  height: min(620px, calc(100vh - 250px));
  min-height: 420px;
  grid-template-columns:245px minmax(0, 1fr);
}

.mcp-debug-drawer-content .mcp-tool-browser {
  height: 100%;
  min-height: 0;
  flex: 1;
}

.mcp-tool-list {
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  border-right: 1px solid #e4e7ec;
  background: #f9fafb;
}

.mcp-tool-list-head {
  @apply flex items-center justify-between px-3.5 py-[13px] text-xs text-[#344054];
}

.mcp-tool-list-head span {
  min-width: 22px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e4e7ec;
  text-align: center;
}

.mcp-tool-list button {
  @apply flex w-full cursor-pointer items-center gap-[9px] border-0 border-t border-[#eaecf0] bg-transparent px-3 py-2.5 text-left text-[#344054];
}

.mcp-tool-list button.active {
  background: #eef4ff;
  color: #175cd3;
}

.mcp-tool-copy {
  min-width: 0;
}

.mcp-tool-list b, .mcp-tool-list small {
  @apply block truncate;
}

.mcp-tool-list b {
  font-size: 12px;
}

.mcp-tool-list small {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 10px;
}

.mcp-tool-list p {
  display: -webkit-box;
  margin-top: 5px;
  overflow: hidden;
  color: #667085;
  font-size: 10px;
  line-height: 15px;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mcp-tool-glyph {
  @apply grid size-[27px] shrink-0 place-items-center rounded-[7px] bg-white text-[#155eef];
}

.mcp-tool-detail {
  min-width: 0;
  min-height: 0;
  padding: 20px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.mcp-tool-heading h3 {
  margin: 0;
  font-size: 17px;
}

.mcp-tool-heading code {
  color: #98a2b3;
  font-size: 11px;
}

.mcp-tool-description {
  margin: 12px 0 18px;
  color: #475467;
  line-height: 1.65;
}

.mcp-section-title {
  margin-bottom: 8px;
}

.mcp-section-title span {
  color: #98a2b3;
  font-size: 11px;
}

.mcp-parameter-table-wrap {
  @apply overflow-auto rounded-lg border border-[#eaecf0];
}

.mcp-parameter-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.mcp-parameter-table th {
  padding: 8px 10px;
  background: #f9fafb;
  color: #667085;
  text-align: left;
  font-weight: 500;
}

.mcp-parameter-table td {
  padding: 9px 10px;
  border-top: 1px solid #eaecf0;
  color: #475467;
  vertical-align: top;
}

.mcp-parameter-table code {
  color: #175cd3;
  font-weight: 600;
}

.mcp-invoke-form {
  @apply mt-[18px] border-t border-[#eaecf0] pt-4;
}

.mcp-invoke-action {
  justify-content: flex-end;
  margin-top: -8px;
}

.mcp-output {
  max-height: 230px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 8px;
  background: #101828;
  color: #d0d5dd;
  white-space: pre-wrap;
}

.mcp-tools-empty {
  min-height: 360px;
}

@media (max-width: 1280px) {
  .mcp-list {
    grid-template-columns:repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .mcp-list {
    grid-template-columns:repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .mcp-page-header, .mcp-test-bar {
    @apply flex-col items-start;
  }

  .mcp-list {
    grid-template-columns:repeat(2, minmax(0, 1fr));
  }

  .mcp-tool-browser {
    height: min(680px, calc(100vh - 210px));
    min-height: 480px;
    grid-template-columns:1fr;
    grid-template-rows:minmax(150px, 34%) minmax(0, 1fr);
  }

  .mcp-tool-list {
    max-height: none;
    border-right: 0;
    border-bottom: 1px solid #e4e7ec;
  }
}

@media (max-width: 520px) {
  .mcp-list {
    grid-template-columns:1fr;
  }
}
</style>
