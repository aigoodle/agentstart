---
"vue-agent-start": minor
---

BREAKING (pre-1.0): channel 渠道域彻底迁移,不再保留 connector 兼容别名。

- `client.channels` 成为渠道 API 的正主实现(catalog、accounts、connections、events、conversations、bindings、identities、audits、dead-letters);`client.connectors` 只保留业务 Connector 目录域的 12 个方法,其上的渠道方法已删除。
- 渠道类型(`Channel*`、`TenantAgentBinding`、`EmployeeAgentBinding`、`RobotUser`、`MyRobot`)正主声明移至 `channel-hub/types`;`connector-hub/types` 不再导出渠道类型。
- `connector-hub` 入口删除 `ChannelConnectionPanel` / `NativeChannelSetupGuide` / `MyRobotsPanel` / `RobotFormModal` 兼容导出,请从 `vue-agent-start/channel-hub`(或根入口)导入。
- 共享动态表单 `JsonSchemaForm` 移至 `src/ui/components/`;`vue-agent-start/connector-hub` 入口继续导出它,公共 API 不变。
- 根入口对 channel-hub 改为全量 `export *`(此前仅导出 `ChannelHubApp`),渠道组件与类型可从包根直接导入。

对应后端重构:spring-agent-start 将消息接入渠道从 `agent-start-connector(-connectors)` 拆分为 `agent-start-channel` / `agent-start-channels/`(REST 路径 `/channels`、`/channel-*` 不变)。
