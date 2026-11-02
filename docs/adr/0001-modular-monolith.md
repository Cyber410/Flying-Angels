# ADR-0001: Use a modular monolith with feature packages

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

The project has one React client, one Spring Boot backend and one MySQL database. Athlete, meet, event and import workflows share data and need coordinated transactions. The team also needs a structure that is easy to explain, integrate and deploy during sprints.

## Decision

Keep one deployable Spring Boot application organized into athlete, meet, event, note and importing feature packages. Controllers handle HTTP, services own workflow rules/transactions, repositories own queries, and DTOs define the external contract. Use constructor injection. Do not introduce separate deployed services for the current feature set.

## Alternatives considered

- Package everything by technical layer: simple initially, but separates the files changed together for one feature.
- Microservices: independent deployment boundaries, but additional networking, data ownership and distributed-workflow coordination.

## Consequences and trade-offs

- A single deployment and database make cross-feature transactions straightforward.
- Feature packages give team members a practical ownership boundary, but integration remains a shared responsibility.
- The current event service spans definitions, participation and results; it can be split internally if complexity warrants it.
- Scaling and deployment remain application-wide; package boundaries are not runtime isolation.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/BackendApplication.java](../../backend/src/main/java/com/flyingangels/backend/BackendApplication.java)
- [backend/src/main/java/com/flyingangels/backend/athlete/AthleteController.java](../../backend/src/main/java/com/flyingangels/backend/athlete/AthleteController.java)
- [backend/src/main/java/com/flyingangels/backend/athlete/AthleteService.java](../../backend/src/main/java/com/flyingangels/backend/athlete/AthleteService.java)
- [backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
