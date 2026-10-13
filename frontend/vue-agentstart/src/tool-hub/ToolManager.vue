<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {DeleteOutlined, EyeOutlined, PlayCircleOutlined, PlusOutlined} from '@ant-design/icons-vue';
import {Alert, Button, Empty, Form, FormItem, Input, Card, Modal, Select, Spin, Switch, Tag, Textarea, message} from '../ui';
import {createAgentStartClient, type SaveCustomToolRequest, type ToolCatalogItem} from '../client';
import {useAgentStartClient} from '../client/vue';
import {mergeAgentStartHeaders, useAgentStartConfig} from '../config';

type ToolTab = 'all' | 'custom' | 'system';
const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(global.headers),
});
const tools = ref<ToolCatalogItem[]>([]);
const loading = ref(false);
const error = ref('');
const tab = ref<ToolTab>('all');
const search = ref('');
const editing = ref(false);
const saving = ref(false);
const running = ref(false);
const runTool = ref<ToolCatalogItem>();
const detailTool = ref<ToolCatalogItem>();
const argsText = ref('{}');
const output = ref('');
const headersText = ref('{}');
const form = ref<SaveCustomToolRequest>(emptyForm());

interface ToolParameter {
  name: string;
  type: string;
  required: boolean;
  description: string;
  constraint: string;
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
    const constraints: string[] = [];
    if (Array.isArray(field.enum)) constraints.push(`可选：${field.enum.join(' / ')}`);
    if (field.default !== undefined) constraints.push(`默认：${JSON.stringify(field.default)}`);
    if (field.format) constraints.push(`格式：${field.format}`);
    if (field.minimum !== undefined) constraints.push(`最小：${field.minimum}`);
    if (field.maximum !== undefined) constraints.push(`最大：${field.maximum}`);
    return {
      name,
      type: schemaType(field),
      required: required.has(name),
      description: field.description || field.title || '暂无说明',
      constraint: constraints.join('；') || '—'
    };
  });
}

function openDetail(tool: ToolCatalogItem) {
  detailTool.value = tool;
}

function emptyForm(): SaveCustomToolRequest {
  return {
    name: '',
    description: '',
    method: 'POST',
    url: '',
    inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": true\n}',
    headers: {},
    enabled: true
  };
}

const counts = computed(() => ({
  all: tools.value.length,
  system: tools.value.filter(item => !item.custom).length,
  custom: tools.value.filter(item => item.custom).length,
}));
const visibleTools = computed(() => {
  const query = search.value.trim().toLowerCase();
  return tools.value.filter(item => {
    if (tab.value === 'custom' && !item.custom) return false;
    if (tab.value === 'system' && item.custom) return false;
    return !query || item.name.toLowerCase().includes(query) || (item.description ?? '').toLowerCase().includes(query);
  });
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    tools.value = await client.tools.list();
  } catch (exception: any) {
    error.value = exception?.message ?? '工具列表加载失败';
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  form.value = emptyForm();
  headersText.value = '{}';
  editing.value = true;
}

async function save() {
  if (!form.value.name.trim() || !form.value.description.trim() || !form.value.url.trim()) {
    return void message.warning('请填写名称、说明和请求地址');
  }
  let headers: Record<string, string>;
  try {
    JSON.parse(form.value.inputSchema);
    headers = JSON.parse(headersText.value || '{}');
    if (!headers || Array.isArray(headers) || typeof headers !== 'object') throw new Error();
  } catch {
    return void message.error('请求头和输入 Schema 必须是合法 JSON');
  }
  saving.value = true;
  try {
    await client.tools.saveCustom({...form.value, name: form.value.name.trim(), url: form.value.url.trim(), headers});
    editing.value = false;
    message.success('自定义工具已创建');
    await load();
  } catch (exception: any) {
    message.error(exception?.message ?? '创建失败');
  } finally {
    saving.value = false;
  }
}

async function remove(tool: ToolCatalogItem) {
  if (!window.confirm(`确定删除“${tool.name}”吗？`)) return;
  try {
    await client.tools.deleteCustom(tool.name);
    message.success('工具已删除');
    await load();
  } catch (exception: any) {
    message.error(exception?.message ?? '删除失败');
  }
}

function openRun(tool: ToolCatalogItem) {
  runTool.value = tool;
  argsText.value = '{}';
  output.value = '';
}

async function run() {
  if (!runTool.value) return;
  let args: Record<string, unknown>;
  try {
    args = JSON.parse(argsText.value || '{}');
  } catch {
    return void message.error('参数必须是合法 JSON');
  }
  running.value = true;
  output.value = '';
  try {
    const result = await client.tools.invoke(runTool.value.name, args);
    output.value = typeof result === 'string' ? result : JSON.stringify(result, null, 2);
  } catch (exception: any) {
    output.value = `调用失败：${exception?.message ?? exception}`;
  } finally {
    running.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="tool-manager as-management">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px">⌘</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">工具管理</div>
          <div class="as-page-subtitle">
            系统工具 · 自定义工具 · Agent 能力扩展
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <Input v-model:value="search" class="as-management-search" allow-clear placeholder="搜索名称或说明"/>
        <Button type="primary" @click="openCreate">
          <PlusOutlined/>
          创建工具
        </Button>
      </div>
    </div>

    <Alert v-if="error" class="as-management-error" type="error" show-icon :message="error"/>
    <div class="tool-toolbar as-management-toolbar">
      <nav class="tool-tabs as-management-segments">
        <button v-for="item in ([['all','全部'],['system','系统'],['custom','自定义']] as const)" :key="item[0]"
                :class="{ active: tab === item[0] }" @click="tab = item[0]">{{ item[1] }} <span>{{
            counts[item[0]]
          }}</span></button>
      </nav>
    </div>
    <Spin :spinning="loading">
      <div v-if="!loading && !visibleTools.length" class="as-management-empty">
        <Empty description="暂无符合条件的工具"/>
      </div>
      <div v-else class="tool-grid as-management-grid">
        <Card variant="management" v-for="tool in visibleTools" :key="tool.name" class="tool-card"
                 :title="tool.label || tool.name" :subtitle="tool.name"
                 :description="tool.description || '暂无说明'"
                 @click="openDetail(tool)" @keydown.enter="openDetail(tool)">
          <template #icon>⌘</template>
          <template #badge>
            <Tag :color="tool.custom ? 'blue' : 'green'">{{ tool.custom ? '自定义' : '系统' }}</Tag>
          </template>
          <template #meta>
            <div class="tool-meta">
              <Tag>{{ parametersOf(tool).length }} 个参数</Tag>
            </div>
          </template>
          <template #actions>
            <div class="tool-card-actions">
              <Button size="small" @click.stop="openDetail(tool)">
                <EyeOutlined/>
                详情
              </Button>
              <Button size="small" @click.stop="openRun(tool)">
                <PlayCircleOutlined/>
                试运行
              </Button>
              <Button v-if="tool.custom" size="small" danger @click.stop="remove(tool)">
                <DeleteOutlined/>
                删除
              </Button>
            </div>
          </template>
        </Card>
      </div>
    </Spin>

    <Modal v-model:open="editing" title="创建 API 工具" :width="680" :confirm-loading="saving" ok-text="创建"
           @ok="save">
      <Form layout="vertical">
        <div class="tool-form-row">
          <FormItem label="工具名称" required><Input v-model:value="form.name" placeholder="query_order"/></FormItem>
          <FormItem label="请求方法" required><Select v-model:value="form.method"
                                                      :options="['GET','POST','PUT','PATCH','DELETE'].map(value => ({ label: value, value }))"/>
          </FormItem>
        </div>
        <FormItem label="工具说明" required><Input v-model:value="form.description"
                                                   placeholder="告诉模型何时、如何使用此工具"/></FormItem>
        <FormItem label="请求地址" required><Input v-model:value="form.url"
                                                   placeholder="https://api.example.com/orders/{orderId}"/></FormItem>
        <FormItem label="输入 JSON Schema" required><Textarea v-model:value="form.inputSchema" :rows="8"/></FormItem>
        <FormItem label="固定请求头（JSON）"><Textarea v-model:value="headersText" :rows="4"
                                                     placeholder='{"Authorization":"Bearer ..."}'/></FormItem>
        <FormItem label="启用">
          <Switch v-model:checked="form.enabled"/>
        </FormItem>
      </Form>
    </Modal>
    <Modal :open="!!detailTool" :width="820" :footer="false" @cancel="detailTool = undefined">
      <template #title>
        <div class="detail-title"><span class="tool-icon">⌘</span>
          <div class="as-modal-heading">
            <div class="as-modal-title">{{ detailTool?.label || detailTool?.name }}</div>
            <code class="as-modal-subtitle">{{ detailTool?.name }}</code>
          </div>
        </div>
      </template>
      <div v-if="detailTool" class="tool-detail">
        <p class="detail-description">{{ detailTool.description || '暂无工具说明' }}</p>
        <dl class="detail-facts">
          <div>
            <dt>函数名</dt>
            <dd><code>{{ detailTool.name }}</code></dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>{{ detailTool.source || (detailTool.custom ? 'custom' : 'system') }}</dd>
          </div>
          <div v-if="detailTool.method">
            <dt>请求方式</dt>
            <dd>
              <Tag color="blue">{{ detailTool.method }}</Tag>
            </dd>
          </div>
          <div v-if="detailTool.url">
            <dt>请求地址</dt>
            <dd><code>{{ detailTool.url }}</code></dd>
          </div>
        </dl>
        <div class="detail-section-title"><strong>请求参数</strong><span>{{
            parametersOf(detailTool).length
          }} 个字段</span></div>
        <div v-if="parametersOf(detailTool).length" class="parameter-table-wrap">
          <table class="parameter-table">
            <thead>
            <tr>
              <th>字段</th>
              <th>类型</th>
              <th>必填</th>
              <th>参数说明</th>
              <th>约束</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="parameter in parametersOf(detailTool)" :key="parameter.name">
              <td><code>{{ parameter.name }}</code></td>
              <td><span class="type-badge">{{ parameter.type }}</span></td>
              <td><span :class="['required-badge', { required: parameter.required }]">{{
                  parameter.required ? '是' : '否'
                }}</span></td>
              <td>{{ parameter.description }}</td>
              <td>{{ parameter.constraint }}</td>
            </tr>
            </tbody>
          </table>
        </div>
        <Empty v-else description="该工具不需要请求参数"/>
        <div class="detail-actions">
          <Button @click="detailTool = undefined">关闭</Button>
          <Button type="primary" @click="openRun(detailTool); detailTool = undefined">
            <PlayCircleOutlined/>
            试运行
          </Button>
        </div>
      </div>
    </Modal>
    <Modal :open="!!runTool" :title="`试运行 · ${runTool?.name ?? ''}`" :width="700" :confirm-loading="running"
           ok-text="运行" @ok="run" @cancel="runTool = undefined">
      <Form layout="vertical">
        <FormItem label="参数 JSON"><Textarea v-model:value="argsText" :rows="8"/></FormItem>
        <FormItem v-if="output" label="输出">
          <pre class="tool-output">{{ output }}</pre>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
@reference "tailwindcss/theme.css";

.tool-manager {
  color: var(--as-mgmt-text)
}

.tool-toolbar, .tool-toolbar-actions, .tool-card-title, .tool-card footer, .tool-meta, .tool-card-actions, .tool-form-row, .detail-title, .detail-actions {
  @apply flex items-center;
}

.tool-toolbar {
  @apply justify-between gap-3;
}

.tool-toolbar-actions {
  @apply gap-2;
  width: min(430px, 50%);
}

.tool-toolbar-actions > :first-child {
  @apply min-w-0 flex-1;
}

.tool-toolbar-actions .as-btn {
  @apply shrink-0;
}

.tool-tabs span {
  @apply ml-[3px] text-[10px] text-[#98a2b3];
}

.tool-grid {
  @apply gap-2.5;
  grid-template-columns:repeat(auto-fill, minmax(290px, 1fr));
}

.tool-card {
  @apply min-h-[164px] cursor-pointer overflow-hidden p-3.5;
}

.tool-card:hover, .tool-card:focus-visible {
  @apply outline-none;
}

.tool-card-title {
  @apply min-w-0 gap-[9px];
}

.tool-card-title > div:last-child {
  @apply min-w-0;
}

.tool-icon {
  @apply grid size-8 shrink-0 place-items-center rounded-lg bg-[#eef4ff] text-[17px] text-[#155eef];
}

.tool-card h3 {
  @apply m-0 truncate text-sm leading-5;
}

.tool-card code {
  @apply block max-w-full truncate text-[10px] text-[#98a2b3];
}

.tool-card > p {
  display: -webkit-box;
  min-height: 36px;
  margin: 8px 0;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2
}

.tool-meta, .tool-card-actions {
  @apply gap-1;
}

.tool-card :deep(.as-management-card__badge .as-tag) {
  margin: 0;
}

.tool-meta .as-tag {
  margin: 0;
  padding: 0 5px;
  font-size: 10px;
  line-height: 18px
}

.tool-card-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
}

.tool-card-actions :deep(.as-btn) {
  gap: 4px
}

.tool-form-row {
  @apply items-start gap-4;
}

.tool-form-row > *:first-child {
  flex: 1
}

.tool-form-row > *:last-child {
  width: 150px
}

.detail-title {
  @apply gap-2.5;
}

.detail-description {
  margin: 0 0 16px;
  color: #475467;
  line-height: 1.7
}

.detail-facts {
  @apply mb-5 grid gap-x-6 gap-y-3 rounded-[10px] border border-[#eaecf0] bg-[#f9fafb] px-4 py-3;
  grid-template-columns:repeat(2, minmax(0, 1fr));
}

.detail-facts div {
  min-width: 0
}

.detail-facts dt {
  margin-bottom: 3px;
  color: #98a2b3;
  font-size: 11px
}

.detail-facts dd {
  overflow-wrap: anywhere;
  margin: 0;
  color: #344054;
  font-size: 13px
}

.detail-facts code {
  font-size: 12px
}

.detail-section-title {
  @apply mb-2.5 flex items-center justify-between;
}

.detail-section-title span {
  color: #98a2b3;
  font-size: 12px
}

.parameter-table-wrap {
  @apply overflow-auto rounded-[10px] border border-[#eaecf0];
}

.parameter-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px
}

.parameter-table th {
  padding: 9px 10px;
  background: #f9fafb;
  color: #667085;
  text-align: left;
  font-weight: 500;
  white-space: nowrap
}

.parameter-table td {
  padding: 10px;
  border-top: 1px solid #eaecf0;
  color: #475467;
  vertical-align: top
}

.parameter-table td:first-child code {
  color: #175cd3;
  font-size: 12px;
  font-weight: 600
}

.type-badge, .required-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 5px;
  background: #f2f4f7;
  color: #475467;
  font-family: monospace;
  font-size: 10px;
  white-space: nowrap
}

.required-badge.required {
  background: #fff1f0;
  color: #d92d20
}

.detail-actions {
  @apply mt-[18px] justify-end gap-2;
}

.tool-output {
  max-height: 280px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 8px;
  background: #101828;
  color: #d0d5dd;
  white-space: pre-wrap
}

@media (max-width: 760px) {
  .tool-toolbar {
    @apply flex-col items-stretch;
  }

  .tool-toolbar-actions {
    @apply w-full;
  }

  .tool-grid {
    grid-template-columns:1fr
  }

  .tool-form-row {
    @apply block;
  }

  .tool-form-row > *:last-child {
    width: auto
  }

  .detail-facts {
    grid-template-columns:1fr
  }

  .parameter-table {
    min-width: 680px
  }
}

</style>
