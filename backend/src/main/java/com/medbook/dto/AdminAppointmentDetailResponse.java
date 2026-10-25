package com.medbook.dto;

import com.medbook.entity.Appointment;

import java.util.List;














public record AdminAppointmentDetailResponse(
        AdminAppointmentResponse appointment,
        List<AppointmentStatusHistoryResponse> history
) {









    public static AdminAppointmentDetailResponse from(Appointment appointment,
                                                      List<AppointmentStatusHistoryResponse> history) {
        return new AdminAppointmentDetailResponse(
                AdminAppointmentResponse.from(appointment),
                List.copyOf(history)
        );
    }
}
