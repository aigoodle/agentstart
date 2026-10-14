<script setup lang="ts">
/**
 * MonitorPanel — content for {@code AppDesignDrawer}'s "监测" tab.
 *
 * All backend I/O goes through {@link AppStudioApi} — the host supplies
 * {@code fetchAppMetrics}, {@code fetchAppLlmUsage}, {@code fetchAppRecentLlmCalls}
 * and {@code fetchAppLlmTrend}; the trend is independent from the recent-calls table.
 *
 * Layout mirrors the Dify workspace/monitor stub:
 *   ┌───── 应用级指标 ──────────────┐
 *   │  会话数  消息数  平均互动  最近活跃  │
 *   └───────────────────────────────┘
 *   ┌───── 当前应用模型用量 ────────────┐
 *   │  调用数  Tokens  费用  平均延迟 │
 *   └───────────────────────────────┘
 *   ┌───── 当前应用最近 10 条模型调用 ──────┐
 *   │  Table: model · tokens · cost │
 *   └───────────────────────────────┘
 */
import { computed, markRaw, onBeforeUnmount, ref, watch } from 'vue';

import {
  ClockCircleOutlined,
  CommentOutlined,
  DollarOutlined,
  ExperimentOutlined,
  FieldTimeOutlined,
  MessageOutlined,
  ReloadOutlined,
  SwapOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue';
import { Button, Empty, Segmented, Skeleton, Table, Tag } from '../../ui';

import SparkChart from '../components/SparkChart.vue';

import type {
  AppStudioApi,
  StudioAppMetrics,
  StudioLlmCallRecord,
  StudioLlmTrendPoint,
  StudioLlmTrendRange,
  StudioLlmUsageStats,
} from '../api';

interface Props {
  app?: { id: string; name: string } | null;
  api: AppStudioApi;
}
const props = defineProps<Props>();

const appMetrics = ref<StudioAppMetrics | null>(null);
const totalStats = ref<StudioLlmUsageStats | null>(null);
const recentCalls = ref<StudioLlmCallRecord[]>([]);
const loading = ref(false);
const trendLoading = ref(false);
const callChartRange = ref<StudioLlmTrendRange>('HOUR');
const callTrend = ref<StudioLlmTrendPoint[]>([]);
const metricError = ref('');
const usageError = ref('');
const callsError = ref('');
const trendError = ref('');
const chartMetric = ref<'totalTokens' | 'calls' | 'avgLatencyMs'>('totalTokens');
const chartMetricOptions = [{ label: 'Tokens', value: 'totalTokens' }, { label: '调用次数', value: 'calls' }, { label: '平均延迟', value: 'avgLatencyMs' }];
let loadVersion = 0;
let trendVersion = 0;

const callChartOptions = [
  { label: '近 1 小时', value: 'HOUR' },
  { label: '近 24 小时', value: 'DAY' },
  { label: '近 7 天', value: 'WEEK' },
];

const callChart = computed(() => {
  return {
    series: callTrend.value.map((point) => point[chartMetric.value]),
    labels: callTrend.value.map((point) => fmtChartTime(point.bucketStart)),
  };
});

const trendDescription = computed(() => ({
  HOUR: '每 10 分钟一个节点，共 6 个时间桶',
  DAY: '每 1 小时一个节点，共 24 个时间桶',
  WEEK: '每天一个节点，共 7 个时间桶',
})[callChartRange.value]);

async function loadTrend() {
  const version = ++trendVersion;
  const appId = props.app?.id;
  callTrend.value = [];
  trendError.value = '';
  trendLoading.value = false;
  if (!appId) return;
  if (!props.api.fetchAppLlmTrend) {
    trendError.value = '宿主尚未接入应用级调用趋势接口';
    return;
  }
  trendLoading.value = true;
  try {
    const points = await props.api.fetchAppLlmTrend(appId, callChartRange.value);
    if (version === trendVersion && appId === props.app?.id) callTrend.value = points;
  } catch (e: any) {
    if (version === trendVersion) trendError.value = e?.message ?? '调用趋势加载失败';
  } finally {
    if (version === trendVersion) trendLoading.value = false;
  }
}

const callColumns = [
  { title: '模型', key: 'model', dataIndex: 'model', ellipsis: true },
  {
    title: '提供商',
    key: 'provider',
    dataIndex: 'provider',
    width: 110,
    ellipsis: true,
  },
  {
    title: 'Tokens',
    key: 'totalTokens',
    dataIndex: 'totalTokens',
    width: 90,
    align: 'right' as const,
  },
  { title: '输入', key: 'promptTokens', dataIndex: 'promptTokens', width: 80, align: 'right' as const },
  { title: '输出', key: 'completionTokens', dataIndex: 'completionTokens', width: 80, align: 'right' as const },
  { title: '费用', key: 'costMicros', width: 90, align: 'right' as const },
  { title: '延迟', key: 'latencyMs', width: 80, align: 'right' as const },
  { title: '状态', key: 'success', width: 70, align: 'center' as const },
  { title: '时间', key: 'createdAt', width: 150, align: 'right' as const },
];

async function reload() {
  const version = ++loadVersion;
  const appId = props.app?.id;
  appMetrics.value = null;
  totalStats.value = null;
  recentCalls.value = [];
  metricError.value = usageError.value = callsError.value = '';
  loading.value = false;
  if (!appId) { await loadTrend(); return; }
  loading.value = true;
  const current = () => version === loadVersion && appId === props.app?.id;
  const request = async <T,>(fetch: (() => Promise<T>) | undefined, apply: (value: T) => void, error: typeof metricError, label: string) => {
    if (!fetch) { error.value = `宿主尚未接入${label}接口`; return; }
    try {
      const result = await fetch();
      if (current()) apply(result);
    } catch (e: any) {
      if (current()) error.value = e?.message ?? `${label}加载失败`;
    }
  };
  await Promise.all([
    request(props.api.fetchAppMetrics ? () => props.api.fetchAppMetrics!(appId) : undefined, v => appMetrics.value = v, metricError, '应用指标'),
    request(props.api.fetchAppLlmUsage ? () => props.api.fetchAppLlmUsage!(appId) : undefined, v => totalStats.value = v, usageError, '应用级模型用量'),
    request(props.api.fetchAppRecentLlmCalls ? () => props.api.fetchAppRecentLlmCalls!(appId, 10) : undefined, v => recentCalls.value = v, callsError, '应用级调用记录'),
    loadTrend(),
  ]);
  if (current()) loading.value = false;
}

watch(() => props.app?.id, reload, { immediate: true });
watch(callChartRange, loadTrend);
onBeforeUnmount(() => { loadVersion++; trendVersion++; });

// ---- Formatters ---------------------------------------------------------
function fromNow(iso?: string): string {
  if (!iso) return '暂无数据';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86_400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86_400 * 30) return `${Math.floor(diff / 86_400)} 天前`;
  return new Date(iso).toLocaleDateString();
}
function fmtNumber(n?: number): string {
  if (n == null) return '0';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}
function fmtCost(micros?: number): string {
  const currency = totalStats.value?.currency ?? '';
  if (!micros) return `${currency ? currency + ' ' : ''}0.00`;
  const v = micros / 1_000_000;
  return `${currency ? currency + ' ' : ''}${v.toFixed(v < 1 ? 4 : 2)}`;
}
function fmtLatency(ms?: number): string {
  if (ms == null) return '—';
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)}s`;
  return `${Math.round(ms)}ms`;
}
/** Compact table timestamp — same-day shows HH:mm:ss, older shows MM-DD HH:mm. */
function fmtCallTime(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, '0');
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  if (sameDay) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fmtChartTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  if (callChartRange.value === 'WEEK') {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
  return callChartRange.value === 'DAY'
    ? `${pad(d.getHours())}:00`
    : `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * Tile palette — each metric gets a distinct hue via CSS variables so the
 * icon badge (bg + fg) and the top accent line stay in sync. Colors are
 * picked from the Tailwind 500/50 pairs so the tint stays legible on white.
 */
type TilePalette = { fg: string; bg: string };
const PALETTE = {
  indigo: { fg: '#4f46e5', bg: '#eef2ff' },
  green: { fg: '#16a34a', bg: '#f0fdf4' },
  orange: { fg: '#ea580c', bg: '#fff7ed' },
  sky: { fg: '#0284c7', bg: '#f0f9ff' },
  violet: { fg: '#7c3aed', bg: '#f5f3ff' },
  fuchsia: { fg: '#c026d3', bg: '#fdf4ff' },
  rose: { fg: '#e11d48', bg: '#fff1f2' },
  cyan: { fg: '#0891b2', bg: '#ecfeff' },
} satisfies Record<string, TilePalette>;

const appTiles = computed(() => {
  const m = appMetrics.value;
  return [
    {
      label: '全部会话数',
      value: fmtNumber(m?.totalConversations),
      icon: markRaw(MessageOutlined),
      palette: PALETTE.indigo,
    },
    {
      label: '全部消息数',
      value: fmtNumber(m?.totalMessages),
      icon: markRaw(CommentOutlined),
      palette: PALETTE.green,
    },
    {
      label: '平均会话互动数',
      value: (m?.avgInteractionsPerConversation ?? 0).toFixed(2),
      icon: markRaw(SwapOutlined),
      palette: PALETTE.orange,
    },
    {
      label: '最近活跃',
      value: fromNow(m?.lastActivityAt),
      icon: markRaw(ClockCircleOutlined),
      palette: PALETTE.sky,
    },
  ];
});

const usageTiles = computed(() => {
  const t = totalStats.value;
  return [
    {
      label: 'LLM 调用数',
      value: fmtNumber(t?.calls),
      sub: t ? `失败 ${t.errors} · 成功率 ${t.calls ? (((t.calls - t.errors) / t.calls) * 100).toFixed(1) : '—'}${t.calls ? '%' : ''}` : '',
      icon: markRaw(ThunderboltOutlined),
      palette: PALETTE.violet,
    },
    {
      label: '总 Tokens',
      value: fmtNumber(t?.totalTokens),
      sub: t
        ? `Prompt ${fmtNumber(t.promptTokens)} · Completion ${fmtNumber(t.completionTokens)}`
        : '',
      icon: markRaw(ExperimentOutlined),
      palette: PALETTE.fuchsia,
    },
    {
      label: '输入 Tokens', value: fmtNumber(t?.promptTokens), sub: '发送给模型的上下文', icon: markRaw(MessageOutlined), palette: PALETTE.indigo,
    },
    {
      label: '输出 Tokens', value: fmtNumber(t?.completionTokens), sub: '模型生成的内容', icon: markRaw(CommentOutlined), palette: PALETTE.green,
    },
    {
      label: '估算费用',
      value: fmtCost(t?.costMicros),
      sub: '按配置的模型定价累计',
      icon: markRaw(DollarOutlined),
      palette: PALETTE.rose,
    },
    {
      label: '平均延迟',
      value: fmtLatency(t?.avgLatencyMs),
      sub: '当前应用所有模型调用',
      icon: markRaw(FieldTimeOutlined),
      palette: PALETTE.cyan,
    },
  ];
});
</script>

<template>
  <div class="monitor">
    <div class="monitor-head">
      <div>
        <div class="monitor-title">监测 · 追踪应用性能</div>
        <div class="monitor-sub">
          仅统计当前应用的会话与模型调用；Token、费用和延迟包含该应用内的各轮模型调用。
        </div>
      </div>
      <Button size="small" :loading="loading" :disabled="!app?.id" @click="reload">
        <template #icon>
          <ReloadOutlined />
        </template>
        刷新
      </Button>
    </div>

    <Empty v-if="!app?.id" description="请选择应用以查看监测数据" />
    <template v-else>
    <!-- Per-app tiles -->
    <section class="monitor-section">
      <div class="monitor-section-title">
        <span class="monitor-section-dot" />
        {{ app?.name || '应用' }} · 应用级指标
      </div>
      <div v-if="metricError" class="monitor-error" role="alert">{{ metricError }}</div>
      <div class="monitor-tiles">
        <div
          v-for="t in appTiles"
          :key="t.label"
          class="monitor-tile"
          :style="{
            '--tile-fg': t.palette.fg,
            '--tile-bg': t.palette.bg,
          }"
        >
          <div class="monitor-tile-icon">
            <component :is="t.icon" />
          </div>
          <div class="monitor-tile-body">
            <div class="monitor-tile-label">{{ t.label }}</div>
            <div class="monitor-tile-value">
              <Skeleton
                v-if="loading && !appMetrics"
                active
                :paragraph="false"
                :title="{ width: '60%' }"
              />
              <template v-else>{{ appMetrics ? t.value : '—' }}</template>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Application LLM tiles -->
    <section class="monitor-section">
      <div class="monitor-section-title">
        <span class="monitor-section-dot" />
        当前应用模型用量
      </div>
      <div v-if="usageError" class="monitor-error" role="alert">{{ usageError }}</div>
      <div class="monitor-note">累计统计；费用按模型定价估算。未记录应用归属的历史调用不计入。</div>
      <div class="monitor-tiles">
        <div
          v-for="t in usageTiles"
          :key="t.label"
          class="monitor-tile"
          :style="{
            '--tile-fg': t.palette.fg,
            '--tile-bg': t.palette.bg,
          }"
        >
          <div class="monitor-tile-icon">
            <component :is="t.icon" />
          </div>
          <div class="monitor-tile-body">
            <div class="monitor-tile-label">{{ t.label }}</div>
            <div class="monitor-tile-value">
              <Skeleton
                v-if="loading && !totalStats"
                active
                :paragraph="false"
                :title="{ width: '60%' }"
              />
              <template v-else>{{ totalStats ? t.value : '—' }}</template>
            </div>
            <div v-if="t.sub" class="monitor-tile-sub">{{ t.sub }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Recent-call trend + records share one row to keep the dashboard compact. -->
    <section class="monitor-section monitor-call-grid">
      <div class="monitor-call-card">
        <div class="monitor-call-head">
          <div class="monitor-section-title">
            <span class="monitor-section-dot" />
            当前应用调用趋势
          </div>
          <Segmented
            v-model:value="callChartRange"
            size="small"
            :options="callChartOptions"
          />
        </div>
        <div class="monitor-chart-note">
          {{ trendDescription }}
        </div>
        <Segmented v-model:value="chartMetric" size="small" :options="chartMetricOptions" class="monitor-chart-metric" />
        <div v-if="trendError" class="monitor-error" role="alert">{{ trendError }}</div>
        <Skeleton v-else-if="trendLoading" active :paragraph="{ rows: 5 }" />
        <Empty v-else-if="callTrend.length === 0" description="所选时段暂无调用趋势" />
        <SparkChart
          v-else
          :series="callChart.series"
          :labels="callChart.labels"
          color="var(--as-primary, #6366f1)"
          :height="230"
        />
      </div>

      <div class="monitor-call-card monitor-call-table-card">
        <div class="monitor-section-title">
          <span class="monitor-section-dot" />
          当前应用最近 10 条模型调用
        </div>
        <div v-if="callsError" class="monitor-error" role="alert">{{ callsError }}</div>
        <Table
          v-else
          class="monitor-table"
          :columns="callColumns"
          :data-source="recentCalls"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 880, y: 260 }"
          :locale="{ emptyText: '尚无 LLM 调用记录' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'costMicros'">
              {{ fmtCost(record.costMicros) }}
            </template>
            <template v-else-if="column.key === 'latencyMs'">
              {{ fmtLatency(record.latencyMs) }}
            </template>
            <template v-else-if="column.key === 'success'">
              <Tag :color="record.success ? 'green' : 'red'" class="mono-tag" :title="record.errorType">
                {{ record.success ? '✓' : '✕' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              <span
                class="monitor-time"
                :title="record.createdAt"
              >{{ fmtCallTime(record.createdAt) }}</span>
            </template>
          </template>
        </Table>

      </div>
    </section>
    </template>
  </div>
</template>

<style scoped>
.monitor {
  color: var(--as-text);
  padding: 16px 24px 24px;
  background: var(--as-bg-layout, #f8fafc);
  min-height: 100%;
}
.monitor-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.monitor-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--as-text, #0f172a);
}
.monitor-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--as-text-secondary, #64748b);
}
.monitor-sub code {
  padding: 1px 4px;
  background: color-mix(in srgb, var(--as-primary, #1677ff) 10%, var(--as-bg, #fff));
  color: var(--as-primary, #4338ca);
  border-radius: 3px;
  font-size: 11px;
}
.monitor-section {
  margin-top: 20px;
}
.monitor-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--as-text, #334155);
  margin-bottom: 10px;
}
.monitor-section-dot {
  width: 4px;
  height: 14px;
  background: var(--as-primary, #6366f1);
  border-radius: 2px;
}
.monitor-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.monitor-tile {
  position: relative;
  padding: 16px;
  background: var(--as-bg, #fff);
  border-radius: 12px;
  border: 1px solid var(--as-border, #d9d9d9);
  min-height: 96px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  overflow: hidden;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease,
    border-color 0.15s ease;
}
.monitor-tile::before {
  /* Left accent bar — colored per tile, keeps the grid visually rhythmic. */
  content: '';
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--tile-fg, #6366f1);
}
.monitor-tile:hover {
  box-shadow: var(--as-card-shadow-hover, 0 6px 18px rgba(15, 23, 42, 0.08));
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--as-primary, #1677ff) 35%, var(--as-border, #d9d9d9));
}
.monitor-tile-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--tile-fg, #6366f1) 12%, var(--as-bg, #fff));
  color: color-mix(in srgb, var(--tile-fg, #4f46e5) 75%, var(--as-text, #0f172a));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.monitor-tile-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.monitor-tile-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--as-text-secondary, #64748b);
  letter-spacing: 0.02em;
}
.monitor-tile-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--as-text, #0f172a);
  line-height: 1.2;
  min-height: 28px;
  font-variant-numeric: tabular-nums;
  word-break: keep-all;
}
.monitor-tile-sub {
  font-size: 11px;
  color: var(--as-text-secondary, #94a3b8);
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.monitor-empty {
  padding: 24px 0;
}

/* ── Recent-call trend + records ───────────────────────────────────── */
.monitor-call-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1.2fr);
  gap: 12px;
  align-items: stretch;
}
.monitor-call-card {
  min-width: 0;
  padding: 14px;
  background: var(--as-bg, #fff);
  border: 1px solid var(--as-border, #d9d9d9);
  border-radius: 12px;
}
.monitor-call-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.monitor-call-head .monitor-section-title,
.monitor-call-table-card > .monitor-section-title {
  margin-bottom: 0;
}
.monitor-chart-note {
  margin: 6px 0 12px 10px;
  color: var(--as-text-secondary, #94a3b8);
  font-size: 11px;
}
.monitor-call-table-card {
  overflow: hidden;
}
.monitor-call-table-card .monitor-table {
  margin-top: 10px;
}

/* ── Recent calls table ─────────────────────────────────────────────── */
.monitor-table :deep(.as-table thead > tr > th) {
  background: var(--as-bg-layout, #f8fafc);
  color: var(--as-text-secondary, #475569);
  font-weight: 600;
  white-space: nowrap;
}
.monitor-table :deep(.as-table tbody > tr > td) {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.monitor-table :deep(.as-table tbody > tr > td.as-table__ellipsis) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.monitor-time {
  color: var(--as-text-secondary, #475569);
  font-size: 12px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.mono-tag {
  margin: 0;
  min-width: 28px;
  text-align: center;
}

@media (max-width: 960px) {
  .monitor-call-grid {
    grid-template-columns: 1fr;
  }
}
.monitor-call-table-card :deep(.as-table-wrap) {
  border-color: var(--as-border, #d9d9d9);
}
.monitor-table :deep(.as-table th),
.monitor-table :deep(.as-table td) {
  border-color: var(--as-border-secondary, #e5e7eb);
}
.monitor-table :deep(.as-table tbody > tr:hover > td) {
  background: var(--as-bg-layout, #f8fafc);
}
.monitor-error { padding: 10px 12px; margin-bottom: 10px; border: 1px solid color-mix(in srgb, var(--as-danger) 35%, var(--as-border)); border-radius: 8px; color: var(--as-danger); background: color-mix(in srgb, var(--as-danger) 8%, var(--as-bg)); font-size: 12px; }
.monitor-note { color: var(--as-text-secondary); font-size: 11px; margin-bottom: 10px; }
.monitor-chart-metric { margin-bottom: 12px; }
</style>
