# AgentStart

参考 AgentScope 重构的 Java AI 应用平台。当前仅创建 Maven 多模块骨架，无业务代码。

- Java 21，Maven 3.9+。
- Maven 坐标：`io.github.aigoodle:agentstart:0.1.0-SNAPSHOT`。
- 各模块暂不声明依赖；Spring Boot、模型及 AgentScope 版本将在接入时确定。
- 原有 `frontend/` 保留，不参与 Maven 构建。

## 项目结构

根 POM 聚合 6 个职责分组；分组 POM 的 packaging 为 `pom`，同时作为子模块的父 POM。
21 个功能模块保留原 artifactId，仍可按需引用；聚合与继承不代表运行时依赖。

```text
agentstart/
├── pom.xml
├── agentstart-foundation/                         # 基础设施
│   ├── agentstart-common/
│   ├── agentstart-persistence/
│   ├── agentstart-model/
│   └── agentstart-observability/
├── agentstart-engine/                         # 执行引擎
│   ├── agentstart-agent/
│   ├── agentstart-tools/
│   ├── agentstart-runtime-agentscope/
│   └── agentstart-workflow/
├── agentstart-context/                         # 知识与上下文
│   ├── agentstart-knowledge/
│   ├── agentstart-rag/
│   ├── agentstart-memory/
│   └── agentstart-skill/
├── agentstart-integrations/                         # 业务集成
│   ├── agentstart-connector/
│   ├── agentstart-plugin/
│   ├── agentstart-channel/
│   └── agentstart-trigger/
├── agentstart-access/                         # 接入层
│   ├── agentstart-web/
│   ├── agentstart-completion/
│   └── agentstart-spring-boot-starter/
├── agentstart-applications/                         # 应用
│   ├── agentstart-server/
│   └── agentstart-example/
└── frontend/                       # 独立前端，不参与 Maven 构建
```

Java 版本、编码和构建插件集中在根 POM，功能模块通过分组父 POM 继承。
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
| `agentstart-knowledge` | 知识资产、文档解析、分块、索引与入库任务。 |
| `agentstart-rag` | 查询规划、召回编排、融合、重排与证据处理。 |
| `agentstart-workflow` | 业务 DAG、节点执行、检查点、租约与流程恢复。 |
| `agentstart-memory` | 长期记忆服务及存储适配；运行内上下文由 Runtime 管理。 |
| `agentstart-skill` | 技能目录、发布与渐进加载。 |
| `agentstart-connector` | 外部系统连接、凭证与受治理执行。 |
| `agentstart-plugin` | 业务插件契约、安装与运行适配。 |
| `agentstart-channel` | 消息渠道、可信身份、路由与可靠投递。 |
| `agentstart-trigger` | 定时、Webhook 与事件触发。 |
| `agentstart-observability` | 调用计量、运行审计与观测适配。 |
| `agentstart-web` | 管理 API 与业务 API。 |
| `agentstart-completion` | OpenAI / Dify 兼容对话与流式 API。 |
| `agentstart-spring-boot-starter` | Spring Boot 自动配置与按需装配入口。 |
| `agentstart-server` | 独立服务启动模块。 |
| `agentstart-example` | 嵌入式使用示例与跨模块验证。 |

## 边界

工作流负责流程调度与恢复；AgentScope Runtime 负责 Agent 内部执行与恢复。
RAG 管线统一供工作流、对话和 Agent 检索工具使用；知识模块管理入库与索引。
工具执行统一经过宿主权限网关；租户和用户身份由宿主提供。
框架无关模块不直接依赖 AgentScope，适配集中在 `agentstart-runtime-agentscope`。
服务和示例模块目前也是空 JAR，不包含启动类。

## 验证

```bash
mvn -B -T1 validate
mvn -B -T1 test

# 构建整个执行引擎分组
mvn -B -T1 -f agentstart-engine/pom.xml test

# 按 artifactId 构建单个功能模块及其依赖
mvn -B -T1 -pl :agentstart-runtime-agentscope -am test
```

每个模块包含 `src/main/java`、`src/main/resources`、`src/test/java`、`src/test/resources`。
空目录使用 `.gitkeep` 保留。

## IDEA 导入

打开仓库根目录的 `pom.xml` 并作为 Maven 项目加载，Maven 工具窗口执行 Reload All Maven Projects。
移动模块后，如有旧路径的单独关联，先解除旧关联，再关联根 POM。
各聚合模块的目录名、artifactId 和 name 保持一致；知识与上下文聚合为
`agentstart-context`，避免与功能模块 `agentstart-knowledge` 重名。
项目 SDK 与 Maven 导入器使用 JDK 21。
