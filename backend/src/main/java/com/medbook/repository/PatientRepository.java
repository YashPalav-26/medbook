package com.medbook.repository;

import com.medbook.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;







@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {








    Optional<Patient> findByUserId(Long userId);








    java.util.List<Patient> findByActiveTrue();






    @Query("SELECT p FROM Patient p JOIN FETCH p.user ORDER BY p.id ASC")
    java.util.List<Patient> findAllWithUser();
}
