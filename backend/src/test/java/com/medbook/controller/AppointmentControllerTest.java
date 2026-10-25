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






    private static LocalDate futureDate() {
        return LocalDate.now().plusDays(7);
    }

    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue();
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
        MvcResult result = mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }





    @Test
    void createAppointmentReturns201WithLocationHeaderAndJsonBody() throws Exception {
        Doctor doctor = createTestDoctor("rest-create-doc@medbook.test", "Rest Cardiology");
        Patient patient = createTestPatient("rest-create-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        MvcResult result = mockMvc.perform(post("/api/v1/appointments/admin")
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


        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/appointments/" + appointmentId);


        Object feeValue = JsonPath.read(json, "$.consultationFeeSnapshot");
        assertThat(feeValue).isInstanceOf(Number.class);
        assertThat(new BigDecimal(String.valueOf(feeValue))).isEqualByComparingTo(TEST_FEE);


        Appointment stored = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(statusHistoryRepository.findByAppointmentId(appointmentId)).hasSize(1);
    }





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

        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/admin"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("patientId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("patientId is required"));
    }

    @Test
    void createAppointmentReturns400WhenPatientIdIsNotPositive() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(0L, 1L, futureDate(), "10:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("patientId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("patientId must be a positive number"));
    }

    @Test
    void createAppointmentReturns400WhenAppointmentDateIsInThePast() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/admin")
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


        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), futureDate(), "11:00", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Start time")))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void createAppointmentReturns400WhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"patientId\": 1, \"doctorId\": 1, \"appointmentDate\": \"not-a-date\","
                                + " \"startTime\": \"10:00\", \"endTime\": \"11:00\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }





    @Test
    void createAppointmentReturns404WhenDoctorDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(1L, 999_999L, futureDate(), "10:00", "11:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Doctor not found with id: 999999"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/admin"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void createAppointmentReturns404WhenPatientDoesNotExist() throws Exception {
        Doctor doctor = createTestDoctor("rest-404-doc@medbook.test", "Rest Neurology");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        mockMvc.perform(post("/api/v1/appointments/admin")
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





    @Test
    void createAppointmentReturns409WhenDoctorIsInactive() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc@medbook.test", "Rest Dermatology");
        Patient patient = createTestPatient("rest-409-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        doctor.setActive(false);
        doctorRepository.save(doctor);

        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Doctor is inactive and cannot accept appointments: "
                        + doctor.getId()))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/admin"));
    }

    @Test
    void createAppointmentReturns409WhenPatientIsInactive() throws Exception {
        Doctor doctor = createTestDoctor("rest-409-doc2@medbook.test", "Rest Psychiatry");
        Patient patient = createTestPatient("rest-409-pat2@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        patient.setActive(false);
        patientRepository.save(patient);

        mockMvc.perform(post("/api/v1/appointments/admin")
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

        mockMvc.perform(post("/api/v1/appointments/admin")
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


        postBookingAndReadId(bookingJson(first.getId(), doctor.getId(), date, "10:00", "11:00"));

        mockMvc.perform(post("/api/v1/appointments/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(second.getId(), doctor.getId(), date, "10:30", "11:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already has an appointment overlapping")));


        assertThat(appointmentRepository.findByDoctorId(doctor.getId())).hasSize(1);
    }





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


        Appointment stored = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stored.getCancellationReason()).isEqualTo("Patient asked to reschedule");
        assertThat(statusHistoryRepository.findByAppointmentId(appointmentId)).hasSize(2);


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

        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/appointments/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }






    private static String selfBookingJson(Long doctorId, LocalDate date, String start, String end) {
        return """
                {
                  "doctorId": %s,
                  "appointmentDate": "%s",
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(doctorId, date, start, end);
    }

    private String tokenFor(User user, String role) {
        return createTestToken(String.valueOf(user.getId()), role);
    }

    @Test
    void patientSelfBookingCreatesAppointmentForTheAuthenticatedPatient() throws Exception {
        Doctor doctor = createTestDoctor("rest-self-doc@medbook.test", "Rest Self Cardiology");
        Patient patient = createTestPatient("rest-self-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        Patient otherPatient = createTestPatient("rest-self-other@medbook.test");

        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(doctor.getId(), date, "15:00", "16:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patient.getId().intValue()))
                .andExpect(jsonPath("$.doctorId").value(doctor.getId().intValue()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));


        List<Appointment> doctorsAppointments = appointmentRepository.findByDoctorId(doctor.getId());
        assertThat(doctorsAppointments).hasSize(1);
        assertThat(doctorsAppointments.get(0).getPatient().getId()).isEqualTo(patient.getId());
        assertThat(doctorsAppointments.get(0).getPatient().getId()).isNotEqualTo(otherPatient.getId());
    }

    @Test
    void patientSelfBookingIgnoresAClientSuppliedPatientId() throws Exception {
        Doctor doctor = createTestDoctor("rest-ignore-doc@medbook.test", "Rest Ignore Cardiology");
        Patient caller = createTestPatient("rest-ignore-caller@medbook.test");
        Patient victim = createTestPatient("rest-ignore-victim@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));



        String smuggled = """
                {
                  "patientId": %s,
                  "doctorId": %s,
                  "appointmentDate": "%s",
                  "startTime": "13:00",
                  "endTime": "14:00"
                }
                """.formatted(victim.getId(), doctor.getId(), date);

        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(caller.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smuggled))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(caller.getId().intValue()))
                .andExpect(jsonPath("$.patientId").value(org.hamcrest.Matchers.not(victim.getId().intValue())));

        assertThat(appointmentRepository.findByDoctorId(doctor.getId())).allSatisfy(a ->
                assertThat(a.getPatient().getId()).isEqualTo(caller.getId()));
    }

    @Test
    void patientSelfBookingValidationFailuresReturn400() throws Exception {
        Patient patient = createTestPatient("rest-self-400-pat@medbook.test");


        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"appointmentDate\": \"%s\", \"startTime\": \"10:00\", \"endTime\": \"11:00\" }"
                                .formatted(futureDate())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("doctorId"));


        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(1L, LocalDate.now().minusDays(1), "10:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("appointmentDate"));
    }

    @Test
    void patientSelfBookingIsRejectedForDoctorAndAdminCallers() throws Exception {
        Doctor doctor = createTestDoctor("rest-self-roles-doc@medbook.test", "Rest Self Gastro");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));


        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(doctor.getUser(), "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isForbidden());


        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSupportBookingIsRejectedForPatientCallers() throws Exception {
        Patient patient = createTestPatient("rest-adminpath-pat@medbook.test");
        Doctor doctor = createTestDoctor("rest-adminpath-doc@medbook.test", "Rest Adminpath Cardio");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        mockMvc.perform(post("/api/v1/appointments/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(patient.getId(), doctor.getId(), date, "10:00", "11:00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Booking for another patient requires an administrator"));
    }

    @Test
    void patientSelfBookingRecordsTheCallerInStatusHistory() throws Exception {
        Doctor doctor = createTestDoctor("rest-self-hist-doc@medbook.test", "Rest Self History");
        Patient patient = createTestPatient("rest-self-hist-pat@medbook.test");
        LocalDate date = futureDate();
        createRecurringAvailability(doctor, date, LocalTime.of(9, 0), LocalTime.of(17, 0));

        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(doctor.getId(), date, "09:00", "10:00")))
                .andExpect(status().isCreated())
                .andReturn();
        long appointmentId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        var history = statusHistoryRepository.findByAppointmentId(appointmentId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangedByUser()).isNotNull();
        assertThat(history.get(0).getChangedByUser().getId()).isEqualTo(patient.getUser().getId());
    }

    @Test
    void patientSelfBookingReturns404WhenDoctorDoesNotExist() throws Exception {
        Patient patient = createTestPatient("rest-self-404-pat@medbook.test");

        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBookingJson(999_999L, futureDate(), "10:00", "11:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor not found with id: 999999"));
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
