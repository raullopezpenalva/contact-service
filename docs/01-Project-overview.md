
## 1- Purpose of the Service

The Contact Backend is a microservice responsible for:
- Receiving messages sent from the public form on the personal website.
- Persisting those messages securely.
- Notifying the website owner when a new message is received.
- Allowing basic querying and management of those messages through protected administrative access.
This service is part of the backend ecosystem of the personal website and serves both a professional demonstrative (portfolio) and real functional purpose.

## 2- Problem Solved

Currently:
- The form could send an email directly from the frontend.
- There would be no persistence.
- There would be no control or traceability.
- There would be no security layer or robust validation.
With this service:
- Messages are stored.
- Traceability exists.
- Messages can be managed later.
- Real backend architecture is demonstrated.

## 3- Functional Scope (Future Vision)

The service, in its vision, could include:
- Advanced message management.
- Automatic classification (spam/priority).
- Attachments.
- Frontend admin panel.
- Advanced rate limiting.
- Metrics and observability.
- Integration with newsletter or CRM.
- Webhooks
- Event-driven architecture.
