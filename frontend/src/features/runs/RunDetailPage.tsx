import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { AlertTriangle, CheckCircle2, CircleSlash, Flame, ShieldCheck, Trash2, XCircle } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { catalogApi, runsApi } from '../../api/endpoints';
import { ACTIVE_STATUSES, type RunDetail } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { Badge, Button, Card, ErrorBanner, Modal, PageHeader, SelectInput, Spinner, StatTile, TextInput } from '../../components/ui';
import { formatDateTime, formatNumber, formatSlot } from '../../lib/format';
import { DocumentsView } from './DocumentsView';
import { InvigilationView } from './InvigilationView';
import { ProgressBar, RunStatusBadge } from './RunBits';
import { formatDuration, phaseLabel } from './runFormat';
import { SeatingView } from './SeatingView';
import { TimetableGrid } from './TimetableGrid';
import { useRunEvents } from './useRunEvents';

type Tab = 'timetable' | 'seating' | 'invigilation' | 'documents';

export function RunDetailPage() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const detail = useQuery({
    queryKey: ['run', id],
    queryFn: () => runsApi.get(id),
    // While the publication job runs, poll until it flips the status.
    refetchInterval: (q) => (q.state.data?.run.status === 'PUBLISHING' ? 1500 : false),
  });
  const status = detail.data?.run.status;
  const active = !!status && ACTIVE_STATUSES.includes(status);
  const event = useRunEvents(id, active);
  const [tab, setTab] = useState<Tab>('timetable');
  const [disrupting, setDisrupting] = useState(false);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['run', id] });
    void queryClient.invalidateQueries({ queryKey: ['runs'] });
  };
  const cancel = useMutation({ mutationFn: () => runsApi.cancel(id), onSuccess: refresh });
  const remove = useMutation({ mutationFn: () => runsApi.remove(id), onSuccess: () => navigate('/admin/runs') });

  if (detail.isPending) return <Spinner />;
  if (detail.isError) return <ErrorBanner error={detail.error} />;
  const d = detail.data;
  const run = d.run;
  const hasPlan = ['DONE', 'PUBLISHING', 'PUBLISHED', 'SUPERSEDED'].includes(run.status);
  const deletable = !active && !['PUBLISHED', 'PUBLISHING'].includes(run.status);

  return (
    <>
      <PageHeader
        title={run.label ?? `Run ${run.id}`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <RunStatusBadge status={run.status} /> #{run.id} · started by {run.createdBy} · {formatDateTime(run.createdAt)}
            {run.parentRunId && (
              <Link to={`/admin/runs/${run.parentRunId}`} className="text-brand-600 hover:underline">derived from run #{run.parentRunId}</Link>
            )}
          </span>
        }
        actions={
          <>
            {active && <Button variant="secondary" loading={cancel.isPending} onClick={() => cancel.mutate()}><CircleSlash className="size-4" /> Cancel</Button>}
            {hasPlan && run.status !== 'SUPERSEDED' && (
              <Button variant="secondary" onClick={() => setDisrupting(true)}><Flame className="size-4" /> Room lost…</Button>
            )}
            {deletable && (
              <Button variant="ghost" aria-label="Delete run" onClick={() => window.confirm(`Delete run #${run.id}?`) && remove.mutate()}><Trash2 className="size-4" /></Button>
            )}
          </>
        }
      />
      <ErrorBanner error={cancel.error ?? remove.error} />

      {active && (
        <Card title="Solving" className="mb-6">
          <ProgressBar pct={event?.pct ?? run.progressPct} className="h-3" />
          <div className="mt-3 flex flex-wrap justify-between gap-2 text-sm text-slate-600">
            <span>{phaseLabel(event?.phase ?? 'QUEUED')}{event?.message ? ` · ${event.message}` : ''}</span>
            <span className="tabular-nums">
              {event?.pct ?? run.progressPct}%{event?.score !== null && event?.score !== undefined && ` · best score ${formatNumber(event.score)}`}
            </span>
          </div>
        </Card>
      )}

      {run.status === 'INFEASIBLE' && d.infeasibility && <InfeasibilityPanel detail={d} />}
      {run.status === 'FAILED' && (
        <div className="mb-6 rounded-lg bg-red-50 p-4 text-sm text-red-800 ring-1 ring-red-200">
          <p className="font-medium">The run failed</p>
          <p className="mt-1">{d.errorMessage}</p>
        </div>
      )}
      {d.dataChanged && (
        <div className="mb-6 flex gap-2 rounded-lg bg-amber-50 p-4 text-sm text-amber-900 ring-1 ring-amber-200">
          <AlertTriangle className="size-4 shrink-0" />
          The data has changed since this run was solved, so it can no longer be approved. Start a new run.
        </div>
      )}
      {d.disruption && (
        <div className="mb-6 rounded-lg bg-sky-50 p-4 text-sm text-sky-900 ring-1 ring-sky-200">
          <p className="font-medium">Re-seated without {d.disruption.lostRoom}</p>
          <p className="mt-1">
            {d.disruption.summary}. Slots affected: {d.disruption.affectedSlots.map(formatSlot).join(', ')}.
          </p>
        </div>
      )}

      {d.metrics && <MetricsPanel detail={d} />}
      {hasPlan && <ApprovalPanel detail={d} onChange={refresh} />}

      {hasPlan && (
        <Card className="mt-6">
          <div className="-mt-1 mb-5 flex gap-1 border-b border-slate-200" role="tablist">
            {(['timetable', 'seating', 'invigilation', ...(run.status === 'PUBLISHED' || run.status === 'SUPERSEDED' ? ['documents'] : [])] as Tab[]).map((t) => (
              <button
                key={t}
                role="tab"
                aria-selected={tab === t}
                onClick={() => setTab(t)}
                className={clsx('-mb-px border-b-2 px-3 py-2 text-sm font-medium capitalize', tab === t ? 'border-brand-700 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800')}
              >
                {t}
              </button>
            ))}
          </div>
          {tab === 'timetable' && <TimetableTab runId={id} />}
          {tab === 'seating' && d.metrics && <SeatingView runId={id} slots={d.metrics.slots} />}
          {tab === 'invigilation' && <InvigilationView runId={id} />}
          {tab === 'documents' && <DocumentsView runId={id} />}
        </Card>
      )}

      <Modal open={disrupting} title="A room is unavailable" onClose={() => setDisrupting(false)}>
        {disrupting && <DisruptionForm detail={d} onCreated={(childId) => { setDisrupting(false); navigate(`/admin/runs/${childId}`); }} />}
      </Modal>
    </>
  );
}

function TimetableTab({ runId }: { runId: number }) {
  const timetable = useQuery({ queryKey: ['run', runId, 'timetable'], queryFn: () => runsApi.timetable(runId) });
  if (timetable.isPending) return <Spinner />;
  if (timetable.isError) return <ErrorBanner error={timetable.error} />;
  return <TimetableGrid entries={timetable.data} />;
}

function InfeasibilityPanel({ detail }: { detail: RunDetail }) {
  const issues = detail.infeasibility?.issues ?? [];
  const proven = issues.some((i) => i.proof);
  return (
    <Card title={proven ? 'No clash-free timetable exists' : 'No timetable found'} className="mb-6">
      <p className="mb-4 text-sm text-slate-600">
        {proven
          ? 'Each item below is a proof: no algorithm could satisfy these inputs. Change the data or rules, then run again.'
          : 'The search could not place every exam. Adding slots or relaxing rules usually helps.'}
      </p>
      <ul className="space-y-3">
        {issues.map((issue, i) => (
          <li key={i} className="rounded-lg bg-amber-50 p-3 text-sm ring-1 ring-amber-200">
            <div className="flex items-center gap-2">
              <Badge tone="amber">{issue.type.replaceAll('_', ' ').toLowerCase()}</Badge>
              {issue.proof && <Badge tone="red">proof</Badge>}
            </div>
            <p className="mt-2 text-slate-800">{issue.message}</p>
            {issue.students.length > 0 && <p className="mt-1 text-xs text-slate-600">Students: {issue.students.join(', ')}</p>}
          </li>
        ))}
      </ul>
    </Card>
  );
}

function MetricsPanel({ detail }: { detail: RunDetail }) {
  const m = detail.metrics!;
  const s = m.solver;
  const t = s.timetable;
  const maxLoad = Math.max(1, ...m.slots.map((x) => x.candidates));
  const seats = detail.inputSummary?.seats ?? maxLoad;
  return (
    <div className="mb-6 space-y-6">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatTile label="Score" value={formatNumber(t.score)} hint={`from ${formatNumber(s.initialScore)} after DSatur`} />
        <StatTile label="Clashes" value={<span className={t.clashPairs === 0 ? 'text-emerald-700' : 'text-red-700'}>{t.clashPairs}</span>} hint="must be 0" />
        <StatTile label="Same-day pairs" value={formatNumber(t.sameDayPairs)} hint={`${formatNumber(t.studentsWithSameDay)} students`} />
        <StatTile label="Back-to-back" value={formatNumber(t.backToBackPairs)} hint="overnight pairs" />
        <StatTile label="Hard papers close" value={formatNumber(t.hardGapPairs)} />
        <StatTile label="Seat use" value={`${Math.round(s.seatUtilisation * 100)}%`} hint={`${s.roomSlotsUsed} room-slots`} />
        <StatTile label="Duties each" value={`${s.minFacultyDuties}–${s.maxFacultyDuties}`} hint={s.unfilledDuties ? `${s.unfilledDuties} unfilled` : 'all filled'} />
        <StatTile label="Solve time" value={formatDuration(s.elapsedMillis)} hint={`${formatNumber(s.optimiserIterations)} moves`} />
      </div>
      {m.warnings.length > 0 && (
        <ul className="space-y-1 text-sm text-amber-800">
          {m.warnings.map((w) => <li key={w} className="flex gap-2"><AlertTriangle className="size-4 shrink-0" />{w}</li>)}
        </ul>
      )}
      <Card title="Candidates per slot">
        <div className="flex h-40 items-end gap-1" aria-label="Candidates per slot">
          {m.slots.map((x) => (
            <div key={x.slot} className="group relative flex h-full flex-1 flex-col justify-end">
              <div
                className={clsx('rounded-t', x.candidates > 0 ? 'bg-brand-500 group-hover:bg-brand-700' : 'bg-slate-200')}
                style={{ height: `${Math.max(2, (x.candidates / Math.max(seats, maxLoad)) * 100)}%` }}
                title={`${formatSlot(x.slot)}: ${x.candidates} candidates, ${x.exams} exams, ${x.rooms} rooms`}
              />
            </div>
          ))}
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Bars are scaled to the {formatNumber(seats)} seats available per slot. Hover for details.
        </p>
      </Card>
    </div>
  );
}

function ApprovalPanel({ detail, onChange }: { detail: RunDetail; onChange: () => void }) {
  const { user } = useAuth();
  const [comment, setComment] = useState('');
  const run = detail.run;
  const mine = detail.approvals.some((a) => a.username === user?.username);
  const approve = useMutation({ mutationFn: () => runsApi.approve(run.id, comment || undefined), onSuccess: () => { setComment(''); onChange(); } });
  const withdraw = useMutation({ mutationFn: () => runsApi.withdraw(run.id), onSuccess: onChange });
  const retry = useMutation({ mutationFn: () => runsApi.retryPublish(run.id), onSuccess: onChange });
  const publishFailed = run.status === 'DONE' && detail.errorMessage?.startsWith('Publishing failed');

  let body: ReactNode;
  if (run.status === 'PUBLISHED') {
    body = <p className="flex items-center gap-2 text-sm text-emerald-800"><CheckCircle2 className="size-4" /> Live for students since publication. Hall tickets and charts are in the Documents tab.</p>;
  } else if (run.status === 'SUPERSEDED') {
    body = <p className="text-sm text-slate-600">A newer plan has replaced this one.</p>;
  } else if (run.status === 'PUBLISHING') {
    body = <p className="text-sm text-violet-800">Generating hall tickets, seating charts and the seat index…</p>;
  } else {
    body = (
      <div className="space-y-3">
        <p className="text-sm text-slate-600">Two different exam-cell members must approve before the plan is published.</p>
        {publishFailed && (
          <div className="flex flex-wrap items-center gap-3 text-sm text-red-700">
            <XCircle className="size-4" /> {detail.errorMessage}
            <Button variant="secondary" loading={retry.isPending} onClick={() => retry.mutate()}>Retry publishing</Button>
          </div>
        )}
        {!mine ? (
          <div className="flex flex-wrap items-end gap-2">
            <TextInput label="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} className="min-w-[18rem]" />
            <Button loading={approve.isPending} disabled={!!detail.dataChanged} onClick={() => approve.mutate()}>
              <ShieldCheck className="size-4" /> Approve
            </Button>
          </div>
        ) : (
          <Button variant="secondary" loading={withdraw.isPending} onClick={() => withdraw.mutate()}>Withdraw my approval</Button>
        )}
      </div>
    );
  }

  return (
    <Card title="Approval" className="mt-6">
      <ErrorBanner error={approve.error ?? withdraw.error ?? retry.error} title="Approval failed" />
      {body}
      {detail.approvals.length > 0 && (
        <ul className="mt-4 divide-y divide-slate-100 text-sm">
          {detail.approvals.map((a) => (
            <li key={a.username} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
              <span><span className="font-medium">{a.displayName}</span> <span className="text-slate-500">({a.username})</span>{a.comment && <span className="text-slate-600"> · “{a.comment}”</span>}</span>
              <span className="text-xs text-slate-500">{formatDateTime(a.approvedAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function DisruptionForm({ detail, onCreated }: { detail: RunDetail; onCreated: (id: number) => void }) {
  const rooms = useQuery({ queryKey: ['rooms'], queryFn: catalogApi.rooms });
  const [room, setRoom] = useState('');
  const [slot, setSlot] = useState('');
  const disrupt = useMutation({ mutationFn: () => runsApi.disrupt(detail.run.id, room, slot), onSuccess: (r) => onCreated(r.id) });
  const slots = detail.metrics?.slots.filter((s) => s.candidates > 0) ?? [];
  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        The timetable stays as it is. Only the students seated in the lost room move, into spare seats or a reserve room. The result is a
        new run that needs the usual two approvals.
      </p>
      <ErrorBanner error={disrupt.error} title="Could not re-seat" />
      <SelectInput label="Room" value={room} onChange={(e) => setRoom(e.target.value)}>
        <option value="">Choose a room…</option>
        {rooms.data?.filter((r) => r.active).map((r) => <option key={r.id} value={r.code}>{r.code} · {r.building}</option>)}
      </SelectInput>
      <SelectInput label="Slot" value={slot} onChange={(e) => setSlot(e.target.value)} hint="Leave on 'every slot' if the room is out for the whole exam period.">
        <option value="">Every slot</option>
        {slots.map((s) => <option key={s.slot} value={s.slot}>{formatSlot(s.slot)}</option>)}
      </SelectInput>
      <div className="flex justify-end">
        <Button variant="danger" disabled={!room} loading={disrupt.isPending} onClick={() => disrupt.mutate()}>Re-seat students</Button>
      </div>
    </div>
  );
}
