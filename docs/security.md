# Authentication + JWT Security (Step 14)

A beginner-friendly walkthrough of the first security implementation in MedBook: password
hashing, registration/login, JWT Bearer tokens, a stateless filter chain, and role rules.

---

## The Big Picture

```
POST /api/v1/auth/register  (public) ──> AuthService ──> User + patient profile (DB)
POST /api/v1/auth/login     (public) ──> AuthService ──> JWT token
                                                │
                                    password encoder (BCrypt)
                                    secret from JWT_SECRET (never committed)

GET/POST /api/v1/appointments/**  (protected)
    ↓
Security filter chain: token present? → signature/exp ok? → has a business role?
    ↓ yes                    ↓ no → 401 ErrorResponse JSON
AppointmentController     role-less caller → 403 ErrorResponse JSON
```

New code in this step:

```
com.medbook.security
├── SecurityConfig                  ← filter chain, key, encoder/decoder, password encoder
├── JwtProperties                   ← secret + lifetime, injected from env/config only
├── JwtService                      ← issue signed tokens (sub, email, roles, iat, exp)
├── MedbookUserDetails              ← Spring's view of our User (email, hash, flags, roles)
├── MedbookUserDetailsService       ← load that view by email
├── CurrentUserProvider             ← identity of the current request for future steps
├── MedbookAuthenticationEntryPoint ← 401 JSON body writer
└── MedbookAccessDeniedHandler      ← 403 JSON body writer

com.medbook.service
├── AuthService                     ← register/login business logic
├── InvalidCredentialsException     ← 401, same message for every wrong answer
├── AccountLockedException          ← 401, only after the password matched
├── AccountInactiveException        ← 401, only after the password matched
└── EmailAlreadyRegisteredException ← 409

com.medbook.controller
└── AuthController                  ← POST /api/v1/auth/register and /login

com.medbook.dto
├── RegisterRequest / LoginRequest / AuthResponse
```

---

## Authentication vs Authorization

- **Authentication** answers *"who are you?"*. Here: email + BCrypt password at login, after
  which the client receives a signed JWT. On every later request, the token itself is the
  proof of identity (validated by signature, no password needed again).
- **Authorization** answers *"are you allowed to do this?"*. Here: the filter chain checks
  that the token is valid **and** that its roles include one of the business roles.

A JWT is just the *evidence* of authentication; the filter chain enforces both answers on
every request.

---

## Password Hashing (BCrypt)

Passwords are **never** stored, compared, or logged in plaintext. The `PasswordEncoder` bean
is a `BCryptPasswordEncoder`:

```java
// registration
String passwordHash = passwordEncoder.encode(request.password());  // → $2a$10$...
userRepository.save(new User(email, passwordHash, ...));

// login
passwordEncoder.matches(request.password(), user.getPasswordHash());
```

Why BCrypt: it is slow on purpose (defeats brute-force guessing) and salts every hash, so
two users with the same password get completely different hash strings. Two deliberate
test-only plaintext strings exist in tests — they are low-entropy fixtures (e.g.
`"SecretPass1"`), never committed credentials, and only their hashes exist in readable test
code. The one real secret, `JWT_SECRET`, never appears in committed code.

---

## UserDetailsService and the Role Mapping

Spring Security asks for a "user" by a username string. MedBook's sign-in identity is the
email address, so `MedbookUserDetailsService` adapts between the two and wraps the found
row in `MedbookUserDetails`, which translates schema columns into Spring Security's
concepts:

| Schema column | Spring Security meaning |
|---|---|
| `email` | the username |
| `password_hash` | the stored BCrypt hash |
| `roles` (`ADMIN`, `DOCTOR`, `PATIENT`) | authorities `ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_PATIENT` |
| `is_active` | enabled |
| `is_locked` | not locked (inverted) |

The `ROLE_` prefix choice is the standard Spring Security convention: `hasRole("ADMIN")`
and `hasAnyRole(...)` expect it, and putting the full authority in the JWT's `roles` claim
means the same mapping works in tokens and in a direct `UserDetails` lookup.

---

## Registration and Login

```
POST /api/v1/auth/register → 201 (patient account + signed-in token)
POST /api/v1/auth/login    → 200 (token) · 401 (any wrong answer)
```

**Registration** validates the request (email format, password 8–72 chars, names ≤ 100,
phone pattern mirroring the column CHECK, birth date in the past), rejects a taken email
with 409, hashes the password, then creates the user **plus the patient profile** in one
transaction — so a new account can book immediately. The request has nowhere to ask for a
role: Jackson ignores an injected `"role": "ADMIN"` field, and the service hardcodes
`PATIENT`. Emails are stored lower-cased.

**Login** normalizes the email, then follows a careful order:

1. unknown email → run BCrypt against a dummy hash anyway (so the response time does not
   reveal whether the account exists) → generic 401 `"Invalid email or password"`,
2. wrong password → `failed_login_attempts + 1` → the same generic 401,
3. correct password but locked → 401 with a lock message,
4. correct password but inactive → 401 with a deactivated message,
5. correct password and good standing → reset the failure counter, stamp `last_login_at`
   (UTC, per the schema note), issue the token.

Only someone who already knows the password ever sees the locked/inactive messages. A DSL
wrapping these steps as "the three rights" is a good memory hook: **right password →
right standing → right roles**.

---

## JWT: Claims, Lifetime, Bearer

A signed token has three parts (`header.payload.signature`) and is only base64-encoded —
readable by anyone, verifiable by the server.

```json
{
  "iss": "medbook",
  "sub": "123",
  "email": "patient@example.com",
  "roles": ["ROLE_PATIENT"],
  "iat": 1726800000,
  "exp": 1726803600
}
```

- `sub` is the numeric user ID (stable primary key, never an editable field),
- `roles` carries the Spring Security authorities so the filter chain never needs a
  database lookup,
- `iat`/`exp` bound the token's lifetime; the default is 60 minutes, configurable via
  `JWT_EXPIRATION_MINUTES`.

Clients send it as `Authorization: Bearer <token>` on every protected call. The filter
chain verifies the HMAC-SHA256 signature, rejects expired tokens, converts `roles` into
authorities, and places a `JwtAuthenticationToken` in the security context. The plaintext
password is never in a claim (nor anywhere else after hashing).

---

## The Signing Secret (Never Committed)

The HMAC key is derived (SHA-256) from configured secret material or generated randomly:

```java
@Value("${medbook.security.jwt.secret:${JWT_SECRET:}}") String secret
@Value("${medbook.security.jwt.expiration-minutes:${JWT_EXPIRATION_MINUTES:60}}") long expirationMinutes
```

- `medbook.security.jwt.secret` (any Spring property source) →
- `JWT_SECRET` (environment variable) →
- ephemeral random key + loud warning log ("tokens become invalid on restart").

`application.properties` contains no JWT settings, and no secret appears in tests either
(the test context happily uses the ephemeral key; only throwaway fixture passwords exist
there). A configured secret shorter than 32 characters fails fast at startup.

Why care: anyone holding the secret can mint a valid ADMIN token. Keep `JWT_SECRET` in the
deployment environment (or a secret manager), never in the repository, never in logs, and
never echoed in an error message.

---

## Stateless REST Security and the Filter Chain

Each request is authenticated from scratch; the server keeps **no session**, so horizontal
scaling never needs sticky sessions:

| Decision | Configuration | Why |
|---|---|---|
| Public | `/api/v1/auth/**`, `/api/v1/hello`, `/actuator/health`, `/actuator/info` | registrations, logins, smoke test, probes |
| Protected | `/api/v1/appointments/**` | valid token + a business role (`ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_PATIENT`) |
| Everything else | `anyRequest().authenticated()` | a safe default for future endpoints |
| 401 body | `MedbookAuthenticationEntryPoint` | JSON `ErrorResponse`: no token vs invalid/expired token |
| 403 body | `MedbookAccessDeniedHandler` | JSON `ErrorResponse` for authenticated-but-roleless callers |
| Sessions | `STATELESS` | nothing stored server-side |
| CSRF | disabled | CSRF needs cookie auth to attack; this API only reads Bearer headers browsers never send by themselves |
| formLogin / httpBasic / logout | disabled | token API, not a browser session app |

Unauthenticated requests to the appointment API get 401 **with the same `ErrorResponse`
shape** as every other client error (`timestamp`, `status`, `error`, `message`, `path`,
empty `fieldErrors`). A validly signed token without any business role gets 403 — the
distinction from 401 ("we know who you are, but not allowed") makes the boundary explicit.

---

## Current Role Boundaries (Deliberately Small)

- The **only** dedicated role rule today is the appointment fence above. Any of the three
  business roles may call the appointment endpoints; the task forbids inventing ownership
  rules ("this patient may only see their own appointments") before the service layer
  implements them, and no administrative endpoint exists yet to fence off.
- Registration can only ever grant `PATIENT`. Doctor and admin accounts must come from a
  controlled back-office step (the next auth-related step).
- `401` (missing/invalid token) vs `403` (valid token, wrong roles) is enforced and tested.

## Getting the Authenticated User in Code

```java
@Autowired CurrentUserProvider currentUser;

currentUser.currentUserId()     // Optional<Long> from the JWT subject
currentUser.currentUserEmail()  // Optional<String> from the JWT email claim
currentUser.requireCurrentUserId() // throws 401-style InsufficientAuthenticationException when absent
```

`appointment_status_history.changed_by_user_id` is still written as `NULL` — not because
identity is missing, but because the appointment service signature has not been extended
with the actor yet (a small, self-contained change left for the step that needs it):

```java
// future wiring sketch
Long callerId = currentUserProvider.requireCurrentUserId();
appointmentService.cancelAppointment(id, reason, callerId); // overload to be added
```

This is the integration seam: identity is available from the security context, and the
service will accept it as an argument — never as a client-supplied request field.

---

## Testing Strategy

Two integration test classes run the real filter chain against real MySQL
(`@SpringBootTest` + `@Transactional`, rollback; no H2; no mocked security filter):

- `AuthControllerTest` (12 tests) — registration success/shape (201, token, `ROLE_PATIENT`),
  BCrypt hash round-trip with a plaintext fixture, duplicate email 409, privilege-escalation
  attempt neutralized, Bean Validation failures, login success (real token decoded with the
  real `JwtDecoder`: `sub`, `email`, `roles`, `iat`/`exp` = 60 min), unknown email and wrong
  password with the **same** generic 401, failure counter incremented, locked and inactive
  accounts only revealing their state after the password matched, counter reset +
  `last_login_at` on success, blank-field 400s.
- `SecurityFilterTest` (12 tests) — protected endpoint without token → 401 JSON, public
  endpoints stay open, garbage token → 401 without leaking internals, expired token → 401,
  forged token signed with a different key → 401, malformed `Authorization` schemes
  rejected, valid tokens reaching the service for each business role, role-less token → 403,
  `CurrentUserProvider` reading the JWT caller (and empty when nobody is authenticated), and
  a full end-to-end flow: register → Bearer token → book an appointment (201).

`AppointmentControllerTest` was updated (not removed) to build its MockMvc with
`springSecurity()` and to send a real test-issued JWT on every request — so the existing
Step 13 assertions now prove the protected API works *behind* authentication. A DSL-style
"three rights" login flow is the memory hook: **right password → right standing → right
roles**.

No real secret appears anywhere in the tests: the test context uses the ephemeral key, and
the only plaintext strings are low-entropy fixtures (`"SecretPass1"`, `"RightPass99"`).

---

## Known Limitations (Honest List)

1. **No automatic account lockout.** `failed_login_attempts` is counted and reset, but
   nothing locks an account after N failures — a deliberate policy decision left open.
2. **No refresh tokens / logout.** Tokens simply expire; "logout" is client-side token
   deletion. No revocation list exists.
3. **HS256 symmetric key.** One shared secret signs and verifies. RS256 (public/private)
   is the usual next step when multiple services must verify tokens.
4. **No token rotation or `jti` claim** — replay within the token lifetime is possible.
5. **No ownership rules.** Any authenticated business role can call any appointment
   endpoint; "my appointments only" needs a service-layer query by the authenticated user.
6. **Registration is patient-only.** Doctor/admin accounts need a back-office flow.
7. **`changed_by_user_id` still NULL** — the wiring sketch above is prepared but not
   applied to `AppointmentService` yet.
8. **BCrypt cost is the default (10)** — tune it per deployment hardware if needed.
9. **Not production-grade.** No rate limiting on `/login`, no HTTPS enforcement here (do
   that at the proxy), no password-strength meter, no audit logging of security events.
   This is the first security milestone, not a finished product.
