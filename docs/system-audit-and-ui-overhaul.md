# MedBook — Complete System Audit & Frontend UI Overhaul (Step 26)

Date: 2026-09-27 · Scope: full-stack audit (Phase 1) + frontend completion and modern
UI redesign (Phase 2). The repository is the source of truth; prior reports were
treated as hints only and re-verified against the code.

---

## 1. Roles discovered

Exactly three roles exist in the backend (`Role` table seeded by Flyway V1):
`ADMIN`, `DOCTOR`, `PATIENT`. JWT claims carry `roles: ["ROLE_<X>"]`. No other
roles exist in code or migrations; none were invented.

| Role | Login | Dashboard | Allowed routes (frontend guard) | Authoritative backend boundary |
|---|---|---|---|---|
| PATIENT | shared `/login` | `/dashboard` (patient) | `/dashboard`, `/appointments/new`, `/appointments/me`, `/appointments/:id`, `/profile` | `/api/v1/appointments/**` requires any role; booking rejects non-patients |
| DOCTOR | shared `/login` | `/dashboard` → `DoctorDashboardPage` | doctor dashboard, `/doctor/appointments`, `/appointments/:id`, `/profile` | transition endpoints verify DOCTOR role + assignment from JWT |
| ADMIN | shared `/login` | `/admin` | `/admin`, `/admin/appointments(/:id)`, `/admin/specialties`, `/admin/doctors`, `/admin/availability`, `/profile` (+ patient routes if also PATIENT) | `/api/v1/admin/**` → `hasRole(ADMIN)` |

One shared login system with role-aware routing; no separate login flows exist
or are needed. `admin@medbook.test` holds ADMIN+PATIENT and correctly sees both
the Care and Administration nav sections.

## 2. Authentication architecture

- Registration (`/auth/register`, patient self-signup) and shared login
  (`/auth/login`) return a JWT; BCrypt password hashing; HS256 with issuer
  `medbook`, subject = user id; expiration enforced server-side.
- Token stored in `localStorage` via `AuthContext`
  (`login/register/logout/updateUser`); axios client attaches it as a bearer
  token; a 401 interceptor clears auth and redirects to `/login`.
- Route protection: `ProtectedRoute`, `AdminRoute`, `DoctorRoute` are UX
  guards only; the backend remains authoritative (verified live with a forged
  token — server rejected it, session cleared, redirect to `/login`).
- Password change exists in `MeController` (`/me/change-password`) with
  current-password verification (documented limitation: no token revocation).
- Profile ownership: profile endpoints derive identity from the JWT, never
  from client-supplied IDs.

## 3. Role-based dashboards

| Area | Status |
|---|---|
| Patient dashboard | IMPLEMENTED (welcome, quick actions, next appointment — real data) |
| Patient booking | IMPLEMENTED (specialty → doctor → date/time wizard, JWT identity only) |
| Patient appointments/cancel | IMPLEMENTED (list with status filter, detail, cancel w/ reason) |
| Doctor dashboard & lifecycle | IMPLEMENTED (today/upcoming from `/appointments/me`; confirm/complete/no-show) |
| Admin dashboard | IMPLEMENTED (real overview metrics + one Recharts status chart) |
| Admin specialties/doctors/availability | IMPLEMENTED (CRUD + deactivate, MUI tables/dialogs) |
| Admin patient support booking | IMPLEMENTED (`POST /appointments/admin` via Book-for-a-Patient modal) |
| Admin patient list | IMPLEMENTED (`GET /admin/patients`) |
| User management (beyond doctors) | NOT SUPPORTED by backend — documented, not fabricated |
| Reports/statistics beyond overview | NOT SUPPORTED by backend — intentionally absent |

## 4. Backend feature inventory

40 endpoints across 8 controllers:
- `AuthController`: register, login.
- `AppointmentController`: patient self-book, admin support-book, role-aware
  `/appointments/me`, get by id, cancel, confirm, complete, no-show.
- `AdminController`: specialties CRUD + deactivate; doctors CRUD + deactivate;
  availability CRUD + deactivate; patients list; appointments list with
  date/status/doctor filters; overview; detail; cancel.
- `DoctorController`: doctor discovery, doctor availability.
- `SpecialtyController`: specialty discovery.
- `MeController`: profile, account update, patient/doctor profile update,
  change password.
- `NotificationController`: list, unread count, mark read, mark all read.
- `HelloController`: smoke endpoint.

Security: `permitAll` for `/auth/**`, `/hello`, `/actuator/health,info`;
`hasAnyRole(ADMIN,DOCTOR,PATIENT)` for `/appointments/**`; `hasRole(ADMIN)`
for `/admin/**`; anything else `authenticated`.

## 5. Backend → frontend coverage matrix

Every meaningful endpoint has: an API service function (`src/api/*`), a React
Query hook (`src/hooks/*`), a consuming page/component, and loading/empty/error
states. Spot-verified in the browser for all three roles (Section 12). The one
gap found during the Phase 1 re-verification (admin patient list had an API
consumer only inside the booking modal) was closed in the previous session;
nothing further was found. Notifications, profile, and admin management flows
are all wired end-to-end and tested (95 frontend tests).

## 6. Missing frontend features implemented

None in this session — the audit found the coverage complete (the admin
patients endpoint and Book-for-a-Patient modal were completed in the prior
session and were re-verified here).

## 7. UI library strategy

The spec's component-library rule is now enforced in code:
- MUI (`@mui/material` v9) is the primary library for all standard primitives:
  Button, TextField, Select, Dialog, Drawer, Table, Alert, Chip, Skeleton,
  CircularProgress, Badge, IconButton, Tooltip, MenuItem, FormControl.
- Tailwind v4 is restricted to layout, spacing, responsive composition, and
  page-level styling.
- MedBook-specific compositions (AppShell sidebar, notification panel,
  appointment cards, booking wizard, dashboard cards, homepage hero) remain
  custom, as the spec permits — they are feature compositions, not primitives.
- Deleted custom primitives: `Button.tsx` internals (now MUI adapter),
  `Input.tsx` (MUI TextField), `Select.tsx` (MUI Select), `Modal.tsx` (MUI
  Dialog), `Feedback.tsx` internals (MUI Alert/Chip/Skeleton/CircularProgress),
  `Toast.tsx` (deleted entirely).

## 8. MUI integration

- Single centralized theme in `frontend/src/theme.ts` (`createTheme`,
  `cssVariables: true`) mounted once in `App.tsx` with `ThemeProvider` +
  `CssBaseline`. Palette mirrors the MedBook tokens from `index.css`
  (brand-600 primary #167b71, ink text/backgrounds, semantic
  success/warning/error/info, ink-50 background, white paper), typography
  uses Inter + Source Serif 4 for headings, radius 10/14, soft MedBook
  shadows, `textTransform: none` buttons.
- Layer ordering: `@layer theme, base, components, utilities;` added to
  `index.css` so Tailwind's preflight cannot reset Emotion styles.
- v9 specifics handled: `slotProps.htmlInput` (replaces `inputProps`),
  `aria-disabled` on disabled selects, `labelId` association for Select,
  MenuItem-based listboxes (bare `<option>` children lack the required
  tabindex), `GridLegacy` avoided entirely.

## 9. Tailwind usage

Layout/spacing/responsive only (`grid`, `max-w-*`, `flex`, `sm:`/`md:`/`lg:`,
spacing, text colors from the same token palette). No competing visual system:
MUI overrides and Tailwind utilities reference the same design tokens.

## 10. Toast solution

Sonner (`sonner@^2.0.8`). The custom `ToastProvider` was deleted; a tiny
facade (`src/lib/toast.ts`) keeps the `toast.success/error/info` call-site
API, rendered by Sonner's `<Toaster>` mounted once in `App.tsx` (themed white
cards, bottom-right). Used for booking, cancellation, status transitions,
profile/password updates; errors that require correction stay inline in
forms/modals. jsdom lacks `Element.animate`, so the Vitest setup stubs it for
Sonner's transitions.

## 11. Animation solution

- CSS transitions/keyframes: hover states, `mb-rise` for dialogs/toasts,
  `mb-fade` for page content, MUI Skeleton pulse — all disabled under
  `prefers-reduced-motion`.
- GSAP was removed after this audit: the landing hero was its only use, and
  `fromTo(..., {opacity: 0})` left the headline invisible until
  `requestAnimationFrame` ran (background tabs, headless renders). The hero is
  now plain visible markup; the shell's CSS fade is the only page motion. This
  also removed a runtime dependency and ~70 kB from the app chunk.
- Barba.js: deliberately NOT integrated. It expects to own page transitions
  by intercepting navigations between server-rendered pages, which conflicts
  with React Router's client-side rendering; forcing it in would risk double
  renders and fight the SPA architecture for no user benefit.

## 12. Responsive strategy

Single `AppShell` for all roles: fixed sidebar ≥1024px, MUI Drawer below
(trigger button shows only on small screens), sticky topbar with bell +
identity + sign-out, content constrained to readable measure. Verified at
390×844 (mobile): no horizontal overflow on login, dashboard, booking,
appointments, or home; MUI Drawer opens/closes (Escape verified); admin table
is desktop-only with a card list on mobile. Dialogs are width-constrained
(`maxWidth="sm" fullWidth`).

## 13. Accessibility

- MUI primitives bring correct roles/keyboard behavior (dialog focus trap,
  drawer Escape, listbox/option semantics, focus-visible rings).
- Labels are programmatically associated: TextField label/id, Select via
  `labelId`/`aria-labelledby`, plus `aria-label` routing for filter inputs
  through `slotProps.htmlInput`.
- Status is conveyed with text + color (MUI Chips with labels; chart bars are
  axis-labelled, never color-only).
- Errors render as `role="alert"` MUI Alerts; toasts announce via Sonner's
  live region; icons are `aria-hidden` with text alternatives nearby.

## 14. Dashboard analytics

One chart: "Appointments by status" bar chart on the Admin Dashboard, fed by
the real `GET /admin/appointments/overview` `statusCounts` (Recharts,
`isAnimationActive={false}`, per-status colors matching the badge palette,
`Cell`-per-status pattern). No invented metrics; no other charts added.

## 15. Testing

- Frontend: **105/105 passing** across 10 files (100 after the audit, plus 5
  regression tests for the primitives: single-anchor `ButtonLink`, keyboard
  `LinkCard`, `Input` ref routing, currency formatting and tablist arrow keys).
  Tests updated for the MUI DOM:
  listbox interaction helper (`src/test/mui.ts` with `waitForEnabled` +
  portal-aware option lookup), humanized chip labels, MUI Skeleton class,
  Badge visibility contract, `fireEvent.change` for controlled date inputs,
  Sonner `<Toaster>` in wrappers where toasts are asserted.
- Backend: `mvn clean test` → **293/293 passing** (untouched by this step).
- Build: `npm run build` clean (tsc + vite) with vendor chunk splitting
  (`vendor-react`, `vendor-mui`, `vendor-charts`).
- Typecheck: `tsc -b` clean.

## 16. Known limitations

- ~~`AppointmentResponse` carries no participant names~~ — resolved in the
  current working tree: the DTO now exposes `patientName` / `doctorName`
  ("First Last", never addresses or medical data), and patient/doctor screens
  display those names.
- Notification badge relies on 30s polling (no WebSockets in scope).
- Notifications are in-app only (no email/SMS provider in scope).
- `reference_id` is an ID, not a FK (intentional, documented in Step 25).
- Password change does not revoke existing JWTs.
- Admin "user management" (editing arbitrary users) is not supported by the
  backend and was not fabricated.
- Final report items delivered in chat per the Step 26 spec; this document
  covers the 16 required documentation sections.

---

## 17. Post-audit corrections (current working tree)

A second pass over every page fixed defects that the first pass left behind:

| Area | Defect | Fix |
|---|---|---|
| Navigation | 13 places rendered `<Button>` inside `<Link>` (invalid nested interactive markup; two controls per CTA) | `ButtonLink` renders one MUI-styled anchor; all call sites converted |
| Cards | Dashboard and appointment cards were `<div onClick>`: unreachable by keyboard, not announced as links | `LinkCard` (real router link, focus ring, middle-click works) |
| Forms | `Input` sent react-hook-form's `ref` to the MUI wrapper, not the `<input>`; password toggles were absolutely-positioned over the text | `Input` forwards `inputRef` and supports `endAdornment`; Login/Register/Profile use MUI adornments |
| Money | Rupee icons (`IndianRupee`) beside `$` amounts; mixed `$300` / `$300.00` formatting | `formatCurrency` (shared helper, DollarSign icon) everywhere |
| Dates | Admin tables and availability showed raw ISO strings; "today" used `toISOString()` (UTC) so the doctor dashboard could show the wrong day | `formatDate` everywhere; `todayIso()` (local date) for today/upcoming/min-date logic |
| Admin UI | Ad-hoc `blue/gray/amber/purple/green` utilities, `rounded-lg`, off-system shadows, icon colours varying per page | Design tokens only; shared `PageHeader` icon tile; accessible link cards |
| Admin tables | Wide tables overflowed the page on small screens | Hidden secondary columns below `sm`/`md` + scroll-contained tables (appointments already had cards) |
| Accessibility | No skip link; tablist had no arrow-key behaviour; notification dialog did not receive or return focus | Skip link, roving tabindex + arrow keys, focus move/restore |
| Motion | GSAP hero left the headline at `opacity: 0` until rAF ran | Removed GSAP and the effect; content is visible by default |
| Semantics | Dashboard's next visit read "Dr. visit #ID"; homepage had a hidden duplicate brand link and a fake "Your next visit" | Shows the real doctor name; duplicate/fake content removed and the marketing card is labelled "Example appointment" |
