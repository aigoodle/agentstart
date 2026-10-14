# vue-agentstart 项目约定

## UI / 组件规范

- **小号按钮必须同步调小字体**：在本项目中使用小号按钮（如 Element Plus 的 `size="small"`、自定义 `.btn--small` 等）时，按钮文字的 `font-size` 也要相应调小一号（例如默认 14px → 小号 12px），保持按钮尺寸与字号一致。
  - 本项目按钮统一走 `AsButton` 组件（`<Button>` / `<a-button>`），`size="small"` 对应 `src/ui/style.css` 中的 `.as-btn--small`（基准字号 `--as-font-size: 14px`，小号应为 `text-xs` = 12px）。改这一处即可全局生效，无需逐个组件处理。
  - **改完库 CSS 必须重新构建**：宿主 demo（`frontend/agentstart-demo`）加载的是构建产物 `dist/style.css`（源码里的 Tailwind `@apply` 未展开，demo 不装 Tailwind）。流程：`pnpm build` → 在 demo 目录执行 `node scripts/gen-force-dark.mjs` 重新生成 `hud-force-dark.css` → 浏览器强刷/重启 dev server。
