import { qs, type HttpCore } from './core';
import type {
  ChannelAccount, ChannelAttachment, ChannelAuditRecord, ChannelConnection, ChannelConnectionEditConfiguration,
  ChannelConversation, ChannelConversationPage, ChannelConversationSummary, ChannelDeadLetterReplayResult,
  ChannelDefinition, ChannelEvent, ChannelEventPage, ChannelIdentity, ChannelRuntimeNodes,
  EmployeeAgentBinding, SaveChannelConnection, TenantAgentBinding,
} from '../channel-hub/types';

const enc = encodeURIComponent;

/** Channel gateway API — long-running message channels, separated from callable business connectors. */
export interface ChannelsNamespace {
  listChannelAudits(params?: { action?: string; resourceType?: string; resourceId?: string; actorId?: string; outcome?: string; limit?: number }): Promise<ChannelAuditRecord[]>;
  listChannelDeadLetters(limit?: number): Promise<ChannelEvent[]>;
  replayChannelDeadLetters(eventIds: string[]): Promise<ChannelDeadLetterReplayResult>;
  listChannels(refresh?: boolean, runtimeNodeId?: string): Promise<ChannelDefinition[]>;
  listChannelRuntimeNodes(): Promise<ChannelRuntimeNodes>;
  /** Deployment-scoped diagnostics. Requires PLATFORM_ADMIN/SYSTEM_ADMIN/DEPLOYMENT_ADMIN by default. */
  listChannelAccounts(provider: string, channelId: string): Promise<ChannelAccount[]>;
  listChannelConnections(tenantId?: string, ownerId?: string): Promise<ChannelConnection[]>;
  getChannelConnectionConfiguration(id: string, tenantId?: string): Promise<ChannelConnectionEditConfiguration>;
  saveChannelConnection(request: SaveChannelConnection): Promise<ChannelConnection>;
  testChannelConnection(id: string, tenantId?: string): Promise<ChannelConnection>;
  deleteChannelConnection(id: string, tenantId?: string): Promise<void>;
  listChannelEvents(params?: { tenantId?: string; connectionId?: string; limit?: number }): Promise<ChannelEvent[]>;
  pageChannelEvents(params?: { connectionId?: string; conversationId?: string; cursor?: string; limit?: number }): Promise<ChannelEventPage>;
  retryChannelEvent(id: string, tenantId?: string): Promise<ChannelEvent>;
  handoffChannelEvent(id: string, note?: string, tenantId?: string, assignmentGroup?: string): Promise<ChannelEvent>;
  replyChannelEvent(id: string, request: { content?: string; messageType?: string; attachments?: ChannelAttachment[]; contentPayload?: Record<string, unknown>; idempotencyKey?: string }, tenantId?: string): Promise<ChannelEvent>;
  listChannelConversations(params?: { status?: string; assigneeId?: string; slaBreached?: boolean; limit?: number }): Promise<ChannelConversation[]>;
  pageChannelConversations(params?: { status?: string; assigneeId?: string; slaBreached?: boolean; provider?: string; channelId?: string; cursor?: string; limit?: number }): Promise<ChannelConversationPage>;
  getChannelConversation(id: string): Promise<ChannelConversation>;
  getChannelConversationSummary(): Promise<ChannelConversationSummary>;
  claimChannelConversation(id: string, expectedVersion: number, assignmentGroup?: string): Promise<ChannelConversation>;
  resumeChannelConversationBot(id: string, expectedVersion: number): Promise<ChannelConversation>;
  closeChannelConversation(id: string, expectedVersion: number): Promise<ChannelConversation>;
  addChannelConversationNote(id: string, content: string): Promise<ChannelEvent>;
  getTenantAgentBinding(tenantId?: string): Promise<TenantAgentBinding | null>;
  saveTenantAgentBinding(tenantId: string | undefined, request: { defaultAgentId: string; defaultAgentVersionId?: string; fallbackAgentId?: string; fallbackAgentVersionId?: string; enabled?: boolean }): Promise<TenantAgentBinding>;
  getEmployeeAgentBinding(employeeId: string, tenantId?: string): Promise<EmployeeAgentBinding | null>;
  saveEmployeeAgentBinding(employeeId: string, agentId: string, tenantId?: string, agentVersionId?: string): Promise<EmployeeAgentBinding>;
  listChannelIdentities(tenantId?: string): Promise<ChannelIdentity[]>;
  saveChannelIdentity(request: Omit<ChannelIdentity, 'id' | 'tenantId'>, tenantId?: string): Promise<ChannelIdentity>;
}

export function createChannelsNamespace(core: HttpCore): ChannelsNamespace {
  return {
    listChannelAudits: (params = {}) => core.request(`/channel-audits${qs(params)}`),
    listChannelDeadLetters: (limit = 100) => core.request(`/channel-dead-letters${qs({ limit })}`),
    replayChannelDeadLetters: (eventIds) => core.request('/channel-dead-letters/replay', {
      method: 'POST', body: JSON.stringify({ eventIds }),
    }),
    listChannels: (refresh = false, runtimeNodeId) => core.request(`/channels${qs({ refresh: refresh ? 'true' : undefined, runtimeNodeId })}`),
    listChannelRuntimeNodes: () => core.request('/channels/runtime-nodes'),
    listChannelAccounts: (provider, channelId) => core.request(`/channels/${enc(provider)}/${enc(channelId)}/accounts`),
    listChannelConnections: (_tenantId, ownerId) => core.request(`/channel-connections${qs({ ownerId })}`),
    getChannelConnectionConfiguration: (id) => core.request(`/channel-connections/${enc(id)}/configuration`),
    saveChannelConnection: (request) => core.request('/channel-connections', { method: 'POST', body: JSON.stringify(request) }),
    testChannelConnection: (id) => core.request(`/channel-connections/${enc(id)}/test`, { method: 'POST' }),
    deleteChannelConnection: (id) => core.request(`/channel-connections/${enc(id)}`, { method: 'DELETE' }),
    listChannelEvents: (params = {}) => {
      const { tenantId: _ignored, ...trustedParams } = params;
      return core.request(`/channel-events${qs(trustedParams)}`);
    },
    pageChannelEvents: (params = {}) => core.request(`/channel-events/page${qs(params)}`),
    retryChannelEvent: (id) => core.request(`/channel-events/${enc(id)}/retry`, { method: 'POST' }),
    handoffChannelEvent: (id, note, _tenantId, assignmentGroup) => core.request(`/channel-events/${enc(id)}/handoff`, { method: 'POST', body: JSON.stringify({ note, assignmentGroup }) }),
    replyChannelEvent: (id, request) => core.request(`/channel-events/${enc(id)}/reply`, { method: 'POST', body: JSON.stringify(request) }),
    listChannelConversations: (params = {}) => core.request(`/channel-conversations${qs({
      ...params, slaBreached: params.slaBreached == null ? undefined : String(params.slaBreached),
    })}`),
    pageChannelConversations: (params = {}) => core.request(`/channel-conversations/page${qs({
      ...params, slaBreached: params.slaBreached == null ? undefined : String(params.slaBreached),
    })}`),
    getChannelConversation: (id) => core.request(`/channel-conversations/${enc(id)}`),
    getChannelConversationSummary: () => core.request('/channel-conversations/summary'),
    claimChannelConversation: (id, expectedVersion, assignmentGroup) => core.request(`/channel-conversations/${enc(id)}/claim`, { method: 'POST', body: JSON.stringify({ expectedVersion, assignmentGroup }) }),
    resumeChannelConversationBot: (id, expectedVersion) => core.request(`/channel-conversations/${enc(id)}/resume-bot`, { method: 'POST', body: JSON.stringify({ expectedVersion }) }),
    closeChannelConversation: (id, expectedVersion) => core.request(`/channel-conversations/${enc(id)}/close`, { method: 'POST', body: JSON.stringify({ expectedVersion }) }),
    addChannelConversationNote: (id, content) => core.request(`/channel-conversations/${enc(id)}/notes`, { method: 'POST', body: JSON.stringify({ content }) }),
    getTenantAgentBinding: () => core.request('/tenant-agent-bindings/current'),
    saveTenantAgentBinding: (_tenantId, request) => core.request('/tenant-agent-bindings/current', { method: 'PUT', body: JSON.stringify(request) }),
    getEmployeeAgentBinding: (employeeId) => core.request(`/employee-agent-bindings/${enc(employeeId)}`),
    saveEmployeeAgentBinding: (employeeId, agentId, _tenantId, agentVersionId) => core.request(`/employee-agent-bindings/${enc(employeeId)}`, { method: 'PUT', body: JSON.stringify({ agentId, agentVersionId, enabled: true }) }),
    listChannelIdentities: () => core.request('/channel-identities'),
    saveChannelIdentity: (request) => core.request('/channel-identities', { method: 'PUT', body: JSON.stringify(request) }),
  };
}
