/**
 * Public channel/gateway model types.
 *
 * These declarations mirror the backend `agent-start-channel` runtime
 * (`/channels`, `/channel-*` REST). Callable business connectors keep their
 * own catalog types in connector-hub/types.
 */
export interface ChannelDefinition {
  provider: string; channelId: string; name: string; description?: string; version?: string;
  installed: boolean; enabled: boolean; runtimeStatus: string;
  credentialSchema?: string; configurationSchema?: string;
  uiSchema?: Record<string, unknown>; capabilities?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}
export type ChannelAccountScope = 'PERSONAL' | 'TENANT';
export type ChannelInstancePolicy = 'SINGLE' | 'MULTIPLE';
export interface ChannelIdentityBridgeDefinition {
  enabled: boolean;
  mode?: 'OAUTH' | 'MANUAL' | 'DIRECTORY' | string;
  externalIdentityLabel?: string;
  enterpriseIdentityLabel?: string;
  description?: string;
}
/** Backend-published UI contract, normally authored by a channel module in YAML. */
export interface ChannelAccountModel {
  scope: ChannelAccountScope;
  instancePolicy?: ChannelInstancePolicy;
  ownerRequired?: boolean;
  ownerLabel?: string;
  ownerPlaceholder?: string;
  identityBridge?: ChannelIdentityBridgeDefinition;
}
export interface ChannelAccount {
  channelId: string; accountId: string; name: string; enabled: boolean;
  configured: boolean; running: boolean; connected: boolean;
  lastConnectedAt?: string; lastError?: string; metadata?: Record<string, unknown>;
}
export type ChannelRuntimeNodes = Record<string, string[]>;
export interface ChannelConnection {
  id: string; tenantId: string; ownerType: string; ownerId: string;
  provider: string; channelId: string; name: string; desiredStatus: string;
  runtimeStatus: string; runtimeAccountId: string; runtimeNodeId?: string; agentId?: string; agentVersionId?: string;
  runtimeMetadata?: Record<string, unknown>;
  credentialsConfigured: boolean; lastError?: string; lastTestedAt?: string;
  configVersion: number;
}
export interface ChannelConnectionEditConfiguration {
  credentials: Record<string, unknown>;
  config: Record<string, unknown>;
  configuredSecretFields: string[];
}
export interface SaveChannelConnection {
  id?: string; tenantId?: string; ownerType?: string; ownerId?: string;
  provider: string; channelId: string; name: string;
  credentials?: Record<string, unknown>; config?: Record<string, unknown>;
  enabled?: boolean; agentId?: string; agentVersionId?: string; runtimeNodeId?: string;
}
export interface ChannelEvent {
  id: string; tenantId: string; connectionId?: string; runtimeNodeId?: string; ownerId?: string;
  provider: string; channelId: string; accountId: string; messageId?: string;
  senderId?: string; replyTargetId?: string; conversationId?: string; direction: string; content?: string;
  messageType?: 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'FILE' | 'RICH_TEXT' | string;
  attachments?: ChannelAttachment[]; contentPayload?: Record<string, unknown>;
  replyToEventId?: string;
  handled: boolean; status: string; replyContent?: string; durationMs?: number;
  errorMessage?: string; idempotencyKey?: string; platformMessageId?: string;
  senderType?: 'AGENT' | 'EMPLOYEE' | 'ADMIN' | 'SYSTEM'; senderActorId?: string;
  attempts?: number; nextAttemptAt?: string; sentAt?: string; deliveredAt?: string;
  eventTime?: string; createdAt?: string;
}
export interface ChannelAttachment {
  type: 'IMAGE' | 'AUDIO' | 'VIDEO' | 'FILE' | string;
  url?: string; name?: string; mimeType?: string; size?: number;
  metadata?: Record<string, unknown>;
}
export interface ChannelEventPage { items: ChannelEvent[]; nextCursor?: string; hasMore: boolean }
export interface ChannelConversation {
  id: string; tenantId: string; connectionId: string; ownerId?: string;
  provider: string; channelId: string; accountId: string; conversationId: string;
  agentId?: string; agentVersionId?: string; agentRouteReason?: string; routingPolicyVersion: number;
  status: 'BOT_ACTIVE' | 'WAITING_HUMAN' | 'HUMAN_ACTIVE' | 'CLOSED';
  agentPaused: boolean; assigneeId?: string; assigneeName?: string; assignmentGroup?: string;
  handoffRequestedAt?: string; claimedAt?: string; closedAt?: string; lastMessageAt?: string;
  lastEventId?: string; lastMessagePreview?: string; unreadCount: number; slaDueAt?: string;
  slaBreached: boolean; lockVersion: number;
}
export interface ChannelConversationPage {
  items: ChannelConversation[]; nextCursor?: string; hasMore: boolean;
}
export interface ChannelConversationSummary {
  total: number; waitingHuman: number; humanActive: number; slaBreached: number; unread: number;
}
export interface TenantAgentBinding { id: string; tenantId: string; defaultAgentId: string; defaultAgentVersionId?: string; fallbackAgentId?: string; fallbackAgentVersionId?: string; routingPolicyVersion: number; enabled: boolean }
export interface EmployeeAgentBinding { id: string; tenantId: string; employeeId: string; agentId: string; agentVersionId?: string; routingPolicyVersion: number; enabled: boolean }
export interface ChannelIdentity {
  id: string; tenantId: string; provider: string; channelId: string; accountId: string;
  externalUserId: string; enterpriseUserId: string; verificationStatus: string; enabled: boolean;
}
export interface ChannelAuditRecord {
  id: string; tenantId: string; actorId?: string; actorName?: string; principalType?: string;
  action: string; resourceType: string; resourceId: string; outcome: string;
  details?: Record<string, unknown>; createdAt?: string;
}
export interface ChannelDeadLetterReplayResult { requested: number; eligible: number; requeued: number }

/**
 * 宿主业务系统注入的当前人员信息。组件模块不产生人员数据 —— 由宿主
 * (如 vben 的 useUserStore)在页面上通过 prop 传入。`userId` 必填,
 * 其余字段按宿主提供的粒度按需使用。
 */
export interface RobotUser {
  userId: string;
  username?: string;
  realName?: string;
  avatar?: string;
  [key: string]: unknown;
}

/** Channel connection projected as a user-facing robot card; optional fields are presentation metadata. */
export interface MyRobot extends ChannelConnection {
  icon?: string;
  iconBackground?: string;
  description?: string;
  welcomeMessage?: string;
  agentName?: string;
  createdByName?: string;
  updatedAt?: string;
}
