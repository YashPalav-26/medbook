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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;









@SpringBootTest
@Transactional
class AdminAppointmentsTest {

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

    private String adminToken;
    private User adminUser;
    private Specialty cardiology;
    private Doctor doctorA;
    private Doctor doctorB;
    private Patient patientJohn;
    private Patient patientMary;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        adminUser = new User("admin-appt@medbook.test", "hashed", "Ada", "Admin", "+919999100001");
        adminUser.addRole(adminRole);
        adminUser = userRepository.save(adminUser);
        adminToken = createTestToken(String.valueOf(adminUser.getId()), "ROLE_ADMIN");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .build();

        cardiology = specialtyRepository.save(new Specialty("Admin Appt Cardio", "test"));

        doctorA = createDoctor("adminappt-doca@medbook.test", "Cardiologist One");
        doctorB = createDoctor("adminappt-docb@medbook.test", "Cardiologist Two");

        patientJohn = createPatient("adminappt-john@medbook.test", "John", "Reed");
        patientMary = createPatient("adminappt-mary@medbook.test", "Mary", "Sue");
    }


    private String createTestToken(String subject, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medbook")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of(roles))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String tokenFor(User user, String role) {
        return createTestToken(String.valueOf(user.getId()), role);
    }

    private User createUserWithRole(String email, String roleName, String firstName, String lastName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));
        User user = new User(email, "hashed", firstName, lastName, "+919999100002");
        user.addRole(role);
        return userRepository.save(user);
    }

    private Doctor createDoctor(String email, String name) {
        String[] parts = name.split(" ");
        User user = createUserWithRole(email, "DOCTOR", parts[0], parts[1]);
        return doctorRepository.save(new Doctor(user, cardiology, "LIC-APPT-" + System.nanoTime(), 5,
                new BigDecimal("120.00")));
    }

    private Patient createPatient(String email, String first, String last) {
        User user = createUserWithRole(email, "PATIENT", first, last);
        return patientRepository.save(new Patient(user, LocalDate.of(1990, 1, 1)));
    }

    private Appointment createAppointment(Patient patient, Doctor doctor, LocalDate date,
                                          LocalTime start, AppointmentStatus status) {
        Appointment appointment = new Appointment(patient, doctor, date, start,
                start.plusMinutes(30), doctor.getConsultationFee());
        appointment.setStatus(status);
        if (status == AppointmentStatus.CANCELLED) {
            appointment.setCancellationReason("fixture cancellation");
        }
        return appointmentRepository.save(appointment);
    }





    @Test
    void adminCanListAppointments() throws Exception {
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                AppointmentStatus.SCHEDULED);

        mockMvc.perform(get("/api/v1/admin/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].patientName").isString())
                .andExpect(jsonPath("$[0].doctorName").isString())
                .andExpect(jsonPath("$[0].specialty").value("Admin Appt Cardio"));
    }

    @Test
    void patientCannotListAdminAppointments() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(
                                createUserWithRole("adminappt-pat@medbook.test", "PATIENT", "Pat", "Ient"),
                                "ROLE_PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotListAdminAppointments() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(
                                createUserWithRole("adminappt-doc@medbook.test", "DOCTOR", "Doc", "Tor"),
                                "ROLE_DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotListAdminAppointments() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/admin/appointments"))
                .andExpect(status().isUnauthorized());
    }





    @Test
    void listReturnsCorrectPatientAndDoctorInformation() throws Exception {
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientMary, doctorB, LocalDate.now().plusDays(3), LocalTime.of(11, 0),
                AppointmentStatus.SCHEDULED);

        mockMvc.perform(get("/api/v1/admin/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.patientName == 'John Reed')]").exists())
                .andExpect(jsonPath("$[?(@.patientName == 'Mary Sue')]").exists())
                .andExpect(jsonPath("$[?(@.doctorName == 'Cardiologist One')]").exists())
                .andExpect(jsonPath("$[?(@.doctorName == 'Cardiologist Two')]").exists())
                .andExpect(jsonPath("$[0].consultationFeeSnapshot").value(120.00));
    }

    @Test
    void dateFilterReturnsOnlyThatDate() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(4);
        createAppointment(patientJohn, doctorA, targetDate, LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(5), LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/appointments")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$..id");
        assertThat(ids).hasSize(1);
    }

    @Test
    void statusFilterReturnsOnlyThatStatus() throws Exception {
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(9, 0),
                AppointmentStatus.CONFIRMED);
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientJohn, doctorB, LocalDate.now().plusDays(3), LocalTime.of(11, 0),
                AppointmentStatus.CANCELLED);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/appointments")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> statuses = JsonPath.read(result.getResponse().getContentAsString(), "$..status");
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).containsOnly("CONFIRMED");
    }

    @Test
    void doctorFilterReturnsOnlyThatDoctor() throws Exception {
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientJohn, doctorB, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                AppointmentStatus.SCHEDULED);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/appointments")
                        .param("doctorId", doctorA.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> doctorIds = JsonPath.read(result.getResponse().getContentAsString(), "$..doctorId");
        assertThat(doctorIds).isNotEmpty();
        assertThat(doctorIds).containsOnly(doctorA.getId().intValue());
    }

    @Test
    void patientFilterReturnsOnlyThatPatient() throws Exception {
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientMary, doctorA, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                AppointmentStatus.SCHEDULED);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/appointments")
                        .param("patientId", patientMary.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> patientIds = JsonPath.read(result.getResponse().getContentAsString(), "$..patientId");
        assertThat(patientIds).isNotEmpty();
        assertThat(patientIds).containsOnly(patientMary.getId().intValue());
    }

    @Test
    void combinedFiltersIntersect() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(6);
        createAppointment(patientJohn, doctorA, targetDate, LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientJohn, doctorA, targetDate, LocalTime.of(10, 0),
                AppointmentStatus.CANCELLED);
        createAppointment(patientJohn, doctorB, targetDate, LocalTime.of(11, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientMary, doctorA, targetDate, LocalTime.of(12, 0),
                AppointmentStatus.SCHEDULED);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/appointments")
                        .param("date", targetDate.toString())
                        .param("status", "SCHEDULED")
                        .param("doctorId", doctorA.getId().toString())
                        .param("patientId", patientJohn.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$..id");
        assertThat(ids).hasSize(1);
    }

    @Test
    void invalidStatusValueIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }





    @Test
    void detailReturnsAppointmentWithParticipantsAndHistory() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.CONFIRMED);


        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, AppointmentStatus.CONFIRMED);
        history.setNotes("Appointment created");
        statusHistoryRepository.save(history);

        mockMvc.perform(get("/api/v1/admin/appointments/" + appointment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.id").value(appointment.getId()))
                .andExpect(jsonPath("$.appointment.patientId").value(patientJohn.getId().intValue()))
                .andExpect(jsonPath("$.appointment.patientName").value("John Reed"))
                .andExpect(jsonPath("$.appointment.doctorId").value(doctorA.getId().intValue()))
                .andExpect(jsonPath("$.appointment.doctorName").value("Cardiologist One"))
                .andExpect(jsonPath("$.appointment.specialty").value("Admin Appt Cardio"))
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.consultationFeeSnapshot").value(120.00))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].status").value("CONFIRMED"));
    }

    @Test
    void detailHistoryExposesNameButNeverAccountIdOrEmail() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.SCHEDULED);
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, AppointmentStatus.SCHEDULED);
        history.setNotes("Appointment created");
        history.setChangedByUser(adminUser);
        statusHistoryRepository.save(history);

        String body = mockMvc.perform(get("/api/v1/admin/appointments/" + appointment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].changedByName").value("Ada Admin"))
                .andReturn().getResponse().getContentAsString();


        assertThat(body).doesNotContain(adminUser.getEmail());
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("\"changedByUserId\"");
    }

    @Test
    void detailOfUnknownAppointmentIs404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999999")));
    }





    @Test
    void overviewReportsRealPerStatusCountsIncludingToday() throws Exception {


        MvcResult before = mockMvc.perform(get("/api/v1/admin/appointments/overview"))
                .andExpect(status().isOk())
                .andReturn();
        int scheduledBefore = ((Number) JsonPath.read(before.getResponse().getContentAsString(),
                "$.statusCounts.SCHEDULED")).intValue();
        long totalBefore = ((Number) JsonPath.read(before.getResponse().getContentAsString(),
                "$.totalAppointments")).longValue();


        createAppointment(patientJohn, doctorA, LocalDate.now(), LocalTime.of(15, 0),
                AppointmentStatus.SCHEDULED);
        createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3), LocalTime.of(9, 0),
                AppointmentStatus.SCHEDULED);

        MvcResult after = mockMvc.perform(get("/api/v1/admin/appointments/overview"))
                .andExpect(status().isOk())
                .andReturn();
        String body = after.getResponse().getContentAsString();

        int scheduledAfter = ((Number) JsonPath.read(body, "$.statusCounts.SCHEDULED")).intValue();
        long totalAfter = ((Number) JsonPath.read(body, "$.totalAppointments")).longValue();
        int todayCount = ((Number) JsonPath.read(body, "$.todayAppointments")).intValue();

        assertThat(scheduledAfter).isEqualTo(scheduledBefore + 2);
        assertThat(totalAfter).isEqualTo(totalBefore + 2);


        java.util.Map<String, Object> statusCounts = com.jayway.jsonpath.JsonPath.parse(body)
                .read("$.statusCounts", java.util.Map.class);
        assertThat(statusCounts.keySet()).containsExactlyInAnyOrder(
                "SCHEDULED", "CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW");


        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/admin/appointments").param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<Integer> todaysIds = JsonPath.read(result.getResponse().getContentAsString(), "$..id");
                    assertThat(todayCount).isGreaterThanOrEqualTo(todaysIds.size() - 1);
                });


        assertThat(JsonPath.<String>read(body, "$.today")).isEqualTo(today.toString());
    }





    @Test
    void adminCancelsScheduledAppointmentWithReason() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"Patient called support to reschedule\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Patient called support to reschedule"))
                .andExpect(jsonPath("$.patientName").value("John Reed"));

        Appointment stored = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).isEqualTo("Patient called support to reschedule");
    }

    @Test
    void adminCancellationRecordsAdminAsChangedBy() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"Clinic closed for emergency\"}"))
                .andExpect(status().isOk());

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(appointment.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(history.get(0).getChangedByUser()).isNotNull();
        assertThat(history.get(0).getChangedByUser().getId()).isEqualTo(adminUser.getId());
        assertThat(history.get(0).getNotes()).isEqualTo("Clinic closed for emergency");
    }

    @Test
    void adminCannotCancelFinishedAppointment() throws Exception {
        Appointment completed = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.COMPLETED);
        Appointment noShow = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(11, 0), AppointmentStatus.NO_SHOW);

        mockMvc.perform(post("/api/v1/admin/appointments/" + completed.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"late cancellation\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/appointments/" + noShow.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"late cancellation\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotCancelAlreadyCancelledAppointment() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"second cancel\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCancellationRequiresReason() throws Exception {
        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());


        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void adminCancellationOfUnknownAppointmentIs404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/appointments/999999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"no such row\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCancellationDoesNotCreateResidueOnRollback() throws Exception {


        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"should roll back\"}"))
                .andExpect(status().isConflict());

        assertThat(statusHistoryRepository.findByAppointmentId(appointment.getId())).isEmpty();
    }

    @Test
    void existingPatientCancelEndpointStillRejectsAdmins() throws Exception {


        Appointment appointment = createAppointment(patientJohn, doctorA, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"admin trying the old route\"}"))
                .andExpect(status().isForbidden());

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }
}
