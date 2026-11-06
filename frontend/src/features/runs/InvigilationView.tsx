import { useQuery } from '@tanstack/react-query';
import { runsApi } from '../../api/endpoints';
import { ErrorBanner, Spinner, Table, Td, Th } from '../../components/ui';
import { formatSlot } from '../../lib/format';

/** Duties per faculty member: the min-heap keeps these counts within a duty or two. */
export function InvigilationView({ runId }: { runId: number }) {
  const duties = useQuery({ queryKey: ['run', runId, 'invigilation'], queryFn: () => runsApi.invigilation(runId) });
  if (duties.isPending) return <Spinner />;
  if (duties.isError) return <ErrorBanner error={duties.error} />;
  const byFaculty = new Map<string, { name: string; slots: string[] }>();
  for (const d of duties.data) {
    const entry = byFaculty.get(d.facultyCode) ?? { name: d.facultyName, slots: [] };
    entry.slots.push(`${formatSlot(d.slot)} · ${d.roomCode}`);
    byFaculty.set(d.facultyCode, entry);
  }
  const rows = [...byFaculty.entries()].sort((a, b) => b[1].slots.length - a[1].slots.length || a[0].localeCompare(b[0]));
  return (
    <Table>
      <thead><tr><Th>Staff</Th><Th>Name</Th><Th>Duties</Th><Th>Where and when</Th></tr></thead>
      <tbody className="divide-y divide-slate-100">
        {rows.map(([code, f]) => (
          <tr key={code} className="align-top">
            <Td className="font-mono font-medium">{code}</Td>
            <Td>{f.name}</Td>
            <Td className="tabular-nums">{f.slots.length}</Td>
            <Td className="whitespace-normal text-xs text-slate-600">{f.slots.join(' · ')}</Td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
