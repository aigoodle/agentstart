<!--
<script setup lang="ts">
import { nextTick, onMounted, ref, shallowRef } from 'vue'
import { FlowDesigner, NodeConfigCard } from 'vue-agent-start'

/**
 * 工作流设计器调试页 —— 对齐 vben web-antd 的 agent-flow/debug.vue：
 * 纯前端画布 + 外部 NodeConfigCard 面板，工具栏验证 defineExpose 方法。
 */

const designerRef = shallowRef<any>(null)
const selectedNode = ref<any>(null)
const mode = ref<'WORKFLOW' | 'CHATFLOW'>('WORKFLOW')

// 本地轻量 toast（库的 message 未从顶层导出，这里保持 HUD 风格自绘）
const toast = ref<{ type: 'ok' | 'warn' | 'err'; text: string } | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined
function showToast(type: 'ok' | 'warn' | 'err', text: string) {
  toast.value = { type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = null
  }, 2800)
}

function onNodeClick(node: any) {
  selectedNode.value = node
}

function onNodeDelete() {
  selectedNode.value = null
}

function onCloseConfig() {
  selectedNode.value = null
}

function onConfigDataChange(payload: { nodeId: string; data: any }) {
  designerRef.value?.patchNodeData?.(payload.nodeId, payload.data)
}

function onReset() {
  designerRef.value?.reloadGraph?.(null)
  selectedNode.value = null
}

function onDumpGraph() {
  const info = designerRef.value?.getFlowInfo?.()
  console.log('[workflow] graph:', info)
  showToast('ok', `已打印图数据到 console：${info?.nodes?.length ?? 0} 节点 / ${info?.edges?.length ?? 0} 边`)
}

function onValidate() {
  const errors: string[] = designerRef.value?.validateWorkflow?.() ?? []
  if (errors.length === 0) showToast('ok', '校验通过')
  else showToast('warn', `校验失败：${errors.join('；')}`)
}

function toggleMode() {
  mode.value = mode.value === 'WORKFLOW' ? 'CHATFLOW' : 'WORKFLOW'
  onReset()
}

onMounted(async () => {
  await nextTick()
  designerRef.value?.reloadGraph?.(null)
})
</script>

<template>
  <div class="view-fill wf-view">
    <div class="wf-bar hud-panel">
      <span class="wf-tag">FLOW</span>
      <span class="wf-mode hud-mono">mode = {{ mode }}</span>
      <div class="wf-actions">
        <button type="button" class="wf-btn" @click="onReset">重置画布</button>
        <button type="button" class="wf-btn" @click="toggleMode">切换模式</button>
        <button type="button" class="wf-btn" @click="onDumpGraph">打印图数据</button>
        <button type="button" class="wf-btn" @click="onValidate">运行校验</button>
      </div>
      <Transition name="hud-fade">
        <span v-if="toast" class="wf-toast" :class="`wf-toast&#45;&#45;${toast.type}`">{{ toast.text }}</span>
      </Transition>
    </div>

    <div class="wf-body">
      <div class="wf-canvas hud-panel hud-embed">
        <FlowDesigner
          ref="designerRef"
          class="wf-designer"
          :mode="mode"
          @node-click="onNodeClick"
          @node-delete="onNodeDelete"
        />
      </div>
      <div v-if="selectedNode" class="wf-panel">
        <NodeConfigCard
          :select-node="selectedNode"
          @on-close="onCloseConfig"
          @data-change="onConfigDataChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wf-bar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  padding: 8px 14px;
}

.wf-tag {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #04121f;
  background: linear-gradient(135deg, var(&#45;&#45;hud-primary-bright), var(&#45;&#45;hud-primary-deep));
  border-radius: 4px;
  box-shadow: var(&#45;&#45;hud-glow);
}

.wf-mode {
  font-size: 11px;
  color: var(&#45;&#45;hud-text-muted);
}

.wf-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.wf-btn {
  padding: 4px 12px;
  font-size: 12px;
  color: var(&#45;&#45;hud-text);
  background: rgba(0, 150, 220, 0.08);
  border: 1px solid var(&#45;&#45;hud-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.wf-btn:hover {
  color: var(&#45;&#45;hud-primary-bright);
  border-color: var(&#45;&#45;hud-border-strong);
  background: var(&#45;&#45;hud-accent-soft);
  box-shadow: var(&#45;&#45;hud-glow);
}

.wf-toast {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  width: fit-content;
  margin: 0 auto;
  z-index: 30;
  padding: 6px 16px;
  font-size: 12px;
  white-space: nowrap;
  border-radius: 8px;
  backdrop-filter: blur(8px);
}
.wf-toast&#45;&#45;ok {
  color: #6ee7a0;
  background: rgba(10, 60, 40, 0.85);
  border: 1px solid rgba(34, 197, 94, 0.45);
  box-shadow: 0 0 12px rgba(34, 197, 94, 0.25);
}
.wf-toast&#45;&#45;warn {
  color: #fde68a;
  background: rgba(60, 45, 10, 0.85);
  border: 1px solid rgba(250, 204, 21, 0.45);
  box-shadow: 0 0 12px rgba(250, 204, 21, 0.2);
}
.wf-toast&#45;&#45;err {
  color: #ff9d9d;
  background: rgba(60, 15, 15, 0.85);
  border: 1px solid rgba(255, 68, 68, 0.5);
  box-shadow: 0 0 12px rgba(255, 68, 68, 0.3);
}

.wf-body {
  display: flex;
  gap: 10px;
  flex: 1;
  min-height: 0;
}

.wf-canvas {
  position: relative;
  flex: 1;
  min-width: 0;
}

.wf-designer {
  width: 100%;
  height: 100%;
}

.wf-panel {
  flex: none;
  height: 100%;
  overflow: auto;
  background: var(&#45;&#45;hud-panel-bg);
  border: 1px solid var(&#45;&#45;hud-border);
  border-radius: 12px;
  box-shadow: var(&#45;&#45;hud-glow);
  backdrop-filter: blur(10px);
}
</style>
-->
