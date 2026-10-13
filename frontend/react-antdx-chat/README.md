# React Ant Design X Chat

## 宿主项目接入

宿主只需引入 `public/chat-frame.js`。脚本加载后仅注册 `window.BootSchoolChat`，不会
创建按钮或 iframe，因此可以安全地写在 HTML 中；用户登录成功后调用 `login()` 才会
显示助手，退出登录调用 `logout()` 会完整移除 UI 并清理会话数据。

本地开发地址是 `http://localhost:5073/chat/chat-frame.js`。生产环境建议把 React
构建产物部署到宿主同域 `/chat/`，脚本地址保持为 `/chat/chat-frame.js`。

### 第一步：在 index.html 引入

```html
<script src="/chat/chat-frame.js"></script>
```

如需定制标题和空会话首页，请在 SDK 脚本之前声明配置：

```html
<script>
window.__AI_CHAT_CONFIG__ = {
  title: '智能 AI 助手',
  home: {
    title: '智能 AI 助手',
    subtitle: '7×24 智能陪伴 · 系统使用顾问',
    description: '您好! 我是您的 AI 助手……',
    capabilitiesTitle: 'AI 能力',
    capabilities: [
      { title: '系统使用', description: '菜单功能讲解', icon: 'help', color: 'sky', prompt: '请介绍系统菜单' },
    ],
    suggestionsTitle: '猜你想问',
    suggestions: ['日程冲突怎么自动检测和处理?'],
  },
}
</script>
<script src="/chat/chat-frame.js"></script>
```

页面加载后也可以调用 `window.BootSchoolChat.configure(nextConfig)`，iframe 会通过
既有 `postMessage` 通道立即刷新，无需重新创建。

### 第二步：登录后调用

```js
window.BootSchoolChat.login({
  apiKey: 'app-xxx',
  context: {
    token: 'Bearer xxx',
    user: { userId: '1', realName: '管理员', roles: ['admin'] },
    theme: 'light',
    locale: 'zh-CN',
    path: '/dashboard',
    platform: 'admin',
  },
  onEvent(event) {
    if (event.name === 'openRoute') router.push(event.data.path)
    if (event.name === 'buildPanel') applyAiData(event.data)
  },
})
```

上下文变化时更新，退出登录时销毁：

```js
window.BootSchoolChat.updateContext(nextContext)
window.BootSchoolChat.logout()
```

### Vue 3 最小示例

```vue
<script setup>
import { watch } from 'vue'

watch(
  () => authStore.token,
  (token) => {
    if (!token) return window.BootSchoolChat?.logout()

    window.BootSchoolChat?.login({
      context: {
        token: `Bearer ${token}`,
        user: authStore.user,
        theme: 'light',
        locale: 'zh-CN',
        path: route.fullPath,
        platform: 'admin',
      },
      onEvent: (event) => console.log('AI 业务事件', event),
    })
  },
  { immediate: true },
)
</script>
```

### 全局 API

- `login({ context, apiKey?, onEvent? })`：登录后挂载助手并注入上下文。
- `configure(config)`：动态更新助手标题、欢迎区、能力卡片和推荐问题。
- `updateContext(context)`：用户、主题、语言或路由变化后更新上下文。
- `logout()`：退出登录，移除 UI，清理 `boot-school-ai-session` 和 `bus_data`。
- `open()` / `close()`：主动打开或关闭面板。
- `postToChat(type, payload)`：向 iframe 发送自定义指令。
- `on(type, handler)`：监听 iframe 消息，返回取消监听函数。
- `storage.get/set/remove/merge`：统一管理同源共享数据。
- `tip(text, options)` / `hideTip()`：显示或隐藏助手气泡提示。

底层消息信封为 `{ channel: 'boot-school-chat', type, payload }`。iframe 发出
`chat:ready`、`chat:close`、`chat:event`，装载器下发 `host:context`、`host:open`、
`host:close`、`host:dock`、`host:command`。一般接入方只使用上面的全局 API，无需直接
处理 `postMessage`。

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```
