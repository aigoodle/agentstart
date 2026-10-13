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
