package com.medbook.service;

import com.medbook.dto.NotificationResponse;
import com.medbook.entity.Appointment;
import com.medbook.entity.Notification;
import com.medbook.entity.NotificationType;
import com.medbook.entity.User;
import com.medbook.repository.NotificationRepository;
import com.medbook.repository.UserRepository;
import com.medbook.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;






























@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               CurrentUserProvider currentUserProvider) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }





















    @Transactional(propagation = Propagation.MANDATORY)
    public Notification createAppointmentNotification(User recipient,
                                                      NotificationType type,
                                                      String title,
                                                      String message,
                                                      Long referenceId) {
        Notification notification = new Notification(recipient, type, title, message, referenceId);
        return notificationRepository.save(notification);
    }








    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyPatient(Appointment appointment, NotificationType type, String actorName) {
        User patientUser = appointment.getPatient().getUser();
        String when = appointment.getAppointmentDate() + " at " + appointment.getStartTime();
        String doctorName = appointment.getDoctor().getUser().getFirstName() + " "
                + appointment.getDoctor().getUser().getLastName();

        String title;
        String message;
        switch (type) {
            case APPOINTMENT_CREATED -> {
                title = "Appointment booked";
                message = "Your appointment with Dr. " + doctorName + " on " + when
                        + " has been booked.";
            }
            case APPOINTMENT_CONFIRMED -> {
                title = "Appointment confirmed";
                message = "Dr. " + doctorName + " confirmed your appointment on " + when + ".";
            }
            case APPOINTMENT_CANCELLED -> {
                title = "Appointment cancelled";
                message = "Your appointment with Dr. " + doctorName + " on " + when
                        + " was cancelled by " + actorName + ".";
            }
            case APPOINTMENT_COMPLETED -> {
                title = "Visit completed";
                message = "Your visit with Dr. " + doctorName + " on " + when
                        + " was marked completed.";
            }
            case APPOINTMENT_NO_SHOW -> {
                title = "Marked as no-show";
                message = "You were marked as a no-show for your appointment with Dr. "
                        + doctorName + " on " + when + ".";
            }
            default -> throw new IllegalArgumentException("Unsupported notification type: " + type);
        }

        createAppointmentNotification(patientUser, type, title, message, appointment.getId());
    }






    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDoctor(Appointment appointment, NotificationType type, String actorName) {
        User doctorUser = appointment.getDoctor().getUser();
        String when = appointment.getAppointmentDate() + " at " + appointment.getStartTime();
        String patientName = appointment.getPatient().getUser().getFirstName() + " "
                + appointment.getPatient().getUser().getLastName();

        if (type == NotificationType.APPOINTMENT_CANCELLED) {
            createAppointmentNotification(
                    doctorUser,
                    type,
                    "Appointment cancelled",
                    "The appointment with " + patientName + " on " + when
                            + " was cancelled by " + actorName + ".",
                    appointment.getId());
            return;
        }
        throw new IllegalArgumentException("Unsupported doctor notification type: " + type);
    }





    public static String actorName(User actor) {
        return actor == null ? "the clinic" : actor.getFirstName() + " " + actor.getLastName();
    }








    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }




    @Transactional(readOnly = true)
    public long getCurrentUserUnreadCount() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }







    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = currentUserProvider.requireCurrentUserId();

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }










    @Transactional
    public long markAllAsRead() {
        Long userId = currentUserProvider.requireCurrentUserId();
        notificationRepository.markAllReadForUser(userId);
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
}
