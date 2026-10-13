<script setup lang="ts">
import { computed, ref } from 'vue';

import PromptEditor from '@/components/PromptEditor.vue';
import PromptEditorTagPanel from '@/components/PromptEditorTagPanel.vue';
import RestfulSelectCard from '@/components/stubs/RestfulSelectCard.vue';
import WfField from '@/workflow/WfField.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

import AuthorizationItem from '../AuthorizationItem.vue';
import ParamTableItem from '../ParamTableItem.vue';

defineProps<{
  /**
   * 当前节点在 vue-flow 里的真实 id —— 变量插入面板需要它来定位上游节点。
   * 由 NodeConfigCard 从 selectNode.id 传下来，不要读 formState.id（formState
   * 是 node.data 的深拷贝，通常没有 id 字段）。
   */
  nodeId?: string;
}>();

const formState: any = defineModel();

const targetElement = ref<HTMLElement | null>(null);
const restfulSelectCard = ref();
const showAttr = ref<boolean>(false);

const checkAttr = (event: any) => {
  targetElement.value = event.currentTarget;
  showAttr.value = true;
};
const closeAttrPanel = () => {
  showAttr.value = false;
};

const openRestful = () => {
  restfulSelectCard.value?.showModal?.();
};

const selectAttr = (variableSelector: any) => {
  formState.value.body.BINARY = variableSelector;
  showAttr.value = false;
};

const getVarSelectLabel = (variableSelector: any) =>
  workflow_utils.getVariableLabel(variableSelector);

const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as const;

/** Body type 与显示标签映射 */
const BODY_TYPES = [
  { value: 'NONE', label: 'none' },
  { value: 'FORM_DATA', label: 'form-data' },
  { value: 'X_WWW_FORM_URLENCODED', label: 'x-www-form-urlencoded' },
  { value: 'JSON', label: 'JSON' },
  { value: 'RAW', label: 'raw' },
  { value: 'BINARY', label: 'binary' },
] as const;

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

/** 非空条目计数（headers / parameters / form rows） */
const nonEmptyCount = (rows: any) =>
  Array.isArray(rows) ? rows.filter((r) => r && r.name).length : 0;

const headerCount = computed(() => nonEmptyCount(formState.value.headers));
const paramCount = computed(() => nonEmptyCount(formState.value.parameters));
const bodyHasContent = computed(() => {
  const t = formState.value.bodyType;
  const body = formState.value.body || {};
  if (t === 'NONE') return false;
  if (t === 'JSON') return !!(body.JSON && String(body.JSON).trim());
  if (t === 'RAW') return !!(body.RAW && String(body.RAW).trim());
  if (t === 'BINARY') return !!body.BINARY;
  if (t === 'FORM_DATA') return nonEmptyCount(body.FORM_DATA) > 0;
  if (t === 'X_WWW_FORM_URLENCODED') return nonEmptyCount(body.X_WWW_FORM_URLENCODED) > 0;
  return false;
});
const authEnabled = computed(() => {
  const auth = formState.value.authorization;
  return !!(auth && auth.auth_type && auth.auth_type !== 'none');
});
</script>

<template>
  <PromptEditorTagPanel
    :show="showAttr"
    :target-element="targetElement"
    :node-id="nodeId"
    @close="closeAttrPanel"
    @select="selectAttr"
  />
  <RestfulSelectCard ref="restfulSelectCard" />

  <!-- API：method + URL + 超时 一体化 -->
  <div class="wf-config-section">
    <WfField title="API" required>
      <template #operations>
        <a-button type="link" size="small" @click="openRestful">
          从服务导入
        </a-button>
      </template>

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
          placeholder="/api/foo 或 https://api.example.com/endpoint"
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
        <a-tooltip title="失败重试次数（0-10）。仅在服务器无响应/超时时重试，返回错误码不重试。">
          <div class="wf-http-timeout">
            <span class="wf-http-timeout-label">↻</span>
            <a-input-number
              v-model:value="formState.maxRetries"
              :min="0"
              :max="10"
              :step="1"
              :controls="false"
              :bordered="false"
              size="small"
              placeholder="0"
              class="wf-http-timeout-input"
            />
            <span class="wf-http-timeout-suffix">次</span>
          </div>
        </a-tooltip>
      </div>
    </WfField>
  </div>

  <!-- Authorization -->
  <div class="wf-config-section">
    <WfField title="Authorization" foldable :default-fold="!authEnabled">
      <template v-if="authEnabled" #operations>
        <span class="wf-http-badge wf-http-badge-on">已启用</span>
      </template>
      <AuthorizationItem
        v-model="formState.authorization"
        name="headers"
        :node-id="nodeId"
      />
    </WfField>
  </div>

  <!-- Headers -->
  <div class="wf-config-section">
    <WfField title="Headers" foldable :default-fold="headerCount === 0">
      <template v-if="headerCount > 0" #operations>
        <span class="wf-http-badge">{{ headerCount }}</span>
      </template>
      <ParamTableItem v-model="formState.headers" name="headers" :node-id="nodeId" />
    </WfField>
  </div>

  <!-- Query 参数 -->
  <div class="wf-config-section">
    <WfField title="Query 参数" foldable :default-fold="paramCount === 0">
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

  <!-- Body -->
  <div class="wf-config-section">
    <WfField title="Body">
      <template v-if="bodyHasContent" #operations>
        <span class="wf-http-badge">{{ formState.bodyType.toLowerCase().replace(/_/g, '-') }}</span>
      </template>

      <!-- Body 类型切换：紧凑的 chip 风格 -->
      <div class="wf-http-body-tabs">
        <button
          v-for="t in BODY_TYPES"
          :key="t.value"
          class="wf-http-body-tab"
          :class="{ 'is-active': formState.bodyType === t.value }"
          @click="formState.bodyType = t.value"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- Body 内容按类型分发 -->
      <div class="wf-http-body-body">
        <div v-if="formState.bodyType === 'NONE'" class="wf-http-body-empty">
          不发送 body
        </div>

        <div v-else-if="formState.bodyType === 'BINARY'" class="wf-http-binary">
          <div class="wf-http-binary-trigger" @click="checkAttr($event)">
            <template v-if="formState.body.BINARY">
              <a-tag color="processing">
                {{ getVarSelectLabel(formState.body.BINARY) }}
              </a-tag>
            </template>
            <span v-else class="wf-http-binary-hint">
              点击选择二进制变量（file 类型）
            </span>
          </div>
        </div>

        <PromptEditor
          v-else-if="formState.bodyType === 'JSON'"
          title="JSON"
          :node-id="nodeId"
          v-model="formState.body.JSON"
          min-height="120px"
        />

        <PromptEditor
          v-else-if="formState.bodyType === 'RAW'"
          title="RAW"
          :node-id="nodeId"
          v-model="formState.body.RAW"
          min-height="120px"
        />

        <ParamTableItem
          v-else-if="formState.bodyType === 'FORM_DATA'"
          v-model="formState.body.FORM_DATA"
          name="FORM_DATA"
          :node-id="nodeId"
        />

        <ParamTableItem
          v-else-if="formState.bodyType === 'X_WWW_FORM_URLENCODED'"
          v-model="formState.body.X_WWW_FORM_URLENCODED"
          name="X_WWW_FORM_URLENCODED"
          :node-id="nodeId"
        />
      </div>
    </WfField>
  </div>
</template>

<style scoped>
/* -------------------------------------------------------------
 * API 一体化输入条：[METHOD ▼] │ [URL] │ [⏱ 30 s]
 * 三段用 1px 竖分割线分隔，整条聚焦时描个统一的 primary ring。
 * ------------------------------------------------------------- */
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

/* 超时嵌入右侧，字号收敛、单位后缀、无 controls */
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

/* -------------------------------------------------------------
 * 计数/状态徽章 —— 折叠标题右侧
 * ------------------------------------------------------------- */
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

.wf-http-badge-on {
  color: #047857;
  background: #d1fae5;
}

/* -------------------------------------------------------------
 * Body 类型切换：更紧凑的 chip
 * ------------------------------------------------------------- */
.wf-http-body-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border-radius: 6px;
  overflow-x: auto;
}

.wf-http-body-tab {
  flex: none;
  padding: 3px 8px;
  font-size: 11px;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.12s, color 0.12s;
}

.wf-http-body-tab:hover {
  color: #1f2937;
}

.wf-http-body-tab.is-active {
  color: #4338ca;
  background: var(--wf-config-surface, #ffffff);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  font-weight: 500;
}

.wf-http-body-body {
  margin-top: 8px;
}

.wf-http-body-empty {
  padding: 8px 12px;
  text-align: center;
  font-size: 11px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.wf-http-binary-trigger {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  min-height: 32px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.wf-http-binary-trigger:hover {
  border-color: #6366f1;
}

.wf-http-binary-hint {
  font-size: 12px;
  color: #9ca3af;
}
</style>
