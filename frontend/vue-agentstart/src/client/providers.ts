/**
 * `client.providers` — model-provider gallery, credentials, remote-model
 * discovery and the DB-driven catalog/definition endpoints
 * (`/model-providers`, `/model-provider-definitions`, `/model-provider-impls`).
 */
import type {
  CatalogRow,
  ModelType,
  ProviderCredentialInfo,
  ProviderDefinition,
  ProviderView,
  RemoteModel,
} from '../provider-hub/types';
import { qs, type HttpCore } from './core';

/** Payload accepted by {@link ProvidersNamespace.addPredefinedModel}. */
export interface PredefinedModelPayload {
  model: string;
  label?: string;
  modelType: ModelType;
  contextLength?: number;
  dimensions?: number;
  features?: string[];
  parameterRules?: Array<Record<string, unknown>>;
  sortOrder?: number;
}

export interface ProvidersNamespace {
  /** GET /model-providers — gallery rows with installedModelCount etc. */
  list(tenantId?: string): Promise<ProviderView[]>;
  /** GET /model-providers/{name}. */
  get(name: string, tenantId?: string): Promise<ProviderView>;

  /** GET /model-providers/{name}/credential. */
  getCredential(name: string, tenantId?: string): Promise<ProviderCredentialInfo>;
  /** PUT /model-providers/{name}/credential — save-and-fetch flow. */
  saveCredential(
    name: string,
    credentials: Record<string, unknown>,
    tenantId?: string,
  ): Promise<ProviderCredentialInfo>;
  /** DELETE /model-providers/{name}/credential. */
  deleteCredential(name: string, tenantId?: string): Promise<void>;

  /** GET /model-providers/{name}/remote-models — non-destructive vendor peek. */
  listRemoteModels(name: string, tenantId?: string): Promise<RemoteModel[]>;
  /** POST /model-providers/{name}/refresh-catalog — re-hit vendor, no DB write. */
  refreshCatalog(name: string, tenantId?: string): Promise<RemoteModel[]>;

  /** GET /model-providers/{name}/catalog — predefined + custom rows w/ state. */
  getCatalog(name: string, tenantId?: string): Promise<CatalogRow[]>;

  /** PUT …/models/{model}/enabled — per-predefined-model enable flip. */
  setModelEnabledByName(
    providerName: string,
    modelName: string,
    modelType: ModelType,
    enabled: boolean,
    tenantId?: string,
  ): Promise<boolean>;
  /** PUT …/models/{model}/default — tenant default per model type. */
  setModelDefaultByName(
    providerName: string,
    modelName: string,
    modelType: ModelType,
    tenantId?: string,
  ): Promise<void>;

  /** GET /model-provider-impls — Java implementation bean keys. */
  listImplementationKeys(): Promise<string[]>;
  /** POST /model-provider-definitions. */
  createDefinition(payload: ProviderDefinition): Promise<ProviderDefinition>;
  /** PUT /model-provider-definitions/{id}. */
  updateDefinition(id: string, patch: Partial<ProviderDefinition>): Promise<void>;
  /** DELETE /model-provider-definitions/{id}. */
  deleteDefinition(id: string): Promise<void>;

  /** POST /model-providers/{name}/predefined-models — idempotent upsert. */
  addPredefinedModel(
    providerName: string,
    payload: PredefinedModelPayload,
  ): Promise<Record<string, unknown>>;
  /** DELETE /model-providers/{name}/predefined-models/{id}. */
  deletePredefinedModel(providerName: string, id: string): Promise<void>;
}

export function createProvidersNamespace(core: HttpCore): ProvidersNamespace {
  async function tenantParam(explicit?: string): Promise<string | undefined> {
    return explicit ?? (await core.tenant());
  }

  function providerPath(name: string): string {
    return `/model-providers/${encodeURIComponent(name)}`;
  }

  return {
    async list(tenantId) {
      return core.request<ProviderView[]>(
        `/model-providers${qs({ tenantId: await tenantParam(tenantId) })}`,
      );
    },
    async get(name, tenantId) {
      return core.request<ProviderView>(
        `${providerPath(name)}${qs({ tenantId: await tenantParam(tenantId) })}`,
      );
    },

    async getCredential(name, tenantId) {
      return core.request<ProviderCredentialInfo>(
        `${providerPath(name)}/credential${qs({ tenantId: await tenantParam(tenantId) })}`,
      );
    },
    async saveCredential(name, credentials, tenantId) {
      return core.request<ProviderCredentialInfo>(`${providerPath(name)}/credential`, {
        method: 'PUT',
        body: JSON.stringify({ tenantId: await tenantParam(tenantId), credentials }),
      });
    },
    async deleteCredential(name, tenantId) {
      return core.request<void>(
        `${providerPath(name)}/credential${qs({ tenantId: await tenantParam(tenantId) })}`,
        { method: 'DELETE' },
      );
    },

    async listRemoteModels(name, tenantId) {
      return core.request<RemoteModel[]>(
        `${providerPath(name)}/remote-models${qs({ tenantId: await tenantParam(tenantId) })}`,
      );
    },
    async refreshCatalog(name, tenantId) {
      return core.request<RemoteModel[]>(
        `${providerPath(name)}/refresh-catalog${qs({ tenantId: await tenantParam(tenantId) })}`,
        { method: 'POST' },
      );
    },

    async getCatalog(name, tenantId) {
      return core.request<CatalogRow[]>(
        `${providerPath(name)}/catalog${qs({ tenantId: await tenantParam(tenantId) })}`,
      );
    },

    async setModelEnabledByName(providerName, modelName, modelType, enabled, tenantId) {
      return core.request<boolean>(
        `${providerPath(providerName)}/models/${encodeURIComponent(modelName)}/enabled` +
          qs({ modelType, tenantId: await tenantParam(tenantId) }),
        { method: 'PUT', body: JSON.stringify({ enabled }) },
      );
    },
    async setModelDefaultByName(providerName, modelName, modelType, tenantId) {
      return core.request<void>(
        `${providerPath(providerName)}/models/${encodeURIComponent(modelName)}/default` +
          qs({ modelType, tenantId: await tenantParam(tenantId) }),
        { method: 'PUT' },
      );
    },

    listImplementationKeys: () => core.request<string[]>('/model-provider-impls'),
    createDefinition: (payload) =>
      core.request<ProviderDefinition>('/model-provider-definitions', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    updateDefinition: (id, patch) =>
      core.request<void>(`/model-provider-definitions/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(patch),
      }),
    deleteDefinition: (id) =>
      core.request<void>(`/model-provider-definitions/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      }),

    addPredefinedModel: (providerName, payload) =>
      core.request<Record<string, unknown>>(
        `${providerPath(providerName)}/predefined-models`,
        { method: 'POST', body: JSON.stringify(payload) },
      ),
    deletePredefinedModel: (providerName, id) =>
      core.request<void>(
        `${providerPath(providerName)}/predefined-models/${encodeURIComponent(id)}`,
        { method: 'DELETE' },
      ),
  };
}
