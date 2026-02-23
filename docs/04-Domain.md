
# Domain (MVP+)

This document defines the domain model for the **Contact Backend** service for the personal website.

Objective: maintain a **minimal model for the MVP**, but with “cheap” fields that provide **traceability and scalability** without introducing premature complexity.

---

## Main Entity: `ContactMessage`

Represents a message sent from the public contact form.

### Responsibilities
- Store the message content (email, subject, content).
- Record basic origin metadata (IP, User-Agent) for traceability and anti-spam.
- Manage a simple lifecycle through a `status` field.
- Allow later administration (internal notes, archiving, etc.).
- Enable notification resilience (attempt count and timestamps).

---

## Status: `ContactMessageStatus`

Enum that defines the message status.

Values:

- `NEW`  
  Message just received and persisted. Initial state.

- `READ`  
  Message reviewed by the administrator.

- `ARCHIVED`  
  Message managed/closed. Kept for historical purposes.

- `SPAM`  
  Message marked as spam (e.g., honeypot, simple heuristic, abuse).

- `NOTIFICATION_FAILED`  
  The message was saved correctly, but notification to the administrator failed.

---

## Attributes (MVP+)

### Identity and Content
- `id: UUID`  
  Unique identifier for the message.

- `email: String`  
  Sender's email.

- `subject: String`  
  Message subject.

- `content: String (TEXT)`  
  Message content (long text).

### Status and Administration
- `status: ContactMessageStatus`  
  Current status of the message.

- `adminNote: String (TEXT, nullable)`  
  Internal note for management (e.g., “replied by email”, “pending call”).

### Traceability (anti-abuse and debugging)
- `sourceIp: String (nullable)`  
  Source IP of the request (if available in the reverse-proxy / app).

- `userAgent: String (TEXT, nullable)`  
  Client User-Agent.

### Notifications (future resilience)
- `notificationAttempts: Integer`  
  Number of notification attempts made (MVP: usually 0 or 1).

- `lastNotifiedAt: Instant (nullable)`  
  Timestamp of the last successful notification attempt.

### Dedupe (avoid simple duplicates)
- `contentHash: String (nullable)`  
  Content hash (e.g., email+subject+content) to detect repeated retries.

### Auditing
- `createdAt: Instant`  
  Creation date/time.

- `updatedAt: Instant`  
  Last update date/time.

---

## Validation Rules (MVP)

These rules apply to the public creation endpoint.

- `email`
  - required
  - valid email format
  - recommended max length: 254

- `subject`
  - required
  - min: 3
  - max: 120

- `content`
  - required
  - min: 10
  - max: 4000

Traceability and administration fields:
- `sourceIp`, `userAgent`, `adminNote`, `contentHash`, `lastNotifiedAt` can be `null`.
- `notificationAttempts` cannot be `null` (initial recommended value: 0).

---

## Lifecycle (status)

Initial state:
- When creating a valid message: `NEW`

Expected transitions (admin):
- `NEW` -> `READ`
- `READ` -> `ARCHIVED`
- Any state -> `SPAM` (if abuse is detected)

Technical state:
- If notification fails after persisting: `NOTIFICATION_FAILED`  
  (The message remains accessible to admin. In the future, retries could be implemented.)

---

## Recommended Indexes (PostgreSQL)

For administration and performance:

- `createdAt` (DESC)  
  Recent listings.

- `(status, createdAt)`  
  Filters by status + temporal order.

- `email`  
  Search by sender.

- `contentHash` (optional)  
  If deduplication is implemented.

---


## Attribute Summary Table

| Attribute            | Type                    | Nullable | Description                                                      |
|----------------------|-------------------------|----------|------------------------------------------------------------------|
| id                   | UUID                    | No       | Unique identifier for the message                                |
| email                | String                  | No       | Sender's email                                                  |
| subject              | String                  | No       | Message subject                                                 |
| content              | String (TEXT)           | No       | Message content (long text)                                     |
| status               | ContactMessageStatus    | No       | Current status of the message                                   |
| adminNote            | String (TEXT)           | Yes      | Internal note for management                                    |
| sourceIp             | String                  | Yes      | Source IP of the request                                        |
| userAgent            | String (TEXT)           | Yes      | Client User-Agent                                               |
| notificationAttempts | Integer                 | No       | Number of notification attempts made                             |
| lastNotifiedAt       | Instant                 | Yes      | Timestamp of the last successful notification attempt            |
| contentHash          | String                  | Yes      | Content hash for deduplication                                  |
| createdAt            | Instant                 | No       | Creation date/time                                              |
| updatedAt            | Instant                 | No       | Last update date/time                                           |