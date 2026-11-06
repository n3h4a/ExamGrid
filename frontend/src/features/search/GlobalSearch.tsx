import { useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { catalogApi } from '../../api/endpoints';
import type { SearchHit } from '../../api/types';

const ROUTES: Record<string, string> = {
  student: '/admin/students',
  course: '/admin/courses',
  faculty: '/admin/faculty',
  room: '/admin/rooms',
};

/** Debounced search box over students, courses, faculty and rooms (trigram-backed API). */
export function GlobalSearch() {
  const [text, setText] = useState('');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const t = window.setTimeout(() => setQuery(text.trim()), 250);
    return () => window.clearTimeout(t);
  }, [text]);

  const { data } = useQuery({
    queryKey: ['search', query],
    queryFn: () => catalogApi.search(query),
    enabled: query.length >= 2,
    staleTime: 30_000,
  });

  const groups: [string, SearchHit[]][] = data
    ? [
        ['Students', data.students],
        ['Courses', data.courses],
        ['Faculty', data.faculty],
        ['Rooms', data.rooms],
      ]
    : [];
  const total = groups.reduce((n, [, hits]) => n + hits.length, 0);

  return (
    <div className="relative max-w-md">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
      <input
        type="search"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => window.setTimeout(() => setOpen(false), 150)}
        placeholder="Search roll number, course, room, faculty…"
        aria-label="Search"
        className="w-full rounded-md border-0 bg-slate-100 py-2 pl-9 pr-3 text-sm placeholder:text-slate-500 focus:bg-white focus:ring-2 focus:ring-brand-500"
      />
      {open && query.length >= 2 && data && (
        <div className="absolute left-0 right-0 top-11 z-30 max-h-96 overflow-y-auto rounded-lg bg-white py-2 shadow-lg ring-1 ring-slate-200">
          {total === 0 && <p className="px-4 py-2 text-sm text-slate-500">No matches for “{query}”.</p>}
          {groups
            .filter(([, hits]) => hits.length > 0)
            .map(([label, hits]) => (
              <div key={label} className="py-1">
                <p className="px-4 text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
                {hits.map((hit) => (
                  <Link
                    key={`${hit.type}-${hit.id}`}
                    to={`${ROUTES[hit.type]}?q=${encodeURIComponent(hit.code)}`}
                    className="flex items-baseline gap-2 px-4 py-1.5 text-sm hover:bg-slate-50"
                  >
                    <span className="font-mono font-medium text-slate-900">{hit.code}</span>
                    <span className="truncate text-slate-500">{hit.label}</span>
                  </Link>
                ))}
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
