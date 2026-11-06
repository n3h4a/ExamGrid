import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ImportBatch, ImportReport } from '../../api/types';
import { renderRoutes, signIn } from '../../test/render';
import { ImportsPage } from './ImportsPage';

const routes = [{ path: '/admin/imports', element: <ImportsPage /> }];
const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });

const report: ImportReport = {
  kind: 'ENROLMENTS',
  mode: 'REPLACE',
  totalRows: 2,
  acceptedRows: 2,
  errorCount: 0,
  warningCount: 0,
  issues: [],
  issuesTruncated: false,
  changes: { enrolmentsAdded: 2, enrolmentsRemoved: 5 },
};
const batch = (id: number, status: ImportBatch['status']): ImportBatch => ({
  id, kind: 'ENROLMENTS', filename: 'enrolments.csv', sizeBytes: 40, sha256: 'ab'.repeat(32), dryRun: status !== 'COMMITTED',
  status, totalRows: 2, acceptedRows: 2, errorCount: 0, warningCount: 0, createdBy: 'admin', createdAt: '2026-11-06T10:00:00Z',
});

describe('ImportsPage', () => {
  beforeEach(() => signIn());
  afterEach(() => vi.unstubAllGlobals());

  it('checks a file first and commits the same file only after a clean dry run', async () => {
    const uploads: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === 'POST') {
        uploads.push(url);
        expect((init.body as FormData).get('file')).toBeInstanceOf(File);
        return json({ batch: url.includes('dryRun=true') ? batch(4, 'VALIDATED') : batch(5, 'COMMITTED'), report });
      }
      return json({ items: [], page: 0, size: 15, totalItems: 0, totalPages: 0 });
    }));
    renderRoutes(routes, '/admin/imports');

    await userEvent.selectOptions(screen.getByLabelText('What does the file contain?'), 'enrolments');
    await userEvent.click(screen.getByLabelText('Replace all enrolments'));
    const file = new File(['roll,course\n24CS001,CS101\n24CS002,CS101\n'], 'enrolments.csv', { type: 'text/csv' });
    await userEvent.upload(screen.getByLabelText('Choose file'), file);
    expect(screen.queryByRole('button', { name: /Import 2 rows/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Check file/ }));
    const commit = await screen.findByRole('button', { name: /Import 2 rows/ });
    expect(uploads).toEqual(['/api/imports/enrolments?dryRun=true&mode=REPLACE']);

    await userEvent.click(commit);
    expect(await screen.findByText(/Imported\. Batch #5/)).toBeInTheDocument();
    expect(uploads[1]).toBe('/api/imports/enrolments?dryRun=false&mode=REPLACE');
  });
});
