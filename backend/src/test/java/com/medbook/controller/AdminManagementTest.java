package com.medbook.controller;

import com.medbook.dto.DoctorAdminResponse;
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
import com.jayway.jsonpath.JsonPath;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the ADMIN management API (specialties, doctors, availability)
 * over real MySQL with real JWTs.
 *
 * <p>The class is {@code @Transactional}, so every user, doctor, specialty and
 * availability row these tests create is rolled back — no residue (Part R #23).
 * The authorization matrix (anonymous 401 / patient 403 / doctor 403 / admin allowed)
 * is exercised against every endpoint group.</p>
 */
@SpringBootTest
@Transactional
class AdminManagementTest {

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
    private DoctorAvailabilityRepository availabilityRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        adminUser = new User("admin-mgmt@medbook.test", "hashed", "Ada", "Admin", "+919999000001");
        adminUser.addRole(adminRole);
        adminUser = userRepository.save(adminUser);
        adminToken = createTestToken(String.valueOf(adminUser.getId()), "ROLE_ADMIN");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                // Default caller is the ADMIN fixture (project convention); tests that
                // exercise other roles override the header explicitly.
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .build();
    }

    /** Real JWT minted with the application's encoder, as the security filter expects. */
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

    private User createUserWithRole(String email, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));
        User user = new User(email, "hashed", "Test", "User", "+919999000002");
        user.addRole(role);
        return userRepository.save(user);
    }

    private Specialty createSpecialty(String name) {
        return specialtyRepository.save(new Specialty(name, "test specialty"));
    }

    private Patient createPatient(String email) {
        User user = createUserWithRole(email, "PATIENT");
        return patientRepository.save(new Patient(user, LocalDate.of(1990, 6, 15)));
    }

    private Doctor createDoctor(String email, Specialty specialty) {
        User user = createUserWithRole(email, "DOCTOR");
        return doctorRepository.save(new Doctor(user, specialty, "LIC-ADMIN-" + System.nanoTime(), 5,
                new BigDecimal("100.00")));
    }

    // ==================================================================
    // PATIENTS (read-only admin list)
    // ==================================================================

    @Test
    void adminListsPatientsWithAccountLinkage() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Patient patient = createPatient("admin-list-pat-" + suffix + "@medbook.test");

        mockMvc.perform(get("/api/v1/admin/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + patient.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.email == 'admin-list-pat-" + suffix + "@medbook.test')]").exists());
    }

    @Test
    void patientListNeverLeaksSecurityInternals() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Patient patient = createPatient("admin-list-sec-" + suffix + "@medbook.test");

        String body = mockMvc.perform(get("/api/v1/admin/patients"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // DTO-only serialization: no hash, no date-of-birth of OTHER patients beyond the
        // intentionally exposed one, and no raw user id reference.
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).contains(patient.getUser().getEmail());
    }

    @Test
    void patientCannotListAdminPatients() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User patientUser = createUserWithRole("admin-list-denied-" + suffix + "@medbook.test", "PATIENT");

        mockMvc.perform(get("/api/v1/admin/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patientUser, "ROLE_PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotListAdminPatients() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/admin/patients"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // SPECIALTIES
    // ==================================================================

    @Test
    void adminCreatesSpecialtyAndItAppearsInList() throws Exception {
        mockMvc.perform(post("/api/v1/admin/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Cardio\",\"description\":\"created by test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Admin Cardio"))
                .andExpect(jsonPath("$.description").value("created by test"));

        // Present in the admin list (no second insert — the class rolls back).
        mockMvc.perform(get("/api/v1/admin/specialties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Admin Cardio')]").exists());
    }

    @Test
    void duplicateSpecialtyNameIsRejectedWith409() throws Exception {
        createSpecialty("Dup Cardio");

        mockMvc.perform(post("/api/v1/admin/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dup cardio\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Specialty name already exists: dup cardio"));
    }

    @Test
    void adminUpdatesSpecialty() throws Exception {
        Specialty specialty = createSpecialty("Old Name");

        mockMvc.perform(put("/api/v1/admin/specialties/" + specialty.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("updated"));
    }

    @Test
    void adminDeactivatesSpecialtyAndDiscoveryStopsOfferingIt() throws Exception {
        Specialty specialty = createSpecialty("Retire Me");

        mockMvc.perform(patch("/api/v1/admin/specialties/" + specialty.getId() + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Retire Me"))
                // The DTO has no active field, so assert on the entity through the repository.
                .andDo(result -> assertThat(specialtyRepository.findById(specialty.getId()).orElseThrow().isActive())
                        .isFalse());

        // Public discovery no longer lists it.
        mockMvc.perform(get("/api/v1/specialties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Retire Me')]").doesNotExist());
    }

    @Test
    void patientCannotManageSpecialties() throws Exception {
        User patient = createUserWithRole("admin-pat@medbook.test", "PATIENT");
        mockMvc.perform(post("/api/v1/admin/specialties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient, "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotManageSpecialties() throws Exception {
        User doctor = createUserWithRole("admin-doc@medbook.test", "DOCTOR");
        mockMvc.perform(get("/api/v1/admin/specialties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(doctor, "ROLE_DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotManageSpecialties() throws Exception {
        // A MockMvc without the default ADMIN header: truly anonymous call.
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/admin/specialties"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // DOCTORS
    // ==================================================================

    private String doctorCreateJson(String email, String license, Long specialtyId) {
        return """
                {
                  "email": "%s",
                  "password": "Doctor#Pass1",
                  "firstName": "Gregory",
                  "lastName": "House",
                  "phoneNumber": "+15550007777",
                  "specialtyId": %s,
                  "medicalLicenseNumber": "%s",
                  "yearsExperience": 9,
                  "bio": "Diagnostician",
                  "consultationFee": 250.00,
                  "profilePictureUrl": "https://example.com/house.png"
                }
                """.formatted(email, specialtyId, license);
    }

    @Test
    void adminCreatesDoctorWithUserRoleAndProfile() throws Exception {
        Specialty specialty = createSpecialty("Admin Doc Cardio");

        MvcResult result = mockMvc.perform(post("/api/v1/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("house@medbook.test", "LIC-HOUSE-1", specialty.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/admin/doctors/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("house@medbook.test"))
                .andExpect(jsonPath("$.specialty").value("Admin Doc Cardio"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        long doctorId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        // The linked user exists with the DOCTOR role and a hashed password.
        User linked = userRepository.findByEmail("house@medbook.test").orElseThrow();
        assertThat(linked.hasRole("DOCTOR")).isTrue();
        assertThat(linked.hasRole("ADMIN")).isFalse();
        assertThat(linked.getPasswordHash()).isNotEqualTo("Doctor#Pass1");
        assertThat(linked.getPasswordHash()).startsWith("$2");

        // Profile fields survived the round trip.
        DoctorAdminResponse adminView = DoctorAdminResponse.from(doctorRepository.findByIdWithUserAndSpecialty(doctorId).orElseThrow());
        assertThat(adminView.medicalLicenseNumber()).isEqualTo("LIC-HOUSE-1");
        assertThat(adminView.consultationFee().compareTo(new BigDecimal("250.00"))).isZero();
        assertThat(adminView.profilePictureUrl()).isEqualTo("https://example.com/house.png");
    }

    @Test
    void duplicateEmailIsRejectedAndNothingIsCreated() throws Exception {
        Specialty specialty = createSpecialty("Admin Dup Email Spec");
        createUserWithRole("taken@medbook.test", "PATIENT");

        mockMvc.perform(post("/api/v1/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("taken@medbook.test", "LIC-DUP-EMAIL", specialty.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email address already exists"));

        // Rollback check: no doctor row exists for the rejected request.
        assertThat(doctorRepository.findByMedicalLicenseNumber("LIC-DUP-EMAIL")).isEmpty();
    }

    @Test
    void duplicateLicenseIsRejectedAndNothingIsCreated() throws Exception {
        Specialty specialty = createSpecialty("Admin Dup Lic Spec");
        Doctor existing = createDoctor("existing-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("newdoc@medbook.test", existing.getMedicalLicenseNumber(), specialty.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));

        // The user for the rejected creation was never committed.
        assertThat(userRepository.findByEmail("newdoc@medbook.test")).isEmpty();
    }

    @Test
    void adminUpdatesDoctorProfile() throws Exception {
        Specialty first = createSpecialty("Admin Upd Spec A");
        Specialty second = createSpecialty("Admin Upd Spec B");
        Doctor doctor = createDoctor("upd-doc@medbook.test", first);

        mockMvc.perform(put("/api/v1/admin/doctors/" + doctor.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "specialtyId": %s,
                                  "medicalLicenseNumber": "%s",
                                  "yearsExperience": 15,
                                  "bio": "Updated bio",
                                  "consultationFee": 410.50,
                                  "profilePictureUrl": null
                                }
                                """.formatted(second.getId(), doctor.getMedicalLicenseNumber())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialty").value("Admin Upd Spec B"))
                .andExpect(jsonPath("$.yearsExperience").value(15))
                .andExpect(jsonPath("$.consultationFee").value(410.50));

        Doctor stored = doctorRepository.findByIdWithUserAndSpecialty(doctor.getId()).orElseThrow();
        assertThat(stored.getSpecialty().getName()).isEqualTo("Admin Upd Spec B");
        assertThat(stored.getYearsExperience()).isEqualTo(15);
    }

    @Test
    void adminDeactivatesDoctorAndDiscoveryStopsOfferingThem() throws Exception {
        Specialty specialty = createSpecialty("Admin Deact Spec");
        Doctor doctor = createDoctor("deact-doc@medbook.test", specialty);

        mockMvc.perform(patch("/api/v1/admin/doctors/" + doctor.getId() + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(doctorRepository.findById(doctor.getId()).orElseThrow().isActive()).isFalse();

        // The public discovery API (active doctors only) no longer offers them.
        String body = mockMvc.perform(get("/api/v1/doctors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                (List<?>) com.jayway.jsonpath.JsonPath.parse(body).read("$[?(@.id == " + doctor.getId() + ")]")
        ).isEmpty();
    }

    @Test
    void patientCannotManageDoctors() throws Exception {
        User patient = createUserWithRole("admin-pat2@medbook.test", "PATIENT");
        mockMvc.perform(get("/api/v1/admin/doctors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient, "ROLE_PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotManageDoctors() throws Exception {
        User doctor = createUserWithRole("admin-doc2@medbook.test", "DOCTOR");
        mockMvc.perform(post("/api/v1/admin/doctors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(doctor, "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("x@medbook.test", "LIC-X", 1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotManageDoctors() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(post("/api/v1/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("anon@medbook.test", "LIC-ANON", 1L)))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // AVAILABILITY
    // ==================================================================

    @Test
    void adminCreatesAvailabilityForDoctor() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Spec");
        Doctor doctor = createDoctor("avail-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek": 1,
                                  "startTime": "09:00",
                                  "endTime": "17:00",
                                  "recurring": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek").value(1))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.endTime").value("17:00"))
                .andExpect(jsonPath("$.recurring").value(true));
    }

    @Test
    void invalidTimeWindowIsRejectedWith400() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail 400 Spec");
        Doctor doctor = createDoctor("avail400-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"17:00\",\"endTime\":\"09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start time must be before end time"));
    }

    @Test
    void invalidDayOfWeekIsRejectedWith400() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Day Spec");
        Doctor doctor = createDoctor("availday-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":9,\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("dayOfWeek")));
    }

    @Test
    void invalidDateRangeIsRejectedWith400() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Range Spec");
        Doctor doctor = createDoctor("availrange-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek": 2,
                                  "startTime": "09:00",
                                  "endTime": "12:00",
                                  "effectiveFrom": "2026-10-10",
                                  "effectiveUntil": "2026-10-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("effectiveUntil must not be before effectiveFrom"));
    }

    @Test
    void duplicateWindowIsRejectedWith409() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Dup Spec");
        Doctor doctor = createDoctor("availdup-doc@medbook.test", specialty);
        DoctorAvailability window = new DoctorAvailability(doctor, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        availabilityRepository.save(window);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This availability window already exists for the doctor"));
    }

    @Test
    void adminUpdatesAvailability() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Upd Spec");
        Doctor doctor = createDoctor("availupd-doc@medbook.test", specialty);
        DoctorAvailability window = new DoctorAvailability(doctor, (byte) 3,
                LocalTime.of(10, 0), LocalTime.of(12, 0));
        window = availabilityRepository.save(window);

        mockMvc.perform(put("/api/v1/admin/availability/" + window.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":4,\"startTime\":\"13:00\",\"endTime\":\"16:00\",\"recurring\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value(4))
                .andExpect(jsonPath("$.startTime").value("13:00"))
                .andExpect(jsonPath("$.recurring").value(false));
    }

    @Test
    void adminClosesAvailabilityAndBookingAvailabilityCheckRejectsClosedWindows() throws Exception {
        Specialty specialty = createSpecialty("Admin Avail Close Spec");
        Doctor doctor = createDoctor("availclose-doc@medbook.test", specialty);
        DoctorAvailability window = new DoctorAvailability(doctor, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        window.setRecurring(true);
        window = availabilityRepository.save(window);

        mockMvc.perform(patch("/api/v1/admin/availability/" + window.getId() + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveUntil").value(LocalDate.now().minusDays(1).toString()));

        // The public availability API (date-filtered) no longer reports the closed window.
        LocalDate nextMonday = LocalDate.now().plusDays(((8 - LocalDate.now().getDayOfWeek().getValue()) % 7) + 7);
        mockMvc.perform(get("/api/v1/doctors/" + doctor.getId() + "/availability?date=" + nextMonday)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotManageAvailability() throws Exception {
        User patient = createUserWithRole("admin-pat3@medbook.test", "PATIENT");
        Specialty specialty = createSpecialty("Admin Avail Guard Spec");
        Doctor doctor = createDoctor("availguard-doc@medbook.test", specialty);

        mockMvc.perform(post("/api/v1/admin/doctors/" + doctor.getId() + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient, "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotManageAvailability() throws Exception {
        User doctorUser = createUserWithRole("availdoc-guard@medbook.test", "DOCTOR");
        Specialty specialty = createSpecialty("Admin Avail Guard2 Spec");
        Doctor doctor = createDoctor("availguard2-doc@medbook.test", specialty);

        mockMvc.perform(put("/api/v1/admin/availability/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(doctorUser, "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // TRANSACTIONS / RESIDUE
    // ==================================================================

    @Test
    void failedDoctorCreationLeavesNoUserAndNoDoctorRows() throws Exception {
        Specialty specialty = createSpecialty("Admin Tx Spec");
        // Valid account data + a license that will conflict mid-transaction.
        Doctor existing = createDoctor("tx-existing@medbook.test", specialty);
        String conflictingLicense = existing.getMedicalLicenseNumber();

        mockMvc.perform(post("/api/v1/admin/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorCreateJson("txnewdoc@medbook.test", conflictingLicense, specialty.getId())))
                .andExpect(status().isConflict());

        // The user row created inside the failed transaction must be gone (rollback).
        assertThat(userRepository.findByEmail("txnewdoc@medbook.test")).isEmpty();
        assertThat(doctorRepository.findByMedicalLicenseNumber(conflictingLicense))
                .map(Doctor::getId)
                .contains(existing.getId());
    }

    @Test
    void doctorLifecycleKeepsExistingAppointmentBehaviourIntact() throws Exception {
        // A booked appointment with a doctor who is later deactivated: the existing
        // appointment must keep working (references are preserved, never deleted).
        Specialty specialty = createSpecialty("Admin Lifecycle Spec");
        Doctor doctor = createDoctor("lifecycle-doc@medbook.test", specialty);

        Role patientRole = roleRepository.findByName("PATIENT").orElseThrow();
        User patientUser = new User("lifecycle-pat@medbook.test", "hashed", "Life", "Cycle", "+919999000003");
        patientUser.addRole(patientRole);
        patientUser = userRepository.save(patientUser);
        Patient patient = patientRepository.save(new Patient(patientUser, LocalDate.of(1990, 1, 1)));

        LocalDate date = LocalDate.now().plusDays(7);
        int mysqlDay = date.getDayOfWeek().getValue() == 7 ? 0 : date.getDayOfWeek().getValue();
        DoctorAvailability availability =
                new DoctorAvailability(doctor, (byte) mysqlDay, LocalTime.of(9, 0), LocalTime.of(17, 0));
        availability.setRecurring(true);
        availabilityRepository.save(availability);

        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, date, LocalTime.of(10, 0), LocalTime.of(10, 30),
                doctor.getConsultationFee()));

        // Deactivate the doctor through the admin API.
        mockMvc.perform(patch("/api/v1/admin/doctors/" + doctor.getId() + "/deactivate"))
                .andExpect(status().isOk());

        // The existing appointment is untouched and keeps its doctor reference.
        Appointment stored = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(stored.getDoctor().getId()).isEqualTo(doctor.getId());
        // No history rows were fabricated by the deactivation.
        assertThat(statusHistoryRepository.findByAppointmentId(appointment.getId())).isEmpty();
    }
}
