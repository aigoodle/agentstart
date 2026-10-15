import { createApp, type Plugin } from 'vue'
import { createPinia } from 'pinia'

// 共用 shadcn UI 和主题；Vite 将链接库的源码 CSS 直接编译并支持 HMR。
import { AgentStartPlugin } from 'vue-agent-start'
// 发布包使用方仍应显式导入此路径；demo 将它解析到源码 CSS。
import 'vue-agent-start/style.css'

// HUD 是独立视觉皮肤；基础控件从组件库的语义颜色令牌读取主题。
// 不再加载通过覆盖浅色 hex 生成的 force-dark 样式。
import './styles/hud.css'
import './styles/shadcn.css'

import App from './App.vue'
import { router } from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 链接包携带第二份 vue 类型（3.5.40 vs 宿主 3.5.43），Plugin 泛型不匹配，断言绕过
app.use(AgentStartPlugin as unknown as Plugin, {
  // mock 中间件与 proxy 都挂在这个前缀下
  apiBase: '/api',
})

app.mount('#app')
