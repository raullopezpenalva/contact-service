
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
## Security


The application is secured using Spring Security. Endpoints under `/api/v1/admin/*` are protected with Basic Auth authentication.

The admin username and password can be easily changed using environment variables in the `service.env` file:

```
SPRING_SECURITY_USER_NAME=user-example
SPRING_SECURITY_USER_PASSWORD=password-example
```
---
## Documentation

Projects documentation is located in `/docs`:

- [`project-overview.md`](./docs/01-Project-overview.md) -- Overview of the project
- [`MVP-scope.md`](./docs/02-MVP-scope.md) -- MVP scope
- [`stack.md`](./docs/03-Stack.md) -- Stack decisions
- [`domain.md`](./docs/04-Domain.md) -- Domain model definition
- [`flows.md`](./docs/05-Flows.md) -- Functional flows (public + admin)
- [`DTO.md`](./docs/06-DTO.md) -- DTO information


## Local Development

1. Clone the repository.
2. Set up environment for postgres and the app with two files:
   1. postgres.env
		Example:
		```
		POSTGRES_DB=example_db
		POSTGRES_USER=user-example
		POSTGRES_PASSWORD=password-example
		```
   2. service.env
		Example:
		```
		# -----------------------------------------------
		# --- SPRING APPLICATION CONFIGURATION ---
		# -----------------------------------------------

		# --- Application Environment Variables ---
		SPRING_APPLICATION_NAME=contact-service
		SPRING_SERVER_PORT=8080

		# --- PostgreSQL Database Configuration ---
		SPRING_DATASOURCE_URL=jdbc:postgresql://contact_postgres:5432/example_db
		SPRING_DATASOURCE_USERNAME=user-example
		SPRING_DATASOURCE_PASSWORD=password-example

		# --- Security Configuration ---
		SPRING_SECURITY_USER_NAME=user-example
		SPRING_SECURITY_USER_PASSWORD=password-example
		```
3. Start the infrastructure with Docker Compose:
	 ```sh
	 docker-compose up -d --build
	 ```
	Remenber to setup the host port what you want to use in the ports setup for the contact_service in the compose.yml

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