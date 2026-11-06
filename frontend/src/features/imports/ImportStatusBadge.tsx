import type { ImportBatch } from '../../api/types';
import { Badge } from '../../components/ui';

export function ImportStatusBadge({ status, dryRun }: { status: ImportBatch['status']; dryRun: boolean }) {
  if (status === 'COMMITTED') return <Badge tone="green">Imported</Badge>;
  if (status === 'REJECTED') return <Badge tone="red">Rejected</Badge>;
  return <Badge tone="blue">{dryRun ? 'Checked' : 'Validated'}</Badge>;
}
