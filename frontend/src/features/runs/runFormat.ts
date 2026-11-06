const PHASES: Record<string, string> = {
  QUEUED: 'Waiting for a solver thread',
  GRAPH: 'Building the conflict graph',
  COLOURING: 'Colouring with DSatur',
  OPTIMISING: 'Optimising with the metaheuristic',
  ROOMS: 'Allocating rooms (min-cost flow)',
  SEATING: 'Seating candidates',
  INVIGILATION: 'Assigning invigilators',
  DONE: 'Finished',
};

export function phaseLabel(phase: string): string {
  return PHASES[phase] ?? phase;
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  return s < 60 ? `${s.toFixed(1)} s` : `${Math.floor(s / 60)} min ${Math.round(s % 60)} s`;
}
