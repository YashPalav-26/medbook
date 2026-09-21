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
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transaction and locking tests for {@link AppointmentService}.
 *
 * <p><strong>Why this class is not {@code @Transactional}:</strong> the tests in
 * {@link AppointmentServiceTest} run inside one test transaction that is always rolled
 * back, which is fast but hides what the database really does. These tests need real
 * transaction boundaries: real commits, a real rollback after a failure, and two
 * simultaneous database transactions for the lock test. Because of that, fixtures are
 * committed in {@link #setUp()} and every row created here is deleted again in
 * {@link #tearDown()}, so no test data stays in the database.</p>
 */
@SpringBootTest
class AppointmentServiceTransactionTest {

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
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private LocalDate appointmentDate;
    private Long specialtyId;
    private Long doctorId;
    private Long patientId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Fixtures are committed on purpose: the service opens its own transaction for
        // each call, and another transaction can only see committed rows.
        transactionTemplate.executeWithoutResult(status -> {
            Specialty specialty = specialtyRepository.save(
                    new Specialty("Transaction Test Specialty", "Test specialty"));
            specialtyId = specialty.getId();

            User doctorUser = createUser("tx-doctor@medbook.test", "DOCTOR", "+919000000001");
            User patientUser = createUser("tx-patient@medbook.test", "PATIENT", "+919000000002");
            userIds.add(doctorUser.getId());
            userIds.add(patientUser.getId());

            Doctor doctor = doctorRepository.save(new Doctor(
                    doctorUser, specialty, "LIC-TX-" + System.nanoTime(), 8, TEST_FEE));
            Patient patient = patientRepository.save(
                    new Patient(patientUser, LocalDate.of(1985, 5, 5)));
            doctorId = doctor.getId();
            patientId = patient.getId();

            appointmentDate = LocalDate.now().plusDays(7);
            DoctorAvailability availability = new DoctorAvailability(
                    doctor, toMysqlDayOfWeek(appointmentDate), LocalTime.of(9, 0), LocalTime.of(17, 0));
            availability.setRecurring(true);
            availabilityRepository.save(availability);
        });
    }

    @AfterEach
    void tearDown() {
        // Delete in foreign-key order: history -> appointments -> availability -> profiles.
        if (doctorId != null) {
            deleteAppointmentsOfDoctor();
            availabilityRepository.deleteAll(availabilityRepository.findByDoctorId(doctorId));
            doctorRepository.deleteById(doctorId);
        }
        if (patientId != null) {
            appointmentRepository.deleteAll(appointmentRepository.findByPatientId(patientId));
            patientRepository.deleteById(patientId);
        }
        if (specialtyId != null) {
            specialtyRepository.deleteById(specialtyId);
        }
        for (Long userId : userIds) {
            userRepository.deleteById(userId);
        }
    }

    private void deleteAppointmentsOfDoctor() {
        List<Appointment> appointments = appointmentRepository.findByDoctorId(doctorId);
        for (Appointment appointment : appointments) {
            statusHistoryRepository.deleteAll(
                    statusHistoryRepository.findByAppointmentId(appointment.getId()));
        }
        appointmentRepository.deleteAll(appointments);
    }

    private User createUser(String email, String roleName, String phoneNumber) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));
        User user = new User(email, "hashed-password", "Transaction", "Test", phoneNumber);
        user.addRole(role);
        return userRepository.save(user);
    }

    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue();
        return (byte) (javaDayOfWeek == 7 ? 0 : javaDayOfWeek);
    }

    // ------------------------------------------------------------------
    // Real commits
    // ------------------------------------------------------------------

    @Test
    void createAppointmentCommitsAppointmentAndStatusHistory() {
        Appointment created = appointmentService.createAppointment(
                patientId, doctorId, appointmentDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(created.getId()).isNotNull();

        // Fresh reads in their own transactions prove the rows are really committed.
        List<Appointment> persisted = appointmentRepository.findByDoctorId(doctorId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getId()).isEqualTo(created.getId());
        assertThat(persisted.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentId(created.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void cancelAppointmentCommitsCancelledStatusAndHistory() {
        Appointment created = appointmentService.createAppointment(
                patientId, doctorId, appointmentDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        appointmentService.cancelAppointment(created.getId(), "Committed cancellation");

        // The commit also proves the cancellation_reason CHECK constraint is satisfied.
        Appointment reloaded = appointmentRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(reloaded.getCancellationReason()).isEqualTo("Committed cancellation");

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentId(created.getId());
        assertThat(history).hasSize(2);
        assertThat(history).extracting(AppointmentStatusHistory::getStatus)
                .containsExactlyInAnyOrder(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED);
    }

    // ------------------------------------------------------------------
    // Real rollbacks
    // ------------------------------------------------------------------

    @Test
    void createAppointmentRollsBackWhenTheTransactionFailsAfterCreation() {
        LocalTime start = LocalTime.of(11, 0);
        LocalTime end = LocalTime.of(12, 0);

        // The service joins this transaction, inserts the appointment and its history row,
        // and then the surrounding transaction fails on purpose.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            appointmentService.createAppointment(patientId, doctorId, appointmentDate, start, end);
            throw new IllegalStateException("simulated failure after the appointment was inserted");
        })).isInstanceOf(IllegalStateException.class);

        // Because the inserts ran inside the rolled-back transaction, nothing is left.
        assertThat(appointmentRepository.findByDoctorId(doctorId)).isEmpty();
        assertThat(appointmentRepository.findConflictingAppointments(
                doctorId, appointmentDate, start, end, AppointmentStatus.CANCELLED)).isEmpty();
    }

    @Test
    void createAppointmentLeavesNoResidueWhenBookingIsRejected() {
        appointmentService.createAppointment(
                patientId, doctorId, appointmentDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        // Real transaction, real conflict, real rollback.
        assertThatThrownBy(() -> appointmentService.createAppointment(
                patientId, doctorId, appointmentDate, LocalTime.of(10, 30), LocalTime.of(11, 30)))
                .isInstanceOf(AppointmentConflictException.class);

        List<Appointment> persisted = appointmentRepository.findByDoctorId(doctorId);
        assertThat(persisted).hasSize(1);
        assertThat(statusHistoryRepository.findByAppointmentId(persisted.get(0).getId())).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Transaction / lock configuration
    // ------------------------------------------------------------------

    @Test
    void appointmentServiceMethodsAreTransactional() throws Exception {
        Method create = AppointmentService.class.getMethod(
                "createAppointment", Long.class, Long.class, LocalDate.class, LocalTime.class, LocalTime.class);
        Method cancel = AppointmentService.class.getMethod("cancelAppointment", Long.class, String.class);

        // Without @Transactional the service could not open a transaction on its own,
        // and findByIdForUpdate would fail with TransactionRequiredException.
        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(cancel.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void repositoryLockMethodsUsePessimisticWrite() throws Exception {
        assertPessimisticWrite(DoctorRepository.class, "findByIdForUpdate");
        assertPessimisticWrite(AppointmentRepository.class, "findByIdForUpdate");
    }

    private static void assertPessimisticWrite(Class<?> repository, String methodName) throws Exception {
        Method method = repository.getMethod(methodName, Long.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock)
                .as("%s.%s must be annotated with @Lock", repository.getSimpleName(), methodName)
                .isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void doctorRowLockBlocksAConcurrentTransactionUntilItCommits() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        AtomicBoolean secondTransactionFinished = new AtomicBoolean(false);
        AtomicReference<Throwable> secondTransactionFailure = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Transaction 1: takes the doctor row lock and holds it.
            Future<?> lockHolder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                doctorRepository.findByIdForUpdate(doctorId).orElseThrow();
                lockAcquired.countDown();
                await(releaseLock);
            }));

            assertThat(lockAcquired.await(20, TimeUnit.SECONDS))
                    .as("first transaction must acquire the doctor row lock")
                    .isTrue();

            // Transaction 2: tries to lock the same row while transaction 1 still holds it.
            Future<?> blocked = executor.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(
                            status -> doctorRepository.findByIdForUpdate(doctorId).orElseThrow());
                    secondTransactionFinished.set(true);
                } catch (Throwable failure) {
                    secondTransactionFailure.set(failure);
                }
            });

            // If SELECT ... FOR UPDATE were missing, transaction 2 would finish immediately.
            Thread.sleep(700);
            assertThat(secondTransactionFinished.get())
                    .as("second transaction must be blocked while the first one holds the row lock")
                    .isFalse();

            releaseLock.countDown();

            lockHolder.get(20, TimeUnit.SECONDS);
            blocked.get(20, TimeUnit.SECONDS);

            assertThat(secondTransactionFailure.get()).isNull();
            assertThat(secondTransactionFinished.get()).isTrue();
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
