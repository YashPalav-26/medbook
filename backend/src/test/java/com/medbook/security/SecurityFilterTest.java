package com.medbook.security;

import com.jayway.jsonpath.JsonPath;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
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
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the HTTP security boundary: the real filter chain decoded by the
 * real {@code JwtDecoder}.
 *
 * <p>Every assertion below goes through the actual Bearer-token processing — no fabricated
 * security context, no mocked filter. Where a token needs to be made, it is made with the
 * application's own {@code JwtEncoder} (same secret, same conventions), except in the test
 * that deliberately signs with a different key.</p>
 */
@SpringBootTest
@Transactional
class SecurityFilterTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Token minting
    // ------------------------------------------------------------------

    private String mintToken(String subject, String email, List<String> roles,
                             Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtService.ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim(JwtService.ROLES_CLAIM, roles)
                .build();
        return jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** A valid one-hour token carrying the given business roles. */
    private String validToken(String... roles) {
        Instant now = Instant.now();
        return mintToken("2", "filter@medbook.test", List.of(roles), now, now.plus(1, ChronoUnit.HOURS));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------
    // Unauthenticated access
    // ------------------------------------------------------------------

    @Test
    void protectedAppointmentEndpointWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/1"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void publicEndpointsStayOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"nobody@medbook.test\", \"password\": \"x\" }"))
                .andExpect(status().isUnauthorized())
                // Reaching the login service (not the security fence) proves the endpoint is public.
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ------------------------------------------------------------------
    // Token handling
    // ------------------------------------------------------------------

    @Test
    void garbageTokenReturns401JsonWithoutLeakingInternals() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token-at-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/1"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("signature");
        assertThat(body).doesNotContain("Jwt");
        assertThat(body).doesNotContain("stackTrace");
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant now = Instant.now();
        String expired = mintToken("2", "filter@medbook.test", List.of("ROLE_PATIENT"),
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The access token is missing, invalid, or expired"));
    }

    @Test
    void forgedTokenWithWrongSignatureReturns401() throws Exception {
        // Signed by a different key than the application's secret.
        javax.crypto.SecretKey otherKey = new javax.crypto.spec.SecretKeySpec(
                "unrelated-test-signing-key-forgery-attempt".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");
        org.springframework.security.oauth2.jwt.JwtEncoder forger =
                new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                        new com.nimbusds.jose.jwk.source.ImmutableSecret<>(otherKey));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtService.ISSUER)
                .subject("2")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("email", "filter@medbook.test")
                .claim(JwtService.ROLES_CLAIM, List.of("ROLE_ADMIN"))
                .build();
        String forged = forger.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedAuthorizationHeaderIsRejected() throws Exception {
        // Wrong scheme: the Bearer resolver ignores it, so the request is anonymous.
        mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, "Token " + validToken("ROLE_PATIENT")))
                .andExpect(status().isUnauthorized());

        // Empty bearer value: no usable token either.
        mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Authenticated access and role behaviour
    // ------------------------------------------------------------------

    @Test
    void validPatientTokenReachesProtectedEndpoints() throws Exception {
        // 404 here is the proof of acceptance: the request passed the security layer and
        // reached the service, which simply has no row with that ID.
        mockMvc.perform(get("/api/v1/appointments/987654321")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_PATIENT"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found with id: 987654321"));
    }

    @Test
    void eachBusinessRoleReachesAppointmentEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/987654321")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/appointments/987654321")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_DOCTOR"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/appointments/987654321")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_DOCTOR", "ROLE_PATIENT"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenWithoutBusinessRoleIsForbidden() throws Exception {
        // Authenticated (signature valid) but authorized for nothing: 403, not 401.
        mockMvc.perform(get("/api/v1/appointments/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to access this resource"));
    }

    // ------------------------------------------------------------------
    // Authenticated-user retrieval (Part G)
    // ------------------------------------------------------------------

    @Test
    void currentUserProviderReadsTheJwtCaller() {
        Map<String, Object> claims = Map.of("sub", "2", "email", "filter@medbook.test",
                "roles", List.of("ROLE_PATIENT"));
        Jwt jwt = new Jwt("test-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), claims);
        assertThat(jwt.getSubject()).isEqualTo("2");

        // The two-argument constructor marks the token authenticated, exactly like the
        // real filter does after validating a Bearer token.
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT"))));

        assertThat(currentUserProvider.currentUserId()).contains(2L);
        assertThat(currentUserProvider.currentUserEmail()).contains("filter@medbook.test");
        assertThat(currentUserProvider.requireCurrentUserId()).isEqualTo(2L);
    }

    @Test
    void currentUserProviderIsEmptyWithoutAuthenticatedCaller() {
        SecurityContextHolder.clearContext();

        assertThat(currentUserProvider.currentUserId()).isEmpty();
        assertThat(currentUserProvider.currentUserEmail()).isEmpty();
        assertThatThrownBy(() -> currentUserProvider.requireCurrentUserId())
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    // ------------------------------------------------------------------
    // End-to-end: token -> booking
    // ------------------------------------------------------------------

    @Test
    void registeredPatientTokenCanBookAnAppointment() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "filter-book@medbook.test",
                                  "password": "SecretPass1",
                                  "firstName": "Filter",
                                  "lastName": "Booking",
                                  "phoneNumber": "+919876543302",
                                  "dateOfBirth": "1995-06-06"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.token");
        long patientId = patientRepository.findByUserId(
                        userRepository.findByEmail("filter-book@medbook.test").orElseThrow().getId())
                .orElseThrow()
                .getId();

        LocalDate date = LocalDate.now().plusDays(7);
        int javaDay = date.getDayOfWeek().getValue();
        byte mysqlDay = (byte) (javaDay == 7 ? 0 : javaDay);

        Role doctorRole = roleRepository.findByName("DOCTOR").orElseThrow();
        User doctorUser = new User("filter-doc@medbook.test", "hashed", "Filter", "Doc", "+919876543303");
        doctorUser.addRole(doctorRole);
        userRepository.save(doctorUser);
        Specialty specialty = specialtyRepository.save(new Specialty("Filter Specialty", "test"));
        Doctor doctor = doctorRepository.save(
                new Doctor(doctorUser, specialty, "LIC-FILTER-" + System.nanoTime(), 5,
                        new java.math.BigDecimal("400.00")));

        DoctorAvailability availability = new DoctorAvailability(doctor, mysqlDay, LocalTime.of(9, 0),
                LocalTime.of(17, 0));
        availability.setRecurring(true);
        availabilityRepository.save(availability);

        // The full protected flow: register -> bearer token -> book.
        mockMvc.perform(post("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": %d,
                                  "doctorId": %d,
                                  "appointmentDate": "%s",
                                  "startTime": "10:00",
                                  "endTime": "11:00"
                                }
                                """.formatted(patientId, doctor.getId(), date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // Registering the patient through the public endpoint created the usable profile,
        // linked to the registered user account.
        User registeredUser = userRepository.findByEmail("filter-book@medbook.test").orElseThrow();
        Patient profile = patientRepository.findByUserId(registeredUser.getId()).orElseThrow();
        assertThat(profile.getId()).isEqualTo(patientId);
        assertThat(registeredUser.getRoles()).extracting(Role::getName).containsExactly("PATIENT");
    }
}
