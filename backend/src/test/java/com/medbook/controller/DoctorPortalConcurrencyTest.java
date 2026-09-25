package com.medbook.controller;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.SpecialtyRepository;
import com.medbook.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrency test for the doctor status transitions (Step 21) over real MySQL.
 *
 * <p><strong>Why this class is not {@code @Transactional}:</strong> the two confirming
 * requests run on worker threads, which can never see the uncommitted rows of a test
 * transaction. Following the {@link com.medbook.service.AppointmentServiceTransactionTest}
 * pattern, fixtures are committed on purpose and every row created here is deleted again,
 * so no test data stays in the database.</p>
 *
 * <p>The race itself proves the pessimistic row lock: exactly one of the two simultaneous
 * confirm requests may win (200); the loser must observe the winner's status and be
 * rejected with 409 — never two CONFIRMED audit rows.</p>
 */
@SpringBootTest
class DoctorPortalConcurrencyTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate tx;

    private Long specialtyId;
    private Long doctorAUserId;
    private Long doctorBUserId;
    private Long patientUserId;
    private Long doctorAId;
    private Long patientId;
    private String doctorAToken;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            Specialty specialty = specialtyRepository.save(
                    new Specialty("Concurrency Cardio " + System.nanoTime(), "test"));
            specialtyId = specialty.getId();

            User doctorAUser = new User("concurrency-doc-a@medbook.test", "hashed", "Rae", "Lock", "+919999220001");
            doctorAUser.addRole(roleRepository.findByName("DOCTOR").orElseThrow());
            doctorAUser = userRepository.save(doctorAUser);
            doctorAUserId = doctorAUser.getId();

            User doctorBUser = new User("concurrency-doc-b@medbook.test", "hashed", "Rex", "Lock", "+919999220002");
            doctorBUser.addRole(roleRepository.findByName("DOCTOR").orElseThrow());
            doctorBUser = userRepository.save(doctorBUser);
            doctorBUserId = doctorBUser.getId();

            User patientUser = new User("concurrency-pat@medbook.test", "hashed", "Paul", "Lock", "+919999220003");
            patientUser.addRole(roleRepository.findByName("PATIENT").orElseThrow());
            patientUser = userRepository.save(patientUser);
            patientUserId = patientUser.getId();

            Doctor doctorA = doctorRepository.save(new Doctor(
                    doctorAUser, specialty, "LIC-CONC-A-" + System.nanoTime(), 5, new BigDecimal("120.00")));
            doctorAId = doctorA.getId();

            Patient patient = patientRepository.save(new Patient(patientUser, LocalDate.of(1990, 1, 1)));
            patientId = patient.getId();
        });

        doctorAToken = tokenFor(doctorAUserId, "ROLE_DOCTOR");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(post("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorAToken))
                .build();
    }

    @AfterEach
    void tearDown() {
        // FK-safe cleanup of everything committed by this class.
        tx.executeWithoutResult(status -> {
            statusHistoryRepository.findAll().stream()
                    .filter(h -> h.getAppointment() != null
                            && appointmentRepository.findById(h.getAppointment().getId())
                                    .map(a -> doctorAId.equals(a.getDoctor().getId()))
                                    .orElse(false))
                    .forEach(statusHistoryRepository::delete);
            appointmentRepository.findAll().stream()
                    .filter(a -> doctorAId.equals(a.getDoctor().getId()))
                    .forEach(appointmentRepository::delete);
            patientRepository.findById(patientId).ifPresent(patientRepository::delete);
            doctorRepository.findById(doctorAId).ifPresent(doctorRepository::delete);
            userRepository.deleteById(doctorAUserId);
            userRepository.deleteById(doctorBUserId);
            userRepository.deleteById(patientUserId);
            specialtyRepository.deleteById(specialtyId);
        });
    }

    /** Real JWT minted with the application's encoder, as the security filter expects. */
    private String tokenFor(Long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medbook")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void concurrentTransitionsSerializeOnRowLock() throws Exception {
        // Fixture committed outside any test transaction so the worker threads see it.
        Long appointmentId = tx.execute(status -> {
            Appointment appointment = appointmentRepository.save(new Appointment(
                    patientRepository.findById(patientId).orElseThrow(),
                    doctorRepository.findById(doctorAId).orElseThrow(),
                    LocalDate.now().plusDays(7), LocalTime.of(11, 0), LocalTime.of(11, 30),
                    new BigDecimal("120.00")));
            statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED));
            return appointment.getId();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> confirmTask = () -> {
            start.await();
            return mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/confirm")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
        Future<Integer> f1 = pool.submit(confirmTask);
        Future<Integer> f2 = pool.submit(confirmTask);
        start.countDown();
        int status1 = f1.get();
        int status2 = f2.get();
        pool.shutdown();

        // One request wins with 200; the loser observes the winner's status and gets 409.
        assertThat(List.of(status1, status2)).containsExactlyInAnyOrder(200, 409);

        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);

        // Exactly one CONFIRMED audit row: the loser wrote nothing.
        long confirmedRows = statusHistoryRepository.findByAppointmentId(appointmentId).stream()
                .filter(h -> h.getStatus() == AppointmentStatus.CONFIRMED)
                .count();
        assertThat(confirmedRows).isEqualTo(1);
    }
}
