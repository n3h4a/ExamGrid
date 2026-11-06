import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/client';
import { userApi } from '../../api/endpoints';
import type { Role, User } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { Badge, Button, Card, ErrorBanner, Modal, PageHeader, Pagination, SelectInput, Spinner, Table, Td, TextInput, Th } from '../../components/ui';
import { formatDateTime } from '../../lib/format';

const ROLE_LABEL: Record<Role, string> = { EXAM_CELL: 'Exam cell', FACULTY: 'Faculty', STUDENT: 'Student' };

function NewUserForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ username: '', displayName: '', password: '', role: 'EXAM_CELL' as Role, studentRoll: '', facultyCode: '' });
  const create = useMutation({
    mutationFn: () =>
      userApi.create({
        ...form,
        studentRoll: form.role === 'STUDENT' ? form.studentRoll : undefined,
        facultyCode: form.role === 'FACULTY' ? form.facultyCode : undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      onDone();
    },
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  return (
    <form onSubmit={(e: FormEvent) => { e.preventDefault(); create.mutate(); }} className="space-y-4">
      <ErrorBanner error={create.error} title="Could not create the account" />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput label="Username" required value={form.username} onChange={set('username')} />
        <TextInput label="Display name" required value={form.displayName} onChange={set('displayName')} />
        <TextInput label="Initial password" type="password" required minLength={8} value={form.password} onChange={set('password')} hint="At least 8 characters" />
        <SelectInput label="Role" value={form.role} onChange={set('role')}>
          {(Object.keys(ROLE_LABEL) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
        </SelectInput>
        {form.role === 'STUDENT' && <TextInput label="Roll number" required value={form.studentRoll} onChange={set('studentRoll')} />}
        {form.role === 'FACULTY' && <TextInput label="Staff code" required value={form.facultyCode} onChange={set('facultyCode')} />}
      </div>
      <div className="flex justify-end"><Button type="submit" loading={create.isPending}>Create account</Button></div>
    </form>
  );
}

export function UsersPage() {
  const queryClient = useQueryClient();
  const { user: me } = useAuth();
  const [role, setRole] = useState<Role | ''>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const users = useQuery({ queryKey: ['users', role, q, page], queryFn: () => userApi.list(role || undefined, q, page), placeholderData: keepPreviousData });
  const toggle = useMutation({
    mutationFn: (u: User) => userApi.update(u.id, { enabled: !u.enabled }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
    onError: (e) => window.alert(errorMessage(e)),
  });
  return (
    <>
      <PageHeader title="Users" description="Accounts for the exam cell, faculty and students. Publishing a timetable needs two exam-cell accounts." actions={<Button onClick={() => setCreating(true)}><Plus className="size-4" /> New account</Button>} />
      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <TextInput placeholder="Search…" aria-label="Search users" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} className="max-w-xs" />
          <SelectInput aria-label="Role filter" value={role} onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0); }} className="max-w-[12rem]">
            <option value="">All roles</option>
            {(Object.keys(ROLE_LABEL) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </SelectInput>
        </div>
        {users.isPending ? <Spinner /> : users.isError ? <ErrorBanner error={users.error} /> : (
          <>
            <Table>
              <thead><tr><Th>Username</Th><Th>Name</Th><Th>Role</Th><Th>Linked to</Th><Th>Last login</Th><Th>Status</Th><Th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {users.data.items.map((u) => (
                  <tr key={u.id}>
                    <Td className="font-medium">{u.username}</Td>
                    <Td>{u.displayName}</Td>
                    <Td><Badge tone={u.role === 'EXAM_CELL' ? 'violet' : u.role === 'FACULTY' ? 'blue' : 'slate'}>{ROLE_LABEL[u.role]}</Badge></Td>
                    <Td className="font-mono text-xs">{u.studentRoll ?? u.facultyCode ?? '—'}</Td>
                    <Td>{formatDateTime(u.lastLoginAt)}</Td>
                    <Td>{u.enabled ? <Badge tone="green">active</Badge> : <Badge tone="red">disabled</Badge>}</Td>
                    <Td className="text-right">
                      {u.id !== me?.id && <Button variant="ghost" onClick={() => toggle.mutate(u)}>{u.enabled ? 'Disable' : 'Enable'}</Button>}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={users.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
      <Modal open={creating} title="New account" onClose={() => setCreating(false)}>
        {creating && <NewUserForm onDone={() => setCreating(false)} />}
      </Modal>
    </>
  );
}
