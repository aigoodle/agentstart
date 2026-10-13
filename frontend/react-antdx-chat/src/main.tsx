/* eslint-disable react-refresh/only-export-components */
import { StrictMode, useLayoutEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, theme as antdTheme } from 'antd'
import './index.css'
// XMarkdown 浅/深色主题样式同时载入，由内容包裹层的 className 选择生效（见 App / Copilot）。
import '@ant-design/x-markdown/themes/light.css'
import '@ant-design/x-markdown/themes/dark.css'
import App from './App.tsx'
import Copilot from './Copilot.tsx'
import { initHostBridge, isHostManaged } from './lib/host-bridge.ts'
import { useHostContext, useIsDark } from './lib/useHostBridge.ts'

// 作为宿主 iframe 嵌入时，初始化通讯桥（发送 chat:ready 并接收 host:context）。
initHostBridge()

// 布局模式：URL 带 ?mode=Copilot 时渲染助手式（Copilot 侧边面板）界面，否则全屏聊天。
// 仅在入口读取一次（无需路由），两种界面共用同一套 Chat 对接逻辑（useChat）。
const isCopilotMode =
  new URLSearchParams(window.location.search).get('mode')?.toLowerCase() === 'copilot'

// 跟随宿主下发的主题（深/浅色）。独立打开时 ctx 为 null，走默认浅色。
// 同时把 dark 类同步到 <html>，让 Tailwind 的 dark: 变体随之生效。
function Root() {
  const isDark = useIsDark()
  const hostContext = useHostContext()

  useLayoutEffect(() => {
    const root = document.documentElement
    root.style.colorScheme = isDark ? 'dark' : 'light'
    if (isDark) {
      root.classList.add('dark')
      root.classList.remove('light')
    } else {
      root.classList.add('light')
      root.classList.remove('dark')
    }
  }, [isDark])

  // 跨域 iframe 必须等握手拿到认证上下文后再创建聊天 provider。
  if (isHostManaged && !hostContext) return <div role="status">正在连接宿主页面…</div>

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      }}
    >
      {isCopilotMode ? <Copilot /> : <App />}
    </ConfigProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
