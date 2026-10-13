import { createApp, type Plugin } from 'vue'
import { createPinia } from 'pinia'

// 组件库入口自带样式 side-effect（dist/style.css），必须先于宿主覆盖样式导入
import { AgentStartPlugin } from 'vue-agent-start'
// Vite library 构建会把 src/index.ts 的 `import './style.css'` 抽到独立的
// dist/style.css,但不会在 dist/index.js 里保留运行时 import。
// 必须显式加载(利用 package.json 的 "./style.css" 子路径导出),否则只有 JS 没有样式。
import 'vue-agent-start/style.css'

// 宿主 HUD 皮肤，两段都必须在库样式之后：
// 1) 生成的强制暗色层（重映射库内写死的浅色 hex，引用 --hud-* 变量）
// 2) 手写主题（定义 --hud-*/--as-*/--kh-* 令牌 + as-* 组件覆写，同特异性后来者胜）
import './styles/hud-force-dark.css'
import './styles/hud.css'

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
