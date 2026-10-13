const locale = {
  // app-level strings
  agentName: '智能 AI 助手',
  agentSubtitle: '为个人与团队提供智能问答和工作支持',
  brandTitle: '智能 AI 助手',
  newConversation: '新对话',
  curConversation: '当前',
  dock: '停靠到右侧',
  undock: '取消停靠',
  today: '今天',
  yesterday: '昨天',
  delete: '删除',
  deleteSuccess: '会话已删除',
  deleteFailed: '删除会话失败, 请稍后重试',
  retry: '重试',
  deepThink: '深度思考',
  placeholder: '请输入您的问题... (Shift + Enter 换行, Enter 发送)',
  itIsNowANewConversation: '当前已是新会话, 请先在此会话发送消息',

  // welcome / empty state
  welcomeTitle: '你好, 欢迎使用智能 AI 助手 👋',
  welcomeDescription:
    '我可以协助您进行内容创作、数据分析、知识问答和任务规划, 为个人与团队提供便捷的智能服务。',
  promptsTeacherTitle: '✍️ 内容创作',
  promptsLeaderTitle: '📊 数据分析',
  promptsStudentTitle: '📚 知识问答',
  promptsParentTitle: '🧭 工作提效',
  promptsRecommendTitle: '✨ 你可以这样问我',

  // teacher prompts
  promptTeacher_1_label: '撰写方案',
  promptTeacher_1_desc: '根据目标和受众生成一份结构清晰的项目方案',
  promptTeacher_2_label: '内容改写',
  promptTeacher_2_desc: '润色一段文字, 使表达更专业、简洁',
  promptTeacher_3_label: '文档审阅',
  promptTeacher_3_desc: '检查文档中的表达和逻辑问题, 并给出修改建议',
  promptTeacher_4_label: '内容总结',
  promptTeacher_4_desc: '提炼长文的核心观点、结论和待办事项',

  // leader prompts
  promptLeader_1_label: '撰写周报',
  promptLeader_1_desc: '根据本周进展、风险和关键数据起草一份工作周报',
  promptLeader_2_label: '推广文案',
  promptLeader_2_desc: '为新产品发布撰写一份简洁有吸引力的推广文案',
  promptLeader_3_label: '管理建议',
  promptLeader_3_desc: '分析团队工作量和任务数据, 给出资源安排建议',

  // student prompts
  promptStudent_1_label: '概念讲解',
  promptStudent_1_desc: '用简单语言解释一个复杂概念, 并给出示例',
  promptStudent_2_label: '问题分析',
  promptStudent_2_desc: '分析问题的可能原因, 并给出解决思路',
  promptStudent_3_label: '行动计划',
  promptStudent_3_desc: '围绕一个目标制定四周行动计划',

  // parent prompts
  promptParent_1_label: '沟通建议',
  promptParent_1_desc: '如何礼貌、清晰地向协作方说明项目延期风险',
  promptParent_2_label: '任务拆解',
  promptParent_2_desc: '把一个复杂任务拆成可执行的步骤和检查点',
  promptParent_3_label: '会议准备',
  promptParent_3_desc: '整理会议议程、讨论问题和会后待办模板',

  // built-in demo conversations
  whatIsAntDesignX: '帮我写一份项目方案',
  howToQuicklyInstallAndImportComponents: '生成一份团队周工作汇报',
  newAgiHybridInterface: '如何把复杂任务拆成清晰步骤?',

  // canned assistant replies for demo history
  aiMessage_1:
    '拆解复杂任务时, 可以先明确目标和完成标准, 再按阶段列出关键成果, 将每个成果拆成可执行动作, 最后补充负责人、截止时间和检查点。优先处理依赖项和高风险事项, 并定期根据进展调整计划。',
  aiMessage_2:
    '一份清晰的团队周报通常包括五部分: ① 本周完成事项; ② 核心数据与成果; ③ 当前风险和阻塞; ④ 需要协作或决策的事项; ⑤ 下周工作计划。我可以基于您提供的信息自动整理成统一模板。',

  // placeholders / error fallbacks for useXChat
  noData: '思考中, 请稍候...',
  requestAborted: '_(已停止生成)_',
  requestFailed: '请求失败, 请稍后再试',

  // slot config in Sender
  slotTextStart: '请用',
  slotTextEnd: '的方式回答下面的问题:',
};

export default locale;
