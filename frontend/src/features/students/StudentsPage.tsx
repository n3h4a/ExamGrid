import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { catalogApi } from '../../api/endpoints';
import { Badge, Card, EmptyState, ErrorBanner, Modal, PageHeader, Pagination, Spinner, Table, Td, TextInput, Th } from '../../components/ui';
import { useSearchParam } from '../../lib/useSearchParam';

export function StudentsPage() {
  const [q, setQ] = useSearchParam('q');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const students = useQuery({
    queryKey: ['students', q, page],
    queryFn: () => catalogApi.students(q, page),
    placeholderData: keepPreviousData,
  });
  const detail = useQuery({
    queryKey: ['student', selected],
    queryFn: () => catalogApi.student(selected!),
    enabled: selected !== null,
  });

  return (
    <>
      <PageHeader title="Students" description="Students are created by enrolment imports. Click a student to see their courses." />
      <Card>
        <div className="mb-4 max-w-sm">
          <TextInput placeholder="Search roll number or name…" aria-label="Search students" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        {students.isPending ? (
          <Spinner />
        ) : students.isError ? (
          <ErrorBanner error={students.error} />
        ) : students.data.items.length === 0 ? (
          <EmptyState title="No students">Import an enrolment file to add students.</EmptyState>
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Roll</Th>
                  <Th>Name</Th>
                  <Th>Dept</Th>
                  <Th>Email</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {students.data.items.map((s) => (
                  <tr key={s.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setSelected(s.id)}>
                    <Td className="font-mono font-medium">{s.roll}</Td>
                    <Td>{s.name || '—'}</Td>
                    <Td>{s.department || '—'}</Td>
                    <Td>{s.email ?? '—'}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <p className="mt-3 text-xs text-slate-500">{students.data.totalItems.toLocaleString()} students</p>
            <Pagination page={page} totalPages={students.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <Modal open={selected !== null} title={detail.data ? `${detail.data.student.roll} · ${detail.data.student.name}` : 'Student'} onClose={() => setSelected(null)}>
        {detail.isPending ? (
          <Spinner />
        ) : detail.data ? (
          <div className="space-y-2 text-sm">
            <p className="text-slate-600">Department {detail.data.student.department || '—'}</p>
            <p className="font-medium">Enrolled in {detail.data.courses.length} course(s)</p>
            <div className="flex flex-wrap gap-1.5">
              {detail.data.courses.map((c) => (
                <Badge key={c}>{c}</Badge>
              ))}
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
