import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { catalogApi } from '../../api/endpoints';
import type { Faculty, FacultyRequest } from '../../api/types';
import { Badge, Button, Card, EmptyState, ErrorBanner, Modal, PageHeader, Pagination, Spinner, Table, Td, TextInput, Th } from '../../components/ui';
import { useSearchParam } from '../../lib/useSearchParam';

const split = (s: string) => s.split(/[,;\s]+/).map((x) => x.trim()).filter(Boolean);

function FacultyForm({ initial, onSubmit, onCancel, busy, error }: {
  initial?: Faculty;
  onSubmit: (b: FacultyRequest) => void;
  onCancel: () => void;
  busy: boolean;
  error: unknown;
}) {
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [department, setDepartment] = useState(initial?.department ?? '');
  const [teaches, setTeaches] = useState((initial?.teaches ?? []).join(', '));
  const [away, setAway] = useState((initial?.unavailableSlots ?? []).join(', '));
  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({ code, name, email: email || undefined, department, teaches: split(teaches), unavailableSlots: split(away) });
  }
  return (
    <form onSubmit={submit} className="space-y-4">
      <ErrorBanner error={error} title="Could not save" />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput label="Staff code" required disabled={!!initial} value={code} onChange={(e) => setCode(e.target.value)} />
        <TextInput label="Name" required value={name} onChange={(e) => setName(e.target.value)} />
        <TextInput label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextInput label="Department" value={department} onChange={(e) => setDepartment(e.target.value)} />
      </div>
      <TextInput label="Teaches" value={teaches} onChange={(e) => setTeaches(e.target.value)} hint="Course codes, comma separated. Never assigned to invigilate these papers." />
      <TextInput
        label="Unavailable"
        value={away}
        onChange={(e) => setAway(e.target.value)}
        hint="Dates (2026-12-14, whole day) or slots (2026-12-14-AFTERNOON), comma separated."
      />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={busy}>Save</Button>
      </div>
    </form>
  );
}

export function FacultyPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useSearchParam('q');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Faculty | 'new' | null>(null);
  const faculty = useQuery({ queryKey: ['faculty', q, page], queryFn: () => catalogApi.faculty(q, page), placeholderData: keepPreviousData });
  const save = useMutation({
    mutationFn: (b: FacultyRequest) => (editing && editing !== 'new' ? catalogApi.updateFaculty(editing.id, b) : catalogApi.createFaculty(b)),
    onSuccess: () => {
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['faculty'] });
    },
  });
  const remove = useMutation({
    mutationFn: (f: Faculty) => catalogApi.deleteFaculty(f.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['faculty'] }),
    onError: (e) => window.alert(errorMessage(e)),
  });

  return (
    <>
      <PageHeader
        title="Faculty"
        description="Staff available to invigilate. Duties are shared out fairly, never in a slot they are away and never for a paper they teach."
        actions={<Button onClick={() => { save.reset(); setEditing('new'); }}><Plus className="size-4" /> New faculty</Button>}
      />
      <Card>
        <div className="mb-4 max-w-sm">
          <TextInput placeholder="Search code or name…" aria-label="Search faculty" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        {faculty.isPending ? (
          <Spinner />
        ) : faculty.isError ? (
          <ErrorBanner error={faculty.error} />
        ) : faculty.data.items.length === 0 ? (
          <EmptyState title="No faculty">Import a faculty list or add staff one by one.</EmptyState>
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Code</Th>
                  <Th>Name</Th>
                  <Th>Dept</Th>
                  <Th>Teaches</Th>
                  <Th>Unavailable</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {faculty.data.items.map((f) => (
                  <tr key={f.id}>
                    <Td className="font-mono font-medium">{f.code}</Td>
                    <Td>{f.name}</Td>
                    <Td>{f.department || '—'}</Td>
                    <Td className="space-x-1">{f.teaches.map((c) => <Badge key={c}>{c}</Badge>)}</Td>
                    <Td className="text-xs">{f.unavailableSlots.length ? `${f.unavailableSlots.length} slot(s)` : '—'}</Td>
                    <Td className="text-right">
                      <Button variant="ghost" onClick={() => { save.reset(); setEditing(f); }}>Edit</Button>
                      <Button variant="ghost" onClick={() => window.confirm(`Delete ${f.code}?`) && remove.mutate(f)}>Delete</Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={faculty.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <Modal open={editing !== null} title={editing === 'new' ? 'New faculty member' : 'Edit faculty member'} onClose={() => setEditing(null)}>
        {editing !== null && (
          <FacultyForm initial={editing === 'new' ? undefined : editing} onSubmit={(b) => save.mutate(b)} onCancel={() => setEditing(null)} busy={save.isPending} error={save.error} />
        )}
      </Modal>
    </>
  );
}
