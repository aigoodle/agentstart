<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type {
  AgentRunClient,
  AgentRunEvent,
  AgentRunSnapshot,
} from '../agent-run';

const props = withDefaults(defineProps<{
  client: AgentRunClient;
  runId: string;
  pollIntervalMs?: number;
  eventTypes?: string[];
  showDetails?: boolean;
  showInspector?: boolean;
}>(), { pollIntervalMs: 1_000, eventTypes: () => [], showDetails: true, showInspector: true });

const emit = defineEmits<{
  (event: 'settled', snapshot: AgentRunSnapshot): void;
  (event: 'error', error: Error): void;
  (event: 'event-selected', value: AgentRunEvent): void;
}>();

const snapshot = ref<AgentRunSnapshot>();
const events = ref<AgentRunEvent[]>([]);
const loading = ref(false);
const acting = ref(false);
const error = ref('');
const approvalDecisions = ref<Record<string, 'APPROVE' | 'DENY'>>({});
const selectedSequence = ref<number>();
let controller: AbortController | undefined;

const pendingApprovals = computed(() => {
  if (!snapshot.value?.responseJson) return undefined;
  try {
    const response = JSON.parse(snapshot.value.responseJson) as {
      pendingApproval?: { approvalId: string; toolName: string; toolInput: string };
      pendingApprovals?: Array<{ approvalId: string; toolName: string; toolInput: string }>;
    };
    if (response.pendingApprovals?.length) return response.pendingApprovals;
    return response.pendingApproval ? [response.pendingApproval] : undefined;
  } catch {
    return undefined;
  }
});
const pendingApproval = computed(() => pendingApprovals.value?.[0]);
watch(pendingApprovals, (approvals) => {
  approvalDecisions.value = Object.fromEntries(
    (approvals ?? []).map((approval) => [approval.approvalId, 'APPROVE' as const]),
  );
});
const filteredEvents = computed(() => props.eventTypes.length === 0
  ? events.value
  : events.value.filter((event) => props.eventTypes.includes(event.type)));
const selectedEvent = computed(() => events.value.find((event) => event.sequence === selectedSequence.value));
const selectedPayload = computed(() => {
  if (!selectedEvent.value?.payloadJson) return undefined;
  try { return JSON.parse(selectedEvent.value.payloadJson); } catch { return selectedEvent.value.payloadJson; }
});
const durationLabel = computed(() => {
  const started = snapshot.value?.startedAt ? Date.parse(snapshot.value.startedAt) : Number.NaN;
  const endedAt = snapshot.value?.finishedAt ?? snapshot.value?.updatedAt;
  const ended = endedAt ? Date.parse(endedAt) : Number.NaN;
  if (!Number.isFinite(started) || !Number.isFinite(ended) || ended < started) return '';
  const millis = ended - started;
  return millis < 1_000 ? `${millis} ms` : `${(millis / 1_000).toFixed(2)} s`;
});

function eventPayload(event: AgentRunEvent): string {
  if (!event.payloadJson) return '';
  try { return JSON.stringify(JSON.parse(event.payloadJson), null, 2); } catch { return event.payloadJson; }
}

function eventLabel(type: string): string {
  if (type.startsWith('STEP_')) return type.slice(5).replaceAll('_', ' ');
  return type;
}

function selectEvent(event: AgentRunEvent) {
  selectedSequence.value = event.sequence;
  emit('event-selected', event);
}

function formattedJson(value?: string): string {
  if (!value) return '';
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function isRunConflict(cause: unknown): boolean {
  const value = cause as { status?: number; code?: string } | null;
  return value?.status === 409 || [
    'run_concurrent_update', 'invalid_run_transition',
    'agent_run_not_waiting', 'agent_run_not_paused',
  ].includes(value?.code ?? '');
}

async function handleActionFailure(cause: unknown) {
  let failure = cause instanceof Error ? cause : new Error(String(cause));
  if (isRunConflict(cause)) {
    await load();
    failure = new Error('该任务已由其他操作人处理，运行状态已刷新');
  }
  error.value = failure.message;
  emit('error', failure);
}

async function load() {
  controller?.abort();
  controller = new AbortController();
    events.value = [];
    selectedSequence.value = undefined;
  error.value = '';
  loading.value = true;
  try {
    const initial = await props.client.get(props.runId);
    snapshot.value = initial;
    const iterator = props.client.watch(props.runId, {
      pollIntervalMs: props.pollIntervalMs,
      signal: controller.signal,
    });
    while (true) {
      const next = await iterator.next();
      if (next.done) {
        snapshot.value = next.value;
        emit('settled', next.value);
        break;
      }
      events.value.push(...next.value.filter((candidate) =>
        !events.value.some((existing) => existing.sequence === candidate.sequence)));
    }
  } catch (cause) {
    if (controller.signal.aborted) return;
    const failure = cause instanceof Error ? cause : new Error(String(cause));
    error.value = failure.message;
    emit('error', failure);
  } finally {
    loading.value = false;
  }
}

async function cancelRun() {
  acting.value = true;
  try {
    snapshot.value = await props.client.cancel(props.runId);
    controller?.abort();
    emit('settled', snapshot.value);
  } catch (cause) {
    await handleActionFailure(cause);
  } finally { acting.value = false; }
}

async function decide(decision: 'APPROVE' | 'DENY') {
  const approvals = pendingApprovals.value;
  if (!approvals?.length) return;
  acting.value = true;
  try {
    if (approvals.length === 1) {
      await props.client.resume(props.runId, approvals[0].approvalId, decision);
    } else {
      if (!props.client.resumeMany) throw new Error('当前宿主 AgentRunClient 不支持批量审批');
      await props.client.resumeMany(props.runId, Object.fromEntries(
        approvals.map((approval) => [approval.approvalId, decision]),
      ));
    }
    await load();
  } catch (cause) {
    await handleActionFailure(cause);
  } finally { acting.value = false; }
}

async function submitApprovalDecisions() {
  const approvals = pendingApprovals.value;
  if (!approvals?.length) return;
  if (approvals.length === 1) return decide(approvalDecisions.value[approvals[0].approvalId] ?? 'APPROVE');
  acting.value = true;
  try {
    if (!props.client.resumeMany) throw new Error('当前宿主 AgentRunClient 不支持批量审批');
    await props.client.resumeMany(props.runId, { ...approvalDecisions.value });
    await load();
  } catch (cause) {
    await handleActionFailure(cause);
  } finally { acting.value = false; }
}

watch(() => props.runId, load, { immediate: true });
onBeforeUnmount(() => controller?.abort());
defineExpose({ refresh: load, cancel: cancelRun, snapshot, events });
</script>

<template>
  <section class="agent-run-timeline">
    <header>
      <slot name="header" :snapshot="snapshot" :run-id="runId">
        <div><strong>Agent Run</strong><code>{{ runId }}</code><small v-if="durationLabel">{{ durationLabel }}</small></div>
        <span class="status" :data-status="snapshot?.status">{{ snapshot?.status ?? 'LOADING' }}</span>
      </slot>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <slot v-if="pendingApproval" name="approval" :approval="pendingApproval" :approvals="pendingApprovals"
          :approve="() => decide('APPROVE')" :deny="() => decide('DENY')" :acting="acting">
      <div class="approval">
        <div>{{ pendingApprovals?.length === 1 ? '工具等待审批' : `${pendingApprovals?.length} 个工具等待批量审批` }}</div>
        <div v-for="approval in pendingApprovals" :key="approval.approvalId" class="approval-item">
          <div class="approval-item-head">
            <strong>{{ approval.toolName }}</strong>
            <select v-if="(pendingApprovals?.length ?? 0) > 1" v-model="approvalDecisions[approval.approvalId]" :disabled="acting">
              <option value="APPROVE">通过</option>
              <option value="DENY">拒绝</option>
            </select>
          </div>
          <pre>{{ approval.toolInput }}</pre>
        </div>
        <template v-if="pendingApprovals?.length === 1">
          <button :disabled="acting" @click="decide('APPROVE')">通过</button>
          <button :disabled="acting" @click="decide('DENY')">拒绝</button>
        </template>
        <button v-else :disabled="acting" @click="submitApprovalDecisions">提交审批决定</button>
      </div>
    </slot>

    <details v-if="showDetails && snapshot" class="run-details">
      <summary>运行上下文</summary>
      <dl>
        <div><dt>Agent</dt><dd>{{ snapshot.agentId }}</dd></div>
        <div><dt>Conversation</dt><dd>{{ snapshot.conversationId || '-' }}</dd></div>
        <div><dt>Version</dt><dd>{{ snapshot.version }}</dd></div>
        <div><dt>Tenant</dt><dd>{{ snapshot.tenantId }}</dd></div>
      </dl>
      <details v-if="snapshot.requestJson"><summary>请求快照</summary><pre>{{ formattedJson(snapshot.requestJson) }}</pre></details>
      <details v-if="snapshot.definitionJson"><summary>Agent 快照</summary><pre>{{ formattedJson(snapshot.definitionJson) }}</pre></details>
      <details v-if="snapshot.responseJson"><summary>响应快照</summary><pre>{{ formattedJson(snapshot.responseJson) }}</pre></details>
      <p v-if="snapshot.error" class="error">{{ snapshot.error }}</p>
    </details>

    <div class="timeline-workspace" :class="{ 'has-inspector': showInspector && selectedEvent }">
    <ol>
      <li v-for="event in filteredEvents" :key="event.sequence"
          :class="{ selected: selectedSequence === event.sequence }" tabindex="0"
          @click="selectEvent(event)" @keydown.enter="selectEvent(event)">
        <slot name="event" :event="event" :payload="eventPayload(event)">
          <div><code>#{{ event.sequence }}</code><strong>{{ eventLabel(event.type) }}</strong><time>{{ event.createdAt }}</time></div>
          <pre v-if="event.payloadJson">{{ eventPayload(event) }}</pre>
        </slot>
      </li>
    </ol>
    <aside v-if="showInspector && selectedEvent" class="event-inspector">
      <slot name="inspector" :event="selectedEvent" :payload="selectedPayload" :close="() => selectedSequence = undefined">
        <header><strong>运行状态检查</strong><button aria-label="关闭检查器" @click="selectedSequence = undefined">×</button></header>
        <dl>
          <div><dt>序号</dt><dd>#{{ selectedEvent.sequence }}</dd></div>
          <div><dt>类型</dt><dd>{{ eventLabel(selectedEvent.type) }}</dd></div>
          <div><dt>时间</dt><dd>{{ selectedEvent.createdAt || '-' }}</dd></div>
        </dl>
        <pre v-if="selectedEvent.payloadJson">{{ eventPayload(selectedEvent) }}</pre>
        <p v-else class="empty">该事件没有状态负载</p>
      </slot>
    </aside>
    </div>
    <slot v-if="!loading && filteredEvents.length === 0" name="empty">
      <p class="empty">暂无运行事件</p>
    </slot>

    <footer>
      <slot name="actions" :snapshot="snapshot" :cancel="cancelRun" :refresh="load" :acting="acting">
        <button v-if="snapshot && ['CREATED', 'RUNNING', 'WAITING_APPROVAL'].includes(snapshot.status)"
                :disabled="acting" @click="cancelRun">取消运行</button>
        <button v-if="!loading" :disabled="acting" @click="load">刷新</button>
        <span v-if="loading">正在同步…</span>
      </slot>
    </footer>
  </section>
</template>

<style scoped>
.agent-run-timeline { display:grid; gap:12px; color:#1f2937; font-size:13px }
header, header>div, li>div, footer { display:flex; align-items:center; gap:10px }
header { justify-content:space-between }
header code { margin-left:8px; color:#64748b }
header small { margin-left:8px; color:#94a3b8 }
.status { border-radius:999px; padding:3px 9px; background:#e2e8f0; font-weight:600 }
.status[data-status="COMPLETED"] { background:#dcfce7; color:#166534 }
.status[data-status="FAILED"], .status[data-status="TIMED_OUT"] { background:#fee2e2; color:#991b1b }
.status[data-status="WAITING_APPROVAL"] { background:#fef3c7; color:#92400e }
ol { display:grid; gap:8px; margin:0; padding:0; list-style:none }
li { border-left:2px solid #cbd5e1; padding:6px 10px; cursor:pointer; outline:none }
li:hover { background:#f8fafc }
li.selected, li:focus-visible { border-left-color:#2563eb; background:#eff6ff }
li time { margin-left:auto; color:#94a3b8 }
.timeline-workspace { display:grid; gap:12px; min-width:0 }
.timeline-workspace.has-inspector { grid-template-columns:minmax(0,1fr) minmax(260px,38%) }
.event-inspector { align-self:start; position:sticky; top:8px; min-width:0; padding:12px; border:1px solid #cbd5e1; border-radius:8px; background:#fff }
.event-inspector header { display:flex; justify-content:space-between }
.event-inspector header button { margin:0; padding:0 6px; border:0; font-size:18px }
.event-inspector dl { display:grid; gap:7px; margin:12px 0 }
.event-inspector dl div { display:grid; grid-template-columns:52px minmax(0,1fr); gap:8px }
.event-inspector dt { color:#64748b }
.event-inspector dd { min-width:0; margin:0; overflow-wrap:anywhere }
pre { overflow:auto; margin:6px 0 0; padding:8px; border-radius:6px; background:#f8fafc; white-space:pre-wrap }
.approval { padding:12px; border:1px solid #f59e0b; border-radius:8px; background:#fffbeb }
.approval-item { margin-top:8px; padding-top:8px; border-top:1px solid #fde68a }
.approval-item-head { display:flex; align-items:center; justify-content:space-between; gap:12px }
.run-details { padding:10px; border:1px solid #e2e8f0; border-radius:8px; background:#fff }
.run-details summary { cursor:pointer; font-weight:600 }
.run-details dl { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:8px; margin:10px 0 }
.run-details dl div { min-width:0 }
.run-details dt { color:#64748b; font-size:11px }
.run-details dd { overflow:hidden; margin:2px 0 0; text-overflow:ellipsis; white-space:nowrap }
.empty { margin:0; padding:16px; color:#94a3b8; text-align:center }
button { margin-right:8px; padding:5px 10px; border:1px solid #cbd5e1; border-radius:6px; background:white; cursor:pointer }
button:disabled { cursor:not-allowed; opacity:.5 }
.error { color:#b91c1c }
@media (max-width:720px) { .timeline-workspace.has-inspector { grid-template-columns:1fr } .event-inspector { position:static } }
</style>
