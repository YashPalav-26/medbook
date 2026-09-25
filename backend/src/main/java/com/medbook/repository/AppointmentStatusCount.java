package com.medbook.repository;

import com.medbook.entity.AppointmentStatus;

/**
 * Read-only projection row for the admin dashboard overview query
 * ({@code SELECT status, COUNT(*) FROM appointments GROUP BY status}).
 *
 * <p>Spring Data JPA fills interface projections from the aliased JPQL columns
 * ({@code a.status AS status, COUNT(a) AS total}), so no extra DTO class or entity
 * mapping is needed. One grouped query answers "how many appointments are in each
 * status" for the whole clinic — instead of five separate {@code countByStatus}
 * calls, the dashboard overview costs a single round trip.</p>
 *
 * <p>Groups are only returned for statuses that currently exist in the table; the
 * admin service turns the sparse rows into a complete per-status map with explicit
 * zeros, so the API contract is stable.</p>
 */
public interface AppointmentStatusCount {

    /** The lifecycle status this row groups by. */
    AppointmentStatus getStatus();

    /** Number of appointments currently in {@link #getStatus()}. */
    long getTotal();
}
