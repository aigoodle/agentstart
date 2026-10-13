<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 总览驾驶舱 —— 自制 HUD 监控大屏首页。
 * 直接 fetch mock/后端接口（信封 { code:'ok', data }），聚合 LLMOps 指标 + 各实体计数。
 */

const router = useRouter()

/** define 注入的编译期常量；模板无法直接访问全局，需经 setup 暴露 */
const isMock = __USE_MOCK__

interface LlmTotal {
  calls: number
  errors: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  costMicros: number
  avgLatencyMs: number
}
interface TrendBucket {
  bucketStart: string
  calls: number
  errors: number
  totalTokens: number
  costMicros: number
  avgLatencyMs: number
}
interface RecentCall {
  id: string
  provider: string
  model: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  costMicros: number
  latencyMs: number
  success: boolean
  createdAt: string
}

const ENTITY_SOURCES = [
  { label: '智能体应用', path: '/agents', route: '/apps' },
  { label: '知识库', path: '/datasets', route: '/knowledge' },
  { label: '模型', path: '/models', route: '/model' },
  { label: '工作流', path: '/workflows', route: '/workflow' },
  { label: '触发器', path: '/triggers', route: '/triggers' },
  { label: 'MCP 服务', path: '/mcp-servers', route: '/mcp' },
  { label: '技能', path: '/skills', route: '/skills' },
  { label: '工具', path: '/tools', route: '/tools' },
  { label: '渠道定义', path: '/channels', route: '/channels' },
  { label: '机器人连接', path: '/channel-connections', route: '/robots' },
]

const total = ref<LlmTotal | null>(null)
const trend = ref<TrendBucket[]>([])
const recent = ref<RecentCall[]>([])
const counts = ref<{ key: string; label: string; value: number; path: string }[]>([])
const loadedAt = ref('')
const loadError = ref('')

async function api<T>(path: string): Promise<T> {
  const res = await fetch(`/api/agent-start${path}`)
  const json = (await res.json()) as { code: string; message?: string; data: T }
  if (json.code !== 'ok') throw new Error(json.message ?? json.code)
  return json.data
}

onMounted(async () => {
  try {
    const [t, tr, rc, ...entityLists] = await Promise.all([
      api<LlmTotal>('/llmops/total'),
      api<TrendBucket[]>('/llmops/trend'),
      api<RecentCall[]>('/llmops/recent?limit=8'),
      ...ENTITY_SOURCES.map((s) => api<unknown[]>(s.path).catch(() => [] as unknown[])),
    ])
    total.value = t
    trend.value = Array.isArray(tr) ? tr : []
    recent.value = Array.isArray(rc) ? rc : []
    counts.value = ENTITY_SOURCES.map((s, i) => ({
      key: `${s.route}-${s.label}`,
      label: s.label,
      value: Array.isArray(entityLists[i]) ? entityLists[i].length : 0,
      path: s.route,
    }))
    loadedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  }
})

const maxCalls = computed(() => Math.max(1, ...trend.value.map((b) => b.calls)))
const errorRate = computed(() => {
  const t = total.value
  if (!t || t.calls === 0) return '0.00'
  return ((t.errors / t.calls) * 100).toFixed(2)
})

function fmtTokens(n: number): string {
  if (n >= 1e8) return `${(n / 1e8).toFixed(2)} 亿`
  if (n >= 1e4) return `${(n / 1e4).toFixed(1)} 万`
  return String(n)
}
function fmtCost(micros: number): string {
  return `¥${(micros / 1e6).toFixed(2)}`
}
function fmtMs(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(2)} s` : `${Math.round(n)} ms`
}
function fmtInt(n: number): string {
  return n.toLocaleString('zh-CN')
}
function barHeight(b: TrendBucket): string {
  return `${Math.max(2, (b.calls / maxCalls.value) * 100)}%`
}
function hourOf(bucketStart: string): string {
  const d = new Date(bucketStart)
  return Number.isNaN(d.getTime()) ? '' : `${String(d.getHours()).padStart(2, '0')}:00`
}
function timeOf(createdAt: string): string {
  const d = new Date(createdAt)
  return Number.isNaN(d.getTime()) ? createdAt : d.toLocaleTimeString('zh-CN', { hour12: false })
}
function barTip(b: TrendBucket): string {
  return `${hourOf(b.bucketStart)} · 调用 ${fmtInt(b.calls)} · 错误 ${b.errors} · ${fmtTokens(b.totalTokens)} tokens · ${fmtMs(b.avgLatencyMs)}`
}
</script>

<template>
  <div class="view-page ov">
    <div v-if="loadError" class="ov-alert">
      ⚠ 数据加载失败：{{ loadError }}（请确认 mock 中间件已启用或后端 /api 可达）
    </div>

    <section class="ov-kpis">
      <div class="hud-panel ov-kpi">
        <span class="hud-label">总调用次数</span>
        <span class="ov-kpi-num hud-mono">{{ total ? fmtInt(total.calls) : '——' }}</span>
        <span class="ov-kpi-sub">近 24 小时全部模型请求</span>
      </div>
      <div class="hud-panel ov-kpi">
        <span class="hud-label">错误次数</span>
        <span class="ov-kpi-num hud-mono" :class="{ 'ov-kpi-num--danger': (total?.errors ?? 0) > 0 }">
          {{ total ? fmtInt(total.errors) : '——' }}
        </span>
        <span class="ov-kpi-sub">错误率 {{ errorRate }}%</span>
      </div>
      <div class="hud-panel ov-kpi">
        <span class="hud-label">Token 消耗</span>
        <span class="ov-kpi-num hud-mono">{{ total ? fmtTokens(total.totalTokens) : '——' }}</span>
        <span class="ov-kpi-sub">
          提示 {{ total ? fmtTokens(total.promptTokens) : '—' }} · 补全 {{ total ? fmtTokens(total.completionTokens) : '—' }}
        </span>
      </div>
      <div class="hud-panel ov-kpi">
        <span class="hud-label">平均延迟</span>
        <span class="ov-kpi-num hud-mono">{{ total ? fmtMs(total.avgLatencyMs) : '——' }}</span>
        <span class="ov-kpi-sub">端到端首包平均耗时</span>
      </div>
      <div class="hud-panel ov-kpi">
        <span class="hud-label">累计成本</span>
        <span class="ov-kpi-num hud-mono">{{ total ? fmtCost(total.costMicros) : '——' }}</span>
        <span class="ov-kpi-sub">按供应商定价折算</span>
      </div>
    </section>

    <section class="ov-main">
      <div class="hud-panel ov-trend">
        <header class="ov-head">
          <h2 class="hud-title">调用趋势 · 近 24 小时</h2>
          <div class="ov-legend">
            <span class="ov-legend-item"><i class="ov-dot ov-dot--cyan" />调用量</span>
            <span class="ov-legend-item"><i class="ov-dot ov-dot--red" />错误量</span>
          </div>
        </header>
        <div class="ov-chart">
          <div v-for="(b, i) in trend" :key="b.bucketStart" class="ov-bar-wrap" :title="barTip(b)">
            <div class="ov-bar" :style="{ height: barHeight(b) }">
              <div
                v-if="b.errors > 0"
                class="ov-bar-err"
                :style="{ height: `${Math.min(100, (b.errors / Math.max(1, b.calls)) * 100)}%` }"
              />
            </div>
            <span v-if="i % 4 === 0" class="ov-bar-label hud-mono">{{ hourOf(b.bucketStart) }}</span>
          </div>
          <div v-if="trend.length === 0" class="ov-chart-empty hud-label">暂无趋势数据</div>
        </div>
      </div>

      <aside class="hud-panel ov-status">
        <header class="ov-head">
          <h2 class="hud-title">系统状态</h2>
        </header>
        <ul class="ov-status-list">
          <li>
            <span class="hud-label">数据源</span>
            <span class="ov-status-val">
              <i class="ov-dot" :class="isMock ? 'ov-dot--green' : 'ov-dot--cyan'" />
              {{ isMock ? 'MOCK 中间件' : 'LIVE 后端代理' }}
            </span>
          </li>
          <li>
            <span class="hud-label">API 前缀</span>
            <span class="ov-status-val hud-mono">/api/agent-start</span>
          </li>
          <li>
            <span class="hud-label">组件库</span>
            <span class="ov-status-val hud-mono">vue-agent-start</span>
          </li>
          <li>
            <span class="hud-label">主题</span>
            <span class="ov-status-val">暗黑 HUD · dark</span>
          </li>
          <li>
            <span class="hud-label">快照时间</span>
            <span class="ov-status-val hud-mono">{{ loadedAt || '加载中…' }}</span>
          </li>
          <li>
            <span class="hud-label">链路状态</span>
            <span class="ov-status-val">
              <i class="ov-dot" :class="loadError ? 'ov-dot--red' : 'ov-dot--green'" />
              {{ loadError ? '异常' : '正常' }}
            </span>
          </li>
        </ul>
      </aside>
    </section>

    <section class="ov-entities">
      <button
        v-for="c in counts"
        :key="c.key"
        type="button"
        class="hud-panel ov-entity"
        @click="router.push(c.path)"
      >
        <span class="ov-entity-num hud-mono">{{ c.value }}</span>
        <span class="hud-label">{{ c.label }}</span>
        <span class="ov-entity-go">进入 ›</span>
      </button>
    </section>

    <section class="hud-panel ov-recent">
      <header class="ov-head">
        <h2 class="hud-title">最近模型调用</h2>
        <span class="hud-label">LLMOps · 实时流水</span>
      </header>
      <table class="ov-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>供应商</th>
            <th>模型</th>
            <th class="ov-num">Tokens</th>
            <th class="ov-num">延迟</th>
            <th class="ov-num">成本</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in recent" :key="r.id">
            <td class="hud-mono ov-dim">{{ timeOf(r.createdAt) }}</td>
            <td>{{ r.provider }}</td>
            <td class="hud-mono">{{ r.model }}</td>
            <td class="ov-num hud-mono">{{ fmtInt(r.totalTokens) }}</td>
            <td class="ov-num hud-mono">{{ fmtMs(r.latencyMs) }}</td>
            <td class="ov-num hud-mono">{{ fmtCost(r.costMicros) }}</td>
            <td>
              <span class="ov-tag" :class="r.success ? 'ov-tag--ok' : 'ov-tag--err'">
                {{ r.success ? '正常' : '失败' }}
              </span>
            </td>
          </tr>
          <tr v-if="recent.length === 0">
            <td colspan="7" class="ov-empty hud-label">暂无调用记录</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.ov {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------- 告警条 ---------- */
.ov-alert {
  padding: 10px 14px;
  font-size: 13px;
  color: #ff9d9d;
  background: rgba(255, 68, 68, 0.08);
  border: 1px solid rgba(255, 68, 68, 0.5);
  border-radius: 10px;
  box-shadow: 0 0 14px rgba(255, 68, 68, 0.25);
}

/* ---------- KPI 行 ---------- */
.ov-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.ov-kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
}

.ov-kpi-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--hud-primary-bright);
  text-shadow: 0 0 12px rgba(0, 224, 255, 0.55);
}
.ov-kpi-num--danger {
  color: var(--hud-danger);
  text-shadow: 0 0 12px rgba(255, 68, 68, 0.55);
}

.ov-kpi-sub {
  font-size: 11px;
  color: var(--hud-text-muted);
}

/* ---------- 主区：趋势 + 状态 ---------- */
.ov-main {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) minmax(280px, 1fr);
  gap: 12px;
}

.ov-trend,
.ov-status,
.ov-recent {
  padding: 14px 16px;
}

.ov-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.ov-legend {
  display: flex;
  gap: 14px;
}
.ov-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--hud-text-muted);
}

.ov-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.ov-dot--cyan {
  background: var(--hud-primary);
  box-shadow: 0 0 6px rgba(0, 204, 255, 0.8);
}
.ov-dot--green {
  background: var(--hud-success);
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.8);
}
.ov-dot--red {
  background: var(--hud-danger);
  box-shadow: 0 0 6px rgba(255, 68, 68, 0.8);
}

/* ---------- 柱状趋势图 ---------- */
.ov-chart {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 210px;
  padding-bottom: 22px;
}

.ov-bar-wrap {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.ov-bar {
  position: relative;
  width: 100%;
  max-width: 26px;
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #35d9ff 0%, rgba(0, 204, 255, 0.22) 100%);
  box-shadow: 0 0 8px rgba(0, 204, 255, 0.35);
  transition: height 0.5s ease, box-shadow 0.2s;
  overflow: hidden;
}
.ov-bar-wrap:hover .ov-bar {
  box-shadow: 0 0 14px rgba(0, 224, 255, 0.7);
}

.ov-bar-err {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: linear-gradient(180deg, #ff6b6b 0%, rgba(255, 68, 68, 0.35) 100%);
}

.ov-bar-label {
  position: absolute;
  bottom: -20px;
  font-size: 10px;
  color: var(--hud-text-faint);
  white-space: nowrap;
}

.ov-chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.7;
}

/* ---------- 系统状态 ---------- */
.ov-status-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.ov-status-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid var(--hud-border-faint);
}
.ov-status-list li:last-child {
  border-bottom: none;
}

.ov-status-val {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--hud-text);
}

/* ---------- 实体计数网格 ---------- */
.ov-entities {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.ov-entity {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.ov-entity:hover {
  transform: translateY(-2px);
  border-color: var(--hud-border-strong);
  box-shadow: var(--hud-glow-strong);
}
.ov-entity:hover .ov-entity-go {
  opacity: 1;
}

.ov-entity-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--hud-primary-bright);
  text-shadow: 0 0 10px rgba(0, 224, 255, 0.5);
}

.ov-entity-go {
  position: absolute;
  right: 14px;
  bottom: 12px;
  font-size: 11px;
  color: var(--hud-primary);
  opacity: 0;
  transition: opacity 0.2s;
}

/* ---------- 最近调用表 ---------- */
.ov-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ov-table th {
  padding: 8px 12px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #8fc7e8;
  background: rgba(2, 16, 34, 0.85);
  border-bottom: 1px solid var(--hud-border);
}
.ov-table th:first-child {
  border-radius: 8px 0 0 8px;
}
.ov-table th:last-child {
  border-radius: 0 8px 8px 0;
}
.ov-table td {
  padding: 8px 12px;
  color: var(--hud-text);
  border-bottom: 1px solid var(--hud-border-faint);
}
.ov-table tbody tr {
  transition: background 0.15s;
}
.ov-table tbody tr:hover {
  background: rgba(0, 204, 255, 0.05);
}
.ov-num {
  text-align: right;
}
.ov-dim {
  color: var(--hud-text-muted);
}

.ov-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 11px;
  border-radius: 999px;
}
.ov-tag--ok {
  color: #4ade80;
  background: rgba(34, 197, 94, 0.12);
  border: 1px solid rgba(34, 197, 94, 0.4);
}
.ov-tag--err {
  color: #ff6b6b;
  background: rgba(255, 68, 68, 0.1);
  border: 1px solid rgba(255, 68, 68, 0.45);
  text-shadow: 0 0 8px rgba(255, 68, 68, 0.6);
}

.ov-empty {
  padding: 26px 0;
  text-align: center;
  opacity: 0.7;
}

/* ---------- 窄屏兜底 ---------- */
@media (max-width: 1280px) {
  .ov-kpis,
  .ov-entities {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .ov-main {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
