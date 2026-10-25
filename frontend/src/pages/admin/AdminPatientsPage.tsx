import { useMemo, useState } from 'react';
import { useAdminPatients } from '../../hooks/useAdmin';
import { AppShell } from '../../components/layout/AppShell';
import { Card } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { PageHeader, ActiveBadge, EmptyState, ErrorBox } from '../../components/admin/AdminUi';
import { toApiError } from '../../api/client';
import { formatDate } from '../../lib/dates';
import { Users } from 'lucide-react';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';










export function AdminPatientsPage() {
  const patientsQuery = useAdminPatients();
  const [search, setSearch] = useState('');

  const patients = patientsQuery.data ?? [];

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return patients;
    return patients.filter((p) =>
      `${p.firstName} ${p.lastName}`.toLowerCase().includes(term) ||
      p.email.toLowerCase().includes(term) ||
      p.phoneNumber.toLowerCase().includes(term),
    );
  }, [patients, search]);

  return (
    <AppShell>
      <div>
        <PageHeader
          icon={<Users className="h-6 w-6 text-brand-600" />}
          title="Patients"
          description="Everyone registered with the clinic. Read-only — patients create their own accounts."
        />

        <Card className="p-6">
          {patientsQuery.isLoading ? (
            <EmptyState loading message="Loading patients..." />
          ) : patientsQuery.isError ? (
            <ErrorBox message={toApiError(patientsQuery.error).message} />
          ) : patients.length === 0 ? (
            <EmptyState
              icon={<Users className="h-6 w-6 text-ink-300" />}
              message="No patients registered yet."
            />
          ) : (
            <>
              <div className="mb-4 max-w-sm">
                <Input
                  label="Search"
                  type="search"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Name, email or phone"
                />
              </div>

              {filtered.length === 0 ? (
                <EmptyState
                  icon={<Users className="h-6 w-6 text-ink-300" />}
                  message="No patients match your search."
                />
              ) : (
                <TableContainer component={Paper} className="overflow-x-auto !bg-transparent !shadow-none">
                  <Table size="small" aria-label="Patients">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Email</TableCell>
                        <TableCell className="hidden sm:table-cell">Phone</TableCell>
                        <TableCell className="hidden sm:table-cell">Date of birth</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filtered.map((patient) => (
                        <TableRow key={patient.id} hover>
                          <TableCell className="!font-medium text-ink-900">
                            {patient.firstName} {patient.lastName}
                          </TableCell>
                          <TableCell className="text-ink-600">{patient.email}</TableCell>
                          <TableCell className="hidden text-ink-600 sm:table-cell">{patient.phoneNumber}</TableCell>
                          <TableCell className="hidden text-ink-600 sm:table-cell">{formatDate(patient.dateOfBirth)}</TableCell>
                          <TableCell>
                            <ActiveBadge active={patient.active && patient.userActive} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
