import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { Features } from 'lightningcss'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // 端口优先级：CLI `--port` (Vite 自动透传) > VITE_PORT > PORT (云平台/容器约定) > 5173
  const port = Number(env.VITE_PORT || env.PORT) || 5073
  // VITE_HOST: 留空或 "true" → 监听 0.0.0.0（局域网可访问，Vite 会打印本机 IP）；
  // 也可以填具体地址，例如 127.0.0.1 仅本机访问。
  const rawHost = env.VITE_HOST?.trim()
  const host: string | boolean =
    !rawHost || rawHost === 'true' ? true : rawHost === 'false' ? false : rawHost
  const strictPort = env.VITE_STRICT_PORT === 'true'

  const rawBase = env.VITE_BASE?.trim() || '/chat/'
  const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`

  return {
    base,
    plugins: [react(), tailwindcss()],
    // Tailwind 的开发输出包含原生 CSS 嵌套；旧 Chrome 会忽略其中的 dark:/hover:。
    // 开发与构建都转换为兼容选择器，并为现代颜色函数生成回退值。
    css: {
      transformer: 'lightningcss',
      lightningcss: {
        targets: { chrome: 100 << 16 },
        include: Features.Nesting,
      },
    },
    build: {
      cssTarget: 'chrome100',
    },
    server: {
      host,
      port,
      strictPort,
      proxy: {
        '/chatapi': {
          target: 'http://101.33.254.31:18091/v1',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/chatapi/, ''),
        },
      },
    },
    preview: {
      host,
      port,
      strictPort,
    },
  }
})
