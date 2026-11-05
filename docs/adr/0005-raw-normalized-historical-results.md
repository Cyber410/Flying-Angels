# ADR-0005: Preserve raw performance values alongside normalized measurements

- Recorded: 2026-10-14
- Status: **Implemented; awaiting team ratification**
- Scope: Backend
- Approver: Not yet recorded; review with the team before marking Accepted.

## Context

The spreadsheet mixes values such as 2:44.07, 5.18m, NT and qualifier-suffixed or malformed text. The club has not confirmed which source values are seeds, official results or eligible personal bests. Converting or labeling them without context could misrepresent the source.

## Decision

Store trimmed raw result text separately from normalized decimal score, unit, parsing status and provenance. Normalize known track/field/point formats. Preserve unrecognized historical values as UNPARSED with no comparison score. Keep roster performances event-less rather than assigning an invented meet. An event leaderboard uses one best comparable attempt per athlete; it does not establish official personal-best or season-ranking eligibility.

## Alternatives considered

- Only store a number: easy comparison but loses qualifiers, formatting and evidence of malformed input.
- Only store text: preserves input but cannot compare track/field values consistently.
- Automatically treat all imported performances as official meet results: fills missing context by assumption.

## Consequences and trade-offs

- The original file and raw values remain available as evidence while recognized values support comparison.
- VALID describes parsing, not external verification.
- Qualifier suffix meanings, Excel serial/time conversion, seasons, age groups, wind and indoor/outdoor distinctions need approved business rules.
- Legacy and central storage coexist; profile mapping suppresses matching display copies without deleting their underlying data.
- Identical raw category/value imports without meet identity cannot distinguish separate competitions with the same performance.

## Implementation and verification evidence

- [backend/src/main/java/com/flyingangels/backend/event/ResultValues.java](../../backend/src/main/java/com/flyingangels/backend/event/ResultValues.java)
- [backend/src/main/java/com/flyingangels/backend/event/EventService.java](../../backend/src/main/java/com/flyingangels/backend/event/EventService.java)
- [backend/src/main/java/com/flyingangels/backend/athlete/dto/AthleteProfileResponse.java](../../backend/src/main/java/com/flyingangels/backend/athlete/dto/AthleteProfileResponse.java)
- [backend/src/test/java/com/flyingangels/backend/event/ResultValuesTest.java](../../backend/src/test/java/com/flyingangels/backend/event/ResultValuesTest.java)

## Review and supersession

Record the team's reviewer, review date and linked PR when ratified. If the decision changes after acceptance, add a new ADR and mark this one Superseded with a link; retain its history. These records document the implementation and do not claim an earlier team approval.
