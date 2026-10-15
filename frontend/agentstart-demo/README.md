# Agent Start Demo

项目默认通过 Vite 代理连接真实的 `spring-agent-start` 后端：

```text
浏览器 /api/agent-start/*
  -> Vite 去掉 /api
  -> http://localhost:18090/agent-start/*
```

启动前请确保后端在 `18090` 端口运行，然后执行：

```bash
pnpm dev
```

后端地址可在 `.env.development` 的 `VITE_AGENT_API_TARGET` 中修改。
若需临时恢复离线 mock 模式，可使用 `MOCK=true pnpm dev`。


## UI 与主题

演示项目和组件库共用 shadcn-vue 组件与语义主题令牌。
`/ui` 是基础组件验证页，覆盖表单、数字选择、tags、JSON Schema 表单、按钮、Modal、Drawer 和嵌套弹窗。
右上角偏好设置支持明亮、暗黑、跟随系统，以及普通／科技视觉风格和自定义主题色。

Vite 使用 Tailwind v4 插件直接编译 `vue-agentstart/src/style.css` 和演示项目自身的工具类。
修改组件库样式会直接 HMR，不再依赖旧的 `dist/style.css`。
普通接入方只需加载发布包 `vue-agent-start/style.css`，不需要 Tailwind 配置。

验证命令：组件库执行 `pnpm run typecheck && pnpm run test && pnpm run build`；演示项目执行 `pnpm run build`。
