# 公共 Card 约定

业务目录卡片必须从 `src/ui` 导入公共 `Card`。它组合本地 shadcn-vue 的
`Card`、`CardHeader`、`CardTitle`、`CardDescription`、`CardContent`、`CardFooter`。
禁止在业务模块再次实现卡片外壳，或通过 `:deep` 覆盖公共 Card 的内部结构。

- `variant="management"`：应用、知识库、渠道、MCP、工具、技能、插件、连接器、模型等目录卡片。
- `variant="create"`：新建入口，通过 `title`、`subtitle`、`disabled` 和 `@click` 配置。
- `compact`：同一组件的紧凑密度，只适用于供应商等紧凑目录；不能在页面另写 padding/gap。
- `icon`、`title`、`subtitle`、`description`、默认插槽填充业务内容。
- `badge` 放状态或 shadcn DropdownMenu；`meta` 放底部元数据；`actions` 直接放公共 Button，不能另套 footer 或操作栏布局。

组件拥有边框、颜色、圆角、阴影、图标容器、标题字号、描述截断、间距、footer、换行和键盘激活。
页面只拥有网格布局、品牌图标和业务内容。颜色来自 shadcn 语义令牌，状态标签使用公共 Tag（shadcn Badge）。
业务信息较多时卡片可以自然增高，但不能使用独立外观或固定高度裁切按钮。

修改公共 Card 必须验证应用、knowledge、channels 和 MCP，并检查明暗模式及窄列。
`CardArchitecture.test.ts` 检查所有目录入口使用公共 Card，且没有重新覆盖内部布局。
专用表单选项、图表或编辑器面板可以直接组合 shadcn Card；它们不是目录卡片，不应复制目录卡片规范。

业务信息浮层统一使用 `shadcn/popover` 的 Popover/Trigger/Content，并添加 `as-info-popover`，由 Reka 管理定位、焦点和关闭行为；科技模式的边框装饰由主题样式提供。分页使用公共 `Pagination`（shadcn Select/Button），通过 `page-change`、`page-size-change` 更新业务数据。

交互主色约定：主要按钮使用 `--primary` / `--primary-foreground`，输入框边框使用 `--input`、焦点使用 `--ring`，菜单与选中项使用 `--accent` 或主色与语义表面的 color-mix。禁止在业务控件中写死紫色主色或浅色选中背景；类型、状态标签的语义颜色单独管理。

科技模式的 HUD 装饰统一由 `hud-frame.css` 提供：页面 `.hud-panel`、信息 `.as-info-popover`、抽屉 `.as-sheet-hud__chrome` 共用 `--as-frame-mask`。颜色从全局 `--primary` 派生，四角使用连续折线路径，将横边、斜边和竖边一起加粗，局部平行细线增强层次，不再另画整圈内边框。宿主样式应在旧 HUD 样式之后引入此文件，避免固定色或 `!important` 覆盖。

提示统一入口：浮动成功/错误/信息使用 `message`（shadcn Sonner）；页面内错误、警告和信息使用 `InlineNotice`（shadcn Alert/AlertDescription）。提示表面和正文遵循语义主题变量，不再使用页面自绘的浅色提示背景。
