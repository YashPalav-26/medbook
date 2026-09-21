package com.medbook.service;

import com.medbook.dto.DoctorCreateRequest;
import com.medbook.dto.DoctorUpdateRequest;
import com.medbook.entity.Doctor;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.SpecialtyRepository;
import com.medbook.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * ADMIN management of doctors (create with login account / list / update / deactivate).
 *
 * <p><strong>Doctor creation is one atomic transaction</strong> over three writes:
 * the {@code users} row (BCrypt-hashed password), the {@code DOCTOR} role
 * assignment, and the {@code doctors} profile. If any step fails — duplicate email,
 * unknown specialty, duplicate license — the whole operation rolls back, so the
 * database can never hold a user without a doctor profile or vice versa.</p>
 *
 * <p>Security invariants: the created account always receives the DOCTOR role
 * (this API can never mint an ADMIN), the password exists only as a hash, and
 * responses are DTOs ({@link com.medbook.dto.DoctorAdminResponse}) so no security
 * internal can leak.</p>
 */
@Service
public class DoctorAdminService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PasswordEncoder passwordEncoder;

    public DoctorAdminService(DoctorRepository doctorRepository,
                              UserRepository userRepository,
                              RoleRepository roleRepository,
                              SpecialtyRepository specialtyRepository,
                              PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.specialtyRepository = specialtyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Every doctor, active and inactive.
     */
    @Transactional(readOnly = true)
    public List<Doctor> listAll() {
        return doctorRepository.findAllWithUserAndSpecialty();
    }

    /**
     * One doctor by ID.
     *
     * @throws DoctorNotFoundException when the ID does not exist
     */
    @Transactional(readOnly = true)
    public Doctor get(Long id) {
        return requireDoctor(id);
    }

    /**
     * Create a doctor together with their login account and DOCTOR role.
     *
     * @throws com.medbook.service.EmailAlreadyRegisteredException when the email is taken (409)
     * @throws DuplicateLicenseNumberException                     when the license is taken (409)
     * @throws SpecialtyNotFoundException                          when the specialty does not exist (404)
     */
    @Transactional
    public Doctor create(DoctorCreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        if (doctorRepository.existsByMedicalLicenseNumber(request.medicalLicenseNumber().trim())) {
            throw new DuplicateLicenseNumberException(request.medicalLicenseNumber().trim());
        }
        var specialty = specialtyRepository.findById(request.specialtyId())
                .orElseThrow(() -> new SpecialtyNotFoundException(request.specialtyId()));

        // 1. The login account. Only the BCrypt hash of the password is ever stored.
        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), request.phoneNumber().trim());
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        user.addRole(doctorRole);
        User savedUser = userRepository.save(user);

        // 2. The professional profile, linked to the new account.
        Doctor doctor = new Doctor(savedUser, specialty, request.medicalLicenseNumber().trim(),
                request.yearsExperience(), request.consultationFee());
        doctor.setBio(request.bio());
        doctor.setProfilePictureUrl(request.profilePictureUrl());
        return doctorRepository.save(doctor);
    }

    /**
     * Update a doctor's profile fields. Credentials (email/password) and the linked
     * user's name/phone are account management, not profile management, so they are
     * not touched here.
     *
     * @throws DoctorNotFoundException          when the ID does not exist
     * @throws DuplicateLicenseNumberException  when another doctor already uses the license
     * @throws SpecialtyNotFoundException       when the new specialty does not exist
     */
    @Transactional
    public Doctor update(Long id, DoctorUpdateRequest request) {
        Doctor doctor = requireDoctor(id);
        if (doctorRepository.existsByMedicalLicenseNumberAndIdNot(request.medicalLicenseNumber().trim(), id)) {
            throw new DuplicateLicenseNumberException(request.medicalLicenseNumber().trim());
        }
        var specialty = specialtyRepository.findById(request.specialtyId())
                .orElseThrow(() -> new SpecialtyNotFoundException(request.specialtyId()));

        doctor.setSpecialty(specialty);
        doctor.setMedicalLicenseNumber(request.medicalLicenseNumber().trim());
        doctor.setYearsExperience(request.yearsExperience());
        doctor.setBio(request.bio());
        doctor.setConsultationFee(request.consultationFee());
        doctor.setProfilePictureUrl(request.profilePictureUrl());
        return doctorRepository.save(doctor);
    }

    /**
     * Deactivate a doctor's profile ({@code doctors.is_active = false}). The doctor
     * stops appearing in booking discovery; nothing is deleted and existing
     * appointments keep their references.
     *
     * @throws DoctorNotFoundException when the ID does not exist
     */
    @Transactional
    public Doctor deactivate(Long id) {
        Doctor doctor = requireDoctor(id);
        doctor.setActive(false);
        return doctorRepository.save(doctor);
    }

    private Doctor requireDoctor(Long id) {
        return doctorRepository.findByIdWithUserAndSpecialty(id)
                .orElseThrow(() -> new DoctorNotFoundException(id));
    }
}
