export type ConnectorRiskLevel = 'READ' | 'WRITE' | 'DESTRUCTIVE' | string;
export type ConnectorCapability = 'CHANNEL_INBOUND' | 'CHANNEL_OUTBOUND' | 'ACTION' | 'EVENT';

export interface ConnectorKey { provider: string; connectorId: string }
export interface ConnectorAction {
  id: string; name: string; description?: string; inputSchema: string;
  outputSchema?: string; idempotent?: boolean; timeout?: string;
  riskLevel?: ConnectorRiskLevel; metadata?: Record<string, unknown>;
  capabilities?: ConnectorCapability[];
}
export interface ConnectorDefinition {
  key: ConnectorKey; name: string; description?: string; version?: string;
  source?: string; icon?: string; category?: string; configurationSchema?: string;
  actions: ConnectorAction[]; trustLevel?: string; license?: string;
  capabilities?: ConnectorCapability[];
  metadata?: Record<string, unknown>;
}
export interface ConnectorInstallation {
  id: string; tenantId: string; provider: string; connectorId: string;
  version?: string; source?: string; enabled: boolean; trustLevel?: string;
}
export interface ConnectorConnection {
  id: string; tenantId: string; installationId: string; name: string;
  status: string; credentialsConfigured: boolean;
}
export interface SaveConnectorConnection {
  id?: string; tenantId?: string; installationId: string; name: string;
  credentials?: Record<string, unknown>; config?: Record<string, unknown>;
}
export interface ConnectorError { code: string; message: string; retryable?: boolean }
export interface ConnectorResult {
  success: boolean; data?: unknown; content?: unknown[]; error?: ConnectorError;
  metadata?: Record<string, unknown>;
}
export interface ConnectorConnectionTestResult { success: boolean; code: string; message: string }
export interface ConnectorExecutionRecord {
  id: string; tenantId: string; provider: string; connectorId: string; actionId: string;
  installationId?: string; connectionId?: string; agentId?: string; workflowId?: string;
  runId?: string; nodeId?: string; status: string; durationMs?: number;
  errorCode?: string; errorMessage?: string; createdAt?: string;
}

export interface JsonSchema {
  writeOnly?: boolean;
  type?: string; title?: string; description?: string; format?: string;
  properties?: Record<string, JsonSchema>; required?: string[]; enum?: unknown[];
  default?: unknown; items?: JsonSchema; additionalProperties?: boolean | JsonSchema;
  minimum?: number; maximum?: number;
}

export function parseJsonSchema(value?: string | JsonSchema): JsonSchema {
  if (!value) return { type: 'object', properties: {}, additionalProperties: true };
  if (typeof value !== 'string') return value;
  try { return JSON.parse(value) as JsonSchema; }
  catch { return { type: 'object', properties: {}, additionalProperties: true }; }
}
