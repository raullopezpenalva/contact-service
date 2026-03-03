# 07 - Domain Events

## Overview

The contact module emits a domain event whenever a new contact request is created.

This allows the system to react to business actions without tightly coupling modules.

Current implemented event:

- `ContactRequestCreatedEvent`

---

## Why Events?

Events are used to:

- Decouple the contact module from the notification module
- Follow a clean architecture approach
- Enable future extensibility (email, audit logs, analytics, etc.)
- Respect the Open/Closed principle

---

## Event Flow

1. Client sends POST `/api/v1/contact/messages`
2. `ContactPublicService` persists the entity
3. A `ContactRequestCreatedEvent` is published
4. A handler listens for the event
5. The notification module reacts (Telegram)
````md
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
TelegramClient
````
---

## Event Structure

All events implement:

```java
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
    String eventType();
    default String correlationId() { return null; }
}
```
## ContactRequestCreatedEvent
Represents the creation of a new contact request.
Contains:
- eventId
- occurredAt
- eventType
- contactRequestId
- email
- subject
- message

---
## Publisher

An abstraction is used:
````java
public interface EventPublisher {
    void publish(DomainEvent event);
}
````

This avoids coupling to Spring directly in the domain layer.

---
## Handler
The event is handled by:
ContactRequestCreatedEventHandler
This handler delegates to the notification service.
