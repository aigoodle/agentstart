/**
 * useAgentStudio — legacy agent CRUD composable kept for back-compat.
 * Now a thin wrapper over the unified {@link createAgentStartClient};
 * `setAgentStudioApiBase` / `setAgentStudioHeaders` keep working and
 * recreate the shared client on change. New code should prefer
 * `createAgentStartClient().agents`.
 */
import { createAgentStartClient, type AgentStartClient } from '../../client';
import type { AgentEntity, AppTypeDescriptor, CreateAgentRequest } from '../types';

/**
 * 追加到每个请求的 header。传函数会在每次请求前重新求值，方便宿主接入
 * pinia store 里的 access-token —— 登录/退出时不用重挂 composable。
 */
export type HeadersLike =
  | Record<string, string>
  | (() => Promise<Record<string, string>> | Record<string, string>);

let apiBase = '/api';
let headersProvider: () => Promise<Record<string, string>> | Record<string, string> = () => ({});
let shared: AgentStartClient | null = null;

function client(): AgentStartClient {
  if (!shared) {
    shared = createAgentStartClient({
      baseUrl: apiBase,
      headers: () => headersProvider(),
    });
  }
  return shared;
}

export function setAgentStudioApiBase(base: string) {
  apiBase = base.replace(/\/+$/, '');
  shared = null;
}

/**
 * 设置每次请求都会拼上的 header（如 Authorization）。示例：
 *   setAgentStudioHeaders(() => ({
 *     Authorization: `Bearer ${useAccessStore().accessToken}`,
 *   }));
 * 传静态对象也可以 —— 但推荐用函数，避免 token 轮换后拿旧值。
 */
export function setAgentStudioHeaders(headers: HeadersLike) {
  headersProvider = typeof headers === 'function' ? headers : () => headers;
  shared = null;
}

export function useAgentStudio() {
  return {
    listAgents: () => client().agents.list() as unknown as Promise<AgentEntity[]>,
    getAgent: (id: string) =>
      client().agents.get(id) as unknown as Promise<AgentEntity>,
    createAgent: (req: CreateAgentRequest) =>
      client().agents.create(req as never) as unknown as Promise<AgentEntity>,
    updateAgent: (id: string, req: CreateAgentRequest) =>
      client().agents.update(id, req as never) as unknown as Promise<AgentEntity>,
    deleteAgent: (id: string) => client().agents.remove(id),
  };
}

// ---------------------------------------------------------------------------
// Dify-style app-type descriptors.
//
// LEFT PANE (the tiles): small icon + title + short description — 84px tall.
// RIGHT PANE (the preview): a mocked, screenshot-style illustration of what
//   the app looks like once created. We render these as inline SVGs (no image
//   assets so the package stays dependency-free) — they're detailed enough to
//   convey the visual character of each app type.
// ---------------------------------------------------------------------------

// Chatbot — a friendly two-bubble chat mock.
const CHATBOT_SVG = `
<svg viewBox="0 0 480 300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="chatBg" x1="0" x2="0" y1="0" y2="1">
      <stop offset="0" stop-color="#EEF4FF"/>
      <stop offset="1" stop-color="#DBEAFE"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="480" height="300" fill="url(#chatBg)"/>
  <rect x="30" y="24" width="420" height="42" rx="10" fill="#fff" stroke="#e2e8f0"/>
  <circle cx="52" cy="45" r="12" fill="#6366f1"/>
  <text x="70" y="49" font-size="13" font-family="ui-sans-serif" fill="#111">对话应用</text>
  <text x="70" y="62" font-size="10" font-family="ui-sans-serif" fill="#94a3b8">在线</text>
  <!-- Bot bubble -->
  <rect x="30" y="86" width="220" height="52" rx="14" fill="#fff" stroke="#e0e7ff"/>
  <text x="46" y="108" font-size="12" fill="#334155">👋 你好，我可以帮你什么？</text>
  <text x="46" y="126" font-size="10" fill="#94a3b8">刚刚</text>
  <!-- User bubble -->
  <rect x="230" y="152" width="220" height="52" rx="14" fill="#4f46e5"/>
  <text x="246" y="174" font-size="12" fill="#eef2ff">帮我总结这个文档</text>
  <text x="246" y="192" font-size="10" fill="#c7d2fe">刚刚 · 已读</text>
  <!-- Bot bubble 2 -->
  <rect x="30" y="220" width="280" height="52" rx="14" fill="#fff" stroke="#e0e7ff"/>
  <text x="46" y="242" font-size="12" fill="#334155">好的，我先读一下...</text>
  <circle cx="298" cy="246" r="3" fill="#94a3b8">
    <animate attributeName="opacity" values="0.3;1;0.3" dur="1.2s" repeatCount="indefinite"/>
  </circle>
</svg>`;

// Agent — a "brain + tool orbits" mock.
const AGENT_SVG = `
<svg viewBox="0 0 480 300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="agentBg" x1="0" x2="0" y1="0" y2="1">
      <stop offset="0" stop-color="#F0FDF4"/>
      <stop offset="1" stop-color="#DCFCE7"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="480" height="300" fill="url(#agentBg)"/>
  <!-- center agent core -->
  <circle cx="240" cy="150" r="52" fill="#fff" stroke="#86efac" stroke-width="2"/>
  <circle cx="240" cy="150" r="42" fill="#dcfce7"/>
  <text x="222" y="164" font-size="32">🤖</text>
  <text x="215" y="220" font-size="11" fill="#065f46" font-weight="600">Agent 核心</text>
  <!-- tool nodes with dashed lines -->
  <g stroke="#059669" stroke-width="1.5" stroke-dasharray="4 3" fill="none">
    <path d="M 200 120 Q 130 90 80 70"/>
    <path d="M 200 180 Q 130 200 80 230"/>
    <path d="M 280 120 Q 360 90 400 70"/>
    <path d="M 280 180 Q 360 210 400 230"/>
  </g>
  <g font-family="ui-sans-serif">
    <rect x="30" y="52" width="100" height="36" rx="8" fill="#fff" stroke="#86efac"/>
    <text x="46" y="76" font-size="12" fill="#065f46">🔍 web_search</text>
    <rect x="30" y="212" width="100" height="36" rx="8" fill="#fff" stroke="#86efac"/>
    <text x="46" y="236" font-size="12" fill="#065f46">🧮 calculator</text>
    <rect x="350" y="52" width="100" height="36" rx="8" fill="#fff" stroke="#86efac"/>
    <text x="368" y="76" font-size="12" fill="#065f46">📚 knowledge</text>
    <rect x="350" y="212" width="100" height="36" rx="8" fill="#fff" stroke="#86efac"/>
    <text x="368" y="236" font-size="12" fill="#065f46">🌐 http_request</text>
  </g>
</svg>`;

// Workflow — a small DAG.
const WORKFLOW_SVG = `
<svg viewBox="0 0 480 300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="wfBg" x1="0" x2="0" y1="0" y2="1">
      <stop offset="0" stop-color="#FEF6EE"/>
      <stop offset="1" stop-color="#FED7AA"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="480" height="300" fill="url(#wfBg)"/>
  <!-- nodes -->
  <g font-family="ui-sans-serif" font-size="11">
    <rect x="30" y="130" width="76" height="40" rx="8" fill="#fff" stroke="#f97316"/>
    <text x="52" y="155" fill="#7c2d12">▶ 开始</text>
    <rect x="164" y="60" width="96" height="40" rx="8" fill="#fff" stroke="#f97316"/>
    <text x="184" y="85" fill="#7c2d12">🧠 LLM 节点</text>
    <rect x="164" y="200" width="96" height="40" rx="8" fill="#fff" stroke="#f97316"/>
    <text x="180" y="225" fill="#7c2d12">📚 知识检索</text>
    <rect x="318" y="130" width="96" height="40" rx="8" fill="#fff" stroke="#f97316"/>
    <text x="336" y="155" fill="#7c2d12">🔀 IF / ELSE</text>
    <rect x="452" y="115" width="0" height="0"/>
  </g>
  <!-- 结束 -->
  <rect x="405" y="45" width="60" height="30" rx="6" fill="#fff" stroke="#f97316" stroke-dasharray="3 2"/>
  <text x="418" y="65" font-size="10" fill="#7c2d12">✅ 完成</text>
  <rect x="405" y="225" width="60" height="30" rx="6" fill="#fff" stroke="#f97316" stroke-dasharray="3 2"/>
  <text x="418" y="245" font-size="10" fill="#7c2d12">❌ 失败</text>
  <!-- edges -->
  <g stroke="#c2410c" stroke-width="1.5" fill="none">
    <path d="M 106 150 L 130 150 L 130 80 L 164 80"/>
    <path d="M 106 150 L 130 150 L 130 220 L 164 220"/>
    <path d="M 260 80 L 285 80 L 285 145 L 318 145"/>
    <path d="M 260 220 L 285 220 L 285 155 L 318 155"/>
    <path d="M 414 130 L 425 90 L 425 75"/>
    <path d="M 414 170 L 425 210 L 425 225"/>
  </g>
</svg>`;

// Chatflow — a compact DAG (START → LLM → 直接回复) next to a chat preview
// panel. Mirrors Dify's chatflow tile: same visual canvas as workflow, but the
// terminal node is a "直接回复 (ANSWER)" instead of a generic 结束, and there
// is a chat bubble panel on the right showing the streamed answer.
const CHATFLOW_SVG = `
<svg viewBox="0 0 480 300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="cfBg" x1="0" x2="0" y1="0" y2="1">
      <stop offset="0" stop-color="#EEF4FF"/>
      <stop offset="1" stop-color="#E0E7FF"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="480" height="300" fill="url(#cfBg)"/>
  <!-- LEFT: mini DAG -->
  <g font-family="ui-sans-serif" font-size="10">
    <rect x="20" y="60" width="72" height="34" rx="8" fill="#fff" stroke="#6366f1"/>
    <text x="34" y="82" fill="#3730a3">▶ 开始</text>
    <rect x="110" y="60" width="72" height="34" rx="8" fill="#fff" stroke="#6366f1"/>
    <text x="124" y="82" fill="#3730a3">🧠 LLM</text>
    <rect x="200" y="60" width="88" height="34" rx="8" fill="#fff" stroke="#4f46e5" stroke-width="1.5"/>
    <text x="212" y="82" fill="#3730a3">💬 直接回复</text>
    <g stroke="#818cf8" stroke-width="1.5" fill="none">
      <path d="M 92 77 L 110 77"/>
      <path d="M 182 77 L 200 77"/>
    </g>
    <!-- 记忆挂饰 -->
    <rect x="20" y="120" width="120" height="26" rx="6" fill="#fff" stroke="#c7d2fe" stroke-dasharray="3 2"/>
    <text x="32" y="138" fill="#4338ca">🧠 对话记忆 · 20 轮</text>
    <rect x="150" y="120" width="138" height="26" rx="6" fill="#fff" stroke="#c7d2fe" stroke-dasharray="3 2"/>
    <text x="162" y="138" fill="#4338ca">🌊 SSE 流式输出</text>
  </g>
  <!-- Divider -->
  <line x1="300" y1="20" x2="300" y2="280" stroke="#c7d2fe" stroke-dasharray="4 4"/>
  <!-- RIGHT: chat preview panel -->
  <g font-family="ui-sans-serif">
    <rect x="316" y="24" width="140" height="18" rx="4" fill="#4f46e5"/>
    <text x="326" y="37" font-size="10" fill="#eef2ff">Chatflow 预览</text>
    <!-- user bubble -->
    <rect x="336" y="56" width="120" height="34" rx="10" fill="#4f46e5"/>
    <text x="346" y="76" font-size="10" fill="#eef2ff">波士顿今天天气？</text>
    <!-- assistant bubble -->
    <rect x="316" y="102" width="140" height="60" rx="10" fill="#fff" stroke="#c7d2fe"/>
    <text x="326" y="120" font-size="10" fill="#334155">今日多云，最高 22°C</text>
    <text x="326" y="134" font-size="10" fill="#334155">最低 15°C。傍晚可能</text>
    <text x="326" y="148" font-size="10" fill="#334155">有阵雨，注意携带雨具。</text>
    <!-- streaming caret -->
    <rect x="326" y="170" width="4" height="10" fill="#4f46e5">
      <animate attributeName="opacity" values="0.2;1;0.2" dur="1s" repeatCount="indefinite"/>
    </rect>
    <text x="336" y="180" font-size="9" fill="#94a3b8">流式输出中…</text>
    <!-- input row -->
    <rect x="316" y="240" width="112" height="26" rx="13" fill="#fff" stroke="#c7d2fe"/>
    <text x="326" y="257" font-size="10" fill="#94a3b8">和 Bot 聊天…</text>
    <circle cx="446" cy="253" r="10" fill="#4f46e5"/>
    <text x="442" y="257" font-size="10" fill="#fff">➤</text>
  </g>
</svg>`;

// Text generator — form input + generated block.
const TEXT_GEN_SVG = `
<svg viewBox="0 0 480 300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="tgBg" x1="0" x2="0" y1="0" y2="1">
      <stop offset="0" stop-color="#FEF2F2"/>
      <stop offset="1" stop-color="#FEE2E2"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="480" height="300" fill="url(#tgBg)"/>
  <!-- Input form -->
  <rect x="30" y="26" width="420" height="20" rx="4" fill="#fff" stroke="#fca5a5"/>
  <text x="40" y="41" font-size="11" fill="#94a3b8">主题：</text>
  <text x="85" y="41" font-size="11" fill="#1e293b">Q3 产品复盘要点</text>
  <rect x="30" y="54" width="200" height="20" rx="4" fill="#fff" stroke="#fca5a5"/>
  <text x="40" y="69" font-size="11" fill="#94a3b8">语调：</text>
  <text x="85" y="69" font-size="11" fill="#1e293b">正式</text>
  <rect x="250" y="54" width="200" height="20" rx="4" fill="#fff" stroke="#fca5a5"/>
  <text x="260" y="69" font-size="11" fill="#94a3b8">长度：</text>
  <text x="305" y="69" font-size="11" fill="#1e293b">300 字</text>
  <rect x="380" y="88" width="70" height="24" rx="4" fill="#dc2626"/>
  <text x="398" y="105" font-size="12" fill="#fff">▶ 生成</text>
  <!-- Divider line -->
  <line x1="30" y1="128" x2="450" y2="128" stroke="#fca5a5" stroke-dasharray="3 3"/>
  <text x="30" y="145" font-size="11" fill="#94a3b8">生成结果</text>
  <!-- Output block -->
  <rect x="30" y="152" width="420" height="130" rx="8" fill="#fff" stroke="#fca5a5"/>
  <text x="45" y="174" font-size="12" fill="#1e293b" font-weight="600">Q3 产品复盘：三大关键洞察</text>
  <text x="45" y="196" font-size="11" fill="#475569">1. 新用户留存率环比提升 12%，与 v2.1</text>
  <text x="45" y="212" font-size="11" fill="#475569">   的 onboarding 改动强相关；</text>
  <text x="45" y="230" font-size="11" fill="#475569">2. 高付费用户 ARPU 略微下降，需重点</text>
  <text x="45" y="246" font-size="11" fill="#475569">   排查 Enterprise 计划 SLA 问题；</text>
  <text x="45" y="264" font-size="11" fill="#94a3b8">▍ 正在生成...</text>
</svg>`;

export const APP_TYPES: AppTypeDescriptor[] = [
  {
    id: 'chatbot',
    title: '对话应用',
    description: '轻量级 Chat：模型 + 记忆 + 系统 Prompt。',
    hint: 'REACT 策略 · 保留 20 轮对话记忆',
    icon: '💬',
    iconBg: '#EEF4FF',
    previewSvg: CHATBOT_SVG,
  },
  {
    id: 'agent',
    title: 'Agent 应用',
    description: '让模型调用工具的推理循环，可自主搜索、计算、执行。',
    hint: 'FUNCTION_CALLING · 可挂多工具 · 最多 6 轮',
    icon: '🤖',
    iconBg: '#EFFDF4',
    previewSvg: AGENT_SVG,
  },
  {
    id: 'workflow',
    title: '工作流',
    description: '面向单任务的可视化编排工作流。',
    hint: '拖拽节点编排 · 一次性执行 · 无对话记忆',
    icon: '🧬',
    iconBg: '#FEF6EE',
    previewSvg: WORKFLOW_SVG,
  },
  {
    id: 'chatflow',
    title: 'Chatflow',
    description: '支持记忆的复杂多步骤对话工作流。',
    hint: '拖拽节点编排 · 保留对话记忆 · 支持流式输出',
    icon: '💠',
    iconBg: '#EEF4FF',
    // 与 workflow 共用画布，但收尾是"直接回复 (ANSWER)" 节点，并附带对话记忆
    // 与流式输出 —— 缩略图突出这套差异化，避免与 workflow 混为一谈。
    previewSvg: CHATFLOW_SVG,
  },
  {
    id: 'text-generator',
    title: '文本生成应用',
    description: '固定 Prompt + 参数化变量，一次调用生成长文本。',
    hint: 'PLAN_EXECUTE · 单次输出 · 支持变量',
    icon: '📝',
    iconBg: '#FEF2F2',
    previewSvg: TEXT_GEN_SVG,
  },
];
