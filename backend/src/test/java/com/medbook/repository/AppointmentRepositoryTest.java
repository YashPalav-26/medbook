package com.medbook.repository;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
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
class AppointmentRepositoryTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private RoleRepository roleRepository;

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

    @Test
    void findAllExecutesAgainstRealSchema() {


        assertThat(appointmentRepository.findAll()).isNotNull();
    }

    @Test
    void savedAppointmentIsRetrievableById() {
        Doctor doctor = createTestDoctor("doctor1@medbook.test", "Cardiology");
        Patient patient = createTestPatient("patient1@medbook.test");

        Appointment appointment = new Appointment(
                patient,
                doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);

        assertThat(saved.getId()).isNotNull();

        var reloaded = appointmentRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAppointmentDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(reloaded.get().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.get().getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(reloaded.get().getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(reloaded.get().getConsultationFeeSnapshot()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    void findByDoctorIdReturnsAllAppointmentsForDoctor() {
        Doctor doctor = createTestDoctor("doctor2@medbook.test", "Dermatology");
        Patient patient = createTestPatient("patient2@medbook.test");


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(11, 0), LocalTime.of(12, 0),
                new BigDecimal("500.00")
        ));


        Doctor otherDoctor = createTestDoctor("doctor3@medbook.test", "Neurology");
        Patient otherPatient = createTestPatient("patient3@medbook.test");
        appointmentRepository.save(new Appointment(
                otherPatient, otherDoctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(14, 0), LocalTime.of(15, 0),
                new BigDecimal("600.00")
        ));

        List<Appointment> appointments = appointmentRepository.findByDoctorId(doctor.getId());

        assertThat(appointments).hasSize(2);
    }

    @Test
    void findByPatientIdReturnsAllAppointmentsForPatient() {
        Doctor doctor = createTestDoctor("doctor4@medbook.test", "Pediatrics");
        Patient patient = createTestPatient("patient4@medbook.test");


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                new BigDecimal("400.00")
        ));


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(10, 0), LocalTime.of(10, 30),
                new BigDecimal("400.00")
        ));


        Patient otherPatient = createTestPatient("patient5@medbook.test");
        appointmentRepository.save(new Appointment(
                otherPatient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(11, 0), LocalTime.of(11, 30),
                new BigDecimal("400.00")
        ));

        List<Appointment> appointments = appointmentRepository.findByPatientId(patient.getId());

        assertThat(appointments).hasSize(2);
    }

    @Test
    void findByStatusReturnsAppointmentsWithMatchingStatus() {
        Doctor doctor = createTestDoctor("doctor5@medbook.test", "Psychiatry");
        Patient patient = createTestPatient("patient6@medbook.test");


        Appointment scheduled = appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));


        Appointment confirmed = appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(11, 0), LocalTime.of(12, 0),
                new BigDecimal("500.00")
        ));
        confirmed.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(confirmed);


        Appointment cancelled = appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 17),
                LocalTime.of(14, 0), LocalTime.of(15, 0),
                new BigDecimal("500.00")
        ));
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        cancelled.setCancellationReason("Patient requested reschedule");
        appointmentRepository.save(cancelled);

        List<Appointment> scheduledAppointments = appointmentRepository.findByStatus(AppointmentStatus.SCHEDULED);
        List<Appointment> confirmedAppointments = appointmentRepository.findByStatus(AppointmentStatus.CONFIRMED);
        List<Appointment> cancelledAppointments = appointmentRepository.findByStatus(AppointmentStatus.CANCELLED);



        assertThat(scheduledAppointments)
                .extracting(Appointment::getId)
                .contains(scheduled.getId())
                .doesNotContain(confirmed.getId(), cancelled.getId());

        assertThat(confirmedAppointments)
                .extracting(Appointment::getId)
                .contains(confirmed.getId())
                .doesNotContain(scheduled.getId(), cancelled.getId());

        assertThat(cancelledAppointments)
                .extracting(Appointment::getId)
                .contains(cancelled.getId())
                .doesNotContain(scheduled.getId(), confirmed.getId());
    }

    @Test
    void findByDoctorIdAndAppointmentDateFiltersCorrectly() {
        Doctor doctor = createTestDoctor("doctor6@medbook.test", "Ophthalmology");
        Patient patient = createTestPatient("patient7@medbook.test");


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));

        List<Appointment> oct15 = appointmentRepository.findByDoctorIdAndAppointmentDate(
                doctor.getId(), LocalDate.of(2026, 10, 15)
        );
        List<Appointment> oct16 = appointmentRepository.findByDoctorIdAndAppointmentDate(
                doctor.getId(), LocalDate.of(2026, 10, 16)
        );

        assertThat(oct15).hasSize(1);
        assertThat(oct16).hasSize(1);
    }

    @Test
    void findByPatientIdAndAppointmentDateFiltersCorrectly() {
        Doctor doctor = createTestDoctor("doctor7@medbook.test", "Gastroenterology");
        Patient patient = createTestPatient("patient8@medbook.test");


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));


        appointmentRepository.save(new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(11, 0), LocalTime.of(12, 0),
                new BigDecimal("500.00")
        ));

        List<Appointment> oct15 = appointmentRepository.findByPatientIdAndAppointmentDate(
                patient.getId(), LocalDate.of(2026, 10, 15)
        );
        List<Appointment> oct16 = appointmentRepository.findByPatientIdAndAppointmentDate(
                patient.getId(), LocalDate.of(2026, 10, 16)
        );

        assertThat(oct15).hasSize(1);
        assertThat(oct16).hasSize(1);
    }

    @Test
    void savedAppointmentHasDefaultStatusScheduled() {
        Doctor doctor = createTestDoctor("doctor8@medbook.test", "Urology");
        Patient patient = createTestPatient("patient9@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);

        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void appointmentStatusHistoryRelationshipIsNavigable() {
        Doctor doctor = createTestDoctor("doctor9@medbook.test", "Oncology");
        Patient patient = createTestPatient("patient10@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        entityManager.flush();
        entityManager.refresh(saved);


        AppointmentStatusHistory history = new AppointmentStatusHistory(
                saved, AppointmentStatus.CONFIRMED
        );
        history.setNotes("Doctor confirmed appointment");
        statusHistoryRepository.save(history);

        entityManager.flush();
        entityManager.refresh(saved);


        Appointment reloaded = appointmentRepository.findById(saved.getId()).orElseThrow();
        List<AppointmentStatusHistory> historyList = reloaded.getStatusHistory();

        assertThat(historyList).hasSize(1);
        assertThat(historyList.get(0).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(historyList.get(0).getNotes()).isEqualTo("Doctor confirmed appointment");
    }

    @Test
    void appointmentRelationshipToDoctorIsNavigable() {
        Doctor doctor = createTestDoctor("doctor10@medbook.test", "Rheumatology");
        Patient patient = createTestPatient("patient11@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        entityManager.flush();
        entityManager.refresh(saved);


        entityManager.clear();

        Appointment reloaded = appointmentRepository.findById(saved.getId()).orElseThrow();


        Doctor loadedDoctor = reloaded.getDoctor();

        assertThat(loadedDoctor).isNotNull();
        assertThat(loadedDoctor.getId()).isEqualTo(doctor.getId());
    }

    @Test
    void appointmentRelationshipToPatientIsNavigable() {
        Doctor doctor = createTestDoctor("doctor11@medbook.test", "Endocrinology");
        Patient patient = createTestPatient("patient12@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        entityManager.flush();
        entityManager.refresh(saved);

        entityManager.clear();

        Appointment reloaded = appointmentRepository.findById(saved.getId()).orElseThrow();


        Patient loadedPatient = reloaded.getPatient();

        assertThat(loadedPatient).isNotNull();
        assertThat(loadedPatient.getId()).isEqualTo(patient.getId());
    }

    @Test
    void appointmentTimestampsPopulatedByDatabase() {
        Doctor doctor = createTestDoctor("doctor12@medbook.test", "Hematology");
        Patient patient = createTestPatient("patient13@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        entityManager.flush();
        entityManager.refresh(saved);

        assertThat(saved.getCreatedAt()).as("created_at populated by DB default").isNotNull();
        assertThat(saved.getUpdatedAt()).as("updated_at populated by DB default").isNotNull();
    }

    @Test
    void deleteRemovesAppointment() {
        Doctor doctor = createTestDoctor("doctor13@medbook.test", "Infectious Disease");
        Patient patient = createTestPatient("patient14@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        Long savedId = saved.getId();

        appointmentRepository.delete(saved);

        assertThat(appointmentRepository.findById(savedId)).isEmpty();
    }

    @Test
    void multipleDoctorsCanHaveSeparateAppointments() {
        Doctor doctor1 = createTestDoctor("doctor14@medbook.test", "Allergy");
        Doctor doctor2 = createTestDoctor("doctor15@medbook.test", "Dentistry");

        Patient patient1 = createTestPatient("patient15@medbook.test");
        Patient patient2 = createTestPatient("patient16@medbook.test");


        appointmentRepository.save(new Appointment(
                patient1, doctor1,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        ));
        appointmentRepository.save(new Appointment(
                patient2, doctor1,
                LocalDate.of(2026, 10, 16),
                LocalTime.of(11, 0), LocalTime.of(12, 0),
                new BigDecimal("500.00")
        ));


        appointmentRepository.save(new Appointment(
                patient1, doctor2,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(14, 0), LocalTime.of(15, 0),
                new BigDecimal("600.00")
        ));

        List<Appointment> doctor1Appts = appointmentRepository.findByDoctorId(doctor1.getId());
        List<Appointment> doctor2Appts = appointmentRepository.findByDoctorId(doctor2.getId());

        assertThat(doctor1Appts).hasSize(2);
        assertThat(doctor2Appts).hasSize(1);
    }

    @Test
    void appointmentNotesAndReasonAreOptional() {
        Doctor doctor = createTestDoctor("doctor16@medbook.test", "General");
        Patient patient = createTestPatient("patient17@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );
        appointment.setReason("Routine checkup");
        appointment.setNotes("Patient reports no specific issues");

        Appointment saved = appointmentRepository.save(appointment);

        var reloaded = appointmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReason()).isEqualTo("Routine checkup");
        assertThat(reloaded.getNotes()).isEqualTo("Patient reports no specific issues");
    }

    @Test
    void appointmentCancellationReasonRequiredForCancelledStatus() {
        Doctor doctor = createTestDoctor("doctor17@medbook.test", "Cardiology");
        Patient patient = createTestPatient("patient18@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason("Patient cancelled due to scheduling conflict");

        Appointment saved = appointmentRepository.save(appointment);

        var reloaded = appointmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(reloaded.getCancellationReason()).isEqualTo("Patient cancelled due to scheduling conflict");
    }

    @Test
    void appointmentStatusHistoryFindByAppointmentIdReturnsAllHistoryRecords() {
        Doctor doctor = createTestDoctor("doctor18@medbook.test", "Dermatology");
        Patient patient = createTestPatient("patient19@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        Long appointmentId = saved.getId();


        statusHistoryRepository.save(new AppointmentStatusHistory(saved, AppointmentStatus.SCHEDULED));
        statusHistoryRepository.save(new AppointmentStatusHistory(saved, AppointmentStatus.CONFIRMED));
        statusHistoryRepository.save(new AppointmentStatusHistory(saved, AppointmentStatus.COMPLETED));


        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentId(appointmentId);

        assertThat(history).hasSize(3);
    }

    @Test
    void appointmentStatusHistoryFindByAppointmentIdOrderByChangedAtAscReturnsOrderedHistory() {
        Doctor doctor = createTestDoctor("doctor19@medbook.test", "Neurology");
        Patient patient = createTestPatient("patient20@medbook.test");

        Appointment appointment = new Appointment(
                patient, doctor,
                LocalDate.of(2026, 10, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                new BigDecimal("500.00")
        );

        Appointment saved = appointmentRepository.save(appointment);
        Long appointmentId = saved.getId();


        statusHistoryRepository.save(new AppointmentStatusHistory(saved, AppointmentStatus.SCHEDULED));
        AppointmentStatusHistory confirmed = new AppointmentStatusHistory(saved, AppointmentStatus.CONFIRMED);
        confirmed.setNotes("Confirmed by doctor");
        statusHistoryRepository.save(confirmed);

        AppointmentStatusHistory completed = new AppointmentStatusHistory(saved, AppointmentStatus.COMPLETED);
        completed.setNotes("Consultation done");
        statusHistoryRepository.save(completed);


        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(appointmentId);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(history.get(1).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.get(2).getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }
}
