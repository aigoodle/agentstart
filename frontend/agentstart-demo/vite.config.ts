import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

import { agentStartMock } from './mock/plugin.js'

// vue-agent-start 源码路径（用于开发时直接引用源码，无需重新构建）
const libRoot = fileURLToPath(new URL('../vue-agentstart', import.meta.url))
const vueAgentStartSource = `${libRoot}/src/index.ts`
const vueAgentStartClientSource = `${libRoot}/src/client/index.ts`
const vueAgentStartProviderSource = `${libRoot}/src/provider-hub/index.ts`
const vueAgentStartKnowledgeSource = `${libRoot}/src/knowledge-hub/index.ts`
const vueAgentStartStudioSource = `${libRoot}/src/agent-studio/index.ts`
const vueAgentStartAgentFlowSource = `${libRoot}/src/agent-flow/index.ts`
const vueAgentStartConnectorSource = `${libRoot}/src/connector-hub/index.ts`
const vueAgentStartChannelSource = `${libRoot}/src/channel-hub/index.ts`
const vueAgentStartPluginSource = `${libRoot}/src/plugin-hub/index.ts`
const vueAgentStartMcpSource = `${libRoot}/src/mcp-hub/index.ts`
const vueAgentStartSkillSource = `${libRoot}/src/skill-hub/index.ts`
const vueAgentStartToolSource = `${libRoot}/src/tool-hub/index.ts`
const vueAgentStartTriggerSource = `${libRoot}/src/trigger-hub/index.ts`
const vueAgentStartAigoodleSource = `${libRoot}/src/aigoodle.ts`
const vueAgentStartStyleSource = `${libRoot}/dist/style.css` // 使用构建后的 CSS（已处理 Tailwind @apply）
const vueAgentStartFlowSource = `${libRoot}/src/agent-flow`

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 默认连接真实后端；只有显式设置 MOCK=true 才启用内置 mock。
  const useMock = env.MOCK === 'true'
  const apiTarget = env.VITE_AGENT_API_TARGET || 'http://localhost:18090'
  const chatIframeTarget = env.VITE_CHAT_IFRAME_DEV_TARGET || 'http://localhost:5073'
  const devPort = Number(env.VITE_PORT) || 5173

  return {
    plugins: [vue(), ...(useMock ? [agentStartMock()] : [])],
    define: {
      __USE_MOCK__: JSON.stringify(useMock),
    },
    resolve: {
      // vue-agent-start 通过 link: 引入，其 node_modules 里也带有 vue/pinia 等，
      // dedupe 保证运行时只有一份实例。
      dedupe: [
        'vue',
        'pinia',
        'vue-router',
        '@ant-design/icons-vue',
        '@vue-flow/core',
        '@vue-flow/background',
        '@vue-flow/minimap',
        '@vue-flow/controls',
        '@vue-flow/node-resizer',
        'vue-draggable-next',
      ],
      // 直接解析 vue-agent-start 的源码，无需每次重新构建库
      // 使用数组格式 + 正则表达式，确保精确匹配
      alias: [
        { find: /^vue-agent-start\/style\.css$/, replacement: vueAgentStartStyleSource },
        { find: /^vue-agent-start\/client$/, replacement: vueAgentStartClientSource },
        { find: /^vue-agent-start\/provider-hub$/, replacement: vueAgentStartProviderSource },
        { find: /^vue-agent-start\/knowledge-hub$/, replacement: vueAgentStartKnowledgeSource },
        { find: /^vue-agent-start\/agent-studio$/, replacement: vueAgentStartStudioSource },
        { find: /^vue-agent-start\/agent-flow$/, replacement: vueAgentStartAgentFlowSource },
        { find: /^vue-agent-start\/connector-hub$/, replacement: vueAgentStartConnectorSource },
        { find: /^vue-agent-start\/channel-hub$/, replacement: vueAgentStartChannelSource },
        { find: /^vue-agent-start\/plugin-hub$/, replacement: vueAgentStartPluginSource },
        { find: /^vue-agent-start\/mcp-hub$/, replacement: vueAgentStartMcpSource },
        { find: /^vue-agent-start\/skill-hub$/, replacement: vueAgentStartSkillSource },
        { find: /^vue-agent-start\/tool-hub$/, replacement: vueAgentStartToolSource },
        { find: /^vue-agent-start\/trigger-hub$/, replacement: vueAgentStartTriggerSource },
        { find: /^vue-agent-start\/aigoodle$/, replacement: vueAgentStartAigoodleSource },
        { find: /^vue-agent-start$/, replacement: vueAgentStartSource },
        // vue-agent-start 内部别名（legacy agent-flow 模块使用）
        { find: /^@\/(.*)$/, replacement: `${vueAgentStartFlowSource}/$1` },
      ],
    },
    optimizeDeps: {
      // 永不缓存链接的组件库：其源文件必须保留在 Vite 的转换图中，
      // 这样编辑才能立即触发 HMR，无需重新构建 dist。
      exclude: ['vue-agent-start'],
    },
    server: {
      // 监听所有网卡地址，便于通过局域网 IP 访问；仍可用 --port 自定义启动端口。
      host: '0.0.0.0',
      port: devPort,
      fs: {
        // 允许访问 link: 的组件库源码（位于本项目的上级目录）
        allow: [fileURLToPath(new URL('..', import.meta.url))],
      },
      // 避免源码链接库耗尽 Linux inotify 额度。保留 HMR，改用轮询检测文件变更。
      watch: {
        usePolling: true,
        interval: 500,
      },
      proxy: useMock
        ? undefined
        : {
            '/api': {
              target: apiTarget,
              changeOrigin: true,
              rewrite: (path) => path.replace(/^\/api/, ''),
              ws: true,
            },
            // react-antdx-chat 使用 /chat/ 作为 Vite base；保留前缀转发，
            // 这样 iframe 内的 JS/CSS 资源也会继续命中聊天项目。
            '/chat/': {
              target: chatIframeTarget,
              changeOrigin: true,
              ws: true,
            },
            // 聊天项目固定通过 /chatapi/agent-start 访问 Agent 后端。
            '/chatapi': {
              target: apiTarget,
              changeOrigin: true,
              rewrite: (path) => path.replace(/^\/chatapi/, ''),
              ws: true,
            },
          },
    },
  }
})
