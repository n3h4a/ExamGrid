import { screen } from '@testing-library/react';
import { adminUser, renderRoutes, signIn } from '../test/render';
import { RequireRole } from './RequireRole';

const routes = [
  { path: '/login', element: <p>login page</p> },
  { path: '/me', element: <p>student home</p> },
  { element: <RequireRole roles={['EXAM_CELL']} />, children: [{ path: '/admin', element: <p>admin area</p> }] },
];

describe('RequireRole', () => {
  it('sends anonymous users to the login page', () => {
    renderRoutes(routes, '/admin');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('sends users of another role to their own home', () => {
    signIn({ ...adminUser, role: 'STUDENT', studentRoll: '21CS045' });
    renderRoutes(routes, '/admin');
    expect(screen.getByText('student home')).toBeInTheDocument();
  });

  it('lets the right role through', () => {
    signIn();
    renderRoutes(routes, '/admin');
    expect(screen.getByText('admin area')).toBeInTheDocument();
  });

  it('ignores an expired session', () => {
    sessionStorage.setItem('examgrid.session', JSON.stringify({ token: 'x', expiresAt: '2000-01-01T00:00:00Z', user: adminUser }));
    renderRoutes(routes, '/admin');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });
});
