import { useMutation, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/client';
import { publicApi } from '../../api/endpoints';
import { Button, ErrorBanner, TextInput } from '../../components/ui';
import { formatDate } from '../../lib/format';
import { ExamList } from './ExamList';

/** Public, no-login "find my seat" page for result day. Shows rooms and seats, never names. */
export function SeatFinderPage() {
  const [roll, setRoll] = useState('');
  const status = useQuery({ queryKey: ['publication-status'], queryFn: publicApi.status });
  const lookup = useMutation({ mutationFn: (r: string) => publicApi.seat(r.trim()) });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (roll.trim()) lookup.mutate(roll);
  }

  const notFound = lookup.error instanceof ApiError && lookup.error.status === 404;
  const limited = lookup.error instanceof ApiError && lookup.error.status === 429;

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-brand-900 text-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-4">
          <Link to="/seat" className="flex items-center gap-2">
            <img src="/favicon.svg" alt="" className="size-7" />
            <span className="text-lg font-semibold">ExamGrid</span>
          </Link>
          <Link to="/login" className="text-sm text-brand-100 hover:text-white">Sign in</Link>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="text-3xl font-semibold tracking-tight">Find my seat</h1>
        <p className="mt-2 text-slate-600">
          {status.data?.published
            ? `Exams run from ${formatDate(status.data.firstExam)} to ${formatDate(status.data.lastExam)}.`
            : 'The exam timetable has not been published yet.'}
        </p>
        <form onSubmit={submit} className="mt-6 flex gap-2">
          <TextInput placeholder="Roll number, e.g. 21CS045" aria-label="Roll number" value={roll} onChange={(e) => setRoll(e.target.value)} className="max-w-xs text-base uppercase" />
          <Button type="submit" loading={lookup.isPending}><Search className="size-4" /> Find</Button>
        </form>
        <div className="mt-8">
          {notFound && <p className="text-sm text-slate-600">No exams found for “{roll.toUpperCase()}”. Check the roll number, or the timetable may not be published yet.</p>}
          {limited && <p className="text-sm text-amber-700">Too many searches from this network. Please wait a minute and try again.</p>}
          {lookup.isError && !notFound && !limited && <ErrorBanner error={lookup.error} />}
          {lookup.data && (
            <>
              <p className="mb-3 text-sm text-slate-600"><span className="font-mono font-semibold">{lookup.data.roll}</span> · {lookup.data.exams.length} exam(s)</p>
              <ExamList exams={lookup.data.exams} />
              <p className="mt-6 text-sm text-slate-500">Sign in to download your hall ticket.</p>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
