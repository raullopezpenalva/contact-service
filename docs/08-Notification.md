# 08 - Notification Module

## Overview

The Notification Module is responsible for reacting to domain events and sending external notifications.

It is designed following Clean Architecture principles and remains fully decoupled from the business logic of the Contact module.

Currently implemented notification channel:

- Telegram Bot API
- Email SMTP

## Supported Notification Channels

The module supports multiple notification channels that can be configured independently or used simultaneously. Channel configuration is managed through the `app.notifications.channels` application property.

**Supported channels:**
- `EMAIL` – SMTP-based email delivery
- `TELEGRAM` – Telegram Bot API integration

**Configuration syntax:**
- Single channel: `app.notifications.channels=EMAIL`
- Multiple channels: `app.notifications.channels=EMAIL,TELEGRAM`

Channel names must be specified in uppercase and separated by commas when using multiple channels. This configuration can be set via environment variables in your `example.env` file for environment-specific deployment.
The module is extensible and allows additional channels (SMS, Webhooks, etc.) without modifying the Contact module.

---

## Design Goals

- Decouple business logic from external integrations
- Follow Single Responsibility Principle
- Enable future extensibility
- Avoid tight coupling between modules
- Keep infrastructure concerns isolated

---

## Architectural Placement

Location: `modules/platform/notification`

Structure:
```
notification
├── application
│   ├── handler
│   ├── service
│   ├── mapper
│   └── port
│       ├── in
│       └── out
├── domain
│   └── model
└── infrastructure
    ├── emailSender
    ├── telegram
    └── persistence
        ├── entity
        ├── repository
        ├── adapter
        └── mapper
````

---

## Event-Driven Flow

1. A contact request is created
2. `ContactPublicService` persists the entity
3. A `ContactRequestCreatedEvent` is published
4. `ContactRequestCreatedEventHandler` receives the event
5. The handler maps:
   - Event → `NotificationMessage`
   - Event → `NotificationDelivery`
6. `NotificationService`:
   - Persists delivery (PENDING)
   - Sends notification via gateway
   - Updates delivery status (SENT / FAILED)
   - Persists updated delivery

---

## Sequence Diagram (Conceptual)
````
Client
↓
ContactPublicService
↓
ContactRequestCreatedEvent
↓
EventPublisher
↓
ContactRequestCreatedEventHandler
↓
NotificationService
↓
TelegramNotificationChannel or/and EmailNotificationChannel
↓
TelegramClient or/and EmailClient
````
---

## NotificationService

The `NotificationService` acts as an orchestration layer for notification delivery.

Responsibilities:

- Persist delivery state before sending
- Execute the notification through the configured gateway
- Update delivery status (SENT / FAILED)
- Handle retry-related state (attempts, errors)
- Ensure delivery traceability

It does NOT:

- Contain infrastructure-specific logic
- Perform direct database operations (uses repository port)

---

## Telegram Integration

The Telegram integration is implemented in the infrastructure layer.

### TelegramClient

Responsible for:

- Building HTTP requests
- Calling Telegram Bot API
- Sending the message payload

Endpoint used: `POST https://api.telegram.org/bot<toke>/sendMessage`

Payload example:

```json
{
  "chat_id": "123456",
  "text": "New contact request received",
  "disable_web_page_preview": true
}
```

---
## Email Integration

The Email integration is implemented in the infrastructure layer and use `spring-boot-starter-mail` dependency.

### EmailClient

Responsible for:

- Building the email request with `MimeMessageHelper`
- Sends the email via SMTP with `JavaMailSender`

email application porperties:
```
spring.mail.host=${SPRING_MAIL_HOST}
spring.mail.port=${SPRING_MAIL_PORT}
spring.mail.username=${SPRING_MAIL_USERNAME}
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.starttls.required=false
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
```
---
## Notification Delivery Model

To improve reliability and traceability, notification delivery is persisted in the database.

Each notification attempt is represented by a `NotificationDelivery` domain object.

### Responsibilities

- Track delivery status (PENDING, SENT, FAILED)
- Store number of attempts
- Capture last error message
- Persist payload snapshot for debugging

### Lifecycle

1. A delivery is created with status `PENDING`
2. It is persisted before sending
3. A send attempt is performed
4. Status is updated to:
   - `SENT` on success
   - `FAILED` on error
5. The updated state is persisted

This enables:

- Retry strategies
- Idempotency
- Observability
- Future multi-channel support
---
## Persistence Layer

Notification delivery persistence is implemented using a clean separation between domain and infrastructure.

### Components

- Domain Model:
  - `NotificationDelivery`
  - `NotificationStatus`
  - `NotificationChannel`

- Application Port:
  - `NotificationDeliveryRepository`

- Infrastructure:
  - `NotificationDeliveryEntity` (JPA)
  - `NotificationDeliveryJpaRepository`
  - `NotificationDeliveryRepositoryAdapter`
  - `NotificationDeliveryPersistenceMapper`

### Design Principles

- Domain is independent from persistence
- JPA entities are isolated in infrastructure
- Mapping is handled explicitly via mappers
- Application depends only on ports (interfaces)

This ensures the system can evolve without coupling to a specific database.
---
## Configuration
The following properties are required:
````properties
app.notification.enabled
app.notifications.channels
telegram.bot.token
telegram.chat.id
telegram.api.base-url
email.recipient
````
Example configuration via environment variables:
````properties
app.notification.enabled=${NOTIFICATION_ENABLED:true}
app.notifications.channels=${NOTIFICATIONS_CHANNELS:EMAIL}
telegram.bot.token=${TELEGRAM_BOT_TOKEN}
telegram.chat.id=${TELEGRAM_CHAT_ID}
telegram.api.base-url=${TELEGRAM_API_BASE_URL:https://api.telegram.org}
email.recipient=${EMAIL_RECIPIENT:dummy}
````

---
## Feature Toggle
Notifications can be enabled or disabled via:
````properties
notifications.enabled
````
This allows running the application without external calls in certain environments (e.g., local development).

---
## Decoupling Strategy
The Contact module does not depend on the notification channel (like Telegram or Email).
It only:
- Publishes a domain event
The Notification module:
- Listens to events
- Reacts independently
This ensures:
- High cohesion
- Low coupling
- Easy extensibility

---
## Future Improvements
Possible architectural improvements:
- Message broker integration (Kafka / RabbitMQ)
- Outbox pattern implementation
- Retry mechanisms
- Structured logging and observability

---
## Reliability Considerations

The system is designed to support reliable notification delivery.

Although retries are not implemented yet, the current design enables:

- Retry mechanisms based on delivery status
- Idempotency using eventId + channel
- Failure tracking through persisted errors
- Future scheduling of pending deliveries

This prepares the system for production-grade resilience.