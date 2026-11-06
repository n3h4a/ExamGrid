import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { useState } from 'react';
import { runsApi } from '../../api/endpoints';
import type { RoomPlan, SlotLoad } from '../../api/types';
import { ErrorBanner, SelectInput, Spinner } from '../../components/ui';
import { formatSlot } from '../../lib/format';
import { seatLabel } from '../../lib/seats';
import { tintsFor } from './palette';

function RoomChart({ room }: { room: RoomPlan }) {
  const tints = tintsFor(room.courses);
  const seats = new Map(room.seats.map((s) => [`${s.row}:${s.column}`, s]));
  const blocked = new Set(room.blockedSeats);
  return (
    <section className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <header className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">
          {room.roomCode} <span className="text-sm font-normal text-slate-500">{room.building}</span>
        </h3>
        <p className="text-xs text-slate-500">
          {room.seats.length}/{room.capacity} seats · invigilators {room.invigilators.join(', ') || 'none'}
        </p>
      </header>
      <div className="mb-2 flex flex-wrap gap-1.5">
        {room.courses.map((c) => (
          <span key={c} className={clsx('rounded px-1.5 py-0.5 font-mono text-xs ring-1 ring-inset', tints.get(c))}>{c}</span>
        ))}
      </div>
      <p className="text-center text-[10px] font-semibold uppercase tracking-wide text-slate-400">Front</p>
      <div className="overflow-x-auto">
        <div className="mx-auto grid w-max gap-0.5" style={{ gridTemplateColumns: `repeat(${room.columns}, 3.4rem)` }}>
          {Array.from({ length: room.rows }, (_, r) =>
            Array.from({ length: room.columns }, (_, c) => {
              const label = seatLabel(r, c);
              const seat = seats.get(`${r}:${c}`);
              return (
                <div
                  key={label}
                  title={seat ? `${label} · ${seat.roll} · ${seat.courseCode}` : label}
                  className={clsx(
                    'rounded px-1 py-0.5 text-[9px] leading-tight ring-1 ring-inset',
                    blocked.has(label) ? 'bg-slate-300 text-slate-500 ring-slate-400' : seat ? tints.get(seat.courseCode) : 'bg-white text-slate-300 ring-slate-200',
                  )}
                >
                  <div className="font-semibold">{label}</div>
                  <div className="truncate font-mono">{seat ? seat.roll : blocked.has(label) ? '×' : ''}</div>
                </div>
              );
            }),
          )}
        </div>
      </div>
    </section>
  );
}

/** Seating charts of every room in one slot; neighbours never share a paper. */
export function SeatingView({ runId, slots }: { runId: number; slots: SlotLoad[] }) {
  const used = slots.filter((s) => s.candidates > 0);
  const [slot, setSlot] = useState(used[0]?.slot ?? '');
  const rooms = useQuery({ queryKey: ['run', runId, 'rooms', slot], queryFn: () => runsApi.rooms(runId, slot), enabled: !!slot });
  return (
    <div>
      <div className="mb-4 max-w-sm">
        <SelectInput label="Slot" value={slot} onChange={(e) => setSlot(e.target.value)}>
          {used.map((s) => (
            <option key={s.slot} value={s.slot}>
              {formatSlot(s.slot)} · {s.candidates} candidates in {s.rooms} rooms
            </option>
          ))}
        </SelectInput>
      </div>
      {rooms.isPending ? <Spinner /> : rooms.isError ? <ErrorBanner error={rooms.error} /> : (
        <div className="grid gap-4 xl:grid-cols-2">
          {rooms.data.map((r) => <RoomChart key={r.roomCode} room={r} />)}
        </div>
      )}
    </div>
  );
}
