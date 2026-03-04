# 09 — Testing Strategy

This document describes the testing strategy used in the Contact Service.

The goal is to ensure correctness of the core logic while keeping the test suite fast and maintainable.

The project currently uses two levels of testing:

- Unit Tests
- Integration Tests

---

# 1. Unit Tests

Unit tests verify small pieces of logic in isolation without starting the Spring application context.

These tests are fast and deterministic.

They focus on:

- Mappers
- Domain transformations
- Pure application logic

Unit tests use:

- **JUnit 5**
- **Mockito (when needed)**

Example unit tests implemented:
```
NotificationMessageMapperTest
ContactMessageCreatedEventMapperTest
```
Example execution:
````bash
mvn test
````
These tests do **not require Spring Boot to start**, which makes them extremely fast.

---

# 2. Integration Tests

Integration tests verify that multiple layers of the application work correctly together.

These tests start the Spring Boot context and exercise the application through its HTTP API.

Integration tests validate the full flow:
```
HTTP request
↓
Controller
↓
Application Service
↓
Repository
↓
Domain Event
↓
Event Handler
↓
Notification Service
````

The real Telegram client is **not used** during tests.

Instead we mock the notification channel:
```
@MockitoBean NotificationChannel
````
This allows the system to behave as if notifications were sent while avoiding external API calls.

---

# 3. Example Integration Test

Example class:
```
ContactControllerTest
````
Test scenarios implemented:

### 1. Successful contact request

Verifies that:

- API returns `201 Created`
- Message is persisted in the database
- Notification channel is invoked

### 2. Notification failure

Verifies that:

- API still returns `201 Created`
- Message is persisted in the database
- Notification failure does not break the request flow

This behavior ensures that the notification system is **non-blocking**.

---

# 4. Running the Tests

Run all tests:
```
mvn clean test
````
Run a specific test:
```
mvn -Dtest=NotificationMessageMapperTest test
````
Run the integration test:
```
mvn -Dtest=ContactControllerTest test
````

---

# 5. Future Improvements

Possible improvements to the testing strategy:

- Add **Testcontainers** for PostgreSQL
- Add **integration tests for admin endpoints**
- Add **validation tests for request payloads**
- Add **contract tests for external integrations**

---

# 6. Test Philosophy

The testing philosophy of this project is:

- **Unit tests for pure logic**
- **Integration tests for real application flows**
- **External systems mocked**
- **Fast feedback during development**

This approach provides a good balance between reliability and development speed.
