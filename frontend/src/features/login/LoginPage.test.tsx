import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { adminUser, renderRoutes, stubFetch } from '../../test/render';
import { LoginPage } from './LoginPage';

const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '/admin', element: <p>admin home</p> },
  { path: '/me', element: <p>student home</p> },
];

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('signs in and lands on the role home page', async () => {
    stubFetch({ '/api/auth/login': { body: { accessToken: 't', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(), user: adminUser } } });
    renderRoutes(routes, '/login');
    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'admin12345');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('admin home')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('examgrid.session')!).token).toBe('t');
  });

  it('shows the API problem when credentials are wrong', async () => {
    stubFetch({ '/api/auth/login': { status: 401, body: { status: 401, code: 'invalid_credentials', detail: 'Invalid username or password.' } } });
    renderRoutes(routes, '/login');
    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.');
    expect(sessionStorage.getItem('examgrid.session')).toBeNull();
  });
});
