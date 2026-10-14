# 迁移与重构计划

旧项目：`/home/chm/chenhuanmou/spring-agent-start`。新项目独立构建，迁移不修改旧项目。

## 目标与约束

- Java 21，Spring Boot 4.1.1，Spring AI 2.0.1；参考本地 spring-ai-examples 的 2.0 系列用法。
- Maven groupId 与 Java 包统一为 `io.aigoodle`。
- 按基础设施、执行引擎、上下文、集成、接入、独立 server 分层。
- 保留已有功能与测试，按职责重构；不把复制源码、空模块或编译成功视为迁移完成。
- Java 与 POM 使用多行格式，迁移后的代码必须可阅读。
- 基础层不依赖 engine、integrations 或 access；运行时适配与业务契约分离。

## 执行顺序

1. 依赖基线与坐标：统一版本管理、内部依赖与包名，确认 Maven reactor。
2. foundation：common 的身份、目录、加密与 JSON；persistence 的租户隔离与审计；model 的提供商、凭证、模型实例与配置；observability 的模型计量。将持久化基类归入 persistence，业务观测适配延后到对应业务模块。
3. engine：工具契约与执行、agent 应用契约、AgentScope 运行时适配、工作流 DAG 与恢复。补齐旧实现行为测试，再替换运行时。
4. context：知识入库、向量存储适配、RAG 管线、记忆和技能。保留可选提供商与存储能力。
5. integrations：连接器、插件与视频任务、渠道及平台适配、触发器；把可选集成与核心契约分开。
6. access 与 server：管理 API、兼容对话 API、自动装配、数据库迁移与启动配置；统一 HTTP 栈和文件/流式接口，验证完整启动。
7. frontend：核对 demo 和 Vue 组件包的接口、构建、命名和联调；需要重命名时同时更新工作区引用。
8. 全项目验收：全量构建与测试、前端构建、数据库与运行联调、兼容接口及自动配置检查；清理旧包与路径，更新运行文档。

## 模型供应商专项优化（2026-10-14）

- 供应商聚合改为agentstart-model-provider，子模块统一agentstart-model-*，增加OpenAI/Kimi/智谱/通用OpenAI接口模块；前端目录、导出路径和宿主引用改为model-provider-hub。
- 系统默认模型桌面端每行4卡片；栏目显示“已支持模型供应商”，包含已配置/未配置供应商，最后为自定义接口。
- 移除启动目录同步监听器；配置单个供应商时同步其预置，管理员手动入口同步全目录。目录中已有用户配置保留。
- 供应商页面的模型/凭证/启用设置改为请求内批量读取，取消逐卡重复查询；默认业务与Mapper日志改为INFO，可按具体Mapper开启SQL调试。
- 针对启动无数据库访问、部分旧目录与新预置合并、请求批量读取、自定义子路径HTTP目录/对话新增回归；最终页面联调通过：每行4卡、9个已支持供应商且自定义最后、手动同步成功、Base URL缺失前后端均拒绝；最终包启动health UP，启动和联调日志SQL明细为0；卡片名称换行且状态标记移到内容区，避免徽标挤压标题。

- 验证：全项目test/package成功，加上新增配置校验回归合计886项后端测试（0失败/错误，14项环境跳过）；前端105项测试、组件库及demo构建、全项目Spotless检查通过。日志在/tmp/agentstart-provider-complete-regression.log和/tmp/agentstart-provider-credential-validation.log。截图已保存到artifacts/migration-validation/model-providers-four-cards.jpg与model-provider-supported-gallery.jpg。

- 本轮联调进程已停止，源代码与截图已保存；按用户要求，系统已接受2026-10-14 08:42:29（Asia/Shanghai）的关机计划。

## 迁移基线验收（本轮优化前）

- 迁移重构已完成：foundation、engine、context、integrations、access 五个职责分组与独立一级 server；Java 包和 Maven groupId 为 io.aigoodle。
- 基线为 Java 21 / Spring Boot 4.1.1 / Spring AI 2.0.1；Java/POM 由 Spotless 统一格式化。默认执行后端为 NATIVE，AgentScope 为可选后端。
- 最终干净构建通过：`mvn clean verify spotless:check`，881 项测试，0 失败/错误，14 项跳过（外部模型/渠道及专项环境测试）。Vue 组件库105项测试、组件库/demo/React聊天前端构建全部通过。
- 最终 jar 使用现有 `.env.local` 启动，PostgreSQL/Redis 配置生效，actuator health 为 UP。实际 Qwen 工具调用、SDK 父应用委派已发布 NATIVE 子应用、重启后的审批恢复、知识上传/分块/嵌入检索均已验证。
- 浏览器验收：主要管理页面、工作流画布和节点参数、应用创建/编辑/保存/发布、嵌入聊天流式返回45；亮暗/颜色/视觉模式/系统模式与刷新保持通过。临时验收应用和知识库已清理。
- 已有配置限制：5个触发器指向空的已发布工作流；wechat-official 不在原、新 native 平台实现中，原账号保留并显示降级。实际 Elasticsearch/Milvus 服务与外部渠道发送未在本机联调；可选存储9项自动装配测试通过。不修改旧业务数据来掩盖这些配置问题。
- 关机：验收结果已保存，本次后台/demo/聊天前端均已停止；系统已接受关机计划：2026-10-14 01:47（Asia/Shanghai）。

## 迁移基线验收证据

| 要求 | 当前证据 |
| --- | --- |
| 从基础层迁移全部业务模块 | 原reactor的业务源码已迁入；RAG四模块合并，HTTP starter合并，业务观测移入对应模块；旧example的启动职责由server承接，原未启用zhipu独立SDK不加入新reactor。 |
| 包名、坐标、模块边界 | 根POM及所有子POM使用io.aigoodle；源码无旧包引用；foundation无engine/context/integrations/access依赖；server直接继承根POM。 |
| 升级后可构建、格式化、保留行为 | 57个reactor项目全部verify成功；881项测试和Spotless通过，覆盖租户、模型、工具、Agent、workflow、知识、渠道、插件、HTTP和自动配置。 |
| SDK适配可运行 | 真实工具调用和已发布委派成功；JDBC版本冲突、身份/预算/取消、多轮审批及两个独立Java进程恢复有回归。注解工具Schema库重定位，最终jar只含隔离SDK分发包和主应用Schema3/5依赖。 |
| 前后端实际联调 | PostgreSQL/Redis连接、Qwen算术、实际嵌入检索、页面创建保存发布、聊天iframe与流式结果均已运行核对；health UP。 |
| 主题正确 | 浏览器验证亮暗、蓝/紫色、普通/科技、跟随系统及刷新保留；修正overview强制暗色和系统模式状态混淆。 |
| 保存与文档 | README记录构建、env启动、demo和React聊天前端启动；截图保存在`/home/chm/Documents/ChatGPT/agent-start/artifacts/migration-validation/`。 |

最终构建日志：`/tmp/agentstart-final-acceptance.log`；前端日志：`/tmp/agentstart-final-draft-ui-tests.log`、`/tmp/agentstart-final-draft-ui-build.log`、`/tmp/agentstart-final-draft-demo-build.log`、`/tmp/agentstart-chat-build.log`。日志是本次运行的临时证据，持久化验收结论和截图以上述文件/目录为准。

## 阶段验证记录（历史）

下列记录保留各阶段的证据；其中“尚未”描述当时状态，当前状态以上节为准。

- 页面编辑/发布/对话验收通过：浏览器选择qwen3.7-plus并保存提示词，后台验证保存字段与ACTIVE版本，嵌入React聊天调用calculator返回45；临时应用已删除。最新亮色/蓝色/普通模式与刷新保持已复测。可选pgvector/Elasticsearch/Milvus新增9项选中/未选中/宿主覆盖测试全部通过；pgvector装配明确排在Boot JDBC之后。未连接实际Elasticsearch/Milvus服务，该限制不等同于线上部署已验证。

- 工具网关身份修复复测通过：真实 SDK 父应用调用已发布 NATIVE 子应用，工具观测和最终答案均为60，临时应用已清理。修复后 server 依赖链格式化/测试/打包成功；组件库105项测试和构建、demo和React聊天前端构建成功。聊天代理默认地址改为本地后台并支持env覆盖，新建应用显式为草稿。

- 真实 SDK 父应用调用已发布 NATIVE 子应用暴露工具网关虚拟线程丢失 CurrentUser 的问题；已在提交任务时捕获身份、执行时 callAs 并恢复，新增匿名后续调用隔离回归。临时应用已删除，修复后的真实委派复测进行中。

- 修正 SDK 自动工厂遗漏目录回调的装配问题；新增自动装配调用宿主目录的回归。PublishedAgentDelegation 在可信 CurrentUser.extra 中传递祖先链，拒绝回到上游、非法链及深度超限，执行后恢复原调用者。36 项 SDK/链路测试及格式检查通过（/tmp/agentstart-sdk-delegation-cycle.log）；真实已发布应用委派仍待联调。

- SDK 委派已接入平台目录与已发布应用执行服务：AgentToolCatalog 延迟解析，ToolRegistry.scoped 保留网关/身份但使用调用级工具快照；委派经同一治理网关，拒绝自委派及重复名称。真实 SDK 测试确认子任务调用一次、捕获调用者身份、结果回传且全局工具目录不变。33 项适配测试、现有5项解析测试及server6项启动测试通过（/tmp/agentstart-sdk-delegation-server.log）。委派循环与真实发布应用联调仍待验证。

- 工具解析契约提取为 AgentToolCatalog，现有 AgentToolResolver 实现公开以供执行适配复用；注册工具选择、委派名称及租户检查保留原实现。5 项现有工具解析测试及格式检查通过（/tmp/agentstart-tool-catalog.log）。SDK 委派尚未接入该契约。

- 真实 Redis 专项通过：使用 server env 认证并选择配置数据库，PING 成功，执行 RedisChannelOutboundRateLimiter 源码中的原始 Lua（独立临时键），首次返回0、第二次返回989ms，临时键删除。未发送渠道消息或输出凭证。Vue/demo 未发现其他无指令嵌套 template。

- 真实文件入库/检索及 UI 验证通过：独立 HIGH_QUALITY 测试库使用现有 Qwen Embedding，文本上传完成，API 和页面向量召回原文，分数0.661。修复 RecallTestingPanelV2 无指令 template 导致结果正文不渲染，并将侧栏分段数标签从“关联应用”改为“片段”。新增结果/空状态 DOM 回归；组件库105测试、类型检查和构建通过，临时知识库已清理。截图 /tmp/agentstart-knowledge-retrieval-fixed.jpg。

- 真实 PostgreSQL + Qwen 验证成功：独立草稿 SDK 应用调用 calculator 得到2；带审批的调用暂停后，后台进程重启，再经 HTTP 批准原 runId，执行一次并返回50，数据库状态 COMPLETED。临时草稿已删除，运行审计保留。修正恢复控制器长事务包裹整个模型执行的问题，各运行存储继续使用自己的短事务；AgentRun 相关测试及格式检查通过（/tmp/agentstart-resume-http-check.log）。当前服务会话81026，健康UP。

- 隔离 SDK 的可执行包已验证：server package 成功，BOOT-INF/lib 只有 agentstart-agentscope-sdk，没有 agentscope-core；主应用仍保留 Victools 5/Validator 3。新包用现有 env 重启成功，健康 UP，tools/model-providers/datasets/triggers 接口返回 ok（会话 66053，/tmp/agentstart-live-isolated-server.log）。SDK 打包生命周期和依赖边界已写入模块 README，最终真实交互验收仍待完成。

- SDK 依赖隔离完成：新增 agentstart-agentscope-sdk，在 generate-resources 重定位 SDK 内部 Victools 4/Validator 2，process-resources 导出类供 reactor 直接测试；SDK 本身与内部库进入独立分发 jar，其他依赖正常传递。恢复并通过注解 Schema 回归。32 项适配测试无跳过；从干净目录运行 server/SDK/JDBC/独立进程的 45 项针对性测试及格式检查成功（/tmp/agentstart-sdk-isolated-server.log）。完整打包和最终联调仍待验收。

- SDK 依赖隔离范围已核对缓存发行 POM 与 Shade 3.6.2 插件描述：SDK Victools 4.38 与 JSON Schema Validator 2 需保留 Jackson 2 ABI，主应用保留 Victools 5/Validator 3。注解工具的兼容性回归已保存并明确禁用，直到隔离完成后启用；跳过不能作为兼容完成证据。

- SDK 独立进程恢复验证通过：两个 Java 子进程分别暂停/恢复，共享文件型 H2 状态表，第一进程退出后第二进程批准并执行工具一次，产生最终答案。夹具不共享内存状态，日志保存在测试临时目录；测试及格式检查通过（/tmp/agentstart-sdk-process-recovery.log）。公共运行记录恢复已由 JDBC 重建测试覆盖，真实 PostgreSQL + 外部模型的跨进程全链路仍待验收。

- 最新完整回归通过：`mvn test spotless:check`，865 项测试、0 失败/错误、14 跳过（/tmp/agentstart-approval-full-regression.log）；demo 类型检查及构建通过（/tmp/agentstart-approval-demo-build.log）。当前状态与历史验证记录已分节整理，最终验收待办保持明确。

- 总迭代限制跨审批段保留：默认工厂在模型边界使用累计计数检查 maxIterations，超限映射为 MAX_ITERATIONS（含持久化终态），不按每次恢复重置次数。批准/拒绝与 maxIterations=1 的真实运行时测试通过；31 项适配测试和格式检查通过（/tmp/agentstart-sdk-iteration-budget.log）。

- 多轮 JDBC 审批恢复通过：1/2 轮审批参数化测试每轮重建运行时/工厂/状态存储，第二次暂停更新审批 ID，累计模型/工具预算与实际调用次数一致，最后完成原运行。6 项 JDBC 集成测试及格式检查通过（/tmp/agentstart-sdk-multiround-approval.log），数据库为 H2 PostgreSQL 模式，独立进程及真实外部模型仍待验收。

- JDBC 审批恢复集成通过：暂停后重建 AgentScopeRuntime、DefaultAgentScopeAgentFactory 和 JdbcAgentScopeStateStore，再从持久化 runId 批准，工具仅执行一次，模型总计两次，原 runId 完成；模型上限2/工具上限1保持有效。5 项 JDBC 集成测试/格式检查通过（/tmp/agentstart-sdk-jdbc-approval.log）。使用 H2 PostgreSQL 模式及离线模型，不替代独立进程/真实 PostgreSQL/外部模型验收。

- 审批累计预算接入：检查点记录默认工厂实际模型/工具计数，resume 恢复计数而不重置额度；移除带预算审批的临时拒绝限制。批准/拒绝与模型上限 0/1/2 的真实 SDK 组合测试通过；工具计数恢复、超额拒绝及失败锁存测试通过。29 项适配测试/格式检查通过（/tmp/agentstart-sdk-approval-budget.log），多轮审批及 JDBC 重建续跑仍待验收。

- SDK 审批批准/拒绝路径均经真实工厂与公共运行时验证：批准执行工具一次，拒绝执行零次并可完成；暂停记录 APPROVAL 步骤，避免输出伪 FINAL。24 项适配测试通过；追加审批/最终步骤断言后工厂测试与格式检查通过（/tmp/agentstart-sdk-approval-deny-step.log）。累计预算、多轮审批及 JDBC 重建续跑仍待验收。

- 公共 AgentScopeRuntime 已接入审批暂停/续跑：WAITING_APPROVAL 保存待批工具与原调用者，resume 校验租户/调用者/全部审批 ID，沿用运行及会话 ID 恢复 SDK 状态；活动运行防止重复执行。真实默认工厂测试覆盖暂停、错误调用者拒绝、批准执行一次及完成终态；23 项适配测试通过，编译/格式检查通过。memoryEnabled=false 或带模型/工具预算的审批配置仍明确拒绝，累计预算、拒绝/多轮审批与 JDBC 重建续跑仍待完成。

- 真实 SDK 审批续跑验证通过：ASK 暂停后关闭 Agent，从共享状态重建实例，批准消息恢复原调用 ID、解析参数及原始 JSON；工具仅执行一次并输出最终答案。修正确认 ToolUseBlock 缺少原始 content 导致 SDK 参数校验失败。23 项适配测试及格式检查通过（/tmp/agentstart-sdk-approval-resume.log）。公共运行时生命周期及跨进程审批恢复仍未开放。

- 审批权限路径验证：SDK 仅对 ToolBase 执行权限判断，GovernedAgentScopeTool 已继承 ToolBase；默认工厂为允许工具配置 ALLOW，为需审批工具配置 ASK，治理网关执行边界仍保留。真实 SDK 测试确认 ASK 产生带原调用 ID/参数的确认事件且未执行工具，23 项适配测试/格式检查通过（/tmp/agentstart-sdk-permissions.log）。公共 runtime 仍拒绝审批配置，待暂停/恢复全链路完成后开放。

- 审批桥接基础已加入：SDK RequireUserConfirmEvent 转平台 PendingApproval，持久化后构造 ConfirmResult 元数据消息，严格校验所有审批 ID 一一对应；混合批准/拒绝、未知 ID 及缺失决定测试通过。22 项适配测试和格式检查通过（/tmp/agentstart-sdk-approval-bridge.log）。该转换器尚未接入运行暂停/恢复，不能宣称审批续跑完成。

- SDK 状态存储配置不再静默降级：state-store=jdbc 且没有 JDBC/宿主状态存储时拒绝启动，未知 backend 拒绝启动；mock JdbcTemplate 装配用例确认选择 JdbcAgentScopeStateStore。21 项适配测试与格式检查通过（/tmp/agentstart-sdk-state-config.log）。

- 独立 JDBC 实例并发写入测试通过：相同版本只有一个提交成功（4 项 SDK JDBC 集成测试，/tmp/agentstart-sdk-jdbc-race.log）。新版 server 已重新打包并用 scripts/run-server.py/env 启动（会话 6219，/tmp/agentstart-live-server-state.log），健康 UP。真实 PostgreSQL agent_scope_state 表插入/版本更新/拒绝过期写入实测通过，验证事务回滚；这仍不替代真实提供商 SDK 会话执行/审批恢复验收。

- SDK 状态 JDBC 存储已实现并加入 server 默认装配：单值/列表、用户会话隔离、版本条件写入、删除及会话枚举；DDL 为 db/agentscope-schema.sql。完整 server 测试覆盖存储重建读取、过期写入拒绝、列表及删除、SDK 第二次调用读取数据库状态。3 项 JDBC 集成测试和格式检查通过（/tmp/agentstart-sdk-state-jdbc.log），此前含 server 6 项启动测试也通过。数据库测试为 H2 PostgreSQL 模式，真实 PostgreSQL 新表和审批续跑仍待验证。

- 生成参数覆盖测试确认：显式 SDK temperature/maxTokens 覆盖当前请求，topP/模型及应用默认值保留，下一次请求不继承临时覆盖。20 项适配测试通过（/tmp/agentstart-sdk-options.log）；整理模型/工厂的 SDK 类型导入，编译及格式检查通过（/tmp/agentstart-sdk-readability.log）。

- 模型适配职责整理：工具参数关联/严格 JSON 校验移入 SpringAiToolCallAssembler，SpringAiAgentScopeModel 专注消息和响应转换。19 项执行行为测试及格式检查通过（/tmp/agentstart-sdk-assembler.log）。

- 工具参数边界验证：严格 JSON 对象解析拒绝尾随对象/非法内容，避免默认 Jackson 忽略尾随令牌后执行错误参数；新增交错调用 ID、完整调用去重、冲突名称、歧义片段和双对象拒绝测试。19 项适配测试及格式检查通过（/tmp/agentstart-sdk-fragment-boundaries.log）。

- Spring AI 流式工具调用已按调用 ID 合并参数片段，完整 JSON 重复结果可去重；缺少 ID 的多调用片段与冲突名称明确拒绝，避免错误关联后执行。模型适配参数化测试覆盖完整/分段 JSON 及结果回传，17 项 SDK 适配测试和格式检查通过（/tmp/agentstart-sdk-fragments.log）。供应商累计快照和其他复杂流协议仍需进一步验证。

- SDK 会话并发保护：默认工厂使用 ConflictPolicy.FAIL，注入的 AgentStateStore 必须支持版本写入；拒绝不能进行版本校验的共享存储。双线程真实 SDK 同会话测试确认一个请求完成、另一个返回并发修改错误，防止静默覆盖会话。16 项适配测试和格式检查通过（/tmp/agentstart-sdk-conflict.log）。

- 默认 AgentScope 工厂共享 SDK AgentStateStore，宿主可注入自己的持久化状态存储；memoryEnabled=false 使用独立临时状态。真实 SDK 两次调用测试证实第二轮包含第一轮问题与回答，关闭记忆后不加载历史；15 项适配测试和格式检查通过（/tmp/agentstart-sdk-memory.log）。默认仍是内存 SDK 状态，跨进程审批恢复尚未完成。

- 最新回归 `mvn test spotless:check` 成功：844 项测试、0 失败/错误、14 跳过（/tmp/agentstart-sdk-full-regression.log），覆盖 server SDK 装配、JDBC 完成/取消及既有模块。已知 SDK 注解生成器 ABI 冲突不在通过路径内，仍须解决。

- JDBC 取消集成验证通过：完整 server 下执行永不完成的 SDK 模型，取消后发布流停止，CANCELLED 与结束时间/事件进入 JdbcAgentRunStore。使用 H2 PostgreSQL 模式，2 项 SDK JDBC 集成测试与格式检查通过（/tmp/agentstart-sdk-jdbc-cancel.log）。

- AgentScope 取消执行已接入：按租户/运行 ID 定位活动线程，中断模型订阅并持久化 CANCELLED；终态与重复取消直接返回，拒绝外租户及其他运行时的运行。启动前检查取消状态、执行结束与取消同步，避免取消状态被完成/失败覆盖。14 项适配测试及格式检查通过（/tmp/agentstart-sdk-cancel.log）；跨进程 SDK 检查点恢复与注解生成器隔离仍未完成。

- AgentScope 完整 server JDBC 集成测试通过：自动装配选择 JdbcAgentRunStore，真实 SDK 离线执行保存完成终态/响应/步骤，跨租户读取隔离；使用 H2 PostgreSQL 模式（/tmp/agentstart-sdk-jdbc.log），不作为真实 PostgreSQL SDK 执行证明。SDK 注解工具与 Spring AI SchemaGenerator 的 Jackson ABI 冲突仍待依赖隔离；Maven Central 当前发布版本仍为 2.0.3，暂无直接升级替代证据。

- 独立 server 已加入 AgentScope 适配依赖；完整应用测试发现 SDK 的 Victools 4.38 覆盖 Spring AI 2 所需 5.0，已统一主应用到 5.0，server 6 项启动/HTTP 测试及 13 项适配测试通过。额外探测证实 SDK 注解工具 SchemaGenerator 存在 Jackson 2/3 返回类型 ABI 冲突（/tmp/agentstart-schema-sdk.log），平台手工 schema 工具桥接不受影响；正式完成仍需隔离 SDK 注解生成器依赖，不能声称该扩展路径兼容。现有运行进程仍为此前打包版本，待重新打包启动验证。

- 工作流浏览器巡检：已有应用“流程编排-工作流设计”画布呈现 8 节点/7 连线，LLM 节点参数面板显示 qwen3.6-flash、提示词变量及记忆配置，调试输入面板成功打开；浏览器 error 日志为空。截图 /tmp/agentstart-workflow-editor.jpg。本轮仅查看，未保存/发布或执行已有包含集成节点的工作流，完整执行需用可控测试图验证。

- 可复现启动方式已保存到 scripts/run-server.py 与 README：执行打包后的一级 server jar，安全解析本地 env，支持指定 env/jar 和 JAVA_HOME，不通过 shell 执行配置内容。解析现有 9 项 env 配置及脚本语法检查成功。新增 env.local 与 Python 缓存 Git 忽略规则；源码/构建引用未发现旧 io.github.aigoodle 坐标。

- 修正 demo 跟随系统主题：模式独立持久化为 dark/light/system，系统媒体查询变化更新有效颜色，显式暗黑/明亮不再按颜色相同误判为系统。移除抽屉中的重复监听与颜色调试日志，选项增加 aria-pressed。浏览器验证系统模式刷新保留及显式选择互斥，demo 类型检查/构建通过（/tmp/agentstart-theme-mode-build.log）；尚未模拟操作系统颜色改变。

- 浏览器真实接口巡检：总览、应用工坊（14 应用）、知识库（5 库）、模型供应商、渠道（6 原生适配）、插件（视频插件）、MCP/技能空状态、工具（7 工具）、触发器（10 条）均成功呈现；浏览器 error 日志为空。计算器页面试运行 2*(3+4)/7 返回 2，截图 /tmp/agentstart-demo-calculator.jpg。仅证明页面加载及该工具执行；复杂编辑/发布/对话/文件上传交互仍需覆盖。

- 真实启动验证（2026-10-13）：server 可执行 jar 打包成功，使用 agentstart-server/.env.local 启动 Netty 18090；demo MOCK=false 在 5179 运行，浏览器加载数据库总览和实体计数。亮/暗、紫色主题、普通视觉模式及刷新保留已实测，修正总览主题标签写死为暗黑的问题，demo 构建通过。已定位现有数据库 5 个启用触发器指向已发布但 nodes=[] 的工作流 0a1709e4b49a3b5a37815cc86a024c73（版本 2026-08-18）；引擎正确拒绝空图，保留原数据；页面全覆盖、SDK 恢复和最终验收尚未完成。运行日志 /tmp/agentstart-live-server.log、/tmp/agentstart-live-demo.log；会话 28328/80510。

- AgentScope 已复用 AgentRunStore 保存 CREATED/RUNNING/终态与步骤，自动配置延迟获取宿主 JDBC 存储，独立宿主可用内存存储。完成、预算失败、超时及跨租户查询通过适配器测试；工具参数/结果片段按调用 ID 汇总进入步骤。13 项针对性测试和格式检查通过；这不等同于 SDK 检查点恢复，数据库端实际联调和审批恢复仍待验证。

- 最新全项目回归：`mvn test spotless:check` 成功，840 项测试、0 失败、0 错误、14 跳过（日志 `/tmp/agentstart-budget-regression.log`）。这证明自动配置及预算修改未破坏已有测试，真实 PostgreSQL 部署与浏览器交互验收仍独立待完成。

- Spring AI → AgentScope 文本/工具模型适配器已加入：沿用平台 ChatModel、保留供应商选项与通用生成参数、输出文本增量、转换工具调用与结果消息，模型回调禁止直接执行工具。离线测试验证流式文本不重复、调用 ID/JSON 参数/结果及采样设置；与工具适配、运行时、自动配置共 13 项测试通过。默认工厂已自动组装平台模型、技能提示和治理工具；真实 SDK 离线循环验证工具执行及结果回传，自定义工厂可覆盖默认装配。模型/工具预算已通过共享原子计数限制，工具预算失败后阻止后续模型调用；测试覆盖真实 SDK 模型上限、并行工具计数、无限预算与运行隔离。复杂内容/供应商流片段、恢复能力仍待完成，已加入 server 可选执行后端，应用默认仍为 NATIVE。

- 已完成 server 一级模块调整，移除 applications 空聚合层。
- 已统一新项目 POM groupId；已引入 Boot / AI BOM。
- common、persistence、model 源码与原测试已迁入；基础测试通过，在线 Ollama 测试因服务不可达跳过。
- BaseEntity 已移入 persistence；模型设置服务校验请求租户，model 自动配置显式排在 persistence 之后。
- 已接入 Spotless / Palantir Java Format，迁入代码已统一格式化。
- observability 的模型计量、费用聚合、趋势查询及原测试已迁入；新增登录租户隔离测试。Agent/tool/channel 观测与评估适配已迁入对应上层模块。
- tools、memory、skill 已迁入并格式化，原测试通过。tools 仅依赖 common 和 Spring AI 工具契约，不依赖模型目录和数据库。
- skill 增加 H2 生命周期与租户隔离集成测试；更新、删除 SQL 显式约束租户，技能表加入 persistence 默认保护列表。
- RAG 的契约、查询规划与重排已合并到 agentstart-rag；knowledge 与 Agent 旧运行时已迁入并通过原测试。
- connector、channel、plugin、workflow、trigger 已迁入并格式化，原模块测试已通过；trigger 测试补齐了关联应用权限查询所需的 agent schema。
- persistence 补上 MyBatis queryCursor 拦截：MyBatis-Plus 3.5.17 的 selectOne 使用游标，原拦截签名会遗漏此路径。知识模块跨租户 Mapper 测试现已通过，并显式验证 SQL 拦截器注册。
- AgentScope 2.0.3 工厂适配基线已实现：真正执行 ReActAgent，映射结果/工具事件及文本流，隔离租户会话键，支持超时取消并关闭每次调用的 Agent。旧策略运行时继续保留。
- AgentScope 治理工具适配器已实现，SDK 调用经过 ToolRegistry 执行网关，在 boundedElastic 中恢复捕获的调用者身份，传递租户、运行及会话上下文；测试覆盖网关调用、身份传播、拒绝与租户不匹配。运行标识已加入 SDK RuntimeContext。复杂事件载荷、SDK 检查点与审批恢复尚未完成；宿主已有 ModelService/ToolRegistry 时自动提供默认 AgentScopeAgentFactory，未配置审批延续时拒绝相关调用，不能作为完整生产运行时验收。
- web 与 completion 已迁入，原 HTTP 接口与流式测试通过；spring-boot-starter 已配置 HTTP 接入依赖。
- server 核心装配、演示身份桥与生产配置检查已迁入。新增独立随机端口启动测试，H2 PostgreSQL 模式严格初始化全部核心 schema，健康检查及模型提供商 HTTP 接口通过。
- provider 聚合已迁入 Qwen、DeepSeek、Volcengine；store 聚合已迁入 pgvector、Elasticsearch、Milvus，构建及已有测试通过。旧 Zhipu SDK 模块本来未参与旧项目构建，继续由 model 的 OpenAI 兼容预设提供 Zhipu 能力。
- 平台渠道 SPI/core/testkit/native、QQBot、企业微信、飞书、钉钉、Email、Webhook、bundle 已迁入；业务插件 example、seedance、video 和 channel-redis、connector-tools、plugin-agent 已迁入。
- server 已接回原有 Qwen/Volcengine、视频插件、插件 Agent、Redis 限流、连接器工具、平台渠道 bundle 与 RAG 检索工具适配，严格数据库初始化启动测试通过。测试关闭 Redis 健康探针以隔离外部基础设施；真实 Redis 行为仍需部署联调。
- JSON Schema validator 统一为 3.0.0；插件和视频任务校验通过 JSON 文本边界使用 Jackson 3 校验器，避免 Boot 4 装配后出现 Jackson 2 方法签名冲突。
- Vue 组件库类型检查、103 项测试及构建通过；demo 构建通过。渠道测试改用按钮可见文字，保留账号保存、会话分页与过期响应隔离断言。前端目录与包名暂保留。
- 可选提供商/存储及平台渠道源码已迁入；真实 PostgreSQL 已启动并通过管理页面读取；外部存储/Redis 专项、文件上传与复杂前端交互仍待验证。
- HTTP 公共响应、错误码、分页、工作流运行请求及 Jackson 2/3 树桥接已迁到 agentstart-api；completion 已移除对 web 模块的依赖。
- 文件上传已拆成 Servlet/WebFlux 适配，共用知识服务；Servlet SSE 按 WebApplicationType 装配。WebFlux 上传解析和 JDBC 在 boundedElastic 执行，通过 Reactor Context 恢复并限定身份，限制缓冲区大小并对超限返回 413/upload_too_large。
- server 真实 HTTP 测试覆盖模型注册、数据集创建、文本文件上传、分块预览、空文件、非法规则及超限文件，使用离线测试模型，不依赖外部 LLM。
- 修复可变 Builder 请求 DTO 的 Jackson 3 反序列化：显式无参构造、禁用全参构造作为 JSON creator，保留字段初始默认值；模型类型同时接受枚举名与协议值。
- WebFlux 同步 SDK 控制器已配置虚拟线程执行器，注册 Reactor Context/ThreadLocal 身份桥接；server 测试验证工作线程名称和可信租户/用户，演示过滤器不再跨异步请求持续持有 ThreadLocal。
- API 公共错误处理器已接入；completion 在不含 web 模块的独立 HTTP 进程中验证 404/500 的统一错误码与 traceId，敏感诊断细节不会直接返回。缺少执行服务时跳过聊天控制器及生成服务。
- server/.env.local 的 PostgreSQL 与 Redis 端口已确认可达，未输出凭证。真实后台及页面加载/主题切换已验证，最终重新打包启动及复杂交互仍待验收。
- RAG 的 KnowledgeSearchTool 及自动配置已迁入独立的 agentstart-rag-tools，检索核心不依赖工具运行时。
- 完整集成回归通过，server HTTP 测试验证 knowledge_search 工具与渠道目录可用。
- MCP 服务端鉴权已迁入 access 的独立 agentstart-mcp-auth-spring-starter，原鉴权与权限测试通过。核对旧 example 当前目录后确认没有 src/test：旧文档“跨模块测试”描述过时，不存在可直接迁移的测试。跨模块验证由 server 的真实 HTTP 集成测试继续补齐，不恢复已删除的 example 模块。
- Boot 4 生产配置检查已更新为当前 EnvironmentPostProcessor 接口及工厂注册项，并通过真实启动入口的拒绝测试。
- 业务观测自动配置与评估已接回：工具、Agent、渠道各自拥有 Micrometer 适配；AgentEvaluationRunner 独立于指标开关装配。缺少指标依赖/关闭指标的装配测试与 HTTP 工具执行后的计数器断言通过。

## 观测适配归属

基础 observability 只保留模型调用的事实、计量装饰器和聚合查询。旧模块中的能力已按以下职责迁移并保留测试：

- `agentops/MicrometerToolExecutionListener` → tools 的 `io.aigoodle.tool.observability`。
- `agentops/MicrometerAgentRunObserver`、`ObservationAgentRunObserver` → agent 的 `io.aigoodle.agent.observability`。
- `eval/*` 与 `AgentEvaluationRunnerTest` → agent 的评估能力。
- `channel/*` 与对应测试 → channel 的观测适配。
- 原 `AgentOpsMetricsTest` 已拆分为工具和 Agent 两部分保留覆盖。

## 验证标准

每一步运行所涉及模块与上游的测试，保留真实行为覆盖；数据库测试使用 H2，在线模型测试以实际可达性记录结果。最终必须验证整个项目和前端，不能用基础模块的通过代替全项目完成。

最终验收需按 server 的 env 配置启动后台和前端 demo，操作页面、接口及颜色主题切换。整个升级与调试完成后保存工作、停止服务，再按用户要求关闭电脑；阶段性完成不代表整体完成。

## 应用级监测统计

- 应用监测页改用 `/apps/{appId}/metrics/llm/total`、`recent`、`trend`；接口先校验当前租户对应用的读取权限，再按租户与应用筛选调用记录。原 `/llmops/*` 全局接口保留。
- `goodle_llm_calls` 新增可空 `app_id`，以及 `(tenant_id, app_id, created_at)` 索引。部署前通过既有数据库初始化/迁移流程执行 observability-schema.sql 中的增量 DDL，再更新后端。历史记录保持空归属，不按推测回填，应用级统计从有应用归属的新调用开始。
- Agent、工作流节点、迭代子流程及持久化恢复传递可信应用归属；共享模型凭证的资源租户不替代实际业务租户。应用设计器试运行携带 appId，后台校验应用写权限后计入对应应用。流式计量在创建调用时捕获归属，并保留到成功、失败或取消时。
- 自定义前端宿主需实现 `fetchAppLlmUsage(appId)`、`fetchAppRecentLlmCalls(appId, limit)`、`fetchAppLlmTrend(appId, range)`。内置 SDK 和 Spring 适配器已接入；缺少应用级接口时显示明确提示，不回退到全局统计。
- 页面提供累计输入/输出/总 Tokens、调用数/失败数/成功率、估算费用/配置币种、平均延迟，以及近 1 小时/24 小时/7 天趋势与最近 10 条调用。费用依赖模型定价配置，未配置定价的调用费用仍为 0。
