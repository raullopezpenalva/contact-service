# MVP Stack

- Runtime: Java + Spring Boot
- API: REST JSON
- Persistence: Spring Data JPA + PostgreSQL
- Database: PostgreSQL (Docker)
- Notification: Telegram(MVPv1.0.0) and Email SMTP (Spring Mail)(on MVPv1.2.0)
- Admin Auth: Managed accounts + short-lived RS256 JWT Bearer (Spring Security), persistent sessions, explicit ADMIN authorization
- Local Infrastructure: Docker Compose (backend + postgres)