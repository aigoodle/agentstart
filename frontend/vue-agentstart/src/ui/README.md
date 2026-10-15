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
