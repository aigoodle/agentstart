<script setup lang="ts">
import { computed, reactive, ref } from 'vue';

import Icon from '../workflow/Icon.vue';
import nodeCatalog from '../workflow/utils/node_config';

interface Props { graph?: Record<string, unknown>; }
const props = withDefaults(defineProps<Props>(), { graph: () => ({}) });

interface TraceEvent {
  event?: string;
  data?: Record<string, any>;
}

const steps = ref<Record<string, any>[]>([]);
const status = ref<'idle' | 'running' | 'success' | 'failed'>('idle');
const finalResult = ref<Record<string, any> | null>(null);
const expanded = reactive<Record<number, boolean>>({});

const nodesById = computed(() => {
  const map = new Map<string, Record<string, any>>();
  const nodes = Array.isArray((props.graph as any)?.nodes) ? (props.graph as any).nodes : [];
  nodes.forEach((node: Record<string, any>) => map.set(String(node.id), node));
  return map;
});

function nodeOf(step: Record<string, any>) {
  return nodesById.value.get(String(step.node_id ?? step.nodeId ?? step.id ?? ''));
}
function nodeType(step: Record<string, any>) {
  return String(step.node_type ?? step.nodeType ?? step.type ?? nodeOf(step)?.type ?? 'NODE').toUpperCase();
}
function nodeMeta(step: Record<string, any>) {
  const type = nodeType(step);
  return nodeCatalog.find((item) => item.type === type) ?? { type, name: type, icon: 'workflow', category: '其他' as const };
}
function nodeTitle(step: Record<string, any>, index: number) {
  const node = nodeOf(step);
  return step.title || step.node_title || step.nodeTitle || node?.data?.label || node?.label || nodeMeta(step).name || `节点 ${index + 1}`;
}
function nodeAccent(step: Record<string, any>) {
  const colors: Record<string, string> = { 输入: '#10b981', 'AI 模型': '#6366f1', 逻辑: '#f59e0b', 工具: '#06b6d4', 输出: '#f97316', 其他: '#64748b' };
  return colors[nodeMeta(step).category] ?? colors.其他;
}

const statusText = computed(() => ({
  idle: '等待执行', running: '执行中', success: '执行完成', failed: '执行失败',
}[status.value]));

function receive(payload: { name: string; data?: unknown }) {
  // 新版 chat iframe 用 workflow:trace 包一层；旧版会直接把 SSE 事件名
  // 作为 name 发给宿主。两种格式都接收，避免升级任一侧后时间线变空。
  const trace = (payload.data ?? {}) as TraceEvent;
  const event = payload.name === 'workflow:trace' ? trace.event ?? '' : payload.name;
  if (![
    'workflow_started', 'run-start', 'node_finished', 'step', 'message',
    'agent_message', 'answer_delta', 'message_replace', 'workflow_finished',
    'chat_finished', 'result', 'error',
  ].includes(event)) return;
  const rawData = payload.name === 'workflow:trace' ? trace.data : payload.data;
  const data = rawData && typeof rawData === 'object'
    ? rawData as Record<string, any>
    : {};
  if (event === 'workflow_started' || event === 'run-start') {
    steps.value = [];
    finalResult.value = null;
    status.value = 'running';
  } else if (event === 'node_finished' || event === 'step') {
    steps.value.push(data);
    status.value = 'running';
  } else if (event === 'message' || event === 'agent_message' || event === 'answer_delta' || event === 'message_replace') {
    const answer = String(data.answer ?? data.delta ?? '');
    if (!answer) return;
    // 流式 LLM 的 node_finished 先于 answer 分片到达；将文本归入最近的 LLM 节点。
    const llmStep = [...steps.value].reverse().find((step) => nodeType(step) === 'LLM');
    if (llmStep) {
      llmStep.__streamedAnswer = event === 'message_replace'
        ? answer
        : String(llmStep.__streamedAnswer ?? '') + answer;
    }
  } else if (event === 'workflow_finished' || event === 'chat_finished' || event === 'result') {
    finalResult.value = data;
    const resultSteps = data.steps ?? data.stepRecords ?? data.data?.steps ?? data.data?.stepRecords;
    if (Array.isArray(resultSteps) && resultSteps.length > 0) {
      steps.value = resultSteps;
    }
    status.value = data.status === 'failed' || data.success === false ? 'failed' : 'success';
  } else if (event === 'error') {
    finalResult.value = data;
    status.value = 'failed';
  }
}

function pretty(value: unknown) {
  if (value === undefined || value === null) return '{}';
  if (typeof value === 'string') return value;
  try { return JSON.stringify(value, null, 2); } catch { return String(value); }
}

function stepOutput(step: Record<string, any>) {
  if (step.__streamedAnswer) return { text: step.__streamedAnswer };
  return step.outputs ?? step.output ?? step.data;
}

defineExpose({ receive });
</script>

<template>
  <section class="cet-root">
    <header class="cet-head">
      <strong>执行过程</strong>
      <span :class="`cet-status cet-${status}`">{{ statusText }}</span>
    </header>
    <div class="cet-body">
      <div v-if="status === 'idle'" class="cet-empty">在右侧发送消息后，这里将实时展示工作流各节点的执行结果。</div>
      <article v-for="(step, index) in steps" :key="`${step.node_id || step.nodeId || index}-${index}`" class="cet-step">
        <button class="cet-step-head" @click="expanded[index] = !expanded[index]">
          <span class="cet-node-icon" :style="{ color: nodeAccent(step), backgroundColor: `${nodeAccent(step)}18` }"><Icon :name="nodeMeta(step).icon" /></span>
          <span class="cet-name" :title="nodeTitle(step, index)">{{ nodeTitle(step, index) }}</span>
          <code class="cet-type">{{ nodeMeta(step).name }}</code>
          <span class="cet-result" :class="{ failed: step.status === 'failed' || step.failed }">{{ step.status === 'failed' || step.failed ? '!' : '✓' }}</span>
          <span class="cet-time">{{ step.elapsed_ms ?? step.elapsedMillis ?? step.duration ?? 0 }} ms</span>
          <span>{{ expanded[index] ? '⌃' : '⌄' }}</span>
        </button>
        <div v-if="step.__streamedAnswer && !expanded[index]" class="cet-stream-preview">{{ step.__streamedAnswer }}</div>
        <div v-if="expanded[index]" class="cet-detail">
          <b>输入</b><pre>{{ pretty(step.inputs ?? step.input) }}</pre>
          <b>输出 <em v-if="step.__streamedAnswer">流式</em></b><pre>{{ pretty(stepOutput(step)) }}</pre>
          <div v-if="step.error" class="cet-error">{{ step.error }}</div>
        </div>
      </article>
      <div v-if="status === 'running'" class="cet-running"><i /> 正在执行下一个节点…</div>
      <section v-if="finalResult" class="cet-final">
        <strong>最终输出</strong>
        <pre>{{ pretty(finalResult.outputs ?? finalResult.output ?? finalResult) }}</pre>
      </section>
    </div>
  </section>
</template>

<style scoped>
.cet-root{display:flex;flex-direction:column;width:100%;height:100%;min-width:0;background:var(--as-bg,#fff);border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;color:#1f2937}.cet-head{display:flex;align-items:center;justify-content:space-between;min-height:46px;padding:0 14px;border-bottom:1px solid #eef0f3}.cet-head strong{font-size:13px}.cet-status{font-size:11px}.cet-idle{color:#94a3b8}.cet-running{color:#6366f1}.cet-success{color:#16a34a}.cet-failed,.cet-error{color:#dc2626}.cet-body{display:grid;align-content:start;gap:8px;flex:1;min-height:0;padding:14px;overflow:auto}.cet-empty,.cet-running{padding:12px;border-radius:8px;background:#f8fafc;color:#64748b;font-size:12px;line-height:1.6}.cet-step{border:1px solid #e5e7eb;border-radius:9px;overflow:hidden}.cet-step-head{display:flex;align-items:center;gap:7px;width:100%;padding:9px;border:0;background:#fafafa;text-align:left;cursor:pointer}.cet-node-icon{display:grid;place-items:center;width:25px;height:25px;flex:none;border-radius:7px}.cet-node-icon :deep(svg){width:15px;height:15px}.cet-name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:600}.cet-type{max-width:72px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:2px 5px;border-radius:4px;background:#f1f5f9;color:#64748b;font-size:9px}.cet-result{display:grid;place-items:center;width:17px;height:17px;flex:none;border-radius:50%;background:#dcfce7;color:#15803d;font-size:10px}.cet-result.failed{background:#fee2e2;color:#dc2626}.cet-time{font-size:10px;color:#94a3b8}.cet-stream-preview{max-height:70px;padding:8px 10px;overflow:hidden;border-top:1px solid #eef0f3;background:var(--as-bg,#fff);color:#475569;font-size:11px;line-height:1.5;white-space:pre-wrap;word-break:break-word}.cet-detail{display:grid;gap:6px;padding:10px;border-top:1px solid #eef0f3}.cet-detail b{font-size:11px;color:#64748b}.cet-detail b em{margin-left:4px;padding:1px 5px;border-radius:999px;background:#eef2ff;color:#4f46e5;font-size:9px;font-style:normal}.cet-detail pre,.cet-final pre{max-height:220px;margin:0;overflow:auto;padding:9px;border-radius:7px;background:#0f172a;color:#e2e8f0;font:11px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;word-break:break-word}.cet-error{font-size:12px}.cet-running i{display:inline-block;width:8px;height:8px;margin-right:5px;border-radius:50%;background:#6366f1;animation:cet-pulse 1s ease-in-out infinite}.cet-final{display:grid;gap:8px;margin-top:6px}.cet-final strong{font-size:12px}@keyframes cet-pulse{50%{opacity:.25}}
:global(.dark) .cet-root{background:var(--ant-color-bg-container,#18181b);border-color:var(--ant-color-border,#3f3f46);color:var(--ant-color-text,#f4f4f5)}
:global(.dark) .cet-head,:global(.dark) .cet-detail{border-color:#3f3f46;background:#18181b}
:global(.dark) .cet-body{background:#18181b}
:global(.dark) .cet-empty,:global(.dark) .cet-running{background:#27272a;color:#a1a1aa}
:global(.dark) .cet-step{border-color:#3f3f46;background:#202023}
:global(.dark) .cet-step-head{background:#27272a;color:#f4f4f5}
:global(.dark) .cet-type{background:#3f3f46;color:#d4d4d8}
:global(.dark) .cet-stream-preview{border-color:#3f3f46;background:#202023;color:#d4d4d8}
:global(.dark) .cet-detail b{color:#a1a1aa}
</style>
