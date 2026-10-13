import React from 'react';
import { isEmbedded, notifyHost } from '../lib/host-bridge';

/**
 * XMarkdown 自定义标签：AI 生成的"结构化面板"通用容器
 * --------------------------------------------------
 * 典型模型输出：
 *   <build-panel title="本周课表" value='{"slots":[...]}'>
 *     ...(人类可读的预览内容, 可以是 markdown/表格/说明文字)...
 *   </build-panel>
 *
 * 行为：
 *   1. value 属性里塞完整业务 JSON, 供宿主直接消费 (课表/审批草稿/表单预填 等);
 *   2. 组件把子内容当做面板的预览区渲染 (让模型自己写 markdown 表格/要点即可);
 *   3. 底部固定 CTA 按钮 → notifyHost('buildPanel', 解析后的 value 对象),
 *      宿主由 aiAssistant.onAction('buildPanel', handler) 注册页面处理器。
 *      如需业务分流, 让模型在 value JSON 里带 kind 字段, 别塞到消息壳外.
 *
 * 可配属性 (全部可选)：
 *   - value:   业务 JSON, 会被解析后透传给宿主
 *   - title:   头部标题, 默认 "AI 生成面板"
 *   - action:  底部 CTA 按钮文案, 默认 "立即使用"
 *   - subtitle:头部标题右侧的补充说明 (小字)
 *
 * 流式渲染注意：
 *   XMarkdown 会在标签还没闭合时提前渲染一次 (streamStatus='loading').
 *   loading 期间一律展示骨架, 避免"半态按钮点了没反应"。
 */

type BuildPanelProps = {
  value?: string;
  title?: string;
  action?: string;
  subtitle?: string;
  children?: React.ReactNode;
  streamStatus?: 'done' | 'loading';
};

const BuildPanel: React.FC<BuildPanelProps> = ({
  value,
  title,
  action,
  subtitle,
  children,
  streamStatus,
}) => {
  const parsed = parseJsonLoose(value);
  const hasValidPayload = parsed != null;
  const isLoading = streamStatus === 'loading';

  const handleApply = () => {
    if (!hasValidPayload) return;
    if (isEmbedded) {
      notifyHost('buildPanel', parsed);
      return;
    }
    // 独立页调试：直接打印, 便于开发时确认 payload
    console.info('[BuildPanel] apply payload (standalone):', parsed);
  };

  return (
    <div className="my-2 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      {/* 头部 */}
      <div className="flex items-center gap-2 border-b border-slate-100 bg-gradient-to-r from-sky-50 via-white to-violet-50 px-3 py-2 dark:border-zinc-800 dark:from-sky-950/40 dark:via-zinc-900 dark:to-violet-950/40">
        {isLoading ? (
          <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-sky-300 border-t-sky-500" />
        ) : (
          <span className="inline-block h-4 w-1 rounded-full bg-gradient-to-b from-sky-400 to-violet-500" />
        )}
        <span className="text-xs font-semibold text-slate-700 dark:text-zinc-200">
          {title || '生成结果内容'}
        </span>
        {isLoading && (
          <span className="text-[11px] text-slate-400">生成中…</span>
        )}
        {subtitle && (
          <span className="ml-auto text-[11px] text-slate-400">{subtitle}</span>
        )}
      </div>

      {/* 预览区: 由模型自己写 markdown/文字/表格, 流式期间也把 children 渲染在这里, 避免内容外溢到面板外 */}
      <div className="px-3 py-2 text-xs text-slate-600 dark:text-zinc-300">
        {children ?? (
          isLoading ? (
            <div className="space-y-2">
              <span className="block h-3 w-2/3 animate-pulse rounded bg-slate-100 dark:bg-zinc-800" />
              <span className="block h-3 w-1/2 animate-pulse rounded bg-slate-100 dark:bg-zinc-800" />
              <span className="block h-3 w-3/4 animate-pulse rounded bg-slate-100 dark:bg-zinc-800" />
            </div>
          ) : (
            <span className="text-slate-400 dark:text-zinc-500">
              (无预览内容)
            </span>
          )
        )}
      </div>

      {/* 底部 CTA: 只有流式结束后 (拿到完整 value + children) 才渲染, 避免半态点击 / 骨架闪烁 */}
      {!isLoading && (
        <div className="border-t border-slate-100 bg-slate-50/60 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-900/60">
          <button
            type="button"
            onClick={handleApply}
            disabled={!hasValidPayload}
            className="group flex w-full items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-sky-500 via-violet-500 to-rose-500 px-4 py-2 text-sm font-semibold text-white shadow-[0_6px_18px_rgba(139,92,246,0.32)] transition-all hover:-translate-y-0.5 hover:shadow-[0_10px_24px_rgba(139,92,246,0.45)] active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <span>{action || '立即使用'}</span>
            <span className="text-xs text-white/85 transition-transform group-hover:translate-x-0.5">
              →
            </span>
          </button>
        </div>
      )}
    </div>
  );
};

/** 宽松 JSON 解析: 先严格, 再尝试单引号/无引号 key 修正, 都失败返回 null */
function parseJsonLoose(raw?: string): unknown {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed);
  } catch {
    /* fallthrough */
  }
  try {
    const normalized = trimmed
      .replace(/'/g, '"')
      .replace(/([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:/g, '$1"$2":');
    return JSON.parse(normalized);
  } catch {
    return null;
  }
}

export default BuildPanel;
