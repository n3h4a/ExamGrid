import clsx from 'clsx';
import {
  BookOpen,
  CalendarDays,
  ClipboardList,
  DoorOpen,
  FileUp,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  ScrollText,
  ShieldCheck,
  Users,
  UserSquare,
  Workflow,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { NavLink, Outlet } from 'react-router';
import type { Role } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { GlobalSearch } from '../features/search/GlobalSearch';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

const NAV: Record<Role, { section: string; items: NavItem[] }[]> = {
  EXAM_CELL: [
    {
      section: 'Overview',
      items: [{ to: '/admin', label: 'Dashboard', icon: <LayoutDashboard />, end: true }],
    },
    {
      section: 'Data',
      items: [
        { to: '/admin/imports', label: 'Imports', icon: <FileUp /> },
        { to: '/admin/courses', label: 'Courses', icon: <BookOpen /> },
        { to: '/admin/students', label: 'Students', icon: <GraduationCap /> },
        { to: '/admin/rooms', label: 'Rooms', icon: <DoorOpen /> },
        { to: '/admin/faculty', label: 'Faculty', icon: <UserSquare /> },
      ],
    },
    {
      section: 'Scheduling',
      items: [
        { to: '/admin/slots', label: 'Exam slots', icon: <CalendarDays /> },
        { to: '/admin/rules', label: 'Locks & groups', icon: <Workflow /> },
      ],
    },
    {
      section: 'Administration',
      items: [
        { to: '/admin/users', label: 'Users', icon: <Users /> },
        { to: '/admin/audit', label: 'Audit log', icon: <ScrollText /> },
      ],
    },
  ],
  FACULTY: [{ section: 'Me', items: [{ to: '/duties', label: 'My duties', icon: <ClipboardList /> }] }],
  STUDENT: [{ section: 'Me', items: [{ to: '/me', label: 'My exams', icon: <ShieldCheck /> }] }],
};

/** App shell: role-aware sidebar, top bar with search (exam cell) and the signed-in user. */
export function Layout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  if (!user) return null;
  const sections = NAV[user.role];

  const nav = (
    <nav className="flex flex-1 flex-col gap-6 px-3 py-4" aria-label="Main">
      {sections.map((s) => (
        <div key={s.section}>
          <p className="px-3 text-xs font-semibold uppercase tracking-wide text-brand-100/70">{s.section}</p>
          <ul className="mt-2 space-y-0.5">
            {s.items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  onClick={() => setOpen(false)}
                  className={({ isActive }) =>
                    clsx(
                      'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium [&_svg]:size-4',
                      isActive ? 'bg-white/10 text-white' : 'text-brand-100 hover:bg-white/5 hover:text-white',
                    )
                  }
                >
                  {item.icon}
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );

  return (
    <div className="min-h-screen lg:flex">
      <aside
        className={clsx(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-brand-900 transition-transform lg:static lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-14 items-center gap-2 px-6 text-white">
          <img src="/favicon.svg" alt="" className="size-7" />
          <span className="text-lg font-semibold tracking-tight">ExamGrid</span>
        </div>
        {nav}
        <div className="border-t border-white/10 p-4 text-xs text-brand-100/70">
          Signed in as <span className="font-medium text-white">{user.displayName}</span>
        </div>
      </aside>
      {open && <div className="fixed inset-0 z-30 bg-slate-900/40 lg:hidden" onClick={() => setOpen(false)} />}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur lg:px-8">
          <button className="rounded p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu className="size-5" />
          </button>
          <div className="flex-1">{user.role === 'EXAM_CELL' && <GlobalSearch />}</div>
          <span className="hidden text-sm text-slate-600 sm:inline">
            {user.username} · {user.role.replace('_', ' ').toLowerCase()}
          </span>
          <button onClick={logout} className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-slate-600 hover:bg-slate-100">
            <LogOut className="size-4" /> Sign out
          </button>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
