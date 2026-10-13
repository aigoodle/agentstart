import React from 'react';
import { isEmbedded, notifyHost } from '../lib/host-bridge';

/**
 * XMarkdown 自定义标签：LLM 流式输出的可点击动作按钮（整行 CTA 卡片样式）
 * --------------------------------------------------
 * 典型模型输出：
 *   <act-btn desc="通用工作空间入口" path="/workspace">立即打开</act-btn>
 *
 * 属性说明（HTML 属性透传后全部小写，读取都用 kebab-case）：
 *   - path:  目标路由路径（必需）
 *   - title: 打开后的标签页/面包屑标题（可选，透传给宿主）
 *   - desc:  副标题，同时兼作鼠标 hover 提示（可选）
 *   - click: 老格式的 JSON 字符串（如 `{"path":"/home","title":""}`），
 *            兼容用；顶层属性 path/title 会覆盖 JSON 里的同名字段
 *   - 子内容：按钮主标题，缺省显示"立即打开"
 *
 * 流式渲染注意：
 *   XMarkdown 会在标签还没闭合时就把 `<act-btn>` 提前渲染一次（streamStatus='loading'）。
 *   为避免"第一个半态按钮 path 是空的、点了没反应"的错觉，
 *   loading 且 path 未到位时渲染一张骨架卡片；path 一旦有值就切正常按钮。
 */

type ActBtnProps = {
  path?: string;
  title?: string;
  desc?: string;
  click?: string; // legacy JSON payload
  children?: React.ReactNode;
  streamStatus?: 'done' | 'loading';
};

const ActBtn: React.FC<ActBtnProps> = ({
  path,
  title,
  desc,
  click,
  children,
  streamStatus,
}) => {
  // 合并出最终 payload：老格式 click(JSON) 先铺底，顶层 path/title/desc 覆盖
  const payload: Record<string, unknown> = {};
  const legacy = parseJsonLoose(click);
  if (legacy && typeof legacy === 'object') {
    Object.assign(payload, legacy);
  }
  if (path) payload.path = path;
  if (title) payload.title = title;
  if (desc && payload.desc == null) payload.desc = desc;

  const resolvedPath = typeof payload.path === 'string' ? payload.path : '';
  // 标题优先取子文本；子内容为空时用 desc 作为主标题（此时 desc 不再当副标题重复）
  const childLabel = normalizeLabel(children);
  const label = childLabel || desc || '立即打开';
  const subtitle = childLabel ? desc : undefined;

  // 流式渲染中 → 一律先亮加载卡片(旋转 spinner + shimmer 扫光 + 骨架条).
  // 即便 path 已经从属性里解析到, 也可能只是一半 (如 "/workspa"),
  // 等 streamStatus 变 'done' 再切正常按钮才能保证点开的是完整路径.
  if (streamStatus === 'loading') {
    return (
      <div
        className="relative my-1.5 flex w-full items-center gap-2 overflow-hidden rounded-xl bg-gradient-to-r from-sky-500 via-violet-500 to-rose-500 px-4 py-2 text-white shadow-[0_6px_18px_rgba(139,92,246,0.32)]"
      >
        {/* 高光扫过动画：通过 keyframes 让白色斜条持续从左划到右 */}
        <span
          aria-hidden
          className="pointer-events-none absolute inset-y-0 -left-1/3 w-1/3"
          style={{
            background:
              'linear-gradient(90deg,transparent 0%,rgba(255,255,255,0.35) 50%,transparent 100%)',
            animation: 'actbtn-shimmer 1.2s linear infinite',
          }}
        />
        {/* 旋转 spinner */}
        <span className="relative flex h-6 w-6 shrink-0 items-center justify-center">
          <span
            className="block h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
          />
        </span>
        {/* 骨架文字条 */}
        <span className="relative flex min-w-0 flex-1 flex-col gap-1">
          <span className="h-3 w-2/5 animate-pulse rounded bg-white/70" />
          <span className="h-2 w-3/5 animate-pulse rounded bg-white/45" />
        </span>
        <span className="relative shrink-0 text-[11px] font-medium text-white/90">
          生成中…
        </span>
        {/* 局部 keyframes，只对 shimmer 生效，不污染全局 */}
        <style>{`@keyframes actbtn-shimmer{0%{transform:translateX(0)}100%{transform:translateX(600%)}}`}</style>
      </div>
    );
  }

  const handleClick = () => {
    if (!resolvedPath) return;
    if (isEmbedded) {
      notifyHost('openRoute', payload);
      return;
    }
    window.open(resolvedPath, '_blank');
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      title={desc || (typeof payload.title === 'string' ? payload.title : undefined)}
      disabled={!resolvedPath}
      className="group my-1.5 flex w-full items-center gap-2 rounded-xl bg-gradient-to-r from-sky-500 via-violet-500 to-rose-500 px-4 py-2 text-left text-white shadow-[0_6px_18px_rgba(139,92,246,0.32),0_2px_4px_rgba(56,189,248,0.22)] transition-all hover:-translate-y-0.5 hover:shadow-[0_10px_24px_rgba(139,92,246,0.45),0_3px_6px_rgba(56,189,248,0.3)] active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-60"
    >
      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-lg bg-white/25 text-sm font-semibold shadow-inner transition-transform group-hover:scale-105">
        →
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold leading-tight">
          {label}
        </span>
        {subtitle ? (
          <span className="mt-0.5 block truncate text-[11px] leading-tight text-white/85">
            {subtitle}
          </span>
        ) : null}
      </span>
      <span className="ml-1 shrink-0 text-[11px] text-white/75">打开 →</span>
    </button>
  );
};

/** LLM 里常见的 `\n    立即打开\n  ` 之类前后带换行空白，裁一下更好看 */
function normalizeLabel(children: React.ReactNode): React.ReactNode {
  if (typeof children === 'string') {
    const t = children.trim();
    return t || null;
  }
  return children;
}

/** 宽松 JSON 解析：先严格，再尝试单引号/无引号 key 修正，都失败返回 null */
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

export default ActBtn;
