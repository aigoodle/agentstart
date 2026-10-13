import { Button } from 'antd';
import type { AgentApprovalRequiredEvent } from '../lib/provider';

export default function AgentApprovalCard({
  approval,
  loading,
  onDecision,
}: {
  approval: AgentApprovalRequiredEvent | null;
  loading: boolean;
  onDecision: (decision: 'APPROVE' | 'DENY') => void;
}) {
  if (!approval) return null;
  return (
    <div className="mx-auto mb-3 w-full max-w-[940px] rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm dark:border-amber-800 dark:bg-amber-950/30">
      <div className="font-medium text-amber-900 dark:text-amber-100">
        Agent 请求执行工具：{approval.toolName}
      </div>
      <pre className="mt-2 max-h-28 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-white/70 p-2 text-xs text-slate-600 dark:bg-black/20 dark:text-zinc-300">
        {approval.toolInput}
      </pre>
      <div className="mt-3 flex justify-end gap-2">
        <Button size="small" disabled={loading} onClick={() => onDecision('DENY')}>拒绝</Button>
        <Button size="small" type="primary" loading={loading} onClick={() => onDecision('APPROVE')}>批准并继续</Button>
      </div>
    </div>
  );
}
