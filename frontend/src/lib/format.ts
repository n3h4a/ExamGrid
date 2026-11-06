const dateFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return dateFormat.format(new Date(`${iso.slice(0, 10)}T00:00:00`));
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFormat.format(new Date(iso)) : '—';
}

export function formatNumber(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : n.toLocaleString('en-IN');
}

export function capitalise(s: string): string {
  return s.charAt(0) + s.slice(1).toLowerCase();
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** "2026-12-07-AFTERNOON" → "Mon 7 Dec 2026 · Afternoon" */
export function formatSlot(key: string): string {
  const date = key.slice(0, 10);
  const session = key.slice(11);
  return `${formatDate(date)} · ${capitalise(session)}`;
}
