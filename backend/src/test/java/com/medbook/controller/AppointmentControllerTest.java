package com.medbook.controller;

import com.jayway.jsonpath.JsonPath;
import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-layer (MockMvc) tests for {@link AppointmentController}.
 *
 * <p>These are integration tests: the full application context is started and the real
 * {@code AppointmentService} talks to the real MySQL database (no mocked service, no H2),
 * matching the approach the repository and service tests already follow. The service
 * business-rule tests in {@code com.medbook.service} stay untouched.</p>
 *
 * <p>The class is {@link Transactional}, so every doctor, patient, availability slot and
 * appointment created here is rolled back by Spring afterwards.</p>
 *
 * <p>HTTP contract asserted here: creation → 201 (+ {@code Location}), reads and successful
 * cancellation → 200, unknown doctor/patient/appointment → 404, business conflict → 409,
 * invalid request → 400.</p>
 *
 * <p>Since Step 14 the appointment API is protected by Spring Security, so MockMvc is built
 * with the real security filter chain and every request carries a real (test-issued) JWT.
 * The tokens are made by the application's own {@code JwtEncoder}, so the full decode path
 * is exercised — the only difference from production is that the values are test fixtures.</p>
 */
@SpringBootTest
@Transactional
class AppointmentControllerTest {

    private static final BigDecimal TEST_FEE = new BigDecimal("500.00");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private MockMvc mockMvc;

    /**
     * Real ADMIN user row backing the default test token. Ownership authorization resolves
     * the JWT subject against {@code users}, and this contract test exercises the HTTP
     * layer with an administrator (who may access any appointment by ID). Caller-visibility
     * rules for patients/doctors are covered in {@code AppointmentOwnershipTest}.
     */
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        adminUser = new User("rest-admin@medbook.test", "hashed-password", "Rest", "Admin", "+919876543230");
        adminUser.addRole(adminRole);
        userRepository.save(adminUser);

        String token = createTestToken(String.valueOf(adminUser.getId()), "ROLE_ADMIN");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
    }

    /**
     * Issue a real JWT through the application's encoder for the appointment calls below.
     */
    private String createTestToken(String subject, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medbook")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("email", "rest-tester@medbook.test")
                .claim("roles", List.of(roles))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    /** Future date that reuses today's weekday, so it always matches the availability slot. */
    private static LocalDate futureDate() {
        return LocalDate.now().plusDays(7);
    }

    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue(); // 1 = Monday ... 7 = Sunday
        return (byte) (javaDayOfWeek == 7 ? 0 : javaDayOfWeek);
    }

    private Doctor createTestDoctor(String email, String specialtyName) {
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        User user = new User(email, "hashed-password", "Rest", "Doctor", "+919876543220");
        user.addRole(doctorRole);
        userRepository.save(user);

        Specialty specialty = specialtyRepository.save(new Specialty(specialtyName, "Test specialty"));
        return doctorRepository.save(
                new Doctor(user, specialty, "LIC-REST-" + System.nanoTime(), 6, TEST_FEE));
    }

    private Patient createTestPatient(String email) {
        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new IllegalStateException("PATIENT role not seeded"));
        User user = new User(email, "hashed-password", "Rest", "Patient", "+919876543221");
        user.addRole(patientRole);
        userRepository.save(user);

        return patientRepository.save(new Patient(user, LocalDate.of(1992, 3, 3)));
    }

    private void createRecurringAvailability(Doctor doctor, LocalDate date, LocalTime start, LocalTime end) {
        DoctorAvailability availability =
                new DoctorAvailability(doctor, toMysqlDayOfWeek(date), start, end);
        availability.setRecurring(true);
        availabilityRepository.save(availability);
    }

    private static String bookingJson(Long patientId, Long doctorId, LocalDate date,
                                     String start, String end) {
        return """
                {
                  "patientId": %s,
                  "doctorId": %s,
                  "appointmentDate": "%s",
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(patientId, doctorId, date, start, end);
    }

    private long postBookingAndReadId(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ------------------------------------------------------------------
    // 1. Successful creation (JSON serialization / deserialization)
    // ------------------------------------------------------------------

    @Test
    void createAppointmentReturns201WithLocationHeaderAndJsonBody() throws Exception {
        Doctor doctor = createTestDoctor("rest-create-doc@medbook.test", "Rest Cardiology");
        Patient patient = createTestPatient("rest-create-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.patientId").value(patient.getId().intValue()))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId().intValue()))
                .andExpect(jsonPath("$.appointmentDate").value(date.toString()))
                .andExpect(jsonPath("$.startTime").value("10:00"))
                .andExpect(jsonPath("$.endTime").value("11:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancellationReason").value(nullValue()))
                .andExpect(jsonPath("$.consultationFeeSnapshot").isNumber())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        long appointmentId = ((Number) JsonPath.read(json, "$.id")).longValue();

        // The Location header points at the new resource.
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/appointments/" + appointmentId);

        // The client cannot send a fee; the service snapshots it from the doctor.
        Object feeValue = JsonPath.read(json, "$.consultationFeeSnapshot");
        assertThat(feeValue).isInstanceOf(Number.class);
        assertThat(new BigDecimal(String.valueOf(feeValue))).isEqualByComparingTo(TEST_FEE);

        // The appointment and its first status history row are really persisted.
        Appointment stored = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(statusHistoryRepository.findByAppointmentId(appointmentId)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 2. Request validation failures -> 400
    // ------------------------------------------------------------------

    @Test
    void createAppointmentReturns400WhenPatientIdIsMissing() throws Exception {
        String body = """
                {
                  "doctorId": 1,
                  "appointmentDate": "%s",
                  "startTime": "10:00",
                  "endTime": "11:00"
                }
                """.formatted(futureDate());

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("patientId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("patientId is required"));
    }

    @Test
    void createAppointmentReturns400WhenPatientIdIsNotPositive() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(0L, 1L, futureDate(), "10:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("patientId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("patientId must be a positive number"));
    }

    @Test
    void createAppointmentReturns400WhenAppointmentDateIsInThePast() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(1L, 1L, LocalDate.now().minusDays(1), "10:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("appointmentDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("appointmentDate must be today or in the future"));
    }

    @Test
    void createAppointmentReturns400WhenTimeWindowIsInvalid() throws Exception {
        Doctor doctor = createTestDoctor("rest-400-doc@medbook.test", "Rest Gastroenterology");
        Patient patient = createTestPatient("rest-400-pat@medbook.test");

        // Structurally valid for Bean Validation, so the service rule reports the problem.
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), futureDate(), "11:00", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Start time")))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void createAppointmentReturns400WhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"patientId\": 1, \"doctorId\": 1, \"appointmentDate\": \"not-a-date\","
                                + " \"startTime\": \"10:00\", \"endTime\": \"11:00\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    // ------------------------------------------------------------------
    // 3. Missing resources -> 404
    // ------------------------------------------------------------------

    @Test
    void createAppointmentReturns404WhenDoctorDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(1L, 999_999L, futureDate(), "10:00", "11:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Doctor not found with id: 999999"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void createAppointmentReturns404WhenPatientDoesNotExist() throws Exception {
        Doctor doctor = createTestDoctor("rest-404-doc@medbook.test", "Rest Neurology");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(999_999L, doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient not found with id: 999999"));
    }

    @Test
    void getAppointmentReturns404WhenAppointmentDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found with id: 999999"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/999999"));
    }

    @Test
    void cancelAppointmentReturns404WhenAppointmentDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"No longer needed\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found with id: 999999"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/999999/cancel"));
    }

    @Test
    void getAppointmentReturns400WhenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'id'"));
    }

    // ------------------------------------------------------------------
    // 4. Business conflicts -> 409
    // ------------------------------------------------------------------

    @Test
    void createAppointmentReturns409WhenDoctorIsInactive() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc@medbook.test", "Rest Dermatology");
        Patient patient = createTestPatient("rest-409-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        doctor.setActive(false);
        doctorRepository.save(doctor);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Doctor is inactive and cannot accept appointments: "
                        + doctor.getId()))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments"));
    }

    @Test
    void createAppointmentReturns409WhenPatientIsInactive() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc2@medbook.test", "Rest Psychiatry");
        Patient patient = createTestPatient("rest-409-pat2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        patient.setActive(false);
        patientRepository.save(patient);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Patient is inactive and cannot book appointments: "
                        + patient.getId()));
    }

    @Test
    void createAppointmentReturns409WhenWindowIsOutsideDoctorAvailability() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc3@medbook.test", "Rest Oncology");
        Patient patient = createTestPatient("rest-409-pat3@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(12, 0));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "13:00", "14:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("is not available on")));
    }

    @Test
    void createAppointmentReturns409WhenSlotIsAlreadyBooked() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc4@medbook.test", "Rest Urology");
        Patient first = createTestPatient("rest-409-pat4@medbook.test");
        Patient second = createTestPatient("rest-409-pat5@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        // First booking wins.
        postBookingAndReadId(bookingJson(first.getId(), doctor.getId(), date, "10:00", "11:00"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(second.getId(), doctor.getId(), date, "10:30", "11:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already has an appointment overlapping")));

        // The rejected booking wrote nothing.
        assertThat(appointmentRepository.findByDoctorId(doctor.getId())).hasSize(1);
    }

    // ------------------------------------------------------------------
    // 5. Retrieval and cancellation
    // ------------------------------------------------------------------

    @Test
    void getAppointmentReturns200WithTheStoredAppointment() throws Exception {
        Doctor doctor = createTestDoctor("rest-get-doc@medbook.test", "Rest Rheumatology");
        Patient patient = createTestPatient("rest-get-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        long appointmentId = postBookingAndReadId(
                bookingJson(patient.getId(), doctor.getId(), date, "09:30", "10:15"));

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.patientId").value(patient.getId().intValue()))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId().intValue()))
                .andExpect(jsonPath("$.appointmentDate").value(date.toString()))
                .andExpect(jsonPath("$.startTime").value("09:30"))
                .andExpect(jsonPath("$.endTime").value("10:15"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancellationReason").value(nullValue()));
    }

    @Test
    void cancelAppointmentReturns200WithCancelledStatusAndReason() throws Exception {
        Doctor doctor = createTestDoctor("rest-cancel-doc@medbook.test", "Rest Nephrology");
        Patient patient = createTestPatient("rest-cancel-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        long appointmentId = postBookingAndReadId(
                bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00"));

        // The owning patient cancels (ADMIN cannot cancel by policy — see
        // AppointmentOwnershipTest; the default caller here is the ADMIN fixture).
        String patientToken = createTestToken(String.valueOf(patient.getUser().getId()), "ROLE_PATIENT");

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"Patient asked to reschedule\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.patientId").value(patient.getId().intValue()))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId().intValue()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Patient asked to reschedule"));

        // The status change is persisted and the audit trail keeps both rows.
        Appointment stored = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).isEqualTo("Patient asked to reschedule");
        assertThat(statusHistoryRepository.findByAppointmentId(appointmentId)).hasSize(2);

        // A follow-up GET reports the same state.
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelAppointmentReturns400WhenReasonIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"   \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cancellationReason"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("cancellationReason is required"));
    }

    @Test
    void cancelAppointmentReturns400WhenReasonIsTooLong() throws Exception {
        String tooLong = "x".repeat(256);

        mockMvc.perform(post("/api/v1/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"" + tooLong + "\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cancellationReason"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("cancellationReason must be at most 255 characters"));
    }

    // ------------------------------------------------------------------
    // 6. GET /appointments/me (authenticated listing)
    // ------------------------------------------------------------------

    @Test
    void meEndpointReturnsOnlyTheCallingPatientsAppointments() throws Exception {
        Doctor doctor = createTestDoctor("rest-me-doc@medbook.test", "Rest Me Cardiology");
        Patient mine = createTestPatient("rest-me-pat-a@medbook.test");
        Patient other = createTestPatient("rest-me-pat-b@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        long myAppointmentId = postBookingAndReadId(
                bookingJson(mine.getId(), doctor.getId(), date, "10:00", "11:00"));
        postBookingAndReadId(bookingJson(other.getId(), doctor.getId(), date, "12:00", "13:00"));

        String myToken = createTestToken(String.valueOf(mine.getUser().getId()), "ROLE_PATIENT");

        mockMvc.perform(get("/api/v1/appointments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(myAppointmentId))
                .andExpect(jsonPath("$[0].patientId").value(mine.getId().intValue()))
                .andExpect(jsonPath("$[0].doctorId").value(doctor.getId().intValue()))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void meEndpointReturnsEmptyListForPatientWithoutAppointments() throws Exception {
        Patient lonely = createTestPatient("rest-me-lonely@medbook.test");
        String token = createTestToken(String.valueOf(lonely.getUser().getId()), "ROLE_PATIENT");

        mockMvc.perform(get("/api/v1/appointments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void meEndpointIsRejectedForAdminRoleByPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Appointment listing is not available for this account role"));
    }

    @Test
    void meEndpointRequiresAuthentication() throws Exception {
        // A MockMvc without the default ADMIN Authorization header: truly anonymous call.
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/appointments/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void cancelAppointmentReturns409WhenAlreadyCancelled() throws Exception {
        Doctor doctor = createTestDoctor("rest-cancel-doc2@medbook.test", "Rest Hematology");
        Patient patient = createTestPatient("rest-cancel-pat2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        long appointmentId = postBookingAndReadId(
                bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00"));

        String patientToken = createTestToken(String.valueOf(patient.getUser().getId()), "ROLE_PATIENT");

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"First cancellation\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cancellationReason\": \"Second cancellation\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value(containsString("Cannot change appointment status from CANCELLED to CANCELLED")));
    }
}
