# 08 - Notification Module

## Overview

The Notification Module is responsible for reacting to domain events and sending external notifications.

It is designed following Clean Architecture principles and remains fully decoupled from the business logic of the Contact module.

Currently implemented notification channel:

- Telegram Bot API

The module is extensible and allows additional channels (Email, SMS, Webhooks, etc.) without modifying the Contact module.

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
│   ├── handler
│   ├── service
│   └── mapper
├── domain
│   └── model
└── infrastructure
	└── telegram
````

---

## Event-Driven Flow

The notification process is triggered by a domain event.

### Flow

1. A contact request is created
2. `ContactPublicService` persists the entity
3. A `ContactRequestCreatedEvent` is published
4. `ContactRequestCreatedEventHandler` receives the event
5. The handler delegates to `NotificationService`
6. The notification channel sends the external message

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
TelegramNotificationChannel
↓
TelegramClient
````
---

## NotificationService

The `NotificationService` acts as an orchestration layer.

Responsibilities:

- Receive a notification request
- Delegate to the configured notification channel
- Handle logging and error reporting

It does NOT contain infrastructure-specific logic.

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
## Configuration
The following properties are required:
````properties
telegram.bot.token
telegram.chat.id
telegram.api.base-url
````
Example configuration via environment variables:
````properties
telegram.bot.token=${TELEGRAM_BOT_TOKEN}
telegram.chat.id=${TELEGRAM_CHAT_ID}
telegram.api.base-url=${TELEGRAM_API_BASE_URL:https://api.telegram.org}
````

---
## Feature Toggle
Notifications can be enabled or disabled via:
````properties
app.notification.enabled
````
This allows running the application without external calls in certain environments (e.g., local development).

---
## Decoupling Strategy
The Contact module does not depend on Telegram.
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
- Multiple notification channels (Email, Slack, etc.)
- Structured logging and observability
