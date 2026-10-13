<script setup lang="ts">
/**
 * SparkChart — pure-SVG mini area/line chart. Used by AgentMonitorPanel so the
 * package stays dependency-free (no ECharts / D3).
 *
 * Keeps the visual language of Dify's 监测 cards: soft area fill + 1.5px line
 * + light axis grid + labels on the X axis. Y range auto-fits (0 → 1.1×max).
 */
import { computed } from 'vue';

interface Props {
  series: number[];
  labels?: string[];
  color?: string;
  /** Fixed SVG viewport height. Width scales to fill container. */
  height?: number;
  /** Number of horizontal grid lines. */
  gridLines?: number;
}

const props = withDefaults(defineProps<Props>(), {
  color: '#22d3ee',
  height: 140,
  gridLines: 4,
  labels: () => [],
});

const VIEW_W = 480;
const PAD_X = 32;
const PAD_TOP = 8;
const PAD_BOT = 22;

const chartHeight = computed(() => Math.max(80, props.height));
const chartArea = computed(() => chartHeight.value - PAD_TOP - PAD_BOT);

const maxY = computed(() => {
  if (props.series.length === 0) return 1;
  const m = Math.max(...props.series);
  if (m <= 0) return 1;
  return m * 1.15;
});

const points = computed(() => {
  const n = props.series.length;
  if (n === 0) return [];
  const stepX = n === 1 ? 0 : (VIEW_W - PAD_X * 2) / (n - 1);
  return props.series.map((v, i) => {
    const x = PAD_X + stepX * i;
    const y = PAD_TOP + chartArea.value * (1 - v / maxY.value);
    return { x, y };
  });
});

const linePath = computed(() =>
  points.value
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`)
    .join(' '),
);

const areaPath = computed(() => {
  if (points.value.length === 0) return '';
  const first = points.value[0]!;
  const last = points.value[points.value.length - 1]!;
  const baseY = PAD_TOP + chartArea.value;
  return `${linePath.value} L ${last.x.toFixed(2)} ${baseY} L ${first.x.toFixed(2)} ${baseY} Z`;
});

const yTicks = computed(() => {
  const ticks: Array<{ y: number; value: string }> = [];
  const n = props.gridLines;
  for (let i = 0; i <= n; i++) {
    const value = (maxY.value / n) * (n - i);
    const y = PAD_TOP + (chartArea.value / n) * i;
    ticks.push({ y, value: formatTick(value) });
  }
  return ticks;
});

const xTicks = computed(() => {
  const n = props.labels.length;
  if (n === 0 || points.value.length === 0) return [];
  const step = Math.max(1, Math.floor(n / 6));
  const out: Array<{ x: number; label: string }> = [];
  for (let i = 0; i < n; i += step) {
    const p = points.value[i];
    if (!p) continue;
    out.push({ x: p.x, label: props.labels[i]! });
  }
  return out;
});

const gradientId = computed(
  () => `spark-grad-${Math.random().toString(36).slice(2, 8)}`,
);

function formatTick(v: number): string {
  if (v >= 1000) return `${(v / 1000).toFixed(v >= 10_000 ? 0 : 1)}k`;
  if (v >= 10) return v.toFixed(0);
  if (v >= 1) return v.toFixed(1);
  return v.toFixed(2);
}
</script>

<template>
  <div class="spark-wrap">
    <svg
      :viewBox="`0 0 ${VIEW_W} ${chartHeight}`"
      preserveAspectRatio="none"
      class="spark-svg"
    >
      <defs>
        <linearGradient :id="gradientId" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" :stop-color="color" stop-opacity="0.28" />
          <stop offset="1" :stop-color="color" stop-opacity="0" />
        </linearGradient>
      </defs>
      <!-- Grid + Y labels -->
      <g class="spark-grid">
        <line
          v-for="t in yTicks"
          :key="`g-${t.y}`"
          :x1="PAD_X"
          :x2="VIEW_W - PAD_X"
          :y1="t.y"
          :y2="t.y"
        />
        <text
          v-for="t in yTicks"
          :key="`ty-${t.y}`"
          :x="PAD_X - 6"
          :y="t.y + 3"
          class="spark-y-label"
        >
          {{ t.value }}
        </text>
      </g>
      <!-- Area + line -->
      <path :d="areaPath" :fill="`url(#${gradientId})`" />
      <path :d="linePath" :stroke="color" fill="none" stroke-width="1.6" />
      <circle
        v-for="(p, i) in points"
        :key="`p-${i}`"
        :cx="p.x"
        :cy="p.y"
        r="1.6"
        :fill="color"
      />
      <!-- X labels -->
      <g class="spark-x">
        <text
          v-for="t in xTicks"
          :key="`x-${t.x}`"
          :x="t.x"
          :y="chartHeight - 6"
          text-anchor="middle"
          class="spark-x-label"
        >
          {{ t.label }}
        </text>
      </g>
    </svg>
    <div v-if="series.length === 0" class="spark-empty">暂无数据</div>
  </div>
</template>

<style scoped>
.spark-wrap {
  position: relative;
  width: 100%;
}
.spark-svg {
  width: 100%;
  height: auto;
  display: block;
}
.spark-grid line {
  stroke: #eef2f7;
  stroke-width: 1;
}
.spark-y-label {
  fill: #94a3b8;
  font-size: 9px;
  font-family: ui-sans-serif, system-ui, sans-serif;
  text-anchor: end;
}
.spark-x-label {
  fill: #94a3b8;
  font-size: 9px;
  font-family: ui-sans-serif, system-ui, sans-serif;
}
.spark-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 12px;
}
</style>
