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

/**
 * Own-profile management for every authenticated role.
 *
 * <p>All identity comes from {@link CurrentUserProvider} (the JWT subject) — a user ID
 * in the request body or a path parameter is never read, so there is structurally no
 * way to address another user's profile. The response shapes are DTOs
 * ({@link CurrentUserResponse} and its nested records), never JPA entities.</p>
 *
 * <p>Editable/admin-controlled split follows the current application design:</p>
 * <ul>
 * <li><strong>PATIENT</strong> edits the self-service contact/medical fields of their
 *     {@code patients} row (address, emergency contact, insurance, allergies);
 *     {@code date_of_birth} stays registration data, {@code is_active} stays system.</li>
 * <li><strong>DOCTOR</strong> edits exactly {@code bio} and {@code profile_picture_url};
 *     specialty, license, fee and active flag are the admin registry
 *     ({@code DoctorAdminService}).</li>
 * <li><strong>ADMIN</strong> gets the account block only — no patient/doctor profile
 *     exists for the role, and no user management is built here.</li>
 * </ul>
 *
 * <p>Password change re-authenticates the caller (BCrypt verify of the current
 * password) before hashing the new one; plaintext never survives the method.</p>
 */
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

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    /**
     * The authenticated caller's own profile: account block always, plus whichever
     * role-specific profile row exists.
     *
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUserProfile() {
        User user = requireCurrentUser();
        return buildResponse(user);
    }

    // ------------------------------------------------------------------
    // Account fields (every role)
    // ------------------------------------------------------------------

    /**
     * Update the caller's own account fields (first name, last name, phone).
     * Email is deliberately not editable: it is the sign-in identity and the
     * uniqueness anchor for every lookup.
     *
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
    @Transactional
    public CurrentUserResponse updateAccount(AccountUpdateRequest request) {
        User user = requireCurrentUser();

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhoneNumber(request.phoneNumber().trim());
        User saved = userRepository.save(user);

        return buildResponse(saved);
    }

    // ------------------------------------------------------------------
    // Patient profile
    // ------------------------------------------------------------------

    /**
     * Update the caller's own patient profile. The caller must have a patient row
     * (the one linked to their user); a doctor or admin without one gets 403 rather
     * than an accidental profile creation.
     *
     * @throws AccessDeniedException             when the caller has no patient profile
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
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

    // ------------------------------------------------------------------
    // Doctor profile
    // ------------------------------------------------------------------

    /**
     * Update the caller's own doctor profile — only the doctor-editable slice
     * (bio + profile picture). Specialty, license, fee and active state are the
     * admin registry and live only in {@code DoctorAdminService}.
     *
     * @throws AccessDeniedException             when the caller has no doctor profile
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
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

    // ------------------------------------------------------------------
    // Password change
    // ------------------------------------------------------------------

    /**
     * Change the caller's password after re-authenticating them.
     *
     * <p>The current password is verified with BCrypt; a wrong one throws
     * {@link BadCredentialsException} (401). The new password is stored only as a
     * BCrypt hash. Statelessness note: existing JWTs stay valid until they expire —
     * the token model has no revocation, and none is invented here.</p>
     *
     * @throws BadCredentialsException            when the current password is wrong (401)
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = requireCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // The request record falls out of scope here; its plaintext fields are never
        // stored or logged anywhere.
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User requireCurrentUser() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new InsufficientAuthenticationException(
                        "Authenticated account no longer exists"));
    }

    /**
     * Assemble the response. The doctor lookup uses the fetch-join variant so the
     * specialty name is readable without extra lazy queries (OSIV is disabled).
     */
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
