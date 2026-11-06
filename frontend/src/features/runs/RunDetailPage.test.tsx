import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RunDetail } from '../../api/types';
import fixture from '../../test/fixtures/run-detail.json';
import { renderRoutes, signIn } from '../../test/render';
import { RunDetailPage } from './RunDetailPage';

const routes = [{ path: '/admin/runs/:id', element: <RunDetailPage /> }];
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } });

const other = { username: 'examcell2', displayName: 'Second member', approvedAt: '2026-11-06T10:00:00Z', comment: null };

/** A small fake of the run endpoints that remembers approvals, like the server does. */
function fakeServer(initial: Partial<RunDetail>) {
  let detail = { ...(fixture as unknown as RunDetail), ...initial };
  detail = { ...detail, run: { ...detail.run, ...initial.run } };
  const calls: { method: string; url: string; body?: unknown }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ method, url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (url.endsWith('/timetable')) return json([]);
    if (method === 'POST' && url.endsWith('/approvals')) {
      const approvals = [...detail.approvals, { username: 'admin', displayName: 'Admin', approvedAt: '2026-11-06T11:00:00Z', comment: null }];
      const publishing = approvals.length >= 2;
      detail = { ...detail, approvals, run: { ...detail.run, status: publishing ? 'PUBLISHING' : 'DONE' } };
      return json({ approvals: approvals.length, required: 2, publishing });
    }
    if (method === 'DELETE' && url.endsWith('/approvals/me')) {
      detail = { ...detail, approvals: detail.approvals.filter((a) => a.username !== 'admin') };
      return json({ approvals: detail.approvals.length, required: 2, publishing: false });
    }
    if (url === `/api/schedule-runs/${detail.run.id}`) return json(detail);
    return json({ status: 404, code: 'not_found' }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

describe('RunDetailPage approvals', () => {
  beforeEach(() => signIn());
  afterEach(() => vi.unstubAllGlobals());

  it('shows the plan metrics and the second approval starts publishing', async () => {
    const calls = fakeServer({ run: { ...fixture.run, status: 'DONE' } as RunDetail['run'], approvals: [other] });
    renderRoutes(routes, '/admin/runs/1');

    expect(await screen.findByRole('heading', { name: 'Semester plan' })).toBeInTheDocument();
    const approval = screen.getByText('Approval').closest('section')!;
    expect(within(approval).getByText('Second member')).toBeInTheDocument();

    await userEvent.type(within(approval).getByLabelText('Comment (optional)'), 'Checked the clashes');
    await userEvent.click(within(approval).getByRole('button', { name: /Approve/ }));

    expect(await within(approval).findByText(/Generating hall tickets/)).toBeInTheDocument();
    const post = calls.find((c) => c.method === 'POST');
    expect(post).toEqual({ method: 'POST', url: '/api/schedule-runs/1/approvals', body: { comment: 'Checked the clashes' } });
  });

  it('lets an approver withdraw before publication', async () => {
    const calls = fakeServer({
      run: { ...fixture.run, status: 'DONE' } as RunDetail['run'],
      approvals: [{ username: 'admin', displayName: 'Admin', approvedAt: '2026-11-06T09:00:00Z', comment: null }],
    });
    renderRoutes(routes, '/admin/runs/1');
    await userEvent.click(await screen.findByRole('button', { name: 'Withdraw my approval' }));
    expect(await screen.findByRole('button', { name: /Approve/ })).toBeEnabled();
    expect(calls.some((c) => c.method === 'DELETE' && c.url === '/api/schedule-runs/1/approvals/me')).toBe(true);
  });

  it('blocks approval when the catalog changed after the solve', async () => {
    fakeServer({ run: { ...fixture.run, status: 'DONE' } as RunDetail['run'], approvals: [], dataChanged: true });
    renderRoutes(routes, '/admin/runs/1');
    expect(await screen.findByRole('button', { name: /Approve/ })).toBeDisabled();
  });

  it('offers the documents tab once a plan is published', async () => {
    fakeServer({});
    renderRoutes(routes, '/admin/runs/1');
    expect(await screen.findByText(/Live for students since publication/)).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Documents/i })).toBeInTheDocument();
  });
});
