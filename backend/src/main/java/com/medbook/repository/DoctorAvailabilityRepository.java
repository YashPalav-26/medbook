package com.medbook.repository;

import com.medbook.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;







@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {









    List<DoctorAvailability> findByDoctorId(Long doctorId);









    List<DoctorAvailability> findByDoctorIdAndDayOfWeek(Long doctorId, byte dayOfWeek);







    List<DoctorAvailability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);





    boolean existsByDoctorIdAndDayOfWeekAndStartTimeAndEndTime(
            Long doctorId, byte dayOfWeek, java.time.LocalTime startTime, java.time.LocalTime endTime);






    boolean existsByDoctorIdAndDayOfWeekAndStartTimeAndEndTimeAndIdNot(
            Long doctorId, byte dayOfWeek, java.time.LocalTime startTime, java.time.LocalTime endTime, Long id);
}
