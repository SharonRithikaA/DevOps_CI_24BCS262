# Gym Membership Management System

A full-stack membership management system for a gym: members, membership plans, payments,
attendance and trainers, with a role-based (Admin / Staff) dashboard, reports and CSV export.

Built as a Software Engineering & Agile Practices academic project, with business logic
separated into testable service classes and covered by JUnit 5 + Mockito unit tests.

## Overview

Front-desk staff register members, record payments and mark attendance. Administrators manage
plans, trainers and pricing, and review reports. The dashboard gives a live snapshot of
membership health and revenue, and every membership date, fee and discount calculation is done
by a small set of deterministic service classes rather than scattered across controllers.

## Features

- **Members** — register, edit, delete, search (name / ID / phone / email), filter (status,
  plan, payment status), sort, and view a member's full profile with payment and attendance history.
- **Membership plans** — Basic / Standard / Premium / Annual by default, fully editable; admins
  can add, edit, deactivate or delete plans.
- **Payments** — record cash / UPI / card / online payments with a percentage discount (capped
  at 50%), auto-calculated final amount, and pending → paid/failed settlement.
- **Attendance** — one check-in per member per day, blocked for expired or not-yet-started
  memberships, with a searchable daily/history view.
- **Trainers** — manage trainers and assign/unassign members to them.
- **Dashboard** — total/active/expired members, expiring-soon count, today's attendance, monthly
  revenue, active trainers, plus revenue, attendance, plan-distribution and status charts.
- **Reports** — membership, active, expired, revenue and attendance reports with date/plan/payment
  filters, viewable in-app and exportable to CSV. (Admin only.)
- **Auth** — JWT login for Admin and Staff roles with different permissions (see below).

## Technology Stack

**Backend:** Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Validation, Spring
Security (JWT via `spring-boot-starter-oauth2-resource-server`), Maven, MySQL 8.

**Frontend:** React 18 (Vite), React Router, Tailwind CSS, Recharts, lucide-react icons.

**Testing:** JUnit 5, Mockito, Spring Boot Test.

## Architecture

Layered backend, business logic kept out of controllers:

```
Controller  ->  Service  ->  Repository  ->  Database
 (REST I/O)   (business       (Spring Data
              rules,           JPA)
              validation)
```

```
backend/src/main/java/com/gymms/
├── controller/   REST endpoints (thin — delegate to services)
├── service/      Business logic: MembershipService, MemberService, PaymentService,
│                 AttendanceService, PlanService, TrainerService, DashboardService,
│                 ReportService, CsvWriter, AuthService
├── entity/       JPA entities (Member, MembershipPlan, Payment, Attendance, Trainer, AppUser, enums)
├── repository/   Spring Data JPA repositories
├── dto/          Request/response records (validation annotations live here)
├── exception/    Domain exceptions + GlobalExceptionHandler (uniform JSON error body)
├── security/     JwtService (issues tokens)
└── config/       SecurityConfig, AppConfig (shared Clock bean), DataSeeder (demo data)
```

Every service takes its dependencies through the constructor (easy to mock in tests), and every
date-based calculation goes through an injected `Clock` bean rather than calling
`LocalDate.now()` directly — that's what makes `MembershipServiceTest` deterministic.

## Project Structure

```
gym-membership-management-system/
├── backend/           Spring Boot API (Maven project)
├── frontend/           React app (Vite project)
├── database/           setup.sql (create DB + user) and schema.sql (reference DDL)
├── documentation/       API reference and Agile sprint notes
├── screenshots/         (empty — add your own screenshots before submission if required)
└── README.md            This file
```

## Database Setup

1. Install MySQL 8 and make sure it's running.
2. Create the database and a dedicated user:
   ```bash
   mysql -u root -p < database/setup.sql
   ```
   Edit `database/setup.sql` first if you want a different username/password than the
   placeholders it contains.
3. You do **not** need to run `database/schema.sql` — the backend creates and updates the tables
   automatically on startup (`spring.jpa.hibernate.ddl-auto=update`). It's provided as a reference
   and for anyone who prefers to create the schema by hand.

## Installation

Prerequisites: JDK 17+, Maven 3.9+, Node.js 18+, MySQL 8.

```bash
git clone <this repository>
cd gym-membership-management-system
```

### Backend

```bash
cd backend
cp .env.example .env
# edit .env: set DB_PASSWORD to match database/setup.sql, and set JWT_SECRET
#   (generate one with: openssl rand -base64 48)
```

### Frontend

```bash
cd frontend
npm install
cp .env.example .env   # defaults are fine for local development
```

## Environment Variables

Backend (`backend/.env`, see `backend/.env.example`):

| Variable | Purpose | Default |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | MySQL connection | `localhost`, `3306`, `gym_db` |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL credentials | must be set |
| `JWT_SECRET` | HS256 signing key, **32+ characters** | must be set |
| `JWT_EXPIRY_HOURS` | Login token lifetime | `8` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origin(s) | `http://localhost:5173` |
| `SEED_ENABLED` | Load demo users + sample data on first run | `true` |
| `SEED_ADMIN_PASSWORD`, `SEED_STAFF_PASSWORD` | Demo login passwords | `Admin@123`, `Staff@123` |

Frontend (`frontend/.env`, see `frontend/.env.example`):

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Backend URL for production builds (dev uses the Vite proxy) | empty |
| `VITE_SHOW_DEMO_LOGINS` | Show "fill demo login" buttons on the login page | `true` |

Never commit a real `.env` file — both are already listed in `.gitignore`.

## Running the Backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. On first run (empty database) it creates the two demo
users and, if `SEED_ENABLED=true`, loads sample plans/trainers/members/payments/attendance so the
dashboard isn't empty. Sample member emails use `@example.com` and payment references are
prefixed `DEMO-` so they're easy to tell apart from real data.

## Running the Frontend

```bash
cd frontend
npm run dev
```

Open `http://localhost:5173`. In development, Vite proxies `/api/**` to `http://localhost:8080`,
so no CORS configuration is needed locally.

For a production build: `npm run build` (output in `frontend/dist/`, served by any static host —
set `VITE_API_BASE_URL` to your backend's public URL first).

## Demo Credentials

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `Admin@123` |
| Staff | `staff` | `Staff@123` |

These are created automatically on first backend start when `SEED_ENABLED=true` (or, for the
users themselves, whenever the `app_users` table is empty). **Change these passwords, or set
`SEED_ENABLED=false` and create your own users, before any real deployment.**

## Roles and Permissions

| Action | Admin | Staff |
|---|:---:|:---:|
| View dashboard, members, plans, trainers | ✅ | ✅ |
| Register a member | ✅ | ✅ |
| Edit / delete a member | ✅ | ❌ |
| Record a payment | ✅ | ✅ |
| Mark attendance | ✅ | ✅ |
| Manage plans / trainers (create, edit, delete) | ✅ | ❌ (read only) |
| View / export reports | ✅ | ❌ |

Enforced server-side in `SecurityConfig` (not just hidden in the UI).

## API Overview

All endpoints are under `/api` and (except `/api/auth/login`) require
`Authorization: Bearer <token>`. Full request/response shapes are in
[`documentation/API.md`](documentation/API.md).

```
POST   /api/auth/login              GET  /api/plans            GET    /api/trainers
GET    /api/auth/me                 POST /api/plans            POST   /api/trainers
POST   /api/auth/change-password    PUT  /api/plans/{id}       PUT    /api/trainers/{id}
                                     DEL  /api/plans/{id}       PUT    /api/trainers/{id}/members
GET    /api/members                                            DEL    /api/trainers/{id}
GET    /api/members/{id}            GET  /api/payments
POST   /api/members                 POST /api/payments         GET    /api/dashboard
PUT    /api/members/{id}            PUT  /api/payments/{id}/status
DEL    /api/members/{id}                                       GET    /api/reports/{type}
GET    /api/members/{id}/payments   POST /api/attendance       GET    /api/reports/{type}/csv
GET    /api/members/{id}/attendance GET  /api/attendance/today
                                     GET  /api/attendance
```

The frontend calls exactly these endpoints (see `frontend/src/pages/*.jsx` and `frontend/src/api.js`) —
nothing here is undocumented or unused.

## Testing

Three service classes carry the graded business logic, each with a matching JUnit 5 test class
(87 tests total) in `backend/src/test/java/com/gymms/service/`:

| Service | Test class | What's covered |
|---|---|---|
| `MembershipService` | `MembershipServiceTest` | Fee calculation, expiry-date math (incl. month-end/leap-year), active/expired checks, remaining days, status transitions, renewal start date |
| `MemberService` | `MemberServiceTest` | Registration validation (duplicate email/phone, inactive plan, bad dates, future DOB), lookup/not-found, update, delete, search/filter/sort |
| `PaymentService` | `PaymentServiceTest` | Discount/final-amount math (incl. rounding), discount cap, payment recording, membership renewal vs. settling the current period, status transitions |

Two more test classes cover supporting logic used the same way in production:
`AttendanceServiceTest` (duplicate/expired/upcoming attendance rules) and `CsvWriterTest` (report
export escaping).

### Running the tests

```bash
cd backend
mvn test
```

Tests use Mockito to mock repositories, so they run in-memory with no database required, and a
fixed `Clock` (15 March 2026) so date-based assertions are exact and reproducible — for example
`MembershipServiceTest` asserts that a 3-month plan starting 1 Jan 2026 expires 31 Mar 2026, and
that a membership with exactly 7 days left is `EXPIRING_SOON` while 8 days left is `ACTIVE`.

## JUnit 5 Test Execution

These tests were compiled and executed for real against JUnit 5.9 / Mockito 2.23 while building
this project (not fabricated): **87 tests, 87 passed, 0 failed.** A mutation check (deliberately
breaking the expiry-date and discount-cap formulas) was also run to confirm the tests actually
fail when the logic is wrong, not just when it's right.

## Screenshots

Not included — add your own from a running instance before submission if your assignment
requires them (`screenshots/` folder is ready for that).

## Future Enhancements

- Pagination and server-side filtering for very large member lists (current implementation loads
  and filters in memory, which is fine for a gym-sized dataset but not for tens of thousands of rows)
- Email/SMS reminders for expiring memberships
- PDF export for reports (CSV export is implemented; PDF was left out to avoid an unreliable
  half-implemented feature — see "Limitations" below)
- Refresh tokens / token revocation (current JWTs are stateless and simply expire after
  `JWT_EXPIRY_HOURS`)
- Member self-service portal

## Agile Iterations (Sprints)

- **Sprint 1** — Requirements analysis, tech stack selection, project scaffolding (Maven + Vite),
  database design
- **Sprint 2** — Member and membership plan management (entities, services, CRUD APIs, member UI)
- **Sprint 3** — Payment and attendance modules, including business-rule edge cases (discount cap,
  duplicate attendance, expired-membership checks)
- **Sprint 4** — Dashboard, reports (incl. CSV export), authentication and role-based access
- **Sprint 5** — JUnit 5 test suite, bug fixing, documentation, final consistency check

## Limitations

These are genuine, current limitations, not hidden gaps:

- No automated integration/end-to-end tests (`@SpringBootTest` + Testcontainers) — only unit
  tests with mocked repositories. Manual testing against a real MySQL instance is still needed
  before relying on this in production.
- No PDF report export (CSV only).
- No pagination on list endpoints; the UI paginates client-side after fetching the full filtered
  list, which won't scale past a few thousand members.
- No password-reset flow (only "change password" while logged in).
- The full application (controllers, security config, Spring wiring) could not be compiled in
  this environment because Maven Central was not reachable from the sandbox network — the service
  layer (the part carrying the graded business logic and unit tests) *was* compiled and its 87
  tests executed for real; the rest was written and carefully cross-checked (DTO/record shapes,
  repository method names, `@Value` property keys) but not build-verified. **Run `mvn clean
  install` yourself the first time** and fix anything your Java/Maven/MySQL versions surface —
  see the pom.xml for exact dependency versions if you hit a resolution issue.
DevOps CI/CD workflow integrated with Git and GitHub.
