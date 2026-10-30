# Future centralized administrative identity

**Recorded:** 2026-10-30. **Status:** future-release idea, not an implementation
or deployment decision. Documentation tracking: #55.

## Current decision reaffirmed

Continue the [approved users/JWT design](001-admin-users-jwt.md) and its
[API contract](002-admin-auth-contract.md), including issues #45–#48, within
`modules/platform/websecurity`. Keep contact's responsibilities and existing
module communication. Do not extract separate user/auth modules, create another
identity service or redirect current implementation toward infrastructure SSO.
The future idea neither blocks nor replaces the approved security work.

## Future idea

The maintainer wants to study one administrative identity provider for human
access to internal LAN tools, observability/CI interfaces and administration of
personal DMZ/edge applications. Each application would retain distinct clients,
token audiences and authorization; one account must not imply global privileges.
A separate identity service/domain would serve future external/customer users.
Machine/service credentials remain separate from human SSO.

[Homelab #43](https://github.com/raullopezpenalva/homelab/issues/43) owns the
technical proposal. `lan_services` is only a candidate host; no VM, product,
network rule or integration is selected. The global idea is tracked in
[knowledge-system #15](https://github.com/raullopezpenalva/knowledge-system/issues/15).

A future release could integrate an established OpenID Connect provider after a
separate design/migration decision. Contact-service is not being built as the
identity provider for the infrastructure. Existing account/data relationships,
roles, token validation, sessions/revocation, key custody, availability and
rollback would need review before changing the current local implementation.
Do not share password tables or signing secrets across applications as an SSO
shortcut, or assume that external tokens retain the current revocation behavior.

This record changes no dependencies, package boundaries, tables, APIs, token
policy, credentials or runtime configuration. No release or production change is
authorized. Technical Production App keeps its own users/auth modules and web
session/cookie/CSRF design; this idea does not migrate that application to JWT.
