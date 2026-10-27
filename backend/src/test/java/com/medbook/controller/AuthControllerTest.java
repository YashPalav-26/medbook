package com.medbook.controller;

import com.jayway.jsonpath.JsonPath;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static String registerJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "firstName": "Test",
                  "lastName": "Patient",
                  "phoneNumber": "+919876543300",
                  "dateOfBirth": "1990-01-01"
                }
                """.formatted(email, password);
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private User createUser(String email, String rawPassword, String roleName,
                            boolean active, boolean locked) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));
        User user = new User(email, passwordEncoder.encode(rawPassword), "Auth", "Test", "+919876543301");
        user.addRole(role);
        user.setActive(active);
        user.setLocked(locked);
        return userRepository.save(user);
    }

    private String registerAndReturnToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void registerReturns201WithTokenUserAndPatientRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("auth-new@medbook.test", "SecretPass1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMinutes").value(60))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("auth-new@medbook.test"))
                .andExpect(jsonPath("$.user.firstName").value("Test"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_PATIENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("is_locked");
        assertThat(body).doesNotContain("failed_login_attempts");
    }

    @Test
    void registerStoresBcryptHashAndCreatesPatientProfile() throws Exception {
        registerAndReturnToken("auth-hash@medbook.test", "SecretPass1");

        User stored = userRepository.findByEmail("auth-hash@medbook.test").orElseThrow();

        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).isNotEqualTo("SecretPass1");
        assertThat(passwordEncoder.matches("SecretPass1", stored.getPasswordHash())).isTrue();

        Patient profile = patientRepository.findByUserId(stored.getId()).orElseThrow();
        assertThat(profile.isActive()).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        registerAndReturnToken("auth-dupe@medbook.test", "SecretPass1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("auth-dupe@medbook.test", "SecretPass2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("An account with this email address already exists"));
    }

    @Test
    void publicRegistrationNeverEscalatesToAdmin() throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-role@medbook.test",
                                  "password": "SecretPass1",
                                  "firstName": "Test",
                                  "lastName": "Patient",
                                  "phoneNumber": "+919876543300",
                                  "dateOfBirth": "1990-01-01",
                                  "role": "ADMIN",
                                  "roles": ["ADMIN", "DOCTOR"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        List<String> roles = JsonPath.read(result.getResponse().getContentAsString(), "$.user.roles");
        assertThat(roles).containsExactly("ROLE_PATIENT");

        User stored = userRepository.findByEmail("auth-role@medbook.test").orElseThrow();
        assertThat(stored.getRoles()).extracting(Role::getName).containsExactly("PATIENT");
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "short",
                                  "firstName": "",
                                  "lastName": "Patient",
                                  "phoneNumber": "not a phone",
                                  "dateOfBirth": "2990-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'firstName')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phoneNumber')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'dateOfBirth')]").isNotEmpty());
    }

    @Test
    void loginReturns200WithTokenAndDecodableClaims() throws Exception {
        registerAndReturnToken("auth-login@medbook.test", "SecretPass1");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-login@medbook.test", "SecretPass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("auth-login@medbook.test"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_PATIENT"))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        User stored = userRepository.findByEmail("auth-login@medbook.test").orElseThrow();
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(stored.getId()));
        assertThat(jwt.<String>getClaimAsString("email")).isEqualTo("auth-login@medbook.test");
        assertThat(jwt.<List<String>>getClaimAsStringList("roles")).containsExactly("ROLE_PATIENT");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).hasMinutes(60);
    }

    @Test
    void loginRejectsUnknownEmailWithGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-nobody@medbook.test", "WrongPass99")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void loginRejectsWrongPasswordWithGenericMessageAndCountsFailure() throws Exception {
        createUser("auth-wrong@medbook.test", "RightPass99", "PATIENT", true, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-wrong@medbook.test", "WrongPass99")))
                .andExpect(status().isUnauthorized())

                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        User stored = userRepository.findByEmail("auth-wrong@medbook.test").orElseThrow();
        assertThat(stored.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginRejectsLockedUserOnlyAfterCredentialsMatch() throws Exception {

        createUser("auth-locked@medbook.test", "RightPass99", "PATIENT", true, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-locked@medbook.test", "WrongPass99")))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-locked@medbook.test", "RightPass99")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("This account is locked. Please contact the clinic to unlock it."));
    }

    @Test
    void loginRejectsInactiveUserOnlyAfterCredentialsMatch() throws Exception {
        createUser("auth-inactive@medbook.test", "RightPass99", "PATIENT", false, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-inactive@medbook.test", "WrongPass99")))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-inactive@medbook.test", "RightPass99")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("This account is deactivated. Please contact the clinic."));
    }

    @Test
    void loginSuccessResetsFailuresAndUpdatesLastLogin() throws Exception {
        createUser("auth-track@medbook.test", "RightPass99", "PATIENT", true, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-track@medbook.test", "WrongPass99")))
                .andExpect(status().isUnauthorized());

        MvcResult ok = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("auth-track@medbook.test", "RightPass99")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((String) JsonPath.read(ok.getResponse().getContentAsString(), "$.token")).isNotBlank();

        User stored = userRepository.findByEmail("auth-track@medbook.test").orElseThrow();
        assertThat(stored.getFailedLoginAttempts()).isZero();
        assertThat(stored.getLastLoginAt()).isNotNull();
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"\", \"password\": \"\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").isNotEmpty());
    }
}
