<script setup lang="ts">
import { computed } from 'vue';

import WfField from '@/workflow/WfField.vue';

import ParamTableItem from '../ParamTableItem.vue';

defineProps<{
  /** 当前节点 id —— 变量插入面板需要，用于查询上游节点输出。 */
  nodeId?: string;
}>();

const formState: any = defineModel();

const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as const;

const methodStyle = computed(() => {
  const method = (formState.value.method || 'GET').toUpperCase();
  const colors: Record<string, string> = {
    GET: '#2563eb',
    POST: '#059669',
    PUT: '#d97706',
    DELETE: '#dc2626',
    PATCH: '#7c3aed',
  };
  return { '--http-method-color': colors[method] || '#6b7280' };
});

const nonEmptyCount = (rows: any) =>
  Array.isArray(rows) ? rows.filter((r) => r && r.name).length : 0;
const headerCount = computed(() => nonEmptyCount(formState.value.headers));
const paramCount = computed(() => nonEmptyCount(formState.value.parameters));
</script>

<template>
  <!-- 接口地址 + 方法 + 超时 一体化 -->
  <div class="wf-config-section">
    <WfField title="接口地址" required>
      <div class="wf-http-url" :style="methodStyle">
        <a-select
          v-model:value="formState.method"
          :bordered="false"
          size="small"
          class="wf-http-method"
        >
          <a-select-option v-for="m in METHODS" :key="m" :value="m">
            {{ m }}
          </a-select-option>
        </a-select>
        <a-input
          v-model:value="formState.url"
          :bordered="false"
          class="wf-http-url-input"
          placeholder="/api/service/endpoint 或 https://..."
        />
        <a-tooltip title="请求超时（秒），1-600">
          <div class="wf-http-timeout">
            <span class="wf-http-timeout-label">⏱</span>
            <a-input-number
              v-model:value="formState.timeoutSeconds"
              :min="1"
              :max="600"
              :step="1"
              :controls="false"
              :bordered="false"
              size="small"
              placeholder="30"
              class="wf-http-timeout-input"
            />
            <span class="wf-http-timeout-suffix">s</span>
          </div>
        </a-tooltip>
      </div>
    </WfField>
  </div>

  <!-- 头部参数 -->
  <div class="wf-config-section">
    <WfField title="头部参数" foldable :default-fold="headerCount === 0">
      <template v-if="headerCount > 0" #operations>
        <span class="wf-http-badge">{{ headerCount }}</span>
      </template>
      <ParamTableItem v-model="formState.headers" name="headers" :node-id="nodeId" />
    </WfField>
  </div>

  <!-- 请求参数 -->
  <div class="wf-config-section">
    <WfField title="请求参数" foldable :default-fold="paramCount === 0">
      <template v-if="paramCount > 0" #operations>
        <span class="wf-http-badge">{{ paramCount }}</span>
      </template>
      <ParamTableItem
        v-model="formState.parameters"
        name="parameters"
        :node-id="nodeId"
      />
    </WfField>
  </div>
</template>

<style scoped>
.wf-http-url {
  display: flex;
  align-items: stretch;
  background: var(--wf-config-surface, #ffffff);
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.wf-http-url:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
}

.wf-http-method {
  flex: none;
  width: 88px;
  border-right: 1px solid #e5e7eb;
  background: color-mix(in srgb, var(--http-method-color) 10%, transparent);
}

.wf-http-method :deep(.ant-select-selector) {
  padding: 0 8px !important;
  font-weight: 700;
  color: var(--http-method-color);
  font-size: 12px;
  letter-spacing: 0.4px;
}

.wf-http-url-input {
  flex: 1;
  min-width: 0;
}

.wf-http-timeout {
  display: flex;
  align-items: center;
  flex: none;
  padding: 0 8px;
  gap: 2px;
  color: #6b7280;
  background: var(--wf-config-surface-hover, #f9fafb);
  border-left: 1px solid #e5e7eb;
  font-size: 11px;
}

.wf-http-timeout-label {
  font-size: 12px;
  opacity: 0.7;
}

.wf-http-timeout-input {
  width: 42px;
}

.wf-http-timeout-input :deep(.ant-input-number-input) {
  padding: 0 !important;
  height: 30px;
  font-size: 12px;
  text-align: right;
  color: #1f2937;
}

.wf-http-timeout-suffix {
  font-size: 11px;
  color: #9ca3af;
}

.wf-http-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  padding: 0 6px;
  height: 18px;
  font-size: 10px;
  font-weight: 600;
  color: #4338ca;
  background: color-mix(in srgb, var(--wf-accent, #6366f1) 14%, var(--wf-config-surface, #fff));
  border-radius: 9px;
  letter-spacing: 0.2px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  text-transform: none;
}
</style>
