package com.medbook.service;

import com.medbook.dto.AccountUpdateRequest;
import com.medbook.dto.ChangePasswordRequest;
import com.medbook.dto.CurrentUserResponse;
import com.medbook.dto.CurrentUserResponse.DoctorProfileResponse;
import com.medbook.dto.CurrentUserResponse.PatientProfileResponse;
import com.medbook.dto.DoctorProfileUpdateRequest;
import com.medbook.dto.PatientProfileUpdateRequest;
import com.medbook.entity.Doctor;
import com.medbook.entity.Patient;
import com.medbook.entity.User;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.CurrentUserProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(UserRepository userRepository,
                          PatientRepository patientRepository,
                          DoctorRepository doctorRepository,
                          CurrentUserProvider currentUserProvider,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUserProfile() {
        User user = requireCurrentUser();
        return buildResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateAccount(AccountUpdateRequest request) {
        User user = requireCurrentUser();

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhoneNumber(request.phoneNumber().trim());
        User saved = userRepository.save(user);

        return buildResponse(saved);
    }

    @Transactional
    public CurrentUserResponse updatePatientProfile(PatientProfileUpdateRequest request) {
        User user = requireCurrentUser();
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("No patient profile exists for this account"));

        patient.setAddressLine1(request.addressLine1());
        patient.setAddressLine2(request.addressLine2());
        patient.setCity(request.city());
        patient.setStateProvince(request.stateProvince());
        patient.setPostalCode(request.postalCode());
        patient.setCountry(request.country());
        patient.setEmergencyContactName(request.emergencyContactName());
        patient.setEmergencyContactPhone(request.emergencyContactPhone());
        patient.setHasInsurance(request.hasInsurance());
        patient.setInsuranceProvider(request.insuranceProvider());
        patient.setInsurancePolicyNumber(request.insurancePolicyNumber());
        patient.setAllergies(request.allergies());

        Patient saved = patientRepository.save(patient);
        return buildResponse(user, saved, null);
    }

    @Transactional
    public CurrentUserResponse updateDoctorProfile(DoctorProfileUpdateRequest request) {
        User user = requireCurrentUser();
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("No doctor profile exists for this account"));

        doctor.setBio(request.bio());
        doctor.setProfilePictureUrl(request.profilePictureUrl());
        Doctor saved = doctorRepository.save(doctor);
        return buildResponse(user, null, saved);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = requireCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

    }

    private User requireCurrentUser() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new InsufficientAuthenticationException(
                        "Authenticated account no longer exists"));
    }

    private CurrentUserResponse buildResponse(User user) {
        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        Doctor doctor = doctorRepository.findByUserIdWithSpecialty(user.getId()).orElse(null);
        return buildResponse(user, patient, doctor);
    }

    private CurrentUserResponse buildResponse(User user, Patient patient, Doctor doctor) {
        List<String> roles = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName())
                .sorted()
                .toList();

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                roles,
                patient == null ? null : PatientProfileResponse.from(patient),
                doctor == null ? null : new DoctorProfileResponse(
                        doctor.getId(),
                        doctor.getBio(),
                        doctor.getProfilePictureUrl(),
                        doctor.getSpecialty().getName(),
                        doctor.getMedicalLicenseNumber(),
                        doctor.getConsultationFee(),
                        doctor.isActive()
                )
        );
    }
}
