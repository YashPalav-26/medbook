package com.medbook.dto;

import com.medbook.entity.AppointmentStatusHistory;
import com.medbook.entity.AppointmentStatus;
import com.medbook.entity.User;

import java.time.LocalDateTime;























public record AppointmentStatusHistoryResponse(
        Long id,
        AppointmentStatus status,
        String notes,
        String changedByName,
        LocalDateTime changedAt
) {









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
