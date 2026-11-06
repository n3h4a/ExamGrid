import { CalendarDays, DoorOpen, MapPin } from 'lucide-react';
import type { SeatInfo } from '../../api/types';
import { capitalise, formatDate } from '../../lib/format';

/** A student's exams as cards: when, what, where, which seat. */
export function ExamList({ exams }: { exams: SeatInfo[] }) {
  return (
    <ol className="grid gap-3 sm:grid-cols-2">
      {exams.map((e) => (
        <li key={e.courseCode + e.slot} className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-mono text-sm font-semibold text-brand-700">{e.courseCode}</p>
              <p className="text-sm text-slate-700">{e.courseName}</p>
            </div>
            <div className="text-right">
              <p className="text-xs uppercase tracking-wide text-slate-500">Seat</p>
              <p className="text-2xl font-bold tabular-nums text-slate-900">{e.seat}</p>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-600">
            <span className="inline-flex items-center gap-1"><CalendarDays className="size-4" />{formatDate(e.date)} · {capitalise(e.session)}</span>
            <span className="inline-flex items-center gap-1"><DoorOpen className="size-4" />{e.roomCode}</span>
            {e.building && <span className="inline-flex items-center gap-1"><MapPin className="size-4" />{e.building}</span>}
          </div>
        </li>
      ))}
    </ol>
  );
}
