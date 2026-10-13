# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Vite + React 19 + TypeScript single-page app. `src/App.tsx` is a full-screen AI chat UI modeled on the official `@ant-design/x` "ultramodern" playground. It uses three packages in concert:

- `@ant-design/x` v2 — UI primitives: `XProvider`, `Bubble.List`, `Conversations`, `Sender`, `Sender.Switch`, `Actions`.
- `@ant-design/x-sdk` v2 — state hooks: `useXChat`, `useXConversations`, `OpenAIChatProvider`, `XRequest`.
- `@ant-design/x-markdown` v2 — `XMarkdown` with streaming animation, rendered inside the assistant bubble's `contentRender`.

It talks to an Agent service through `/chatapi/agent-start`, using the host JWT and appCode/appId.

## 宿主集成（使用 iframe 气泡嵌入）

本应用可被任意宿主后台以 `<iframe src="/chat/">` 形式嵌入右下角悬浮气泡。开发环境可将 `/chat` 代理到本项目的 5073 端口；SDK 也支持跨域接入。

- **通讯桥**：`src/lib/host-bridge.ts`（逻辑层）+ `src/lib/useHostBridge.ts`（React hooks）。`src/main.tsx` 启动时调用 `initHostBridge()` 发送 `chat:ready` 并接收宿主上下文；当前用 `useHostContext()` 的 `theme` 跟随宿主深/浅色（外层 `ConfigProvider`）。
- **postMessage 协议**（信封 `{ channel:'boot-school-chat', type, payload }`，收消息用 `event.source === window.parent` 校验）：
  - 子→父：`chat:ready`、`chat:close`（请求关闭气泡，调 `requestClose()`）、`chat:dock`（侧边停靠/还原，调 `requestDock(next)`）、`chat:event`（业务通知，调 `notifyHost(name,data)`）。
  - 父→子：`host:context`（`{token,user,theme,locale}`）、`host:open`/`host:close`（气泡开合，`useHostOpen()`）、`host:dock`（停靠回执，`useHostDock()`）、`host:command`（通用指令，`useHostCommand()`）。
- **侧边停靠**：`Copilot.tsx` 头部的停靠按钮(`LayoutOutlined`,仅 `isEmbedded` 显示)调 `requestDock(!docked)`,宿主据此压缩主应用并把本面板固定到右侧满高;按钮高亮由 `useHostDock()` 跟随宿主回执。
- 宿主侧需要实现对应的接入逻辑，SDK 实现在本项目 `public/chat-frame.js`。修改协议时需同步更新宿主应用。
- 独立打开 `/chat/`（非 iframe）时 `isEmbedded=false`，通讯能力自动退化为空操作，不影响本地开发。

This React project is independent from the host application and does not share its build, lint, or routing configuration.

## Commands

Package manager is **pnpm** (a `pnpm-lock.yaml` is committed — do not introduce `npm`/`yarn` lockfiles).

| Task | Command |
| --- | --- |
| Install deps | `pnpm install` |
| Dev server (Vite HMR) | `pnpm dev` |
| Type-check + production build | `pnpm build` (runs `tsc -b && vite build`) |
| Lint | `pnpm lint` |
| Preview built bundle | `pnpm preview` |

There is no test runner configured; do not invent `pnpm test` commands.

## Build / TS setup notes

- TypeScript uses a **project-references** layout: root `tsconfig.json` references `tsconfig.app.json` (for `src/`) and `tsconfig.node.json` (for Vite config). `pnpm build` runs `tsc -b`, so type errors anywhere in those projects block the build.
- `tsconfig.app.json` enables several strict-ish flags worth knowing before writing code:
  - `verbatimModuleSyntax: true` — `import type` is required for type-only imports.
  - `erasableSyntaxOnly: true` — no `enum`, no namespace-style code; use `const` objects / unions instead.
  - `noUnusedLocals` / `noUnusedParameters` — unused symbols fail the build, not just lint.
  - `moduleResolution: "bundler"` with `allowImportingTsExtensions` — relative imports may include `.ts`/`.tsx` (see `main.tsx`'s `./App.tsx`).
- ESLint config (`eslint.config.js`) is flat-config, applies to `**/*.{ts,tsx}`, and extends `react-hooks` + `react-refresh/vite`. Keep components compatible with React Refresh (one component per file as default export, no mixed exports of components and non-components).

## Repository-wide conventions (from user memory)

- **Styling: Tailwind only.** Do not add custom CSS / Less / scoped styles. Tailwind v4 is wired via `@tailwindcss/vite`; `src/index.css` contains only `@import "tailwindcss";`. Tailwind v4's preflight already zeros body margin, so use `h-screen w-screen` on the root layout div rather than touching `html { height: ... }`.
- **API URL prefix `/edu`** for any backend calls into the school server; backend `@RequestMapping` does **not** include `/edu` (the gateway strips it). (Not currently used in this project but applies if it ever calls the school backend.)
- Use `classInfo` (never bare `class`) for class-entity routes, components, and DTOs — `class` is a reserved word and the rest of the stack is standardized on `ClassInfo`.

## Architecture

Entry `src/main.tsx` → `<App />` under `<StrictMode>` and tailwind via `src/index.css`. The single-file `App.tsx` is the whole UI; chat state is owned by the `useXChat` / `useXConversations` hooks from `@ant-design/x-sdk`.

### Agent service integration

- `src/lib/useChat.ts` manages chat state; `src/lib/provider.ts` implements the Agent API adapter.
- SDK iframe URLs include `hostBridge=1`. Those frames wait for `host:context`; console debug frames and standalone pages retain their local session mode.
- Host authentication and route/business data arrive through the bridge; every request refreshes the JWT header. No model API key is required in the host integration.
- `public/chat-frame.js` exposes the backward-compatible `BootSchoolChat` global. Host applications use `aiAssistant.setData()` and `onAction()` for page integration.
- Production hosting must provide `/chat/` static assets and `/chatapi` reverse proxy to the Agent service.
### `@ant-design/x` v2 + `x-sdk` v2 notes

- **The two packages split**: `@ant-design/x` is component-only; the hooks (`useXChat`, `useXConversations`) and request infra (`XRequest`, `OpenAIChatProvider`, `DeepSeekChatProvider`, `AbstractChatProvider`) live in `@ant-design/x-sdk`. Older v1 code that imported `useXAgent` / `XRequest` from `@ant-design/x` directly will not resolve.
- `Bubble.List` `role` keys: `assistant` (placement: start) and `user` (placement: end), matching what `OpenAIChatProvider` emits. Customize per-role via the `role` prop on the list — including `contentRender` to swap in `XMarkdown` for streaming markdown.
- Streaming bubble class: `.ant-bubble-content-updating` is added while a message status is `updating`. The sample's "rainbow underline" effect is reproduced here via Tailwind arbitrary-variant utilities in `App.tsx` (e.g. `[&_.ant-bubble-content-updating]:bg-[linear-gradient(...)]`) — keep it on a parent of `Bubble.List`.
- `XMarkdown` streaming mode: pass `streaming={{ hasNextChunk: status === 'updating' || 'loading', enableAnimation: true }}` and `paragraphTag="div"` (avoids `<p>` validation errors when assistant content contains block-level children). The light theme stylesheet is imported once in `App.tsx` via `import '@ant-design/x-markdown/themes/light.css';` and applied as `className="x-markdown-light"`.
- TS gotchas under `tsconfig.app.json` (`verbatimModuleSyntax`, `erasableSyntaxOnly`):
  - `import type` for `BubbleListProps`, `SenderProps`, `BubbleListRef`, `DefaultMessageInfo`, `XModelMessage`, `SSEFields`, `XModelParams`, `XModelResponse`.
  - `@ant-design/x` exports `MessageStatus` as an enum and `@ant-design/x-sdk` exports `StreamCacheTokenType` as an enum — use the string-literal unions when typing local state instead of importing the enum.
