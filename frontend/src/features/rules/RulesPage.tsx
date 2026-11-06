import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Lock, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { catalogApi } from '../../api/endpoints';
import { Badge, Button, Card, EmptyState, ErrorBanner, PageHeader, SelectInput, Table, Td, TextInput, Th } from '../../components/ui';
import { formatDateTime, formatSlot } from '../../lib/format';

/** Manual overrides the solver must respect: pinned exams and common papers sharing a slot. */
export function RulesPage() {
  const queryClient = useQueryClient();
  const locks = useQuery({ queryKey: ['locks'], queryFn: catalogApi.locks });
  const groups = useQuery({ queryKey: ['groups'], queryFn: catalogApi.groups });
  const slots = useQuery({ queryKey: ['slots'], queryFn: catalogApi.slots });
  const [course, setCourse] = useState('');
  const [slot, setSlot] = useState('');
  const [groupName, setGroupName] = useState('');
  const [groupCourses, setGroupCourses] = useState('');
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['locks'] });
    void queryClient.invalidateQueries({ queryKey: ['groups'] });
    void queryClient.invalidateQueries({ queryKey: ['catalog-summary'] });
  };

  const lock = useMutation({ mutationFn: () => catalogApi.lock(course.trim(), slot), onSuccess: () => { setCourse(''); refresh(); } });
  const unlock = useMutation({ mutationFn: (code: string) => catalogApi.unlock(code), onSuccess: refresh });
  const createGroup = useMutation({
    mutationFn: () => catalogApi.createGroup(groupName.trim(), groupCourses.split(/[,;\s]+/).filter(Boolean)),
    onSuccess: () => { setGroupName(''); setGroupCourses(''); refresh(); },
  });
  const deleteGroup = useMutation({ mutationFn: (id: number) => catalogApi.deleteGroup(id), onSuccess: refresh });

  return (
    <>
      <PageHeader title="Locks & same-slot groups" description="Rules from the real world. The solver treats both as hard constraints and explains if they make a timetable impossible." />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Locked exams">
          <form onSubmit={(e: FormEvent) => { e.preventDefault(); lock.mutate(); }} className="mb-4 grid gap-3 sm:grid-cols-[1fr_1.5fr_auto] sm:items-end">
            <TextInput label="Course" placeholder="CS301" required value={course} onChange={(e) => setCourse(e.target.value)} />
            <SelectInput label="Slot" required value={slot} onChange={(e) => setSlot(e.target.value)}>
              <option value="">Choose a slot…</option>
              {slots.data?.map((s) => <option key={s.id} value={s.key}>{formatSlot(s.key)}</option>)}
            </SelectInput>
            <Button type="submit" loading={lock.isPending}><Lock className="size-4" /> Lock</Button>
          </form>
          <ErrorBanner error={lock.error} title="Could not lock" />
          {locks.data && locks.data.length > 0 ? (
            <Table>
              <thead><tr><Th>Course</Th><Th>Slot</Th><Th>By</Th><Th /></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {locks.data.map((l) => (
                  <tr key={l.courseCode}>
                    <Td className="font-mono font-medium">{l.courseCode}</Td>
                    <Td>{formatSlot(l.slotKey)}</Td>
                    <Td className="text-xs text-slate-500">{l.lockedBy} · {formatDateTime(l.lockedAt)}</Td>
                    <Td className="text-right">
                      <Button variant="ghost" aria-label={`Unlock ${l.courseCode}`} onClick={() => unlock.mutate(l.courseCode)}><Trash2 className="size-4" /></Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <EmptyState title="No locked exams">Lock a course to pin its exam to a slot, e.g. when a paper is set by an external board.</EmptyState>
          )}
        </Card>
        <Card title="Same-slot groups">
          <form onSubmit={(e: FormEvent) => { e.preventDefault(); createGroup.mutate(); }} className="mb-4 grid gap-3 sm:grid-cols-[1fr_1.5fr_auto] sm:items-end">
            <TextInput label="Name" placeholder="Engineering Maths II" required value={groupName} onChange={(e) => setGroupName(e.target.value)} />
            <TextInput label="Courses" placeholder="CS201, EC201, ME201" required value={groupCourses} onChange={(e) => setGroupCourses(e.target.value)} />
            <Button type="submit" loading={createGroup.isPending}>Add group</Button>
          </form>
          <ErrorBanner error={createGroup.error} title="Could not create group" />
          {groups.data && groups.data.length > 0 ? (
            <ul className="divide-y divide-slate-100">
              {groups.data.map((g) => (
                <li key={g.id} className="flex items-center justify-between gap-3 py-2">
                  <div>
                    <p className="text-sm font-medium">{g.name}</p>
                    <div className="mt-1 flex flex-wrap gap-1">{g.courses.map((c) => <Badge key={c}>{c}</Badge>)}</div>
                  </div>
                  <Button variant="ghost" aria-label={`Delete group ${g.name}`} onClick={() => deleteGroup.mutate(g.id)}><Trash2 className="size-4" /></Button>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="No groups">Group common papers taken by several branches so they are examined together.</EmptyState>
          )}
        </Card>
      </div>
    </>
  );
}
