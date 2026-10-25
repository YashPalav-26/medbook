





export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;

  dateOfBirth: string;
}


export interface LoginRequest {
  email: string;
  password: string;
}


export interface AuthUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;

  roles: string[];
}


export interface AuthResponse {
  token: string;

  tokenType: string;
  expiresInMinutes: number;
  user: AuthUser;
}


export interface FieldError {
  field: string;
  message: string;
}


export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: FieldError[];
}


export type RegisterRequestField = keyof RegisterRequest;






export interface CurrentUserResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  roles: string[];
  patient: PatientProfileResponse | null;
  doctor: DoctorProfileResponse | null;
}


export interface PatientProfileResponse {
  patientId: number;
  dateOfBirth: string;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  stateProvince: string | null;
  postalCode: string | null;
  country: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  hasInsurance: boolean;
  insuranceProvider: string | null;
  insurancePolicyNumber: string | null;
  allergies: string | null;
}


export interface DoctorProfileResponse {
  doctorId: number;

  bio: string | null;
  profilePictureUrl: string | null;

  specialtyName: string;
  medicalLicenseNumber: string;
  consultationFee: number;
  active: boolean;
}


export interface AccountUpdateRequest {
  firstName: string;
  lastName: string;
  phoneNumber: string;
}


export interface PatientProfileUpdateRequest {
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
  hasInsurance: boolean;
  insuranceProvider: string;
  insurancePolicyNumber: string;
  allergies: string;
}


export interface DoctorProfileUpdateRequest {
  bio: string;
  profilePictureUrl: string;
}


export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
