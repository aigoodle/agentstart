<script setup lang="ts">
/**
 * 监测 tab — 2-column grid of metric cards, each backed by a SparkChart.
 * Matches Snipaste_09-01-41 (全部会话 / 活跃用户数 / 平均会话互动数 / Token 输出速度 /
 *   用户满意度 / 费用消耗).
 */
import { ref } from 'vue';

import type { StudioMonitorMetric } from '../types';
import SparkChart from './SparkChart.vue';

interface Props {
  metrics: StudioMonitorMetric[];
  loading?: boolean;
}

withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<{
  (e: 'change-period', period: string): void;
  (e: 'refresh'): void;
}>();

const period = ref('过去 7 天');
</script>

<template>
  <div class="mon">
    <header class="mon-top">
      <h2 class="mon-top-title">监测</h2>
      <div class="mon-top-right">
        <select
          class="mon-select"
          :value="period"
          @change="(e) => { period = (e.target as HTMLSelectElement).value; emit('change-period', period); }"
        >
          <option>过去 7 天</option>
          <option>过去 30 天</option>
          <option>过去 90 天</option>
        </select>
        <button
          type="button"
          class="mon-btn"
          @click="emit('refresh')"
          title="刷新"
        >
          ⟳
        </button>
      </div>
    </header>

    <div class="mon-body">
      <div v-if="loading" class="mon-loading">加载中...</div>
      <div v-else-if="metrics.length === 0" class="mon-loading">
        暂无监测数据
      </div>
      <div v-else class="mon-grid">
        <div v-for="m in metrics" :key="m.id" class="mon-card">
          <div class="mon-card-head">
            <div class="mon-card-title">
              {{ m.title }}
              <span class="mon-help" title="过去所选时间段内的指标">ⓘ</span>
            </div>
            <div class="mon-card-period">过去 7 天</div>
          </div>
          <div class="mon-card-value">
            <span class="mon-card-num">{{ m.value }}</span>
            <span v-if="m.unit" class="mon-card-unit">{{ m.unit }}</span>
          </div>
          <div v-if="m.subtitle" class="mon-card-sub">{{ m.subtitle }}</div>
          <div class="mon-card-chart">
            <SparkChart
              :series="m.series"
              :labels="m.labels"
              :color="m.color"
              :height="120"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mon {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.mon-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.mon-top-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.mon-top-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.mon-select {
  padding: 5px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #0f172a;
  outline: none;
}
.mon-select:focus {
  border-color: #6366f1;
}
.mon-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.mon-btn:hover {
  background: #f1f5f9;
}
.mon-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #f8fafc;
}
.mon-loading {
  padding: 60px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}
.mon-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.mon-card {
  padding: 14px 18px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.mon-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mon-card-title {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
}
.mon-help {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 10px;
  cursor: help;
}
.mon-card-period {
  font-size: 10px;
  color: #94a3b8;
}
.mon-card-value {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.mon-card-num {
  font-size: 26px;
  font-weight: 600;
  color: #0f172a;
}
.mon-card-unit {
  font-size: 12px;
  color: #64748b;
}
.mon-card-sub {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}
.mon-card-chart {
  margin-top: 8px;
}
</style>
