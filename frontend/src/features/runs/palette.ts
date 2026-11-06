/** Stable, distinguishable tints per course code for seating charts and timetable chips. */
const TINTS = [
  'bg-sky-100 text-sky-900 ring-sky-300',
  'bg-amber-100 text-amber-900 ring-amber-300',
  'bg-emerald-100 text-emerald-900 ring-emerald-300',
  'bg-violet-100 text-violet-900 ring-violet-300',
  'bg-rose-100 text-rose-900 ring-rose-300',
  'bg-teal-100 text-teal-900 ring-teal-300',
  'bg-orange-100 text-orange-900 ring-orange-300',
  'bg-indigo-100 text-indigo-900 ring-indigo-300',
];

/** Assigns tints in order of first appearance so the courses of one room never share one. */
export function tintsFor(codes: string[]): Map<string, string> {
  const map = new Map<string, string>();
  codes.forEach((code, i) => map.set(code, TINTS[i % TINTS.length]));
  return map;
}

export function tintOf(code: string): string {
  let h = 0;
  for (const ch of code) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return TINTS[h % TINTS.length];
}
