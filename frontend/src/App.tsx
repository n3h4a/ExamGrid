import { QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router';
import { queryClient } from './lib/queryClient';
import { AuthProvider } from './auth/AuthProvider';
import { homeFor, useAuth } from './auth/useAuth';
import { RequireRole } from './auth/RequireRole';
import { Layout } from './components/Layout';
import { AuditPage } from './features/audit/AuditPage';
import { CoursesPage } from './features/courses/CoursesPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { FacultyPage } from './features/faculty/FacultyPage';
import { ImportsPage } from './features/imports/ImportsPage';
import { LoginPage } from './features/login/LoginPage';
import { MyDutiesPage } from './features/portal/MyDutiesPage';
import { MyExamsPage } from './features/portal/MyExamsPage';
import { SeatFinderPage } from './features/portal/SeatFinderPage';
import { RoomsPage } from './features/rooms/RoomsPage';
import { RulesPage } from './features/rules/RulesPage';
import { SlotsPage } from './features/slots/SlotsPage';
import { StudentsPage } from './features/students/StudentsPage';
import { UsersPage } from './features/users/UsersPage';

function Home() {
  const { user } = useAuth();
  return <Navigate to={user ? homeFor(user.role) : '/seat'} replace />;
}

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/seat', element: <SeatFinderPage /> },
  {
    element: <RequireRole roles={['EXAM_CELL']} />,
    children: [
      {
        path: '/admin',
        element: <Layout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'imports', element: <ImportsPage /> },
          { path: 'courses', element: <CoursesPage /> },
          { path: 'students', element: <StudentsPage /> },
          { path: 'rooms', element: <RoomsPage /> },
          { path: 'faculty', element: <FacultyPage /> },
          { path: 'slots', element: <SlotsPage /> },
          { path: 'rules', element: <RulesPage /> },
          { path: 'users', element: <UsersPage /> },
          { path: 'audit', element: <AuditPage /> },
        ],
      },
    ],
  },
  {
    element: <RequireRole roles={['STUDENT']} />,
    children: [{ element: <Layout />, children: [{ path: '/me', element: <MyExamsPage /> }] }],
  },
  {
    element: <RequireRole roles={['FACULTY']} />,
    children: [{ element: <Layout />, children: [{ path: '/duties', element: <MyDutiesPage /> }] }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  );
}
