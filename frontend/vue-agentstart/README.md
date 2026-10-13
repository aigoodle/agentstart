# vue-agent-start

面向 `spring-agent-start` 的 Vue 3 组件库，提供模型供应商、知识库、智能体应用与对话页面。

## 安装

```bash
pnpm add vue-agent-start
```

宿主项目只需要提供 Vue、Pinia、Vue Router 和图标包等轻量 peer dependencies，具体版本范围见 `package.json`。组件库不再依赖或捆绑 Ant Design Vue。

组件库内置了一套接近 Ant Design 交互密度的自有 UI 基座，包括按钮、输入框、选择器、开关、卡片、表单、Modal、Drawer、表格、提示消息等。内部样式由 Tailwind CSS v4 在发布阶段编译，使用方不需要安装或配置 Tailwind。安装 `AgentStartPlugin` 时会自动注册；也可以单独安装：

```ts
import { AgentStartUi } from 'vue-agent-start';
import 'vue-agent-start/style.css';

app.use(AgentStartUi);
```

主题通过 `--as-*` CSS 变量覆盖。构建入口不包含 Tailwind Preflight，因此不会重置宿主项目的原生标签，也不会修改 Element Plus、Naive UI 或其他组件库主题。

组件库同时内置明亮/暗黑、普通/科技感两组外观开关，Drawer、Modal 和通用 `.hud-panel` 的 HUD 外框及导轨也包含在发布样式中，不依赖 demo 项目。完整参数、运行时切换及 CSS 变量说明见 [APPEARANCE.md](./APPEARANCE.md)。

## 推荐：全局配置

在创建 Vue 应用时安装 `AgentStartPlugin`。`apiBase` 和 `headers` 会被所有顶层组件继承：

```ts
import { createApp } from 'vue';
import { AgentStartPlugin } from 'vue-agent-start';

import App from './App.vue';
import { useAccessStore } from './stores/access';

const app = createApp(App);

// 需要先安装 Pinia，确保 store 可以使用。
app.use(pinia);

const accessStore = useAccessStore();

app.use(AgentStartPlugin, {
  apiBase: '/api',
  appearance: {
    theme: 'dark',
    visual: 'tech',
    primaryColor: '#00ccff',
  },
  headers: () => {
    const token = accessStore.accessToken;
    return token
      ? { Authorization: `Bearer ${token}` }
      : {};
  },
});

app.mount('#app');
```

推荐给 `headers` 传函数。函数会在每次 HTTP 请求前执行，登录、刷新 Token 或退出后不需要重新挂载组件。

完成全局配置后，页面无需重复传递请求参数：

```vue
<script setup lang="ts">
import {
  AgentAppsPage,
  AgentChatPage,
  KnowledgeApp,
  ProviderApp,
} from 'vue-agent-start';
</script>

<template>
  <ProviderApp />
  <KnowledgeApp />
  <AgentAppsPage />
  <AgentChatPage :agent-id="agentId" />
</template>
```

例如 `ProviderApp` 发起的请求：

```text
GET /api/agent-start/models/grouped-by-type
Authorization: Bearer <当前 token>
```

## 局部配置与覆盖

组件仍支持独立使用。`headers` 可以是静态对象，也可以是同步或异步函数：

```vue
<script setup lang="ts">
import { ProviderApp } from 'vue-agent-start';

const headers = async () => ({
  Authorization: `Bearer ${await getAccessToken()}`,
});
</script>

<template>
  <ProviderApp api-base="/gateway" :headers="headers" />
</template>
```

配置优先级如下：

1. 组件的 `api-base` 覆盖全局 `apiBase`。
2. 全局 Header 与组件 Header 合并。
3. Header 同名时，组件 Header 覆盖全局 Header。
4. 未配置 `apiBase` 时默认使用 `/api`。

租户应由宿主后端根据登录凭证解析，不应把浏览器传入的 Header 当作授权依据。只有旧网关需要
租户提示且会使用认证结果覆盖它时，才显式启用兼容模式：

```ts
createAgentStartClient({
  getAccessToken: () => getToken(),
  getTenant: () => currentTenantId.value,
  sendTenantHeader: true,
});
```

## 轻量 aigoodle 入口

只使用模型、知识库、智能体列表和对话页面时，可以使用轻量入口，避免引入完整工作流设计器入口：

```ts
import {
  AgentStartPlugin,
  ProviderApp,
  KnowledgeApp,
  AgentAppsPage,
  AgentChatPage,
} from 'vue-agent-start/aigoodle';
```

全局配置方法和覆盖规则与根入口完全相同。

## 不使用 Vue 插件

框架无关或需要自行控制请求时，可以直接创建 Client：

```ts
import { createAgentStartClient } from 'vue-agent-start/client';

const client = createAgentStartClient({
  baseUrl: '/api',
  headers: () => ({
    Authorization: `Bearer ${getToken()}`,
  }),
});

const models = await client.models.list();
```

## 可持久化 Agent Run 检查器

`AgentRunTimeline` 同时适用于原生 Runtime 和 Spring AI Alibaba 等可选扩展。它消费统一的
`/agent-runs` 协议，展示有序事件、运行耗时以及不可变的请求、Agent 定义和响应快照，并提供审批、
取消和刷新能力，但不接管宿主路由或认证。

```vue
<AgentRunTimeline
  ref="runInspector"
  :client="agentStart.runs"
  :run-id="runId"
  :event-types="['TOOL_STARTED', 'TOOL_SUCCEEDED', 'RUN_FAILED']"
  @event-selected="openEnterpriseAudit"
>
  <template #approval="{ approval, approve, deny, acting }">
    <EnterpriseApprovalCard
      :approval="approval"
      :disabled="acting"
      @approve="approve"
      @deny="deny"
    />
  </template>
</AgentRunTimeline>
```

组件提供 `header`、`approval`、`event`、`empty`、`actions` slots，宿主可以接入自己的 RBAC、
审批流程、审计展示和设计系统。模板 ref 暴露 `refresh()`、`cancel()`、`snapshot` 和 `events`。
组件不会要求浏览器选择可信租户，租户身份仍由宿主认证上下文在服务端确定。
