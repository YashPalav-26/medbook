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

/**
 * Notification business logic (Step 25).
 *
 * <h3>Two kinds of entry points</h3>
 * <ul>
 * <li><strong>{@link #createAppointmentNotification}</strong> — called from the
 *     appointment workflows while their transaction is still open. It deliberately
 *     runs in the <em>same transaction</em>
 *     ({@code Propagation.MANDATORY} = join the caller's transaction or fail):
 *     the appointment change and its notification commit together or not at all.
 *     That is the reliable, predictable choice for a clinic — an appointment that
 *     was confirmed must never be missing the "confirmed" notification, and a
 *     rolled-back confirmation must never leave a notification about something
 *     that did not happen. The trade-off is explicit: if notification persistence
 *     fails, the appointment change is rolled back too (fail-loud, no silent
 *     notification loss, no async infrastructure).</li>
 * <li><strong>Everything else</strong> — reads and read-state flips for the
 *     authenticated caller. Identity always comes from the JWT
 *     ({@code CurrentUserProvider}); no user ID is ever accepted from a request,
 *     so there is no route that can address another user's notifications.</li>
 * </ul>
 *
 * <h3>Duplicates</h3>
 * <p>Notification creation happens exactly once per workflow call, at the single
 * code site that performs each lifecycle transition. A transition that is invalid
 * (409) throws before any notification is created, and a transaction rollback
 * (including the notification INSERT) removes any half-written notification — so
 * neither retries nor failed requests can accumulate duplicates.</p>
 */
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

    // ------------------------------------------------------------------
    // Creation (called by the appointment workflows, inside their transaction)
    // ------------------------------------------------------------------

    /**
     * Create one notification for a user about an appointment event.
     *
     * <p><strong>Transaction contract:</strong> {@code Propagation.MANDATORY} makes
     * the caller's open transaction a hard requirement — calling this without one
     * fails immediately instead of silently committing on its own. The
     * notification INSERT joins the appointment's transaction: both commit
     * together, both roll back together.</p>
     *
     * @param recipient   the user who should see the notification
     * @param type        which lifecycle event this is
     * @param title       short headline, for example "Appointment confirmed"
     * @param message     longer detail, for example "Dr. Wells confirmed your visit on 2026-10-01"
     * @param referenceId the related appointment's ID (nullable)
     * @return the persisted notification
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notification createAppointmentNotification(User recipient,
                                                      NotificationType type,
                                                      String title,
                                                      String message,
                                                      Long referenceId) {
        Notification notification = new Notification(recipient, type, title, message, referenceId);
        return notificationRepository.save(notification);
    }

    /**
     * Notify the patient that their appointment reached {@code type}'s lifecycle state.
     * One line per workflow call site — the wording lives here so every notification
     * about the same event reads the same way.
     *
     * <p>{@code MANDATORY}: must run inside the appointment workflow's transaction.</p>
     */
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

    /**
     * Notify the doctor about an appointment event (used when someone else acts on
     * their schedule — today: cancellations). {@code MANDATORY}: must run inside the
     * appointment workflow's transaction.
     */
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

    /**
     * Display name of the user who caused a lifecycle event, for notification wording.
     * Null-safe: context-free internal/test callers legitimately have no user row.
     */
    public static String actorName(User actor) {
        return actor == null ? "the clinic" : actor.getFirstName() + " " + actor.getLastName();
    }

    // ------------------------------------------------------------------
    // Current-user operations (identity from the JWT only)
    // ------------------------------------------------------------------

    /**
     * The authenticated caller's notifications, newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * How many unread notifications the authenticated caller has (the bell badge).
     */
    @Transactional(readOnly = true)
    public long getCurrentUserUnreadCount() {
        Long userId = currentUserProvider.requireCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Mark one of the caller's own notifications as read.
     *
     * @throws NotificationNotFoundException when the notification does not exist
     *         <em>or</em> belongs to another user (indistinguishable by design)
     */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = currentUserProvider.requireCurrentUserId();

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    /**
     * Mark all of the caller's unread notifications as read in one statement.
     *
     * <p>The bulk UPDATE bypasses the persistence context; the follow-up count
     * query runs inside the same transaction, so it always reflects the update.</p>
     *
     * @return the caller's unread count after marking (0 after a successful call,
     *         verified rather than assumed)
     */
    @Transactional
    public long markAllAsRead() {
        Long userId = currentUserProvider.requireCurrentUserId();
        notificationRepository.markAllReadForUser(userId);
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
}
