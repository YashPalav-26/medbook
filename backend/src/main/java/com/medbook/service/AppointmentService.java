package com.medbook.service;

import com.medbook.entity.Appointment;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.Doctor;
import com.medbook.entity.DoctorAvailability;
import com.medbook.entity.NotificationType;
import com.medbook.entity.Patient;
import com.medbook.entity.User;
import com.medbook.repository.AppointmentRepository;
import com.medbook.repository.AppointmentStatusHistoryRepository;
import com.medbook.repository.DoctorAvailabilityRepository;
import com.medbook.repository.DoctorRepository;
import com.medbook.repository.PatientRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


















@Service
public class AppointmentService {





    private static final int JAVA_SUNDAY = 7;


    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PATIENT = "PATIENT";


    private static final String ACCESS_DENIED_TO_APPOINTMENT =
            "You do not have access to this appointment";




















    private static final Map<AppointmentStatus, Set<AppointmentStatus>> DOCTOR_TRANSITIONS = Map.of(
            AppointmentStatus.SCHEDULED, Set.of(AppointmentStatus.CONFIRMED),
            AppointmentStatus.CONFIRMED, Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository statusHistoryRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;







    private final EntityManager entityManager;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository statusHistoryRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorAvailabilityRepository availabilityRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            NotificationService notificationService,
            EntityManager entityManager
    ) {
        this.appointmentRepository = appointmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationService = notificationService;
        this.entityManager = entityManager;
    }
























    @Transactional(readOnly = true)
    public Appointment getAppointment(Long appointmentId) {
        User caller = requireCurrentUser();

        Appointment appointment = appointmentRepository.findByIdWithPatientAndDoctor(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!canAccess(caller, appointment)) {
            throw new AppointmentOwnershipException(ACCESS_DENIED_TO_APPOINTMENT);
        }
        return appointment;
    }

















    @Transactional(readOnly = true)
    public List<Appointment> getCurrentUserAppointments() {
        User caller = requireCurrentUser();

        if (caller.hasRole(ROLE_PATIENT)) {
            return appointmentRepository.findAllByPatientUserId(caller.getId());
        }
        if (caller.hasRole(ROLE_DOCTOR)) {
            return appointmentRepository.findAllByDoctorUserId(caller.getId());
        }
        throw new AppointmentOwnershipException(
                "Appointment listing is not available for this account role");
    }




























    @Transactional
    public Appointment createAppointmentForCurrentUser(com.medbook.dto.PatientBookingRequest request) {
        User caller = requireCurrentUser();

        if (!caller.hasRole(ROLE_PATIENT)) {
            throw new AppointmentOwnershipException(
                    "Only patients can book appointments for themselves;"
                            + " use the support path to book on behalf of a patient");
        }

        Patient patient = patientRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account has no patient profile: " + caller.getId()));

        return createAppointment(
                patient.getId(),
                request.doctorId(),
                request.appointmentDate(),
                request.startTime(),
                request.endTime(),
                request.reason());
    }















    @Transactional
    public Appointment createAppointmentForPatient(com.medbook.dto.CreateAppointmentRequest request) {
        User caller = requireCurrentUser();

        if (!caller.hasRole(ROLE_ADMIN)) {
            throw new AppointmentOwnershipException(
                    "Booking for another patient requires an administrator");
        }
        return createAppointment(
                request.patientId(),
                request.doctorId(),
                request.appointmentDate(),
                request.startTime(),
                request.endTime(),
                null);
    }








    @Transactional
    public Appointment createAppointment(
            Long patientId,
            Long doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return createAppointment(patientId, doctorId, appointmentDate, startTime, endTime, null);
    }
































    @Transactional
    public Appointment createAppointment(
            Long patientId,
            Long doctorId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {


        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));

        if (!doctor.isActive()) {
            throw new DoctorInactiveException(doctorId);
        }


        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        if (!patient.isActive()) {
            throw new PatientInactiveException(patientId);
        }


        validateAppointmentWindow(appointmentDate, startTime, endTime);


        ensureDoctorIsAvailable(doctor, appointmentDate, startTime, endTime);


        ensureSlotIsFree(doctor, appointmentDate, startTime, endTime);


        Appointment appointment = new Appointment(
                patient,
                doctor,
                appointmentDate,
                startTime,
                endTime,
                doctor.getConsultationFee()
        );
        appointment.setReason(reason);
        Appointment saved = appointmentRepository.save(appointment);




        recordStatusChange(saved, AppointmentStatus.SCHEDULED, "Appointment created",
                currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null));



        notificationService.notifyPatient(saved, NotificationType.APPOINTMENT_CREATED, null);



        return reloadWithParticipants(saved.getId());
    }





















    @Transactional
    public Appointment cancelAppointmentForCurrentUser(Long appointmentId, String cancellationReason) {
        User caller = requireCurrentUser();

        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!canCancel(caller, appointment)) {
            throw new AppointmentOwnershipException(ACCESS_DENIED_TO_APPOINTMENT);
        }
        return cancelAppointment(appointment, caller, cancellationReason);
    }














    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String cancellationReason) {
        return cancelAppointment(appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId)), null, cancellationReason);
    }





    private Appointment cancelAppointment(Appointment appointment, User caller, String cancellationReason) {
        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        AppointmentStatus currentStatus = appointment.getStatus();

        if (currentStatus == AppointmentStatus.CANCELLED) {
            throw new InvalidAppointmentStatusException(
                    currentStatus, AppointmentStatus.CANCELLED, "appointment is already cancelled");
        }

        if (currentStatus == AppointmentStatus.COMPLETED || currentStatus == AppointmentStatus.NO_SHOW) {
            throw new InvalidAppointmentStatusException(
                    currentStatus, AppointmentStatus.CANCELLED,
                    "a finished appointment (COMPLETED or NO_SHOW) cannot be cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(cancellationReason);
        Appointment saved = appointmentRepository.save(appointment);



        recordStatusChange(saved, AppointmentStatus.CANCELLED, cancellationReason, caller);




        if (caller != null
                && appointment.getPatient().getUser().getId().equals(caller.getId())) {
            notificationService.notifyDoctor(saved, NotificationType.APPOINTMENT_CANCELLED,
                    NotificationService.actorName(caller));
        } else {
            notificationService.notifyPatient(saved, NotificationType.APPOINTMENT_CANCELLED,
                    NotificationService.actorName(caller));
        }

        return reloadWithParticipants(saved.getId());
    }










    @Transactional
    public Appointment confirmAppointmentForCurrentUser(Long appointmentId) {
        return transitionAppointmentForCurrentUser(
                appointmentId, AppointmentStatus.CONFIRMED, "Appointment confirmed by doctor");
    }










    @Transactional
    public Appointment completeAppointmentForCurrentUser(Long appointmentId) {
        return transitionAppointmentForCurrentUser(
                appointmentId, AppointmentStatus.COMPLETED, "Visit completed");
    }










    @Transactional
    public Appointment markNoShowForCurrentUser(Long appointmentId) {
        return transitionAppointmentForCurrentUser(
                appointmentId, AppointmentStatus.NO_SHOW, "Patient marked as no-show by doctor");
    }
















    private Appointment transitionAppointmentForCurrentUser(
            Long appointmentId, AppointmentStatus target, String notes) {
        User caller = requireCurrentUser();

        if (!caller.hasRole(ROLE_DOCTOR)) {
            throw new AppointmentOwnershipException(
                "Only the assigned doctor can perform this action");
        }

        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!appointment.getDoctor().getUser().getId().equals(caller.getId())) {
            throw new AppointmentOwnershipException(ACCESS_DENIED_TO_APPOINTMENT);
        }

        AppointmentStatus currentStatus = appointment.getStatus();
        Set<AppointmentStatus> allowed = DOCTOR_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidAppointmentStatusException(currentStatus, target,
                "this appointment action is not valid from the current status");
        }

        applyDoctorTransition(appointment, target);
        Appointment saved = appointmentRepository.save(appointment);



        recordStatusChange(saved, target, notes, caller);



        notificationService.notifyPatient(saved, toNotificationType(target),
                NotificationService.actorName(caller));

        return reloadWithParticipants(saved.getId());
    }











    private Appointment reloadWithParticipants(Long appointmentId) {
        entityManager.flush();
        entityManager.clear();
        return appointmentRepository.findByIdWithPatientAndDoctor(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
    }




    private static NotificationType toNotificationType(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> NotificationType.APPOINTMENT_CONFIRMED;
            case COMPLETED -> NotificationType.APPOINTMENT_COMPLETED;
            case NO_SHOW -> NotificationType.APPOINTMENT_NO_SHOW;
            default -> throw new IllegalArgumentException(
                    "No patient notification for doctor transition to " + status);
        };
    }





    private void applyDoctorTransition(Appointment appointment, AppointmentStatus target) {
        appointment.setStatus(target);
        LocalDateTime now = LocalDateTime.now();
        if (target == AppointmentStatus.CONFIRMED) {
            appointment.setConfirmedAt(now);
        } else if (target == AppointmentStatus.COMPLETED) {
            appointment.setCompletedAt(now);
        }
    }











    private void validateAppointmentWindow(LocalDate appointmentDate, LocalTime startTime, LocalTime endTime) {
        if (appointmentDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("Appointment date, start time and end time are required");
        }

        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Appointment date cannot be in the past: " + appointmentDate);
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "Start time " + startTime + " must be before end time " + endTime);
        }
    }





    private void ensureDoctorIsAvailable(
            Doctor doctor,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        byte dayOfWeek = toMysqlDayOfWeek(appointmentDate);
        List<DoctorAvailability> slots =
                availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), dayOfWeek);

        boolean covered = slots.stream()
                .anyMatch(slot -> coversAppointment(slot, appointmentDate, startTime, endTime));

        if (!covered) {
            throw new DoctorUnavailableException(doctor.getId(), appointmentDate, startTime, endTime);
        }
    }


















    private boolean coversAppointment(
            DoctorAvailability slot,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (startTime.isBefore(slot.getStartTime()) || endTime.isAfter(slot.getEndTime())) {
            return false;
        }

        if (slot.isRecurring()) {
            boolean startsTooLate = slot.getEffectiveFrom() != null
                    && appointmentDate.isBefore(slot.getEffectiveFrom());
            boolean endsTooEarly = slot.getEffectiveUntil() != null
                    && appointmentDate.isAfter(slot.getEffectiveUntil());
            return !startsTooLate && !endsTooEarly;
        }

        LocalDate oneOffDate = slot.getEffectiveFrom() != null
                ? slot.getEffectiveFrom()
                : slot.getEffectiveUntil();

        return oneOffDate != null && appointmentDate.equals(oneOffDate);
    }





    private void ensureSlotIsFree(
            Doctor doctor,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        List<Appointment> conflicts = appointmentRepository.findConflictingAppointments(
                doctor.getId(),
                appointmentDate,
                startTime,
                endTime,
                AppointmentStatus.CANCELLED
        );

        if (!conflicts.isEmpty()) {
            throw new AppointmentConflictException(doctor.getId(), appointmentDate, startTime, endTime);
        }
    }








    private void recordStatusChange(Appointment appointment, AppointmentStatus status, String notes, User changedBy) {
        AppointmentStatusHistory history = new AppointmentStatusHistory(appointment, status);
        history.setNotes(notes);
        history.setChangedByUser(changedBy);
        statusHistoryRepository.save(history);
    }










    private User requireCurrentUser() {
        Long callerId = currentUserProvider.requireCurrentUserId();
        return userRepository.findById(callerId)
                .orElseThrow(() -> new AppointmentOwnershipException(
                        "Authenticated account no longer exists"));
    }











    private boolean canAccess(User caller, Appointment appointment) {
        if (caller.hasRole(ROLE_ADMIN)) {
            return true;
        }
        if (caller.hasRole(ROLE_DOCTOR)) {
            return appointment.getDoctor().getUser().getId().equals(caller.getId());
        }
        if (caller.hasRole(ROLE_PATIENT)) {
            return appointment.getPatient().getUser().getId().equals(caller.getId());
        }
        return false;
    }









    private boolean canCancel(User caller, Appointment appointment) {
        if (caller.hasRole(ROLE_ADMIN)) {
            return false;
        }
        return canAccess(caller, appointment);
    }





    private static byte toMysqlDayOfWeek(LocalDate date) {
        int javaDayOfWeek = date.getDayOfWeek().getValue();
        return (byte) (javaDayOfWeek == JAVA_SUNDAY ? 0 : javaDayOfWeek);
    }
}
