import clsx from 'clsx';
import { seatLabel } from '../../lib/seats';

/**
 * Click seats to block or unblock them (pillars, broken benches, aisles). Row A is the front.
 */
export function SeatGridEditor({ rows, columns, blocked, onToggle }: {
  rows: number;
  columns: number;
  blocked: Set<string>;
  onToggle: (label: string) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <p className="mb-2 text-center text-xs font-semibold uppercase tracking-wide text-slate-400">Front of hall</p>
      <div className="mx-auto grid w-max gap-1" style={{ gridTemplateColumns: `repeat(${columns}, minmax(2rem, 1fr))` }} role="grid" aria-label="Seat layout">
        {Array.from({ length: rows }, (_, r) =>
          Array.from({ length: columns }, (_, c) => {
            const label = seatLabel(r, c);
            const isBlocked = blocked.has(label);
            return (
              <button
                type="button"
                key={label}
                role="gridcell"
                aria-pressed={isBlocked}
                aria-label={`Seat ${label}${isBlocked ? ' (blocked)' : ''}`}
                onClick={() => onToggle(label)}
                className={clsx(
                  'h-8 rounded text-[10px] font-medium ring-1 transition-colors',
                  isBlocked
                    ? 'bg-slate-300 text-slate-500 ring-slate-400 line-through'
                    : (r + c) % 2 === 0
                      ? 'bg-sky-50 text-sky-800 ring-sky-200 hover:bg-sky-100'
                      : 'bg-amber-50 text-amber-800 ring-amber-200 hover:bg-amber-100',
                )}
              >
                {label}
              </button>
            );
          }),
        )}
      </div>
    </div>
  );
}
