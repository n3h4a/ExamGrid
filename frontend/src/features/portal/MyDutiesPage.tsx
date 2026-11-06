import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { portalApi } from '../../api/endpoints';
import { Card, EmptyState, ErrorBanner, PageHeader, Spinner, Table, Td, Th } from '../../components/ui';
import { capitalise, formatDate } from '../../lib/format';

export function MyDutiesPage() {
  const duties = useQuery({ queryKey: ['my-duties'], queryFn: portalApi.myDuties, retry: false });
  if (duties.isPending) return <Spinner />;
  if (duties.error instanceof ApiError && duties.error.status === 404) {
    return (
      <>
        <PageHeader title="My invigilation duties" />
        <EmptyState title="The timetable is not published yet">Your duties appear here once the exam cell publishes the plan.</EmptyState>
      </>
    );
  }
  if (duties.isError) return <ErrorBanner error={duties.error} />;
  return (
    <>
      <PageHeader title="My invigilation duties" description={`${duties.data.length} duty slot(s) in the published timetable.`} />
      {duties.data.length === 0 ? (
        <EmptyState title="No duties assigned to you" />
      ) : (
        <Card>
          <Table>
            <thead><tr><Th>Date</Th><Th>Session</Th><Th>Room</Th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {duties.data.map((d) => (
                <tr key={d.slot + d.roomCode}>
                  <Td>{formatDate(d.date)}</Td>
                  <Td>{capitalise(d.session)}</Td>
                  <Td className="font-mono font-medium">{d.roomCode}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
    </>
  );
}
