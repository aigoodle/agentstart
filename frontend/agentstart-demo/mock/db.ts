/**
 * 内存 mock 数据库 —— 为 vue-agent-start 全部组件提供演示数据。
 * 所有 mutation 端点会真实修改内存数据，保证演示交互“活”的。
 * 字段形状严格对齐组件库 client 层的 wire 类型（见 README 与 src/client/*）。
 */

const now = Date.now()
const iso = (offsetMs = 0) => new Date(now + offsetMs).toISOString()
const MIN = 60_000
const HOUR = 3_600_000
const DAY = 86_400_000

let seq = 1000
export function uid(prefix = 'id'): string {
  seq += 1
  return `${prefix}-${seq.toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

// ---------------------------------------------------------------------------
// 模型供应商 & 模型
// ---------------------------------------------------------------------------
const credentialSchema = (keyLabel = 'API Key') => [
  {
    name: 'apiKey',
    label: keyLabel,
    type: 'string',
    required: true,
    secret: true,
    placeholder: 'sk-…',
  },
]

export function createDb() {
  const providers: any[] = [
    {
      name: 'openai',
      label: 'OpenAI',
      description: 'GPT 系列大模型与 Embedding',
      supportedModelTypes: ['LLM', 'TEXT_EMBEDDING', 'IMAGE', 'SPEECH2TEXT', 'TTS'],
      credentialSchema: credentialSchema(),
      predefinedModels: [
        { model: 'gpt-4o', label: 'GPT-4o', modelType: 'LLM', contextLength: 128000, features: ['vision', 'function_calling'] },
        { model: 'gpt-4o-mini', label: 'GPT-4o mini', modelType: 'LLM', contextLength: 128000, features: ['function_calling'] },
        { model: 'text-embedding-3-small', label: 'Embedding 3 Small', modelType: 'TEXT_EMBEDDING', dimensions: 1536 },
        { model: 'dall-e-3', label: 'DALL·E 3', modelType: 'IMAGE' },
      ],
      supportsRemoteModelListing: true,
      credentialConfigured: true,
      credentialId: 'cred-openai',
      credentialMasked: 'sk-proj-****Qx7f',
      installedModelCount: 3,
      enabledModelCount: 3,
    },
    {
      name: 'anthropic',
      label: 'Anthropic',
      description: 'Claude 系列大模型',
      supportedModelTypes: ['LLM'],
      credentialSchema: credentialSchema(),
      predefinedModels: [
        { model: 'claude-sonnet-4-5', label: 'Claude Sonnet 4.5', modelType: 'LLM', contextLength: 200000, features: ['vision', 'function_calling'] },
        { model: 'claude-haiku-4-5', label: 'Claude Haiku 4.5', modelType: 'LLM', contextLength: 200000 },
      ],
      supportsRemoteModelListing: true,
      credentialConfigured: true,
      credentialId: 'cred-anthropic',
      credentialMasked: 'sk-ant-****9b2e',
      installedModelCount: 1,
      enabledModelCount: 1,
    },
    {
      name: 'dashscope',
      label: '阿里云百炼',
      description: '通义千问系列模型',
      supportedModelTypes: ['LLM', 'TEXT_EMBEDDING', 'RERANK', 'IMAGE'],
      credentialSchema: credentialSchema('DashScope API Key'),
      predefinedModels: [
        { model: 'qwen-max', label: '通义千问 Max', modelType: 'LLM', contextLength: 32768, features: ['function_calling'] },
        { model: 'qwen-plus', label: '通义千问 Plus', modelType: 'LLM', contextLength: 131072 },
        { model: 'text-embedding-v3', label: '通义 Embedding v3', modelType: 'TEXT_EMBEDDING', dimensions: 1024 },
        { model: 'gte-rerank', label: 'GTE Rerank', modelType: 'RERANK' },
      ],
      supportsRemoteModelListing: true,
      credentialConfigured: true,
      credentialId: 'cred-dashscope',
      credentialMasked: 'sk-ds-****3a91',
      installedModelCount: 2,
      enabledModelCount: 2,
    },
    {
      name: 'ollama',
      label: 'Ollama',
      description: '本地开源模型运行时',
      supportedModelTypes: ['LLM', 'TEXT_EMBEDDING'],
      credentialSchema: [],
      predefinedModels: [
        { model: 'qwen2.5:14b', label: 'Qwen2.5 14B', modelType: 'LLM', contextLength: 32768 },
        { model: 'nomic-embed-text', label: 'Nomic Embed', modelType: 'TEXT_EMBEDDING', dimensions: 768 },
      ],
      supportsRemoteModelListing: true,
      credentialConfigured: false,
      installedModelCount: 0,
      enabledModelCount: 0,
    },
    {
      name: 'deepseek',
      label: 'DeepSeek',
      description: 'DeepSeek Chat / Reasoner',
      supportedModelTypes: ['LLM'],
      credentialSchema: credentialSchema(),
      predefinedModels: [
        { model: 'deepseek-chat', label: 'DeepSeek Chat', modelType: 'LLM', contextLength: 65536 },
        { model: 'deepseek-reasoner', label: 'DeepSeek Reasoner', modelType: 'LLM', contextLength: 65536 },
      ],
      supportsRemoteModelListing: true,
      credentialConfigured: false,
      installedModelCount: 0,
      enabledModelCount: 0,
    },
    {
      name: 'siliconflow',
      label: '硅基流动',
      description: '开源模型云端推理',
      supportedModelTypes: ['LLM', 'TEXT_EMBEDDING', 'RERANK'],
      credentialSchema: credentialSchema(),
      predefinedModels: [
        { model: 'Qwen/Qwen2.5-72B-Instruct', label: 'Qwen2.5 72B', modelType: 'LLM', contextLength: 32768 },
        { model: 'BAA1/bge-m3', label: 'BGE-M3', modelType: 'TEXT_EMBEDDING', dimensions: 1024 },
      ],
      supportsRemoteModelListing: false,
      credentialConfigured: false,
      installedModelCount: 0,
      enabledModelCount: 0,
    },
  ]

  const models: any[] = [
    { id: 'm-1', tenantId: 'default', providerName: 'openai', modelName: 'gpt-4o', modelType: 'LLM', enabled: true, isDefault: true, credentialId: 'cred-openai', createdAt: iso(-30 * DAY), updatedAt: iso(-2 * DAY) },
    { id: 'm-2', tenantId: 'default', providerName: 'openai', modelName: 'gpt-4o-mini', modelType: 'LLM', enabled: true, isDefault: false, credentialId: 'cred-openai', createdAt: iso(-30 * DAY), updatedAt: iso(-9 * DAY) },
    { id: 'm-3', tenantId: 'default', providerName: 'openai', modelName: 'text-embedding-3-small', modelType: 'TEXT_EMBEDDING', enabled: true, isDefault: true, credentialId: 'cred-openai', createdAt: iso(-28 * DAY), updatedAt: iso(-28 * DAY) },
    { id: 'm-4', tenantId: 'default', providerName: 'anthropic', modelName: 'claude-sonnet-4-5', modelType: 'LLM', enabled: true, isDefault: false, credentialId: 'cred-anthropic', createdAt: iso(-15 * DAY), updatedAt: iso(-1 * DAY) },
    { id: 'm-5', tenantId: 'default', providerName: 'dashscope', modelName: 'qwen-max', modelType: 'LLM', enabled: true, isDefault: false, credentialId: 'cred-dashscope', createdAt: iso(-20 * DAY), updatedAt: iso(-5 * DAY) },
    { id: 'm-6', tenantId: 'default', providerName: 'dashscope', modelName: 'text-embedding-v3', modelType: 'TEXT_EMBEDDING', enabled: true, isDefault: false, credentialId: 'cred-dashscope', createdAt: iso(-20 * DAY), updatedAt: iso(-20 * DAY) },
  ]

  // -------------------------------------------------------------------------
  // 知识库
  // -------------------------------------------------------------------------
  const datasets: any[] = [
    {
      id: 'ds-1', name: '产品知识库', description: '智能终端产品线手册、规格与常见问题', tenantId: 'default',
      documentCount: 4, segmentCount: 12, indexingTechnique: 'high_quality', updatedAt: iso(-2 * HOUR),
      processRuleJson: JSON.stringify({ segmentation: { separator: '\n\n', maxLength: 800 } }),
    },
    {
      id: 'ds-2', name: '客服 FAQ 库', description: '售后政策、退换货流程与话术规范', tenantId: 'default',
      documentCount: 3, segmentCount: 9, indexingTechnique: 'high_quality', updatedAt: iso(-1 * DAY),
      processRuleJson: JSON.stringify({ segmentation: { separator: '\n\n', maxLength: 500 } }),
    },
    {
      id: 'ds-3', name: '研发规范文档库', description: '编码规范、评审流程与上线检查单', tenantId: 'default',
      documentCount: 2, segmentCount: 6, indexingTechnique: 'economy', updatedAt: iso(-6 * DAY),
      processRuleJson: JSON.stringify({ segmentation: { separator: '\n', maxLength: 1000 } }),
    },
  ]

  const documents: Record<string, any[]> = {
    'ds-1': [
      { id: 'doc-11', name: 'X1-Pro 用户手册.pdf', sourceType: 'upload', wordCount: 12840, createdAt: iso(-9 * DAY), updatedAt: iso(-9 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'application/pdf', pageCount: 42, fileSize: 3_245_678 },
      { id: 'doc-12', name: '产品规格对照表.xlsx', sourceType: 'upload', wordCount: 2310, createdAt: iso(-8 * DAY), updatedAt: iso(-8 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'application/vnd.ms-excel', fileSize: 245_760 },
      { id: 'doc-13', name: '固件升级指引.md', sourceType: 'upload', wordCount: 1893, createdAt: iso(-3 * DAY), updatedAt: iso(-3 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'text/markdown', fileSize: 18_944 },
      { id: 'doc-14', name: '新品发布材料.docx', sourceType: 'upload', wordCount: 5600, createdAt: iso(-30 * MIN), updatedAt: iso(-28 * MIN), status: 'INDEXING', enabled: true, parserName: 'default', mediaType: 'application/msword', fileSize: 890_112 },
    ],
    'ds-2': [
      { id: 'doc-21', name: '退换货政策 2026 版.md', sourceType: 'upload', wordCount: 3400, createdAt: iso(-20 * DAY), updatedAt: iso(-20 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'text/markdown', fileSize: 27_600 },
      { id: 'doc-22', name: '客服话术手册.pdf', sourceType: 'upload', wordCount: 8900, createdAt: iso(-12 * DAY), updatedAt: iso(-12 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'application/pdf', pageCount: 26, fileSize: 1_540_000 },
      { id: 'doc-23', name: '工单升级流程.md', sourceType: 'upload', wordCount: 1200, createdAt: iso(-4 * DAY), updatedAt: iso(-4 * DAY), status: 'FAILED', enabled: false, parserName: 'default', mediaType: 'text/markdown', fileSize: 9_800, parseWarningsJson: '["编码异常：文件尾部截断"]' },
    ],
    'ds-3': [
      { id: 'doc-31', name: '前端编码规范.md', sourceType: 'upload', wordCount: 6700, createdAt: iso(-40 * DAY), updatedAt: iso(-40 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'text/markdown', fileSize: 51_200 },
      { id: 'doc-32', name: '上线检查单.md', sourceType: 'upload', wordCount: 980, createdAt: iso(-18 * DAY), updatedAt: iso(-6 * DAY), status: 'COMPLETED', enabled: true, parserName: 'default', mediaType: 'text/markdown', fileSize: 8_100 },
    ],
  }

  const segTexts = [
    '设备首次激活时，请保持电量高于 40%，并连接 2.4GHz Wi-Fi 网络完成配网。',
    '如设备指示灯呈红色常亮，表示固件校验失败，请长按重置键 8 秒后重新升级。',
    '产品自签收之日起 7 天内支持无理由退货，15 天内出现性能故障可换货。',
    '退换货时请保留原包装与全部配件，定制刻字商品不适用无理由退货政策。',
    '工单响应 SLA：P1 级 15 分钟内响应，P2 级 2 小时内响应，超时自动升级至值班经理。',
    '代码提交前必须通过 lint 与单元测试，合并请求至少需要一名 Reviewer 批准。',
    '生产环境发布统一走灰度通道，先放量 5% 观察 30 分钟，无告警后全量。',
    '向量检索默认 topK=5，相似度阈值 0.62，可结合 rerank 模型提升召回精度。',
    '知识库文档更新后，系统会在 5 分钟内完成增量索引重建，无需手动触发。',
    '客服机器人连续两次未命中知识点时将自动转人工，并附带会话摘要。',
    '接口鉴权采用 Bearer Token，令牌有效期 24 小时，过期后需重新申请。',
    '敏感操作（删除、清空）需要二次确认，且全部写入审计日志留痕 180 天。',
  ]

  const segments: Record<string, any[]> = {}
  for (const ds of datasets) {
    const docs = documents[ds.id] ?? []
    const list: any[] = []
    docs.forEach((doc, di) => {
      const n = Math.max(2, Math.min(4, Math.round((ds.segmentCount ?? 6) / docs.length)))
      for (let i = 0; i < n; i++) {
        list.push({
          id: `${doc.id}-seg-${i + 1}`,
          documentId: doc.id,
          position: list.length + 1,
          content: segTexts[(di * 3 + i) % segTexts.length] + `（来源：${doc.name} 第 ${i + 1} 节）`,
          tokenCount: 60 + ((di * 17 + i * 23) % 140),
          enabled: doc.enabled !== false,
          keywords: ['规范', '流程'].slice(0, (di + i) % 3),
        })
      }
    })
    segments[ds.id] = list
  }

  const hitHistory: Record<string, any[]> = {
    'ds-1': [
      { id: uid('hit'), query: '设备红灯常亮怎么处理', method: 'hybrid', hitCount: 3, createdAt: iso(-2 * HOUR) },
      { id: uid('hit'), query: '首次配网要求', method: 'vector', hitCount: 2, createdAt: iso(-1 * DAY) },
    ],
  }

  const indexVersions: Record<string, any[]> = {
    'ds-1': [
      { id: 'iv-1', version: 3, status: 'ACTIVE', embeddingModelVersion: 'text-embedding-3-small@v1', chunkingRuleVersion: 'r-800', contentChecksum: 'a91f3c', documentCount: 4, segmentCount: 12, createdAt: iso(-2 * HOUR) },
      { id: 'iv-2', version: 2, status: 'RETIRED', embeddingModelVersion: 'text-embedding-3-small@v1', chunkingRuleVersion: 'r-500', contentChecksum: '77be01', documentCount: 3, segmentCount: 9, createdAt: iso(-8 * DAY) },
    ],
  }

  // -------------------------------------------------------------------------
  // 智能体应用
  // -------------------------------------------------------------------------
  const agents: any[] = [
    {
      id: 'app-1', tenantId: 'default', appCode: 'cs-assistant', visibility: 'GLOBAL',
      name: '智能客服助手', description: '面向终端用户的售后咨询机器人，接入产品与 FAQ 知识库',
      icon: '🎧', iconBackground: '#0ea5e9', mode: 'chat',
      instructions: '你是售后客服助手。回答要简洁、专业，优先引用知识库内容；无法确定时引导用户转人工。',
      openingStatement: '您好，我是智能客服助手，请问有什么可以帮您？',
      suggestedQuestionsJson: JSON.stringify(['如何申请退货？', '设备红灯常亮怎么办？', '固件如何升级？']),
      datasetIdsJson: JSON.stringify(['ds-1', 'ds-2']),
      retrievalConfigJson: JSON.stringify({ method: 'hybrid', topK: 5, scoreThreshold: 0.62, rerankEnabled: false }),
      modelName: 'gpt-4o', modelProvider: 'openai',
      modelSettingsJson: JSON.stringify({ temperature: 0.3, maxTokens: 2048 }),
      memoryEnabled: true, memoryWindow: 12, published: true,
      createdAt: iso(-25 * DAY), updatedAt: iso(-3 * HOUR),
    },
    {
      id: 'app-2', tenantId: 'default', appCode: 'data-agent', visibility: 'GLOBAL',
      name: '数据分析 Agent', description: '自然语言查询业务库并自动生成图表',
      icon: '📊', iconBackground: '#8b5cf6', mode: 'agent',
      instructions: '你是数据分析助手。将用户问题转成 SQL 查询，执行后总结结论并给出可视化建议。',
      modelName: 'claude-sonnet-4-5', modelProvider: 'anthropic',
      strategy: 'FUNCTION_CALLING', toolNamesJson: JSON.stringify(['sql_query', 'chart_render']),
      modelSettingsJson: JSON.stringify({ temperature: 0.1 }),
      maxIterations: 8, memoryEnabled: true, memoryWindow: 6, published: true,
      createdAt: iso(-14 * DAY), updatedAt: iso(-2 * DAY),
    },
    {
      id: 'app-3', tenantId: 'default', appCode: 'contract-flow', visibility: 'TENANT_LIST',
      name: '合同审查工作流', description: '上传合同 → 条款抽取 → 风险审查 → 生成意见书',
      icon: '📑', iconBackground: '#f59e0b', mode: 'workflow',
      workflowId: 'wf-app3', modelName: 'qwen-max', modelProvider: 'dashscope',
      published: false, createdAt: iso(-7 * DAY), updatedAt: iso(-20 * HOUR),
    },
    {
      id: 'app-4', tenantId: 'default', appCode: 'code-review', visibility: 'PRIVATE',
      name: '代码评审 Chatflow', description: '按 diff 输出评审意见，检查安全与性能隐患',
      icon: '🧑‍💻', iconBackground: '#10b981', mode: 'chatflow',
      workflowId: 'wf-app4', modelName: 'gpt-4o-mini', modelProvider: 'openai',
      published: true, createdAt: iso(-4 * DAY), updatedAt: iso(-4 * DAY),
    },
  ]

  const agentVersions: Record<string, any[]> = {
    'app-1': [
      { id: 'v-101', tenantId: 'default', appId: 'app-1', versionNumber: 3, status: 'ACTIVE', changeSummary: '更新退货政策话术', publishedBy: 'demo', publishedAt: iso(-3 * HOUR), runtimeType: 'NATIVE' },
      { id: 'v-100', tenantId: 'default', appId: 'app-1', versionNumber: 2, status: 'SUPERSEDED', changeSummary: '接入 FAQ 知识库', publishedBy: 'demo', publishedAt: iso(-9 * DAY), runtimeType: 'NATIVE' },
    ],
  }

  const annotations: Record<string, any[]> = {
    'app-1': [
      { id: 'an-1', appId: 'app-1', question: '退货运费谁承担？', content: '7 天无理由退货运费由买家承担；质量问题由商家承担。', enabled: true, hitCount: 14, createdAt: iso(-6 * DAY), updatedAt: iso(-6 * DAY) },
    ],
  }

  const apiTokens: Record<string, any[]> = {
    'app-1': [
      { id: 'tk-1', appId: 'app-1', name: '生产环境', type: 'APP', token: 'as-prod-****-****-9f2c', createdAt: iso(-20 * DAY), lastUsedAt: iso(-40 * MIN) },
      { id: 'tk-2', appId: 'app-1', name: '测试环境', type: 'APP', token: 'as-test-****-****-1a7d', createdAt: iso(-10 * DAY), lastUsedAt: iso(-2 * DAY) },
    ],
  }

  // -------------------------------------------------------------------------
  // 工作流
  // -------------------------------------------------------------------------
  const workflows: any[] = [
    {
      id: 'wf-app3', appId: 'app-3', name: '合同审查工作流', mode: 'WORKFLOW',
      graph: {
        nodes: [
          { id: 'n-start', type: 'custom', position: { x: 40, y: 160 }, data: { type: 'START', label: '开始', config: {} } },
          { id: 'n-llm', type: 'custom', position: { x: 340, y: 160 }, data: { type: 'LLM', label: '条款风险审查', config: { prompt: '审查以下合同条款中的法律风险…' } } },
          { id: 'n-end', type: 'custom', position: { x: 660, y: 160 }, data: { type: 'ANSWER', label: '输出意见书', config: {} } },
        ],
        edges: [
          { id: 'e-1', source: 'n-start', target: 'n-llm' },
          { id: 'e-2', source: 'n-llm', target: 'n-end' },
        ],
      },
      version: 4, published: false, createdAt: iso(-7 * DAY), updatedAt: iso(-20 * HOUR),
    },
  ]

  // -------------------------------------------------------------------------
  // 工具 / 技能 / MCP / 触发器
  // -------------------------------------------------------------------------
  const tools: any[] = [
    { name: 'sql_query', label: 'SQL 查询', description: '对业务只读库执行 SQL 并返回结果集', inputSchema: JSON.stringify({ type: 'object', properties: { sql: { type: 'string', description: 'SELECT 语句' }, database: { type: 'string', enum: ['biz_ro', 'dw_ro'], description: '目标库' } }, required: ['sql'] }), source: 'BUILTIN', category: '数据', enabled: true },
    { name: 'chart_render', label: '图表渲染', description: '根据数据生成折线/柱状/饼图', inputSchema: JSON.stringify({ type: 'object', properties: { chartType: { type: 'string', enum: ['line', 'bar', 'pie'] }, data: { type: 'array', items: { type: 'object' } } }, required: ['chartType', 'data'] }), source: 'BUILTIN', category: '可视化', enabled: true },
    { name: 'web_search', label: '联网搜索', description: '搜索互联网实时信息', inputSchema: JSON.stringify({ type: 'object', properties: { query: { type: 'string' }, topK: { type: 'integer', minimum: 1, maximum: 10 } }, required: ['query'] }), source: 'BUILTIN', category: '检索', enabled: true },
    { name: 'http_request', label: 'HTTP 请求', description: '调用任意 REST 接口', inputSchema: JSON.stringify({ type: 'object', properties: { url: { type: 'string' }, method: { type: 'string', enum: ['GET', 'POST', 'PUT', 'DELETE'] }, body: { type: 'string' } }, required: ['url', 'method'] }), source: 'BUILTIN', category: '集成', enabled: false },
    { name: 'amap_geocode', label: '高德地理编码', description: '地址与经纬度互转', inputSchema: JSON.stringify({ type: 'object', properties: { address: { type: 'string', description: '结构化地址' }, city: { type: 'string' } }, required: ['address'] }), source: 'MCP', category: '地图', provider: 'amap-maps', mcpServerId: 'mcp-1', enabled: true },
    { name: 'weather_query', label: '天气查询', description: '查询城市实时天气与未来 3 天预报', inputSchema: JSON.stringify({ type: 'object', properties: { city: { type: 'string', description: '城市名' }, days: { type: 'integer', minimum: 1, maximum: 3 } }, required: ['city'] }), source: 'CUSTOM', category: '生活', custom: true, method: 'GET', url: 'https://api.example.com/weather', enabled: true },
  ]

  const skills: any[] = [
    { id: 'sk-1', tenantId: 'default', code: 'after-sales-sop', name: '售后处理 SOP', description: '按标准作业程序处理退换货与投诉', instructions: '1. 确认订单与时间窗口\n2. 判定退货类型\n3. 生成处理方案并安抚用户', status: 'PUBLISHED', toolNamesJson: JSON.stringify(['sql_query']), version: 3, createdAt: iso(-16 * DAY), updatedAt: iso(-2 * DAY) },
    { id: 'sk-2', tenantId: 'default', code: 'ticket-summary', name: '工单摘要', description: '把长会话压缩为结构化工单摘要', instructions: '输出字段：问题分类 / 用户诉求 / 已采取措施 / 待办', status: 'PUBLISHED', toolNamesJson: '[]', version: 1, createdAt: iso(-5 * DAY), updatedAt: iso(-5 * DAY) },
    { id: 'sk-3', tenantId: 'default', code: 'compliance-check', name: '合规检查', description: '检查营销文案中的违禁词与合规风险', instructions: '对照广告法禁用词表逐项检查，输出风险等级与修改建议', status: 'DRAFT', toolNamesJson: '[]', version: 1, createdAt: iso(-1 * DAY), updatedAt: iso(-6 * HOUR) },
  ]

  const mcpServers: any[] = [
    { id: 'mcp-1', name: 'amap-maps', transport: 'HTTP', enabled: true, status: 'CONNECTED', toolCount: 12 },
    { id: 'mcp-2', name: 'filesystem', transport: 'STDIO', enabled: true, status: 'CONNECTED', toolCount: 6 },
    { id: 'mcp-3', name: 'github-mcp', transport: 'HTTP', enabled: false, status: 'ERROR', toolCount: 0, lastError: 'connect ETIMEDOUT api.github.com:443' },
  ]

  const triggers: any[] = [
    { id: 'tg-1', tenantId: 'default', name: '订单支付回调', type: 'WEBHOOK', targetType: 'AGENT', targetId: 'app-1', config: { data: { scene: 'order-paid' } }, configJson: '{}', enabled: true, fireCount: 1284, lastFireAt: iso(-12 * MIN), createdAt: iso(-22 * DAY), updatedAt: iso(-12 * MIN) },
    { id: 'tg-2', tenantId: 'default', name: '每日晨报生成', type: 'CRON', targetType: 'AGENT', targetId: 'app-2', config: { scheduleType: 'CRON', expression: '0 8 * * *', timeZone: 'Asia/Shanghai' }, configJson: '{}', enabled: true, nextFireAt: iso(14 * HOUR), lastFireAt: iso(-10 * HOUR), fireCount: 96, createdAt: iso(-30 * DAY), updatedAt: iso(-10 * HOUR) },
    { id: 'tg-3', tenantId: 'default', name: '运行失败告警', type: 'EVENT', targetType: 'WORKFLOW', targetId: 'wf-app3', config: { data: { topic: 'run.failed' } }, configJson: '{}', enabled: false, fireCount: 7, lastFireAt: iso(-3 * DAY), createdAt: iso(-11 * DAY), updatedAt: iso(-3 * DAY) },
    { id: 'tg-4', tenantId: 'default', name: '企微群消息接入', type: 'CHANNEL_MESSAGE', targetType: 'AGENT', targetId: 'app-1', config: { conversationId: 'conv-wecom-1' }, configJson: '{}', enabled: true, fireCount: 342, lastFireAt: iso(-45 * MIN), createdAt: iso(-8 * DAY), updatedAt: iso(-45 * MIN) },
  ]

  const invocations: Record<string, any[]> = {
    'tg-1': [
      { id: uid('inv'), triggerId: 'tg-1', source: 'WEBHOOK', status: 'COMPLETED', conversationId: 'conv-901', runId: 'run-901', payloadJson: '{"orderId":"SO-20260927-001"}', outputsJson: '{"reply":"已受理"}', createdAt: iso(-12 * MIN), updatedAt: iso(-12 * MIN) },
      { id: uid('inv'), triggerId: 'tg-1', source: 'WEBHOOK', status: 'FAILED', payloadJson: '{"orderId":"SO-20260926-887"}', error: 'agent timeout after 30s', createdAt: iso(-1 * DAY), updatedAt: iso(-1 * DAY) },
    ],
    'tg-2': [
      { id: uid('inv'), triggerId: 'tg-2', source: 'CRON', status: 'COMPLETED', runId: 'run-902', outputsJson: '{"report":"…"}', createdAt: iso(-10 * HOUR), updatedAt: iso(-10 * HOUR) },
    ],
  }

  // -------------------------------------------------------------------------
  // Connector 生态
  // -------------------------------------------------------------------------
  const connectors: any[] = [
    {
      key: { provider: 'feishu', connectorId: 'im' }, name: '飞书消息', description: '发送群消息 / 私聊卡片，获取会话列表', version: '1.4.0', source: 'official', category: '协同办公', trustLevel: 'VERIFIED',
      configurationSchema: JSON.stringify({ type: 'object', properties: { webhook: { type: 'string', title: '群机器人 Webhook' } } }),
      actions: [
        { id: 'send_message', name: '发送消息', description: '向指定会话发送富文本消息', inputSchema: JSON.stringify({ type: 'object', properties: { chatId: { type: 'string' }, content: { type: 'string' } }, required: ['chatId', 'content'] }), riskLevel: 'WRITE', capabilities: ['ACTION', 'CHANNEL_OUTBOUND'] },
        { id: 'list_chats', name: '获取会话列表', inputSchema: JSON.stringify({ type: 'object', properties: { pageSize: { type: 'integer' } } }), riskLevel: 'READ', capabilities: ['ACTION'] },
      ],
    },
    {
      key: { provider: 'github', connectorId: 'repo' }, name: 'GitHub 仓库', description: 'Issue 管理与代码搜索', version: '2.1.3', source: 'official', category: '研发效能', trustLevel: 'VERIFIED',
      actions: [
        { id: 'create_issue', name: '创建 Issue', inputSchema: JSON.stringify({ type: 'object', properties: { repo: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' } }, required: ['repo', 'title'] }), riskLevel: 'WRITE', capabilities: ['ACTION'] },
        { id: 'search_code', name: '代码搜索', inputSchema: JSON.stringify({ type: 'object', properties: { q: { type: 'string' } }, required: ['q'] }), riskLevel: 'READ', capabilities: ['ACTION'] },
      ],
    },
    {
      key: { provider: 'mysql', connectorId: 'query' }, name: 'MySQL 查询', description: '对只读副本执行参数化 SQL', version: '1.0.8', source: 'community', category: '数据', trustLevel: 'COMMUNITY',
      actions: [
        { id: 'execute_query', name: '执行查询', inputSchema: JSON.stringify({ type: 'object', properties: { sql: { type: 'string' }, params: { type: 'array', items: {} } }, required: ['sql'] }), riskLevel: 'READ', capabilities: ['ACTION'] },
      ],
    },
    {
      key: { provider: 'jira', connectorId: 'issue' }, name: 'Jira 工单', description: '创建 / 流转 / 评论工单', version: '3.0.1', source: 'official', category: '项目管理', trustLevel: 'VERIFIED',
      actions: [
        { id: 'create_ticket', name: '创建工单', inputSchema: JSON.stringify({ type: 'object', properties: { project: { type: 'string' }, summary: { type: 'string' }, priority: { type: 'string', enum: ['Low', 'Medium', 'High'] } }, required: ['project', 'summary'] }), riskLevel: 'WRITE', capabilities: ['ACTION'] },
        { id: 'transition', name: '流转工单', inputSchema: JSON.stringify({ type: 'object', properties: { issueKey: { type: 'string' }, to: { type: 'string' } }, required: ['issueKey', 'to'] }), riskLevel: 'WRITE', capabilities: ['ACTION'] },
      ],
    },
    {
      key: { provider: 'dingtalk', connectorId: 'robot' }, name: '钉钉机器人', description: '群机器人推送与工作通知', version: '1.2.0', source: 'official', category: '协同办公', trustLevel: 'VERIFIED',
      actions: [
        { id: 'push_markdown', name: '推送 Markdown', inputSchema: JSON.stringify({ type: 'object', properties: { title: { type: 'string' }, text: { type: 'string' } }, required: ['title', 'text'] }), riskLevel: 'WRITE', capabilities: ['ACTION', 'CHANNEL_OUTBOUND'] },
      ],
    },
    {
      key: { provider: 'smtp', connectorId: 'mail' }, name: '邮件发送', description: '通过 SMTP 发送通知邮件', version: '0.9.4', source: 'community', category: '通知', trustLevel: 'COMMUNITY',
      actions: [
        { id: 'send_mail', name: '发送邮件', inputSchema: JSON.stringify({ type: 'object', properties: { to: { type: 'string' }, subject: { type: 'string' }, body: { type: 'string' } }, required: ['to', 'subject', 'body'] }), riskLevel: 'WRITE', capabilities: ['ACTION'] },
      ],
    },
  ]

  const installations: any[] = [
    { id: 'ins-1', tenantId: 'default', provider: 'feishu', connectorId: 'im', version: '1.4.0', source: 'official', enabled: true, trustLevel: 'VERIFIED' },
    { id: 'ins-2', tenantId: 'default', provider: 'github', connectorId: 'repo', version: '2.1.3', source: 'official', enabled: true, trustLevel: 'VERIFIED' },
    { id: 'ins-3', tenantId: 'default', provider: 'mysql', connectorId: 'query', version: '1.0.8', source: 'community', enabled: false, trustLevel: 'COMMUNITY' },
  ]

  const connections: any[] = [
    { id: 'conn-1', tenantId: 'default', installationId: 'ins-1', name: '研发大群机器人', status: 'ACTIVE', credentialsConfigured: true },
    { id: 'conn-2', tenantId: 'default', installationId: 'ins-2', name: 'org 主账号', status: 'ACTIVE', credentialsConfigured: true },
  ]

  const executions: any[] = [
    { id: uid('exec'), tenantId: 'default', provider: 'feishu', connectorId: 'im', actionId: 'send_message', installationId: 'ins-1', connectionId: 'conn-1', agentId: 'app-1', status: 'SUCCESS', durationMs: 182, createdAt: iso(-14 * MIN) },
    { id: uid('exec'), tenantId: 'default', provider: 'github', connectorId: 'repo', actionId: 'create_issue', installationId: 'ins-2', connectionId: 'conn-2', workflowId: 'wf-app3', status: 'SUCCESS', durationMs: 640, createdAt: iso(-2 * HOUR) },
    { id: uid('exec'), tenantId: 'default', provider: 'feishu', connectorId: 'im', actionId: 'list_chats', installationId: 'ins-1', connectionId: 'conn-1', status: 'FAILED', durationMs: 30_012, errorCode: 'TIMEOUT', errorMessage: 'upstream timeout', createdAt: iso(-1 * DAY) },
    { id: uid('exec'), tenantId: 'default', provider: 'mysql', connectorId: 'query', actionId: 'execute_query', status: 'SUCCESS', durationMs: 45, createdAt: iso(-2 * DAY) },
  ]

  // -------------------------------------------------------------------------
  // 渠道 / 机器人 / 会话
  // -------------------------------------------------------------------------
  const channels: any[] = [
    { provider: 'wecom', channelId: 'group-bot', name: '企业微信群机器人', description: '通过群机器人收发消息', version: '1.3.0', installed: true, enabled: true, runtimeStatus: 'ONLINE', credentialSchema: JSON.stringify({ type: 'object', properties: { corpId: { type: 'string', title: '企业 ID' }, botKey: { type: 'string', title: '机器人 Key' } }, required: ['corpId', 'botKey'] }), capabilities: ['CHANNEL_INBOUND', 'CHANNEL_OUTBOUND'] },
    { provider: 'dingtalk', channelId: 'robot', name: '钉钉机器人', description: '群聊 @ 触发与主动推送', version: '1.2.0', installed: true, enabled: true, runtimeStatus: 'ONLINE', credentialSchema: JSON.stringify({ type: 'object', properties: { appKey: { type: 'string' }, appSecret: { type: 'string' } }, required: ['appKey', 'appSecret'] }), capabilities: ['CHANNEL_INBOUND', 'CHANNEL_OUTBOUND'] },
    { provider: 'feishu', channelId: 'im', name: '飞书应用消息', description: '单聊 / 群聊双向消息', version: '1.4.0', installed: true, enabled: false, runtimeStatus: 'OFFLINE', capabilities: ['CHANNEL_INBOUND', 'CHANNEL_OUTBOUND'] },
    { provider: 'slack', channelId: 'app', name: 'Slack App', description: 'Slack 工作区集成', version: '0.8.0', installed: false, enabled: false, runtimeStatus: 'NOT_INSTALLED', capabilities: ['CHANNEL_INBOUND', 'CHANNEL_OUTBOUND'] },
    { provider: 'web', channelId: 'widget', name: '网页挂件', description: '嵌入官网的聊天气泡', version: '2.0.1', installed: true, enabled: true, runtimeStatus: 'ONLINE', capabilities: ['CHANNEL_INBOUND', 'CHANNEL_OUTBOUND'] },
  ]

  const channelAccounts: Record<string, any[]> = {
    'wecom/group-bot': [
      { channelId: 'group-bot', accountId: 'acc-wecom-1', name: '客服一组', enabled: true, configured: true, running: true, connected: true, lastConnectedAt: iso(-3 * MIN) },
      { channelId: 'group-bot', accountId: 'acc-wecom-2', name: '客服二组', enabled: true, configured: true, running: true, connected: false, lastError: 'token 过期', lastConnectedAt: iso(-5 * HOUR) },
    ],
    'dingtalk/robot': [
      { channelId: 'robot', accountId: 'acc-ding-1', name: '研发通知群', enabled: true, configured: true, running: true, connected: true, lastConnectedAt: iso(-1 * MIN) },
    ],
  }

  // MyRobotsPanel 的机器人 = 带 ownerId 的 channel-connection
  const robots: any[] = [
    { id: 'robot-1', tenantId: 'default', ownerType: 'USER', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', name: '售后小助手', desiredStatus: 'RUNNING', runtimeStatus: 'RUNNING', runtimeAccountId: 'acc-wecom-1', agentId: 'app-1', agentVersionId: 'v-101', routingPolicyVersion: 2, credentialsConfigured: true, configVersion: 3, icon: '🎧', iconBackground: '#0ea5e9', description: '企微群里的售后咨询机器人', welcomeMessage: '大家好，我是售后小助手，@我 即可咨询', agentName: '智能客服助手', createdByName: '演示用户', createdAt: iso(-6 * DAY) },
    { id: 'robot-2', tenantId: 'default', ownerType: 'USER', ownerId: 'demo-user', provider: 'web', channelId: 'widget', name: '官网咨询挂件', desiredStatus: 'RUNNING', runtimeStatus: 'STOPPED', agentId: 'app-1', routingPolicyVersion: 2, credentialsConfigured: true, configVersion: 1, icon: '🌐', iconBackground: '#6366f1', description: '嵌入官网首页的聊天气泡', agentName: '智能客服助手', createdByName: '演示用户', createdAt: iso(-2 * DAY) },
  ]

  const conversations: any[] = [
    { id: 'cc-1', tenantId: 'default', connectionId: 'robot-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-1', conversationId: 'conv-wecom-1', agentId: 'app-1', agentVersionId: 'v-101', routingPolicyVersion: 2, status: 'BOT_ACTIVE', agentPaused: false, unreadCount: 0, lastMessageAt: iso(-8 * MIN), lastMessagePreview: '好的，已经为您登记退货申请', slaBreached: false, lockVersion: 4 },
    { id: 'cc-2', tenantId: 'default', connectionId: 'robot-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-1', conversationId: 'conv-wecom-2', agentId: 'app-1', routingPolicyVersion: 2, status: 'WAITING_HUMAN', agentPaused: true, unreadCount: 3, lastMessageAt: iso(-25 * MIN), lastMessagePreview: '这个问题机器人答非所问，找人工！', handoffRequestedAt: iso(-24 * MIN), slaDueAt: iso(35 * MIN), slaBreached: false, lockVersion: 2 },
    { id: 'cc-3', tenantId: 'default', connectionId: 'robot-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-2', conversationId: 'conv-wecom-3', agentId: 'app-2', routingPolicyVersion: 2, status: 'HUMAN_ACTIVE', agentPaused: true, assigneeId: 'demo-user', assigneeName: '演示用户', unreadCount: 1, lastMessageAt: iso(-2 * HOUR), lastMessagePreview: '我补充一下上周的数据口径', claimedAt: iso(-3 * HOUR), slaDueAt: iso(-1 * HOUR), slaBreached: true, lockVersion: 7 },
    { id: 'cc-4', tenantId: 'default', connectionId: 'robot-1', ownerId: 'demo-user', provider: 'dingtalk', channelId: 'robot', accountId: 'acc-ding-1', conversationId: 'conv-ding-1', agentId: 'app-1', routingPolicyVersion: 2, status: 'CLOSED', agentPaused: false, unreadCount: 0, lastMessageAt: iso(-2 * DAY), lastMessagePreview: '感谢咨询，再见', closedAt: iso(-2 * DAY), slaBreached: false, lockVersion: 9 },
  ]

  const channelEvents: any[] = [
    { id: 'ev-1', tenantId: 'default', connectionId: 'robot-1', runtimeAccountId: 'acc-wecom-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-1', conversationId: 'conv-wecom-2', direction: 'INBOUND', content: '这个问题机器人答非所问，找人工！', messageType: 'TEXT', handled: true, status: 'PROCESSED', senderId: 'user-88', senderType: 'AGENT', eventTime: iso(-25 * MIN), createdAt: iso(-25 * MIN) },
    { id: 'ev-2', tenantId: 'default', connectionId: 'robot-1', runtimeAccountId: 'acc-wecom-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-1', conversationId: 'conv-wecom-1', direction: 'INBOUND', content: '我要退货，订单号 SO-20260925-118', messageType: 'TEXT', handled: true, status: 'PROCESSED', senderId: 'user-12', eventTime: iso(-30 * MIN), createdAt: iso(-30 * MIN) },
    { id: 'ev-3', tenantId: 'default', connectionId: 'robot-1', runtimeAccountId: 'acc-wecom-1', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-1', conversationId: 'conv-wecom-1', direction: 'OUTBOUND', content: '好的，已经为您登记退货申请', messageType: 'TEXT', handled: true, status: 'SENT', replyToEventId: 'ev-2', durationMs: 1240, senderType: 'AGENT', eventTime: iso(-29 * MIN), createdAt: iso(-29 * MIN) },
    { id: 'ev-4', tenantId: 'default', connectionId: 'robot-1', runtimeAccountId: 'acc-wecom-2', ownerId: 'demo-user', provider: 'wecom', channelId: 'group-bot', accountId: 'acc-wecom-2', conversationId: 'conv-wecom-9', direction: 'OUTBOUND', content: '推送失败测试', messageType: 'TEXT', handled: false, status: 'FAILED', errorMessage: 'account disconnected', attempts: 3, nextAttemptAt: iso(10 * MIN), eventTime: iso(-1 * HOUR), createdAt: iso(-1 * HOUR) },
  ]

  const audits: any[] = [
    { id: uid('audit'), tenantId: 'default', actorId: 'demo-user', actorName: '演示用户', principalType: 'USER', action: 'CONNECTION_UPDATED', resourceType: 'CHANNEL_CONNECTION', resourceId: 'robot-1', outcome: 'SUCCESS', createdAt: iso(-3 * HOUR) },
    { id: uid('audit'), tenantId: 'default', actorId: 'demo-user', actorName: '演示用户', principalType: 'USER', action: 'CONVERSATION_CLAIMED', resourceType: 'CHANNEL_CONVERSATION', resourceId: 'cc-3', outcome: 'SUCCESS', createdAt: iso(-3 * HOUR) },
    { id: uid('audit'), tenantId: 'default', actorId: 'system', principalType: 'SYSTEM', action: 'EVENT_RETRIED', resourceType: 'CHANNEL_EVENT', resourceId: 'ev-4', outcome: 'FAILURE', details: 'max attempts exceeded', createdAt: iso(-50 * MIN) },
  ]

  // -------------------------------------------------------------------------
  // 聊天会话（AgentChatPage）
  // -------------------------------------------------------------------------
  const chatConversations: Record<string, any[]> = {
    'app-1': [
      { conversationId: 'conv-501', name: '退货运费咨询', userId: 'demo-user', firstMessage: '退货运费谁承担？', updatedAt: iso(-2 * HOUR), pinned: true, messageCount: 4 },
      { conversationId: 'conv-502', name: '固件升级失败', userId: 'demo-user', firstMessage: '设备红灯常亮怎么办', updatedAt: iso(-1 * DAY), messageCount: 2 },
    ],
  }

  const chatMessages: Record<string, any[]> = {
    'app-1/conv-501': [
      { role: 'USER', content: '退货运费谁承担？', createdAt: iso(-2 * HOUR) },
      { role: 'ASSISTANT', content: '7 天无理由退货的运费由买家承担；如果是质量问题退货，运费由商家承担并支持到付。', createdAt: iso(-2 * HOUR + 4000) },
      { role: 'USER', content: '质量问题怎么举证？', createdAt: iso(-1.9 * HOUR) },
      { role: 'ASSISTANT', content: '请拍摄故障视频（含设备序列号）并上传工单，质检确认后自动转商家承担运费。', createdAt: iso(-1.9 * HOUR + 5000) },
    ],
    'app-1/conv-502': [
      { role: 'USER', content: '设备红灯常亮怎么办', createdAt: iso(-1 * DAY) },
      { role: 'ASSISTANT', content: '红灯常亮通常是固件校验失败：请长按重置键 8 秒，待指示灯蓝闪后重新升级固件。', createdAt: iso(-1 * DAY + 6000) },
    ],
  }

  // -------------------------------------------------------------------------
  // LLMOps 观测（总览页）
  // -------------------------------------------------------------------------
  const llmopsTotal = { calls: 18_462, errors: 63, promptTokens: 24_380_112, completionTokens: 6_204_887, totalTokens: 30_584_999, costMicros: 412_560_000, avgLatencyMs: 842 }

  const llmopsTrend = Array.from({ length: 24 }, (_, i) => {
    const bucket = new Date(now - (23 - i) * HOUR)
    const base = 500 + Math.round(280 * Math.sin((i / 24) * Math.PI * 2)) + ((i * 37) % 90)
    return {
      bucketStart: bucket.toISOString(),
      calls: base,
      errors: (i * 7) % 5,
      totalTokens: base * 1650 + ((i * 911) % 40_000),
      costMicros: base * 21_000 + ((i * 1733) % 900_000),
      avgLatencyMs: 700 + ((i * 53) % 300),
    }
  })

  const llmopsRecent = Array.from({ length: 30 }, (_, i) => {
    const pick = [
      { provider: 'openai', model: 'gpt-4o' },
      { provider: 'anthropic', model: 'claude-sonnet-4-5' },
      { provider: 'dashscope', model: 'qwen-max' },
      { provider: 'openai', model: 'text-embedding-3-small' },
    ][i % 4]
    return {
      id: uid('call'),
      provider: pick.provider,
      model: pick.model,
      promptTokens: 320 + ((i * 173) % 2400),
      completionTokens: 60 + ((i * 91) % 700),
      totalTokens: 380 + ((i * 264) % 3100),
      costMicros: 800 + ((i * 431) % 12_000),
      latencyMs: 260 + ((i * 137) % 1800),
      success: i % 17 !== 3,
      createdAt: iso(-i * 7 * MIN),
    }
  })

  return {
    providers,
    models,
    datasets,
    documents,
    segments,
    hitHistory,
    indexVersions,
    agents,
    agentVersions,
    annotations,
    apiTokens,
    workflows,
    tools,
    skills,
    mcpServers,
    triggers,
    invocations,
    connectors,
    installations,
    connections,
    executions,
    channels,
    channelAccounts,
    robots,
    conversations,
    channelEvents,
    audits,
    chatConversations,
    chatMessages,
    llmopsTotal,
    llmopsTrend,
    llmopsRecent,
    tenantAgentBinding: null as any,
  }
}

export type Db = ReturnType<typeof createDb>
