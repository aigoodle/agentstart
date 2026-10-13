/**
 * 节点元数据目录 —— 内置节点的清单
 *
 * 对外通过默认导出。宿主项目可以：
 *   - 直接读取本清单渲染扩展面板
 *   - 通过 `.concat(...)` 追加自己的节点条目
 *
 * {@link NodeCatalogItem.type} 与 backend `spring-agent-workflow` 的
 * {@code NodeType} 枚举一致（UPPER_SNAKE），也是 FlowDesigner 里
 * `<template #node-{type}>` 注册的 slot 名 —— 单一标识贯穿画布、持久化、
 * 引擎执行三层。设计器独有的类型（USER_INPUT / FILE_UPLOAD /
 * LOOP）没有严格对应的 backend NodeType，backend 的
 * {@code NodeType.fromJson} 会把它们映射到最接近的引擎节点（前三者 → START，
 * LOOP → ITERATION），使得设计器保存的图仍能跑起来。
 */

export interface NodeCatalogItem {
  /**
   * 唯一标识 —— 同时是 VueFlow slot 名和 backend `NodeType` 枚举名，
   * 保证画布 / 存储 / 引擎三处对齐。
   */
  type: string;
  name: string;
  icon: string;
  category: '输入' | 'AI 模型' | '逻辑' | '工具' | '输出' | '其他';
  description?: string;
}

const nodeItems: NodeCatalogItem[] = [
  // 输入
  { type: 'START', name: '开始', icon: 'start', category: '输入' },
  { type: 'USER_INPUT', name: '用户输入', icon: 'user', category: '输入' },
  { type: 'FILE_UPLOAD', name: '文件上传', icon: 'file', category: '输入' },

  // AI 模型
  { type: 'VIDEO_GENERATION', name: '视频生成', icon: 'llm', category: 'AI 模型', description: '选择视频模型，异步生成视频' },
  { type: 'LLM', name: 'LLM', icon: 'llm', category: 'AI 模型' },
  { type: 'AGENT', name: 'Agent', icon: 'agent', category: 'AI 模型' },
  { type: 'KNOWLEDGE_RETRIEVAL', name: '知识检索', icon: 'knowledge', category: 'AI 模型' },
  { type: 'QUESTION_CLASSIFIER', name: '问题分类', icon: 'classifier', category: 'AI 模型' },

  // 逻辑
  { type: 'IF_ELSE', name: '条件分支', icon: 'condition', category: '逻辑' },
  { type: 'LOOP', name: '循环', icon: 'loop', category: '逻辑' },
  { type: 'ITERATION', name: '迭代', icon: 'loop', category: '逻辑' },
  { type: 'LIST_OPERATOR', name: '列表操作', icon: 'variable', category: '逻辑' },
  { type: 'VARIABLE_AGGREGATOR', name: '变量聚合', icon: 'variable', category: '逻辑' },
  { type: 'VARIABLE_ASSIGNER', name: '变量赋值', icon: 'variable', category: '逻辑' },
  { type: 'PARAMETER_EXTRACTOR', name: '参数提取', icon: 'variable', category: '逻辑' },
  { type: 'HUMAN_INPUT', name: '人工介入', icon: 'user', category: '逻辑' },
  { type: 'APPROVAL', name: '审批', icon: 'user', category: '逻辑' },
  { type: 'WAIT_EVENT', name: '等待事件', icon: 'user', category: '逻辑' },
  { type: 'SLEEP_UNTIL', name: '定时等待', icon: 'schedule', category: '逻辑' },

  // 工具
  { type: 'HTTP_REQUEST', name: 'HTTP 请求', icon: 'http', category: '工具' },
  { type: 'SERVICE_API', name: '服务接口', icon: 'service', category: '工具' },
  { type: 'CONNECTOR', name: '连接器', icon: 'connector', category: '工具', description: '调用已安装的外部连接器' },
  { type: 'SCHEDULE_TRIGGER', name: '定时任务', icon: 'schedule', category: '工具' },
  { type: 'CODE', name: '代码执行', icon: 'code', category: '工具' },
  { type: 'TEMPLATE_TRANSFORM', name: '模板转换', icon: 'template', category: '工具' },
  { type: 'DOCUMENT_EXTRACTOR', name: '文档提取', icon: 'file', category: '工具' },

  // 输出
  { type: 'ANSWER', name: '直接回复', icon: 'answer', category: '输出' },
  { type: 'END', name: '结束', icon: 'end', category: '输出' },
];

export default nodeItems;
