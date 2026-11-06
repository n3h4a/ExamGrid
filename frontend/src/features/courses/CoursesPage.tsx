import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { catalogApi } from '../../api/endpoints';
import type { Course, CourseRequest } from '../../api/types';
import { Badge, Button, Card, EmptyState, ErrorBanner, Modal, PageHeader, Pagination, Spinner, Table, Td, TextInput, Th } from '../../components/ui';
import { capitalise, formatNumber } from '../../lib/format';
import { useSearchParam } from '../../lib/useSearchParam';
import { CourseForm } from './CourseForm';

export function CoursesPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useSearchParam('q');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Course | 'new' | null>(null);
  const courses = useQuery({
    queryKey: ['courses', q, page],
    queryFn: () => catalogApi.courses(q, page),
    placeholderData: keepPreviousData,
  });

  const save = useMutation({
    mutationFn: (body: CourseRequest) =>
      editing && editing !== 'new' ? catalogApi.updateCourse(editing.id, body) : catalogApi.createCourse(body),
    onSuccess: () => {
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['courses'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog-summary'] });
    },
  });
  const remove = useMutation({
    mutationFn: (c: Course) => catalogApi.deleteCourse(c.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: (e) => window.alert(errorMessage(e)),
  });

  return (
    <>
      <PageHeader
        title="Courses"
        description="Every course with candidates gets an exam. Session rules and hard-paper flags guide the solver."
        actions={
          <Button onClick={() => { save.reset(); setEditing('new'); }}>
            <Plus className="size-4" /> New course
          </Button>
        }
      />
      <Card>
        <div className="mb-4 max-w-sm">
          <TextInput placeholder="Search code or title…" aria-label="Search courses" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        {courses.isPending ? (
          <Spinner />
        ) : courses.isError ? (
          <ErrorBanner error={courses.error} />
        ) : courses.data.items.length === 0 ? (
          <EmptyState title="No courses">Import a course list or create one.</EmptyState>
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Code</Th>
                  <Th>Title</Th>
                  <Th>Dept</Th>
                  <Th>Candidates</Th>
                  <Th>Rules</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {courses.data.items.map((c) => (
                  <tr key={c.id}>
                    <Td className="font-mono font-medium">{c.code}</Td>
                    <Td className="whitespace-normal">{c.name}</Td>
                    <Td>{c.department || '—'}</Td>
                    <Td className="tabular-nums">{formatNumber(c.candidates)}</Td>
                    <Td className="space-x-1">
                      {c.hard && <Badge tone="violet">hard</Badge>}
                      {c.allowedSessions.map((s) => (
                        <Badge key={s} tone="blue">{capitalise(s)} only</Badge>
                      ))}
                    </Td>
                    <Td className="text-right">
                      <Button variant="ghost" aria-label={`Edit ${c.code}`} onClick={() => { save.reset(); setEditing(c); }}>
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        aria-label={`Delete ${c.code}`}
                        onClick={() => window.confirm(`Delete ${c.code} and its ${c.candidates} enrolment(s)?`) && remove.mutate(c)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={courses.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <Modal open={editing !== null} title={editing === 'new' ? 'New course' : `Edit ${editing?.code ?? ''}`} onClose={() => setEditing(null)}>
        {editing !== null && (
          <CourseForm
            initial={editing === 'new' ? undefined : editing}
            onSubmit={(body) => save.mutate(body)}
            onCancel={() => setEditing(null)}
            busy={save.isPending}
            error={save.error}
          />
        )}
      </Modal>
    </>
  );
}
