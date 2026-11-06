import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { catalogApi } from '../../api/endpoints';
import { SESSIONS, type Session, type Slot } from '../../api/types';
import { Badge, Button, Card, Checkbox, EmptyState, ErrorBanner, Field, PageHeader, Spinner, TextInput } from '../../components/ui';
import { capitalise, formatDate } from '../../lib/format';

const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export function SlotsPage() {
  const queryClient = useQueryClient();
  const slots = useQuery({ queryKey: ['slots'], queryFn: catalogApi.slots });
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [sessions, setSessions] = useState<Session[]>(['MORNING', 'AFTERNOON']);
  const [skipDays, setSkipDays] = useState<string[]>(['SUNDAY']);
  const [holidays, setHolidays] = useState('');

  const generate = useMutation({
    mutationFn: () =>
      catalogApi.generateSlots({
        startDate: start,
        endDate: end,
        sessions,
        skipWeekdays: skipDays,
        skipDates: holidays.split(/[,\s]+/).filter(Boolean),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['slots'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog-summary'] });
    },
  });
  const remove = useMutation({
    mutationFn: (s: Slot) => catalogApi.deleteSlot(s.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['slots'] }),
    onError: (e) => window.alert(errorMessage(e)),
  });

  const byDate = new Map<string, Slot[]>();
  for (const s of slots.data ?? []) byDate.set(s.date, [...(byDate.get(s.date) ?? []), s]);

  function submit(e: FormEvent) {
    e.preventDefault();
    generate.mutate();
  }

  return (
    <>
      <PageHeader title="Exam slots" description="Each slot is one sitting (date × session). Slots are the colours the timetable is painted with." />
      <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
        <Card title="Generate slots">
          <form onSubmit={submit} className="space-y-4">
            <ErrorBanner error={generate.error} title="Could not generate slots" />
            <div className="grid grid-cols-2 gap-3">
              <TextInput label="From" type="date" required value={start} onChange={(e) => setStart(e.target.value)} />
              <TextInput label="To" type="date" required value={end} onChange={(e) => setEnd(e.target.value)} />
            </div>
            <Field label="Sessions">
              <div className="flex flex-wrap gap-3">
                {SESSIONS.map((s) => (
                  <Checkbox key={s} label={capitalise(s)} checked={sessions.includes(s)} onChange={(e) => setSessions(e.target.checked ? [...sessions, s] : sessions.filter((x) => x !== s))} />
                ))}
              </div>
            </Field>
            <Field label="Skip weekdays">
              <div className="grid grid-cols-2 gap-1">
                {WEEKDAYS.map((d) => (
                  <Checkbox key={d} label={capitalise(d)} checked={skipDays.includes(d)} onChange={(e) => setSkipDays(e.target.checked ? [...skipDays, d] : skipDays.filter((x) => x !== d))} />
                ))}
              </div>
            </Field>
            <TextInput label="Holidays" placeholder="2026-12-25, 2026-12-26" value={holidays} onChange={(e) => setHolidays(e.target.value)} />
            <Button type="submit" loading={generate.isPending} disabled={sessions.length === 0}>Generate</Button>
            {generate.data && <p className="text-sm text-emerald-700">{generate.data.length} new slot(s) created; existing slots were kept.</p>}
          </form>
        </Card>
        <Card title={`${slots.data?.length ?? 0} slots`}>
          {slots.isPending ? (
            <Spinner />
          ) : byDate.size === 0 ? (
            <EmptyState title="No slots yet">Generate the exam window on the left.</EmptyState>
          ) : (
            <ul className="divide-y divide-slate-100">
              {[...byDate.entries()].map(([date, list]) => (
                <li key={date} className="flex items-center justify-between gap-4 py-2">
                  <span className="text-sm font-medium">{formatDate(date)}</span>
                  <span className="flex flex-wrap gap-2">
                    {list.map((s) => (
                      <span key={s.id} className="inline-flex items-center gap-1">
                        <Badge tone={s.session === 'MORNING' ? 'blue' : s.session === 'AFTERNOON' ? 'amber' : 'violet'}>{capitalise(s.session)}</Badge>
                        <button className="text-slate-400 hover:text-red-600" aria-label={`Delete ${s.key}`} onClick={() => window.confirm(`Delete slot ${s.key}?`) && remove.mutate(s)}>
                          <Trash2 className="size-3.5" />
                        </button>
                      </span>
                    ))}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
