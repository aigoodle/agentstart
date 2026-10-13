/**
 * `client.models` — the `/models` endpoints (installed-model registry,
 * defaults, per-model parameters, validation and connectivity tests).
 */
import type {
  GroupedProviderView,
  ModelEntity,
  ModelParameters,
  ModelRegistration,
  ModelTestResult,
  ModelType,
} from '../provider-hub/types';
import { qs, type HttpCore } from './core';

export interface ListModelsOptions {
  tenantId?: string;
  type?: ModelType | string;
}

export interface ModelsNamespace {
  /** GET /models — installed models, optionally filtered by tenant/type. */
  list(opts?: ListModelsOptions): Promise<ModelEntity[]>;
  /** POST /models — register (install) a model. */
  register(req: ModelRegistration): Promise<ModelEntity>;
  /** POST /models/validate — dry-run credential validation, no DB write. */
  validate(req: ModelRegistration): Promise<void>;
  /** PUT /models/{id}/credentials — patch stored credentials. */
  updateCredentials(id: string, patch: Record<string, unknown>): Promise<ModelEntity>;
  /** PUT /models/{id}/default — mark the tenant default. */
  setDefault(id: string): Promise<void>;
  /** PATCH /models/{id}/enabled — flip the enable switch on one row. */
  setEnabled(id: string, enabled: boolean): Promise<ModelEntity>;
  /** DELETE /models/{id}. */
  remove(id: string): Promise<void>;
  /** POST /models/{id}/test — live connectivity test. */
  test(id: string): Promise<ModelTestResult>;
  /** GET /models/{id}/parameters — current values + UI rules. */
  getParameters(id: string): Promise<ModelParameters>;
  /** PUT /models/{id}/parameters — `null` values remove the override. */
  saveParameters(
    id: string,
    parameters: Record<string, boolean | null | number | string>,
  ): Promise<ModelParameters>;
  /** GET /models/defaults — `{ [modelType]: ModelEntity | null }`. */
  defaults(tenantId?: string): Promise<Record<string, ModelEntity | null>>;
  /** GET /models/grouped-by-type — enabled models grouped for opt-group selects. */
  groupedByType(tenantId?: string): Promise<Record<string, GroupedProviderView[]>>;
  /** Convenience: id of the default model for a type, or null. */
  defaultId(type: ModelType | string, tenantId?: string): Promise<string | null>;
}

export function createModelsNamespace(core: HttpCore): ModelsNamespace {
  async function tenantParam(explicit?: string): Promise<string | undefined> {
    return explicit ?? (await core.tenant());
  }

  async function defaultsImpl(
    tenantId?: string,
  ): Promise<Record<string, ModelEntity | null>> {
    const tenant = await tenantParam(tenantId);
    return core.request<Record<string, ModelEntity | null>>(
      `/models/defaults${qs({ tenantId: tenant })}`,
    );
  }

  return {
    async list(opts = {}) {
      const tenantId = await tenantParam(opts.tenantId);
      return core.request<ModelEntity[]>(`/models${qs({ tenantId, type: opts.type })}`);
    },
    register: (req) =>
      core.request<ModelEntity>('/models', {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    validate: (req) =>
      core.request<void>('/models/validate', {
        method: 'POST',
        body: JSON.stringify(req),
      }),
    updateCredentials: (id, patch) =>
      core.request<ModelEntity>(`/models/${encodeURIComponent(id)}/credentials`, {
        method: 'PUT',
        body: JSON.stringify(patch),
      }),
    setDefault: (id) =>
      core.request<void>(`/models/${encodeURIComponent(id)}/default`, {
        method: 'PUT',
      }),
    setEnabled: (id, enabled) =>
      core.request<ModelEntity>(`/models/${encodeURIComponent(id)}/enabled`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled }),
      }),
    remove: (id) =>
      core.request<void>(`/models/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    test: (id) =>
      core.request<ModelTestResult>(`/models/${encodeURIComponent(id)}/test`, {
        method: 'POST',
      }),
    getParameters: (id) =>
      core.request<ModelParameters>(`/models/${encodeURIComponent(id)}/parameters`),
    saveParameters: (id, parameters) =>
      core.request<ModelParameters>(`/models/${encodeURIComponent(id)}/parameters`, {
        method: 'PUT',
        body: JSON.stringify(parameters),
      }),
    defaults: defaultsImpl,
    async groupedByType(tenantId) {
      const tenant = await tenantParam(tenantId);
      return core.request<Record<string, GroupedProviderView[]>>(
        `/models/grouped-by-type${qs({ tenantId: tenant })}`,
      );
    },
    async defaultId(type, tenantId) {
      const defaults = await defaultsImpl(tenantId);
      return defaults?.[String(type)]?.id ?? null;
    },
  };
}
