# ADR-0002: Separate definitions, meet events, participation and performance attempts

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

Reusable events such as 100m must appear in multiple meets. An athlete can enter an event before recording a performance and can produce multiple attempts. The old result entity also represented assignment, causing a null-result failure and a uniqueness constraint that prevented performance history.

## Decision

Use EventCategory for reusable definitions, Event for an instance inside a Meet, EventParticipation for the unique athlete/event assignment, and EventResult for independently identified attempts. Keep uniqueness on (meet_id, category_id) and on participation(event_id, athlete_id). Permit several results for the same athlete/event. Copy only event metadata/definitions between meets.

## Alternatives considered

- One result row per athlete/event serving as participation: simpler table count, but requires placeholder results and overwrites earlier performances.
- A separate event-definition copy per meet: avoids shared references, but makes definition reuse and consistent units harder.

## Consequences and trade-offs

- Assignments do not require a fake performance and deleting an attempt leaves participation intact.
- Results currently link through event and athlete IDs, not a participation_id foreign key; services maintain their relationship.
- Removing participation deliberately removes that athlete's event attempts. Deleting meets/events also removes child results.
- One category per meet does not model heats/rounds/divisions; extend identity and constraints before introducing those features.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/event/EventParticipation.java](../../backend/src/main/java/com/flyingangels/backend/event/EventParticipation.java)
- [backend/src/main/java/com/flyingangels/backend/event/EventResult.java](../../backend/src/main/java/com/flyingangels/backend/event/EventResult.java)
- [backend/src/main/java/com/flyingangels/backend/event/EventService.java](../../backend/src/main/java/com/flyingangels/backend/event/EventService.java)
- [backend/src/main/java/com/flyingangels/backend/event/Event.java](../../backend/src/main/java/com/flyingangels/backend/event/Event.java)
- [backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java](../../backend/src/test/java/com/flyingangels/backend/ManagementIntegrationTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
