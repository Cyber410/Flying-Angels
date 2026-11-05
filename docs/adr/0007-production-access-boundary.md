# ADR-0007: Separate anonymous development access from authenticated production access

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

The existing development frontend expects anonymous local requests. A shared deployment must restrict profile/result changes, imports and deletion. Full club account management and login UI are not yet implemented.

## Decision

Default development mode retains anonymous access. The prod profile enables HTTP Basic authentication, ADMIN/COACH/VIEWER authorization rules and CSRF protection for writes. Import routes and deletes require ADMIN. Supply a bootstrap ADMIN through environment configuration; do not commit account credentials. Expose /api/csrf for authenticated clients.

## Alternatives considered

- Keep anonymous writes in shared deployment: minimal setup but no identity or modification control.
- Introduce a complete OAuth/JWT/account platform now: broader capabilities but additional scope and lifecycle requirements.
- Disable CSRF for authenticated Basic requests: simpler scripts but omits the chosen browser request protection.

## Consequences and trade-offs

- A production write requires credentials, sufficient role and CSRF token/session handling.
- Only the bootstrap ADMIN is configured by supplied production properties; COACH/VIEWER identities need future provisioning.
- Development remains intentionally open; the deployment must explicitly select the secured configuration.
- Production requires HTTPS and deliberate frontend CORS/cookie integration.
- Current Compose credentials/networking and request tracing are not addressed merely by enabling security; those operational improvements remain separate work.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/config/SecurityConfig.java](../../backend/src/main/java/com/flyingangels/backend/config/SecurityConfig.java)
- [backend/src/main/resources/application-prod.properties](../../backend/src/main/resources/application-prod.properties)
- [backend/src/test/java/com/flyingangels/backend/config/ProductionSecurityTest.java](../../backend/src/test/java/com/flyingangels/backend/config/ProductionSecurityTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
