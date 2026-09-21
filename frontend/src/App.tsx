import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { SpecialtiesAdminPage } from './pages/admin/SpecialtiesAdminPage';
import { DoctorsAdminPage } from './pages/admin/DoctorsAdminPage';
import { AvailabilityAdminPage } from './pages/admin/AvailabilityAdminPage';
import type { ReactNode } from 'react';

// ------------------------------------------------------------------
// Route guards
// ------------------------------------------------------------------

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

/**
 * Admin area guard — UX only, not security. It reads the roles the backend
 * returned at login (already stored by AuthContext) and shows a forbidden card
 * to non-admins. The real boundary is the backend's /api/v1/admin/** → ADMIN
 * role check: every admin API call from these pages would return 403 anyway.
 */
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

// ------------------------------------------------------------------
// Query client
// ------------------------------------------------------------------

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});

// ------------------------------------------------------------------
// App
// ------------------------------------------------------------------

export default function App() {
  return (
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
                  <DashboardPage />
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

            {/* Admin */}
            <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
            <Route path="/admin/specialties" element={<AdminRoute><SpecialtiesAdminPage /></AdminRoute>} />
            <Route path="/admin/doctors" element={<AdminRoute><DoctorsAdminPage /></AdminRoute>} />
            <Route path="/admin/availability" element={<AdminRoute><AvailabilityAdminPage /></AdminRoute>} />

            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
