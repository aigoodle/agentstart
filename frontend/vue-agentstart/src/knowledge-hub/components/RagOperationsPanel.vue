<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { defaultRagPolicy, type DatasetDetailHub, type RagCapabilities, type RagResult } from '../types';
import RagPolicyEditor from './RagPolicyEditor.vue';
import type { IndexVersionWire, IngestionJobWire, RetrievalEvaluationReportWire } from '../../client/knowledge';

const props = defineProps<{ datasetId: string; hub: DatasetDetailHub }>();
const versions = ref<IndexVersionWire[]>([]);
const poisoned = ref<IngestionJobWire[]>([]);
const report = ref<RetrievalEvaluationReportWire | null>(null);
const loading = ref(false);
const error = ref('');
const ragQuery = ref('');
const ragPolicy = ref(defaultRagPolicy());
const ragCapabilities = ref<RagCapabilities>();
const ragResult = ref<RagResult>();
const evaluationJson = ref(JSON.stringify({
  name: 'golden-set', version: 'v1', experimentName: 'current', topK: 5,
  configuration: {},
  cases: [{ id: 'q1', query: '请输入测试问题', relevantSegmentIds: [], noAnswerExpected: false, citedSegmentIds: [] }],
}, null, 2));

async function refresh() {
  loading.value = true; error.value = '';
  try {
    versions.value = props.hub.listIndexVersions ? await props.hub.listIndexVersions(props.datasetId) : [];
    if (props.hub.getRagCapabilities) ragCapabilities.value = await props.hub.getRagCapabilities();
    try {
      poisoned.value = props.hub.listPoisonedIngestionJobs
        ? await props.hub.listPoisonedIngestionJobs(props.datasetId) : [];
    } catch { poisoned.value = []; }
  } catch (e) { error.value = e instanceof Error ? e.message : String(e); }
  finally { loading.value = false; }
}

async function runRag() {
  if (!props.hub.retrieveRag || !ragQuery.value.trim()) return;
  loading.value = true; error.value = '';
  try { ragResult.value = await props.hub.retrieveRag(props.datasetId, { query: ragQuery.value, policy: ragPolicy.value }); }
  catch (e) { error.value = e instanceof Error ? e.message : String(e); }
  finally { loading.value = false; }
}

async function activate(id: string) {
  if (!props.hub.activateIndexVersion) return;
  await props.hub.activateIndexVersion(props.datasetId, id);
  await refresh();
}

async function replay(documentId: string) {
  if (!props.hub.replayIngestionJob) return;
  await props.hub.replayIngestionJob(props.datasetId, documentId);
  await refresh();
}

async function evaluate() {
  if (!props.hub.evaluateRetrieval) return;
  error.value = '';
  try { report.value = await props.hub.evaluateRetrieval(props.datasetId, JSON.parse(evaluationJson.value)); }
  catch (e) { error.value = e instanceof Error ? e.message : String(e); }
}

onMounted(refresh);
</script>

<template>
  <div class="kh-ragops">
    <header><div><h2>RAG 运行与质量</h2><p>索引版本、检索评测和失败摄取任务。</p></div><button @click="refresh">刷新</button></header>
    <p v-if="error" class="kh-ragops-error">{{ error }}</p>
    <section>
      <h3>组合式 RAG 调试</h3>
      <RagPolicyEditor v-model="ragPolicy" :capabilities="ragCapabilities" />
      <textarea v-model="ragQuery" rows="3" placeholder="输入检索问题" />
      <button class="kh-primary" :disabled="!hub.retrieveRag || loading" @click="runRag">执行 RAG</button>
      <div v-if="ragResult" class="kh-results">
        <small>规划 {{ ragResult.queryPlan.queries.length }} 个查询 · 命中 {{ ragResult.evidence.length }} 条 · {{ Object.values(ragResult.stageLatencyMillis).reduce((a,b)=>a+b,0) }} ms</small>
        <article v-for="item in ragResult.evidence" :key="item.segmentId" class="kh-row"><div><strong>{{ item.metadata?.documentName || item.documentId }}</strong><small>{{ item.context || item.content }}</small></div><b>{{ item.retrievalScore.toFixed(4) }}</b></article>
      </div>
    </section>
    <section>
      <h3>索引版本</h3>
      <p v-if="!loading && versions.length === 0" class="kh-muted">暂无版本化索引，当前使用兼容模式。</p>
      <article v-for="item in versions" :key="item.id" class="kh-row">
        <div><strong>{{ item.version }}</strong><span :data-status="item.status">{{ item.status }}</span>
          <small>{{ item.embeddingModelVersion || '未记录 Embedding 版本' }} · {{ item.chunkingRuleVersion || '未记录切分版本' }}</small></div>
        <button v-if="item.status === 'RETIRED' || item.status === 'REBUILDING'" @click="activate(item.id)">
          {{ item.status === 'RETIRED' ? '回退至此版本' : '激活' }}
        </button>
      </article>
    </section>
    <section>
      <h3>检索评测集</h3>
      <textarea v-model="evaluationJson" rows="11" spellcheck="false" />
      <button class="kh-primary" :disabled="!hub.evaluateRetrieval" @click="evaluate">运行评测</button>
      <div v-if="report" class="kh-metrics">
        <div v-for="(value, key) in report.metrics" :key="key"><span>{{ key }}</span><strong>{{ Number(value).toFixed(3) }}</strong></div>
      </div>
    </section>
    <section>
      <h3>隔离任务</h3>
      <p v-if="poisoned.length === 0" class="kh-muted">没有 Poisoned 摄取任务。</p>
      <article v-for="job in poisoned" :key="job.documentId" class="kh-row">
        <div><strong>{{ job.filename || job.documentId }}</strong><small>{{ job.lastError || '未知错误' }} · 重试 {{ job.retryCount || 0 }} 次</small></div>
        <button @click="replay(job.documentId)">人工重放</button>
      </article>
    </section>
  </div>
</template>

<style scoped>
.kh-ragops{padding:24px;display:grid;gap:18px;color:#172033}.kh-ragops header{display:flex;justify-content:space-between;align-items:flex-start}.kh-ragops h2,.kh-ragops h3{margin:0 0 6px}.kh-ragops p{margin:0;color:#667085}.kh-ragops section{border:1px solid #e5e7eb;border-radius:10px;padding:16px;display:grid;gap:10px;background:#fff}.kh-row{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:10px;border:1px solid #eef0f3;border-radius:8px}.kh-row div{display:grid;gap:4px}.kh-row span{margin-left:8px;font-size:11px;color:#475467}.kh-row small{color:#667085}.kh-ragops textarea{width:100%;box-sizing:border-box;border:1px solid #d0d5dd;border-radius:8px;padding:10px;font:12px/1.5 monospace}.kh-ragops button{border:1px solid #d0d5dd;border-radius:7px;background:#fff;padding:7px 12px;cursor:pointer}.kh-ragops .kh-primary{background:#155eef;color:#fff;border-color:#155eef;width:max-content}.kh-metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:8px}.kh-metrics div{background:#f8fafc;padding:10px;border-radius:8px;display:grid}.kh-metrics span,.kh-muted{font-size:12px;color:#667085}.kh-ragops-error{color:#b42318!important}
</style>
