import clsx from 'clsx';
import type { RunStatus } from '../../api/types';
import { Badge } from '../../components/ui';

const STATUS: Record<RunStatus, { tone: 'slate' | 'green' | 'amber' | 'red' | 'blue' | 'violet'; label: string }> = {
  QUEUED: { tone: 'slate', label: 'Queued' },
  RUNNING: { tone: 'blue', label: 'Running' },
  DONE: { tone: 'green', label: 'Ready for review' },
  INFEASIBLE: { tone: 'amber', label: 'Infeasible' },
  FAILED: { tone: 'red', label: 'Failed' },
  CANCELLED: { tone: 'slate', label: 'Cancelled' },
  PUBLISHING: { tone: 'violet', label: 'Publishing…' },
  PUBLISHED: { tone: 'violet', label: 'Published' },
  SUPERSEDED: { tone: 'slate', label: 'Superseded' },
};

export function RunStatusBadge({ status }: { status: RunStatus }) {
  const s = STATUS[status];
  return <Badge tone={s.tone}>{s.label}</Badge>;
}

export function ProgressBar({ pct, className }: { pct: number; className?: string }) {
  const value = Math.max(0, Math.min(100, pct));
  return (
    <div className={clsx('h-2 w-full overflow-hidden rounded-full bg-slate-100', className)} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <div className="h-full rounded-full bg-brand-500 transition-[width] duration-300" style={{ width: `${value}%` }} />
    </div>
  );
}
