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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the doctor appointment lifecycle (Step 21) over real MySQL
 * with real JWTs.
 *
 * <p>Exercised matrix: doctor confirms/completes/no-shows their own appointments;
 * wrong doctor, patients, admins and anonymous callers are rejected; invalid
 * transitions (complete a SCHEDULED appointment, act on CANCELLED/COMPLETED ones)
 * fail with 409; every successful transition appends an audit row whose
 * {@code changed_by_user_id} is the real authenticated doctor. Concurrency is
 * covered in {@link DoctorPortalConcurrencyTest}, which needs real committed rows.</p>
 *     * <p>The class is {@code @Transactional}, so every fixture row is rolled back — no test residue.
     * The concurrency test is the exception: its requests run on other threads that cannot see
     * the uncommitted test transaction, so it commits its own fixture and removes it explicitly.</p>
     */
@SpringBootTest
@Transactional
class DoctorPortalTest {

    private MockMvc mockMvc;

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

    private Doctor doctorA;
    private Doctor doctorB;
    private Patient patient;
    private String doctorAToken;
    private String doctorBToken;
    private String patientToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Specialty specialty = specialtyRepository.save(new Specialty("DoctorPortal Cardio", "test"));

        doctorA = createDoctor("portal-doc-a@medbook.test", specialty);
        doctorB = createDoctor("portal-doc-b@medbook.test", specialty);

        Role patientRole = roleRepository.findByName("PATIENT").orElseThrow();
        User patientUser = new User("portal-pat@medbook.test", "hashed", "Paul", "Portal", "+919999210001");
        patientUser.addRole(patientRole);
        patientUser = userRepository.save(patientUser);
        patient = patientRepository.save(new Patient(patientUser, LocalDate.of(1990, 5, 5)));

        doctorAToken = tokenFor(doctorA.getUser(), "ROLE_DOCTOR");
        doctorBToken = tokenFor(doctorB.getUser(), "ROLE_DOCTOR");
        patientToken = tokenFor(patientUser, "ROLE_PATIENT");

        User adminUser = new User("portal-admin@medbook.test", "hashed", "Ann", "Portal", "+919999210002");
        adminUser.addRole(roleRepository.findByName("ADMIN").orElseThrow());
        adminUser = userRepository.save(adminUser);
        adminToken = tokenFor(adminUser, "ROLE_ADMIN");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(post("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorAToken))
                .build();
    }

    /** Real JWT minted with the application's encoder, as the security filter expects. */
    private String tokenFor(User user, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medbook")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private Doctor createDoctor(String email, Specialty specialty) {
        User user = new User(email, "hashed", "Doc", "Portal", "+919999210003");
        user.addRole(roleRepository.findByName("DOCTOR").orElseThrow());
        user = userRepository.save(user);
        return doctorRepository.save(new Doctor(user, specialty, "LIC-PORTAL-" + System.nanoTime(), 6,
                new BigDecimal("150.00")));
    }

    /**
     * A future SCHEDULED appointment with doctor A, mirroring what the booking service
     * persists over HTTP — including the initial SCHEDULED audit row (with a NULL actor,
     * since repository fixtures have no authenticated caller).
     */
    private Appointment createScheduledAppointment() {
        LocalDate date = LocalDate.now().plusDays(7);

        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctorA, date, LocalTime.of(10, 0), LocalTime.of(10, 30),
                doctorA.getConsultationFee()));
        statusHistoryRepository.save(new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED));

        return appointment;
    }

    // ==================================================================
    // HAPPY PATH
    // ==================================================================

    @Test
    void doctorConfirmsOwnScheduledAppointment() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Appointment stored = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(stored.getConfirmedAt()).isNotNull();
    }

    @Test
    void doctorCompletesConfirmedAppointment() throws Exception {
        Appointment appointment = createScheduledAppointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Appointment stored = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(stored.getCompletedAt()).isNotNull();
    }

    @Test
    void doctorMarksConfirmedAppointmentNoShow() throws Exception {
        Appointment appointment = createScheduledAppointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/no-show")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    // ==================================================================
    // AUTHORIZATION
    // ==================================================================

    @Test
    void wrongDoctorGets403() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorBToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotConfirmCompleteOrNoShow() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Nothing changed.
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void adminDoesNotGetDoctorWorkflowPowers() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        Appointment appointment = createScheduledAppointment();

        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAppointmentIs404() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/999999/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================================================================
    // TRANSITION RULES
    // ==================================================================

    @Test
    void doctorCannotCompleteScheduledAppointment() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot change appointment status from SCHEDULED to COMPLETED:"
                                + " this appointment action is not valid from the current status"));
    }

    @Test
    void doctorCannotNoShowScheduledAppointment() throws Exception {
        Appointment appointment = createScheduledAppointment();

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/no-show")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledAppointmentCannotBeConfirmed() throws Exception {
        Appointment appointment = createScheduledAppointment();
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason("patient called off");
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void completedAppointmentCannotBeCancelled() throws Exception {
        Appointment appointment = createScheduledAppointment();
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment = appointmentRepository.save(appointment);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"too late\"}"))
                .andExpect(status().isConflict());

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void terminalStatesRejectEveryTransition() throws Exception {
        Appointment completed = createScheduledAppointment();
        completed.setStatus(AppointmentStatus.COMPLETED);
        completed = appointmentRepository.save(completed);

        mockMvc.perform(post("/api/v1/appointments/" + completed.getId() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/appointments/" + completed.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/appointments/" + completed.getId() + "/no-show")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());

        Appointment noShow = createScheduledAppointment();
        noShow.setStatus(AppointmentStatus.NO_SHOW);
        noShow = appointmentRepository.save(noShow);

        mockMvc.perform(post("/api/v1/appointments/" + noShow.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    // ==================================================================
    // HISTORY / AUDIT
    // ==================================================================

    @Test
    void everySuccessfulTransitionAppendsHistoryWithRealDoctor() throws Exception {
        Appointment appointment = createScheduledAppointment();

        // SCHEDULED → CONFIRMED
        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // CONFIRMED → COMPLETED
        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Sort by insertion ID: rows written within the same second share changed_at.
        List<AppointmentStatusHistory> history = statusHistoryRepository.findByAppointmentId(appointment.getId())
                .stream()
                .sorted(Comparator.comparing(AppointmentStatusHistory::getId))
                .toList();
        assertThat(history).hasSize(3); // initial SCHEDULED + CONFIRMED + COMPLETED

        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(history.get(1).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.get(2).getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

        // changed_by_user_id is the real doctor for both HTTP transitions; the initial
        // SCHEDULED row (created by the repository save, no JWT in this thread) is NULL.
        assertThat(history.get(1).getChangedByUser().getId()).isEqualTo(doctorA.getUser().getId());
        assertThat(history.get(2).getChangedByUser().getId()).isEqualTo(doctorA.getUser().getId());
        assertThat(history.get(0).getChangedByUser()).isNull();

        // Failed transitions must not add rows. Doctor B is rejected with 403 before the
        // status is even examined — ownership is verified first, so a non-owner cannot
        // use the error to learn whether an appointment is cancellable.
        Appointment cancelled = createScheduledAppointment();
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        cancelled.setCancellationReason("off");
        cancelled = appointmentRepository.save(cancelled);

        mockMvc.perform(post("/api/v1/appointments/" + cancelled.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorBToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/appointments/" + cancelled.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        assertThat(statusHistoryRepository.findByAppointmentId(cancelled.getId())).hasSize(1);
    }

}
