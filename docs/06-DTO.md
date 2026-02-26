# Data Transfer Objects (DTOs)

This document describes the main DTOs (Data Transfer Objects) used in the Contact Service API. DTOs define the structure of data exchanged between the client and the backend, ensuring a clear contract and separation from the internal domain model.

---

## Public DTOs

### CreateContactMessageRequest

Represents the payload sent by the client when submitting a new contact message through the public API.

**Fields:**
- `email` (String): Sender's email address. Required. Must be a valid email format. min-max length: 5-254.
- `subject` (String): Subject of the message. Required. Min length: 3, max length: 120.
- `content` (String): Content of the message. Required. Min length: 10, max length: 4000.

This DTO is used for input validation and does not expose internal or traceability fields.

---

### ContactMessageResponse

Represents the data returned to the client after a contact message is created or when retrieving messages via the API.

**Fields:**
- `id` (UUID): Unique identifier of the message.
- `createdAt` (Instant): Timestamp when the message was created.

---
## Admin DTOs

### SimpleContactMessageAdminResponse

Represents the basic information of a contact message for administrative views.

**Fields:**

- `id` (UUID): Unique identifier of the contact message.
- `createdAt` (LocalDateTime): Timestamp en que se creó el mensaje.
- `email` (String): Dirección de correo electrónico asociada al mensaje de contacto.
- `subject` (String): Asunto del mensaje de contacto.
- `status` (ContactMessageStatus): Estado del mensaje de contacto (por ejemplo, NUEVO, LEÍDO, ARCHIVADO, etc.).
- `sourceIp` (String): Dirección IP de origen desde la que se envió el mensaje de contacto.

### ExtendedContactMessageAdminResponse

Provides extended details of a contact message for comprehensive administrative views.

**Atributos:**
- `id` (UUID): Identificador único del mensaje de contacto.
- `email` (String): Dirección de correo electrónico asociada al mensaje de contacto.
- `subject` (String): Asunto del mensaje de contacto.
- `content` (String): Contenido completo del mensaje de contacto.
- `status` (ContactMessageStatus): Estado del mensaje de contacto.
- `adminNote` (String): Nota administrativa relacionada con el mensaje de contacto.
- `sourceIp` (String): Dirección IP de origen desde la que se envió el mensaje de contacto.
- `userAgent` (String): Cadena User-Agent del cliente que envió el mensaje.
- `createdAt` (LocalDateTime): Marca de tiempo en la que se creó el mensaje.
