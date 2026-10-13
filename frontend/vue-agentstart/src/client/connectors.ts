import { qs, type HttpCore } from './core';
import type {
  ConnectorConnection, ConnectorConnectionTestResult, ConnectorDefinition, ConnectorExecutionRecord,
  ConnectorInstallation, ConnectorResult, SaveConnectorConnection,
} from '../connector-hub/types';

const enc = encodeURIComponent;

/** Callable business connector catalog — channel gateways live in {@link ChannelsNamespace}. */
export interface ConnectorsNamespace {
  list(): Promise<ConnectorDefinition[]>;
  get(provider: string, connectorId: string): Promise<ConnectorDefinition>;
  refresh(): Promise<ConnectorDefinition[]>;
  execute(provider: string, connectorId: string, actionId: string,
    args?: Record<string, unknown>, tenantId?: string): Promise<ConnectorResult>;
  listInstallations(tenantId?: string): Promise<ConnectorInstallation[]>;
  synchronize(tenantId?: string): Promise<ConnectorInstallation[]>;
  setInstallationEnabled(id: string, enabled: boolean, tenantId?: string): Promise<ConnectorInstallation>;
  listConnections(tenantId?: string): Promise<ConnectorConnection[]>;
  saveConnection(request: SaveConnectorConnection): Promise<ConnectorConnection>;
  deleteConnection(id: string, tenantId?: string): Promise<void>;
  testConnection(id: string, tenantId?: string): Promise<ConnectorConnectionTestResult>;
  listExecutions(params?: { tenantId?: string; provider?: string; connectorId?: string; status?: string; limit?: number }): Promise<ConnectorExecutionRecord[]>;
}

export function createConnectorsNamespace(core: HttpCore): ConnectorsNamespace {
  return {
    list: () => core.request('/connectors'),
    get: (provider, id) => core.request(`/connectors/${enc(provider)}/${enc(id)}`),
    refresh: () => core.request('/connectors/refresh', { method: 'POST' }),
    execute: (provider, id, action, args = {}) => core.request(
      `/connectors/${enc(provider)}/${enc(id)}/actions/${enc(action)}/execute`,
      { method: 'POST', body: JSON.stringify(args) },
    ),
    listInstallations: () => core.request('/connector-installations'),
    synchronize: () => core.request('/connector-installations/synchronize', { method: 'POST' }),
    setInstallationEnabled: (id, enabled) => core.request(
      `/connector-installations/${enc(id)}/${enabled ? 'enable' : 'disable'}`,
      { method: 'POST' },
    ),
    listConnections: () => core.request('/connector-connections'),
    saveConnection: (request) => core.request('/connector-connections', { method: 'POST', body: JSON.stringify(request) }),
    deleteConnection: (id) => core.request(`/connector-connections/${enc(id)}`, { method: 'DELETE' }),
    testConnection: (id) => core.request(`/connector-connections/${enc(id)}/test`, { method: 'POST' }),
    listExecutions: (params = {}) => {
      const { tenantId: _ignored, ...trustedParams } = params;
      return core.request(`/connector-executions${qs(trustedParams)}`);
    },
  };
}
