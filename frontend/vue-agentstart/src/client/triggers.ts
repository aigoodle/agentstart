import type { HttpCore } from './core';

export type TriggerType = 'WEBHOOK' | 'CRON' | 'EVENT' | 'CHANNEL_MESSAGE' | 'MANUAL';

export interface TriggerScheduleConfig {
  scheduleType: 'CRON' | 'ONE' | 'ONCE';
  expression?: string;
  runAt?: string;
  timeZone?: string;
  conversationId?: string;
  data?: Record<string, unknown>;
  /** @deprecated Use data. */
  payload?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface CreateTriggerRequest {
  tenantId?: string;
  name: string;
  type: TriggerType;
  targetType?: string;
  targetId: string;
  config: TriggerScheduleConfig | Record<string, unknown>;
  enabled?: boolean;
}

export interface TriggerWire extends CreateTriggerRequest {
  id: string;
  configJson?: string;
  nextFireAt?: string | null;
  lastFireAt?: string | null;
  fireCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TriggerInvocationWire {
  id: string;
  triggerId: string;
  source: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  conversationId?: string;
  runId?: string;
  payloadJson?: string;
  outputsJson?: string;
  replayOf?: string;
  error?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TriggerPage {
  records: TriggerWire[];
  total: number;
  page: number;
  pageSize: number;
}

export interface TriggerPageOptions {
  page?: number;
  pageSize?: number;
  keyword?: string;
  category?: 'APPLICATION' | 'USER';
}

export interface TriggersNamespace {
  list(): Promise<TriggerWire[]>;
  page(options?: TriggerPageOptions): Promise<TriggerPage>;
  get(id: string): Promise<TriggerWire>;
  create(request: CreateTriggerRequest): Promise<TriggerWire>;
  createFromJson(json: string): Promise<TriggerWire>;
  setEnabled(id: string, enabled: boolean): Promise<void>;
  remove(id: string): Promise<void>;
  fire(id: string, payload?: Record<string, unknown>): Promise<Record<string, unknown>>;
  invocations(id: string): Promise<TriggerInvocationWire[]>;
  replay(invocationId: string): Promise<TriggerInvocationWire>;
}

export function createTriggersNamespace(core: HttpCore): TriggersNamespace {
  return {
    list: () => core.request<TriggerWire[]>('/triggers'),
    page: ({ page = 1, pageSize = 10, keyword, category } = {}) => {
      const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
      if (keyword?.trim()) params.set('keyword', keyword.trim());
      if (category) params.set('category', category);
      return core.request<TriggerPage>(`/triggers/page?${params}`);
    },
    get: (id) => core.request<TriggerWire>(`/triggers/${encodeURIComponent(id)}`),
    create: (request) => core.request<TriggerWire>('/triggers', {
      method: 'POST', body: JSON.stringify(request),
    }),
    createFromJson: (json) => core.request<TriggerWire>('/triggers/from-json', {
      method: 'POST', body: json, headers: { 'Content-Type': 'text/plain' },
    }, { json: false }),
    setEnabled: (id, enabled) => core.request<void>(
      `/triggers/${encodeURIComponent(id)}/enabled?enabled=${enabled}`, { method: 'PUT' },
    ),
    remove: (id) => core.request<void>(`/triggers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    fire: (id, payload = {}) => core.request<Record<string, unknown>>(
      `/triggers/${encodeURIComponent(id)}/fire`, { method: 'POST', body: JSON.stringify(payload) },
    ),
    invocations: (id) => core.request<TriggerInvocationWire[]>(
      `/triggers/${encodeURIComponent(id)}/invocations`,
    ),
    replay: (invocationId) => core.request<TriggerInvocationWire>(
      `/invocations/${encodeURIComponent(invocationId)}/replay`, { method: 'POST' },
    ),
  };
}
