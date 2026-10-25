import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import Drawer from '@mui/material/Drawer';
import { useAuth } from '../../hooks/useAuth';
import { Brand } from '../ui/Brand';
import { Button } from '../ui/Button';
import { NotificationBell } from './NotificationBell';
import {
  LayoutDashboard, CalendarPlus, CalendarDays, ShieldCheck, Stethoscope,
  CalendarClock, Tags, CalendarSearch, UserRound, Menu, LogOut, Users,
} from 'lucide-react';








interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

function useNavSections(): { patient: NavItem[]; doctor: NavItem[]; admin: NavItem[] } {
  return {
    patient: [
      { to: '/dashboard', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, end: true },
      { to: '/appointments/new', label: 'Book Appointment', icon: <CalendarPlus className="h-4 w-4" /> },
      { to: '/appointments/me', label: 'My Appointments', icon: <CalendarDays className="h-4 w-4" /> },
      { to: '/profile', label: 'My Profile', icon: <UserRound className="h-4 w-4" /> },
    ],
    doctor: [
      { to: '/dashboard', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, end: true },
      { to: '/doctor/appointments', label: 'My Appointments', icon: <CalendarDays className="h-4 w-4" /> },
      { to: '/profile', label: 'My Profile', icon: <UserRound className="h-4 w-4" /> },
    ],
    admin: [
      { to: '/admin', label: 'Admin Overview', icon: <ShieldCheck className="h-4 w-4" />, end: true },
      { to: '/admin/appointments', label: 'Appointments', icon: <CalendarSearch className="h-4 w-4" /> },
      { to: '/admin/specialties', label: 'Specialties', icon: <Tags className="h-4 w-4" /> },
      { to: '/admin/doctors', label: 'Doctors', icon: <Stethoscope className="h-4 w-4" /> },
      { to: '/admin/patients', label: 'Patients', icon: <Users className="h-4 w-4" /> },
      { to: '/admin/admins', label: 'Admins', icon: <ShieldCheck className="h-4 w-4" /> },
      { to: '/admin/availability', label: 'Availability', icon: <CalendarClock className="h-4 w-4" /> },
      { to: '/profile', label: 'My Profile', icon: <UserRound className="h-4 w-4" /> },
    ],
  };
}

function NavLinks({ items, onNavigate }: { items: NavItem[]; onNavigate?: () => void }) {
  return (
    <ul className="space-y-1">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink
            to={item.to}
            end={item.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-field px-3 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-50 text-brand-700'
                  : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900'
              }`
            }
          >
            {item.icon}
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  );
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const { patient, doctor, admin } = useNavSections();
  const isAdmin = user?.roles?.includes('ROLE_ADMIN') ?? false;
  const isPatient = user?.roles?.includes('ROLE_PATIENT') ?? false;
  const isDoctor = user?.roles?.includes('ROLE_DOCTOR') ?? false;

  return (
    <div className="flex h-full flex-col">
      <div className="px-5 py-5">
        <Brand to="/dashboard" onClick={onNavigate} />
      </div>
      <nav aria-label="Main navigation" className="flex-1 space-y-6 overflow-y-auto px-3 pb-6">
        {isDoctor && !isAdmin && (
          <div>
            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-ink-400">Practice</p>
            <NavLinks items={doctor} onNavigate={onNavigate} />
          </div>
        )}
        {/* Patient navigation only for accounts that actually hold PATIENT —
            an admin-only account would get 403s from the booking and
            appointment endpoints behind these links. */}
        {!isDoctor && isPatient && (
          <div>
            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-ink-400">Care</p>
            <NavLinks items={patient} onNavigate={onNavigate} />
          </div>
        )}
        {isAdmin && (
          <div>
            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-ink-400">Administration</p>
            <NavLinks items={admin} onNavigate={onNavigate} />
          </div>
        )}
      </nav>
      <div className="border-t border-ink-100 px-5 py-4">
        <p className="text-xs text-ink-400">Signed in as</p>
        <p className="truncate text-sm font-medium text-ink-700">{user?.email}</p>
      </div>
    </div>
  );
}


export function AppShell({ title, children }: { title?: string; children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const roleLabel = user?.roles?.[0]?.replace('ROLE_', '') ?? 'User';

  return (
    <div className="min-h-screen bg-ink-50">
      {/* Keyboard users land here first and can jump past the whole sidebar. */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-field focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
      >
        Skip to main content
      </a>

      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-ink-100 bg-white lg:block">
        <SidebarContent />
      </aside>

      {/* Mobile drawer — MUI Drawer (focus trap, Escape, backdrop, swipe-out) */}
      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        aria-label="Navigation drawer"
        sx={{
          display: { lg: 'none' },
          '& .MuiDrawer-paper': {
            width: 'min(18rem, 85vw)',
            borderRadius: '0 14px 14px 0',
            backgroundImage: 'none',
          },
        }}
      >
        <SidebarContent onNavigate={() => setDrawerOpen(false)} />
      </Drawer>

      {/* Content column */}
      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-ink-100 bg-white/90 backdrop-blur">
          <div className="flex items-center justify-between gap-4 px-4 py-3 sm:px-6">
            <div className="flex min-w-0 items-center gap-3">
              <button
                type="button"
                onClick={() => setDrawerOpen(true)}
                aria-label="Open navigation"
                className="rounded-field p-2 text-ink-600 hover:bg-ink-100 lg:hidden"
              >
                <Menu className="h-5 w-5" />
              </button>
              {title && (
                <h1 className="truncate font-display text-lg font-semibold text-ink-900">{title}</h1>
              )}
            </div>
            <div className="flex items-center gap-3">
              <NotificationBell />
              <span className="hidden items-center gap-2 rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700 sm:inline-flex">
                <UserRound className="h-3.5 w-3.5" aria-hidden="true" />
                {user?.firstName} {user?.lastName} · {roleLabel}
              </span>
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Sign Out
              </Button>
            </div>
          </div>
        </header>
        <main
          id="main-content"
          tabIndex={-1}
          className="animate-fade mx-auto w-full max-w-5xl px-4 py-8 focus:outline-none sm:px-6 lg:px-8"
        >
          {children}
        </main>
      </div>
    </div>
  );
}
