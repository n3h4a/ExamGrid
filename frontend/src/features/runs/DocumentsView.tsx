import { useQuery } from '@tanstack/react-query';
import { FileDown } from 'lucide-react';
import { useState } from 'react';
import { download } from '../../api/client';
import { runsApi } from '../../api/endpoints';
import { Button, ErrorBanner, Spinner, TextInput } from '../../components/ui';
import { formatBytes, formatSlot } from '../../lib/format';

/** PDFs generated at publication: timetable, roster, seating charts and hall tickets. */
export function DocumentsView({ runId }: { runId: number }) {
  const docs = useQuery({ queryKey: ['run', runId, 'documents'], queryFn: () => runsApi.documents(runId) });
  const [roll, setRoll] = useState('');
  const [error, setError] = useState<unknown>(null);
  if (docs.isPending) return <Spinner />;
  if (docs.isError) return <ErrorBanner error={docs.error} />;
  const get = (id: number, name: string) => download(`/api/schedule-runs/${runId}/documents/${id}`, name).catch(setError);
  const general = docs.data.filter((d) => d.kind === 'TIMETABLE' || d.kind === 'INVIGILATION');
  const seating = docs.data.filter((d) => d.kind === 'SEATING');
  const tickets = docs.data.filter((d) => d.kind === 'HALL_TICKET');
  const ticket = tickets.find((t) => t.ref === roll.trim().toUpperCase());

  return (
    <div className="space-y-6">
      <ErrorBanner error={error} title="Download failed" />
      <div className="flex flex-wrap gap-2">
        {general.map((d) => (
          <Button key={d.id} variant="secondary" onClick={() => get(d.id, `${d.kind.toLowerCase()}.pdf`)}>
            <FileDown className="size-4" /> {d.kind === 'TIMETABLE' ? 'Timetable' : 'Invigilation roster'} ({formatBytes(d.sizeBytes)})
          </Button>
        ))}
      </div>
      <div>
        <h3 className="mb-2 text-sm font-semibold">Seating charts ({seating.length} slots)</h3>
        <div className="flex flex-wrap gap-2">
          {seating.map((d) => (
            <Button key={d.id} variant="ghost" className="ring-1 ring-slate-200" onClick={() => get(d.id, `seating-${d.ref}.pdf`)}>
              {formatSlot(d.ref)}
            </Button>
          ))}
        </div>
      </div>
      <div>
        <h3 className="mb-2 text-sm font-semibold">Hall tickets ({tickets.length.toLocaleString()} students)</h3>
        <div className="flex max-w-md gap-2">
          <TextInput placeholder="Roll number" aria-label="Hall ticket roll number" value={roll} onChange={(e) => setRoll(e.target.value)} />
          <Button disabled={!ticket} onClick={() => ticket && get(ticket.id, `hall-ticket-${ticket.ref}.pdf`)}>Download</Button>
        </div>
      </div>
    </div>
  );
}
