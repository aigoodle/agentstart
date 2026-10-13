export const connectorTriggerOutputs = [
  { name: 'type', type: 'string', label: '触发器类型' },
  { name: 'triggerId', type: 'string', label: '触发器 ID' },
  { name: 'workflowId', type: 'string', label: '工作流 ID' },
  { name: 'provider', type: 'string', label: '渠道提供方' },
  { name: 'channelId', type: 'string', label: '渠道 ID' },
  { name: 'channelName', type: 'string', label: '渠道名称' },
  { name: 'connectionId', type: 'string', label: '连接实例 ID' },
  { name: 'connectionName', type: 'string', label: '连接实例名称' },
  { name: 'instanceName', type: 'string', label: '机器人 / 账号实例名称' },
  { name: 'accountId', type: 'string', label: '渠道账号 ID' },
  { name: 'runtimeAccountId', type: 'string', label: '运行时账号标识' },
  { name: 'runtimeNodeId', type: 'string', label: '运行节点 ID' },
  { name: 'agentId', type: 'string', label: '绑定 Agent ID' },
  { name: 'agentVersionId', type: 'string', label: '绑定 Agent 版本 ID' },
];

export const connectorMessageOutputs = [
  { name: 'id', type: 'string', label: '消息 ID' },
  { name: 'type', type: 'string', label: '消息类型' },
  { name: 'content', type: 'string', label: '消息内容' },
  { name: 'attachments', type: 'array', label: '附件' },
  { name: 'payload', type: 'object', label: '结构化消息载荷' },
  { name: 'senderId', type: 'string', label: '外部发送者 ID' },
  { name: 'replyTargetId', type: 'string', label: '渠道回复目标 ID' },
  { name: 'tenantId', type: 'string', label: '平台租户 ID' },
  { name: 'userId', type: 'string', label: '连接器账号所属用户 ID' },
  { name: 'ownerId', type: 'string', label: '连接器账号所有者 ID' },
  { name: 'ownerType', type: 'string', label: '连接器账号所有者类型' },
  { name: 'conversationId', type: 'string', label: '外部会话 ID' },
  { name: 'group', type: 'boolean', label: '是否群消息' },
  { name: 'timestamp', type: 'string', label: '消息时间' },
  { name: 'metadata', type: 'object', label: '消息元数据' },
];

function outputFieldsEqual(current: any, expected: any) {
  if (!current || typeof current !== 'object') return false;
  return Object.entries(expected).every(([key, value]) => current[key] === value);
}

function outputListsEqual(current: any, expected: any[]) {
  return Array.isArray(current)
    && current.length === expected.length
    && current.every((item, index) => outputFieldsEqual(item, expected[index]));
}

export function synchronizeStartTriggerOutputs(data: any): any[] {
  data.structOutput ??= { schema: {}, data: [] };
  if (!Array.isArray(data.structOutput.data)) data.structOutput.data = [];

  let triggers = data.structOutput.data.find((item: any) => item?.name === 'triggers');
  if (!triggers) {
    triggers = { id: 'trigger-output', type: 'object', name: 'triggers', description: '触发器账号与渠道信息', hidden: true, children: [] };
    data.structOutput.data.push(triggers);
  }
  const connector = Boolean(data.triggersEnabled && data.triggers?.type === 'connector');
  const triggerFields = {
    type: 'object',
    label: '触发器',
    description: '触发器账号与渠道信息',
  };
  for (const [key, value] of Object.entries(triggerFields)) {
    if (triggers[key] !== value) triggers[key] = value;
  }
  const expectedTriggerChildren = connector
    ? connectorTriggerOutputs
    : connectorTriggerOutputs.slice(0, 1);
  // This helper is also called while variable pickers render. Replacing the
  // reactive children array on every call dirties the same render effect and
  // causes Vue's "Maximum recursive updates" error.
  if (!outputListsEqual(triggers.children, expectedTriggerChildren)) {
    triggers.children = expectedTriggerChildren.map((item) => ({ ...item }));
  }

  const messageIndex = data.structOutput.data.findIndex((item: any) => item?.name === 'message');
  if (connector) {
    const message = {
      id: 'trigger-message', type: 'object', name: 'message', label: '连接器消息',
      description: '消息连接器输入', hidden: true,
      children: connectorMessageOutputs.map((item) => ({ ...item })),
    };
    if (messageIndex < 0) data.structOutput.data.push(message);
    else {
      const current = data.structOutput.data[messageIndex];
      const { children, ...messageFields } = message;
      const sameMessage = outputFieldsEqual(current, messageFields)
        && outputListsEqual(current?.children, children);
      if (!sameMessage) data.structOutput.data[messageIndex] = message;
    }
  } else if (messageIndex >= 0) {
    data.structOutput.data.splice(messageIndex, 1);
  }
  return data.structOutput.data;
}
