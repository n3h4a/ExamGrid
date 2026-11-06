import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GitCompare, Play } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { runsApi } from '../../api/endpoints';
import { ACTIVE_STATUSES, type RunParams, type RunSummary } from '../../api/types';
import { Button, Card, EmptyState, ErrorBanner, Modal, PageHeader, Pagination, Spinner, Table, Td, Th } from '../../components/ui';
import { formatDateTime, formatNumber } from '../../lib/format';
import { NewRunForm } from './NewRunForm';
import { ProgressBar, RunStatusBadge } from './RunBits';
import { formatDuration } from './runFormat';
import { useRunEvents } from './useRunEvents';

function LiveProgress({ run }: { run: RunSummary }) {
  const event = useRunEvents(run.id, true);
  const pct = event?.pct ?? run.progressPct;
  return (
    <div className="w-40">
      <ProgressBar pct={pct} />
      <p className="mt-1 text-xs text-slate-500 tabular-nums">
        {pct}%{event?.score !== null && event?.score !== undefined ? ` · score ${formatNumber(event.score)}` : ''}
      </p>
    </div>
  );
}

export function RunsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const runs = useQuery({ queryKey: ['runs', page], queryFn: () => runsApi.list(page), placeholderData: keepPreviousData });
  const create = useMutation({
    mutationFn: (p: RunParams) => runsApi.create(p),
    onSuccess: (run) => {
      void queryClient.invalidateQueries({ queryKey: ['runs'] });
      navigate(`/admin/runs/${run.id}`);
    },
  });

  function toggle(id: number) {
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length < 5 ? [...s, id] : s));
  }

  return (
    <>
      <PageHeader
        title="Schedule runs"
        description="Each run solves the current data in the background: timetable, rooms, seats and invigilators. Compare runs, then approve the best one."
        actions={
          <>
            <Button variant="secondary" disabled={selected.length < 2} onClick={() => navigate(`/admin/runs/compare?ids=${selected.join(',')}`)}>
              <GitCompare className="size-4" /> Compare {selected.length >= 2 ? `(${selected.length})` : ''}
            </Button>
            <Button onClick={() => { create.reset(); setCreating(true); }}>
              <Play className="size-4" /> New run
            </Button>
          </>
        }
      />
      <Card>
        {runs.isPending ? (
          <Spinner />
        ) : runs.isError ? (
          <ErrorBanner error={runs.error} />
        ) : runs.data.items.length === 0 ? (
          <EmptyState title="No runs yet" action={<Button onClick={() => setCreating(true)}>Start the first run</Button>}>
            A run turns enrolments, slots, rooms and faculty into a complete exam plan.
          </EmptyState>
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th className="w-8"><span className="sr-only">Compare</span></Th>
                  <Th>#</Th>
                  <Th>Label</Th>
                  <Th>Status</Th>
                  <Th>Score</Th>
                  <Th>Solve time</Th>
                  <Th>Started by</Th>
                  <Th>Created</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {runs.data.items.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50">
                    <Td>
                      <input type="checkbox" aria-label={`Select run ${r.id} for comparison`} checked={selected.includes(r.id)} onChange={() => toggle(r.id)} className="size-4 rounded border-slate-300" />
                    </Td>
                    <Td className="tabular-nums text-slate-500">{r.id}</Td>
                    <Td className="whitespace-normal">
                      <Link to={`/admin/runs/${r.id}`} className="font-medium text-brand-700 hover:underline">{r.label ?? `Run ${r.id}`}</Link>
                      {r.parentRunId && <span className="ml-2 text-xs text-slate-500">derived from #{r.parentRunId}</span>}
                    </Td>
                    <Td>{ACTIVE_STATUSES.includes(r.status) ? <LiveProgress run={r} /> : <RunStatusBadge status={r.status} />}</Td>
                    <Td className="tabular-nums">{r.score === null ? '—' : formatNumber(r.score)}</Td>
                    <Td className="tabular-nums">{formatDuration(r.durationMs)}</Td>
                    <Td>{r.createdBy}</Td>
                    <Td>{formatDateTime(r.createdAt)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={runs.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <Modal wide open={creating} title="New schedule run" onClose={() => setCreating(false)}>
        {creating && <NewRunForm onSubmit={(p) => create.mutate(p)} busy={create.isPending} error={create.error} />}
      </Modal>
    </>
  );
}
