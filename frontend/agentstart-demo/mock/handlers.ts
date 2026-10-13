/**
 * mock 路由表 —— 覆盖 vue-agent-start 全部组件用到的 /api/agent-start/* 端点。
 * 响应信封统一为 { code: 'ok', data }；SSE 端点直接写原始流。
 */
import type { IncomingMessage, ServerResponse } from 'node:http'

import type { Db } from './db.js'
import { uid } from './db.js'

export interface Ctx {
  req: IncomingMessage
  res: ServerResponse
  params: Record<string, string>
  query: URLSearchParams
  body: any
  db: Db
}

interface Route {
  method: string
  keys: string[]
  pattern: RegExp
  handler: (ctx: Ctx) => any
}

const routes: Route[] = []

function route(method: string, path: string, handler: (ctx: Ctx) => any) {
  const keys: string[] = []
  const pattern = new RegExp(
    '^' +
      path.replace(/:[^/]+/g, (m) => {
        keys.push(m.slice(1))
        return '([^/]+)'
      }) +
      '$',
  )
  routes.push({ method, keys, pattern, handler })
}

export function matchRoute(method: string, pathname: string): { route: Route; params: Record<string, string> } | null {
  for (const r of routes) {
    if (r.method !== method) continue
    const m = pathname.match(r.pattern)
    if (m) {
      const params: Record<string, string> = {}
      r.keys.forEach((k, i) => (params[k] = decodeURIComponent(m[i + 1]!)))
      return { route: r, params }
    }
  }
  return null
}

// ---------------------------------------------------------------------------
// SSE 工具
// ---------------------------------------------------------------------------
function sseWriter(res: ServerResponse) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  })
  res.write(':ok\n\n')
  return {
    send(event: string, data: unknown) {
      res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
    },
    wait: (ms: number) => new Promise<void>((r) => setTimeout(r, ms)),
    end() {
      res.end()
    },
  }
}

const rand = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min

// ===========================================================================
// models
// ===========================================================================
route('GET', '/models', ({ db, query }) => {
  const type = query.get('type')
  return type ? db.models.filter((m) => m.modelType === type) : db.models
})

route('POST', '/models', ({ db, body }) => {
  const m = {
    id: uid('m'),
    tenantId: 'default',
    providerName: body.providerName,
    modelName: body.modelName,
    modelType: body.modelType,
    enabled: true,
    isDefault: body.asDefault ?? false,
    credentialId: body.credentialId,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
  db.models.push(m)
  return m
})

route('POST', '/models/validate', () => null)

route('PUT', '/models/:id/credentials', ({ db, params }) =>
  db.models.find((m) => m.id === params.id) ?? null,
)

route('PUT', '/models/:id/default', ({ db, params }) => {
  const target = db.models.find((m) => m.id === params.id)
  if (target) {
    db.models
      .filter((m) => m.modelType === target.modelType)
      .forEach((m) => (m.isDefault = m.id === target.id))
  }
  return null
})

route('PATCH', '/models/:id/enabled', ({ db, params, body }) => {
  const m = db.models.find((x) => x.id === params.id)
  if (m) m.enabled = body?.enabled ?? !m.enabled
  return m ?? null
})

route('DELETE', '/models/:id', ({ db, params }) => {
  const i = db.models.findIndex((m) => m.id === params.id)
  if (i >= 0) db.models.splice(i, 1)
  return null
})

route('POST', '/models/:id/test', ({ params }) => ({
  ok: true,
  latencyMs: rand(120, 900),
  kind: params.id.startsWith('m-') ? 'LLM' : 'UNKNOWN',
  snippet: 'pong — 连通性正常',
}))

route('GET', '/models/:id/parameters', () => ({
  rules: [
    { name: 'temperature', label: '温度', type: 'FLOAT', min: 0, max: 2, step: 0.1, precision: 1, defaultValue: 0.7, help: '越高越随机' },
    { name: 'maxTokens', label: '最大 Token', type: 'INT', min: 1, max: 128000, defaultValue: 2048 },
    { name: 'topP', label: 'Top P', type: 'FLOAT', min: 0, max: 1, step: 0.01, precision: 2, defaultValue: 1 },
    { name: 'stream', label: '流式输出', type: 'BOOLEAN', defaultValue: true },
  ],
  parameters: { temperature: 0.7, maxTokens: 2048, stream: true },
}))

route('PUT', '/models/:id/parameters', ({ body }) => ({ rules: [], parameters: body ?? {} }))

route('GET', '/models/defaults', ({ db }) => {
  const out: Record<string, any> = {}
  for (const m of db.models) {
    if (m.isDefault && m.enabled) out[m.modelType] = m
  }
  for (const t of ['LLM', 'TEXT_EMBEDDING', 'RERANK', 'TTS', 'SPEECH2TEXT', 'IMAGE', 'VIDEO', 'MODERATION']) {
    if (!(t in out)) out[t] = null
  }
  return out
})

route('GET', '/models/grouped-by-type', ({ db }) => {
  const grouped: Record<string, any[]> = {}
  for (const p of db.providers) {
    const installed = db.models.filter((m) => m.providerName === p.name)
    if (!installed.length) continue
    for (const m of installed) {
      grouped[m.modelType] ??= []
      const arr = grouped[m.modelType]
      let view = arr.find((v) => v.provider === p.name)
      if (!view) {
        view = {
          id: p.name,
          provider: p.name,
          label: p.label,
          description: p.description,
          modelList: [],
        }
        arr.push(view)
      }
      view.modelList.push({
        id: `${p.name}::${m.modelName}::${m.modelType}`,
        providerName: p.name,
        modelName: m.modelName,
        modelType: m.modelType,
      })
    }
  }
  return grouped
})

// ===========================================================================
// providers
// ===========================================================================
route('GET', '/model-providers', ({ db }) => db.providers)

route('GET', '/model-providers/:name', ({ db, params }) =>
  db.providers.find((p) => p.name === params.name) ?? null,
)

route('GET', '/model-providers/:name/credential', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  return {
    providerName: params.name,
    configured: p?.credentialConfigured ?? false,
    credentialId: p?.credentialId,
    credentialName: p?.credentialMasked,
  }
})

route('PUT', '/model-providers/:name/credential', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  if (p) {
    p.credentialConfigured = true
    p.credentialId = p.credentialId ?? uid('cred')
    p.credentialMasked = 'sk-****' + Math.random().toString(16).slice(2, 6)
  }
  return { providerName: params.name, configured: true, credentialId: p?.credentialId, credentialName: p?.credentialMasked }
})

route('DELETE', '/model-providers/:name/credential', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  if (p) {
    p.credentialConfigured = false
    p.credentialId = undefined
    p.credentialMasked = undefined
  }
  return null
})

route('GET', '/model-providers/:name/remote-models', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  return (p?.predefinedModels ?? []).map((m: any) => ({
    modelId: m.model,
    label: m.label ?? m.model,
    modelType: m.modelType,
    contextLength: m.contextLength,
    dimensions: m.dimensions,
    features: m.features,
    ownedBy: p.name,
    typeInferred: false,
  }))
})

route('POST', '/model-providers/:name/refresh-catalog', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  return (p?.predefinedModels ?? []).map((m: any) => ({
    modelId: m.model,
    label: m.label ?? m.model,
    modelType: m.modelType,
    contextLength: m.contextLength,
    dimensions: m.dimensions,
  }))
})

route('GET', '/model-providers/:name/catalog', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  return (p?.predefinedModels ?? []).map((m: any) => {
    const installed = db.models.find(
      (x) => x.providerName === p.name && x.modelName === m.model,
    )
    return {
      id: `${p.name}::${m.model}`,
      model: m.model,
      label: m.label ?? m.model,
      modelType: m.modelType,
      contextLength: m.contextLength,
      dimensions: m.dimensions,
      features: m.features,
      source: 'predefined',
      enabled: installed?.enabled ?? false,
      isDefault: installed?.isDefault ?? false,
      credentialId: installed?.credentialId,
    }
  })
})

route('PUT', '/model-providers/:name/models/:model/enabled', ({ db, params, query }) => {
  const { name, model } = params
  const modelType = query.get('modelType') ?? 'LLM'
  const enabled = query.get('enabled') !== 'false'
  let m = db.models.find((x) => x.providerName === name && x.modelName === model)
  if (!m) {
    m = { id: uid('m'), tenantId: 'default', providerName: name, modelName: model, modelType, enabled, isDefault: false, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    db.models.push(m)
  }
  m.enabled = enabled
  const p = db.providers.find((x) => x.name === name)
  if (p) p.installedModelCount = db.models.filter((x) => x.providerName === name).length
  return enabled
})

route('PUT', '/model-providers/:name/models/:model/default', ({ db, params, query }) => {
  const modelType = query.get('modelType') ?? 'LLM'
  db.models
    .filter((m) => m.modelType === modelType)
    .forEach((m) => (m.isDefault = m.providerName === params.name && m.modelName === params.model))
  return null
})

route('GET', '/model-provider-impls', () => ['openai', 'anthropic', 'dashscope', 'ollama', 'deepseek'])

route('POST', '/model-provider-definitions', ({ body }) => ({ id: uid('def'), source: 'custom', enabled: true, ...body }))
route('PUT', '/model-provider-definitions/:id', ({ body, params }) => ({ id: params.id, ...body }))
route('DELETE', '/model-provider-definitions/:id', () => null)

route('POST', '/model-providers/:name/predefined-models', ({ db, params, body }) => {
  const p = db.providers.find((x) => x.name === params.name)
  const m = { id: uid('pm'), source: 'custom', ...body }
  p?.predefinedModels.push(m)
  return m
})
route('DELETE', '/model-providers/:name/predefined-models/:id', ({ db, params }) => {
  const p = db.providers.find((x) => x.name === params.name)
  const i = p?.predefinedModels.findIndex((m: any) => m.id === params.id) ?? -1
  if (p && i >= 0) p.predefinedModels.splice(i, 1)
  return null
})

// ===========================================================================
// knowledge / datasets
// ===========================================================================
route('GET', '/datasets', ({ db }) => db.datasets)

route('POST', '/datasets', ({ db, body }) => {
  const ds = {
    id: uid('ds'),
    tenantId: 'default',
    name: body?.name ?? '未命名数据集',
    description: body?.description ?? '',
    documentCount: 0,
    segmentCount: 0,
    indexingTechnique: body?.indexingTechnique ?? 'high_quality',
    updatedAt: new Date().toISOString(),
    processRuleJson: body?.processRuleJson ?? '{}',
  }
  db.datasets.push(ds)
  db.documents[ds.id] = []
  db.segments[ds.id] = []
  return ds
})

route('GET', '/datasets/:id', ({ db, params }) =>
  db.datasets.find((d) => d.id === params.id) ?? null,
)

route('PUT', '/datasets/:id', ({ db, params, body }) => {
  const ds = db.datasets.find((d) => d.id === params.id)
  if (ds) Object.assign(ds, body, { updatedAt: new Date().toISOString() })
  return ds ?? null
})

route('DELETE', '/datasets/:id', ({ db, params }) => {
  const i = db.datasets.findIndex((d) => d.id === params.id)
  if (i >= 0) db.datasets.splice(i, 1)
  delete db.documents[params.id]
  delete db.segments[params.id]
  return null
})

function refreshDatasetCounters(db: Db, dsId: string) {
  const docs = db.documents[dsId] ?? []
  const segs = db.segments[dsId] ?? []
  const ds = db.datasets.find((d) => d.id === dsId)
  if (ds) {
    ds.documentCount = docs.length
    ds.segmentCount = segs.length
    ds.updatedAt = new Date().toISOString()
  }
}

route('GET', '/datasets/:ds/documents', ({ db, params }) => db.documents[params.ds] ?? [])

route('GET', '/datasets/:ds/documents/:doc', ({ db, params }) =>
  (db.documents[params.ds] ?? []).find((d) => d.id === params.doc) ?? null,
)

route('POST', '/datasets/:ds/documents/upload', ({ db, params }) => {
  const doc = {
    id: uid('doc'),
    name: `新上传文档-${rand(100, 999)}.md`,
    sourceType: 'upload',
    wordCount: rand(500, 8000),
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    status: 'INDEXING',
    enabled: true,
    parserName: 'default',
    mediaType: 'text/markdown',
    fileSize: rand(10_000, 900_000),
  }
  ;(db.documents[params.ds] ??= []).push(doc)
  // 模拟异步索引完成
  setTimeout(() => {
    doc.status = 'COMPLETED'
    const segs = (db.segments[params.ds] ??= [])
    for (let i = 0; i < 3; i++) {
      segs.push({ id: uid('seg'), documentId: doc.id, position: segs.length + 1, content: `${doc.name} 的第 ${i + 1} 段示例内容：系统会在索引完成后自动切分并嵌入向量。`, tokenCount: rand(60, 200), enabled: true, keywords: [] })
    }
    refreshDatasetCounters(db, params.ds)
  }, 2500)
  refreshDatasetCounters(db, params.ds)
  return null
})

route('DELETE', '/datasets/:ds/documents/:doc', ({ db, params }) => {
  const docs = db.documents[params.ds] ?? []
  const i = docs.findIndex((d) => d.id === params.doc)
  if (i >= 0) docs.splice(i, 1)
  db.segments[params.ds] = (db.segments[params.ds] ?? []).filter((s) => s.documentId !== params.doc)
  refreshDatasetCounters(db, params.ds)
  return null
})

route('PUT', '/datasets/:ds/documents/:doc/enabled', ({ db, params, body }) => {
  const doc = (db.documents[params.ds] ?? []).find((d) => d.id === params.doc)
  if (doc) {
    doc.enabled = body?.enabled ?? !doc.enabled
    for (const s of db.segments[params.ds] ?? []) {
      if (s.documentId === doc.id) s.enabled = doc.enabled
    }
  }
  return doc ?? null
})

route('GET', '/datasets/:ds/documents/:doc/parsed', () => ({
  content: '# 解析结果\n\n这是 mock 的文档解析正文，用于预览切分效果。',
}))

route('POST', '/datasets/:ds/documents/:doc/reparse', ({ db, params }) => {
  const doc = (db.documents[params.ds] ?? []).find((d) => d.id === params.doc)
  if (doc) {
    doc.status = 'INDEXING'
    setTimeout(() => (doc.status = 'COMPLETED'), 2000)
  }
  return doc ?? null
})

route('POST', '/datasets/preview-chunks', () => ({
  chunks: Array.from({ length: 4 }, (_, i) => ({
    position: i + 1,
    content: `【预览分块 ${i + 1}】文档正文将按分隔符与最大长度切分为若干片段，此处为 mock 内容。`,
    tokenCount: 80 + i * 25,
  })),
  total: 4,
}))

route('GET', '/datasets/:ds/documents/:doc/segments', ({ db, params, query }) => {
  const page = Number(query.get('page') ?? 1)
  const pageSize = Number(query.get('pageSize') ?? 20)
  const all = (db.segments[params.ds] ?? []).filter((s) => s.documentId === params.doc)
  return all.slice((page - 1) * pageSize, page * pageSize)
})

route('POST', '/datasets/:ds/documents/:doc/segments', ({ db, params, body }) => {
  const segs = (db.segments[params.ds] ??= [])
  const seg = { id: uid('seg'), documentId: params.doc, position: segs.length + 1, content: body?.content ?? '', tokenCount: Math.max(1, Math.round((body?.content ?? '').length / 2)), enabled: true, keywords: [] }
  segs.push(seg)
  refreshDatasetCounters(db, params.ds)
  return seg
})

route('PUT', '/datasets/:ds/documents/:doc/segments/:seg', ({ db, params, body }) => {
  const seg = (db.segments[params.ds] ?? []).find((s) => s.id === params.seg)
  if (seg && body) {
    seg.content = body.content ?? seg.content
    seg.tokenCount = Math.max(1, Math.round(String(seg.content).length / 2))
  }
  return seg ?? null
})

route('DELETE', '/datasets/:ds/documents/:doc/segments/:seg', ({ db, params }) => {
  const segs = db.segments[params.ds] ?? []
  const i = segs.findIndex((s) => s.id === params.seg)
  if (i >= 0) segs.splice(i, 1)
  refreshDatasetCounters(db, params.ds)
  return null
})

route('PUT', '/datasets/:ds/documents/:doc/segments/:seg/enabled', ({ db, params, body }) => {
  const seg = (db.segments[params.ds] ?? []).find((s) => s.id === params.seg)
  if (seg) seg.enabled = body?.enabled ?? !seg.enabled
  return seg ?? null
})

route('POST', '/datasets/:ds/retrieve', ({ db, params, body }) => {
  const segs = db.segments[params.ds] ?? []
  const topK = Number(body?.topK ?? 5)
  return segs
    .filter((s) => s.enabled)
    .slice(0, topK)
    .map((s, i) => {
      const doc = (db.documents[params.ds] ?? []).find((d) => d.id === s.documentId)
      return {
        segmentId: s.id,
        content: s.content,
        position: s.position,
        score: Number((0.92 - i * 0.07 + Math.random() * 0.02).toFixed(4)),
        datasetId: params.ds,
        documentId: s.documentId,
        documentName: doc?.name,
        vectorScore: Number((0.88 - i * 0.06).toFixed(4)),
        keywordScore: Number((0.55 - i * 0.05).toFixed(4)),
      }
    })
})

route('GET', '/datasets/:ds/hit-testing/history', ({ db, params }) => db.hitHistory[params.ds] ?? [])

route('GET', '/datasets/:ds/knowledge-graph', ({ db, params }) => {
  const segs = (db.segments[params.ds] ?? []).slice(0, 5)
  const nodes: any[] = [
    { id: 'g-root', type: 'DATASET', label: db.datasets.find((d) => d.id === params.ds)?.name ?? '数据集', datasetId: params.ds, weight: 1 },
  ]
  const edges: any[] = []
  segs.forEach((s, i) => {
    nodes.push({ id: `g-ent-${i}`, type: 'ENTITY', label: ['退换货政策', '固件升级', 'SLA 响应', '灰度发布', '向量检索'][i % 5], datasetId: params.ds, segmentId: s.id, weight: 0.6 + i * 0.05 })
    edges.push({ source: 'g-root', target: `g-ent-${i}`, relation: 'CONTAINS', weight: 1 })
    if (i > 0) edges.push({ source: `g-ent-${i - 1}`, target: `g-ent-${i}`, relation: 'RELATES_TO', weight: 0.4 })
  })
  return { datasetId: params.ds, nodes, edges }
})

route('GET', '/datasets/:ds/rag/index-versions', ({ db, params }) => db.indexVersions[params.ds] ?? [])

route('POST', '/datasets/:ds/rag/index-versions', ({ db, params }) => {
  const list = (db.indexVersions[params.ds] ??= [])
  const v = {
    id: uid('iv'),
    version: list.length + 1,
    status: 'REBUILDING',
    embeddingModelVersion: 'text-embedding-3-small@v1',
    chunkingRuleVersion: 'r-800',
    contentChecksum: Math.random().toString(16).slice(2, 8),
    documentCount: (db.documents[params.ds] ?? []).length,
    segmentCount: (db.segments[params.ds] ?? []).length,
    createdAt: new Date().toISOString(),
  }
  list.unshift(v)
  setTimeout(() => (v.status = 'ACTIVE'), 4000)
  return v
})

route('POST', '/datasets/:ds/rag/index-versions/:vid/activate', ({ db, params }) => {
  for (const v of db.indexVersions[params.ds] ?? []) {
    v.status = v.id === params.vid ? 'ACTIVE' : 'RETIRED'
  }
  return null
})

route('POST', '/datasets/:ds/rag/evaluate', ({ db, params }) => ({
  datasetName: db.datasets.find((d) => d.id === params.ds)?.name ?? '',
  datasetVersion: 'v3',
  experimentName: `eval-${Date.now()}`,
  topK: 5,
  evaluatedAt: new Date().toISOString(),
  metrics: { recallAt5: 0.87, precisionAt5: 0.74, mrr: 0.81, ndcg: 0.79 },
  cases: [],
  configuration: { method: 'hybrid', scoreThreshold: '0.6' },
}))

route('GET', '/datasets/:ds/ingestion-jobs/poisoned', ({ db, params }) =>
  (db.documents[params.ds] ?? [])
    .filter((d) => d.status === 'FAILED')
    .map((d) => ({ documentId: d.id, datasetId: params.ds, filename: d.name, status: 'FAILED', retryCount: 2, lastError: '解析失败：文件尾部截断', updatedAt: d.updatedAt })),
)

route('POST', '/datasets/:ds/ingestion-jobs/:docId/replay', ({ db, params }) => {
  const doc = (db.documents[params.ds] ?? []).find((d) => d.id === params.docId)
  if (doc) {
    doc.status = 'INDEXING'
    setTimeout(() => (doc.status = 'COMPLETED'), 2500)
  }
  return true
})

// ===========================================================================
// agents / apps
// ===========================================================================
route('GET', '/agents', ({ db }) => db.agents)

function toEntity(body: any): any {
  const str = (v: any) => (v === undefined ? undefined : typeof v === 'string' ? v : JSON.stringify(v))
  return {
    id: uid('app'),
    tenantId: 'default',
    visibility: 'GLOBAL',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    published: false,
    ...body,
    suggestedQuestionsJson: str(body.suggestedQuestions),
    datasetIdsJson: str(body.datasetIds),
    retrievalConfigJson: str(body.retrievalConfig),
    modelSettingsJson: str(body.modelSettings),
    toolNamesJson: str(body.toolNames),
    skillIdsJson: str(body.skillIds),
  }
}

route('POST', '/agents', ({ db, body }) => {
  const a = toEntity(body)
  db.agents.push(a)
  return a
})

route('GET', '/agents/:id', ({ db, params }) =>
  db.agents.find((a) => a.id === params.id) ?? null,
)

route('PUT', '/agents/:id', ({ db, params, body }) => {
  const a = db.agents.find((x) => x.id === params.id)
  if (a) {
    const merged = toEntity({ ...a, ...body, id: a.id })
    Object.assign(a, merged, { updatedAt: new Date().toISOString() })
  }
  return a ?? null
})

route('DELETE', '/agents/:id', ({ db, params }) => {
  const i = db.agents.findIndex((a) => a.id === params.id)
  if (i >= 0) db.agents.splice(i, 1)
  return null
})

route('GET', '/apps/:id/permissions', () => ({
  mode: 'ALL',
  grants: [{ type: 'ROLE', subjectId: 'role-cs', includeDescendants: true }],
}))

route('PUT', '/apps/:id/permissions', ({ body }) => body ?? { mode: 'ALL', grants: [] })

route('GET', '/apps/selectors/workflows', ({ db }) =>
  db.agents
    .filter((a) => a.workflowId)
    .map((a) => ({ appId: a.id, workflowId: a.workflowId, name: a.name, icon: a.icon, iconBackground: a.iconBackground, inputVariables: [] })),
)

route('GET', '/agents/:id/versions', ({ db, params }) => db.agentVersions[params.id] ?? [])

route('POST', '/agents/:id/versions/publish', ({ db, params, body }) => {
  const list = (db.agentVersions[params.id] ??= [])
  list.forEach((v) => (v.status = 'SUPERSEDED'))
  const v = {
    id: uid('v'),
    tenantId: 'default',
    appId: params.id,
    versionNumber: list.length + 1,
    status: 'ACTIVE',
    changeSummary: body?.summary ?? '',
    publishedBy: 'demo',
    publishedAt: new Date().toISOString(),
    runtimeType: 'NATIVE',
  }
  list.unshift(v)
  const a = db.agents.find((x) => x.id === params.id)
  if (a) a.published = true
  return v
})

route('POST', '/agents/:id/versions/:vid/rollback', ({ db, params }) => {
  const list = db.agentVersions[params.id] ?? []
  list.forEach((v) => (v.status = v.id === params.vid ? 'ACTIVE' : 'SUPERSEDED'))
  return list.find((v) => v.id === params.vid) ?? null
})

route('POST', '/agents/:id/versions/:vid/disable', ({ db, params }) => {
  const v = (db.agentVersions[params.id] ?? []).find((x) => x.id === params.vid)
  if (v) v.status = 'DISABLED'
  return v ?? null
})

route('GET', '/agent-runtimes', () => [
  { type: 'NATIVE', nativeRuntime: true },
  { type: 'SPRING_AI_ALIBABA', nativeRuntime: false },
])

route('GET', '/agents/:id/tools', ({ db }) =>
  db.tools.filter((t) => t.enabled).map((t) => ({
    name: t.name,
    description: t.description,
    inputSchema: t.inputSchema,
    label: t.label,
    category: t.category,
    provider: t.provider,
    connectorId: t.mcpServerId,
    riskLevel: 'READ',
    configured: true,
  })),
)

route('GET', '/apps/:appId/annotations', ({ db, params }) => db.annotations[params.appId] ?? [])

route('POST', '/apps/:appId/annotations', ({ db, params, body }) => {
  const a = { id: uid('an'), appId: params.appId, question: body?.question, content: body?.content, enabled: body?.enabled ?? true, hitCount: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ;(db.annotations[params.appId] ??= []).push(a)
  return a
})

route('PUT', '/apps/:appId/annotations/:id', ({ db, params, body }) => {
  const a = (db.annotations[params.appId] ?? []).find((x) => x.id === params.id)
  if (a) Object.assign(a, body, { updatedAt: new Date().toISOString() })
  return a ?? null
})

route('DELETE', '/apps/:appId/annotations/:id', ({ db, params }) => {
  const list = db.annotations[params.appId] ?? []
  const i = list.findIndex((x) => x.id === params.id)
  if (i >= 0) list.splice(i, 1)
  return null
})

route('GET', '/apps/:appId/metrics', ({ db, params }) => ({
  appId: params.appId,
  totalConversations: (db.chatConversations[params.appId] ?? []).length + rand(30, 200),
  totalMessages: rand(400, 2600),
  userMessages: rand(200, 1300),
  assistantMessages: rand(200, 1300),
  avgInteractionsPerConversation: Number((3 + Math.random() * 4).toFixed(2)),
  lastActivityAt: new Date().toISOString(),
}))

route('GET', '/apps/:appId/api-tokens', ({ db, params }) => db.apiTokens[params.appId] ?? [])

route('POST', '/apps/:appId/api-tokens', ({ db, params, body }) => {
  const t = { id: uid('tk'), appId: params.appId, name: body?.name ?? 'new-token', type: 'APP', token: `as-live-${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`, createdAt: new Date().toISOString() }
  ;(db.apiTokens[params.appId] ??= []).push({ ...t, token: t.token.slice(0, 8) + '****' })
  return t
})

route('POST', '/apps/:appId/api-tokens/:id/rename', ({ db, params, body }) => {
  const t = (db.apiTokens[params.appId] ?? []).find((x) => x.id === params.id)
  if (t) t.name = body?.name ?? t.name
  return t ?? null
})

route('DELETE', '/apps/:appId/api-tokens/:id', ({ db, params }) => {
  const list = db.apiTokens[params.appId] ?? []
  const i = list.findIndex((x) => x.id === params.id)
  if (i >= 0) list.splice(i, 1)
  return null
})

// ===========================================================================
// chat（会话 + SSE 流）
// ===========================================================================
route('POST', '/chat/conversations/:agentId', ({ db, params }) =>
  db.chatConversations[params.agentId] ?? [],
)

route('POST', '/chat/conversations/:agentId/:convId/messages', ({ db, params }) =>
  db.chatMessages[`${params.agentId}/${params.convId}`] ?? [],
)

function pushConversation(db: Db, agentId: string, convId: string, query: string, answer: string) {
  const list = (db.chatConversations[agentId] ??= [])
  const key = `${agentId}/${convId}`
  const msgs = (db.chatMessages[key] ??= [])
  msgs.push({ role: 'USER', content: query, createdAt: new Date().toISOString() })
  msgs.push({ role: 'ASSISTANT', content: answer, createdAt: new Date().toISOString() })
  let conv = list.find((c) => c.conversationId === convId)
  if (!conv) {
    conv = { conversationId: convId, name: query.slice(0, 18), userId: 'demo-user', firstMessage: query, updatedAt: new Date().toISOString(), pinned: false, messageCount: 0 }
    list.unshift(conv)
  }
  conv.messageCount = msgs.length
  conv.updatedAt = new Date().toISOString()
}

function buildAnswer(db: Db, query: string): string {
  const pool = db.segments['ds-1'] ?? []
  const start = rand(0, Math.max(0, pool.length - 2))
  const hits = pool.slice(start, start + 2)
  const body = hits.map((h) => `• ${h.content}`).join('\n')
  return `已根据知识库检索到与「${query}」相关的内容：\n\n${body}\n\n如需进一步协助，可回复"转人工"。`
}

route('POST', '/agents/:id/chat/stream', async ({ db, res, params, body }) => {
  const sse = sseWriter(res)
  const query = String(body?.query ?? '')
  const convId = String(body?.conversationId ?? uid('conv'))
  try {
    await sse.wait(350)
    sse.send('step', { kind: 'thought', thought: `解析用户问题：「${query}」，先检索知识库再作答。` })
    await sse.wait(500)
    sse.send('step', { kind: 'action', action: 'knowledge_retrieve', actionInput: { query, topK: 5 } })
    await sse.wait(600)
    sse.send('step', { kind: 'observation', observation: '命中 2 条相关片段（score ≥ 0.78）' })
    await sse.wait(500)
    const text = buildAnswer(db, query)
    sse.send('result', { text, conversationId: convId })
    pushConversation(db, params.id, convId, query, text)
  } finally {
    sse.end()
  }
  return undefined
})

route('POST', '/agents/:id/chat/preview/stream', async ({ db, res, body }) => {
  const sse = sseWriter(res)
  try {
    await sse.wait(600)
    sse.send('result', { text: buildAnswer(db, String(body?.query ?? '')) })
  } finally {
    sse.end()
  }
  return undefined
})

// ===========================================================================
// workflows
// ===========================================================================
route('GET', '/apps/:appId/workflow/draft', ({ db, params }) =>
  db.workflows.find((w) => w.appId === params.appId) ?? {
    id: uid('wf'),
    appId: params.appId,
    name: '草稿',
    mode: 'WORKFLOW',
    graph: { nodes: [], edges: [] },
    version: 1,
    published: false,
  },
)

route('PUT', '/apps/:appId/workflow/draft', ({ db, params, body }) => {
  let wf = db.workflows.find((w) => w.appId === params.appId)
  if (!wf) {
    wf = { id: uid('wf'), appId: params.appId, name: '草稿', mode: 'WORKFLOW', version: 0, published: false, createdAt: new Date().toISOString() }
    db.workflows.push(wf)
  }
  wf.graph = body?.graph ?? wf.graph
  wf.version += 1
  wf.updatedAt = new Date().toISOString()
  return wf
})

route('POST', '/apps/:appId/workflow/publish', ({ db, params }) => {
  const wf = db.workflows.find((w) => w.appId === params.appId)
  if (wf) wf.published = true
  return wf ?? null
})

route('POST', '/apps/:appId/workflow/restore/:snapshotId', ({ db, params }) =>
  db.workflows.find((w) => w.appId === params.appId) ?? null,
)

route('GET', '/apps/:appId/workflows', ({ db, params }) =>
  db.workflows.filter((w) => w.appId === params.appId),
)

route('GET', '/workflows', ({ db }) => db.workflows)

route('POST', '/workflows', ({ db, body }) => {
  const wf = { id: body?.appId ?? uid('wf'), appId: body?.appId, name: body?.name ?? '未命名工作流', mode: body?.mode ?? 'WORKFLOW', graph: body?.graph ?? { nodes: [], edges: [] }, version: 1, published: false, createdAt: new Date().toISOString() }
  db.workflows.push(wf)
  return wf
})

route('POST', '/workflows/run-graph', ({ body }) => {
  const nodes = body?.graph?.nodes ?? []
  return {
    runId: uid('run'),
    success: true,
    status: 'SUCCEEDED',
    outputs: { result: `mock 执行完成（${nodes.length} 个节点）` },
    steps: nodes.map((n: any) => ({ nodeId: n.id, nodeType: n.data?.type ?? 'CUSTOM', elapsedMillis: rand(80, 900), status: 'SUCCEEDED' })),
  }
})

route('POST', '/workflows/run-graph/stream', async ({ res, body }) => {
  const sse = sseWriter(res)
  const runId = uid('run')
  const nodes = body?.graph?.nodes ?? []
  try {
    sse.send('workflow_started', { runId })
    const steps: any[] = []
    for (const n of nodes) {
      await sse.wait(420)
      const step = { nodeId: n.id, nodeType: n.data?.type ?? 'CUSTOM', elapsedMillis: rand(120, 800), status: 'SUCCEEDED', outputs: {} }
      steps.push(step)
      sse.send('node_finished', step)
    }
    await sse.wait(300)
    sse.send('workflow_finished', { runId, success: true, status: 'SUCCEEDED', outputs: { result: 'mock 执行完成' }, steps })
  } finally {
    sse.end()
  }
  return undefined
})

route('POST', '/workflow-runs/:runId/cancel', () => true)
route('POST', '/workflow-runs/:runId/pause', () => true)
route('POST', '/workflow-runs/:runId/resume', ({ params }) => ({ runId: params.runId, success: true, status: 'SUCCEEDED', outputs: {}, steps: [] }))
route('POST', '/workflow-runs/:runId/signal', () => ({ accepted: true, duplicate: false }))
route('POST', '/workflow-events/:correlationKey', () => ({ accepted: true, duplicate: false }))
route('GET', '/node-types', () => [])
route('GET', '/workflow-examples', () => [])

// ===========================================================================
// agent-runs（AgentRunTimeline 兜底）
// ===========================================================================
route('GET', '/agent-runs/:runId', ({ params }) => ({
  runId: params.runId,
  tenantId: 'default',
  agentId: 'app-2',
  status: 'COMPLETED',
  definitionJson: JSON.stringify({ strategy: 'FUNCTION_CALLING' }),
  requestJson: JSON.stringify({ query: '上周各渠道销售额对比' }),
  responseJson: JSON.stringify({ text: '已完成对比分析：线上渠道环比 +12.4%…' }),
  version: 3,
  startedAt: new Date(Date.now() - 42_000).toISOString(),
  finishedAt: new Date(Date.now() - 8_000).toISOString(),
  createdAt: new Date(Date.now() - 42_000).toISOString(),
}))

route('GET', '/agent-runs/:runId/events', ({ params }) => {
  const base = Date.now() - 40_000
  const ev = (sequence: number, type: string, payload: any) => ({
    eventId: `${params.runId}-${sequence}`,
    runId: params.runId,
    sequence,
    type,
    payloadJson: JSON.stringify(payload),
    createdAt: new Date(base + sequence * 4000).toISOString(),
  })
  return [
    ev(1, 'RUN_STARTED', {}),
    ev(2, 'TOOL_STARTED', { tool: 'sql_query', input: { sql: 'SELECT …' } }),
    ev(3, 'TOOL_SUCCEEDED', { tool: 'sql_query', rows: 12 }),
    ev(4, 'RUN_COMPLETED', { text: '已完成对比分析' }),
  ]
})

route('POST', '/agent-runs/:runId/resume', ({ params }) => ({ runId: params.runId, status: 'COMPLETED', text: '已批准并继续执行' }))
route('POST', '/agent-runs/:runId/cancel', ({ params }) => ({ runId: params.runId, status: 'CANCELLED', version: 4 }))

// ===========================================================================
// LLMOps（总览页）
// ===========================================================================
route('GET', '/llmops/total', ({ db }) => db.llmopsTotal)
route('GET', '/llmops/trend', ({ db }) => db.llmopsTrend)
route('GET', '/llmops/recent', ({ db, query }) =>
  db.llmopsRecent.slice(0, Number(query.get('limit') ?? 50)),
)

// ===========================================================================
// triggers
// ===========================================================================
route('GET', '/triggers', ({ db }) => db.triggers)

route('GET', '/triggers/page', ({ db, query }) => {
  const page = Number(query.get('page') ?? 1)
  const pageSize = Number(query.get('pageSize') ?? 20)
  const keyword = (query.get('keyword') ?? '').toLowerCase()
  const all = db.triggers.filter((t) => !keyword || t.name.toLowerCase().includes(keyword))
  return { records: all.slice((page - 1) * pageSize, page * pageSize), total: all.length, page, pageSize }
})

route('GET', '/triggers/:id', ({ db, params }) => db.triggers.find((t) => t.id === params.id) ?? null)

function triggerFromBody(body: any): any {
  return {
    id: uid('tg'),
    tenantId: 'default',
    name: body?.name ?? '未命名触发器',
    type: body?.type ?? 'MANUAL',
    targetType: body?.targetType ?? 'AGENT',
    targetId: body?.targetId ?? '',
    config: body?.config ?? {},
    configJson: JSON.stringify(body?.config ?? {}),
    enabled: body?.enabled ?? true,
    fireCount: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

route('POST', '/triggers', ({ db, body }) => {
  const t = triggerFromBody(body)
  db.triggers.push(t)
  return t
})

route('POST', '/triggers/from-json', ({ db, body }) => {
  let parsed = body
  if (typeof body === 'string') {
    try { parsed = JSON.parse(body) } catch { parsed = {} }
  }
  const t = triggerFromBody(parsed)
  db.triggers.push(t)
  return t
})

route('DELETE', '/triggers/:id', ({ db, params }) => {
  const i = db.triggers.findIndex((t) => t.id === params.id)
  if (i >= 0) db.triggers.splice(i, 1)
  return null
})

route('PUT', '/triggers/:id/enabled', ({ db, params, query }) => {
  const t = db.triggers.find((x) => x.id === params.id)
  if (t) t.enabled = query.get('enabled') !== 'false'
  return null
})

route('POST', '/triggers/:id/fire', ({ db, params, body }) => {
  const t = db.triggers.find((x) => x.id === params.id)
  if (t) {
    t.fireCount = (t.fireCount ?? 0) + 1
    t.lastFireAt = new Date().toISOString()
  }
  const inv = { id: uid('inv'), triggerId: params.id, source: 'MANUAL', status: 'COMPLETED', payloadJson: JSON.stringify(body ?? {}), outputsJson: '{"reply":"mock 已执行"}', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ;(db.invocations[params.id] ??= []).unshift(inv)
  return { ok: true, invocationId: inv.id, output: 'mock 已执行' }
})

route('GET', '/triggers/:id/invocations', ({ db, params }) => db.invocations[params.id] ?? [])

route('POST', '/invocations/:invId/replay', ({ db, params }) => {
  for (const list of Object.values(db.invocations)) {
    const inv = list.find((x) => x.id === params.invId)
    if (inv) {
      const copy = { ...inv, id: uid('inv'), status: 'COMPLETED', replayOf: inv.id, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      list.unshift(copy)
      return copy
    }
  }
  return null
})

// ===========================================================================
// tools / skills / mcp
// ===========================================================================
route('GET', '/tools', ({ db }) => db.tools)

route('POST', '/tools/:name/invoke', ({ params, body }) => {
  const outputs: Record<string, any> = {
    sql_query: { columns: ['date', 'channel', 'amount'], rows: [['2026-09-26', '线上', 128_430], ['2026-09-26', '门店', 74_210]], rowCount: 2 },
    chart_render: { chartUrl: 'data:image/svg+xml;base64,mock', type: body?.chartType ?? 'line' },
    web_search: { results: [{ title: `${body?.query ?? 'query'} 的最新结果`, url: 'https://example.com/1', snippet: '…' }] },
    weather_query: { city: body?.city ?? '杭州', weather: '多云', temperature: 26, forecast: ['晴 28°', '小雨 24°', '多云 27°'] },
    amap_geocode: { location: '120.026,30.279', formattedAddress: body?.address ?? '' },
  }
  return outputs[params.name] ?? { ok: true, args: body ?? {}, note: 'mock 工具执行成功' }
})

route('GET', '/tools/custom', ({ db }) => db.tools.filter((t) => t.custom))

route('POST', '/tools/custom', ({ db, body }) => {
  const t = { name: body?.name ?? uid('tool'), label: body?.name, description: body?.description ?? '', inputSchema: body?.inputSchema ?? '{}', source: 'CUSTOM', custom: true, method: body?.method ?? 'GET', url: body?.url ?? '', enabled: body?.enabled ?? true, category: '自定义' }
  db.tools.push(t)
  return t
})

route('DELETE', '/tools/custom/:name', ({ db, params }) => {
  const i = db.tools.findIndex((t) => t.name === params.name && t.custom)
  if (i >= 0) db.tools.splice(i, 1)
  return true
})

route('GET', '/skills', ({ db, query }) => {
  const status = query.get('status')
  return status ? db.skills.filter((s) => s.status === status) : db.skills
})

route('GET', '/skills/:id', ({ db, params }) => db.skills.find((s) => s.id === params.id) ?? null)

route('POST', '/skills', ({ db, body }) => {
  const s = { id: uid('sk'), tenantId: 'default', code: body?.code ?? uid('code'), name: body?.name ?? '未命名技能', description: body?.description ?? '', instructions: body?.instructions ?? '', status: body?.status ?? 'DRAFT', toolNamesJson: JSON.stringify(body?.toolNames ?? []), version: 1, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  db.skills.push(s)
  return s
})

route('PUT', '/skills/:id', ({ db, params, body }) => {
  const s = db.skills.find((x) => x.id === params.id)
  if (s) {
    Object.assign(s, body, { toolNamesJson: body?.toolNames ? JSON.stringify(body.toolNames) : s.toolNamesJson, updatedAt: new Date().toISOString() })
    s.version = (s.version ?? 1) + 1
  }
  return s ?? null
})

route('POST', '/skills/:id/status', ({ db, params, body }) => {
  const s = db.skills.find((x) => x.id === params.id)
  if (s) s.status = body?.status ?? s.status
  return s ?? null
})

route('DELETE', '/skills/:id', ({ db, params }) => {
  const i = db.skills.findIndex((s) => s.id === params.id)
  if (i >= 0) db.skills.splice(i, 1)
  return null
})

route('GET', '/mcp-servers', ({ db }) => db.mcpServers)

route('POST', '/mcp-servers', ({ db, body }) => {
  const s = { id: uid('mcp'), name: body?.name ?? 'unnamed', transport: body?.transport ?? 'HTTP', enabled: body?.enabled ?? true, status: 'CONNECTED', toolCount: body?.toolCount ?? 0 }
  db.mcpServers.push(s)
  return s
})

route('DELETE', '/mcp-servers/:id', ({ db, params }) => {
  const i = db.mcpServers.findIndex((s) => s.id === params.id)
  if (i >= 0) db.mcpServers.splice(i, 1)
  return null
})

route('POST', '/mcp-servers/:id/test', ({ db, params }) => {
  const s = db.mcpServers.find((x) => x.id === params.id)
  const ok = s?.name !== 'github-mcp'
  if (s) {
    s.status = ok ? 'CONNECTED' : 'ERROR'
    s.lastError = ok ? undefined : 'connect ETIMEDOUT'
  }
  return { ok, toolCount: ok ? (s?.toolCount ?? 4) : 0, latencyMs: rand(80, 600), error: ok ? undefined : 'connect ETIMEDOUT api.github.com:443' }
})

// ===========================================================================
// connectors / installations / connections / executions
// ===========================================================================
route('GET', '/connectors', ({ db }) => db.connectors)
route('POST', '/connectors/refresh', ({ db }) => db.connectors)

route('GET', '/connectors/:provider/:connectorId', ({ db, params }) =>
  db.connectors.find((c) => c.key.provider === params.provider && c.key.connectorId === params.connectorId) ?? null,
)

route('POST', '/connectors/:provider/:connectorId/actions/:action/execute', ({ params, body }) => ({
  success: true,
  data: { action: params.action, echo: body ?? {}, executedAt: new Date().toISOString() },
  content: `mock 执行 ${params.provider}/${params.connectorId}/${params.action} 成功`,
  metadata: { durationMs: rand(60, 800) },
}))

route('GET', '/connector-installations', ({ db }) => db.installations)

route('POST', '/connector-installations/synchronize', ({ db }) => {
  for (const c of db.connectors) {
    const exists = db.installations.some((i) => i.provider === c.key.provider && i.connectorId === c.key.connectorId)
    if (!exists) {
      db.installations.push({ id: uid('ins'), tenantId: 'default', provider: c.key.provider, connectorId: c.key.connectorId, version: c.version, source: c.source, enabled: false, trustLevel: c.trustLevel })
    }
  }
  return db.installations
})

route('POST', '/connector-installations/:id/enable', ({ db, params }) => {
  const ins = db.installations.find((x) => x.id === params.id)
  if (ins) ins.enabled = true
  return ins ?? null
})

route('POST', '/connector-installations/:id/disable', ({ db, params }) => {
  const ins = db.installations.find((x) => x.id === params.id)
  if (ins) ins.enabled = false
  return ins ?? null
})

route('GET', '/connector-connections', ({ db }) => db.connections)

route('POST', '/connector-connections', ({ db, body }) => {
  const conn = { id: body?.id ?? uid('conn'), tenantId: 'default', installationId: body?.installationId, name: body?.name ?? '新连接', status: 'ACTIVE', credentialsConfigured: Boolean(body?.credentials && Object.keys(body.credentials).length) }
  db.connections.push(conn)
  return conn
})

route('DELETE', '/connector-connections/:id', ({ db, params }) => {
  const i = db.connections.findIndex((c) => c.id === params.id)
  if (i >= 0) db.connections.splice(i, 1)
  return null
})

route('POST', '/connector-connections/:id/test', () => ({
  success: true,
  code: 'OK',
  message: `连接正常（${rand(40, 300)}ms）`,
}))

route('GET', '/connector-executions', ({ db, query }) => {
  const limit = Number(query.get('limit') ?? 50)
  const provider = query.get('provider')
  const connectorId = query.get('connectorId')
  const status = query.get('status')
  return db.executions
    .filter((e) => (!provider || e.provider === provider) && (!connectorId || e.connectorId === connectorId) && (!status || e.status === status))
    .slice(0, limit)
})

// ===========================================================================
// channels / robots / conversations / events
// ===========================================================================
route('GET', '/channels', ({ db }) => db.channels)

route('GET', '/channels/runtime-nodes', () => ({ 'runtime-node-1': ['wecom', 'dingtalk', 'web'], 'runtime-node-2': ['feishu'] }))

route('GET', '/channels/:provider/:channelId/accounts', ({ db, params }) =>
  db.channelAccounts[`${params.provider}/${params.channelId}`] ?? [],
)

route('GET', '/channel-connections', ({ db, query }) => {
  const ownerId = query.get('ownerId')
  return ownerId ? db.robots.filter((r) => r.ownerId === ownerId) : [...db.robots, ...db.connections]
})

route('POST', '/channel-connections', ({ db, body }) => {
  const robot = {
    id: body?.id ?? uid('robot'),
    tenantId: 'default',
    ownerType: 'USER',
    ownerId: body?.ownerId ?? 'demo-user',
    provider: body?.provider,
    channelId: body?.channelId,
    name: body?.name ?? '新机器人',
    desiredStatus: body?.desiredStatus ?? 'RUNNING',
    runtimeStatus: 'STARTING',
    agentId: body?.agentId,
    routingPolicyVersion: 1,
    credentialsConfigured: Boolean(body?.credentials && Object.keys(body.credentials).length),
    configVersion: 1,
    createdAt: new Date().toISOString(),
  }
  db.robots.push(robot)
  setTimeout(() => (robot.runtimeStatus = robot.desiredStatus), 1500)
  return robot
})

route('GET', '/channel-connections/:id/configuration', () => ({
  credentials: {},
  config: {},
  configuredSecretFields: ['botKey'],
}))

route('POST', '/channel-connections/:id/test', ({ db, params }) => {
  const r = db.robots.find((x) => x.id === params.id)
  if (r) {
    r.lastTestedAt = new Date().toISOString()
    r.configVersion = (r.configVersion ?? 0) + 1
  }
  return r ?? { success: true }
})

route('DELETE', '/channel-connections/:id', ({ db, params }) => {
  const i = db.robots.findIndex((r) => r.id === params.id)
  if (i >= 0) db.robots.splice(i, 1)
  return null
})

route('GET', '/channel-events', ({ db, query }) => {
  const connectionId = query.get('connectionId')
  const limit = Number(query.get('limit') ?? 100)
  return db.channelEvents.filter((e) => !connectionId || e.connectionId === connectionId).slice(0, limit)
})

route('GET', '/channel-events/page', ({ db, query }) => {
  const connectionId = query.get('connectionId')
  const conversationId = query.get('conversationId')
  const items = db.channelEvents.filter(
    (e) => (!connectionId || e.connectionId === connectionId) && (!conversationId || e.conversationId === conversationId),
  )
  return { items, hasMore: false }
})

route('POST', '/channel-events/:id/retry', ({ db, params }) => {
  const e = db.channelEvents.find((x) => x.id === params.id)
  if (e) {
    e.status = 'SENT'
    e.attempts = (e.attempts ?? 0) + 1
    e.errorMessage = undefined
  }
  return e ?? null
})

route('POST', '/channel-events/:id/handoff', ({ db, params, body }) => {
  const e = db.channelEvents.find((x) => x.id === params.id)
  const conv = db.conversations.find((c) => c.conversationId === e?.conversationId)
  if (conv) {
    conv.status = 'WAITING_HUMAN'
    conv.handoffRequestedAt = new Date().toISOString()
    conv.assignmentGroup = body?.assignmentGroup
    conv.lockVersion += 1
  }
  return e ?? null
})

route('POST', '/channel-events/:id/reply', ({ db, params, body }) => {
  const src = db.channelEvents.find((x) => x.id === params.id)
  const ev = {
    id: uid('ev'),
    tenantId: 'default',
    connectionId: src?.connectionId,
    provider: src?.provider,
    channelId: src?.channelId,
    accountId: src?.accountId,
    conversationId: src?.conversationId,
    direction: 'OUTBOUND',
    content: body?.content ?? '',
    messageType: body?.messageType ?? 'TEXT',
    handled: true,
    status: 'SENT',
    replyToEventId: params.id,
    senderType: 'EMPLOYEE',
    senderActorId: 'demo-user',
    durationMs: rand(80, 400),
    eventTime: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  }
  db.channelEvents.unshift(ev)
  return ev
})

route('GET', '/channel-dead-letters', ({ db, query }) =>
  db.channelEvents.filter((e) => e.status === 'FAILED').slice(0, Number(query.get('limit') ?? 50)),
)

route('POST', '/channel-dead-letters/replay', ({ db, body }) => {
  const ids: string[] = body?.eventIds ?? []
  let eligible = 0
  for (const e of db.channelEvents) {
    if (ids.includes(e.id) && e.status === 'FAILED') {
      e.status = 'QUEUED'
      e.attempts = 0
      eligible++
    }
  }
  return { requested: ids.length, eligible, requeued: eligible }
})

route('GET', '/channel-conversations', ({ db, query }) => {
  const status = query.get('status')
  const limit = Number(query.get('limit') ?? 100)
  return db.conversations.filter((c) => !status || c.status === status).slice(0, limit)
})

route('GET', '/channel-conversations/page', ({ db }) => ({ items: db.conversations, hasMore: false }))

route('GET', '/channel-conversations/summary', ({ db }) => ({
  total: db.conversations.length,
  waitingHuman: db.conversations.filter((c) => c.status === 'WAITING_HUMAN').length,
  humanActive: db.conversations.filter((c) => c.status === 'HUMAN_ACTIVE').length,
  slaBreached: db.conversations.filter((c) => c.slaBreached).length,
  unread: db.conversations.reduce((s, c) => s + (c.unreadCount ?? 0), 0),
}))

route('GET', '/channel-conversations/:id', ({ db, params }) =>
  db.conversations.find((c) => c.id === params.id) ?? null,
)

function withLock(conv: any, expectedVersion: any, mutate: () => void) {
  if (expectedVersion !== undefined && Number(expectedVersion) !== conv.lockVersion) {
    throw Object.assign(new Error('会话版本已过期，请刷新后重试'), { status: 409 })
  }
  mutate()
  conv.lockVersion += 1
  return conv
}

route('POST', '/channel-conversations/:id/claim', ({ db, params, body }) => {
  const conv = db.conversations.find((c) => c.id === params.id)
  if (!conv) return null
  return withLock(conv, body?.expectedVersion, () => {
    conv.status = 'HUMAN_ACTIVE'
    conv.assigneeId = 'demo-user'
    conv.assigneeName = '演示用户'
    conv.assignmentGroup = body?.assignmentGroup
    conv.claimedAt = new Date().toISOString()
  })
})

route('POST', '/channel-conversations/:id/resume-bot', ({ db, params, body }) => {
  const conv = db.conversations.find((c) => c.id === params.id)
  if (!conv) return null
  return withLock(conv, body?.expectedVersion, () => {
    conv.status = 'BOT_ACTIVE'
    conv.agentPaused = false
    conv.assigneeId = undefined
    conv.assigneeName = undefined
  })
})

route('POST', '/channel-conversations/:id/close', ({ db, params, body }) => {
  const conv = db.conversations.find((c) => c.id === params.id)
  if (!conv) return null
  return withLock(conv, body?.expectedVersion, () => {
    conv.status = 'CLOSED'
    conv.closedAt = new Date().toISOString()
    conv.unreadCount = 0
  })
})

route('POST', '/channel-conversations/:id/notes', ({ db, params, body }) => {
  const conv = db.conversations.find((c) => c.id === params.id)
  const ev = {
    id: uid('ev'),
    tenantId: 'default',
    connectionId: conv?.connectionId,
    provider: conv?.provider ?? 'wecom',
    channelId: conv?.channelId ?? 'group-bot',
    accountId: conv?.accountId,
    conversationId: conv?.conversationId,
    direction: 'INTERNAL',
    content: body?.content ?? '',
    messageType: 'TEXT',
    handled: true,
    status: 'PROCESSED',
    senderType: 'EMPLOYEE',
    senderActorId: 'demo-user',
    eventTime: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  }
  db.channelEvents.unshift(ev)
  return ev
})

route('GET', '/channel-audits', ({ db, query }) =>
  db.audits.slice(0, Number(query.get('limit') ?? 50)),
)

route('GET', '/tenant-agent-bindings/current', ({ db }) => db.tenantAgentBinding)

route('PUT', '/tenant-agent-bindings/current', ({ db, body }) => {
  db.tenantAgentBinding = {
    id: uid('tab'),
    tenantId: 'default',
    defaultAgentId: body?.defaultAgentId,
    defaultAgentVersionId: body?.defaultAgentVersionId,
    fallbackAgentId: body?.fallbackAgentId,
    fallbackAgentVersionId: body?.fallbackAgentVersionId,
    routingPolicyVersion: (db.tenantAgentBinding?.routingPolicyVersion ?? 0) + 1,
    enabled: body?.enabled ?? true,
  }
  return db.tenantAgentBinding
})

route('GET', '/employee-agent-bindings/:employeeId', () => null)

route('PUT', '/employee-agent-bindings/:employeeId', ({ params, body }) => ({
  id: uid('eab'),
  tenantId: 'default',
  employeeId: params.employeeId,
  agentId: body?.agentId,
  agentVersionId: body?.agentVersionId,
  routingPolicyVersion: 1,
  enabled: true,
}))

route('GET', '/channel-identities', () => [])
route('PUT', '/channel-identities', ({ body }) => body ?? [])
