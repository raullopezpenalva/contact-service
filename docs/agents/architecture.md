# Architecture guide for Codex

## Existing repository map

Java sources live under
`src/main/java/com/raullopezpenalva/contact_service/`:

| Path | Responsibility |
| --- | --- |
| `modules/contact/api` | Public/admin controllers, DTOs, API error responses |
| `modules/contact/application` | Contact use cases, mapping, client context and application exceptions |
| `modules/contact/domain` | Contact message model, status, and contact-owned domain events |
| `modules/contact/infrastructure` | Repository, security configuration and supporting adapters |
| `modules/platform/notification` | Notification orchestration, domain delivery state, ports, configuration and channel/persistence adapters |
| `modules/platform/websecurity` | Request quotas, body limits, proxy trust, security logging and managed administrator identities; login/JWT remains planned in #45–#48 |
| `shared/events` | Shared event abstraction and Spring event publication configuration |

Tests mirror these concerns under `src/test/java`; existing API integration tests
also live in the root application package. Preserve these conventions instead of
reorganizing unrelated files as part of a feature.

This is the current implementation, not a claim of strict framework-independent
domain layers: for example, ContactMessage currently carries JPA and validation
annotations. Do not silently rewrite it to fit an idealized architecture.

## Module interaction to preserve

ContactPublicService validates through the API DTO, maps/persists a ContactMessage,
and publishes ContactRequestCreatedEvent through the shared EventPublisher port.
The notification module owns an asynchronous Spring event handler that resolves
configured channels, maps the event, creates delivery records, and delegates to
NotificationService and channel gateways. Email and Telegram are infrastructure
adapters; the contact module must not start calling them directly. Delivery
failures are recorded without breaking the accepted public request flow.

The maintainer-approved #26 scheduler stays inside the notification module and
reuses failed delivery records through NotificationService. See
[notification retries](../12-Notification-retries.md) for snapshot compatibility,
configuration and single-instance limits. This is not a distributed queue.

Preserve the current transaction, asynchronous publication/listener semantics and
existing event payload contract unless a change is explicitly studied. Do not
claim after-commit delivery, durable event transport, retries, or distributed
delivery guarantees that the existing implementation does not provide.

Keep public/admin DTOs separate and API documentation aligned with controller
mappings. Preserve authorization, input validation and abuse controls when
extending endpoints. Keep notification ports/adapters within the existing module;
do not introduce cross-module repository access as an implementation shortcut.

## Discuss structural decisions before implementing

Prior study with the maintainer is required to:

- Add/remove a module or move responsibilities/packages across existing layers.
- Change how modules communicate, event ownership/contracts, or synchronous versus
  asynchronous boundaries.
- Introduce a broker, outbox, scheduler-based interaction, new persistence boundary,
  or a replacement architecture.

First inspect the affected flow and prepare a short proposal in the issue/PR:
the problem, current behavior, alternatives, recommended change, compatibility and
data impact, and validation plan. Obtain the maintainer's decision before making
that structural change. A generic request to implement a feature is not approval
for an unmentioned architectural redesign. Routine methods, DTOs, adapters and
tests inside existing responsibilities can proceed autonomously.

## Relevant references

The maintainer reaffirmed this ownership on 2026-10-30. Keep current users/JWT
work in platform; the [future centralized identity idea](../security/003-future-centralized-identity.md)
does not authorize a module extraction or change the current implementation plan.

- [Domain model](../04-Domain.md)
- [API flows](../05-Flows.md)
- [DTOs](../06-DTO.md)
- [Events](../07-Events.md)
- [Notification module](../08-Notification.md)
- [Tests and known limitations](../09-Tests.md)
- [Security and deployment assumptions](../10-Security-hardening.md)

When documentation and code differ, investigate the intended contract and correct
the affected sources together; do not treat old prose as proof of runtime behavior.
