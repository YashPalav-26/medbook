import { Link, useNavigate } from 'react-router-dom';
import { useMyAppointments } from '../hooks/useMyAppointments';
import { toApiError } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import {
  Calendar,
  Clock,
  User,
  Stethoscope,
  AlertCircle,
  Loader2,
  IndianRupee,
  XCircle,
  CalendarPlus,
} from 'lucide-react';
import type { AppointmentResponse } from '../types/appointment';

const statusColors: Record<AppointmentResponse['status'], string> = {
  SCHEDULED: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  COMPLETED: 'bg-gray-100 text-gray-800',
  CANCELLED: 'bg-red-100 text-red-800',
  NO_SHOW: 'bg-orange-100 text-orange-800',
};

function statusStyle(status: AppointmentResponse['status']): string {
  return statusColors[status] ?? 'bg-gray-100 text-gray-800';
}

export function MyAppointmentsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { data: appointments, isLoading, error } = useMyAppointments();

  const apiError = error ? toApiError(error) : null;

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4 flex items-center justify-center">
        <div className="flex items-center gap-3 text-gray-600">
          <Loader2 className="w-6 h-6 animate-spin" />
          Loading your appointments...
        </div>
      </div>
    );
  }

  if (apiError) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <Card className="max-w-md mx-auto p-8">
          <div className="text-center">
            <AlertCircle className="w-16 h-16 text-red-600 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-gray-900 mb-2">
              {apiError.status === 403 ? 'Not Allowed' : 'Error Loading Appointments'}
            </h2>
            <p className="text-gray-600 mb-6">{apiError.message}</p>
            <Button onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="mb-6">
          <Link to="/dashboard" className="text-blue-600 hover:underline text-sm">
            ← Back to Dashboard
          </Link>
        </div>

        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-3">
            <Calendar className="w-8 h-8 text-blue-600" />
            My Appointments
          </h1>
          <p className="text-gray-600 mt-2">
            Appointments for <span className="font-medium">{user?.firstName} {user?.lastName}</span>
          </p>
        </div>

        {appointments && appointments.length === 0 && (
          <Card className="p-8 text-center">
            <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <CalendarPlus className="w-8 h-8 text-blue-600" />
            </div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">No appointments yet</h2>
            <p className="text-gray-600 mb-6">You have not booked any appointments.</p>
            <Button onClick={() => navigate('/appointments/new')}>Book Appointment</Button>
          </Card>
        )}

        {appointments && appointments.length > 0 && (
          <ul className="space-y-4">
            {appointments.map((appointment) => (
              <li key={appointment.id}>
                <Card
                  className="p-6 cursor-pointer hover:shadow-md transition-shadow"
                  onClick={() => navigate(`/appointments/${appointment.id}`)}
                >
                  <div className="flex items-start justify-between mb-4">
                    <h3 className="text-lg font-semibold text-gray-900">
                      Appointment #{appointment.id}
                    </h3>
                    <span
                      className={`px-3 py-1 rounded-full text-sm font-medium ${statusStyle(appointment.status)}`}
                    >
                      {appointment.status}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                    <div className="flex items-center gap-2 text-gray-700">
                      <User className="w-4 h-4 text-gray-400" />
                      Patient ID: {appointment.patientId}
                    </div>
                    <div className="flex items-center gap-2 text-gray-700">
                      <Stethoscope className="w-4 h-4 text-gray-400" />
                      Doctor ID: {appointment.doctorId}
                    </div>
                    <div className="flex items-center gap-2 text-gray-700">
                      <Calendar className="w-4 h-4 text-gray-400" />
                      {appointment.appointmentDate}
                    </div>
                    <div className="flex items-center gap-2 text-gray-700">
                      <Clock className="w-4 h-4 text-gray-400" />
                      {appointment.startTime} – {appointment.endTime}
                    </div>
                    <div className="flex items-center gap-2 text-gray-700">
                      <IndianRupee className="w-4 h-4 text-gray-400" />
                      ₹{appointment.consultationFeeSnapshot.toFixed(2)}
                    </div>
                    {appointment.status === 'CANCELLED' && appointment.cancellationReason && (
                      <div className="flex items-center gap-2 text-red-700 md:col-span-2">
                        <XCircle className="w-4 h-4" />
                        {appointment.cancellationReason}
                      </div>
                    )}
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
