package com.medbook.repository;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;







@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {









    List<Appointment> findByDoctorId(Long doctorId);









    List<Appointment> findByPatientId(Long patientId);









    List<Appointment> findByStatus(AppointmentStatus status);










    List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate appointmentDate);










    List<Appointment> findByPatientIdAndAppointmentDate(Long patientId, LocalDate appointmentDate);


























    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.appointmentDate = :appointmentDate " +
           "AND a.status <> :excludedStatus " +
           "AND a.startTime < :endTime " +
           "AND a.endTime > :startTime")
    List<Appointment> findConflictingAppointments(
            @Param("doctorId") Long doctorId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedStatus") AppointmentStatus excludedStatus
    );












    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);














    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdWithPatientAndDoctor(@Param("id") Long id);













    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id AND p.id = :patientId")
    Optional<Appointment> findByIdAndPatientIdWithParticipants(
            @Param("id") Long id, @Param("patientId") Long patientId);









    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE a.id = :id AND d.id = :doctorId")
    Optional<Appointment> findByIdAndDoctorIdWithParticipants(
            @Param("id") Long id, @Param("doctorId") Long doctorId);











    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE p.user.id = :userId ORDER BY a.appointmentDate DESC, a.startTime DESC")
    List<Appointment> findAllByPatientUserId(@Param("userId") Long userId);







    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user " +
           "WHERE d.user.id = :userId ORDER BY a.appointmentDate DESC, a.startTime DESC")
    List<Appointment> findAllByDoctorUserId(@Param("userId") Long userId);
























    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.specialty " +
           "WHERE (:appointmentDate IS NULL OR a.appointmentDate = :appointmentDate) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:doctorId IS NULL OR d.id = :doctorId) " +
           "AND (:patientId IS NULL OR p.id = :patientId) " +
           "ORDER BY a.appointmentDate DESC, a.startTime DESC, a.id DESC")
    List<Appointment> findAdminAppointments(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("status") AppointmentStatus status,
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId);











    @Query("SELECT a FROM Appointment a " +
           "JOIN FETCH a.patient p JOIN FETCH p.user " +
           "JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.specialty " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdForAdmin(@Param("id") Long id);











    @Query("SELECT a.status AS status, COUNT(a) AS total FROM Appointment a GROUP BY a.status")
    List<AppointmentStatusCount> countGroupedByStatus();









    long countByAppointmentDate(LocalDate appointmentDate);
}
