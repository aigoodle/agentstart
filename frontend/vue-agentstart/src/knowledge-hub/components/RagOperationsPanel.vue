<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { defaultRagPolicy, type DatasetDetailHub, type RagCapabilities, type RagPolicy, type RagResult, type RecentQuery } from '../types';
import type { RetrievalConfig } from '../types/dataset';
import RagPolicyEditor from './RagPolicyEditor.vue';
import type { IndexVersionWire, IngestionJobWire, RetrievalEvaluationReportWire } from '../../client/knowledge';

const props = defineProps<{ datasetId: string; hub: DatasetDetailHub }>();
const emit = defineEmits<{ (e: 'saved'): void }>();
const policy = ref(defaultRagPolicy());
const capabilities = ref<RagCapabilities>();
const rerankModels = ref<Array<{ id: string; label: string }>>([]);
const ready = ref(false);
const initializing = ref(false);
const running = ref(false);
const saving = ref(false);
const operating = ref(false);
const saved = ref(false);
const persistedPolicy = ref('');
const error = ref('');
const operationsError = ref('');
const query = ref('');
const result = ref<RagResult>();
const executedPolicy = ref<RagPolicy>();
const resultOutdated = computed(() => !!result.value && (query.value.trim() !== result.value.originalQuery || JSON.stringify(policy.value) !== JSON.stringify(executedPolicy.value)));
const history = ref<RecentQuery[]>([]);
const versions = ref<IndexVersionWire[]>([]);
const poisoned = ref<IngestionJobWire[]>([]);
const report = ref<RetrievalEvaluationReportWire | null>(null);
const evaluationJson = ref(JSON.stringify({
  name: 'golden-set', version: 'v1', experimentName: 'current', topK: 5,
  configuration: {},
  cases: [{ id: 'q1', query: '请输入测试问题', relevantSegmentIds: [], noAnswerExpected: false, citedSegmentIds: [] }],
}, null, 2));
const dirty = computed(() => ready.value && JSON.stringify(policy.value) !== persistedPolicy.value);
const locked = computed(() => initializing.value || running.value || saving.value);
const channelNames: Record<string, string> = { VECTOR: '向量', SPARSE: '全文', RAPTOR: '层次摘要', GRAPH: '图谱', MULTIMODAL: '多模态' };
const methodLabel = computed(() => policy.value.retrieval.channels.map(c => channelNames[c] || c).join(' + '));
const latency = computed(() => Object.values(result.value?.stageLatencyMillis || {}).reduce((sum, ms) => sum + ms, 0));
const stageNames: Record<string, string> = { planning: '查询规划', retrieval: '召回', ranking: '融合与重排', context: '上下文' };


function channelScores(metadata?: Record<string, unknown>): Record<string, number> {
  const scores = metadata?.ragChannelScores;
  if (!scores || typeof scores !== 'object') return {};
  return Object.fromEntries(Object.entries(scores).filter((entry): entry is [string, number] => typeof entry[1] === 'number'));
}

function restorePolicy(config: RetrievalConfig): RagPolicy {
  const defaults = defaultRagPolicy();
  defaults.retrieval.scoreThreshold = config.scoreThreshold ?? 0;
  defaults.retrieval.maxChunksPerDocument = config.maxChunksPerDocument ?? 0;
  defaults.context.neighborWindow = config.neighborWindow ?? 0;
  if (config.ragPolicy) {
    // Older saved policies may lack newly introduced recall fields.
    return Object.fromEntries(Object.entries(defaults).map(([section, values]) => [section, {
      ...values, ...(config.ragPolicy as unknown as Record<string, object>)[section],
    }])) as unknown as RagPolicy;
  }
  const topK = config.topK || defaults.rerank.topN;
  defaults.retrieval.channels = config.method === 'FULL_TEXT' ? ['SPARSE'] : config.method === 'HYBRID' ? ['VECTOR', 'SPARSE'] : ['VECTOR'];
  defaults.retrieval.candidateLimitPerQuery = Math.max(topK, topK * (config.recallMultiplier || 6));
  defaults.retrieval.scoreThreshold = config.scoreThreshold ?? 0;
  defaults.retrieval.maxChunksPerDocument = config.maxChunksPerDocument ?? 0;
  defaults.fusion.method = defaults.retrieval.channels.length > 1 ? 'RRF' : 'NONE';
  defaults.rerank.enabled = !!config.rerankEnabled;
  defaults.rerank.provider = config.rerankModelId ? 'model' : 'score';
  defaults.rerank.model = config.rerankModelId;
  defaults.rerank.candidateLimit = config.rerankPoolSize || defaults.retrieval.candidateLimitPerQuery;
  defaults.rerank.topN = topK;
  defaults.budget.maxEvidenceItems = Math.max(topK, defaults.budget.maxEvidenceItems);
  defaults.context.neighborWindow = config.neighborWindow || 0;
  return defaults;
}

async function initialize() {
  initializing.value = true; ready.value = false; error.value = '';
  try {
    const [dataset, caps, models, recent] = await Promise.all([
      props.hub.loadDataset(props.datasetId),
      props.hub.getRagCapabilities?.(),
      props.hub.listRerankModels?.().catch(() => []),
      props.hub.listRecallHistory(props.datasetId, 20).catch(() => []),
    ]);
    policy.value = restorePolicy(dataset.retrievalConfigJson ? JSON.parse(dataset.retrievalConfigJson) : {});
    capabilities.value = caps;
    rerankModels.value = models || [];
    history.value = recent;
    persistedPolicy.value = JSON.stringify(policy.value);
    ready.value = true;
  } catch (e) { error.value = e instanceof Error ? e.message : String(e); }
  finally { initializing.value = false; }
}

function validationError(): string {
  const value = policy.value;
  if (!value.retrieval.channels.length) return '请至少选择一个召回通道。';
  if (capabilities.value && value.retrieval.channels.some(c => !capabilities.value!.retrievalChannels.includes(c))) return '当前策略包含后端未启用的召回通道，请调整检索设置。';
  if (capabilities.value && !capabilities.value.queryStrategies.includes(value.queryPlanning.strategy)) return '当前查询规划策略未启用，请选择可用策略。';
  if (value.queryPlanning.strategy !== 'DIRECT' && !value.queryPlanning.modelId?.trim()) return '请填写查询规划模型 ID。';
  if (value.rerank.enabled && value.rerank.provider === 'model' && !value.rerank.model) return '请选择重排模型。';
  return '';
}

async function savePolicy() {
  error.value = validationError();
  if (error.value || locked.value || !ready.value) return;
  const draft: RagPolicy = JSON.parse(JSON.stringify(policy.value));
  saving.value = true; saved.value = false;
  try {
    const dataset = await props.hub.loadDataset(props.datasetId);
    const current = dataset.retrievalConfigJson ? JSON.parse(dataset.retrievalConfigJson) : {};
    // One canonical policy drives both testing and saved defaults. Mirror compatible
    // fields for callers that still use the traditional retrieval API.
    await props.hub.updateDataset(props.datasetId, { retrievalConfig: {
      ...current, ragPolicy: draft, method: 'VECTOR', topK: draft.rerank.topN,
      scoreThreshold: draft.retrieval.scoreThreshold ?? 0,
      maxChunksPerDocument: draft.retrieval.maxChunksPerDocument ?? 0,
      rerankEnabled: draft.rerank.enabled, rerankModelId: draft.rerank.model || null,
      rerankerName: draft.rerank.provider === 'model' ? 'model' : 'weighted',
      rerankPoolSize: draft.rerank.candidateLimit,
      neighborWindow: draft.context.neighborWindow,
    } });
    persistedPolicy.value = JSON.stringify(draft); saved.value = true; emit('saved');
  } catch (e) { error.value = e instanceof Error ? e.message : String(e); }
  finally { saving.value = false; }
}

async function runQuery(value = query.value) {
  if (!ready.value || locked.value || !props.hub.retrieveRag || !value.trim()) return;
  error.value = validationError();
  if (error.value) return;
  query.value = value;
  const draft: RagPolicy = JSON.parse(JSON.stringify(policy.value));
  running.value = true; result.value = undefined;
  try {
    executedPolicy.value = draft;
    result.value = await props.hub.retrieveRag(props.datasetId, { query: value.trim(), policy: draft });
    // Show the new run immediately; refresh from the durable backend history when available.
    const local: RecentQuery = { id: `run-${Date.now()}`, query: value.trim(), method: methodLabel.value,
      hitCount: result.value.evidence.length, at: new Date().toLocaleTimeString() };
    history.value = [local, ...history.value].slice(0, 20);
    try {
      const recent = await props.hub.listRecallHistory(props.datasetId, 20);
      if (recent.some(h => h.query === local.query)) history.value = recent;
    } catch { /* Keep the successful run visible if history is temporarily unavailable. */ }
  } catch (e) { error.value = e instanceof Error ? e.message : String(e); }
  finally { running.value = false; }
}

async function refreshOperations() {
  operating.value = true; operationsError.value = '';
  try {
    [versions.value, poisoned.value] = await Promise.all([
      props.hub.listIndexVersions?.(props.datasetId) || Promise.resolve([]),
      props.hub.listPoisonedIngestionJobs?.(props.datasetId) || Promise.resolve([]),
    ]);
  } catch (e) { operationsError.value = e instanceof Error ? e.message : String(e); }
  finally { operating.value = false; }
}
async function operate(action: () => Promise<unknown>) {
  operating.value = true; operationsError.value = '';
  try { await action(); await refreshOperations(); }
  catch (e) { operationsError.value = e instanceof Error ? e.message : String(e); }
  finally { operating.value = false; }
}
async function evaluate() {
  if (!props.hub.evaluateRetrieval) return;
  operating.value = true; operationsError.value = '';
  try {
    report.value = await props.hub.evaluateRetrieval(props.datasetId, {
      ...JSON.parse(evaluationJson.value), policy: JSON.parse(JSON.stringify(policy.value)),
      topK: policy.value.rerank.topN,
    });
  } catch (e) { operationsError.value = e instanceof Error ? e.message : String(e); }
  finally { operating.value = false; }
}
onMounted(initialize);
</script>

<template>
  <div class="kh-ragops">
    <header class="kh-workbench-header">
      <div><h2>RAG 运行</h2><p>配置检索策略，输入问题，查看知识库返回的证据。</p></div>
      <div class="kh-actions">
        <span v-if="dirty" class="kh-draft">策略有未保存的修改</span>
        <span v-else-if="saved" role="status">默认策略已保存</span>
        <button class="kh-primary" :disabled="!ready || locked" @click="savePolicy">{{ saving ? '保存中…' : '保存为默认策略' }}</button>
      </div>
    </header>
    <p v-if="error" role="alert" class="kh-error">{{ error }}</p>
    <div v-if="initializing" class="kh-empty" role="status">正在加载知识库策略…</div>
    <button v-else-if="!ready" @click="initialize">重新加载</button>
    <div v-else class="kh-workbench">
      <div class="kh-config-column">
        <section class="kh-query-card">
          <div class="kh-card-title"><h3>检索测试</h3><span class="kh-tag">{{ methodLabel }}</span></div>
          <p>本次检索直接使用下方设置，保存后可在业务中复用。</p>
          <label class="kh-query-label" for="kh-rag-query">检索问题</label>
          <textarea id="kh-rag-query" v-model="query" :disabled="locked" rows="5" maxlength="4000" placeholder="输入需要从知识库中查找的问题…" @keydown.ctrl.enter.prevent="runQuery()" @keydown.meta.enter.prevent="runQuery()" />
          <div class="kh-actions kh-query-actions"><small>{{ query.length }}/4000 · Ctrl / ⌘ + Enter</small>
            <button class="kh-primary" :disabled="!hub.retrieveRag || locked || !query.trim()" @click="runQuery()">{{ running ? '检索中…' : '执行检索' }}</button>
          </div>
          <p v-if="!hub.retrieveRag" class="kh-error">当前宿主未连接 RAG 检索接口。</p>
        </section>
        <section class="kh-strategy-card">
          <div class="kh-card-title"><h3>检索策略</h3><span class="kh-tag">{{ dirty ? '当前草稿' : '知识库默认策略' }}</span></div>
          <fieldset class="kh-editor-fieldset" :disabled="locked">
            <RagPolicyEditor v-model="policy" :capabilities="capabilities" :rerank-models="rerankModels" />
          </fieldset>
        </section>
        <section class="kh-history-card">
          <div class="kh-card-title"><h3>最近检索</h3><small>点击问题，使用当前策略重新检索</small></div>
          <p v-if="!history.length">暂无检索记录。</p>
          <button v-for="item in history" :key="item.id" class="kh-history-row" :disabled="locked" @click="runQuery(item.query)">
            <span class="kh-history-query">{{ item.query }}</span><small>{{ item.hitCount }} 命中 · {{ item.at }}</small>
          </button>
        </section>
      </div>
      <section class="kh-results-column" :aria-busy="running" aria-label="检索结果">
        <div class="kh-card-title"><h3>检索结果 <span v-if="result" class="kh-tag">{{ result.evidence.length }} 条证据</span></h3><small v-if="result">{{ latency }} ms</small></div>
        <div v-if="running" class="kh-empty" role="status"><span class="kh-empty-symbol">◌</span>正在规划查询并检索知识库…</div>
        <div v-else-if="!result" class="kh-empty"><span class="kh-empty-symbol">⌕</span><strong>检索证据将在这里展示</strong><p>调整左侧设置并执行检索，查看命中片段、来源和运行过程。</p></div>
        <template v-else>
          <p v-if="resultOutdated" class="kh-draft">设置或问题已修改；再次执行检索后更新结果。</p>
          <div class="kh-run-summary">
            <p>本次问题：{{ result.originalQuery }}</p>
            <p v-if="executedPolicy">本次策略：{{ executedPolicy.retrieval.channels.map(c => channelNames[c] || c).join(' + ') }} · Top K {{ executedPolicy.rerank.topN }} · {{ executedPolicy.rerank.enabled ? '启用重排' : '未启用重排' }}</p>
            <div class="kh-stage-list"><span v-for="(ms, stage) in result.stageLatencyMillis" :key="stage">{{ stageNames[stage] || stage }} <b>{{ ms }} ms</b></span></div>
            <details class="kh-query-plan"><summary>查询规划 · {{ result.queryPlan.queries.length }} 个</summary>
              <ol><li v-for="item in result.queryPlan.queries" :key="item.id"><span class="kh-tag">{{ item.type }}</span> {{ item.query }}</li></ol>
            </details>
          </div>
          <div v-if="!result.evidence.length" class="kh-empty"><strong>没有找到匹配片段</strong><p>可以改写问题、增加候选数量，或降低向量相似度阈值后重试。</p></div>
          <article v-for="(item, index) in result.evidence" :key="`${item.datasetId}:${item.segmentId}`" class="kh-evidence">
            <div class="kh-evidence-head"><span class="kh-hit-number">{{ index + 1 }}</span><strong>{{ item.metadata?.documentName || item.documentId || '未命名文档' }}</strong><span class="kh-score">得分 {{ (item.rerankScore ?? item.fusionScore ?? item.retrievalScore).toFixed(3) }}</span></div>
            <div class="kh-evidence-metadata"><span v-if="item.metadata?.heading">{{ item.metadata.heading }}</span><span v-if="item.metadata?.blockType">{{ item.metadata.blockType }}</span><span v-if="item.position != null">片段位置 {{ item.position }}</span></div>
            <div class="kh-evidence-content">{{ item.content }}</div>
            <details v-if="item.context && item.context !== item.content" class="kh-context"><summary>查看扩展上下文</summary><div class="kh-evidence-content">{{ item.context }}</div></details>
            <footer><span v-for="(score, channel) in channelScores(item.metadata)" :key="channel">{{ channelNames[channel] || channel }} {{ score.toFixed(3) }}</span><span>召回 {{ item.retrievalScore.toFixed(3) }}</span><span v-if="item.fusionScore != null">融合 {{ item.fusionScore.toFixed(3) }}</span><span v-if="item.rerankScore != null">重排 {{ item.rerankScore.toFixed(3) }}</span><span v-if="item.matchedQueryIds.length">匹配查询 {{ item.matchedQueryIds.join('、') }}</span></footer>
          </article>
        </template>
      </section>
    </div>
    <details class="kh-operations" @toggle="($event.target as HTMLDetailsElement).open && refreshOperations()">
      <summary>质量评测与索引运维 <small>使用当前检索策略评测，管理索引和失败任务</small></summary>
      <p v-if="operationsError" role="alert" class="kh-error">{{ operationsError }}</p>
      <div class="kh-operations-grid">
        <section><h3>检索评测集</h3><p>评测复用上方策略与 Top K。</p><textarea v-model="evaluationJson" rows="10" spellcheck="false" aria-label="评测集 JSON" />
          <button class="kh-primary" :disabled="!ready || !hub.evaluateRetrieval || operating || locked" @click="evaluate">{{ operating ? '处理中…' : '运行评测' }}</button>
          <div v-if="report" class="kh-metrics"><div v-for="(value, key) in report.metrics" :key="key"><span>{{ key }}</span><strong>{{ Number(value).toFixed(3) }}</strong></div></div>
        </section>
        <section><div class="kh-card-title"><h3>索引与摄取任务</h3><button :disabled="operating" @click="refreshOperations">刷新状态</button></div>
          <p v-if="!versions.length">暂无版本化索引，当前使用兼容模式。</p>
          <article v-for="item in versions" :key="item.id" class="kh-row"><div><strong>{{ item.version }}</strong><small>{{ item.status }} · {{ item.embeddingModelVersion || '未记录 Embedding 版本' }}</small></div><button v-if="hub.activateIndexVersion && (item.status === 'RETIRED' || item.status === 'REBUILDING')" :disabled="operating" @click="operate(() => hub.activateIndexVersion!(datasetId, item.id))">{{ item.status === 'RETIRED' ? '回退至此版本' : '激活' }}</button></article>
          <h3>隔离任务</h3><p v-if="!poisoned.length">没有隔离的摄取任务。</p>
          <article v-for="job in poisoned" :key="job.documentId" class="kh-row"><div><strong>{{ job.filename || job.documentId }}</strong><small>{{ job.lastError || '未知错误' }} · 重试 {{ job.retryCount || 0 }} 次</small></div><button v-if="hub.replayIngestionJob" :disabled="operating" @click="operate(() => hub.replayIngestionJob!(datasetId, job.documentId))">人工重放</button></article>
        </section>
      </div>
    </details>
  </div>
</template>

<style scoped>
.kh-ragops { padding: 22px; display: flex; flex-direction: column; gap: 18px; flex: 1; min-height: 0; overflow-y: auto; color: var(--kh-color-text-primary); }
.kh-ragops *, .kh-ragops *::before { box-sizing: border-box; }
.kh-workbench-header, .kh-actions, .kh-card-title, .kh-evidence-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.kh-ragops h2 { margin: 0 0 5px; font-size: 21px; letter-spacing: -.4px; }
.kh-ragops h3 { margin: 0; font-size: 14px; font-weight: 600; }
.kh-ragops p, .kh-ragops small { margin: 0; color: var(--kh-color-text-tertiary); font-size: 12px; line-height: 1.6; }
.kh-actions { justify-content: flex-end; font-size: 12px; color: var(--kh-color-primary); }
.kh-workbench { display: grid; grid-template-columns: minmax(320px, 5fr) minmax(0, 7fr); gap: 18px; align-items: start; }
.kh-config-column { min-width: 0; display: grid; gap: 14px; }
.kh-ragops section { min-width: 0; border: 1px solid var(--kh-color-border); border-radius: 12px; padding: 16px; display: grid; gap: 12px; background: var(--kh-color-surface); }
.kh-ragops .kh-query-card { border-color: var(--kh-color-primary); background: var(--kh-color-surface); }
.kh-editor-fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.kh-ragops textarea { width: 100%; border: 1px solid var(--kh-input-border); border-radius: 8px; padding: 12px; font-family: inherit; font-size: 13px; line-height: 1.65; background: var(--kh-input-bg); color: var(--kh-color-text-primary); resize: vertical; }
.kh-query-label { font-size: 12px; font-weight: 500; }
.kh-query-actions { justify-content: space-between; }
.kh-ragops button { border: 1px solid var(--kh-color-border); border-radius: 8px; background: var(--kh-color-surface); color: var(--kh-color-text-secondary); padding: 8px 13px; font-family: inherit; font-size: 12px; cursor: pointer; }
.kh-ragops button:hover { background: var(--kh-color-surface-hover); }
.kh-ragops button:disabled { opacity: .45; cursor: not-allowed; }
.kh-ragops button:focus-visible, .kh-ragops textarea:focus-visible, .kh-ragops summary:focus-visible { outline: 2px solid var(--kh-color-primary); outline-offset: 2px; }
.kh-ragops .kh-primary { background: var(--kh-color-primary); color: var(--kh-color-primary-contrast); border-color: var(--kh-color-primary); width: max-content; }
.kh-ragops .kh-primary:hover { background: var(--kh-color-primary-strong); }
.kh-tag { display: inline-flex; padding: 3px 7px; background: var(--kh-color-primary-soft); color: var(--kh-color-primary); font-size: 10px; border-radius: 5px; font-weight: 500; }
.kh-ragops .kh-results-column { min-height: 460px; align-content: start; background: var(--kh-color-surface-sunken); gap: 14px; position: sticky; top: 0; max-height: calc(100vh - 170px); overflow-y: auto; }
.kh-empty { min-height: 240px; display: flex; justify-content: center; align-items: center; flex-direction: column; gap: 10px; padding: 28px; text-align: center; color: var(--kh-color-text-muted); font-size: 13px; }
.kh-empty-symbol { font-size: 42px; color: var(--kh-color-primary); opacity: .6; }
.kh-run-summary { display: grid; gap: 10px; padding: 12px; border-radius: 8px; border: 1px solid var(--kh-color-border); background: var(--kh-color-surface); }
.kh-stage-list { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: var(--kh-color-text-secondary); }
.kh-stage-list span { border-radius: 5px; padding: 5px 7px; background: var(--kh-color-surface-sunken); }
.kh-stage-list b { margin-left: 4px; font-weight: 500; }
.kh-ragops summary { cursor: pointer; font-size: 12px; color: var(--kh-color-text-secondary); line-height: 1.6; }
.kh-query-plan ol { padding-left: 18px; margin: 10px 0 0; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.kh-evidence { border: 1px solid var(--kh-color-border); border-radius: 9px; background: var(--kh-color-surface); padding: 14px; display: grid; gap: 10px; }
.kh-evidence-head { justify-content: flex-start; gap: 8px; }
.kh-evidence-head strong { font-size: 12px; min-width: 0; overflow-wrap: anywhere; flex: 1; }
.kh-hit-number { padding: 2px 7px; border-radius: 5px; background: var(--kh-color-primary); color: var(--kh-color-primary-contrast); font-size: 11px; }
.kh-score { font-size: 11px; color: var(--kh-color-primary); white-space: nowrap; }
.kh-evidence-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; font-size: 13px; color: var(--kh-color-text-secondary); }
.kh-evidence-metadata, .kh-evidence footer { display: flex; gap: 10px; flex-wrap: wrap; font-size: 10px; color: var(--kh-color-text-tertiary); }
.kh-context .kh-evidence-content { padding-top: 8px; }
.kh-evidence footer { border-top: 1px solid var(--kh-color-divider); padding-top: 9px; }
.kh-ragops .kh-history-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; text-align: left; border: 0; padding: 7px 8px; width: 100%; }
.kh-history-query { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.kh-history-row small { white-space: nowrap; }
.kh-operations { border: 1px solid var(--kh-color-border); border-radius: 10px; background: var(--kh-color-surface); padding: 14px; }
.kh-operations > summary { font-size: 13px; font-weight: 500; }
.kh-operations > summary small { font-weight: 400; margin-left: 10px; }
.kh-operations-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px; }
.kh-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; padding: 10px; border: 1px solid var(--kh-color-border); border-radius: 8px; }
.kh-row div { display: grid; gap: 5px; min-width: 0; font-size: 12px; }
.kh-metrics { display: grid; grid-template-columns: repeat(auto-fit,minmax(100px,1fr)); gap: 8px; }
.kh-metrics div { background: var(--kh-color-surface-sunken); padding: 10px; border-radius: 8px; display: grid; gap: 5px; font-size: 12px; }
.kh-ragops .kh-error { color: var(--kh-color-danger); }
@media (max-width: 850px) { .kh-ragops { padding: 14px; } .kh-workbench, .kh-operations-grid { grid-template-columns: minmax(0, 1fr); } .kh-ragops .kh-results-column { position: static; max-height: none; } }
</style>
