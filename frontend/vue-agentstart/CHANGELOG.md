# vue-agent-start

## 0.2.0

### Major internal rework — distributable build + unified client SDK

- **Library build**: `vite build` (library mode) now produces a publishable
  `dist/` — `index.{js,cjs}` plus per-module entries (`client`, `provider-hub`,
  `knowledge-hub`, `agent-studio`, `agent-flow`), TypeScript declarations via
  `vite-plugin-dts`, sourcemaps, and a single collected `style.css`. The
  package no longer points `main`/`module`/`exports` at `src/index.ts`;
  consumers no longer need to compile SFC/TS sources themselves.
- **`exports` subpaths**: `.`, `./client`, `./provider-hub`,
  `./knowledge-hub`, `./agent-studio`, `./agent-flow`, `./style.css`.
  `files` is whitelisted to `dist` (+ README/CHANGELOG).
- **Declaration emit fix**: `vite-plugin-dts` pins `@vue/language-core` 2.2.0,
  whose codegen always emits `__typeRefs` for component template refs; the
  resulting cross-module type chain trips TS4082 ("private name 'Props'") and
  silently drops the affected component's `.d.ts`. A pnpm `overrides` entry
  aligns it on 2.2.12 (same version `vue-tsc` uses), where `__typeRefs` is
  behind the opt-in `inferComponentDollarRefs` flag.
- **Unified SDK**: new framework-neutral `createAgentStartClient`
  (`client.models / providers / agents / workflows / knowledge / runs`) with
  envelope unwrapping, auth/tenant header injection, `onUnauthorized` and
  timeouts. All Vue adapters (`useProviderHub`, `useKnowledge`,
  `useAgentStudio`, `createSpringAgentStartAdapter`,
  `createAgentStudioSpringBackend`, `createAgentRunClient`, `AgentAppsPage`)
  now delegate to it; no component concatenates `/agent-start` paths anymore.
  The Vue bindings (`provideAgentStartClient` / `useAgentStartClient` /
  `installAgentStartClient`) live in `vue-agent-start/client`.
- **Wire fix**: `createAgentStudioSpringBackend` now applies the
  `/agent-start` controller namespace like every other adapter (it previously
  hit `/api/agents` instead of `/api/agent-start/agents`). Hosts that
  compensated for the old behaviour (e.g. extra proxy rewrites) should remove
  the compensation; `namespace: ''` is available as an escape hatch.
- **SSR hygiene**: module-scope DOM access removed from the adapter/client
  layers (`onCopyApi` clipboard default is guarded; storage-file base is a
  configurable string, not a DOM read). Component-level audit: `setup()` and
  `immediate` watchers no longer touch `window`/`document` unguarded
  (`ChatIframePanel` message listener moved into `onMounted`;
  `RetrievalConfigPopover` / `PromptEditorTagPanel` immediate-watch DOM access
  guarded; `window.location` computeds in `AgentAppsPage` / `AppDesignDrawer`
  return safe fallbacks on the server).
- **Style isolation**: every unscoped `<style>` block is now namespaced —
  `OutputItemCard`'s h()-rendered tree classes moved from generic `output-*`
  to `wf-out-*`; knowledge-hub runs on `--kh-*` design tokens, agent-flow on
  `--wf-*`; dead unprefixed `--table-header-bg` dropped.
- **Tooling**: `typecheck` (vue-tsc), `test` (vitest + happy-dom),
  changesets for semver discipline; `vue-router` declared as an optional peer
  (only `AgentAppsPage` uses it, defensively).
