import { useState, type FormEvent } from 'react';
import { SESSIONS, type Course, type CourseRequest, type Session } from '../../api/types';
import { Button, Checkbox, ErrorBanner, Field, TextInput } from '../../components/ui';
import { capitalise } from '../../lib/format';

export function CourseForm({ initial, onSubmit, onCancel, busy, error }: {
  initial?: Course;
  onSubmit: (body: CourseRequest) => void;
  onCancel: () => void;
  busy: boolean;
  error: unknown;
}) {
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [department, setDepartment] = useState(initial?.department ?? '');
  const [hard, setHard] = useState(initial?.hard ?? false);
  const [sessions, setSessions] = useState<Session[]>(initial?.allowedSessions ?? []);

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({ code, name, department, hard, allowedSessions: sessions });
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <ErrorBanner error={error} title="Could not save the course" />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput label="Code" required disabled={!!initial} value={code} onChange={(e) => setCode(e.target.value)} hint={initial ? 'Codes cannot be changed.' : 'e.g. CS301'} />
        <TextInput label="Department" value={department} onChange={(e) => setDepartment(e.target.value)} />
      </div>
      <TextInput label="Title" required value={name} onChange={(e) => setName(e.target.value)} />
      <Checkbox label="Hard paper (kept at least the minimum number of days from other hard papers)" checked={hard} onChange={(e) => setHard(e.target.checked)} />
      <Field label="Allowed sessions" hint="Leave all unticked for any session, e.g. tick only Afternoon for labs.">
        <div className="flex gap-4">
          {SESSIONS.map((s) => (
            <Checkbox
              key={s}
              label={capitalise(s)}
              checked={sessions.includes(s)}
              onChange={(e) => setSessions(e.target.checked ? [...sessions, s] : sessions.filter((x) => x !== s))}
            />
          ))}
        </div>
      </Field>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={busy}>
          Save course
        </Button>
      </div>
    </form>
  );
}
