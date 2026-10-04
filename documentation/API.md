# API Reference

Base URL: `http://localhost:8080` (development). All paths below are relative to that.

All endpoints except `POST /api/auth/login` require:
```
Authorization: Bearer <jwt>
```
Missing/invalid/expired tokens return `401`. Insufficient role returns `403`.

Error responses always have this shape:
```json
{
  "timestamp": "2026-03-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Membership end date cannot be before the start date",
  "fieldErrors": { "email": "Email must be a valid address" }
}
```
`fieldErrors` is `null` unless the failure was a `@Valid` validation error.

---

## Auth

### `POST /api/auth/login`
Body: `{ "username": "admin", "password": "Admin@123" }`
Response `200`: `{ "token": "...", "username": "admin", "fullName": "Gym Administrator", "role": "ADMIN" }`
`401` on bad credentials.

### `GET /api/auth/me`
Returns the current user: `{ "username", "fullName", "role" }`.

### `POST /api/auth/change-password`
Body: `{ "currentPassword", "newPassword" }` (new password 8–72 chars, must differ from current).

---

## Members

### `GET /api/members`
Query params (all optional): `q` (name/ID/phone/email, partial, case-insensitive), `status`
(`ACTIVE` | `EXPIRING_SOON` | `EXPIRED` | `UPCOMING` — `ACTIVE` includes expiring-soon),
`planId`, `paymentStatus` (`PAID`|`PENDING`|`FAILED`), `sortBy` (`name`|`memberCode`|`joinDate`|`expiry`),
`direction` (`asc`|`desc`).

### `GET /api/members/{id}` / `POST /api/members` / `PUT /api/members/{id}` / `DELETE /api/members/{id}`
`POST`/`PUT` body:
```json
{
  "fullName": "Asha Active", "email": "asha@example.com", "phone": "9840012345",
  "dateOfBirth": "1998-01-10", "gender": "FEMALE", "address": "12, Anna Nagar, Chennai",
  "emergencyContact": "Parent - 9111111111", "planId": 1,
  "membershipStartDate": null, "membershipEndDate": null, "trainerId": null
}
```
`membershipStartDate`/`membershipEndDate`/`trainerId` are optional — start defaults to today
(registration) or is kept unchanged (edit), end is computed from the plan's duration.
Delete requires the **Admin** role; register and view are available to Staff too.

### `GET /api/members/{id}/payments` / `GET /api/members/{id}/attendance`
Payment and attendance history for one member, newest first.

---

## Membership Plans (`/api/plans`) — Admin write, everyone read

`GET /api/plans`, `GET /api/plans/{id}`,
`POST`/`PUT` body: `{ "name", "durationMonths", "price", "description", "active" }`,
`DELETE /api/plans/{id}` (fails with `409` if members or payments reference the plan — deactivate
it instead).

---

## Trainers (`/api/trainers`) — Admin write, everyone read

`GET /api/trainers`, `GET /api/trainers/{id}`,
`POST`/`PUT` body: `{ "name", "email", "phone", "specialization", "experienceYears", "availability", "active" }`,
`PUT /api/trainers/{id}/members` body: `{ "memberIds": [1, 2, 3] }` (replaces the trainer's
assigned members with exactly this set), `DELETE /api/trainers/{id}` (un-assigns members, doesn't
delete them).

---

## Payments (`/api/payments`)

### `GET /api/payments`
Query params: `q`, `status`, `method`, `from`, `to` (ISO dates).

### `POST /api/payments`
```json
{ "memberId": 1, "planId": 1, "discountPercent": 10, "method": "UPI", "status": "PAID", "transactionReference": null }
```
`discountPercent` optional (default 0, max 50). `status` optional (default `PAID`).
`transactionReference` optional (auto-generated as `TXN-yyyyMMdd-XXXXXXXX` if omitted).
A `PAID` payment settles the member's current unpaid period, or renews their membership
(continuing after the current end date, or starting today if it had lapsed).

### `PUT /api/payments/{id}/status`
Body: `{ "status": "PAID" }` or `{ "status": "FAILED" }`. Only works on `PENDING` payments.

---

## Attendance (`/api/attendance`)

`POST /api/attendance` — body `{ "memberId": 1 }`. Rejects a second check-in the same day
(`409`) and memberships that are expired or not yet started (`400`).
`GET /api/attendance/today` — today's check-ins.
`GET /api/attendance` — query params `date`, `q`, `memberId`.

---

## Dashboard

`GET /api/dashboard` — counts, monthly revenue, active trainers, four chart series
(6-month revenue, 14-day attendance, plan distribution, status breakdown), the next 5 members
expiring soon and the 5 most recent payments.

---

## Reports (`/api/reports`) — Admin only

`GET /api/reports/{type}` and `GET /api/reports/{type}/csv` (downloads a file), where `type` is
one of `membership`, `active`, `expired`, `revenue`, `attendance`. Query params: `from`, `to`,
`planId`, `paymentStatus` (ignored by the `attendance` report).

JSON shape:
```json
{ "title": "Revenue report", "columns": ["Date", "..."], "rows": [["2026-03-15", "..."]], "summary": { "Transactions": "12" } }
```
