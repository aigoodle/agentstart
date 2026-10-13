import { inject, type App, type InjectionKey } from 'vue';

/**
 * BackendAdapter — 由宿主应用注入的后端 API 抽象。
 *
 * 组件内部不直接调用任何 HTTP 客户端，而是通过 useBackend() 读取当前实例，
 * 从而实现前后端解耦。宿主项目只要实现下列方法并 provide 即可复用整套 UI。
 */
export interface BackendAdapter {
  /** 加载租户当前默认模型（modelType: LLM / EMBEDDING / ...） */
  getCurrentModel: (modelType: string) => Promise<{ data: any }>;

  /** 分页/条件查询工作流列表 */
  getWorkflow: (query: Record<string, any>) => Promise<{ data: any[] }>;

  /** 发布工作流 */
  publishWorkflow: (payload: Record<string, any>) => Promise<{ data: any }>;

  /** 可选：保存工作流草稿（未 provide 时按钮走 no-op） */
  saveWorkflow?: (payload: Record<string, any>) => Promise<{ data: any }>;

  /** 可选：试运行工作流（未 provide 时按钮走 no-op） */
  runWorkflow?: (payload: Record<string, any>) => Promise<{ data: any }>;

  /**
   * 可选：SSE 流式试运行（`POST /workflows/run-graph/stream`）。返回原始
   * `Response`，由调用方（调试面板）用 `readSseEvents` 消费；`opts.signal`
   * 用于中途取消。未 provide 时调试面板回退到 `runWorkflow` 一次性执行。
   */
  runWorkflowStream?: (
    payload: Record<string, any>,
    opts?: { signal?: AbortSignal },
  ) => Promise<Response | null>;

  cancelWorkflowRun?: (runId: string, reason?: string) => Promise<{ data: any }>;
  pauseWorkflowRun?: (runId: string, reason?: string) => Promise<{ data: any }>;
  resumeWorkflowRun?: (runId: string) => Promise<{ data: any }>;
  signalWorkflowRun?: (runId: string, request: Record<string, any>) => Promise<{ data: any }>;

  /** 可选：模型下拉数据源（Pinia store 风格，返回带 fetch 方法的对象） */
  useModelState?: () => any;

  /** 可选：额外扩展点，任意自定义 API */
  [key: string]: any;
}

export const BackendAdapterKey: InjectionKey<BackendAdapter> = Symbol(
  'agent-flow.BackendAdapter',
);

/** 一个内置的 no-op 实现，未 provide 时用它兜底避免直接抛错。 */
export const defaultBackend: BackendAdapter = {
  getCurrentModel: async () => ({ data: {} }),
  getWorkflow: async () => ({ data: [] }),
  publishWorkflow: async () => ({ data: {} }),
  useModelState: () => ({
    modelList: [],
    /** 兼容 fetchModelList / getModelList 两种命名，宿主 provide 时也会保留同名 */
    fetchModelList: async () => [],
    getModelList: async () => [],
  }),
};

let installed: BackendAdapter | null = null;

/** 在 Vue 应用启动时注入实现，见 index.ts createAgentFlow() */
export function provideBackend(app: App, adapter: BackendAdapter) {
  installed = adapter;
  app.provide(BackendAdapterKey, adapter);
}

/**
 * 组件内部使用；优先 inject，回退全局实例，最后回退到 no-op。
 *
 * 注意：Vue 的 `inject()` 在 setup / 生命周期钩子之外 (例如点击回调、
 * setTimeout 里的箭头函数) 会**忽略默认值直接返回 undefined**。所以先看
 * 全局 `installed`（`provideBackend()` 会写这个模块级变量），命中就直接
 * 用；这样即使在点击处理器里调用 saveWorkflow / publishWorkflow 也不会因
 * 拿到 undefined 而崩。
 */
function useBackend(): BackendAdapter {
  if (installed) return installed;
  return inject(BackendAdapterKey, defaultBackend) ?? defaultBackend;
}

/**
 * 兼容层：workflow 内部代码历史上直接 `import { getCurrentModel } from '#/api'`。
 * 我们把同名的函数变成"读取当前 adapter 后转发"的薄壳，改动最小化。
 */
export const getCurrentModel: BackendAdapter['getCurrentModel'] = (t) =>
  useBackend().getCurrentModel(t);

export const getWorkflow: BackendAdapter['getWorkflow'] = (q) =>
  useBackend().getWorkflow(q);

export const publishWorkflow: BackendAdapter['publishWorkflow'] = (p) =>
  useBackend().publishWorkflow(p);

export const saveWorkflow: BackendAdapter['saveWorkflow'] = async (p) => {
  const b = useBackend();
  if (!b.saveWorkflow) return { data: {} };
  return b.saveWorkflow(p);
};

export const runWorkflow: BackendAdapter['runWorkflow'] = async (p) => {
  const b = useBackend();
  if (!b.runWorkflow) return { data: {} };
  return b.runWorkflow(p);
};

export async function cancelWorkflowRun(runId: string, reason?: string) {
  const fn = useBackend().cancelWorkflowRun;
  if (!fn) throw new Error('宿主未配置 cancelWorkflowRun');
  return fn(runId, reason);
}

export async function pauseWorkflowRun(runId: string, reason?: string) {
  const fn = useBackend().pauseWorkflowRun;
  if (!fn) throw new Error('宿主未配置 pauseWorkflowRun');
  return fn(runId, reason);
}

export async function resumeWorkflowRun(runId: string) {
  const fn = useBackend().resumeWorkflowRun;
  if (!fn) throw new Error('宿主未配置 resumeWorkflowRun');
  return fn(runId);
}

export async function signalWorkflowRun(runId: string, request: Record<string, any>) {
  const fn = useBackend().signalWorkflowRun;
  if (!fn) throw new Error('宿主未配置 signalWorkflowRun');
  return fn(runId, request);
}

/** 流式试运行薄壳：宿主未实现时返回 `null`，调用方据此回退一次性执行。 */
export const runWorkflowStream: Required<
  Pick<BackendAdapter, 'runWorkflowStream'>
>['runWorkflowStream'] = async (p, opts) => {
  const b = useBackend();
  if (!b.runWorkflowStream) return null;
  return b.runWorkflowStream(p, opts);
};

export const useModelState = () => {
  const b = useBackend();
  return b.useModelState ? b.useModelState() : defaultBackend.useModelState!();
};
