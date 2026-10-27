package com.medbook.repository;

import com.medbook.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByMedicalLicenseNumber(String medicalLicenseNumber);

    boolean existsByMedicalLicenseNumber(String medicalLicenseNumber);

    List<Doctor> findBySpecialtyName(String specialtyName);

    List<Doctor> findByActiveTrue();

    Optional<Doctor> findByUserId(Long userId);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty "
            + "WHERE d.active = true ORDER BY d.id ASC")
    List<Doctor> findAllByActiveTrueWithUserAndSpecialty();

    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty "
            + "WHERE d.specialty.id = :specialtyId AND d.active = true ORDER BY d.id ASC")
    List<Doctor> findAllBySpecialtyIdAndActiveTrueWithUserAndSpecialty(@Param("specialtyId") Long specialtyId);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty ORDER BY d.id ASC")
    List<Doctor> findAllWithUserAndSpecialty();

    @Query("SELECT d FROM Doctor d JOIN FETCH d.user JOIN FETCH d.specialty WHERE d.id = :id")
    Optional<Doctor> findByIdWithUserAndSpecialty(@Param("id") Long id);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.specialty WHERE d.user.id = :userId")
    Optional<Doctor> findByUserIdWithSpecialty(@Param("userId") Long userId);

    boolean existsByMedicalLicenseNumberAndIdNot(String medicalLicenseNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
