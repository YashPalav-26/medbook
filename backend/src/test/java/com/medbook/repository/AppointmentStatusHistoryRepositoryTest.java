package com.medbook.repository;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;







@SpringBootTest
@Transactional
class AppointmentStatusHistoryRepositoryTest {

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;




    private User createTestDoctorUser(String email) {
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        User user = new User(email, "hashed-password", "Test", "Doctor", "+919876543210");
        user.addRole(doctorRole);
        return userRepository.save(user);
    }




    private User createTestPatientUser(String email) {
        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new IllegalStateException("PATIENT role not seeded"));
        User user = new User(email, "hashed-password", "Test", "Patient", "+919876543211");
        user.addRole(patientRole);
        return userRepository.save(user);
    }




    private Specialty createTestSpecialty(String name) {


        return specialtyRepository.save(new Specialty(name + "-" + System.nanoTime(), "Test specialty"));
    }




    private Doctor createTestDoctor(String email, String specialtyName) {
        User user = createTestDoctorUser(email);
        Specialty specialty = createTestSpecialty(specialtyName);
        Doctor doctor = new Doctor(user, specialty, "LIC-" + System.nanoTime(), 5, new BigDecimal("500.00"));
        return doctorRepository.save(doctor);
    }




    private Patient createTestPatient(String email) {
        User user = createTestPatientUser(email);
        return patientRepository.save(new Patient(user, java.time.LocalDate.of(1990, 1, 1)));
    }




    private Appointment createTestAppointment(Doctor doctor, Patient patient) {
        return appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));
    }

    @Test
    void findAllExecutesAgainstRealSchema() {

        assertThat(statusHistoryRepository.findAll()).isNotNull();
    }

    @Test
    void savedStatusHistoryIsRetrievableById() {
        Doctor doctor = createTestDoctor("doctor1@medbook.test", "Cardiology");
        Patient patient = createTestPatient("patient1@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.CONFIRMED
        );
        history.setNotes("Doctor confirmed the appointment");
        AppointmentStatusHistory saved = statusHistoryRepository.save(history);

        assertThat(saved.getId()).isNotNull();

        var reloaded = statusHistoryRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reloaded.get().getNotes()).isEqualTo("Doctor confirmed the appointment");
    }

    @Test
    void findByAppointmentIdReturnsAllHistoryRecords() {
        Doctor doctor = createTestDoctor("doctor2@medbook.test", "General");
        Patient patient = createTestPatient("patient2@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);


        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.CONFIRMED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.COMPLETED));

        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentId(appointment.getId());

        assertThat(history).hasSize(3);
    }

    @Test
    void findByAppointmentIdOrderByChangedAtAscReturnsOrderedHistory() {
        Doctor doctor = createTestDoctor("doctor3@medbook.test", "Neurology");
        Patient patient = createTestPatient("patient3@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);


        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED));

        AppointmentStatusHistory confirmed = new AppointmentStatusHistory(appointment, AppointmentStatus.CONFIRMED);
        confirmed.setNotes("Confirmed by admin");
        statusHistoryRepository.save(confirmed);

        AppointmentStatusHistory completed = new AppointmentStatusHistory(appointment, AppointmentStatus.COMPLETED);
        completed.setNotes("Consultation completed");
        statusHistoryRepository.save(completed);

        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(appointment.getId());

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(history.get(1).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.get(2).getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void appointmentRelationshipToAppointmentIsNavigable() {
        Doctor doctor = createTestDoctor("doctor4@medbook.test", "Pediatrics");
        Patient patient = createTestPatient("patient4@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.CONFIRMED
        );
        history.setNotes("Confirmed by doctor");
        AppointmentStatusHistory saved = statusHistoryRepository.save(history);

        var reloaded = statusHistoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAppointment()).isNotNull();
        assertThat(reloaded.getAppointment().getId()).isEqualTo(appointment.getId());
    }

    @Test
    void statusHistoryDefaultsToNullChangedByUser() {
        Doctor doctor = createTestDoctor("doctor5@medbook.test", "Psychiatry");
        Patient patient = createTestPatient("patient5@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.SCHEDULED
        );

        AppointmentStatusHistory saved = statusHistoryRepository.save(history);

        var reloaded = statusHistoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getChangedByUser()).isNull();
    }

    @Test
    void statusHistoryWithChangedByUserIsRetrievable() {
        Doctor doctor = createTestDoctor("doctor6@medbook.test", "Ophthalmology");
        Patient patient = createTestPatient("patient6@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);


        User changedByUser = createTestDoctorUser("history-admin-" + System.nanoTime() + "@medbook.test");

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.CONFIRMED
        );
        history.setChangedByUser(changedByUser);
        history.setNotes("Confirmed by admin user");

        AppointmentStatusHistory saved = statusHistoryRepository.save(history);

        var reloaded = statusHistoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getChangedByUser()).isNotNull();
        assertThat(reloaded.getChangedByUser().getId()).isEqualTo(changedByUser.getId());
    }

    @Test
    void savedStatusHistoryHasTimestampPopulatedByDatabase() {
        Doctor doctor = createTestDoctor("doctor7@medbook.test", "Gastroenterology");
        Patient patient = createTestPatient("patient7@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.CONFIRMED
        );

        AppointmentStatusHistory saved = statusHistoryRepository.save(history);
        entityManager.flush();
        entityManager.refresh(saved);

        assertThat(saved.getChangedAt()).as("changed_at populated by DB default").isNotNull();
    }

    @Test
    void deleteStatusHistoryRecord() {
        Doctor doctor = createTestDoctor("doctor8@medbook.test", "Urology");
        Patient patient = createTestPatient("patient8@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.CONFIRMED
        );

        AppointmentStatusHistory saved = statusHistoryRepository.save(history);
        Long savedId = saved.getId();

        statusHistoryRepository.delete(saved);

        assertThat(statusHistoryRepository.findById(savedId)).isEmpty();
    }

    @Test
    void multipleAppointmentsCanHaveSeparateStatusHistory() {
        Doctor doctor = createTestDoctor("doctor9@medbook.test", "Oncology");
        Patient patient1 = createTestPatient("patient9@medbook.test");
        Patient patient2 = createTestPatient("patient10@medbook.test");

        Appointment appointment1 = createTestAppointment(doctor, patient1);
        Appointment appointment2 = createTestAppointment(doctor, patient2);


        statusHistoryRepository.save(new AppointmentStatusHistory(appointment1, AppointmentStatus.SCHEDULED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment1, AppointmentStatus.CONFIRMED));


        statusHistoryRepository.save(new AppointmentStatusHistory(appointment2, AppointmentStatus.SCHEDULED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment2, AppointmentStatus.COMPLETED));

        List<AppointmentStatusHistory> history1 = statusHistoryRepository.findByAppointmentId(appointment1.getId());
        List<AppointmentStatusHistory> history2 = statusHistoryRepository.findByAppointmentId(appointment2.getId());

        assertThat(history1).hasSize(2);
        assertThat(history2).hasSize(2);
    }

    @Test
    void appointmentStatusHistoryWithNullNotesIsRetrievable() {
        Doctor doctor = createTestDoctor("doctor10@medbook.test", "Rheumatology");
        Patient patient = createTestPatient("patient11@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, AppointmentStatus.SCHEDULED
        );


        AppointmentStatusHistory saved = statusHistoryRepository.save(history);

        var reloaded = statusHistoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getNotes()).isNull();
    }

    @Test
    void statusHistoryWithSameAppointmentDifferentStatuses() {
        Doctor doctor = createTestDoctor("doctor11@medbook.test", "Endocrinology");
        Patient patient = createTestPatient("patient12@medbook.test");
        Appointment appointment = createTestAppointment(doctor, patient);


        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.CONFIRMED));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.NO_SHOW));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.CANCELLED));

        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentId(appointment.getId());

        assertThat(history).hasSize(4);
    }
}
