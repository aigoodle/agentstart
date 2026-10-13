<script setup lang="ts">
/**
 * WorkflowDebugPanel — 工作流试运行调试面板（单列紧凑布局）。
 *
 * 输入参数、执行操作、逐节点执行过程和最终输出共用一列，避免窄面板
 * 被固定侧栏挤占空间。
 *
 * 执行优先走 SSE 流式端点（`POST /workflows/run-graph/stream`，事件序列
 * `workflow_started → node_finished (每节点) → workflow_finished`，与 Dify
 * 协议一致）：每个节点执行完立即推送、立即渲染。宿主未提供流式执行器
 * （prop `executeWorkflowStream` / BackendAdapter.runWorkflowStream 均缺失）
 * 或流式请求被拒（如旧后端没有该端点）时，回退到一次性 `run-graph`。
 * 注意：流式请求失败后的回退会把工作流再执行一次（仅失败路径）。
 */
import { computed, onBeforeUnmount, reactive, ref } from 'vue';

import { readSseEvents } from '../../client/sse';
import { cancelWorkflowRun, pauseWorkflowRun, resumeWorkflowRun, runWorkflow, runWorkflowStream, signalWorkflowRun } from '../adapter/backend';
import type { ChatDebugVariable } from './chat-iframe-types';

interface Props {
  graph: Record<string, unknown>;
  variables?: ChatDebugVariable[];
  title?: string;
  closable?: boolean;
  /** 宿主执行器；内置 AgentAppsPage 通过统一 client 调用 /agent-start/workflows/run-graph。 */
  executeWorkflow?: (payload: Record<string, unknown>) => Promise<unknown>;
  /**
   * 宿主 SSE 流式执行器（/agent-start/workflows/run-graph/stream）。返回原始
   * `Response` 由面板逐节点消费；`opts.signal` 用于中途取消。返回 `null` 表示
   * 宿主未实现 —— 面板自动回退一次性执行；请求被拒同样回退（会二次执行）。
   */
  executeWorkflowStream?: (
    payload: Record<string, unknown>,
    opts?: { signal?: AbortSignal },
  ) => Promise<Response | null>;
  cancelRun?: (runId: string, reason?: string) => Promise<unknown>;
  pauseRun?: (runId: string, reason?: string) => Promise<unknown>;
  resumeRun?: (runId: string) => Promise<unknown>;
  signalRun?: (runId: string, request: Record<string, unknown>) => Promise<unknown>;
}

const props = withDefaults(defineProps<Props>(), {
  variables: () => [],
  title: '工作流调试',
  closable: true,
});
const emit = defineEmits<{ (e: 'close'): void }>();

const values = reactive<Record<string, unknown>>({});
const running = ref(false);
/** true while an SSE stream is being consumed (drives stop button + tail pulse). */
const streamActive = ref(false);
const result = ref<Record<string, any> | null>(null);
const requestError = ref('');
const abortNotice = ref(false);
/** Completed-node timeline. Streaming appends; the terminal event may replace. */
const liveSteps = ref<Array<Record<string, any>>>([]);
const expanded = reactive<Record<string, boolean>>({});
const abortController = ref<AbortController | null>(null);
const activeRunId = ref('');
const controlBusy = ref(false);
const waitPayload = ref('{}');
/** Monotonic token — every frame checks it so stale runs can't mutate state. */
let runSeq = 0;

const nodesById = computed(() => {
  const map = new Map<string, any>();
  const nodes = Array.isArray((props.graph as any)?.nodes)
    ? (props.graph as any).nodes
    : [];
  nodes.forEach((node: any) => map.set(String(node.id), node));
  return map;
});

const missingRequired = computed(() =>
  props.variables.filter((item) => item.required && isEmpty(values[item.name])),
);

function isEmpty(value: unknown) {
  return value === undefined || value === null || value === '';
}

function defaultValue(item: ChatDebugVariable): unknown {
  if (item.defaultValue !== undefined) return item.defaultValue;
  if (item.type === 'boolean') return false;
  return '';
}

function ensureDefaults() {
  props.variables.forEach((item) => {
    if (!(item.name in values)) values[item.name] = defaultValue(item);
  });
}
ensureDefaults();

function setValue(item: ChatDebugVariable, raw: unknown) {
  const type = (item.type || 'string').toLowerCase();
  if (type === 'number' || type === 'integer') {
    values[item.name] = raw === '' ? '' : Number(raw);
  } else {
    values[item.name] = raw;
  }
}

function stepTitle(step: any, index: number) {
  const node = nodesById.value.get(String(step?.nodeId ?? step?.id ?? ''));
  return step?.title || node?.data?.label || node?.label || `节点 ${index + 1}`;
}

function stepType(step: any) {
  const node = nodesById.value.get(String(step?.nodeId ?? step?.id ?? ''));
  return step?.nodeType || step?.type || node?.type || 'NODE';
}

function stepInputs(step: any): unknown {
  return step?.inputs ?? step?.input ?? step?.resolvedInputs;
}

function stepOutputs(step: any): unknown {
  return step?.outputs ?? step?.output;
}

function pretty(value: unknown) {
  if (value === undefined) return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function unwrapResponse(response: any): Record<string, any> {
  let data = response?.data ?? response ?? {};
  // 同时兼容 axios 响应与 ApiResponse<T> 包装。
  if (data && typeof data === 'object' && data.data && !data.steps && !data.outputs) {
    data = data.data;
  }
  return data && typeof data === 'object' ? data : { outputs: data };
}

/** 后端 node_finished 是 snake_case（对齐聊天流），旧 step 事件是 camelCase。 */
function normalizeStep(raw: Record<string, any>): Record<string, any> {
  return {
    ...raw,
    nodeId: raw.nodeId ?? raw.node_id,
    nodeType: raw.nodeType ?? raw.node_type,
    elapsedMillis: raw.elapsedMillis ?? raw.elapsed_ms,
  };
}

/** 终态携带的 steps 是引擎权威顺序 —— 用它覆盖流式到达序；默认全部收起，点击才展开。 */
function adoptResultSteps(finalResult: Record<string, any>) {
  const authoritative = finalResult?.steps
    ?? finalResult?.stepRecords
    ?? finalResult?.data?.steps
    ?? finalResult?.data?.stepRecords
    ?? finalResult?.result?.steps
    ?? finalResult?.result?.stepRecords;
  if (!Array.isArray(authoritative) || authoritative.length === 0) return;
  liveSteps.value = authoritative.map((step) => normalizeStep(step));
  for (const key of Object.keys(expanded)) delete expanded[key];
}

/** object/array 变量按 JSON 解析，其余原样透传。 */
function buildInputs(): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(values).map(([name, value]) => {
      const type = (props.variables.find((item) => item.name === name)?.type || '').toLowerCase();
      if ((type === 'object' || type === 'array') && typeof value === 'string') {
        try {
          return [name, JSON.parse(value)];
        } catch {
          throw new Error(`参数「${name}」不是有效的 JSON`);
        }
      }
      return [name, value];
    }),
  );
}

function isAbortError(error: any): boolean {
  return error?.name === 'AbortError' || error?.code === 'ABORTED';
}

async function consumeStream(res: Response, mySeq: number) {
  let sawTerminal = false;
  for await (const { event, data } of readSseEvents(res)) {
    if (mySeq !== runSeq) return; // stale run — a newer execute()/reset() owns the state
    if (event === 'workflow_started' || event === 'run-start') {
      try {
        const started = JSON.parse(data);
        activeRunId.value = String(started.runId ?? started.run_id ?? '');
      } catch { /* older streams may send an empty start frame */ }
      continue;
    }
    if (event === 'node_finished' || event === 'step') {
      let step: Record<string, any>;
      try {
        step = JSON.parse(data);
      } catch {
        continue;
      }
      // 默认收起 —— 用户点卡片头才展开查看输入/输出
      liveSteps.value.push(normalizeStep(step));
      continue;
    }
    if (event === 'workflow_finished' || event === 'result') {
      let parsed: Record<string, any>;
      try {
        parsed = JSON.parse(data);
      } catch {
        continue;
      }
      sawTerminal = true;
      result.value = parsed;
      activeRunId.value = String(parsed.runId ?? activeRunId.value ?? '');
      adoptResultSteps(parsed);
      continue;
    }
    if (event === 'error') {
      sawTerminal = true;
      let msg = data || '工作流执行失败';
      try {
        const parsed = JSON.parse(data);
        if (parsed?.message) msg = parsed.message;
      } catch {
        // data wasn't JSON — use it verbatim
      }
      requestError.value = msg;
    }
  }
  if (mySeq !== runSeq) return;
  if (!sawTerminal) {
    requestError.value = requestError.value || '连接中断，未收到最终结果';
  }
}

async function executeOneShot(payload: Record<string, unknown>, mySeq: number) {
  const response = props.executeWorkflow
    ? await props.executeWorkflow(payload)
    : await runWorkflow!(payload);
  if (mySeq !== runSeq) return;
  result.value = unwrapResponse(response);
  activeRunId.value = String(result.value.runId ?? '');
  adoptResultSteps(result.value);
}

async function execute() {
  if (running.value || missingRequired.value.length > 0) return;
  let inputs: Record<string, unknown>;
  try {
    inputs = buildInputs();
  } catch (error: any) {
    requestError.value = error?.message || '参数校验失败';
    return;
  }
  const mySeq = ++runSeq;
  running.value = true;
  streamActive.value = false;
  requestError.value = '';
  abortNotice.value = false;
  result.value = null;
  liveSteps.value = [];
  for (const key of Object.keys(expanded)) delete expanded[key];
  abortController.value = new AbortController();
  const payload = { graph: props.graph, data: inputs, inputs };
  try {
    // ---- 流式优先：每节点完成即推送 ------------------------------------
    const streamFn = props.executeWorkflowStream ?? runWorkflowStream;
    let streamResponse: Response | null = null;
    try {
      streamResponse = await streamFn(payload, { signal: abortController.value.signal });
    } catch (streamError: any) {
      if (mySeq !== runSeq) return; // stale run — newer execute()/reset() owns the state
      if (abortController.value?.signal.aborted || isAbortError(streamError)) {
        abortNotice.value = true;
        return;
      }
      // 流式端点不可用（如旧后端 404）→ 回退一次性执行。
      // 注意：该失败路径会把工作流再执行一次。
      streamResponse = null;
    }
    if (mySeq !== runSeq) return;
    if (!streamResponse) {
      await executeOneShot(payload, mySeq);
      return;
    }
    const contentType = streamResponse.headers.get('content-type') ?? '';
    if (contentType.includes('application/json')) {
      // 端点（或中间网关）返回了 JSON 而不是 SSE —— 就地解析，不重发请求。
      const body = unwrapResponse(await streamResponse.json());
      if (mySeq !== runSeq) return;
      result.value = body;
      adoptResultSteps(body);
      return;
    }
    streamActive.value = true;
    await consumeStream(streamResponse, mySeq);
  } catch (error: any) {
    if (mySeq !== runSeq) return;
    if (abortController.value?.signal.aborted || isAbortError(error)) {
      abortNotice.value = true;
      return;
    }
    requestError.value = error?.message || '工作流执行失败';
  } finally {
    if (mySeq === runSeq) {
      running.value = false;
      streamActive.value = false;
    }
  }
}

async function stop() {
  if (activeRunId.value) {
    controlBusy.value = true;
    try { await (props.cancelRun ?? cancelWorkflowRun)(activeRunId.value, '用户从调试面板停止'); }
    catch (error: any) { requestError.value = error?.message || '取消运行失败'; }
    finally { controlBusy.value = false; }
  }
  abortController.value?.abort();
}

async function pause() {
  if (!activeRunId.value || controlBusy.value) return;
  controlBusy.value = true;
  try { await (props.pauseRun ?? pauseWorkflowRun)(activeRunId.value, '用户从调试面板暂停'); }
  catch (error: any) { requestError.value = error?.message || '暂停运行失败'; }
  finally { controlBusy.value = false; }
}

async function resume() {
  const runId = String(result.value?.runId ?? activeRunId.value ?? '');
  if (!runId || controlBusy.value) return;
  controlBusy.value = true;
  requestError.value = '';
  try {
    result.value = unwrapResponse(await (props.resumeRun ?? resumeWorkflowRun)(runId));
    adoptResultSteps(result.value);
  } catch (error: any) { requestError.value = error?.message || '恢复运行失败'; }
  finally { controlBusy.value = false; }
}

async function submitWait(approved?: boolean) {
  const runId = String(result.value?.runId ?? '');
  const wait = result.value?.waitRequest;
  if (!runId || !wait?.resumeToken || controlBusy.value) return;
  let payload: Record<string, unknown>;
  try { payload = approved === undefined ? JSON.parse(waitPayload.value || '{}') : { approved }; }
  catch { requestError.value = '恢复载荷不是有效的 JSON'; return; }
  controlBusy.value = true;
  requestError.value = '';
  try {
    const signalled = unwrapResponse(await (props.signalRun ?? signalWorkflowRun)(runId, {
      resumeToken: wait.resumeToken,
      eventId: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`,
      payload,
    }));
    const nextResult = signalled.runResult ?? signalled;
    result.value = nextResult;
    adoptResultSteps(nextResult);
  } catch (error: any) { requestError.value = error?.message || '提交恢复事件失败'; }
  finally { controlBusy.value = false; }
}

function reset() {
  runSeq += 1; // invalidate any in-flight stream frames
  abortController.value?.abort();
  running.value = false;
  streamActive.value = false;
  result.value = null;
  activeRunId.value = '';
  requestError.value = '';
  abortNotice.value = false;
  liveSteps.value = [];
  for (const key of Object.keys(expanded)) delete expanded[key];
}

// 面板销毁时中止在途流，避免陈旧帧继续到达。
onBeforeUnmount(() => {
  runSeq += 1;
  abortController.value?.abort();
});
</script>

<template>
  <section class="wdp-shell">
    <header class="wdp-head">
      <div>
        <strong>{{ title }}</strong>
        <span class="wdp-subtitle">逐节点查看执行输入与输出</span>
      </div>
      <button v-if="closable" class="wdp-icon-btn" title="关闭" @click="emit('close')">×</button>
    </header>

    <div class="wdp-body">
      <div class="wdp-input-section">
        <div class="wdp-section-head">
          <span>输入参数</span>
          <span v-if="missingRequired.length" class="wdp-required">{{ missingRequired.length }} 项必填</span>
        </div>
        <div v-if="variables.length" class="wdp-form">
          <label v-for="item in variables" :key="item.name" class="wdp-field">
            <span>{{ item.label || item.name }} <i v-if="item.required">*</i></span>
            <select
              v-if="item.options?.length"
              :value="values[item.name]"
              @change="setValue(item, ($event.target as HTMLSelectElement).value)"
            >
              <option value="">请选择</option>
              <option v-for="option in item.options" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <input
              v-else-if="item.type === 'boolean'"
              type="checkbox"
              :checked="Boolean(values[item.name])"
              @change="setValue(item, ($event.target as HTMLInputElement).checked)"
            />
            <textarea
              v-else-if="item.type === 'object' || item.type === 'array'"
              :value="pretty(values[item.name])"
              rows="3"
              placeholder="请输入 JSON"
              @input="setValue(item, ($event.target as HTMLTextAreaElement).value)"
            />
            <input
              v-else
              :type="item.type === 'number' || item.type === 'integer' ? 'number' : 'text'"
              :value="values[item.name] as any"
              :placeholder="item.description || `请输入${item.label || item.name}`"
              @input="setValue(item, ($event.target as HTMLInputElement).value)"
            />
            <small v-if="item.description">{{ item.description }}</small>
          </label>
        </div>
        <div v-else class="wdp-empty-input">开始节点未定义输入参数，可直接执行。</div>

        <div class="wdp-actions">
          <button class="wdp-run" :disabled="running || missingRequired.length > 0" @click="execute">
            {{ running ? '执行中…' : '▶ 执行工作流' }}
          </button>
          <button v-if="streamActive" class="wdp-stop" title="中止本次流式执行" @click="stop">■ 停止</button>
          <button v-if="streamActive && activeRunId" class="wdp-reset" :disabled="controlBusy" @click="pause">Ⅱ 暂停</button>
          <button
            v-if="result || requestError || abortNotice || liveSteps.length"
            class="wdp-reset"
            :disabled="running"
            @click="reset"
          >清空结果</button>
        </div>
      </div>

      <div class="wdp-process-section">
        <div class="wdp-section-head">
          <span>执行过程</span>
          <span v-if="running" class="wdp-running-chip">
            <span class="wdp-spinner" />
            {{ streamActive ? `已完成 ${liveSteps.length} 个节点` : '正在等待响应…' }}
          </span>
          <span v-else-if="result" :class="['WAITING','PAUSED'].includes(result.status) ? 'wdp-waiting' : result.success === false ? 'wdp-failed' : 'wdp-success'">
            {{ result.status || (result.success === false ? '执行失败' : '执行完成') }}
          </span>
        </div>

        <div v-if="abortNotice" class="wdp-aborted">已手动停止执行，以下为停止前已完成节点的结果。</div>
        <div v-if="requestError" class="wdp-error">{{ requestError }}</div>
        <div v-if="result?.status === 'WAITING'" class="wdp-wait-card">
          <strong>{{ result.waitRequest?.type === 'APPROVAL' ? '等待审批' : result.waitRequest?.type === 'SLEEP_UNTIL' ? '定时等待中' : '等待恢复输入' }}</strong>
          <small v-if="result.waitingNodeId">节点：{{ stepTitle({ nodeId: result.waitingNodeId }, 0) }}</small>
          <small v-if="result.waitRequest?.correlationKey">Correlation Key：<code>{{ result.waitRequest.correlationKey }}</code></small>
          <small v-if="result.waitRequest?.expiresAt">截止：{{ result.waitRequest.expiresAt }}</small>
          <small v-if="result.waitRequest?.wakeAt">自动唤醒：{{ result.waitRequest.wakeAt }}</small>
          <div v-if="result.waitRequest?.type === 'APPROVAL'" class="wdp-actions"><button class="wdp-run" :disabled="controlBusy" @click="submitWait(true)">批准</button><button class="wdp-stop" :disabled="controlBusy" @click="submitWait(false)">拒绝</button></div>
          <template v-else-if="result.waitRequest?.type !== 'SLEEP_UNTIL'">
            <textarea v-model="waitPayload" rows="5" placeholder='恢复载荷 JSON，例如 {"response":"确认"}' />
            <button class="wdp-run" :disabled="controlBusy" @click="submitWait()">提交并恢复</button>
          </template>
        </div>
        <div v-if="result?.status === 'PAUSED'" class="wdp-wait-card"><strong>运行已暂停</strong><button class="wdp-run" :disabled="controlBusy" @click="resume">继续执行</button></div>
        <div
          v-if="!running && !result && !requestError && !abortNotice && liveSteps.length === 0"
          class="wdp-placeholder"
        >填写参数并执行后，这里将逐节点实时展示每个节点的运行详情。</div>

        <div v-if="liveSteps.length > 0 || running" class="wdp-timeline">
          <article
            v-for="(step, index) in liveSteps"
            :key="`${step.nodeId || index}-${index}`"
            class="wdp-step"
            :class="{ failed: step.failed }"
          >
            <button class="wdp-step-head" @click="expanded[String(index)] = !expanded[String(index)]">
              <span class="wdp-dot">{{ step.failed ? '!' : '✓' }}</span>
              <span class="wdp-step-name">{{ stepTitle(step, index) }}</span>
              <code>{{ stepType(step) }}</code>
              <span class="wdp-duration">{{ step.elapsedMillis ?? step.duration ?? 0 }} ms</span>
              <span>{{ expanded[String(index)] ? '⌃' : '⌄' }}</span>
            </button>
            <div v-if="expanded[String(index)]" class="wdp-step-detail">
              <div class="wdp-io"><b>输入</b><pre v-if="stepInputs(step) !== undefined">{{ pretty(stepInputs(step)) }}</pre><p v-else>当前执行接口未返回该节点输入。</p></div>
              <div class="wdp-io"><b>输出</b><pre>{{ pretty(stepOutputs(step)) || '{}' }}</pre></div>
              <div v-if="step.handle" class="wdp-meta">分支：{{ step.handle }}</div>
              <div v-if="step.error" class="wdp-error">{{ step.error }}</div>
            </div>
          </article>
          <!-- 尾部脉冲行：只提示"还有节点在跑"，不预渲染 pending 节点
               （被分支跳过的节点永不回调、并行执行到达乱序，预渲染会误导） -->
          <div v-if="streamActive" class="wdp-step wdp-step-pending">
            <div class="wdp-step-head">
              <span class="wdp-dot wdp-dot-pending" />
              <span class="wdp-step-name">正在执行下一个节点…</span>
            </div>
          </div>
          <div v-if="!running && result && liveSteps.length === 0" class="wdp-placeholder">接口未返回节点执行记录。</div>
        </div>

        <div v-if="result" class="wdp-final">
          <div class="wdp-section-head"><span>最终输出</span><code v-if="result.runId">{{ result.runId }}</code></div>
          <pre>{{ pretty(result.outputs) || '{}' }}</pre>
          <div v-if="result.error" class="wdp-error">{{ result.error }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.wdp-shell{display:flex;flex-direction:column;width:100%;height:100%;overflow:hidden;border:1px solid #e5e7eb;border-radius:12px;background:#fff;box-shadow:0 18px 48px rgb(15 23 42/.18);color:#1f2937}.wdp-head{display:flex;align-items:center;justify-content:space-between;min-height:46px;padding:0 14px;border-bottom:1px solid #eef0f3;background:#fff}.wdp-head>div{display:flex;align-items:baseline;gap:8px}.wdp-subtitle{font-size:12px;color:#94a3b8}.wdp-icon-btn{border:0;background:transparent;font-size:23px;color:#64748b;cursor:pointer}.wdp-body{flex:1;min-height:0;overflow-y:auto}.wdp-input-section,.wdp-process-section{padding:14px;min-width:0}.wdp-input-section{border-bottom:1px solid #eef0f3}.wdp-section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;font-size:13px;font-weight:650}.wdp-required,.wdp-failed{color:#dc2626}.wdp-success{color:#16a34a}.wdp-form{display:grid;gap:10px}.wdp-field{display:grid;gap:5px;font-size:12px;color:#475569}.wdp-field i{font-style:normal;color:#dc2626}.wdp-field input:not([type=checkbox]),.wdp-field select,.wdp-field textarea{box-sizing:border-box;width:100%;padding:8px 10px;border:1px solid #dbe1e8;border-radius:7px;background:#fff;font:inherit;color:#111827;outline:none}.wdp-field input:focus,.wdp-field select:focus,.wdp-field textarea:focus{border-color:#6366f1;box-shadow:0 0 0 2px rgb(99 102 241/.1)}.wdp-field small,.wdp-empty-input{font-size:11px;color:#94a3b8}.wdp-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}.wdp-run,.wdp-reset,.wdp-stop{padding:8px 14px;border-radius:7px;border:1px solid #dbe1e8;cursor:pointer}.wdp-run{flex:1;border-color:#4f46e5;background:#4f46e5;color:#fff;font-weight:600}.wdp-run:disabled{cursor:not-allowed;opacity:.5}.wdp-stop{border-color:#dc2626;background:#fff;color:#dc2626;font-weight:600}.wdp-reset{background:#fff;color:#475569}.wdp-running-chip{display:inline-flex;align-items:center;gap:6px;font-size:11px;font-weight:400;color:#6366f1}.wdp-running,.wdp-placeholder,.wdp-error,.wdp-aborted{padding:12px;border-radius:8px;font-size:12px}.wdp-running,.wdp-placeholder{background:#f8fafc;color:#64748b}.wdp-error{background:#fef2f2;color:#b91c1c}.wdp-aborted{margin-bottom:8px;background:#fffbeb;color:#b45309}.wdp-spinner{display:inline-block;width:11px;height:11px;border:2px solid #c7d2fe;border-top-color:#4f46e5;border-radius:50%;animation:wdp-spin .8s linear infinite}.wdp-timeline{display:grid;gap:8px}.wdp-step{position:relative;border:1px solid #e5e7eb;border-radius:9px;overflow:hidden}.wdp-step.failed{border-color:#fecaca}.wdp-step-head{display:flex;width:100%;align-items:center;gap:8px;padding:10px;border:0;background:#fafafa;text-align:left;cursor:pointer}.wdp-step-pending{border-style:dashed;border-color:#c7d2fe;background:#fafbff}.wdp-step-pending .wdp-step-head{background:transparent;cursor:default}.wdp-dot{display:inline-grid;place-items:center;width:19px;height:19px;border-radius:50%;background:#dcfce7;color:#15803d;font-size:11px}.failed .wdp-dot{background:#fee2e2;color:#dc2626}.wdp-dot-pending{background:#eef2ff;animation:wdp-pulse 1s ease-in-out infinite}.wdp-step-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:600}.wdp-step-head code,.wdp-final code{font-size:10px;color:#64748b}.wdp-duration{font-size:10px;color:#94a3b8}.wdp-step-detail{display:grid;gap:10px;padding:10px;border-top:1px solid #eef0f3}.wdp-io b{display:block;margin-bottom:5px;font-size:11px;color:#64748b}.wdp-io pre,.wdp-final pre{max-height:220px;margin:0;overflow:auto;padding:9px;border-radius:7px;background:#0f172a;color:#e2e8f0;font:11px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;word-break:break-word}.wdp-io p,.wdp-meta{margin:0;font-size:11px;color:#94a3b8}.wdp-final{margin-top:14px}.wdp-final .wdp-section-head{margin-bottom:8px}@keyframes wdp-spin{to{transform:rotate(360deg)}}@keyframes wdp-pulse{0%,100%{opacity:1}50%{opacity:.3}}
.wdp-waiting{color:#d97706}.wdp-wait-card{display:grid;gap:8px;margin-bottom:10px;padding:12px;border:1px solid #fde68a;border-radius:8px;background:#fffbeb;color:#92400e;font-size:12px}.wdp-wait-card small{display:block}.wdp-wait-card textarea{box-sizing:border-box;width:100%;padding:8px;border:1px solid #fcd34d;border-radius:6px;font:11px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace}
:global(.dark) .wdp-shell,:global(.dark) .wdp-head{background:#18181b;border-color:#3f3f46;color:#f4f4f5}
:global(.dark) .wdp-body{background:#18181b}
:global(.dark) .wdp-head,:global(.dark) .wdp-input-section,:global(.dark) .wdp-step-detail{border-color:#3f3f46}
:global(.dark) .wdp-field{color:#d4d4d8}
:global(.dark) .wdp-field input:not([type=checkbox]),:global(.dark) .wdp-field select,:global(.dark) .wdp-field textarea{border-color:#52525b;background:#27272a;color:#f4f4f5}
:global(.dark) .wdp-placeholder,:global(.dark) .wdp-running{background:#27272a;color:#a1a1aa}
:global(.dark) .wdp-step{border-color:#3f3f46;background:#202023}
:global(.dark) .wdp-step-head{background:#27272a;color:#f4f4f5}
:global(.dark) .wdp-step-pending,:global(.dark) .wdp-step-pending .wdp-step-head{background:#202023}
:global(.dark) .wdp-reset,:global(.dark) .wdp-stop{background:#27272a}
</style>
