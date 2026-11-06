import { useQuery } from '@tanstack/react-query';
import { BookOpen, CalendarDays, CheckCircle2, Circle, DoorOpen, GraduationCap, UserSquare } from 'lucide-react';
import { Link } from 'react-router';
import { catalogApi, importApi, publicApi } from '../../api/endpoints';
import { Badge, Card, ErrorBanner, PageHeader, Spinner, StatTile, Table, Td, Th } from '../../components/ui';
import { formatDate, formatDateTime, formatNumber } from '../../lib/format';
import { ImportStatusBadge } from '../imports/ImportStatusBadge';

export function DashboardPage() {
  const summary = useQuery({ queryKey: ['catalog-summary'], queryFn: catalogApi.summary });
  const imports = useQuery({ queryKey: ['imports', 0], queryFn: () => importApi.list(0, 5) });
  const status = useQuery({ queryKey: ['publication-status'], queryFn: publicApi.status });

  if (summary.isPending) return <Spinner />;
  if (summary.isError) return <ErrorBanner error={summary.error} />;
  const s = summary.data;

  const checklist = [
    { done: s.courses > 0, label: 'Courses imported', to: '/admin/imports' },
    { done: s.enrolments > 0, label: 'Enrolments imported', to: '/admin/imports' },
    { done: s.activeRooms > 0, label: 'Rooms with seat layouts', to: '/admin/rooms' },
    { done: s.slots > 0, label: 'Exam slots generated', to: '/admin/slots' },
    { done: s.faculty > 0, label: 'Faculty available to invigilate', to: '/admin/faculty' },
  ];
  const ready = checklist.slice(0, 4).every((c) => c.done);

  return (
    <>
      <PageHeader title="Dashboard" description="What the solver will work with, and what is live for students." />
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
        <StatTile label="Students" value={formatNumber(s.students)} icon={<GraduationCap className="size-4" />} />
        <StatTile label="Courses" value={formatNumber(s.courses)} icon={<BookOpen className="size-4" />} />
        <StatTile label="Enrolments" value={formatNumber(s.enrolments)} hint="student × course" />
        <StatTile label="Rooms" value={formatNumber(s.activeRooms)} hint={`${formatNumber(s.seats)} seats`} icon={<DoorOpen className="size-4" />} />
        <StatTile label="Exam slots" value={formatNumber(s.slots)} icon={<CalendarDays className="size-4" />} />
        <StatTile label="Faculty" value={formatNumber(s.faculty)} icon={<UserSquare className="size-4" />} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card title="Ready to schedule?" className="lg:col-span-1">
          <ul className="space-y-2">
            {checklist.map((c) => (
              <li key={c.label}>
                <Link to={c.to} className="flex items-center gap-2 text-sm hover:underline">
                  {c.done ? <CheckCircle2 className="size-4 text-emerald-600" /> : <Circle className="size-4 text-slate-300" />}
                  <span className={c.done ? 'text-slate-800' : 'text-slate-500'}>{c.label}</span>
                </Link>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-sm text-slate-600">
            {ready ? 'All inputs are in place. Locks and same-slot groups are optional.' : 'Complete the steps above before generating a timetable.'}
          </p>
          <p className="mt-2 text-xs text-slate-500">
            {s.locks} locked course(s) · {s.sameSlotGroups} same-slot group(s)
          </p>
        </Card>

        <Card title="Published timetable" className="lg:col-span-2">
          {status.isPending ? (
            <Spinner />
          ) : status.data?.published ? (
            <div className="space-y-1 text-sm">
              <p>
                <Badge tone="green">Live</Badge> <span className="ml-2">Published {formatDateTime(status.data.publishedAt)}</span>
              </p>
              <p className="text-slate-600">
                {status.data.exams} exams from {formatDate(status.data.firstExam)} to {formatDate(status.data.lastExam)}. Students can find their
                seats at <Link className="text-brand-600 underline" to="/seat">/seat</Link>.
              </p>
            </div>
          ) : (
            <p className="text-sm text-slate-600">Nothing is published yet. A plan goes live after two exam-cell members approve it.</p>
          )}
        </Card>
      </div>

      <Card title="Recent imports" className="mt-6" actions={<Link to="/admin/imports" className="text-sm text-brand-600 hover:underline">All imports</Link>}>
        {imports.data && imports.data.items.length > 0 ? (
          <Table>
            <thead>
              <tr>
                <Th>When</Th>
                <Th>Type</Th>
                <Th>File</Th>
                <Th>Rows</Th>
                <Th>Status</Th>
                <Th>By</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {imports.data.items.map((b) => (
                <tr key={b.id}>
                  <Td>{formatDateTime(b.createdAt)}</Td>
                  <Td className="capitalize">{b.kind.toLowerCase()}</Td>
                  <Td className="font-mono text-xs">{b.filename}</Td>
                  <Td className="tabular-nums">{formatNumber(b.totalRows)}</Td>
                  <Td>
                    <ImportStatusBadge status={b.status} dryRun={b.dryRun} />
                  </Td>
                  <Td>{b.createdBy}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          <p className="text-sm text-slate-500">No files uploaded yet.</p>
        )}
      </Card>
    </>
  );
}
