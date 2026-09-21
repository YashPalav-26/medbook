import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { Brand } from '../ui/Brand';
import { Button } from '../ui/Button';
import {
  LayoutDashboard, CalendarPlus, CalendarDays, ShieldCheck, Stethoscope,
  CalendarClock, Tags, Menu, X, LogOut, UserRound,
} from 'lucide-react';

/**
 * Authenticated app shell: fixed sidebar on desktop, slide-in drawer on mobile,
 * header with user identity + logout. Navigation is role-aware:
 * patients see booking/appointments, admins additionally see the admin area,
 * doctors see their appointments (no invented workflows).
 */

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

function useNavSections(): { patient: NavItem[]; admin: NavItem[] } {
  return {
    patient: [
      { to: '/dashboard', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" />, end: true },
      { to: '/appointments/new', label: 'Book Appointment', icon: <CalendarPlus className="h-4 w-4" /> },
      { to: '/appointments/me', label: 'My Appointments', icon: <CalendarDays className="h-4 w-4" /> },
    ],
    admin: [
      { to: '/admin', label: 'Admin Overview', icon: <ShieldCheck className="h-4 w-4" />, end: true },
      { to: '/admin/specialties', label: 'Specialties', icon: <Tags className="h-4 w-4" /> },
      { to: '/admin/doctors', label: 'Doctors', icon: <Stethoscope className="h-4 w-4" /> },
      { to: '/admin/availability', label: 'Availability', icon: <CalendarClock className="h-4 w-4" /> },
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
  const { patient, admin } = useNavSections();
  const isAdmin = user?.roles?.includes('ROLE_ADMIN') ?? false;
  const isPatient = user?.roles?.includes('ROLE_PATIENT') ?? false;
  const isDoctor = user?.roles?.includes('ROLE_DOCTOR') ?? false;

  return (
    <div className="flex h-full flex-col">
      <div className="px-5 py-5">
        <Brand to="/dashboard" />
      </div>
      <nav aria-label="Main navigation" className="flex-1 space-y-6 overflow-y-auto px-3 pb-6">
        {(isPatient || isAdmin || isDoctor) && (
          <div>
            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-ink-400">Care</p>
            <NavLinks items={patient.filter((i) => isDoctor ? i.to !== '/appointments/new' : true)} onNavigate={onNavigate} />
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

/** Layout for every authenticated page. */
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
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-ink-100 bg-white lg:block">
        <SidebarContent />
      </aside>

      {/* Mobile drawer */}
      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" onClick={() => setDrawerOpen(false)}>
          <div className="absolute inset-0 bg-ink-950/40" />
          <aside
            className="animate-rise absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-white shadow-modal"
            onClick={(e) => e.stopPropagation()}
            aria-label="Navigation drawer"
          >
            <button
              type="button"
              onClick={() => setDrawerOpen(false)}
              aria-label="Close navigation"
              className="absolute right-3 top-4 text-ink-400 hover:text-ink-600"
            >
              <X className="h-5 w-5" />
            </button>
            <SidebarContent onNavigate={() => setDrawerOpen(false)} />
          </aside>
        </div>
      )}

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
        <main className="animate-fade mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
