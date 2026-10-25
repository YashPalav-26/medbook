package com.medbook.controller;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.Notification;
import com.medbook.entity.NotificationType;
import com.medbook.entity.Patient;
import com.medbook.entity.Role;
import com.medbook.entity.Specialty;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.NotificationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;










@SpringBootTest
@Transactional
class NotificationsTest {

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
    private DoctorAvailabilityRepository availabilityRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private String patientToken;
    private String doctorToken;
    private User patientUser;
    private User doctorUser;
    private Patient patient;
    private Doctor doctor;
    private Specialty specialty;

    private static final String PASSWORD = "Test#Pass1";

    @BeforeEach
    void setUp() {
        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new IllegalStateException("PATIENT role not seeded"));
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not seeded"));


        String suffix = String.valueOf(System.nanoTime());
        patientUser = new User("notif-pat-" + suffix + "@medbook.test",
                passwordEncoder.encode(PASSWORD), "Paul", "Patient", "+919111000001");
        patientUser.addRole(patientRole);
        patientUser = userRepository.save(patientUser);

        doctorUser = new User("notif-doc-" + suffix + "@medbook.test",
                passwordEncoder.encode(PASSWORD), "Dana", "Doctor", "+919111000002");
        doctorUser.addRole(doctorRole);
        doctorUser = userRepository.save(doctorUser);

        patient = patientRepository.save(new Patient(patientUser, LocalDate.of(1990, 1, 1)));
        specialty = specialtyRepository.save(new Specialty("Notif Spec-" + suffix, "test"));
        doctor = doctorRepository.save(new Doctor(doctorUser, specialty,
                "LIC-NOTIF-" + suffix, 5, new BigDecimal("150.00")));


        DoctorAvailability availability = new DoctorAvailability(doctor, (byte) 1,
                LocalTime.of(8, 0), LocalTime.of(18, 0));
        availabilityRepository.save(availability);

        patientToken = createTestToken(String.valueOf(patientUser.getId()), "ROLE_PATIENT");
        doctorToken = createTestToken(String.valueOf(doctorUser.getId()), "ROLE_DOCTOR");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken))
                .build();
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


    private Appointment createAppointment(AppointmentStatus status) {
        LocalDate nextMonday = LocalDate.now().plusWeeks(2);
        while (nextMonday.getDayOfWeek().getValue() != 1) {
            nextMonday = nextMonday.plusDays(1);
        }
        Appointment appointment = new Appointment(patient, doctor, nextMonday,
                LocalTime.of(10, 0), LocalTime.of(10, 30), doctor.getConsultationFee());
        appointment.setStatus(status);
        if (status == AppointmentStatus.CANCELLED) {
            appointment.setCancellationReason("fixture cancellation");
        }
        return appointmentRepository.save(appointment);
    }

    private Notification createNotification(User owner, NotificationType type, boolean read) {
        Notification notification = new Notification(owner, type, "Title " + type, "Message " + type, null);
        notification.setRead(read);
        return notificationRepository.save(notification);
    }





    @Test
    void createdNotificationIsPersistedAndBelongsToItsUser() {
        Notification notification = createNotification(patientUser, NotificationType.APPOINTMENT_CREATED, false);

        assertThat(notification.getId()).isNotNull();
        Notification stored = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(patientUser.getId());
        assertThat(stored.getType()).isEqualTo(NotificationType.APPOINTMENT_CREATED);
        assertThat(stored.isRead()).isFalse();



        notificationRepository.flush();
        entityManager.clear();
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getCreatedAt())
                .isNotNull();
    }

    @Test
    void userRetrievesOnlyOwnNotificationsNewestFirst() throws Exception {
        Notification oldest = createNotification(patientUser, NotificationType.APPOINTMENT_CREATED, false);
        Notification newest = createNotification(patientUser, NotificationType.APPOINTMENT_CONFIRMED, false);

        createNotification(doctorUser, NotificationType.APPOINTMENT_CANCELLED, false);





        jakarta.persistence.Query q = entityManager.createQuery(
                "UPDATE Notification n SET n.createdAt = :t WHERE n.id = :id");
        q.setParameter("t", java.time.LocalDateTime.now().minusMinutes(10));
        q.setParameter("id", oldest.getId());
        q.executeUpdate();
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$..id");
        assertThat(ids).containsExactly(newest.getId().intValue(), oldest.getId().intValue());

        assertThat(body).doesNotContain(patientUser.getEmail());
        assertThat(body).doesNotContain("passwordHash");
    }

    @Test
    void userCannotRetrieveAnotherUsersNotifications() throws Exception {


        Notification doctorNotification = createNotification(doctorUser, NotificationType.APPOINTMENT_CREATED, false);

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(patch("/api/v1/notifications/" + doctorNotification.getId() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken))
                .andExpect(status().isNotFound());
    }





    @Test
    void unreadCountReflectsOnlyUnreadRowsOfTheCaller() throws Exception {
        createNotification(patientUser, NotificationType.APPOINTMENT_CREATED, false);
        createNotification(patientUser, NotificationType.APPOINTMENT_CONFIRMED, false);
        createNotification(patientUser, NotificationType.APPOINTMENT_COMPLETED, true);
        createNotification(doctorUser, NotificationType.APPOINTMENT_CREATED, false);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    void markAsReadFlipsOnlyThatRow() throws Exception {
        Notification a = createNotification(patientUser, NotificationType.APPOINTMENT_CREATED, false);
        Notification b = createNotification(patientUser, NotificationType.APPOINTMENT_CONFIRMED, false);

        mockMvc.perform(patch("/api/v1/notifications/" + a.getId() + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.getId().intValue()))
                .andExpect(jsonPath("$.read").value(true));

        assertThat(notificationRepository.findById(a.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(b.getId()).orElseThrow().isRead()).isFalse();

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    @Test
    void markAllAsReadClearsTheBadge() throws Exception {
        createNotification(patientUser, NotificationType.APPOINTMENT_CREATED, false);
        createNotification(patientUser, NotificationType.APPOINTMENT_CONFIRMED, false);
        createNotification(patientUser, NotificationType.APPOINTMENT_COMPLETED, true);

        mockMvc.perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    @Test
    void markingUnknownNotificationIs404() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/999999999/read"))
                .andExpect(status().isNotFound());
    }





    @Test
    void bookingCreatesNotificationForPatient() throws Exception {
        LocalDate nextMonday = LocalDate.now().plusWeeks(2);
        while (nextMonday.getDayOfWeek().getValue() != 1) {
            nextMonday = nextMonday.plusDays(1);
        }

        MvcResult bookingResult = mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doctorId": %d, "appointmentDate": "%s", "startTime": "10:00:00", "endTime": "10:30:00", "reason": "checkup"}
                                """.formatted(doctor.getId(), nextMonday)))
                .andExpect(status().isCreated())
                .andReturn();

        long appointmentId = ((Number) com.jayway.jsonpath.JsonPath
                .read(bookingResult.getResponse().getContentAsString(), "$.id")).longValue();



        entityManager.clear();

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_CREATED"))
                .andExpect(jsonPath("$[0].referenceId").value((int) appointmentId))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].title").isString())
                .andExpect(jsonPath("$[0].message").isString())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void doctorConfirmationCreatesNotificationForPatient() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_CONFIRMED"))
                .andExpect(jsonPath("$[0].referenceId").value(appointment.getId().intValue()));
    }

    @Test
    void doctorCompletionCreatesNotificationForPatient() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_COMPLETED"))
                .andExpect(jsonPath("$[0].referenceId").value(appointment.getId().intValue()));
    }

    @Test
    void doctorNoShowCreatesNotificationForPatient() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_NO_SHOW"))
                .andExpect(jsonPath("$[0].referenceId").value(appointment.getId().intValue()));
    }

    @Test
    void patientCancellationNotifiesTheDoctorNotThePatient() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/appointments/" + appointment.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"cannot make it\"}"))
                .andExpect(status().isOk());


        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId())).isEmpty();

        List<Notification> doctorNotifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(doctorUser.getId());
        assertThat(doctorNotifications).hasSize(1);
        assertThat(doctorNotifications.get(0).getType()).isEqualTo(NotificationType.APPOINTMENT_CANCELLED);
        assertThat(doctorNotifications.get(0).getReferenceId()).isEqualTo(appointment.getId());
    }

    @Test
    void adminCancellationNotifiesBothPatientAndDoctor() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        String suffix = String.valueOf(System.nanoTime());
        User adminUser = new User("notif-admin-" + suffix + "@medbook.test", "hashed", "Ada", "Admin", "+919111000003");
        adminUser.addRole(adminRole);
        adminUser = userRepository.save(adminUser);
        String adminToken = createTestToken(String.valueOf(adminUser.getId()), "ROLE_ADMIN");

        Appointment appointment = createAppointment(AppointmentStatus.SCHEDULED);

        mockMvc.perform(post("/api/v1/admin/appointments/" + appointment.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\":\"clinic closed\"}"))
                .andExpect(status().isOk());

        List<Notification> patientNotifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId());
        List<Notification> doctorNotifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(doctorUser.getId());
        assertThat(patientNotifications).hasSize(1);
        assertThat(patientNotifications.get(0).getType()).isEqualTo(NotificationType.APPOINTMENT_CANCELLED);
        assertThat(doctorNotifications).hasSize(1);
        assertThat(doctorNotifications.get(0).getType()).isEqualTo(NotificationType.APPOINTMENT_CANCELLED);
    }





    @Test
    void failedBookingLeavesNoNotificationResidue() throws Exception {


        LocalDate nextMonday = LocalDate.now().plusWeeks(2);
        while (nextMonday.getDayOfWeek().getValue() != 1) {
            nextMonday = nextMonday.plusDays(1);
        }

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doctorId": %d, "appointmentDate": "%s", "startTime": "11:00:00", "endTime": "10:00:00"}
                                """.formatted(doctor.getId(), nextMonday)))
                .andExpect(status().isBadRequest());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId())).isEmpty();



        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doctorId": %d, "appointmentDate": "%s", "startTime": "10:00:00", "endTime": "10:30:00"}
                                """.formatted(doctor.getId(), nextMonday)))
                .andExpect(status().isCreated());

        List<Notification> notifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.APPOINTMENT_CREATED);
    }

    @Test
    void invalidTransitionFails409AndCreatesNoNotification() throws Exception {


        Appointment appointment = createAppointment(AppointmentStatus.SCHEDULED);
        String confirmUrl = "/api/v1/appointments/" + appointment.getId() + "/confirm";

        mockMvc.perform(post(confirmUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isOk());
        long confirmedNotifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(patientUser.getId()).size();
        assertThat(confirmedNotifications).isEqualTo(1);

        mockMvc.perform(post(confirmUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken))
                .andExpect(status().isConflict());

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId()))
                .hasSize(1);
    }





    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        anonymous.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        anonymous.perform(patch("/api/v1/notifications/1/read")).andExpect(status().isUnauthorized());
        anonymous.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
