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

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<Doctor> listAll() {
        return doctorRepository.findAllWithUserAndSpecialty();
    }

    @Transactional(readOnly = true)
    public Doctor get(Long id) {
        return requireDoctor(id);
    }

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

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), request.phoneNumber().trim());
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        user.addRole(doctorRole);
        User savedUser = userRepository.save(user);

        Doctor doctor = new Doctor(savedUser, specialty, request.medicalLicenseNumber().trim(),
                request.yearsExperience(), request.consultationFee());
        doctor.setBio(request.bio());
        doctor.setProfilePictureUrl(request.profilePictureUrl());
        return doctorRepository.save(doctor);
    }

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
