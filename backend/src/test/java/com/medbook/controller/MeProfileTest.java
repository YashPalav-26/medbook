package com.medbook.controller;

import com.medbook.entity.Appointment;
import com.medbook.entity.Doctor;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the own-profile API ({@code /api/v1/me/**}) over real MySQL
 * with real JWTs: current-user reads, patient/doctor profile updates, the
 * editable/admin-controlled field split, and the authenticated password change.
 *
 * <p>The class is {@code @Transactional}, so every fixture row is rolled back — no
 * residue. Password-change fixtures mint a REAL BCrypt hash so the wrong-password
 * path is exercised against genuine encoder behaviour.</p>
 */
@SpringBootTest
@Transactional
class MeProfileTest {

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
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private Role adminRole;
    private Role doctorRole;
    private Role patientRole;
    private Specialty specialty;

    private static final String PLAINTEXT_PASSWORD = "Current#Pass1";
    private static final String NEW_PASSWORD = "New#Secret99";

    @BeforeEach
    void setUp() {
        adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));
        patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new IllegalStateException("PATIENT role not seeded"));
        specialty = specialtyRepository.save(
                new Specialty("Profile Spec-" + System.nanoTime(), "test"));

        // Admin with a real BCrypt hash so password change flows are authentic.
        User admin = new User("me-admin-" + System.nanoTime() + "@medbook.test",
                passwordEncoder.encode(PLAINTEXT_PASSWORD), "Ada", "Admin", "+919998880001");
        admin.addRole(adminRole);
        admin = userRepository.save(admin);
        adminToken = createTestToken(String.valueOf(admin.getId()), "ROLE_ADMIN");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
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

    private User createUser(String email, String password, Role role) {
        User user = new User(email, passwordEncoder.encode(password), "Test", "User", "+919998880002");
        user.addRole(role);
        return userRepository.save(user);
    }

    private String tokenFor(User user, String... roles) {
        return createTestToken(String.valueOf(user.getId()), roles);
    }

    private Patient createPatientWithProfile(String email) {
        User user = createUser(email, PLAINTEXT_PASSWORD, patientRole);
        Patient patient = patientRepository.save(new Patient(user, LocalDate.of(1990, 5, 15)));
        return patient;
    }

    private Doctor createDoctorWithProfile(String email) {
        User user = createUser(email, PLAINTEXT_PASSWORD, doctorRole);
        Doctor doctor = new Doctor(user, specialty, "LIC-ME-" + System.nanoTime(), 5,
                new BigDecimal("250.00"));
        doctor.setBio("Original bio");
        doctor.setProfilePictureUrl("https://example.com/original.png");
        return doctorRepository.save(doctor);
    }

    // ==================================================================
    // CURRENT USER READ
    // ==================================================================

    @Test
    void authenticatedPatientReadsOwnProfile() throws Exception {
        Patient patient = createPatientWithProfile("me-pat1-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.getUser().getId()))
                .andExpect(jsonPath("$.email").value(patient.getUser().getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PATIENT"))
                .andExpect(jsonPath("$.patient.patientId").value(patient.getId()))
                .andExpect(jsonPath("$.patient.dateOfBirth").value("1990-05-15"))
                .andExpect(jsonPath("$.doctor").doesNotExist())
                // Security internals are never serialized.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.locked").doesNotExist());
    }

    @Test
    void authenticatedDoctorReadsOwnProfileWithAdminControlledFields() throws Exception {
        Doctor doctor = createDoctorWithProfile("me-doc1-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(doctor.getUser(), "ROLE_DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctor.doctorId").value(doctor.getId()))
                .andExpect(jsonPath("$.doctor.bio").value("Original bio"))
                .andExpect(jsonPath("$.doctor.specialtyName").isNotEmpty())
                .andExpect(jsonPath("$.doctor.medicalLicenseNumber").isNotEmpty())
                .andExpect(jsonPath("$.doctor.consultationFee").value(250.00))
                .andExpect(jsonPath("$.doctor.active").value(true))
                .andExpect(jsonPath("$.patient").doesNotExist());
    }

    @Test
    void adminReadsAccountInformationOnly() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.doctor").doesNotExist());
    }

    @Test
    void unauthenticatedProfileReadIsRejected() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // PATIENT PROFILE UPDATE
    // ==================================================================

    /**
     * The patient-profile update is a full-form PUT (project convention): every field
     * is required, "" clears. Helper builds a complete valid payload with overrides.
     */
    private String patientProfileJson(String... overrides) {
        StringBuilder json = new StringBuilder();
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("addressLine1", "12 Beach Road");
        fields.put("addressLine2", "Apt 4B");
        fields.put("city", "Mumbai");
        fields.put("stateProvince", "MH");
        fields.put("postalCode", "400001");
        fields.put("country", "India");
        fields.put("emergencyContactName", "Rita Reed");
        fields.put("emergencyContactPhone", "+91 98200 11222");
        fields.put("insuranceProvider", "Blue Cross");
        fields.put("insurancePolicyNumber", "POL-7788");
        fields.put("allergies", "Peanuts, latex");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            fields.put(overrides[i], overrides[i + 1]);
        }
        json.append("{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append(",\"hasInsurance\":true}");
        return json.toString();
    }

    @Test
    void patientUpdatesOwnProfileFields() throws Exception {
        Patient patient = createPatientWithProfile("me-pat2-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/patient-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressLine1": "12 Beach Road",
                                  "addressLine2": "Apt 4B",
                                  "city": "Mumbai",
                                  "stateProvince": "MH",
                                  "postalCode": "400001",
                                  "country": "India",
                                  "emergencyContactName": "Rita Reed",
                                  "emergencyContactPhone": "+91 98200 11222",
                                  "hasInsurance": true,
                                  "insuranceProvider": "Blue Cross",
                                  "insurancePolicyNumber": "POL-7788",
                                  "allergies": "Peanuts, latex"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.city").value("Mumbai"))
                .andExpect(jsonPath("$.patient.hasInsurance").value(true))
                .andExpect(jsonPath("$.patient.allergies").value("Peanuts, latex"));

        // Persisted, not just echoed.
        Patient reloaded = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(reloaded.getCity()).isEqualTo("Mumbai");
        assertThat(reloaded.getEmergencyContactPhone()).isEqualTo("+91 98200 11222");
        assertThat(reloaded.hasInsurance()).isTrue();
        // Registration data stays untouched.
        assertThat(reloaded.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
    }

    @Test
    void patientProfileValidationRejectsOverlongFields() throws Exception {
        Patient patient = createPatientWithProfile("me-pat3-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/patient-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileJson("city", "x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'city')]").exists());
    }

    @Test
    void doctorCannotUpdatePatientProfile() throws Exception {
        Doctor doctor = createDoctorWithProfile("me-doc2-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/patient-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(doctor.getUser(), "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileJson()))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // DOCTOR PROFILE UPDATE + ADMIN-CONTROLLED PROTECTION
    // ==================================================================

    @Test
    void doctorUpdatesOwnBioAndPicture() throws Exception {
        Doctor doctor = createDoctorWithProfile("me-doc3-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/doctor-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(doctor.getUser(), "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Cardiologist with 15 years of practice",
                                  "profilePictureUrl": "https://example.com/new-avatar.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctor.bio").value("Cardiologist with 15 years of practice"))
                .andExpect(jsonPath("$.doctor.profilePictureUrl").value("https://example.com/new-avatar.png"));

        Doctor reloaded = doctorRepository.findById(doctor.getId()).orElseThrow();
        assertThat(reloaded.getBio()).isEqualTo("Cardiologist with 15 years of practice");
        assertThat(reloaded.getProfilePictureUrl()).isEqualTo("https://example.com/new-avatar.png");
    }

    @Test
    void doctorProfileUpdateCannotTouchAdminControlledFields() throws Exception {
        Doctor doctor = createDoctorWithProfile("me-doc4-" + System.nanoTime() + "@medbook.test");
        BigDecimal originalFee = doctor.getConsultationFee();
        String originalLicense = doctor.getMedicalLicenseNumber();
        Long originalSpecialtyId = doctor.getSpecialty().getId();

        // The update type has no fee/license/specialty fields — but prove a hostile
        // payload carrying them changes nothing.
        mockMvc.perform(put("/api/v1/me/doctor-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(doctor.getUser(), "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "harmless",
                                  "consultationFee": 1.00,
                                  "medicalLicenseNumber": "HACKED",
                                  "specialtyName": "Surgery",
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk());

        Doctor reloaded = doctorRepository.findById(doctor.getId()).orElseThrow();
        assertThat(reloaded.getBio()).isEqualTo("harmless");
        assertThat(reloaded.getConsultationFee()).isEqualByComparingTo(originalFee);
        assertThat(reloaded.getMedicalLicenseNumber()).isEqualTo(originalLicense);
        assertThat(reloaded.getSpecialty().getId()).isEqualTo(originalSpecialtyId);
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void patientCannotUpdateDoctorProfile() throws Exception {
        Patient patient = createPatientWithProfile("me-pat4-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/doctor-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"hacked\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // CROSS-USER PROTECTION
    // ==================================================================

    @Test
    void patientProfileUpdateTouchesOnlyTheCallersOwnRow() throws Exception {
        Patient caller = createPatientWithProfile("me-pat5-" + System.nanoTime() + "@medbook.test");
        Patient other = createPatientWithProfile("me-pat6-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/patient-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(caller.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientProfileJson("city", "CallerCity")))
                .andExpect(status().isOk());

        // The other patient's row is untouched — there is no ID parameter to misuse,
        // and the lookup keys on the JWT subject alone.
        assertThat(patientRepository.findById(other.getId()).orElseThrow().getCity())
                .isNotEqualTo("CallerCity");
    }

    @Test
    void doctorProfileUpdateTouchesOnlyTheCallersOwnRow() throws Exception {
        Doctor caller = createDoctorWithProfile("me-doc5-" + System.nanoTime() + "@medbook.test");
        Doctor other = createDoctorWithProfile("me-doc6-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/doctor-profile")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(caller.getUser(), "ROLE_DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"caller bio\"}"))
                .andExpect(status().isOk());

        assertThat(doctorRepository.findById(other.getId()).orElseThrow().getBio())
                .isEqualTo("Original bio");
    }

    // ==================================================================
    // ACCOUNT UPDATE (name/phone, every role)
    // ==================================================================

    @Test
    void accountUpdateChangesNameAndPhone() throws Exception {
        Patient patient = createPatientWithProfile("me-pat7-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jonathan",
                                  "lastName": "Carter",
                                  "phoneNumber": "+91 90000 12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jonathan"))
                .andExpect(jsonPath("$.lastName").value("Carter"))
                .andExpect(jsonPath("$.phoneNumber").value("+91 90000 12345"));

        User reloaded = userRepository.findById(patient.getUser().getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Jonathan");
        // Email is the sign-in identity and stays fixed.
        assertThat(reloaded.getEmail()).isEqualTo(patient.getUser().getEmail());
    }

    @Test
    void accountUpdateRejectsInvalidPhone() throws Exception {
        Patient patient = createPatientWithProfile("me-pat8-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(put("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"phoneNumber\":\"call me maybe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phoneNumber"));
    }

    // ==================================================================
    // PASSWORD CHANGE
    // ==================================================================

    @Test
    void passwordChangeWithCorrectCurrentPasswordSucceeds() throws Exception {
        Patient patient = createPatientWithProfile("me-pat9-" + System.nanoTime() + "@medbook.test");
        Long userId = patient.getUser().getId();

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(PLAINTEXT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // The new hash verifies against the new password and is a real BCrypt hash.
        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PLAINTEXT_PASSWORD, reloaded.getPasswordHash())).isFalse();
        assertThat(reloaded.getPasswordHash()).startsWith("$2");
        // The plaintext new password appears nowhere in storage.
        assertThat(reloaded.getPasswordHash()).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void passwordChangeWithWrongCurrentPasswordIsRejected() throws Exception {
        Patient patient = createPatientWithProfile("me-pat10-" + System.nanoTime() + "@medbook.test");
        String originalHash = userRepository.findById(patient.getUser().getId()).orElseThrow()
                .getPasswordHash();

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "WrongPassword1",
                                  "newPassword": "%s"
                                }
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Nothing changed.
        User reloaded = userRepository.findById(patient.getUser().getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void passwordChangeValidationRejectsShortNewPassword() throws Exception {
        Patient patient = createPatientWithProfile("me-pat11-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PLAINTEXT_PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        Patient patient = createPatientWithProfile("me-pat12-" + System.nanoTime() + "@medbook.test");

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedPasswordChangeIsRejected() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(post("/api/v1/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"Whatever#1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeDoesNotAffectExistingAppointments() throws Exception {
        // The appointment stays intact and untouched by the account change.
        Patient patient = createPatientWithProfile("me-pat13-" + System.nanoTime() + "@medbook.test");
        Doctor doctor = createDoctorWithProfile("me-doc7-" + System.nanoTime() + "@medbook.test");
        Appointment appointment = appointmentRepository.save(new Appointment(
                patient, doctor, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(10, 30), doctor.getConsultationFee()));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(patient.getUser(), "ROLE_PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(PLAINTEXT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(com.medbook.entity.AppointmentStatus.SCHEDULED);
        assertThat(reloaded.getPatient().getId()).isEqualTo(patient.getId());
    }
}
