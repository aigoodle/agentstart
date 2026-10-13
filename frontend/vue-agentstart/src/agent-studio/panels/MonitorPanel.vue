<script setup lang="ts">
/**
 * MonitorPanel — content for {@code AppDesignDrawer}'s "监测" tab.
 *
 * All backend I/O goes through {@link AppStudioApi} — the host supplies
 * {@code fetchAppMetrics}, {@code fetchLlmUsage}, {@code fetchRecentLlmCalls}
 * and {@code fetchLlmTrend}; the trend is independent from the recent-calls table.
 *
 * Layout mirrors the Dify workspace/monitor stub:
 *   ┌───── 应用级指标 ──────────────┐
 *   │  会话数  消息数  平均互动  最近活跃  │
 *   └───────────────────────────────┘
 *   ┌───── 全局 LLM 用量 ────────────┐
 *   │  调用数  Tokens  费用  平均延迟 │
 *   └───────────────────────────────┘
 *   ┌───── 最近 10 条 LLM 调用 ──────┐
 *   │  Table: model · tokens · cost │
 *   └───────────────────────────────┘
 */
import { computed, markRaw, onMounted, ref, watch } from 'vue';

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
import { Button, Empty, Segmented, Skeleton, Table, Tag, message } from '../../ui';

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

const callChartOptions = [
  { label: '近 1 小时', value: 'HOUR' },
  { label: '近 24 小时', value: 'DAY' },
  { label: '近 7 天', value: 'WEEK' },
];

const callChart = computed(() => {
  return {
    series: callTrend.value.map((point) => point.calls),
    labels: callTrend.value.map((point) => fmtChartTime(point.bucketStart)),
  };
});

const trendDescription = computed(() => ({
  HOUR: '每 10 分钟一个节点，共 6 个时间桶',
  DAY: '每 1 小时一个节点，共 24 个时间桶',
  WEEK: '每天一个节点，共 7 个时间桶',
})[callChartRange.value]);

async function loadTrend() {
  if (!props.api.fetchLlmTrend) {
    callTrend.value = [];
    return;
  }
  trendLoading.value = true;
  try {
    callTrend.value = await props.api.fetchLlmTrend(callChartRange.value);
  } catch (e: any) {
    callTrend.value = [];
    message.error(e?.message ?? '调用趋势加载失败');
  } finally {
    trendLoading.value = false;
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
  { title: '费用', key: 'costMicros', width: 90, align: 'right' as const },
  { title: '延迟', key: 'latencyMs', width: 80, align: 'right' as const },
  { title: '状态', key: 'success', width: 70, align: 'center' as const },
  { title: '时间', key: 'createdAt', width: 150, align: 'right' as const },
];

async function reload() {
  if (!props.app?.id) return;
  loading.value = true;
  const jobs: Promise<unknown>[] = [];
  if (props.api.fetchAppMetrics) {
    jobs.push(
      props.api
        .fetchAppMetrics(props.app.id)
        .then((r) => (appMetrics.value = r))
        .catch(() => (appMetrics.value = null)),
    );
  }
  if (props.api.fetchLlmUsage) {
    jobs.push(
      props.api
        .fetchLlmUsage()
        .then((r) => (totalStats.value = r))
        .catch(() => (totalStats.value = null)),
    );
  }
  if (props.api.fetchRecentLlmCalls) {
    jobs.push(
      props.api
        .fetchRecentLlmCalls(10)
        .then((r) => (recentCalls.value = r))
        .catch(() => (recentCalls.value = [])),
    );
  }
  jobs.push(loadTrend());
  try {
    await Promise.all(jobs);
  } catch (e: any) {
    message.error(e?.message ?? '加载失败');
  } finally {
    loading.value = false;
  }
}

watch(() => props.app?.id, reload);
watch(callChartRange, loadTrend);
onMounted(reload);

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
  if (!micros) return '¥0.00';
  const v = micros / 1_000_000;
  return `¥${v.toFixed(v < 1 ? 4 : 2)}`;
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

const globalTiles = computed(() => {
  const t = totalStats.value;
  return [
    {
      label: 'LLM 调用数',
      value: fmtNumber(t?.calls),
      sub: t ? `失败 ${t.errors}` : '',
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
      label: '费用消耗',
      value: fmtCost(t?.costMicros),
      sub: '按配置的模型定价累计',
      icon: markRaw(DollarOutlined),
      palette: PALETTE.rose,
    },
    {
      label: '平均延迟',
      value: fmtLatency(t?.avgLatencyMs),
      sub: '所有 LLM 调用',
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
          应用级指标 + 全局 LLM 用量。数据源由宿主通过 <code>AppStudioApi</code> 注入。
        </div>
      </div>
      <Button size="small" @click="reload">
        <template #icon>
          <ReloadOutlined />
        </template>
        刷新
      </Button>
    </div>

    <!-- Per-app tiles -->
    <section class="monitor-section">
      <div class="monitor-section-title">
        <span class="monitor-section-dot" />
        {{ app?.name || '应用' }} · 应用级指标
      </div>
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
              <template v-else>{{ t.value }}</template>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Global LLM tiles -->
    <section class="monitor-section">
      <div class="monitor-section-title">
        <span class="monitor-section-dot" />
        全局 LLM 用量
      </div>
      <div class="monitor-tiles">
        <div
          v-for="t in globalTiles"
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
              <template v-else>{{ t.value }}</template>
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
            调用曲线
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
        <Skeleton v-if="trendLoading" active :paragraph="{ rows: 5 }" />
        <SparkChart
          v-else
          :series="callChart.series"
          :labels="callChart.labels"
          color="#6366f1"
          :height="230"
        />
      </div>

      <div class="monitor-call-card monitor-call-table-card">
        <div class="monitor-section-title">
          <span class="monitor-section-dot" />
          最近 10 条 LLM 调用
        </div>
        <Table
          class="monitor-table"
          :columns="callColumns"
          :data-source="recentCalls"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 720, y: 260 }"
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
              <Tag :color="record.success ? 'green' : 'red'" class="mono-tag">
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
        <Empty
          v-if="!loading && recentCalls.length === 0"
          description="尚无 LLM 调用记录 —— 让应用跑一次对话就会出现"
          class="monitor-empty"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.monitor {
  padding: 16px 24px 24px;
  background: #f8fafc;
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
  color: #0f172a;
}
.monitor-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}
.monitor-sub code {
  padding: 1px 4px;
  background: #eef2ff;
  color: #4338ca;
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
  color: #334155;
  margin-bottom: 10px;
}
.monitor-section-dot {
  width: 4px;
  height: 14px;
  background: #6366f1;
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
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef1f5;
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
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
  border-color: #e2e8f0;
}
.monitor-tile-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--tile-bg, #eef2ff);
  color: var(--tile-fg, #4f46e5);
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
  color: #64748b;
  letter-spacing: 0.02em;
}
.monitor-tile-value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  min-height: 28px;
  font-variant-numeric: tabular-nums;
  word-break: keep-all;
}
.monitor-tile-sub {
  font-size: 11px;
  color: #94a3b8;
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
  background: #fff;
  border: 1px solid #eef1f5;
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
  color: #94a3b8;
  font-size: 11px;
}
.monitor-call-table-card {
  overflow: hidden;
}
.monitor-call-table-card .monitor-table {
  margin-top: 10px;
}

/* ── Recent calls table ─────────────────────────────────────────────── */
.monitor-table :deep(.ant-table-thead > tr > th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  white-space: nowrap;
}
.monitor-table :deep(.ant-table-tbody > tr > td) {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.monitor-table :deep(.ant-table-tbody > tr > td.ant-table-cell-ellipsis) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.monitor-time {
  color: #475569;
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
</style>
