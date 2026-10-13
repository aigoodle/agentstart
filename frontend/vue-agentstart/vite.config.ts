/**
 * Vite library-mode build for vue-agent-start.
 *
 * Entries — one per public subpath in package.json `exports`:
 *
 *   index          → dist/index.{js,cjs}          (union surface, eager CSS import)
 *   client         → dist/client/index.{js,cjs}   (framework-neutral SDK only)
 *   provider-hub   → dist/provider-hub/index.{js,cjs}
 *   knowledge-hub  → dist/knowledge-hub/index.{js,cjs}
 *   agent-studio   → dist/agent-studio/index.{js,cjs}
 *   agent-flow     → dist/agent-flow/index.{js,cjs}
 *
 * Shared internal modules (the client core, composables, …) are code-split
 * into `dist/chunks/*` and reused by every entry, so importing both the root
 * and a subpath entry never duplicates runtime state (e.g. the lazy shared
 * client behind setProviderHubApiBase).
 *
 * CSS: `cssCodeSplit: false` collects every style — module CSS, the
 * knowledge-hub tokens and the @vue-flow stylesheets imported by
 * FlowDesigner — into a single `dist/style.css`. Only the root entry imports
 * CSS in source, and subpath consumers are told to import
 * `vue-agent-start/style.css` explicitly, so styles are never duplicated.
 *
 * All runtime peers (vue, vue-router, pinia, icons,
 * @vue-flow/*, vue-draggable-next) stay external — the host supplies them so
 * there is exactly one Vue/pinia instance per app (see .npmrc).
 */
import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';
import dts from 'vite-plugin-dts';

/** Packages the host must provide (peerDependencies + runtime deps). */
const EXTERNALS = [
  'vue',
  'vue-router',
  'pinia',
  '@ant-design/icons-vue',
  'vue-draggable-next',
  '@vue-flow/core',
  '@vue-flow/background',
  '@vue-flow/controls',
  '@vue-flow/minimap',
  '@vue-flow/node-resizer',
];

function isExternal(id: string): boolean {
  return EXTERNALS.some((pkg) => id === pkg || id.startsWith(`${pkg}/`));
}

export default defineConfig({
  plugins: [
    tailwindcss(),
    vue(),
    dts({
      tsconfigPath: './tsconfig.json',
      entryRoot: 'src',
      insertTypesEntry: true,
      // Tests are excluded from the published type surface.
      exclude: ['**/*.test.ts', '**/*.test.tsx', '**/__tests__/**'],
    }),
  ],
  resolve: {
    alias: {
      // Legacy agent-flow internal alias (predates the monorepo merge).
      '@': fileURLToPath(new URL('./src/agent-flow', import.meta.url)),
    },
  },
  build: {
    target: 'es2020',
    sourcemap: true,
    minify: false,
    cssCodeSplit: false,
    lib: {
      entry: {
        index: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
        aigoodle: fileURLToPath(new URL('./src/aigoodle.ts', import.meta.url)),
        'client/index': fileURLToPath(new URL('./src/client/index.ts', import.meta.url)),
        'provider-hub/index': fileURLToPath(new URL('./src/provider-hub/index.ts', import.meta.url)),
        'knowledge-hub/index': fileURLToPath(new URL('./src/knowledge-hub/index.ts', import.meta.url)),
        'agent-studio/index': fileURLToPath(new URL('./src/agent-studio/index.ts', import.meta.url)),
        'agent-flow/index': fileURLToPath(new URL('./src/agent-flow/index.ts', import.meta.url)),
        'connector-hub/index': fileURLToPath(new URL('./src/connector-hub/index.ts', import.meta.url)),
        'channel-hub/index': fileURLToPath(new URL('./src/channel-hub/index.ts', import.meta.url)),
        'plugin-hub/index': fileURLToPath(new URL('./src/plugin-hub/index.ts', import.meta.url)),
        'mcp-hub/index': fileURLToPath(new URL('./src/mcp-hub/index.ts', import.meta.url)),
        'skill-hub/index': fileURLToPath(new URL('./src/skill-hub/index.ts', import.meta.url)),
        'tool-hub/index': fileURLToPath(new URL('./src/tool-hub/index.ts', import.meta.url)),
        'trigger-hub/index': fileURLToPath(new URL('./src/trigger-hub/index.ts', import.meta.url)),
      },
      formats: ['es', 'cjs'],
      // Emit a single collected stylesheet as dist/style.css (matches the
      // ./style.css export in package.json), not dist/<libname>.css.
      cssFileName: 'style',
    },
    rollupOptions: {
      external: isExternal,
      output: [
        {
          format: 'es',
          chunkFileNames: 'chunks/[name]-[hash].js',
        },
        {
          format: 'cjs',
          entryFileNames: '[name].cjs',
          chunkFileNames: 'chunks/[name]-[hash].cjs',
          // agent-flow's entry mixes a default export (the Vue plugin) with
          // named exports — pin CJS interop explicitly (module.exports gets
          // the named exports plus `.default`) so Rollup stops warning.
          exports: 'named',
        },
      ],
    },
  },
});
