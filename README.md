# AgentStart

参考 AgentScope 重构的 Java AI 应用平台。已从旧项目迁移并按职责重构，验证范围和运行记录见 [迁移计划](MIGRATION.md)。

- Java 21，Maven 3.9+。
- Maven 坐标：`io.aigoodle:agentstart:0.1.0-SNAPSHOT`。
- Spring Boot 4.1.1、Spring AI 2.0.1；各职责模块已迁入，验证记录见迁移计划。
- 原有 `frontend/` 保留，不参与 Maven 构建。

## 项目结构

根 POM 聚合 5 个职责分组和一级模块 `agentstart-server`；分组 POM 的 packaging 为 `pom`，同时作为子模块的父 POM。
功能模块使用 `agentstart-*` artifactId，可按需引用；聚合与继承不代表运行时依赖。
模型提供商、向量存储、平台渠道与业务插件分别使用独立聚合目录，避免核心模块强制引入全部 SDK。

```text
agentstart/
├── pom.xml
├── agentstart-foundation/                         # 基础设施
│   ├── agentstart-common/
│   ├── agentstart-persistence/
│   ├── agentstart-model/
│   ├── agentstart-observability/
│   └── agentstart-model-provider/                       # Qwen、DeepSeek、Volcengine、OpenAI、Kimi、智谱、自定义
├── agentstart-engine/                         # 执行引擎
│   ├── agentstart-agent/
│   ├── agentstart-tools/
│   ├── agentstart-runtime-agentscope/
│   ├── agentstart-agentscope-sdk/
│   └── agentstart-workflow/
├── agentstart-context/                         # 知识与上下文
│   ├── agentstart-knowledge/
│   ├── agentstart-rag/
│   ├── agentstart-memory/
│   ├── agentstart-skill/
│   ├── agentstart-store/                          # pgvector、Elasticsearch、Milvus
│   └── agentstart-rag-tools/                      # 检索工具适配
├── agentstart-integrations/                         # 业务集成
│   ├── agentstart-connector/
│   ├── agentstart-plugin/
│   ├── agentstart-channel/
│   ├── agentstart-trigger/
│   ├── agentstart-channel-redis/
│   ├── agentstart-connector-tools/
│   ├── agentstart-plugin-agent/
│   ├── agentstart-channels/                       # 轻量 SPI、原生运行时与平台适配
│   └── agentstart-plugins/                        # 示例、Seedance、统一视频插件
├── agentstart-access/                         # 接入层
│   ├── agentstart-api/                        # 公共 HTTP 契约与 JSON 树桥接
│   ├── agentstart-web/
│   ├── agentstart-completion/
│   ├── agentstart-spring-boot-starter/
│   └── agentstart-mcp-auth-spring-starter/         # MCP 服务端鉴权，可选
├── agentstart-server/                         # 独立服务启动模块
└── frontend/                       # 独立前端，不参与 Maven 构建
```

Java 版本、编码和构建插件集中在根 POM，分组内功能模块通过分组父 POM 继承，`agentstart-server` 直接继承根 POM。
分组内部也不自动互相依赖，依赖关系在实现时按实际需要声明。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| `agentstart-common` | 共享值对象、错误协议与基础工具。 |
| `agentstart-persistence` | 宿主租户身份与持久化基础设施。 |
| `agentstart-model` | 模型目录、凭证、模型实例与调用计量接入。 |
| `agentstart-tools` | 工具契约、注册、权限与执行网关。 |
| `agentstart-agent` | 应用定义、发布版本与框架无关的 AgentRuntime 契约。 |
| `agentstart-runtime-agentscope` | AgentScope 执行适配、事件映射与 Agent 内部恢复。 |
| `agentstart-agentscope-sdk` | SDK 分发包；重定位 Jackson 2 Schema 库，避免与 Spring AI 冲突。 |
| `agentstart-knowledge` | 知识资产、文档解析、分块、索引与入库任务。 |
| `agentstart-rag` | 查询规划、召回编排、融合、重排与证据处理。 |
| `agentstart-workflow` | 业务 DAG、节点执行、检查点、租约与流程恢复。 |
| `agentstart-memory` | 长期记忆服务及存储适配；运行内上下文由 Runtime 管理。 |
| `agentstart-skill` | 技能目录、发布与渐进加载。 |
| `agentstart-connector` | 外部系统连接、凭证与受治理执行。 |
| `agentstart-plugin` | 业务插件契约、安装与运行适配。 |
| `agentstart-channel` | 消息渠道、可信身份、路由与可靠投递。 |
| `agentstart-trigger` | 定时、Webhook 与事件触发。 |
| `agentstart-observability` | 模型调用计量、费用聚合与趋势查询；业务观测适配归属对应上层模块。 |
| `agentstart-web` | 管理 API 与业务 API。 |
| `agentstart-completion` | OpenAI / Dify 兼容对话与流式 API。 |
| `agentstart-spring-boot-starter` | Spring Boot 自动配置与按需装配入口。 |
| `agentstart-server` | 独立服务启动模块。 |

## 边界

工作流负责流程调度与恢复；AgentScope Runtime 负责 Agent 内部执行与恢复。
RAG 管线统一供工作流、对话和 Agent 检索工具使用；知识模块管理入库与索引。
工具执行统一经过宿主权限网关；租户和用户身份由宿主提供。
框架无关模块不直接依赖 AgentScope，适配集中在 `agentstart-runtime-agentscope`。
服务模块已具备独立启动类与核心 HTTP 装配；默认连接 PostgreSQL。

目前已迁入 common、persistence、model、observability 的模型计量、tools、memory 与 skill。
RAG、knowledge、NATIVE Agent 运行时以及 connector、channel、plugin、workflow、trigger 已迁入并通过项目回归。
HTTP 接入与 server 核心装配已迁入并通过启动测试，Vue 组件库与 demo 构建通过。
可选提供商、存储、平台渠道和业务插件已迁入；server 已接回原有默认集成。
业务观测与评估分别归属工具、Agent、渠道模块；MCP 服务端鉴权使用独立 starter。
AgentScope 2.0.3 适配器可执行真实 ReActAgent；加入适配模块且已有模型和工具目录时自动提供默认工厂。
应用通过 `runtimeType=AGENTSCOPE` 选择该后端，`runtimeRef` 可指定宿主自定义工厂 Bean；未指定时使用默认或唯一工厂。适配器关闭每次调用创建的 Agent。
AgentScope 默认模型与治理工具工厂已提供；模型/工具调用预算已接入；运行状态与步骤已复用平台存储。
server 默认设置 `spring-agent.agentscope.state-store=jdbc`，SDK 会话状态使用带版本校验的数据库存储。
宿主可注入支持版本写入的 `AgentStateStore`；未配置 JDBC 的 SDK 宿主使用内存状态。审批支持持久化和重启恢复；已发布子应用委派沿用租户身份与治理网关，完整迁移状态见 [迁移计划](MIGRATION.md)。

独立 server 使用 WebFlux 文件上传适配；Servlet 宿主保留 MultipartFile 接口。
上传缓冲区上限由 `spring-agent.web.max-upload-size` 配置，默认 `100MB`；超限返回 HTTP 413。
WebFlux 下的同步 SDK 控制器在虚拟线程执行；宿主将可信身份放入 Reactor Context，接入层按调用范围恢复 ThreadLocal。
completion 可独立于 web 使用，共享 `agentstart-api` 的错误响应协议。

## 模型供应商配置

可选供应商模块统一位于 `agentstart-foundation/agentstart-model-provider`：
`agentstart-model-qwen`、`agentstart-model-deepseek`、`agentstart-model-volcengine`、
`agentstart-model-openai`、`agentstart-model-kimi`、`agentstart-model-zhipu`、`agentstart-model-openai-compatible`。
共享 OpenAI 兼容传输实现，各模块只注册供应商预置，避免重复客户端代码；Kimi 保留 `moonshot` 数据标识以兼容已有凭证。
前端模块与包子路径为 `model-provider-hub` / `vue-agent-start/model-provider-hub`。

启动不再同步供应商或预置模型数据库目录。保存供应商配置时只同步该供应商；
管理员可点击“同步预置目录”，或调用 `POST /agent-start/model-providers/catalog/sync`。
远程目录仅在配置时或手动拉取时访问；运行时客户端仍按实际调用惰性创建。
供应商列表使用请求内的租户快照，模型、凭证、启用设置各读取一次；不使用跨请求的凭证缓存。
业务与 Mapper 日志默认 INFO；排查模型 SQL 可临时设置 `logging.level.io.aigoodle.model.mapper=DEBUG`。

自定义卡片使用标准 OpenAI Chat Completions / Embeddings 接口，可配置 API Key 和必填 Base URL。
预置目录参考 [Kimi官方模型列表](https://platform.kimi.com/docs/models) 与 [智谱OpenAI兼容文档](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction)，无需重启即可手动同步新增预置。

## 本地运行

在仓库根目录构建并启动后台，脚本读取 `agentstart-server/.env.local`，支持引号和注释；不会执行 env 中的 shell 命令，也不会打印凭证。数据库和 Redis 地址使用该文件配置。

```bash
mvn -pl agentstart-server -am package -DskipTests
python3 scripts/run-server.py
```

另开终端启动真实后端 demo；Vite 默认使用 5173，`VITE_PORT` 可覆盖（当前本地配置为 5178）。端口被占用时以终端输出的地址为准。

```bash
cd frontend/agentstart-demo
MOCK=false pnpm dev --host 127.0.0.1
```

应用编辑页的对话预览使用独立 React 聊天前端，还需启动：

```bash
cd frontend/react-antdx-chat
pnpm install --frozen-lockfile
pnpm dev --host 127.0.0.1
```

聊天前端默认端口 5073，由 demo 的 `/chat/` 代理嵌入；两个前端均支持 `VITE_AGENT_API_TARGET` 配置后台地址。
组件库样式来自构建产物，首次启动 demo 前在 `frontend/vue-agentstart` 执行 `pnpm install --frozen-lockfile` 和 `pnpm build`。

后台默认端口 18090，前端通过 `/api/agent-start` 代理访问。`VITE_AGENT_API_TARGET` 可设置其他后台地址。使用 Ctrl+C 停止各服务。

## 验证

```bash
mvn -B -T1 validate
mvn -B -T1 test
mvn -B -T1 spotless:check

# 修改 Java 代码后统一格式化
mvn -B -T1 spotless:apply

# 构建整个执行引擎分组
mvn -B -T1 -f agentstart-engine/pom.xml test

# 按 artifactId 构建单个功能模块及其依赖
mvn -B -T1 -pl :agentstart-runtime-agentscope -am test
```

功能模块按需包含 Java、资源及测试目录；聚合模块只管理子模块与继承。

## IDEA 导入

打开仓库根目录的 `pom.xml` 并作为 Maven 项目加载，Maven 工具窗口执行 Reload All Maven Projects。
移动模块后，如有旧路径的单独关联，先解除旧关联，再关联根 POM。
各聚合模块的目录名、artifactId 和 name 保持一致；知识与上下文聚合为
`agentstart-context`，避免与功能模块 `agentstart-knowledge` 重名。
项目 SDK 与 Maven 导入器使用 JDK 21。
