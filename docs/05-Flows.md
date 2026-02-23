
# Flows (MVP)

This document describes the functional flows of the **Contact Backend** service in the MVP.

---

## Flow 1 — (Public) Create Contact Message

**Actor:** User (public)  
**Objective:** Send a message from the website form.  
**Input:** `email`, `subject`, `content`  
**Output:** `201 Created` + `id`, `createdAt`

### Steps
1. The user fills out the form at `/contact` and clicks **Send**.
2. The frontend makes a `POST /api/v1/contact/messages` request with the form payload.
3. The backend validates:
   - email format
   - min/max lengths for subject and content
4. The backend creates a `ContactMessage` with:
   - `status = NEW`
   - `createdAt/updatedAt`
   - (optional MVP+) `sourceIp`, `userAgent`, `contentHash`
5. The backend persists the message in the database.
6. The backend attempts to notify the administrator:
   - if OK: records `lastNotifiedAt` and `notificationAttempts += 1`
   - if FAIL: sets `status = NOTIFICATION_FAILED` (the message is retained)
7. The backend responds with `201 Created` and `{ id, createdAt }`.

### Acceptance Criteria
- The message is always saved if valid.
- The user response does not depend on notification success.
- No internal information (e.g., “email failed”) is exposed in the public response.

---

## Flow 2 — (Admin) List Messages

**Actor:** Administrator (authenticated)  
**Objective:** View the list of messages for management.

### Endpoint
- `GET /api/v1/admin/contact/messages`

### Recommended Optional Parameter (simple MVP)
- `status` (optional) — filter by status (`NEW`, `READ`, `ARCHIVED`, `SPAM`, `NOTIFICATION_FAILED`)

Example:
- `/api/v1/admin/contact/messages?status=NEW`

### Steps
1. The admin makes an authenticated request.
2. The backend validates credentials.
3. The backend queries the DB with pagination (and status filter if applied).
4. The backend returns a list with “summary” fields:
   - `id`, `email`, `subject`, `status`, `createdAt`

### Acceptance Criteria
- Endpoint is protected (not accessible without auth).
- Ordered by `createdAt` descending.
- Pagination works.

---

## Flow 3 — (Admin) Change Message Status

**Actor:** Administrator (authenticated)  
**Objective:** Manage the lifecycle of a message (read / archive / spam).

### Endpoint
- `PATCH /api/v1/admin/contact/messages/{id}`

### Payload (MVP)
- `status` (required)

Optional MVP+:
- `adminNote` (optional)

Example:
```json
{
  "status": "READ",
  "adminNote": "Replied by email on 2026-02-23"
}