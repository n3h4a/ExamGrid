import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { AuthProvider } from '../auth/AuthProvider';
import type { User } from '../api/types';

export const adminUser: User = {
  id: 1,
  username: 'admin',
  displayName: 'Admin',
  role: 'EXAM_CELL',
  studentRoll: null,
  facultyCode: null,
  enabled: true,
  createdAt: '2026-11-01T00:00:00Z',
  lastLoginAt: null,
};

/** Stores a session as the login flow would, so components render as that user. */
export function signIn(user: User = adminUser) {
  sessionStorage.setItem(
    'examgrid.session',
    JSON.stringify({ token: 'test-token', expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user }),
  );
}

/** Renders routes inside fresh query, auth and router providers. */
export function renderRoutes(routes: RouteObject[], initialPath: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const utils = render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return { ...utils, router };
}

export function renderWithClient(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

/** A fetch stub answering by URL prefix with JSON bodies. */
export function stubFetch(routes: Record<string, { status?: number; body?: unknown }>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = String(input);
    const key = Object.keys(routes).find((prefix) => url.startsWith(prefix));
    if (!key) return new Response(JSON.stringify({ status: 404, code: 'not_found' }), { status: 404, headers: { 'Content-Type': 'application/problem+json' } });
    const { status = 200, body } = routes[key];
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
