/**
 * ChatIframePanel 相关的共享类型。抽出成独立 .ts 文件，
 * 让 `.vue` 消费方能通过 `import type` 拿到（`<script setup>` 里的 export
 * 无法被外部再导出，独立 ts 才能被 `export type ... from './x'` 转发）。
 */

/** 一个可填写的调试变量 —— 与 START 节点 variables / AgentVariable 兼容 */
export interface ChatDebugVariable {
  /** 参数键；写入 Dify inputs 时用这个名字 */
  name: string;
  /** 显示名 —— 不填走 name */
  label?: string;
  /** string / number / boolean / object / array / file；未识别按 string 处理 */
  type?: string;
  required?: boolean;
  description?: string;
  /** 默认值（string / number / boolean 生效；object/array 会尝试 JSON.parse） */
  defaultValue?: unknown;
  /** enum 类型的可选项（预留） */
  options?: Array<{ label: string; value: string }>;
}

/** 承载给 FlowDesigner / AppDesignDrawer 的调试 iframe 配置 */
export interface ChatIframeConfig {
  /** iframe 基础 URL；例如 `/chat/` 或 `http://localhost:5073/chat/` */
  src: string;
  /** 附加到 URL 的查询参数（difyApiKey / label 等） */
  params?: Record<string, boolean | number | string | null | undefined>;
  /** postMessage 下发给 iframe 的业务上下文 */
  context?: Record<string, unknown>;
  /** 顶部标题 */
  title?: string;
  /** localStorage 命名空间；不填自动生成 */
  sessionKey?: string;
}
