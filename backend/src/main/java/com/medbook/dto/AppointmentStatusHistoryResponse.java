package com.medbook.dto;

import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.User;

import java.time.LocalDateTime;

/**
 * One status-change audit row, as exposed through the admin appointment detail.
 *
 * <p>The {@code appointment_status_history} table records every lifecycle event
 * (creation, confirmation, completion, cancellation, no-show) with the user who caused
 * it. Admins may read that trail; patients and doctors never see it (their APIs keep
 * returning {@link AppointmentResponse}, which has no history field).</p>
 *
 * <p>Only admin-safe fields are exposed: the status, the free-text note, the timestamp,
 * and the actor's display <em>name</em> — never the raw {@code changed_by_user_id} row
 * reference, never email, phone or any other account field, and never the JPA entity
 * itself (serializing {@link com.medbook.entity.AppointmentStatusHistory} would walk
 * lazy {@code appointment} and {@code changedByUser} proxies and leak internals).</p>
 *
 * @param id            history row identifier
 * @param status        the status recorded at that point of the lifecycle
 * @param notes         free-text note stored with the change (for example the cancellation reason)
 * @param changedByName "First Last" of the user who caused the change, {@code null} when
 *                      the row was written without an authenticated caller (internal/test
 *                      path) or the user was later deleted (ON DELETE SET NULL)
 * @param changedAt     when the change happened (database-generated)
 */
public record AppointmentStatusHistoryResponse(
        Long id,
        AppointmentStatus status,
        String notes,
        String changedByName,
        LocalDateTime changedAt
) {

    /**
     * Map a persisted history row to its admin-safe representation.
     *
     * @param history a persisted history row ({@code changedByUser} may be an unloaded
     *                proxy; reading its name fields resolves it — call this only inside
     *                the service transaction, as this API does)
     * @return the response DTO for that row
     */
    public static AppointmentStatusHistoryResponse from(AppointmentStatusHistory history) {
        User changedBy = history.getChangedByUser();
        return new AppointmentStatusHistoryResponse(
                history.getId(),
                history.getStatus(),
                history.getNotes(),
                changedBy == null ? null : changedBy.getFirstName() + " " + changedBy.getLastName(),
                history.getChangedAt()
        );
    }
}
