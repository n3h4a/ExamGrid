import { useState, type FormEvent } from 'react';
import type { Room, RoomRequest } from '../../api/types';
import { Button, Checkbox, ErrorBanner, TextInput } from '../../components/ui';
import { maxSamePaper, seatLabel } from '../../lib/seats';
import { SeatGridEditor } from './SeatGridEditor';

export function RoomForm({ initial, onSubmit, onCancel, busy, error }: {
  initial?: Room;
  onSubmit: (body: RoomRequest) => void;
  onCancel: () => void;
  busy: boolean;
  error: unknown;
}) {
  const [code, setCode] = useState(initial?.code ?? '');
  const [building, setBuilding] = useState(initial?.building ?? '');
  const [rows, setRows] = useState(initial?.rows ?? 6);
  const [columns, setColumns] = useState(initial?.columns ?? 8);
  const [priority, setPriority] = useState(initial?.priority ?? 0);
  const [active, setActive] = useState(initial?.active ?? true);
  const [blocked, setBlocked] = useState<Set<string>>(new Set(initial?.blockedSeats ?? []));

  // Blocked seats outside a shrunken grid are dropped.
  const inGrid = new Set([...blocked].filter((label) => {
    for (let r = 0; r < rows; r++) for (let c = 0; c < columns; c++) if (seatLabel(r, c) === label) return true;
    return false;
  }));
  const capacity = rows * columns - inGrid.size;

  function toggle(label: string) {
    const next = new Set(blocked);
    if (next.has(label)) next.delete(label);
    else next.add(label);
    setBlocked(next);
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({ code, building, rows, columns, blockedSeats: [...inGrid], priority, active });
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <ErrorBanner error={error} title="Could not save the room" />
      <div className="grid gap-4 sm:grid-cols-3">
        <TextInput label="Code" required disabled={!!initial} value={code} onChange={(e) => setCode(e.target.value)} />
        <TextInput label="Building" value={building} onChange={(e) => setBuilding(e.target.value)} />
        <TextInput label="Fill priority" type="number" min={0} value={priority} onChange={(e) => setPriority(Number(e.target.value))} hint="Lower fills first" />
        <TextInput label="Rows" type="number" min={1} max={100} required value={rows} onChange={(e) => setRows(Math.max(1, Math.min(100, Number(e.target.value))))} />
        <TextInput label="Benches per row" type="number" min={1} max={100} required value={columns} onChange={(e) => setColumns(Math.max(1, Math.min(100, Number(e.target.value))))} />
        <div className="flex items-end pb-2">
          <Checkbox label="In use" checked={active} onChange={(e) => setActive(e.target.checked)} />
        </div>
      </div>
      <div className="rounded-lg bg-slate-50 p-4">
        <SeatGridEditor rows={rows} columns={columns} blocked={inGrid} onToggle={toggle} />
        <p className="mt-3 text-center text-sm text-slate-600">
          <span className="font-medium">{capacity}</span> usable seats · up to{' '}
          <span className="font-medium">{maxSamePaper(rows, columns, inGrid)}</span> candidates of one paper without neighbours sharing it ·{' '}
          {inGrid.size} blocked
        </p>
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={busy}>
          Save room
        </Button>
      </div>
    </form>
  );
}
