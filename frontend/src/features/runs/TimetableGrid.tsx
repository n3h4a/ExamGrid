import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { SESSIONS, type TimetableEntry } from '../../api/types';
import { TextInput } from '../../components/ui';
import { capitalise, formatDate } from '../../lib/format';
import { tintOf } from './palette';

/** Days down, sessions across; each cell lists the exams in that slot. */
export function TimetableGrid({ entries }: { entries: TimetableEntry[] }) {
  const [filter, setFilter] = useState('');
  const sessions = SESSIONS.filter((s) => entries.some((e) => e.session === s));
  const byDate = useMemo(() => {
    const map = new Map<string, Map<string, TimetableEntry[]>>();
    for (const e of entries) {
      const day = map.get(e.date) ?? new Map<string, TimetableEntry[]>();
      day.set(e.session, [...(day.get(e.session) ?? []), e]);
      map.set(e.date, day);
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [entries]);
  const f = filter.trim().toUpperCase();

  return (
    <div>
      <div className="mb-4 max-w-xs">
        <TextInput placeholder="Highlight course or department…" aria-label="Highlight" value={filter} onChange={(e) => setFilter(e.target.value)} />
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full border-separate border-spacing-1 text-sm">
          <thead>
            <tr>
              <th className="w-40 px-2 py-1 text-left text-xs font-semibold uppercase text-slate-500">Date</th>
              {sessions.map((s) => (
                <th key={s} className="px-2 py-1 text-left text-xs font-semibold uppercase text-slate-500">{capitalise(s)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {byDate.map(([date, day]) => (
              <tr key={date}>
                <td className="whitespace-nowrap px-2 py-1 align-top font-medium text-slate-700">{formatDate(date)}</td>
                {sessions.map((s) => (
                  <td key={s} className="rounded-lg bg-slate-50 p-2 align-top">
                    <div className="flex flex-wrap gap-1">
                      {(day.get(s) ?? []).map((e) => {
                        const match = f && (e.courseCode.includes(f) || e.department.toUpperCase() === f);
                        return (
                          <span
                            key={e.courseCode}
                            title={`${e.courseName} · ${e.candidates} candidates · ${e.rooms.join(', ')}`}
                            className={clsx(
                              'rounded px-1.5 py-0.5 font-mono text-xs ring-1 ring-inset',
                              tintOf(e.courseCode),
                              f && !match && 'opacity-25',
                              match && 'ring-2 ring-brand-700',
                            )}
                          >
                            {e.courseCode}
                            <span className="ml-1 opacity-60">{e.candidates}</span>
                          </span>
                        );
                      })}
                    </div>
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
