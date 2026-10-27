package com.medbook.repository;

import com.medbook.entity.AppointmentStatus;

public interface AppointmentStatusCount {

    AppointmentStatus getStatus();

    long getTotal();
}
