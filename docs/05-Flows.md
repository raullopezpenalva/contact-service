
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
**Objective:** Retrieve a paginated list of messages, sorted from newest to oldest.

### Endpoint
- `GET /api/v1/admin/contact/messages`

### Optional Parameters
- `status` — Filter messages by status (`NEW`, `READ`, `ARCHIVED`, `SPAM`, `NOTIFICATION_FAILED`).

### Required Parameters
- `page` — Specifies which page of results to retrieve, starting from 0.
- `size` — Specifies the number of items per page.

Example:
- `/api/v1/admin/contact/messages?status=NEW&page=0&size=20`

### Steps
1. The administrator sends an authenticated request.
2. The backend verifies authentication credentials.
3. The backend queries the database, applying pagination and the status filter if provided.
4. The backend returns a list of message summaries, each containing:
   - `id`, `email`, `subject`, `status`, `createdAt`

### Acceptance Criteria
- The endpoint is secured and accessible only to authenticated administrators.
- Results are ordered by `createdAt` in descending order (newest first).
- Pagination and optional status filtering function as expected.

---
## Flow 3 — (Admin) Retrieve Detailed Message by ID

**Actor:** Administrator (authenticated)  
**Objective:** Retrieve a specific message with comprehensive details.

### Required Parameters
- `{id}` — The unique identifier of the message, provided in the request path.

Example:  
- `/api/v1/admin/contact/messages/ed816596-559d-49df-9235-619cb9dbd487`

### Steps
1. The administrator sends an authenticated request to the endpoint.
2. The backend verifies the administrator’s authentication credentials.
3. The backend queries the database to retrieve the message entity by its ID.
4. The backend returns a detailed message object containing:
   - `id`, `email`, `subject`, `content`, `status`, `adminNote`, `sourceIp`, `userAgent`, `createdAt`

### Acceptance Criteria
- The endpoint is accessible only to authenticated administrators.
- The response includes all relevant message fields as listed above.
- If the message does not exist, an appropriate error response is returned.

---
## Flow 4 — (Admin) Change Message Status

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