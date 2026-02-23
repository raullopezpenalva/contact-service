
# Contact Service

Contact Service is a Spring Boot microservice designed to handle contact form submissions for a personal website. It provides secure message persistence, administrative management, and notification capabilities, serving both as a real functional backend and a professional portfolio demonstration.

---

## Features

- **Public REST API** for receiving contact messages (`email`, `subject`, `content`).
- **Data validation** and secure persistence using PostgreSQL.
- **Notification system**: Notifies the website owner via SMTP email when a new message is received.
- **Admin endpoints** (protected):
	- List messages with filtering and pagination
	- View message details
	- Change message status (`NEW`, `READ`, `ARCHIVED`, `SPAM`, `NOTIFICATION_FAILED`)
	- Add internal admin notes
- **Traceability**: Stores metadata such as source IP and User-Agent for anti-abuse and debugging.
- **Simple authentication** for admin endpoints (Basic Auth).
- **Docker Compose** setup for local development (backend + PostgreSQL).

---

## Technology Stack

- Java + Spring Boot
- RESTful JSON API
- Spring Data JPA + PostgreSQL
- Spring Mail (SMTP notifications)
- Spring Security (Basic Auth for admin endpoints)
- Docker Compose (local infrastructure)

---

## Domain Model

### Main Entity: `ContactMessage`

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

---

## API Flows

### 1. Public: Create Contact Message
- User submits the contact form (`email`, `subject`, `content`).
- Backend validates and persists the message.
- Attempts to notify the owner (does not block user response).
- Returns `201 Created` with message `id` and `createdAt`.

### 2. Admin: List Messages
- Authenticated admin can list messages, filter by status, and paginate results.

### 3. Admin: Change Message Status
- Admin can update message status (`NEW`, `READ`, `ARCHIVED`, `SPAM`, `NOTIFICATION_FAILED`) and add internal notes.

---

## MVP Scope

### Included
- Public REST endpoint for message submission
- Secure persistence and validation
- Notification to owner
- Basic admin management (list, view, change status)

### Not Included
- Attachments
- Admin UI panel
- Complex roles or JWT
- Advanced rate limiting or captcha
- Event-driven architecture
- Physical deletion of messages

---

## Documentation

Projects documentation is located in `/docs`:

- [`domain.md`](./docs/04-Domain.md) -- Domain model definition
- [`flows.md`](./docs/05-Flows.md) -- Functional flows (public + admin)
- [`stack.md`](./docs/03-Stack.md) -- Stack decisions

## Local Development

1. Clone the repository.
2. Start the infrastructure with Docker Compose:
	 ```sh
	 docker-compose up
	 ```
3. Configure environment variables for SMTP and database as needed.
4. Build and run the Spring Boot application.

---

## Success Criteria

- Users can submit contact forms and messages are stored.
- Owner receives notifications for new messages.
- Admin can manage messages via protected endpoints.
- Service is deployable in a real environment.

---

## Project status

MVP v1 in progress

## License

MIT License