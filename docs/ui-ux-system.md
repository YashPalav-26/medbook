# MedBook UI/UX System (Step 20)

This document describes the design system that every MedBook page is built on —
the tokens, the shared components, the states, and the rules that keep the
patient area, the admin area and the public pages feeling like **one product**.

---

## 1. Visual direction

MedBook is a clinic scheduling product, so the interface is designed to feel
**calm, trustworthy and efficient** — like a well-run modern clinic, not a
flashy marketing site and not an enterprise ERP:

- a **teal brand** as the single accent of identity (clinical, calm);
- generous **whitespace** and a limited palette so real content stands out;
- a **serif display face for headlines** (warmth, editorial quality) over a
  clean **sans-serif for UI text** (clarity, efficiency);
- **soft, small shadows** — surfaces lift gently on hover, modals lift more;
- **subtle motion only**: card hover elevation, modal rise, fade of page
  content. Nothing bounces, spins for long, or distracts.

Marketing copy on the homepage describes only what MedBook actually does:
book appointments, discover doctors and specialties, see availability, sign in
securely with JWT. No fabricated certifications or user counts.

---

## 2. Design tokens (single source of truth)

All tokens live in `frontend/src/index.css` under Tailwind CSS v4's `@theme`,
so every `bg-brand-600`-style class resolves to one definition. Pages never
hardcode hex colors.

### Color

| Token family | Purpose |
|---|---|
| `brand` (50–900) | Primary teal: buttons, links, active nav, focus rings, fee highlights |
| `ink` (50–950) | Neutrals: page background (`ink-50`), surfaces (white), text (`ink-900`), muted text (`ink-500`) |
| `success` / `warning` / `danger` / `info` | Status colors (active, in-force warnings, destructive, scheduled) |

### Typography

- **Display (headlines):** a serif display face via the `font-display` utility
  (self-hosted `@font-face`, no external requests).
- **UI text:** sans-serif for body, labels, buttons, tables.
- Scale: page titles `text-2xl/3xl` (`font-display`), section titles
  `text-lg` medium, body `text-sm/base`, helper text `text-sm text-ink-500`,
  badges `text-xs font-medium`.

### Radius, shadow, motion

- `rounded-card` — cards, modals (larger radius)
- `rounded-field` — inputs, buttons, small callouts
- `rounded-full` — pills, badges, chips
- `shadow-card` — default surface elevation; `hover:shadow-card` + hover lift
  on interactive cards
- `shadow-modal` — dialog elevation
- `animate-fade` (page content), `animate-rise` (modals, drawer) — both
  CSS-only, no animation dependency

---

## 3. Reusable component kit (`src/components/ui/`)

Every page composes these; no page invents its own field or card styles.

| Component | Role |
|---|---|
| `Brand` | Wordmark with a cross-in-roundel icon; links to the app home |
| `Button` | `primary` / `secondary` / `outline` / `ghost` / `danger` variants, `sm/md/lg` sizes, disabled + spinner states |
| `Card` | Surface container with optional `hover` (clickable cards get pointer + elevation) |
| `Input` / `Select` | Label, helper text, error message, focus ring, disabled state — always label-associated (a11y) |
| `StatusBadge` | Status pill with color **and** word (`SCHEDULED`, `CANCELLED`, `Active`, `Closed`…) — never color alone |
| `EmptyState` | Icon + title + explanation + optional action; the universal "nothing here yet" pattern |
| `ErrorBox` | `role="alert"` callout with optional **Try again** retry |
| `Skeleton` | aria-hidden pulse lines for loading placeholders |
| `Spinner` | Small inline `Loader2` for buttons and inline waits |
| `Modal` / `ConfirmDialog` | Focus-trapped dialog (Escape + backdrop close), title/description/body, cancel + confirm actions, busy state |
| `ToastProvider` / `useToast` | Lightweight custom toast system (no dependency): success/error feedback for booking, cancellation, admin mutations |

---

## 4. Application shell (`AppShell`)

All authenticated pages render inside `AppShell`:

- **Desktop (≥ lg):** fixed 256px sidebar — brand, role-aware navigation
  (PATIENT: Dashboard, Book Appointment, My Appointments; ADMIN: Admin
  Overview, Specialties, Doctors, Availability), signed-in identity block.
- **Header:** page title, name/role pill, **Sign Out**.
- **Tablet/mobile:** sidebar collapses behind a hamburger; a slide-in drawer
  (same nav, Escape/backdrop dismiss) keeps navigation touch-friendly.
- Navigation is all React Router `<NavLink>` — **no full page reloads**.
- Role-awareness is UX only. The backend (Spring Security + JWT) remains the
  real authorization boundary; hiding a link is not security.

Unauthenticated pages (Homepage, Login, Register) use a standalone centered
layout with the same tokens, so they read as the same product.

---

## 5. Page patterns

### Public homepage (`/`)
Hero (value proposition + primary/secondary CTAs), a value grid (booking,
doctor discovery, availability, secure access), a 3-step "How it works",
a factual trust section, closing CTA and footer. No stock photos — layout,
color and typography carry the design.

### Auth (`/login`, `/register`)
Centered card on the token background. Password visibility toggles, inline
validation, backend error display, loading spinner in the submit button, and
cross-links between the two pages. Registration groups personal vs. credential
fields and shows password help text. No unsupported fields, no role picker.

### Patient dashboard (`/dashboard`)
Personalized welcome, primary **Book Appointment** action, an **Upcoming
appointment** card built from the real `/appointments/me` data (never fake
stats or charts), a My Appointments shortcut, and a small profile summary.
Empty and error states use the shared components with a retry.

### Booking (`/appointments/new`)
A guided flow on one page: a **3-step wizard indicator** (Doctor → Date & time
→ Confirm), specialty select → doctor select → doctor card (name, specialty,
experience, fee, bio) → date → availability chips (day + window, from the real
API) or a clear "no availability that day" message → start/end time →
confirmation summary with the fee → submit. **No patient ID field exists** —
identity is derived from the JWT. Backend errors (409 conflict, 403, 401)
surface through the shared `ErrorBox` wording.

### Appointment detail (`/appointments/:id`)
Structured summary: header + status badge, patient/doctor info tiles, date &
time tiles, fee highlight, cancellation reason callout when present, and a
destructive **Cancel Appointment** action that opens a **confirmation modal**
requiring a reason and warning about the consequence. Success shows a toast.

### My Appointments (`/appointments/me`)
Page header + primary action, status filter tabs with **real counts** from the
fetched data, responsive appointment **cards** (no wide table — mobile gets a
single column), each with status badge, date/time/fee, doctor/patient line and
the cancellation reason when present. Click-through to detail preserved.

### Admin area (`/admin/**`)
Same tokens and components as the patient area, slightly more information-
dense: dashboard overview cards; specialties/doctors/availability pages built
on the shared table/list + modal + badge patterns. Availability uses friendly
day names (mapped to the backend's numeric values) and Open/Closed badges.
A shared `AdminNav` links the three areas.

---

## 6. Loading, empty, error, success — the four states

Every data-driven page implements all four with the shared kit:

1. **Loading** — `Skeleton` layouts shaped like the content (never a bare
   "Loading…" text).
2. **Empty** — `EmptyState` with an explanation *and* a relevant action
   ("Book your first appointment", "Show all").
3. **Error** — `ErrorBox` (`role="alert"`) with human wording and a **Try
   again** retry. Backend messages are normalized by `toApiError`; raw stack
   traces or internals are never shown.
4. **Success** — toast for mutations (booked, cancelled, specialty created…),
   inline for everything else. Toasts are used sparingly.

---

## 7. Responsive behavior

- Mobile-first: single column everywhere; grids collapse (`sm:grid-cols-2`,
  `lg:grid-cols-3`).
- Sidebar → drawer under `lg`; header stays compact.
- Tap targets stay ≥ ~40px; modals are scrollable and never overflow
  (`max-h-[90vh] overflow-y-auto`).
- No horizontal overflow: lists/cards instead of wide tables on mobile.

---

## 8. Accessibility

- Semantic HTML (`nav`, `main`, `section` + `aria-labelledby`, ordered wizard
  steps with `aria-current="step"`).
- Every input/select is label-associated; icon-only buttons carry
  `aria-label`.
- `role="alert"` for errors; `role="tablist"` (with `aria-selected`) for the
  status filters.
- Status is conveyed by **word + color** (`StatusBadge`), never color alone.
- Visible focus rings on all interactive elements; Escape closes modals and
  the drawer; modal panels are `aria-modal` and labelled.
- Navigation is keyboard-operable throughout (real buttons and links).

---

## 9. Consistency rules (the "one product" contract)

1. Never hardcode colors/spacing — use tokens and Tailwind spacing.
2. Never build a one-off field, card, badge or modal — compose the kit.
3. Every async surface shows the four states (§6).
4. Action buttons: primary (brand) for the main action, outline for
   secondary, danger for destructive, ghost for tertiary.
5. Role-aware UI is convenience; the server is the authority.
6. Real data only — no fabricated metrics, no mock cards, no invented
   statuses.
