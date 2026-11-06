import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../api/client';
import { catalogApi } from '../../api/endpoints';
import type { Room, RoomRequest } from '../../api/types';
import { Badge, Button, Card, EmptyState, ErrorBanner, Modal, PageHeader, Spinner, Table, Td, Th } from '../../components/ui';
import { formatNumber } from '../../lib/format';
import { useSearchParam } from '../../lib/useSearchParam';
import { RoomForm } from './RoomForm';

export function RoomsPage() {
  const queryClient = useQueryClient();
  const [q] = useSearchParam('q');
  const [editing, setEditing] = useState<Room | 'new' | null>(null);
  const rooms = useQuery({ queryKey: ['rooms'], queryFn: catalogApi.rooms });
  const save = useMutation({
    mutationFn: (body: RoomRequest) =>
      editing && editing !== 'new' ? catalogApi.updateRoom(editing.id, body) : catalogApi.createRoom(body),
    onSuccess: () => {
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['rooms'] });
      void queryClient.invalidateQueries({ queryKey: ['catalog-summary'] });
    },
  });
  const remove = useMutation({
    mutationFn: (r: Room) => catalogApi.deleteRoom(r.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['rooms'] }),
    onError: (e) => window.alert(errorMessage(e)),
  });

  const list = (rooms.data ?? []).filter((r) => !q || r.code.includes(q.toUpperCase()));
  const seats = list.filter((r) => r.active).reduce((n, r) => n + r.capacity, 0);

  return (
    <>
      <PageHeader
        title="Rooms"
        description="Exam halls as seat grids. The solver fills rooms in priority order and never seats two candidates of one paper side by side."
        actions={
          <Button onClick={() => { save.reset(); setEditing('new'); }}>
            <Plus className="size-4" /> New room
          </Button>
        }
      />
      <Card>
        {rooms.isPending ? (
          <Spinner />
        ) : rooms.isError ? (
          <ErrorBanner error={rooms.error} />
        ) : list.length === 0 ? (
          <EmptyState title="No rooms">Import a room list or create a room with its seat layout.</EmptyState>
        ) : (
          <>
            <p className="mb-3 text-sm text-slate-600">
              {list.length} rooms · {formatNumber(seats)} usable seats in active rooms
            </p>
            <Table>
              <thead>
                <tr>
                  <Th>Priority</Th>
                  <Th>Code</Th>
                  <Th>Building</Th>
                  <Th>Layout</Th>
                  <Th>Seats</Th>
                  <Th>Blocked</Th>
                  <Th>Status</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.map((r) => (
                  <tr key={r.id}>
                    <Td className="tabular-nums text-slate-500">{r.priority}</Td>
                    <Td className="font-mono font-medium">{r.code}</Td>
                    <Td>{r.building || '—'}</Td>
                    <Td className="tabular-nums">{r.rows} × {r.columns}</Td>
                    <Td className="tabular-nums">{r.capacity}</Td>
                    <Td className="font-mono text-xs">{r.blockedSeats.join(' ') || '—'}</Td>
                    <Td>{r.active ? <Badge tone="green">active</Badge> : <Badge>inactive</Badge>}</Td>
                    <Td className="text-right">
                      <Button variant="ghost" onClick={() => { save.reset(); setEditing(r); }}>Edit</Button>
                      <Button variant="ghost" onClick={() => window.confirm(`Delete room ${r.code}?`) && remove.mutate(r)}>Delete</Button>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </>
        )}
      </Card>
      <Modal wide open={editing !== null} title={editing === 'new' ? 'New room' : `Edit ${editing?.code ?? ''}`} onClose={() => setEditing(null)}>
        {editing !== null && (
          <RoomForm initial={editing === 'new' ? undefined : editing} onSubmit={(b) => save.mutate(b)} onCancel={() => setEditing(null)} busy={save.isPending} error={save.error} />
        )}
      </Modal>
    </>
  );
}
