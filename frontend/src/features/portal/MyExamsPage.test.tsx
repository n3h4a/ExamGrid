import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { User } from '../../api/types';
import { Layout } from '../../components/Layout';
import { adminUser, renderRoutes, signIn, stubFetch } from '../../test/render';
import { MyExamsPage } from './MyExamsPage';

const student: User = { ...adminUser, id: 9, username: '24cs001', displayName: 'Asha Rao', role: 'STUDENT', studentRoll: '24CS001' };
const routes = [
  { element: <Layout />, children: [{ path: '/me', element: <MyExamsPage /> }] },
  { path: '/login', element: <p>login page</p> },
];

describe('student portal', () => {
  beforeEach(() => signIn(student));
  afterEach(() => vi.unstubAllGlobals());

  it('shows only the student navigation and their own exams', async () => {
    stubFetch({
      '/api/students/me/exams': {
        body: {
          roll: '24CS001', name: 'Asha Rao', department: 'CS',
          exams: [{ courseCode: 'CS101', courseName: 'Programming', slot: '2026-12-01-MORNING', date: '2026-12-01', session: 'MORNING', roomCode: 'LH-1', building: 'Main', seat: 'B4' }],
        },
      },
    });
    renderRoutes(routes, '/me');

    expect(await screen.findByText('Asha Rao · 24CS001')).toBeInTheDocument();
    expect(screen.getByText('CS101')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Hall ticket/ })).toBeInTheDocument();
    const nav = screen.getAllByRole('navigation', { name: 'Main' })[0];
    expect(within(nav).getByRole('link', { name: /My exams/ })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: /Imports/ })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: /Schedule runs/ })).not.toBeInTheDocument();
  });

  it('says so when nothing is published yet, and signs out', async () => {
    stubFetch({ '/api/students/me/exams': { status: 404, body: { status: 404, code: 'not_published' } } });
    renderRoutes(routes, '/me');
    expect(await screen.findByText('The timetable is not published yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Sign out/ }));
    expect(sessionStorage.getItem('examgrid.session')).toBeNull();
  });
});
