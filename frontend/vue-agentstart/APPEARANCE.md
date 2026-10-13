# 外观与主题配置

`vue-agent-start` 的基础样式、明暗主题和 Drawer HUD 边框均随组件库发布，不依赖演示项目。宿主项目只负责选择外观参数，也可以覆盖公开的 CSS 变量。

## 必须加载样式

无论使用根入口还是子路径入口，都建议显式加载发布后的样式文件：

```ts
import { AgentStartPlugin } from 'vue-agent-start';
import 'vue-agent-start/style.css';
```

Vite 的 library mode 会把源码中的 CSS 提取为独立文件，因此仅导入 JavaScript 不能保证样式被加载。

## 启动时配置

```ts
app.use(AgentStartPlugin, {
  apiBase: '/api',
  appearance: {
    theme: 'dark',          // 'light' | 'dark'
    visual: 'tech',         // 'normal' | 'tech'
    primaryColor: '#00ccff',
  },
});
```

- `theme` 控制明亮或暗黑配色，同时同步 `<html data-theme>` 和兼容 Tailwind 的 `.dark` class。
- `visual: 'normal'` 使用普通产品界面；`visual: 'tech'` 启用组件库内置的 HUD 装饰。没有设置 `visual` 时，为兼容旧版本，Drawer 默认显示科技感边框。
- `primaryColor` 接受任意有效 CSS 颜色，并同步按钮、知识库强调色及 HUD 线条颜色。

## 运行时切换

```ts
import { setAgentStartAppearance } from 'vue-agent-start';

setAgentStartAppearance({ theme: 'light' });
setAgentStartAppearance({ visual: 'normal' });
setAgentStartAppearance({ primaryColor: '#722ed1' });
```

每次调用只修改已传入的字段，因此可以分别保存和切换三个设置。Drawer 和 Modal 会 Teleport 到 `body`，默认把参数写到 `document.documentElement` 才能让弹层继承主题。

如果是不会 Teleport 的局部嵌入界面，可以传入第二个参数：

```ts
setAgentStartAppearance(
  { theme: 'dark', visual: 'tech', primaryColor: '#13c2c2' },
  document.querySelector('#agent-start-zone') as HTMLElement,
);
```

## 直接使用 HTML 属性

不使用插件参数时，也可以沿用稳定的 HTML 约定：

```html
<html class="dark" data-theme="dark" data-visual="tech">
```

```css
:root {
  --color-as-primary: #00ccff;
  --as-theme-primary: #00ccff;
}
```

## Drawer HUD 自定义变量

Drawer 四周边框、Modal 切角边框、通用 `.hud-panel` 和对应导轨已经包含在 `vue-agent-start/style.css` 中。Drawer 与 Modal 的四边导轨贴紧组件边缘；Modal 保留向外伸出的四个斜角装饰，不需要从 demo 复制 CSS。

可按需覆盖：

```css
:root {
  --as-hud-surface: #07192b;
  --as-hud-line: rgb(0 204 255 / 72%);
  --as-hud-hot: #8ff3ff;
  --as-hud-glow: rgb(0 204 255 / 48%);
}
```

建议宿主优先设置 `primaryColor` 或 `--as-theme-primary`，只有需要精确设计系统对齐时才分别覆盖 HUD 变量。

## 通用 HUD Panel

组件库内部的工作流配置面板、模型选择器、Prompt 面板等会直接使用公共 `.hud-panel`。宿主自己的区域也可以使用同一个工具类：

```html
<section class="hud-panel">
  <!-- 内容 -->
</section>
```

如果外部装饰线允许略微超出容器，可同时添加 `hud-embed`：

```html
<section class="hud-panel hud-embed"></section>
```

在 `visual: 'tech'` 下显示切角与发光边框；切换为 `visual: 'normal'` 后自动变成普通圆角边框。Modal 和 Drawer 使用相同的切换规则和主题色，不需要为它们单独引入 demo 样式。

## 样式职责

- 组件库负责组件布局、普通模式、科技模式、明暗基础令牌和 HUD 边框。
- 宿主负责选择参数、保存用户偏好，以及按品牌需要覆盖公开变量。
- 演示项目仅展示设置面板和偏好持久化，不是组件库的运行依赖。
