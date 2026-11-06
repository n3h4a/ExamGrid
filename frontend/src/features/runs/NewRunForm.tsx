import { useState, type FormEvent } from 'react';
import type { Optimizer, RunParams } from '../../api/types';
import { Button, Checkbox, ErrorBanner, SelectInput, TextInput } from '../../components/ui';

/** Run parameters with the solver defaults pre-filled; advanced knobs are tucked away. */
export function NewRunForm({ onSubmit, busy, error }: { onSubmit: (p: RunParams) => void; busy: boolean; error: unknown }) {
  const [label, setLabel] = useState('');
  const [optimizer, setOptimizer] = useState<Optimizer>('ANNEALING');
  const [timeLimit, setTimeLimit] = useState(60);
  const [iterations, setIterations] = useState(1_000_000);
  const [seed, setSeed] = useState(42);
  const [sameDay, setSameDay] = useState(10);
  const [backToBack, setBackToBack] = useState(3);
  const [hardGap, setHardGap] = useState(5);
  const [gapDays, setGapDays] = useState(2);
  const [perInvigilator, setPerInvigilator] = useState(30);
  const [diagonal, setDiagonal] = useState(false);
  const [advanced, setAdvanced] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({
      label: label || undefined,
      optimizer,
      timeLimitSeconds: timeLimit,
      maxIterations: iterations,
      seed,
      weights: { sameDay, backToBack, hardGap, hardMinGapDays: gapDays },
      studentsPerInvigilator: perInvigilator,
      diagonalAdjacency: diagonal,
    });
  }

  const num = (set: (n: number) => void) => (e: { target: { value: string } }) => set(Number(e.target.value));

  return (
    <form onSubmit={submit} className="space-y-4">
      <ErrorBanner error={error} title="Could not start the run" />
      <TextInput label="Label" placeholder="Dec 2026 end-semester, draft 1" value={label} onChange={(e) => setLabel(e.target.value)} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectInput label="Optimiser" value={optimizer} onChange={(e) => setOptimizer(e.target.value as Optimizer)} hint="Annealing is best when slots are scarce.">
          <option value="ANNEALING">Simulated annealing</option>
          <option value="TABU">Tabu search</option>
          <option value="NONE">None (DSatur only)</option>
        </SelectInput>
        <TextInput label="Time limit (seconds)" type="number" min={1} max={600} value={timeLimit} onChange={num(setTimeLimit)} />
      </div>
      <fieldset className="rounded-lg bg-slate-50 p-4">
        <legend className="px-1 text-sm font-medium text-slate-700">Penalty weights (per pair of exams a student sits)</legend>
        <div className="grid gap-3 sm:grid-cols-4">
          <TextInput label="Same day" type="number" min={0} value={sameDay} onChange={num(setSameDay)} />
          <TextInput label="Back-to-back" type="number" min={0} value={backToBack} onChange={num(setBackToBack)} />
          <TextInput label="Hard papers close" type="number" min={0} value={hardGap} onChange={num(setHardGap)} />
          <TextInput label="Min. days apart" type="number" min={0} value={gapDays} onChange={num(setGapDays)} />
        </div>
      </fieldset>
      <button type="button" className="text-sm text-brand-600 hover:underline" onClick={() => setAdvanced(!advanced)}>
        {advanced ? 'Hide' : 'Show'} advanced settings
      </button>
      {advanced && (
        <div className="grid gap-4 sm:grid-cols-3">
          <TextInput label="Move budget" type="number" min={0} value={iterations} onChange={num(setIterations)} />
          <TextInput label="Random seed" type="number" value={seed} onChange={num(setSeed)} hint="Same seed, same plan" />
          <TextInput label="Students per invigilator" type="number" min={1} value={perInvigilator} onChange={num(setPerInvigilator)} />
          <div className="sm:col-span-3">
            <Checkbox label="Diagonal neighbours count as adjacent (stricter seating)" checked={diagonal} onChange={(e) => setDiagonal(e.target.checked)} />
          </div>
        </div>
      )}
      <div className="flex justify-end">
        <Button type="submit" loading={busy}>Start run</Button>
      </div>
    </form>
  );
}
