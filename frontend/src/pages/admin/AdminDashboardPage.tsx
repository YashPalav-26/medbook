import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { AdminNav } from './SpecialtiesAdminPage';
import { PageHeader } from '../../components/admin/AdminUi';
import { Stethoscope, UserCog, CalendarClock } from 'lucide-react';

/**
 * ADMIN dashboard — the entry point of the admin area.
 * Navigation cards follow the exact Dashboard card pattern (icon circle, title,
 * description, hover shadow) so the admin area feels like the same application.
 * No counts are shown: the backend provides no stats endpoint and fake numbers
 * are worse than none.
 */
export function AdminDashboardPage() {
  return (
    <div className="min-h-screen bg-gray-50">
      <AdminNav />
      <main className="max-w-3xl mx-auto px-6 py-8">
        <PageHeader
          icon={<UserCog className="w-8 h-8 text-blue-600" />}
          title="Admin Dashboard"
          description="Manage the reference data patients and doctors use every day."
        />

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <Link to="/admin/specialties" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center text-blue-600">
                <Stethoscope className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Specialties</h3>
              <p className="text-sm text-gray-600">Add or retire medical specialties</p>
            </Card>
          </Link>
          <Link to="/admin/doctors" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center text-green-600">
                <UserCog className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Doctors</h3>
              <p className="text-sm text-gray-600">Create profiles and login accounts</p>
            </Card>
          </Link>
          <Link to="/admin/availability" className="block">
            <Card className="p-6 flex flex-col items-center text-center space-y-3 hover:shadow-md transition-shadow cursor-pointer">
              <div className="w-12 h-12 bg-purple-100 rounded-full flex items-center justify-center text-purple-600">
                <CalendarClock className="w-6 h-6" />
              </div>
              <h3 className="font-medium text-gray-900">Manage Availability</h3>
              <p className="text-sm text-gray-600">Set when doctors take appointments</p>
            </Card>
          </Link>
        </div>
      </main>
    </div>
  );
}
