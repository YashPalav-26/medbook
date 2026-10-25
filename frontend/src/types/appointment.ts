




export type AppointmentStatus = 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';





export interface CreateAppointmentRequest {
  doctorId: number;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  reason?: string;
}





export interface AdminCreateAppointmentRequest {
  patientId: number;
  doctorId: number;
  appointmentDate: string;
  startTime: string;
  endTime: string;
}


export interface SpecialtyResponse {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
}


export interface DoctorSummaryResponse {
  id: number;
  firstName: string;
  lastName: string;
  specialty: string;
  yearsExperience: number;
  bio: string | null;
  consultationFee: number;
  profilePictureUrl: string | null;
}


export interface DoctorAvailabilityResponse {
  id: number | null;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  recurring: boolean;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
}


export interface CancelAppointmentRequest {
  cancellationReason: string;
}






export interface AppointmentResponse {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  cancellationReason: string | null;
  consultationFeeSnapshot: number;
}






export interface AdminAppointmentResponse {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  specialty: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  cancellationReason: string | null;
  consultationFeeSnapshot: number;
}


export interface AppointmentStatusHistoryResponse {
  id: number;
  status: AppointmentStatus;
  notes: string | null;
  changedByName: string | null;
  changedAt: string;
}


export interface AdminAppointmentDetailResponse {
  appointment: AdminAppointmentResponse;
  history: AppointmentStatusHistoryResponse[];
}






export interface PatientAdminResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  dateOfBirth: string;
  active: boolean;
  userActive: boolean;
}


export interface AdminOverviewResponse {
  todayAppointments: number;
  today: string;
  statusCounts: Record<AppointmentStatus, number>;
  totalAppointments: number;
}


export interface AdminAppointmentFilters {
  date?: string;
  status?: AppointmentStatus | '';
  doctorId?: number | '';
  patientId?: number;
}