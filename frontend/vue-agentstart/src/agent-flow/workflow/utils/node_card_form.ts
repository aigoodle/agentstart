/**
 * Per-node default `data` shape, keyed by the SAME identifier the FlowDesigner
 * registers its `#node-XXX` slot under and stores on `node.type`. Aligned with
 * the backend `NodeType` enum (UPPER_SNAKE) so a saved graph round-trips
 * cleanly — no adapter translation needed between the designer and the engine.
 *
 * Designer-only entries with no backend {@code NodeType} equivalent
 * ({@code USER_INPUT}, {@code FILE_UPLOAD}, {@code LOOP})
 * still use the UPPER_SNAKE convention for consistency; the backend's
 * {@code NodeType.fromJson} maps them onto the closest engine node at run
 * time (USER_INPUT/FILE_UPLOAD → START, LOOP → ITERATION).
 */
const nodeCardForm: any = {
  START: {
    variables: [],
    sysVariables: [
      { name: 'query', type: 'String', required: true },
      { name: 'files', type: 'Array[File]' },
      { name: 'dialogue_count', type: 'Number' },
      { name: 'conversation_id', type: 'String' },
      { name: 'user_id', type: 'String' },
      { name: 'tenant_id', type: 'String' },
      { name: 'app_id', type: 'String' },
      { name: 'workflow_id', type: 'String' },
      { name: 'workflow_run_id', type: 'String' },
    ],
    triggers: {},
    triggersEnabled: false,
    structOutput: {
      schema: {},
      data: [
        {
          id: '0',
          type: 'object',
          name: 'triggers',
          value: '',
          description: '触发器输出',
          hidden: true,
          children: [
            { name: 'type', type: 'string', label: '触发器类型' },
          ],
        },
      ],
    },
    output: [{ type: 'string', name: 'query', value: '', label: '用户查询' }],
  },
  ANSWER: {
    answer: '',
  },
  AGENT: {
    runtimeType: 'NATIVE',
    runtimeRef: '',
    modelId: '',
    context: '',
    system: '',
    selectOptions: [],
    required: false,
    memory: {
      window: {
        enabled: true,
        size: 10,
        user: '0',
        assistant: '0',
      },
    },
    model: {
      completionParams: {},
      modelId: 'chat',
    },
    systemPrompt: {
      id: 'prompt_template',
      role: 'system',
      text: '',
    },
    userPrompt: {
      id: 'user_prompt_template',
      role: 'user',
      text: '用户查询：{{#var.query#}}',
    },
    tools: [],
    skillIds: [],
    agentParameters: {},
    output: [
      { type: 'string', name: 'text', value: '', label: '生成内容' },
      { type: 'file', name: 'file', value: null, label: '生成文件' },
      { type: 'object', name: 'json', value: {}, label: '生成Json' },
    ],
  },
  LLM: {
    temperature: 0.7,
    max_tokens: 1000,
    prompt: '',
    model: {
      completionParams: {},
      modeId: '',
      mode: 'chat',
    },
    systemPrompt: {
      id: 'prompt_template',
      role: 'system',
      text: '',
    },
    userPrompt: {
      id: 'user_prompt_template',
      role: 'user',
      text: '用户查询：{{#var.query#}}',
    },
    memory: {
      query_prompt_template: '{{#sys.query#}}',
      role_prefix: {
        assistant: '',
        user: '',
      },
      window: {
        enabled: true,
        size: 10,
        user: '0',
        assistant: '0',
      },
    },
    structOutputEnabled: false,
    structOutput: {
      schema: {},
      data: [
        {
          id: '0',
          type: 'object',
          name: 'struct',
          value: '',
          description: '结构化内容',
          hidden: true,
          children: [],
        },
      ],
    },
    output: [{ type: 'string', name: 'text', value: '', label: '生成内容' }],
  },
  QUESTION_CLASSIFIER: {
    categories: ['正面', '负面', '中性'],
    model: {
      completionParams: {},
      modeId: '',
      mode: 'chat',
    },
    classes: [
      {
        id: 1,
        name: '',
      },
    ],
  },
  IF_ELSE: {
    cases: [],
    output: {},
  },

  CODE: {
    modelId: '',
    context: '',
    system: '',
    variables: [],
    selectOptions: [],
    required: false,
    memory: { windows: 1 },
    code_language: 'python',
    code: '\ndef main(arg1: str, arg2: str) -> dict:\n    return {\n        "result": arg1 + arg2,\n    }\n',
  },
  KNOWLEDGE_RETRIEVAL: {
    label: '知识检索',
    description: '知识库检索节点',
    queryVariableSelector: [],
    required: false,
    memory: { windows: 1 },
    metadataFilter: 'disabled',
    metadataConditions: [{ name: '', value: '', operator: 'IS' }],
    dataset: {
      useAnnotation: false,
      dynamic: false,
      dynamicVariableSelector: [],
      datasets: [],
    },
    output: [
      {
        type: 'Array[Object]',
        name: 'result[]',
        value: '',
        label: '召回分段',
        children: [
          {
            type: 'string',
            name: 'text',
            value: '',
            label: '分段内容',
          },
          {
            type: 'object',
            name: 'metadata',
            value: '',
            label: '元数据',
          },
        ],
      },
    ],
  },
  USER_INPUT: {
    variable: '',
    required: true,
  },
  FILE_UPLOAD: {
    allowedTypes: ['pdf', 'txt', 'doc'],
    maxSize: 10,
  },
  LOOP: {
    maxIterations: 10,
    condition: '',
  },
  VARIABLE_AGGREGATOR: {
    variables: [],
    operation: 'combine',
    output: [
      {
        type: 'Object',
        name: 'result',
        value: '',
        label: '聚合变量',
        children: [],
      },
    ],
  },
  HTTP_REQUEST: {
    method: 'GET',
    url: '',
    timeoutSeconds: 30,
    maxRetries: 0,
    authorization: {
      auth_type: 'none',
      api_key_header: 'Authorization',
      api_key_header_prefix: 'bearer',
      api_key_value: '',
    },
    headers: [{ name: '', value: '' }],
    parameters: [{ name: '', value: '' }],
    bodyType: 'NONE',
    body: {
      NONE: null,
      BINARY: '',
      FORM_DATA: [{ name: '', value: '', type: 'TEXT' }],
      X_WWW_FORM_URLENCODED: [{ name: '', value: '' }],
      JSON: '',
      RAW: '',
    },
    output: [
      { type: 'number', name: 'status', value: 0, label: '响应状态码' },
      { type: 'string', name: 'body', value: '', label: '响应体' },
      { type: 'object', name: 'headers', value: {}, label: '响应头' },
    ],
  },
  SERVICE_API: {
    method: 'GET',
    url: '',
    timeoutSeconds: 30,
    headers: [{ name: '', value: '' }],
    parameters: [{ name: '', value: '' }],
    output: [
      { type: 'number', name: 'status', value: 0, label: '响应状态码' },
      { type: 'string', name: 'body', value: '', label: '响应体' },
    ],
  },
  VIDEO_GENERATION: {
    label: '视频生成', model: {}, prompt: '', parameters: {}, taskTimeoutSeconds: 3600,
    output: [{ type: 'object', name: 'result', value: {}, label: '生成结果', children: [
      { type: 'object', name: 'data', value: {}, label: '视频', children: [
        { type: 'string', name: 'videoUrl', value: '', label: '视频地址' },
        { type: 'string', name: 'taskId', value: '', label: '任务编号' },
        { type: 'string', name: 'status', value: '', label: '状态' },
      ] },
    ] }],
  },
  CONNECTOR: {
    label: '连接器', provider: '', connectorId: '', connectorName: '',
    connectorMode: 'CHANNEL_MESSAGE', channelSource: 'REPLY_TRIGGER', channelConnectionId: '',
    channelName: '', messageContent: '', messageType: 'TEXT', targetId: '', channelConversationId: '',
    actionId: '', actionName: '', installationId: '', connectionId: '', connectionName: '',
    connectionSource: 'DEFAULT', connectionIdTemplate: '',
    inputs: {}, inputField: 'input', inputText: '', outputKey: 'result',
    output: [{
      type: 'object', name: 'result', value: {}, label: '连接器执行结果', children: [
        { type: 'boolean', name: 'success', value: true, label: '是否执行成功' },
        { type: 'object', name: 'data', value: {}, label: '业务返回数据' },
        { type: 'array', name: 'content', value: [], label: '文本或资源内容' },
        { type: 'object', name: 'metadata', value: {}, label: '执行元数据' },
        { type: 'string', name: 'provider', value: '', label: '连接器提供方' },
        { type: 'string', name: 'connectorId', value: '', label: '连接器 ID' },
        { type: 'string', name: 'actionId', value: '', label: 'Action ID' },
        { type: 'string', name: 'connectionId', value: '', label: '连接账号 ID' },
        { type: 'string', name: 'messageId', value: '', label: '渠道消息 ID' },
        { type: 'string', name: 'channelId', value: '', label: '消息渠道 ID' },
        { type: 'string', name: 'targetId', value: '', label: '接收目标 ID' },
        { type: 'string', name: 'conversationId', value: '', label: '会话 ID' },
      ],
    }],
  },
  SCHEDULE_TRIGGER: {
    name: '工作流定时任务',
    targetWorkflowId: '',
    targetWorkflowName: '',
    targetWorkflowIcon: '',
    targetWorkflowIconBackground: '',
    targetWorkflowInputs: [],
    model: { modelId: '', modelName: '', mode: 'chat', completionParams: {} },
    inputVariableSelector: [],
    extractionPrompt: {
      id: 'schedule_extraction_prompt',
      role: 'system',
      text: '',
    },
    schedule: '',
    output: [
      { type: 'boolean', name: 'success', value: true, label: '是否成功' },
      { type: 'string', name: 'errorCode', value: '', label: '错误码' },
      { type: 'string', name: 'message', value: '', label: '结果消息' },
      { type: 'object', name: 'result', value: {}, label: '解析后的定时参数' },
      { type: 'string', name: 'action', value: '', label: '执行动作' },
      { type: 'string', name: 'taskName', value: '', label: '定时任务名称' },
      { type: 'object', name: 'data', value: {}, label: '目标工作流输入' },
      { type: 'string', name: 'intent', value: '', label: '用户意图' },
      { type: 'string', name: 'triggerId', value: '', label: '定时任务 ID' },
      { type: 'string', name: 'nextFireAt', value: '', label: '下次执行时间' },
      { type: 'string', name: 'targetWorkflowId', value: '', label: '目标工作流 ID' },
      { type: 'string', name: 'userId', value: '', label: '创建用户 ID' },
      { type: 'string', name: 'tenantId', value: '', label: '租户 ID' },
      { type: 'number', name: 'deletedCount', value: 0, label: '删除数量' },
      { type: 'array', name: 'deletedTriggers', value: [], label: '已删除任务' },
    ],
  },
  TEMPLATE_TRANSFORM: {
    template: '',
    variables: [],
  },
  END: {
    output: [],
  },
  ITERATION: {
    iteratorVariableSelector: [],
    outputVariableSelector: [],
    childrenNodes: [],
    parallelSize: 1,
    errorMode: 'terminate',
    output: [
      { type: 'array', name: 'result', value: [], label: '迭代结果' },
    ],
  },
  PARAMETER_EXTRACTOR: {
    parameters: [
      { name: 'param1', type: 'string', description: '', required: false },
    ],
    model: { modelId: '', modelName: '', mode: 'chat', completionParams: {} },
    reasoning_mode: 'function_call',
    inputVariableSelector: [],
    systemPrompt: {
      id: 'prompt_template',
      role: 'system',
      text: '',
    },
    output: [
      { type: 'object', name: 'result', value: {}, label: '抽取结果' },
    ],
  },
  LIST_OPERATOR: {
    inputVariableSelector: [],
    operation: 'filter',
    filterConditions: [],
    mapExpression: '',
    offset: 0,
    limit: 100,
    sortField: '',
    sortOrder: 'asc',
    output: [
      { type: 'array', name: 'result', value: [], label: '结果列表' },
    ],
  },
  DOCUMENT_EXTRACTOR: {
    variableSelector: [],
    extractMode: 'text',
    splitMode: 'none',
    chunkSize: 1000,
    output: [
      { type: 'string', name: 'text', value: '', label: '提取文本' },
    ],
  },
  HUMAN_INPUT: {
    correlationKey: '',
    formMode: 'FIXED',
    model: { modelId: '', modelName: '', modelProvider: '', mode: 'chat', completionParams: {} },
    generationPrompt: '{{#sys.query#}}',
    inputSchema: { type: 'object', properties: {} },
    formFields: [],
    formTitle: '人工输入',
    prompt: '',
    submitButtonText: '提交',
    timeoutEnabled: true,
    timeoutValue: 1,
    timeoutUnit: 'hour',
    timeoutSeconds: 3600,
    presentationMode: 'AUTO',
    channelProvider: '', channelId: '', channelConnectionId: '', channelTarget: '',
    deliveryFallback: 'WEB_LINK_THEN_TEXT',
    output: [{ type: 'object', name: 'values', value: {}, label: '人工提交数据' }],
  },
  APPROVAL: {
    correlationKey: '', inputSchema: { type: 'object', properties: { approved: { type: 'boolean' } }, required: ['approved'] },
    timeoutSeconds: 3600, timeoutStrategy: 'TIMEOUT', escalationCorrelationKey: '',
    allowedUserIds: [], allowedRoles: [], escalationAllowedRoles: [],
    output: [{ type: 'boolean', name: 'approved', value: false, label: '是否批准' }],
  },
  WAIT_EVENT: {
    correlationKey: '', inputSchema: { type: 'object', properties: {} }, timeoutSeconds: 0,
    output: [{ type: 'object', name: 'payload', value: {}, label: '事件载荷' }],
  },
  SLEEP_UNTIL: {
    until: '', delayMillis: undefined,
    output: [],
  },
  VARIABLE_ASSIGNER: {
    assignments: [
      { target: '', source: [], writeMode: 'overwrite' },
    ],
    output: [],
  },
};
export default nodeCardForm;
