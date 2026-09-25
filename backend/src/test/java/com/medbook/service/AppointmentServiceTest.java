package com.medbook.service;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.SpecialtyRepository;
import com.medbook.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AppointmentService} business logic.
 *
 * <p>These tests run against the real MySQL schema (no H2), following the existing
 * repository-test approach. The class is {@link Transactional}, so every test runs
 * inside one test transaction that Spring rolls back afterwards: helper rows
 * (users, doctors, patients, availability) and appointments never stay in the
 * database.</p>
 *
 * <p>Dates are always computed relative to {@link LocalDate#now()} so the suite keeps
 * working tomorrow: the "in the past" rule means fixed calendar dates would eventually
 * break.</p>
 */
@SpringBootTest
@Transactional
class AppointmentServiceTest {

    /** Consultation fee stored on test doctors; also used for the snapshot assertion. */
    private static final BigDecimal TEST_FEE = new BigDecimal("500.00");

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

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

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    /**
     * A bookable future date: exactly one week ahead, so it is also "the same weekday as
     * today" and always in the future.
     */
    private static LocalDate futureDate() {
        return LocalDate.now().plusDays(7);
    }

    /**
     * The next date (never today) that falls on the requested weekday.
     */
    private static LocalDate nextDateOnWeekday(DayOfWeek weekday) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != weekday) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * Convert a Java date to the MySQL {@code day_of_week} convention used by
     * {@code doctor_availability}: 0 = Sunday ... 6 = Saturday.
     */
    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue(); // 1 = Monday ... 7 = Sunday
        return (byte) (javaDayOfWeek == 7 ? 0 : javaDayOfWeek);
    }

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

    private Doctor createTestDoctor(String email, String specialtyName) {
        User user = createTestDoctorUser(email);
        // Unique suffix: the shared test database may already contain the demo seed data
        // (real specialties like "Cardiology"), and the name column is unique.
        Specialty specialty = specialtyRepository.save(
                new Specialty(specialtyName + "-" + System.nanoTime(), "Test specialty"));
        return doctorRepository.save(
                new Doctor(user, specialty, "LIC-" + System.nanoTime(), 5, TEST_FEE));
    }

    private Patient createTestPatient(String email) {
        User user = createTestPatientUser(email);
        return patientRepository.save(new Patient(user, LocalDate.of(1990, 1, 1)));
    }

    /**
     * Create a recurring availability slot for the weekday of {@code date}, covering
     * {@code start}-{@code end} indefinitely (no effective date bounds).
     */
    private DoctorAvailability createRecurringAvailability(
            Doctor doctor, LocalDate date, LocalTime start, LocalTime end) {
        DoctorAvailability availability =
                new DoctorAvailability(doctor, toMysqlDayOfWeek(date), start, end);
        availability.setRecurring(true);
        return availabilityRepository.save(availability);
    }

    // ------------------------------------------------------------------
    // Successful creation
    // ------------------------------------------------------------------

    @Test
    void createAppointmentSavesAppointmentAsScheduled() {
        Doctor doctor = createTestDoctor("doc-create-1@medbook.test", "Cardiology");
        Patient patient = createTestPatient("pat-create-1@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(appointment.getId()).isNotNull();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.getAppointmentDate()).isEqualTo(date);
        assertThat(appointment.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(appointment.getEndTime()).isEqualTo(LocalTime.of(11, 0));

        // Round-trip through the database: flush, detach, reload.
        entityManager.flush();
        entityManager.clear();
        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void createAppointmentStoresConsultationFeeSnapshot() {
        Doctor doctor = createTestDoctor("doc-create-2@medbook.test", "Dermatology");
        Patient patient = createTestPatient("pat-create-2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(appointment.getConsultationFeeSnapshot()).isEqualByComparingTo(TEST_FEE);
    }

    @Test
    void createAppointmentCreatesInitialStatusHistory() {
        Doctor doctor = createTestDoctor("doc-create-3@medbook.test", "Neurology");
        Patient patient = createTestPatient("pat-create-3@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        var history = statusHistoryRepository.findByAppointmentId(appointment.getId());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    // ------------------------------------------------------------------
    // Unknown / inactive participants
    // ------------------------------------------------------------------

    @Test
    void createAppointmentThrowsWhenDoctorNotFound() {
        Patient patient = createTestPatient("pat-error-1@medbook.test");

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), 999_999L, futureDate(), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void createAppointmentThrowsWhenDoctorInactive() {
        Doctor doctor = createTestDoctor("doc-error-1@medbook.test", "Pediatrics");
        Patient patient = createTestPatient("pat-error-2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        doctor.setActive(false);
        doctorRepository.save(doctor);

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorInactiveException.class);
    }

    @Test
    void createAppointmentThrowsWhenPatientNotFound() {
        Doctor doctor = createTestDoctor("doc-error-2@medbook.test", "Psychiatry");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThatThrownBy(() -> appointmentService.createAppointment(
                999_999L, doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void createAppointmentThrowsWhenPatientInactive() {
        Doctor doctor = createTestDoctor("doc-error-3@medbook.test", "Ophthalmology");
        Patient patient = createTestPatient("pat-error-3@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        patient.setActive(false);
        patientRepository.save(patient);

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(PatientInactiveException.class);
    }

    // ------------------------------------------------------------------
    // Invalid date / time window
    // ------------------------------------------------------------------

    @Test
    void createAppointmentThrowsWhenDateInPast() {
        Doctor doctor = createTestDoctor("doc-error-4@medbook.test", "Gastroenterology");
        Patient patient = createTestPatient("pat-error-4@medbook.test");

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), LocalDate.now().minusDays(1),
                LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("past");
    }

    @Test
    void createAppointmentThrowsWhenStartTimeIsNotBeforeEndTime() {
        Doctor doctor = createTestDoctor("doc-error-5@medbook.test", "Urology");
        Patient patient = createTestPatient("pat-error-5@medbook.test");

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), futureDate(), LocalTime.of(11, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start time");
    }

    // ------------------------------------------------------------------
    // Doctor availability
    // ------------------------------------------------------------------

    @Test
    void createAppointmentThrowsWhenOutsideAvailabilityWindow() {
        Doctor doctor = createTestDoctor("doc-avail-1@medbook.test", "Oncology");
        Patient patient = createTestPatient("pat-avail-1@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(13, 0), LocalTime.of(14, 0)))
                .isInstanceOf(DoctorUnavailableException.class);
    }

    @Test
    void createAppointmentThrowsWhenWindowReachesOutsideAvailability() {
        Doctor doctor = createTestDoctor("doc-avail-2@medbook.test", "Rheumatology");
        Patient patient = createTestPatient("pat-avail-2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(12, 0));

        // Partial overlap: 11:30-12:30 is not fully contained in 09:00-12:00.
        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(11, 30), LocalTime.of(12, 30)))
                .isInstanceOf(DoctorUnavailableException.class);
    }

    @Test
    void createAppointmentThrowsWhenDoctorHasNoAvailabilityOnThatWeekday() {
        Doctor doctor = createTestDoctor("doc-avail-3@medbook.test", "Endocrinology");
        Patient patient = createTestPatient("pat-avail-3@medbook.test");
        LocalDate date = futureDate();

        // Availability exists, but for another weekday.
        createRecurringAvailability(doctor, date.plusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorUnavailableException.class);
    }

    @Test
    void createAppointmentUsesMysqlDayOfWeekConvention() {
        // MySQL: 0 = Sunday ... 6 = Saturday. Java: 1 = Monday ... 7 = Sunday.
        LocalDate sunday = nextDateOnWeekday(DayOfWeek.SUNDAY);
        LocalDate monday = nextDateOnWeekday(DayOfWeek.MONDAY);

        Doctor sundayDoctor = createTestDoctor("doc-avail-4@medbook.test", "Hematology");
        Doctor mondayDoctor = createTestDoctor("doc-avail-5@medbook.test", "Nephrology");
        Patient patient = createTestPatient("pat-avail-4@medbook.test");

        DoctorAvailability sundaySlot =
                new DoctorAvailability(sundayDoctor, (byte) 0, LocalTime.of(9, 0), LocalTime.of(17, 0));
        sundaySlot.setRecurring(true);
        availabilityRepository.save(sundaySlot);

        DoctorAvailability mondaySlot =
                new DoctorAvailability(mondayDoctor, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
        mondaySlot.setRecurring(true);
        availabilityRepository.save(mondaySlot);

        // Sunday must be stored/queried as 0, otherwise this booking is rejected.
        Appointment sundayAppointment = appointmentService.createAppointment(
                patient.getId(), sundayDoctor.getId(), sunday, LocalTime.of(10, 0), LocalTime.of(11, 0));

        // Monday must be stored/queried as 1.
        Appointment mondayAppointment = appointmentService.createAppointment(
                patient.getId(), mondayDoctor.getId(), monday, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(sundayAppointment.getAppointmentDate().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(mondayAppointment.getAppointmentDate().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void createAppointmentRespectsEffectiveDateRange() {
        Doctor doctor = createTestDoctor("doc-range-1@medbook.test", "General Medicine");
        Patient patient = createTestPatient("pat-range-1@medbook.test");
        LocalDate date = futureDate();
        byte dayOfWeek = toMysqlDayOfWeek(date);

        // Recurring slot that has not started yet on the requested date.
        DoctorAvailability notYetEffective =
                new DoctorAvailability(doctor, dayOfWeek, LocalTime.of(9, 0), LocalTime.of(17, 0));
        notYetEffective.setRecurring(true);
        notYetEffective.setEffectiveFrom(date.plusDays(7));
        notYetEffective.setEffectiveUntil(date.plusDays(28));
        availabilityRepository.save(notYetEffective);

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorUnavailableException.class);

        // Recurring slot that already ended before the requested date.
        DoctorAvailability expired =
                new DoctorAvailability(doctor, dayOfWeek, LocalTime.of(10, 0), LocalTime.of(16, 0));
        expired.setRecurring(true);
        expired.setEffectiveFrom(date.minusDays(28));
        expired.setEffectiveUntil(date.minusDays(7));
        availabilityRepository.save(expired);

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorUnavailableException.class);

        // A slot whose effective range contains the requested date allows the booking.
        DoctorAvailability inRange =
                new DoctorAvailability(doctor, dayOfWeek, LocalTime.of(8, 0), LocalTime.of(18, 0));
        inRange.setRecurring(true);
        inRange.setEffectiveFrom(date.minusDays(1));
        inRange.setEffectiveUntil(date.plusDays(1));
        availabilityRepository.save(inRange);

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(11, 0), LocalTime.of(12, 0));

        assertThat(appointment.getId()).isNotNull();
    }

    @Test
    void createAppointmentTreatsNonRecurringSlotAsOneOffDate() {
        Doctor doctor = createTestDoctor("doc-range-2@medbook.test", "Pulmonology");
        Patient patient = createTestPatient("pat-range-2@medbook.test");
        LocalDate oneOffDate = futureDate();
        LocalDate sameWeekdayNextWeek = oneOffDate.plusDays(7);

        DoctorAvailability oneOffSlot =
                new DoctorAvailability(doctor, toMysqlDayOfWeek(oneOffDate),
                        LocalTime.of(9, 0), LocalTime.of(17, 0));
        oneOffSlot.setRecurring(false);
        oneOffSlot.setEffectiveFrom(oneOffDate);
        oneOffSlot.setEffectiveUntil(oneOffDate);
        availabilityRepository.save(oneOffSlot);

        // The same weekday one week later must NOT match a non-recurring slot.
        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient.getId(), doctor.getId(), sameWeekdayNextWeek,
                LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isInstanceOf(DoctorUnavailableException.class);

        // The one-off date itself is bookable.
        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), oneOffDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(appointment.getId()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Conflict detection
    // ------------------------------------------------------------------

    @Test
    void createAppointmentThrowsWhenOverlappingExistingAppointment() {
        Doctor doctor = createTestDoctor("doc-conflict-1@medbook.test", "Cardiology");
        Patient patient1 = createTestPatient("pat-conflict-1@medbook.test");
        Patient patient2 = createTestPatient("pat-conflict-2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        appointmentService.createAppointment(
                patient1.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient2.getId(), doctor.getId(), date, LocalTime.of(10, 30), LocalTime.of(11, 30)))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("overlapping");
    }

    @Test
    void createAppointmentAllowsBackToBackAppointments() {
        Doctor doctor = createTestDoctor("doc-conflict-2@medbook.test", "Dermatology");
        Patient patient1 = createTestPatient("pat-conflict-3@medbook.test");
        Patient patient2 = createTestPatient("pat-conflict-4@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        appointmentService.createAppointment(
                patient1.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        // 11:00-12:00 touches but does not overlap 10:00-11:00.
        Appointment second = appointmentService.createAppointment(
                patient2.getId(), doctor.getId(), date, LocalTime.of(11, 0), LocalTime.of(12, 0));

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void createAppointmentWritesNothingWhenBookingIsRejected() {
        Doctor doctor = createTestDoctor("doc-conflict-3@medbook.test", "Neurology");
        Patient patient1 = createTestPatient("pat-conflict-5@medbook.test");
        Patient patient2 = createTestPatient("pat-conflict-6@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment first = appointmentService.createAppointment(
                patient1.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> appointmentService.createAppointment(
                patient2.getId(), doctor.getId(), date, LocalTime.of(10, 15), LocalTime.of(10, 45)))
                .isInstanceOf(AppointmentConflictException.class);

        // No partial booking was written: one appointment, one history row.
        List<Appointment> appointmentsForDoctor = appointmentRepository.findByDoctorId(doctor.getId());
        assertThat(appointmentsForDoctor).hasSize(1);
        assertThat(appointmentsForDoctor.get(0).getId()).isEqualTo(first.getId());
        assertThat(statusHistoryRepository.findByAppointmentId(first.getId())).hasSize(1);
    }

    @Test
    void createAppointmentAllowsRebookingAfterCancellation() {
        Doctor doctor = createTestDoctor("doc-conflict-4@medbook.test", "Psychiatry");
        Patient patient1 = createTestPatient("pat-conflict-7@medbook.test");
        Patient patient2 = createTestPatient("pat-conflict-8@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment first = appointmentService.createAppointment(
                patient1.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        appointmentService.cancelAppointment(first.getId(), "Patient rescheduled the visit");

        // The cancelled slot is free again.
        Appointment second = appointmentService.createAppointment(
                patient2.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(second.getId()).isNotNull();
        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    // ------------------------------------------------------------------
    // Cancellation
    // ------------------------------------------------------------------

    @Test
    void cancelAppointmentUpdatesStatusAndCancellationReason() {
        Doctor doctor = createTestDoctor("doc-cancel-1@medbook.test", "Urology");
        Patient patient = createTestPatient("pat-cancel-1@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        Appointment cancelled = appointmentService.cancelAppointment(
                appointment.getId(), "Doctor called into an emergency");

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Doctor called into an emergency");

        entityManager.flush();
        entityManager.clear();
        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(reloaded.getCancellationReason()).isEqualTo("Doctor called into an emergency");
    }

    @Test
    void cancelAppointmentAppendsStatusHistoryWithoutLosingEarlierEntries() {
        Doctor doctor = createTestDoctor("doc-cancel-2@medbook.test", "Oncology");
        Patient patient = createTestPatient("pat-cancel-2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        appointmentService.cancelAppointment(appointment.getId(), "Patient feeling better");

        var history = statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(appointment.getId());

        // Creation row is preserved and the cancellation row is appended.
        assertThat(history).hasSize(2);
        assertThat(history).extracting(AppointmentStatusHistory::getStatus)
                .containsExactlyInAnyOrder(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED);
        assertThat(history).anySatisfy(row ->
                assertThat(row.getNotes()).isEqualTo("Patient feeling better"));
    }

    @Test
    void cancelAppointmentThrowsWhenReasonIsMissing() {
        Doctor doctor = createTestDoctor("doc-cancel-3@medbook.test", "Rheumatology");
        Patient patient = createTestPatient("pat-cancel-3@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(appointment.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cancellation reason");
    }

    @Test
    void cancelAppointmentThrowsWhenAppointmentNotFound() {
        assertThatThrownBy(() -> appointmentService.cancelAppointment(999_999L, "No longer needed"))
                .isInstanceOf(AppointmentNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void cancelAppointmentThrowsWhenAlreadyCancelled() {
        Doctor doctor = createTestDoctor("doc-cancel-4@medbook.test", "Endocrinology");
        Patient patient = createTestPatient("pat-cancel-4@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        appointmentService.cancelAppointment(appointment.getId(), "First cancellation");

        assertThatThrownBy(() -> appointmentService.cancelAppointment(appointment.getId(), "Second cancellation"))
                .isInstanceOf(InvalidAppointmentStatusException.class)
                .hasMessageContaining("already cancelled");

        // History stays append-only: still exactly one SCHEDULED row and one CANCELLED row.
        assertThat(statusHistoryRepository.findByAppointmentId(appointment.getId())).hasSize(2);
    }

    @Test
    void cancelAppointmentThrowsWhenAppointmentIsInTerminalState() {
        Doctor doctor = createTestDoctor("doc-cancel-5@medbook.test", "Nephrology");
        Patient patient = createTestPatient("pat-cancel-5@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Appointment appointment = appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        // COMPLETED is a terminal state. Completing an appointment is not part of this
        // step, so the fixture sets the status directly to reach that state.
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        assertThatThrownBy(() -> appointmentService.cancelAppointment(appointment.getId(), "Too late"))
                .isInstanceOf(InvalidAppointmentStatusException.class)
                .hasMessageContaining("COMPLETED");
    }
}
