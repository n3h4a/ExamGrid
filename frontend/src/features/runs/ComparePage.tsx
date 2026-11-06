import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Link, useSearchParams } from 'react-router';
import { runsApi } from '../../api/endpoints';
import type { RunDetail } from '../../api/types';
import { Card, ErrorBanner, PageHeader, Spinner } from '../../components/ui';
import { formatNumber } from '../../lib/format';
import { RunStatusBadge } from './RunBits';
import { formatDuration } from './runFormat';

interface Row {
  label: string;
  value: (r: RunDetail) => number | string | null | undefined;
  /** Lower is better: the best value in the row is highlighted. */
  lowerIsBetter?: boolean;
}

const ROWS: Row[] = [
  { label: 'Score', value: (r) => r.metrics?.solver.timetable.score, lowerIsBetter: true },
  { label: 'Clashes', value: (r) => r.metrics?.solver.timetable.clashPairs, lowerIsBetter: true },
  { label: 'Same-day pairs', value: (r) => r.metrics?.solver.timetable.sameDayPairs, lowerIsBetter: true },
  { label: 'Students with two exams on a day', value: (r) => r.metrics?.solver.timetable.studentsWithSameDay, lowerIsBetter: true },
  { label: 'Back-to-back pairs', value: (r) => r.metrics?.solver.timetable.backToBackPairs, lowerIsBetter: true },
  { label: 'Hard papers too close', value: (r) => r.metrics?.solver.timetable.hardGapPairs, lowerIsBetter: true },
  { label: 'Room-slots used', value: (r) => r.metrics?.solver.roomSlotsUsed, lowerIsBetter: true },
  { label: 'Seat use', value: (r) => (r.metrics ? `${Math.round(r.metrics.solver.seatUtilisation * 100)}%` : null) },
  { label: 'Duty spread', value: (r) => (r.metrics ? `${r.metrics.solver.minFacultyDuties}–${r.metrics.solver.maxFacultyDuties}` : null) },
  { label: 'Solve time', value: (r) => (r.metrics ? formatDuration(r.metrics.solver.elapsedMillis) : null) },
  { label: 'Optimiser', value: (r) => r.params.optimizer },
  { label: 'Time limit', value: (r) => `${r.params.timeLimitSeconds} s` },
  { label: 'Weights (day / b2b / hard)', value: (r) => `${r.params.weights.sameDay} / ${r.params.weights.backToBack} / ${r.params.weights.hardGap}` },
  { label: 'Seed', value: (r) => r.params.seed },
];

export function ComparePage() {
  const [params] = useSearchParams();
  const ids = (params.get('ids') ?? '').split(',').map(Number).filter((n) => n > 0);
  const runs = useQuery({ queryKey: ['compare', ids], queryFn: () => runsApi.compare(ids), enabled: ids.length >= 2 });
  if (ids.length < 2) return <ErrorBanner error={new Error('Choose at least two runs to compare.')} />;
  if (runs.isPending) return <Spinner />;
  if (runs.isError) return <ErrorBanner error={runs.error} />;
  return (
    <>
      <PageHeader title="Compare runs" description="Best value per row is highlighted. Approve the run you prefer from its page." />
      <Card>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="py-2 pr-4 text-left text-xs font-semibold uppercase text-slate-500">Metric</th>
                {runs.data.map((r) => (
                  <th key={r.run.id} className="px-4 py-2 text-left">
                    <Link to={`/admin/runs/${r.run.id}`} className="font-semibold text-brand-700 hover:underline">{r.run.label ?? `Run ${r.run.id}`}</Link>
                    <div className="mt-1"><RunStatusBadge status={r.run.status} /></div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ROWS.map((row) => {
                const values = runs.data.map(row.value);
                const numeric = values.filter((v): v is number => typeof v === 'number');
                const best = row.lowerIsBetter && numeric.length > 1 ? Math.min(...numeric) : null;
                return (
                  <tr key={row.label}>
                    <td className="py-2 pr-4 text-slate-600">{row.label}</td>
                    {values.map((v, i) => (
                      <td key={i} className={clsx('px-4 py-2 tabular-nums', best !== null && v === best && 'font-semibold text-emerald-700')}>
                        {v === null || v === undefined ? '—' : typeof v === 'number' ? formatNumber(v) : v}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
