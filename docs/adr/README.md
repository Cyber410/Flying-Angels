# Architecture Decision Records

ADRs explain **why** a design was chosen, which alternatives were considered, and which trade-offs remain. The backend README explains **how** the system works; ADRs preserve the decision history.

These records describe the current implementation. Their status is **Implemented; awaiting team ratification**, because no team approval has been recorded in this session. Review them together, add the reviewer/date/PR, and mark Accepted only when the team agrees.

| ADR | Decision |
| --- | --- |
| [ADR-0001](0001-modular-monolith.md) | Use a modular monolith with feature packages |
| [ADR-0002](0002-event-definition-participation-results.md) | Separate definitions, meet events, participation and performance attempts |
| [ADR-0003](0003-single-import-module-and-upserts.md) | Use one import module with create, update and unchanged behavior |
| [ADR-0004](0004-persisted-previews-row-transactions.md) | Persist import previews and commit selected rows independently |
| [ADR-0005](0005-raw-normalized-historical-results.md) | Preserve raw performance values alongside normalized measurements |
| [ADR-0006](0006-versioned-schema-migrations.md) | Manage schema evolution with Flyway and Hibernate validation |
| [ADR-0007](0007-production-access-boundary.md) | Separate anonymous development access from authenticated production access |

## Workflow

1. Draft an ADR for a consequential design choice, not every routine code edit.
2. Link it from the related Trello card and pull request.
3. Discuss the context, alternatives and consequences with the team.
4. Record acceptance/rejection and reviewer/date; do not invent retrospective approvals.
5. When an accepted choice changes, add a replacement ADR and link both records.
6. In a sprint presentation, explain one decision using its problem, choice, trade-off and test evidence.

Use [template.md](template.md) for the next record. Docker Secrets, network isolation, structured logging/tracing and operational recovery need their own concrete proposals/implementation before being marked implemented.
