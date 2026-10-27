import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Toaster } from 'sonner';
import { theme } from './theme';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './hooks/useAuth';
import { Card } from './components/ui/Card';
import { Button } from './components/ui/Button';
import { ShieldAlert } from 'lucide-react';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { BookAppointmentPage } from './pages/BookAppointmentPage';
import { AppointmentDetailPage } from './pages/AppointmentDetailPage';
import { MyAppointmentsPage } from './pages/MyAppointmentsPage';
import { ProfilePage } from './pages/ProfilePage';
import { DoctorDashboardPage } from './pages/DoctorDashboardPage';
import { DoctorAppointmentsPage } from './pages/DoctorAppointmentsPage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { SpecialtiesAdminPage } from './pages/admin/SpecialtiesAdminPage';
import { DoctorsAdminPage } from './pages/admin/DoctorsAdminPage';
import { AdminPatientsPage } from './pages/admin/AdminPatientsPage';
import { AdminManagementPage } from './pages/admin/AdminManagementPage';
import { AvailabilityAdminPage } from './pages/admin/AvailabilityAdminPage';
import { AdminAppointmentsPage } from './pages/admin/AdminAppointmentsPage';
import { AdminAppointmentDetailPage } from './pages/admin/AdminAppointmentDetailPage';
import type { ReactNode } from 'react';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }
  const isAdmin = user?.roles?.includes('ROLE_ADMIN') ?? false;
  if (!isAdmin) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-ink-50 px-4">
        <Card className="max-w-md p-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-danger-100">
            <ShieldAlert className="h-8 w-8 text-danger-600" aria-hidden="true" />
          </div>
          <h2 className="font-display text-2xl font-semibold text-ink-900">Access denied</h2>
          <p className="mt-2 text-ink-600">
            This area is for clinic administrators. Your account does not have admin rights.
          </p>
          <Button variant="outline" className="mt-6" onClick={() => window.history.back()}>
            Go Back
          </Button>
        </Card>
      </div>
    );
  }
  return <>{children}</>;
}

function DoctorRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }
  const isDoctor = user?.roles?.includes('ROLE_DOCTOR') ?? false;
  if (!isDoctor) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-ink-50 px-4">
        <Card className="max-w-md p-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-danger-100">
            <ShieldAlert className="h-8 w-8 text-danger-600" aria-hidden="true" />
          </div>
          <h2 className="font-display text-2xl font-semibold text-ink-900">Access denied</h2>
          <p className="mt-2 text-ink-600">
            This area is for doctors. Your account does not have doctor rights.
          </p>
          <Button variant="outline" className="mt-6" onClick={() => window.history.back()}>
            Go Back
          </Button>
        </Card>
      </div>
    );
  }
  return <>{children}</>;
}

function DashboardRouter() {
  const { user } = useAuth();
  const isAdmin = user?.roles?.includes('ROLE_ADMIN') ?? false;
  const isDoctor = user?.roles?.includes('ROLE_DOCTOR') ?? false;
  if (isAdmin) return <AdminDashboardPage />;
  return isDoctor ? <DoctorDashboardPage /> : <DashboardPage />;
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Public */}
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Patient / shared */}
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardRouter />
                </ProtectedRoute>
              }
            />
            <Route
              path="/appointments/me"
              element={
                <ProtectedRoute>
                  <MyAppointmentsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/appointments/new"
              element={
                <ProtectedRoute>
                  <BookAppointmentPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/appointments/:id"
              element={
                <ProtectedRoute>
                  <AppointmentDetailPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />

            {/* Doctor portal (UX guard only — the backend remains authoritative) */}
            <Route path="/doctor" element={<DoctorRoute><DoctorDashboardPage /></DoctorRoute>} />
            <Route path="/doctor/appointments" element={<DoctorRoute><DoctorAppointmentsPage /></DoctorRoute>} />

            {/* Admin */}
            <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
            <Route path="/admin/appointments" element={<AdminRoute><AdminAppointmentsPage /></AdminRoute>} />
            <Route path="/admin/appointments/:id" element={<AdminRoute><AdminAppointmentDetailPage /></AdminRoute>} />
            <Route path="/admin/specialties" element={<AdminRoute><SpecialtiesAdminPage /></AdminRoute>} />
            <Route path="/admin/doctors" element={<AdminRoute><DoctorsAdminPage /></AdminRoute>} />
            <Route path="/admin/patients" element={<AdminRoute><AdminPatientsPage /></AdminRoute>} />
            <Route path="/admin/admins" element={<AdminRoute><AdminManagementPage /></AdminRoute>} />
            <Route path="/admin/availability" element={<AdminRoute><AvailabilityAdminPage /></AdminRoute>} />

            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
        </BrowserRouter>
        {/* Third-party toast system (Sonner) — mounted once, themed to MedBook. */}
        <Toaster
          position="bottom-right"
          toastOptions={{
            className: 'text-sm',
            style: { background: '#ffffff', color: '#343e42', border: '1px solid #eceff0' },
          }}
        />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
