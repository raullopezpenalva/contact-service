# Data Transfer Objects (DTOs)

This document describes the main DTOs (Data Transfer Objects) used in the Contact Service API. DTOs define the structure of data exchanged between the client and the backend, ensuring a clear contract and separation from the internal domain model.

---

## CreateContactMessageRequest

Represents the payload sent by the client when submitting a new contact message through the public API.

**Fields:**
- `email` (String): Sender's email address. Required. Must be a valid email format. min-max length: 5-254.
- `subject` (String): Subject of the message. Required. Min length: 3, max length: 120.
- `content` (String): Content of the message. Required. Min length: 10, max length: 4000.

This DTO is used for input validation and does not expose internal or traceability fields.

---

## ContactMessageResponse

Represents the data returned to the client after a contact message is created or when retrieving messages via the API.

**Fields:**
- `id` (UUID): Unique identifier of the message.
- `createdAt` (Instant): Timestamp when the message was created.

---
