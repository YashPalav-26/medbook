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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AppointmentOwnershipTest {

    private static final BigDecimal TEST_FEE = new BigDecimal("500.00");

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private User patientAUser;
    private User patientBUser;
    private User doctorAUser;
    private User doctorBUser;
    private User adminUser;

    private Patient patientA;
    private Patient patientB;
    private Doctor doctorA;
    private Doctor doctorB;

    @BeforeEach
    void setUpFixtures() {
        patientAUser = createUser("PATIENT", "owner-a@medbook.test", "Patient", "OwnerA", "+919900000001");
        patientBUser = createUser("PATIENT", "owner-b@medbook.test", "Patient", "OwnerB", "+919900000002");
        doctorAUser = createUser("DOCTOR", "doc-a@medbook.test", "Doctor", "OwnerA", "+919900000003");
        doctorBUser = createUser("DOCTOR", "doc-b@medbook.test", "Doctor", "OwnerB", "+919900000004");

        adminUser = createUser("ADMIN", "admin-own@" + System.nanoTime() + "medbook.test", "Admin", "User", "+919900000005");

        patientA = patientRepository.save(new Patient(patientAUser, LocalDate.of(1990, 1, 1)));
        patientB = patientRepository.save(new Patient(patientBUser, LocalDate.of(1991, 2, 2)));

        Specialty specialtyA = specialtyRepository.save(new Specialty("Ownership Cardiology", "test"));
        Specialty specialtyB = specialtyRepository.save(new Specialty("Ownership Dermatology", "test"));
        doctorA = doctorRepository.save(
                new Doctor(doctorAUser, specialtyA, "LIC-OWN-A-" + System.nanoTime(), 5, TEST_FEE));
        doctorB = doctorRepository.save(
                new Doctor(doctorBUser, specialtyB, "LIC-OWN-B-" + System.nanoTime(), 5, TEST_FEE));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String roleName, String email, String first, String last, String phone) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));
        User user = new User(email, "hashed-password", first, last, phone);
        user.addRole(role);
        return userRepository.save(user);
    }

    private void loginAs(User user, String... roles) {
        Map<String, Object> claims = Map.of(
                "sub", String.valueOf(user.getId()),
                "email", user.getEmail(),
                "roles", java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toList());
        Jwt jwt = new Jwt("test-token-" + user.getId(),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "HS256"), claims);
        List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities =
                java.util.Arrays.stream(roles)
                        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt, authorities)));
    }

    private Appointment bookAppointment(Patient patient, Doctor doctor) {
        LocalDate date = LocalDate.now().plusDays(7);
        int javaDay = date.getDayOfWeek().getValue();
        byte mysqlDay = (byte) (javaDay == 7 ? 0 : javaDay);
        if (availabilityRepository.findByDoctorId(doctor.getId()).isEmpty()) {
            DoctorAvailability availability =
                    new DoctorAvailability(doctor, mysqlDay, LocalTime.of(9, 0), LocalTime.of(17, 0));
            availability.setRecurring(true);
            availabilityRepository.save(availability);
        }

        return bookAppointment(patient, doctor, LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    private Appointment bookAppointment(Patient patient, Doctor doctor, LocalTime start, LocalTime end) {
        LocalDate date = LocalDate.now().plusDays(7);
        int javaDay = date.getDayOfWeek().getValue();
        byte mysqlDay = (byte) (javaDay == 7 ? 0 : javaDay);
        if (availabilityRepository.findByDoctorId(doctor.getId()).isEmpty()) {
            DoctorAvailability availability =
                    new DoctorAvailability(doctor, mysqlDay, LocalTime.of(9, 0), LocalTime.of(17, 0));
            availability.setRecurring(true);
            availabilityRepository.save(availability);
        }

        return appointmentService.createAppointment(
                patient.getId(), doctor.getId(), date, start, end);
    }

    @Test
    void patientCanRetrieveOwnAppointment() {
        Appointment owned = bookAppointment(patientA, doctorA);
        loginAs(patientAUser, "PATIENT");

        Appointment loaded = appointmentService.getAppointment(owned.getId());

        assertThat(loaded.getId()).isEqualTo(owned.getId());
    }

    @Test
    void patientCannotRetrieveAnotherPatientsAppointment() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        loginAs(patientBUser, "PATIENT");

        assertThatThrownBy(() -> appointmentService.getAppointment(ownedByA.getId()))
                .isInstanceOf(AppointmentOwnershipException.class)
                .hasMessage("You do not have access to this appointment");
    }

    @Test
    void patientCanCancelOwnAppointmentAndHistoryRecordsTheRealCaller() {
        Appointment owned = bookAppointment(patientA, doctorA);
        loginAs(patientAUser, "PATIENT");

        Appointment cancelled = appointmentService.cancelAppointmentForCurrentUser(
                owned.getId(), "Patient changed plans");

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(owned.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(history.get(0).getChangedByUser()).isNull();
        assertThat(history.get(1).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        assertThat(history.get(1).getChangedByUser()).isNotNull();
        assertThat(history.get(1).getChangedByUser().getId()).isEqualTo(patientAUser.getId());
    }

    @Test
    void patientCannotCancelAnotherPatientsAppointment() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        loginAs(patientBUser, "PATIENT");

        assertThatThrownBy(() -> appointmentService.cancelAppointmentForCurrentUser(
                ownedByA.getId(), "Not my appointment"))
                .isInstanceOf(AppointmentOwnershipException.class);

        Appointment reloaded = appointmentRepository.findById(ownedByA.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(statusHistoryRepository.findByAppointmentId(ownedByA.getId())).hasSize(1);
    }

    @Test
    void doctorCanRetrieveAppointmentsAssignedToThem() {
        Appointment assigned = bookAppointment(patientA, doctorA);
        loginAs(doctorAUser, "DOCTOR");

        Appointment loaded = appointmentService.getAppointment(assigned.getId());

        assertThat(loaded.getId()).isEqualTo(assigned.getId());
    }

    @Test
    void doctorCannotRetrieveAppointmentsAssignedToAnotherDoctor() {
        Appointment assignedToA = bookAppointment(patientA, doctorA);
        loginAs(doctorBUser, "DOCTOR");

        assertThatThrownBy(() -> appointmentService.getAppointment(assignedToA.getId()))
                .isInstanceOf(AppointmentOwnershipException.class);
    }

    @Test
    void doctorCannotCancelAppointmentsAssignedToAnotherDoctor() {
        Appointment assignedToA = bookAppointment(patientA, doctorA);
        loginAs(doctorBUser, "DOCTOR");

        assertThatThrownBy(() -> appointmentService.cancelAppointmentForCurrentUser(
                assignedToA.getId(), "Wrong doctor"))
                .isInstanceOf(AppointmentOwnershipException.class);

        assertThat(appointmentRepository.findById(assignedToA.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void doctorMeListingReturnsOnlyAssignedAppointments() {
        Appointment assignedToA = bookAppointment(patientA, doctorA);
        bookAppointment(patientB, doctorB);
        loginAs(doctorAUser, "DOCTOR");

        List<Appointment> mine = appointmentService.getCurrentUserAppointments();

        assertThat(mine).extracting(Appointment::getId).containsExactly(assignedToA.getId());
    }

    @Test
    void adminCanReadAnyAppointmentForSupport() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        loginAs(adminUser, "ADMIN");

        assertThat(appointmentService.getAppointment(ownedByA.getId()).getId())
                .isEqualTo(ownedByA.getId());
    }

    @Test
    void adminCancellationIsExplicitlyRejected() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        loginAs(adminUser, "ADMIN");

        assertThatThrownBy(() -> appointmentService.cancelAppointmentForCurrentUser(
                ownedByA.getId(), "Admin cancellation"))
                .isInstanceOf(AppointmentOwnershipException.class);

        assertThat(appointmentRepository.findById(ownedByA.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void adminMeListingIsExplicitlyRejected() {
        bookAppointment(patientA, doctorA);
        loginAs(adminUser, "ADMIN");

        assertThatThrownBy(() -> appointmentService.getCurrentUserAppointments())
                .isInstanceOf(AppointmentOwnershipException.class)
                .hasMessage("Appointment listing is not available for this account role");
    }

    @Test
    void patientMeListingReturnsOnlyOwnAppointments() {
        Appointment ownedByA = bookAppointment(patientA, doctorA,
                LocalTime.of(10, 0), LocalTime.of(10, 30));
        Appointment ownedByB = bookAppointment(patientB, doctorA,
                LocalTime.of(11, 0), LocalTime.of(11, 30));
        loginAs(patientAUser, "PATIENT");

        List<Appointment> mine = appointmentService.getCurrentUserAppointments();

        assertThat(mine).extracting(Appointment::getId).containsExactly(ownedByA.getId());
        assertThat(mine).extracting(Appointment::getId).doesNotContain(ownedByB.getId());
    }

    @Test
    void meListingIsRejectedWithoutAuthenticatedCaller() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> appointmentService.getCurrentUserAppointments())
                .isInstanceOf(org.springframework.security.authentication.InsufficientAuthenticationException.class);
    }

    @Test
    void accessIsRejectedWhenTheTokenUserNoLongerExists() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);

        Map<String, Object> claims = Map.of("sub", "999999999", "email", "ghost@medbook.test",
                "roles", List.of("ROLE_PATIENT"));
        Jwt jwt = new Jwt("ghost-token", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "HS256"), claims);
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT")))));

        assertThatThrownBy(() -> appointmentService.getAppointment(ownedByA.getId()))
                .isInstanceOf(AppointmentOwnershipException.class)
                .hasMessage("Authenticated account no longer exists");
    }

    @Test
    void serviceLevelAccessFailsWithoutAuthentication() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> appointmentService.getAppointment(ownedByA.getId()))
                .isInstanceOf(org.springframework.security.authentication.InsufficientAuthenticationException.class);
        assertThatThrownBy(() -> appointmentService.cancelAppointmentForCurrentUser(ownedByA.getId(), "x"))
                .isInstanceOf(org.springframework.security.authentication.InsufficientAuthenticationException.class);
    }

    @Test
    void creationHistoryRecordsTheAuthenticatedCaller() {
        loginAs(patientAUser, "PATIENT");
        Appointment created = bookAppointment(patientA, doctorA);

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(created.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangedByUser()).isNotNull();
        assertThat(history.get(0).getChangedByUser().getId()).isEqualTo(patientAUser.getId());
    }

    @Test
    void creationHistoryWithoutCallerLegitimatelyStaysNull() {

        Appointment created = bookAppointment(patientA, doctorA);

        List<AppointmentStatusHistory> history =
                statusHistoryRepository.findByAppointmentIdOrderByChangedAtAsc(created.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangedByUser()).isNull();
    }

    @Test
    void noCrossUserLeakageAcrossReadListAndCancel() {
        Appointment ownedByA = bookAppointment(patientA, doctorA);
        Appointment ownedByB = bookAppointment(patientB, doctorB);

        loginAs(patientAUser, "PATIENT");
        assertThat(appointmentService.getCurrentUserAppointments())
                .extracting(Appointment::getId)
                .containsExactly(ownedByA.getId())
                .doesNotContain(ownedByB.getId());
        assertThatThrownBy(() -> appointmentService.getAppointment(ownedByB.getId()))
                .isInstanceOf(AppointmentOwnershipException.class);
        assertThatThrownBy(() -> appointmentService.cancelAppointmentForCurrentUser(ownedByB.getId(), "x"))
                .isInstanceOf(AppointmentOwnershipException.class);
    }
}
