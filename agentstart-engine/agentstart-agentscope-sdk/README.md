# AgentScope SDK 分发

该模块保留 SDK 的公开 `io.agentscope` 类型，将 SDK 使用的 Victools 4 和 JSON Schema Validator 2 重定位到 `io.aigoodle.runtime.agentscope.internal`。它们继续使用 Jackson 2；Spring AI 的 Victools 5 和 Validator 3 使用原包名。

`agentscope-core` 以 provided 声明，用于解析版本与依赖，不传递原始 SDK jar。Shade 将 SDK 和两个旧 Schema 库写入分发 jar，其他运行依赖按普通 Maven 依赖传递。

生成工作在 generate-resources 阶段执行，随后解包到构建输出目录，使 reactor 的 `mvn test` 和 `mvn package` 都能使用同一套重定位类。每次构建先清理本模块的生成目录，避免旧 SDK 类残留。

修改 SDK 或 Schema 库版本后，应验证注解工具、平台治理工具、数据库状态恢复以及 server 可执行 jar 中的依赖，确认没有再次引入原始 SDK 包。
