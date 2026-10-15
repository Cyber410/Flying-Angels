# ADR-0004: Persist import previews and commit selected rows independently

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

Users must review and select source rows, understand outcomes and safely retry requests. A whole-file failure should not discard other successfully imported records, but a partially saved athlete/result row must not be reported as successful.

## Decision

Store the original file in ImportJob and row classifications/outcomes in ImportRow. Commit selected NEW/UPDATE rows in separate REQUIRES_NEW transactions through ImportRowWriter. Lock the preview row and re-evaluate current identity/differences. Write FAILED in a separate transaction after a caught write failure. Do not reapply rows already IMPORTED/UPDATED. Immediate upload is an adapter over the same preview/commit path.

## Alternatives considered

- One transaction for all selected rows: clean all-or-nothing behavior, but one failure discards every valid row.
- Uncoordinated repository saves without a row transaction: permits partial athlete/result writes.
- An in-memory preview only: simpler storage but loses selection/outcome state across restarts.

## Consequences and trade-offs

- Successful rows survive another row's failure while each row remains internally atomic.
- Summary counts are job-wide/cumulative, not just the current request's inserted rows.
- The separate writer bean is required for Spring transaction-proxy semantics; self-invocation would not provide the same boundary.
- Files are reparsed under current parser rules; parsed DTOs are not immutable snapshots.
- Jobs/source bytes currently have no download, expiry or cleanup endpoint.
- Locks stop reapplying one preview row; demographic identity matching across different jobs can still race without a stable unique identifier.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/importing/ImportJob.java](../../backend/src/main/java/com/flyingangels/backend/importing/ImportJob.java)
- [backend/src/main/java/com/flyingangels/backend/importing/ImportRowWriter.java](../../backend/src/main/java/com/flyingangels/backend/importing/ImportRowWriter.java)
- [backend/src/main/java/com/flyingangels/backend/importing/ImportRowRepository.java](../../backend/src/main/java/com/flyingangels/backend/importing/ImportRowRepository.java)
- [backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java)
- [backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
