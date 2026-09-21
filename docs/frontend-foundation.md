# MedBook Frontend Foundation

React frontend for the MedBook clinic appointment system. Built with Vite, TypeScript, Tailwind CSS, and modern React patterns. Designed to scale from authentication to appointment booking as the backend expands.

## Stack

- **Build**: Vite 7.3.6 with TypeScript 5.8.3
- **UI**: React 19, Tailwind CSS 4.1.0, lucide-react icons
- **Forms**: React Hook Form 7.56.0, Zod 4.0.0, @hookform/resolvers 5.1.0
- **Routing**: React Router 7.5.0
- **State**: TanStack React Query 5.60.0, React Context API
- **HTTP**: Axios 1.7.0 with JWT Bearer interceptor
- **Storage**: localStorage for JWT + user profile (documented trade-off)
- **Testing**: Vitest 3.2.0, Testing Library 16.3.0, jsdom

## Architecture

### Core Layers

**Pages** (`src/App.tsx`)
- `LoginPage`: credential entry, error display, registration link
- `RegisterPage`: multi-field patient registration, date picker, validation
- `DashboardPage`: authenticated view showing user profile, appointment cards (placeholders)

**Routing** (`src/App.tsx`)
- `/login` — public
- `/register` — public
- `/dashboard` — protected (redirects to `/login` if unauthenticated)
- `/` — redirects to `/dashboard`

**Protected Routes** (`src/App.tsx`)
- `ProtectedRoute` wrapper checks `isAuthenticated` from `useAuth()`, redirects to `/login` if false

**Authentication** (`src/context/AuthContext.tsx`, `src/hooks/useAuth.ts`)
- Context stores: `user`, `token`, `isAuthenticated`, `login()`, `register()`, `logout()`
- Initialized from localStorage on first render (survives page refresh)
- `login()` and `register()` call backend, store token + user, update context
- `logout()` clears localStorage and context
- `useAuth()` hook enforces use inside `<AuthProvider>`

**API Client** (`src/api/client.ts`)
- Single Axios instance (`api`) with base URL `/api/v1`
- Request interceptor: attaches `Authorization: Bearer {token}` when available
- Response interceptor: on 401, clears auth and redirects to `/login`
- `toApiError()` transforms Axios failures into UI-friendly errors (message + field errors)
- Environment: `VITE_API_BASE_URL` defaults to `/api/v1` (Vite proxy in dev)

**Auth Endpoints** (`src/api/authApi.ts`)
- `POST /auth/register` → `AuthResponse` (token + user)
- `POST /auth/login` → `AuthResponse` (token + user)

**Storage** (`src/lib/storage.ts`)
- localStorage keys: `medbook.token`, `medbook.user`
- `saveAuth(token, user)` → stores both
- `getStoredToken()`, `getStoredUser()` → retrieves with validation (corrupt data = clear)
- `clearAuth()` → removes both
- **Trade-off documented**: localStorage is readable by JavaScript on the page, so XSS can steal the token. Production alternatives (HttpOnly cookies, in-memory + refresh flow) require backend support not yet implemented.

**UI Components** (`src/components/ui/`)
- `Button`: primary, outline, ghost variants with Tailwind styling
- `Card`: rounded container with border and shadow
- `Input`: text field with integrated label, error display, validation styling

**Types** (`src/types/auth.ts`)
- Request/response DTOs mirror backend exactly
- `RegisterRequest`, `LoginRequest`, `AuthResponse`, `AuthUser`, `ErrorResponse`, `FieldError`

### State Management

**QueryClient** (`src/App.tsx`)
- Configured with 5-minute stale time
- Passed to QueryClientProvider wrapper
- Ready for async queries (not yet wired to auth endpoints, can add as needed)

**Auth Context**
- Single source of truth for logged-in user
- Survives page refresh via localStorage restoration
- No Redux—Context is adequate for authentication state at this scale

### Development Server

**Vite** (`vite.config.ts`)
- Port: 5173 (npm run dev)
- Proxy: `/api/*` → `http://localhost:8080` (backend)
- Tailwind: compiled via `@tailwindcss/vite` plugin
- React: fast refresh enabled

**Build** (`npm run build`)
- TypeScript: `tsc -b` (project references mode)
- Vite: bundles to `dist/`
- Output: 349KB JS, 3.6KB CSS (gzipped: 112KB / 1KB)

## Key Flows

### Login

1. User enters email + password on `/login`
2. Form submit calls `login()` from `useAuth()`
3. `login()` calls `api.post('/auth/login', {email, password})`
4. Backend returns `AuthResponse` (token + user)
5. `saveAuth(token, user)` stores both in localStorage
6. Context updates; redirect to `/dashboard`
7. On 401 (token expired), axios interceptor clears auth and redirects to `/login`

### Registration

1. User fills registration form on `/register` (email, password, name, phone, DOB)
2. Form submit calls `register()` from `useAuth()`
3. `register()` calls `api.post('/auth/register', {...fields})`
4. Backend returns `AuthResponse` (token + user)
5. Same flow as login: store, update context, redirect to dashboard

### Protected Route Access

1. Unauthenticated user navigates to `/dashboard`
2. `ProtectedRoute` checks `isAuthenticated` from context
3. If false, renders `<Navigate to="/login" />`
4. On `/login`, page is now visible; user can log in

### Page Refresh Persistence

1. User logs in and navigates to `/dashboard`
2. User refreshes page
3. React mounts `App` → `AuthProvider` → `useState` hooks run
4. Initial state functions call `getStoredToken()` and `getStoredUser()`
5. localStorage values restored to state; context reflects signed-in user
6. User remains on `/dashboard` (no redirect to login)

## Folder Structure

```
frontend/
├── src/
│   ├── api/
│   │   ├── authApi.ts          # Login/register endpoints
│   │   └── client.ts           # Axios singleton, interceptors
│   ├── components/
│   │   └── ui/
│   │       ├── Button.tsx
│   │       ├── Card.tsx
│   │       └── Input.tsx
│   ├── context/
│   │   └── AuthContext.tsx     # Auth state + provider
│   ├── hooks/
│   │   └── useAuth.ts          # Access auth context
│   ├── lib/
│   │   └── storage.ts          # JWT + user persistence
│   ├── test/
│   │   └── setup.ts            # Vitest config
│   ├── types/
│   │   └── auth.ts             # Request/response DTOs
│   ├── App.tsx                 # Routes, pages, QueryClient
│   ├── App.test.tsx
│   ├── main.tsx                # React entry point
│   └── index.css               # Tailwind directives + globals
├── vite.config.ts              # Dev proxy, build config
├── tsconfig.json               # TypeScript project refs
├── package.json
└── dist/                       # Build output (npm run build)
```

## Backend Integration

**API Base**: `/api/v1` (Vite proxies in dev, same-origin in prod)

**Current Endpoints**:
- `POST /auth/register` — patient registration
- `POST /auth/login` — patient login

**Error Handling**:
- Backend returns `ErrorResponse` (timestamp, status, message, path, fieldErrors[])
- Frontend transforms via `toApiError()` → `ApiError` (message + fieldErrors)
- Forms display field-level errors (e.g., "Email already in use")
- Network failures show "Cannot reach the server" message

**Headers**:
- Outbound: `Content-Type: application/json`, `Authorization: Bearer {token}`
- Expected: backend validates JWT, returns 401 if invalid/expired

## Security Considerations

### Current Limitations

1. **localStorage vulnerability**: JWT readable by any JavaScript on the page. XSS attack = token theft = account compromise.
   - Mitigation: strict Content Security Policy, input validation, output encoding (not yet implemented in frontend, backend responsibility)
   - Production path: HttpOnly cookies + refresh-token flow (requires backend changes)

2. **No CSRF protection**: same-origin proxy removes cross-origin complexity, but no explicit CSRF tokens on forms. POST requests rely on same-origin policy.
   - Not a blocker for this learning project.

3. **No refresh-token rotation**: token expires after backend's configured time (default: check backend config). No refresh flow—user must log in again.
   - Future: implement refresh-token endpoint on backend, add silent refresh to client interceptor.

### Validation

- Forms use Zod schemas (not yet implemented; currently rely on HTML5 `required` + backend validation)
- Future: add `zod()` resolver to React Hook Form for client-side validation

## Testing

**Setup** (`src/test/setup.ts`)
- Vitest environment: jsdom
- Globals: true (no need to import `describe`, `it`, `expect`)

**Example** (`src/App.test.tsx`)
- Renders App without crashing

**To add**:
- Auth context tests (login, logout, persistence)
- API client tests (interceptors, error handling)
- Component tests (form submission, error display)

Run: `npm run test` or `npm run test:watch`

## Next Steps

1. **Appointment booking UI**: add form to book appointments (date, time, doctor selection)
2. **Appointment list**: fetch and display patient's appointments from backend
3. **Form validation**: wire Zod + React Hook Form resolvers
4. **Better error messages**: backend validation feedback in real-time
5. **Refresh flow**: implement token refresh (requires backend endpoint)
6. **Mobile polish**: safe-area insets, responsive layout refinement
7. **Analytics**: track user actions, guard against abuse

## Running Locally

```bash
# Install dependencies
npm install

# Development server (http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Run tests
npm run test
```

**Backend required** at `http://localhost:8080` (Vite proxy forwards `/api/v1` requests to it).
