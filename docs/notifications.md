# In-App Notification System (Step 25)

A beginner-friendly guide to how MedBook notifies users about appointment events,
how the data is stored, which REST endpoints exist, and how the React UI shows them.

---

## 1. Why notifications exist

In a clinic, things change after you book: a doctor confirms your visit, clinic
support cancels it, your visit gets completed. Before Step 25, the only way to
learn about any of that was to keep re-opening the app and checking. A notification
is a small, persistent message that MedBook writes **for a specific user** whenever
one of those appointment events happens. The user sees a badge with the number of
unread messages on a bell in the header and can read them at their own pace.

Notifications are deliberately **in-app only**. There is no email, SMS, or push in
this step — those are future integrations (see §12). Everything needed for them
later (a reliable record of who should be told what) already exists after this step.

---

## 2. The notification lifecycle

A notification has a very short life story:

1. **Created** — an appointment workflow (booking, confirmation, …) inserts one row
   into the `notifications` table, addressed to one user, with `is_read = FALSE`.
2. **Read** — the recipient opens the notification (or clicks "Mark all read"),
   and `is_read` flips to `TRUE`.
3. That's it. Notifications are never edited, never re-sent, and never deleted
   (except when their owner's account is deleted — the FK is `ON DELETE CASCADE`).

This is intentionally simpler than appointments: an appointment is a *workflow* with
statuses and rules; a notification is a *sticky note* that gets pinned and later
ticked off.

---

## 3. Notification types

| Type | When it fires | Who receives it |
|---|---|---|
| `APPOINTMENT_CREATED` | A booking is made (patient self-booking or admin support path) | The patient |
| `APPOINTMENT_CONFIRMED` | The assigned doctor confirms (SCHEDULED → CONFIRMED) | The patient |
| `APPOINTMENT_CANCELLED` | Patient cancels | The **doctor** (the actor already knows) |
| `APPOINTMENT_CANCELLED` | Doctor cancels | The **patient** |
| `APPOINTMENT_CANCELLED` | Admin cancels (support action) | **Both** patient and doctor |
| `APPOINTMENT_COMPLETED` | The doctor completes the visit | The patient |
| `APPOINTMENT_NO_SHOW` | The doctor marks the patient as a no-show | The patient |

The rule of thumb: **everyone affected by the event gets told, the person who caused
it doesn't get told about their own action.**

---

## 4. Relationship to appointments

Each notification row has a `reference_id` column that stores the related
appointment's ID (nullable, in case a future notification type isn't about an
appointment). The frontend uses it to make the notification clickable: clicking
"Appointment confirmed" opens `/appointments/3689`.

Note the relationship is *by ID only*, not a foreign key to `appointments`. That is
on purpose: notifications are historical records. If an appointment could ever be
removed, its notifications should still make sense to the recipient.

---

## 5. Storage: the `notifications` table (V2 migration)

Step 25 added one additive Flyway migration, `V2__create_notifications.sql`
(V1 was not touched). The table:

```sql
CREATE TABLE notifications (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,          -- the recipient
    type         VARCHAR(50)  NOT NULL,             -- one of the 5 enum values (CHECK constraint)
    title        VARCHAR(100) NOT NULL,             -- e.g. "Appointment confirmed"
    message      VARCHAR(500) NOT NULL,             -- human-readable detail
    reference_id BIGINT UNSIGNED,                   -- related appointment ID (nullable)
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user_created (user_id, created_at DESC),
    INDEX idx_notifications_user_unread  (user_id, is_read),
    CHECK (type IN ('APPOINTMENT_CREATED','APPOINTMENT_CONFIRMED','APPOINTMENT_CANCELLED',
                    'APPOINTMENT_COMPLETED','APPOINTMENT_NO_SHOW'))
);
```

Design notes:

- `user_id` points at the existing `users` table — a notification belongs to a
  **user**, not to a patient or doctor row. An account that is both (like the
  seeded admin, who also has a patient profile) sees one unified inbox.
- The two indexes cover the only read patterns the app has: "newest first list for
  this user" and "unread count for this user".
- Like every table in MedBook, `created_at` is written by MySQL
  (`DEFAULT CURRENT_TIMESTAMP(3)`); Hibernate does not set it.

Verify with:

```bash
mysql -uroot -proot medbook -e "SELECT version, description, success FROM flyway_schema_history;"
```

---

## 6. Entity, repository, service

**Entity** — `Notification` (`entity/Notification.java`) maps the table 1:1.
The `type` column is a Java enum `NotificationType` stored as a string
(`@Enumerated(EnumType.STRING)`), the same approach as `AppointmentStatus`.
`created_at` is `insertable = false, updatable = false` because MySQL owns it.

**Repository** — `NotificationRepository` has only user-scoped methods:

- `findByUserIdOrderByCreatedAtDesc(userId)` — the inbox list
- `countByUserIdAndReadFalse(userId)` — the badge number
- `findByIdAndUserId(id, userId)` — one notification **and** its owner; ownership is
  part of the query, so someone else's notification looks exactly like a missing one
- `markAllReadForUser(userId)` — one bulk `UPDATE` instead of loading and saving rows

There is deliberately no "find any notification by id" and no delete method.

**Service** — `NotificationService` has two kinds of methods:

- `createAppointmentNotification(...)`, `notifyPatient(...)`, `notifyDoctor(...)` —
  called by the appointment workflows *inside their transaction* (see §7).
- `getCurrentUserNotifications()`, `getCurrentUserUnreadCount()`, `markAsRead(id)`,
  `markAllAsRead()` — the caller's own operations. The caller's identity always
  comes from the JWT (`CurrentUserProvider.requireCurrentUserId()`); **no user ID is
  ever accepted from a request**, so there is no route that could address another
  user's inbox.

Accessing a notification ID that doesn't exist **or** belongs to someone else
returns **404** — the two cases are indistinguishable by design (same convention as
appointment IDs). That is why the ownership filter lives inside the lookup query.

---

## 7. Appointment integration and the transaction decision

Notification creation is wired into `AppointmentService` and
`AdminAppointmentService` at exactly one point per workflow — right after the
appointment status change and its history row are written:

| Workflow call site | Notification |
|---|---|
| `createAppointment(...)` after the first history row | → patient (`APPOINTMENT_CREATED`) |
| `cancelAppointment(...)` shared flow (patient/doctor) | → the other participant (`APPOINTMENT_CANCELLED`) |
| `transitionAppointmentForCurrentUser(...)` (confirm/complete/no-show) | → patient (matching type) |
| `AdminAppointmentService.cancelAppointmentAsAdmin(...)` | → patient **and** doctor |

**Decision: notifications participate in the same database transaction as the
appointment change.** The creation methods are annotated
`@Transactional(propagation = Propagation.MANDATORY)`, which means "join the
caller's transaction or fail loudly". Consequences, made explicit:

- If the appointment change commits, its notification **must** exist. A confirmed
  appointment can never be missing its "confirmed" notification.
- If anything fails and the transaction rolls back, the appointment change **and**
  the notification disappear together. A failed request can never leave a
  notification about something that did not happen, and a half-written booking can
  never exist with a notification already attached.

We chose this synchronous, same-transaction behaviour over queues/async events
because this project values reliability and predictability over infrastructure. The
trade-off is also explicit: if the notification INSERT fails, the whole appointment
operation fails too (fail-loud rather than silently losing notifications). The
INSERT is a single local-row write with no external calls, so in practice it cannot
fail unless the database itself is failing — in which case the appointment change
could not commit anyway.

**No duplicates:** each workflow creates its notification exactly once, at the
single code site that performs the transition. Invalid transitions (409) throw
before any notification is written, and rollback removes any half-written INSERT —
so retries and failed requests cannot accumulate duplicates. (Verified by tests:
`failedBookingLeavesNoNotificationResidue`, `invalidTransitionFails409AndCreatesNoNotification`.)

---

## 8. REST API

All four endpoints require authentication (anonymous → 401). They live under
`/api/v1/notifications/**`, which is covered by the security config's
`anyRequest().authenticated()` — no special role rule needed.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/notifications` | The caller's notifications, newest first |
| GET | `/api/v1/notifications/unread-count` | The caller's unread count (a number) |
| PATCH | `/api/v1/notifications/{id}/read` | The notification, now marked read (404 if unknown/not owned) |
| POST | `/api/v1/notifications/read-all` | The caller's unread count after marking (0) |

Example response for the list endpoint (`NotificationResponse`):

```json
{
  "id": 148,
  "type": "APPOINTMENT_CONFIRMED",
  "title": "Appointment confirmed",
  "message": "Dr. Sarah Wells confirmed your appointment on 2026-09-28 at 10:00.",
  "referenceId": 3689,
  "read": false,
  "createdAt": "2026-09-26T20:05:11.234"
}
```

The JPA entity is never serialized directly — the DTO contains only the fields
above and never the recipient's account data.

---

## 9. React Query layer

`src/api/notificationApi.ts` wraps the four endpoints; `src/hooks/useNotifications.ts`
exposes the hooks:

| Hook | Purpose |
|---|---|
| `useNotifications(open)` | The list; fetched only while the bell panel is open (`staleTime: 0`, so opening always shows fresh data) |
| `useUnreadNotificationCount()` | The badge; **polls every 30 seconds** while the tab is visible |
| `useMarkNotificationRead()` | Marks one read; updates the list cache in place and invalidates the count |
| `useMarkAllNotificationsRead()` | Marks everything read; updates both caches immediately |

Query keys: `['notifications', 'list']` and `['notifications', 'unread-count']`.
Mutations write to the cache directly (`setQueryData`) instead of refetching, so the
UI reacts instantly.

**Polling, documented:** 30 seconds, only while the tab is visible
(`refetchIntervalInBackground` is false by default). Notifications are not
time-critical in a clinic app, and this keeps request volume negligible. No
WebSockets are used (explicitly out of scope).

---

## 10. The notification UI

The bell lives in the AppShell header (`src/components/layout/NotificationBell.tsx`),
next to the user pill, so it is visible on every authenticated page for every role.

- **Badge** — a small red count bubble on the bell; hidden entirely when there are
  no unread notifications (an empty bell already means "all read").
- **Panel** — clicking the bell opens a dropdown: a "Mark all read" action (only
  when something is unread), then the list. Unread items have a tinted background,
  bold title and a dot; read items are plain — distinguishable by more than color
  alone.
- **Type icons** — every notification shows an icon chip plus a text label
  (Booked / Confirmed / Cancelled / Completed / No-show) using the design system's
  existing status palette (info/brand/danger/ink/warning). No new colors were
  introduced.
- **Empty state, loading, error** — a friendly "No notifications yet" panel,
  skeletons while loading, and an ErrorBox with Retry if the request fails.
- **Click behaviour** — clicking an item marks it read (if unread) and, if it has a
  `referenceId`, navigates to `/appointments/{referenceId}`. Without a reference it
  just marks read and closes.
- **Dismissal** — the panel closes on outside click and on Escape.
- **Mobile** — below Tailwind's `sm` breakpoint the panel becomes a near-full-width
  sheet pinned under the header (`max-sm:fixed inset-x-3 top-16`), verified at a
  375px viewport to fit with margins and never overflow the screen edge.

---

## 11. Read/unread summary

- New notification → `is_read = FALSE`, badge increments (within one 30s poll, or
  immediately after your own actions).
- Clicking one notification → that row is marked read, badge decreases.
- "Mark all read" → all rows flip, badge disappears.
- All read → badge hidden; bell icon looks identical to the never-notified state.

---

## 12. Future: email / SMS / push

The foundation is ready, but nothing more is implemented (per the scope boundary):

- **Email/SMS** — add a `NotificationChannel`-style dispatcher that, after the
  transaction commits (e.g. `@TransactionalEventListener(phase = AFTER_COMMIT)`),
  forwards the same `Notification` row to an email provider or SMS gateway. The
  domain model, ownership rules, and read-state handling would not change.
- **Push** — the `unread-count` endpoint is the natural hook for a "someone posted
  to you" badge on a mobile client.
- **Preferences** — the `notifications` table could gain a `channel` column and the
  user a preferences block; the creation site would stay the single integration
  point.

---

## 13. Testing

Backend (`backend/src/test/java/com/medbook/controller/NotificationsTest.java`,
16 tests over real MySQL with real JWTs, all rolled back after each test):
creation and ownership, per-user retrieval and ordering, cross-user 404, unread
count isolation, mark-one and mark-all, anonymous 401 on all four endpoints, one
test per lifecycle event (booking/confirm/complete/no-show via the real HTTP
endpoints, patient-cancel and admin-cancel), same-transaction residue checks, and
duplicate prevention.

Frontend (`src/components/layout/NotificationBell.test.tsx`, 9 tests): bell renders,
badge count, panel opens, empty state, list rendering with type labels and
timestamps, mark read + navigation to the appointment, mark all read clearing the
badge, click without reference, and outside-click dismissal. Loading and error
states are covered by the mocked API in the suite.

End-to-end (real browser, real MySQL, real JWTs): patient logs in → books → sees
the badge appear via polling within ~30s → opens the panel → clicks the notification
→ lands on the appointment detail page; doctor confirms from their side → patient's
badge updates; "Mark all read" clears the badge; a second patient's notifications
are invisible to the first (404 on cross-user PATCH). Temporary E2E rows are
deleted afterwards.
