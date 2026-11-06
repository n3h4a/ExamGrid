import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { auditApi } from '../../api/endpoints';
import { Card, ErrorBanner, PageHeader, Pagination, Spinner, Table, Td, TextInput, Th } from '../../components/ui';
import { formatDateTime } from '../../lib/format';

export function AuditPage() {
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const log = useQuery({
    queryKey: ['audit', actor, action, page],
    queryFn: () => auditApi.search({ actor: actor || undefined, action: action || undefined, page }),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <PageHeader title="Audit log" description="Every import, run, approval, publication and edit, with who did it and the request reference." />
      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <TextInput placeholder="Actor (username)" aria-label="Actor" value={actor} onChange={(e) => { setActor(e.target.value); setPage(0); }} className="max-w-xs" />
          <TextInput placeholder="Action prefix, e.g. run. or import." aria-label="Action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} className="max-w-xs" />
        </div>
        {log.isPending ? <Spinner /> : log.isError ? <ErrorBanner error={log.error} /> : (
          <>
            <Table>
              <thead><tr><Th>When</Th><Th>Actor</Th><Th>Action</Th><Th>Entity</Th><Th>Details</Th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {log.data.items.map((a) => (
                  <tr key={a.id} className="align-top">
                    <Td>{formatDateTime(a.occurredAt)}</Td>
                    <Td className="font-medium">{a.actor}</Td>
                    <Td className="font-mono text-xs">{a.action}</Td>
                    <Td className="font-mono text-xs">{a.entityType ? `${a.entityType}:${a.entityId ?? ''}` : '—'}</Td>
                    <Td className="max-w-md whitespace-normal">
                      <code className="block break-all text-xs text-slate-600">{JSON.stringify(a.details)}</code>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} totalPages={log.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
