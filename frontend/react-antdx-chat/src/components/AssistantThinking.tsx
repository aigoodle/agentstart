import { LoadingOutlined } from '@ant-design/icons';
import { clsx } from 'clsx';
import type React from 'react';

/**
 * Placeholder rendered inside the assistant bubble while we're waiting for the
 * first streaming token from the backend.
 *
 * Ant Design X sets the message's {@code status} to {@code 'loading'} between
 * "user pressed send" and "first delta arrived" — that gap can be 1–3 seconds
 * for a real LLM call. Without a visible indicator the bubble looks dead:
 * either empty, or (worse) showing the plain locale placeholder as static
 * text. This component paints a rotating spinner + three bouncing dots + a
 * short label so the wait feels intentional and progressing.
 *
 * Pure Tailwind, works in both light and dark host themes.
 */
const AssistantThinking: React.FC<{ isDark: boolean; label?: string }> = ({
  isDark,
  label = 'AI 思考中',
}) => {
  return (
    <div
      className={clsx(
        'flex items-center gap-2.5 py-0.5',
        isDark ? 'text-zinc-300' : 'text-slate-600',
      )}
    >
      <LoadingOutlined
        spin
        style={{
          fontSize: 16,
          color: isDark ? '#60a5fa' : '#3b82f6',
        }}
      />
      <span className="text-sm font-medium">{label}</span>
      <span className="inline-flex items-end gap-1 pb-[3px]">
        <span
          className={clsx(
            'h-1.5 w-1.5 rounded-full',
            isDark ? 'bg-sky-400/80' : 'bg-sky-500/80',
          )}
          style={{ animation: 'copilot-bounce 1.2s ease-in-out -0.32s infinite both' }}
        />
        <span
          className={clsx(
            'h-1.5 w-1.5 rounded-full',
            isDark ? 'bg-violet-400/80' : 'bg-violet-500/80',
          )}
          style={{ animation: 'copilot-bounce 1.2s ease-in-out -0.16s infinite both' }}
        />
        <span
          className={clsx(
            'h-1.5 w-1.5 rounded-full',
            isDark ? 'bg-rose-400/80' : 'bg-rose-500/80',
          )}
          style={{ animation: 'copilot-bounce 1.2s ease-in-out 0s infinite both' }}
        />
      </span>
      {/* keyframes are inlined so this component is drop-in without touching global CSS */}
      <style>{`
        @keyframes copilot-bounce {
          0%, 80%, 100% { transform: translateY(0); opacity: 0.35; }
          40%           { transform: translateY(-4px); opacity: 1; }
        }
      `}</style>
    </div>
  );
};

export default AssistantThinking;
