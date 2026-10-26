# ADR-0009: Separate different birth dates and require identifiers for demographic matches

- Recorded: 2026-10-15
- Status: **Implemented; awaiting team ratification**
- Scope: Backend athlete-import identity matching
- Supersedes: ADR-0003's automatic unique-name and gender tie-breaking policy; ADR-0008's required-DOB rule remains in effect.
- Basis: The project owner specified that different DOBs represent different people and confirmed that even one existing name/DOB match must be blocked until a registration identifier is supplied.
- Approver: Team ratification has not yet been recorded.

## Context

Automatically updating a unique name match could change a different person's DOB. The previous multiple-name rule also classified a third, different DOB as ambiguous rather than creating a separate person. Gender-based narrowing could silently select one of several records sharing name and DOB. The client CSV currently has no MEMBER # column, so the behavior of unkeyed re-imports must be explicit.

## Decision

1. Require a valid incoming DOB under [ADR-0008](0008-required-import-birth-dates.md).
2. Match an exact source/externalId first. A confirmed identifier can support name/DOB corrections.
3. Otherwise, compare first/last name case-insensitively and require exact DOB equality. Known different DOBs represent different people: create a NEW athlete, including when only one existing athlete shares the name.
4. A name/DOB match without a supplied identifier is AMBIGUOUS and cannot be committed, even if there is only one match or no profile changes. Gender does not bypass review.
5. A supplied new identifier can attach to one unkeyed exact name/DOB match. Multiple eligible exact matches still require resolution; do not choose arbitrarily. Explicitly different existing identifiers remain distinct identities.
6. An existing same-name record with unknown DOB cannot be identified from name alone. Require its registered identity rather than guessing a match or silently duplicating it.
7. Recheck these rules during eligible-row commit and use them in immediate uploads too.

## Alternatives considered

- Update a unique name match regardless of DOB: risks overwriting a different person.
- Automatically update a unique name/DOB match: rejected by the owner's explicit request to require an identifier first.
- Use gender to resolve equal name/DOB records: does not satisfy the required review boundary.
- Mark all shared names ambiguous regardless of DOB: prevents legitimate different-DOB registrations.

## Consequences and trade-offs

- Initial unkeyed registrations can create athletes when no conflicting demographic/unknown-DOB identity exists.
- Repeating that CSV without identifiers now requires review rather than automatically updating or skipping matching demographics.
- Administrators must obtain a real identifier, add it to the source and create a new preview. Do not invent identifiers to bypass matching.
- With a confirmed identity, UPDATE and UNCHANGED retain their normal behavior; blank optional fields and performance history remain preserved.
- An unkeyed DOB correction needs identity confirmation. A genuinely different DOB creates a separate athlete; it no longer changes an existing athlete by name alone.
- No schema change, automatic merge, or deletion of existing athletes is required.

## Implementation and verification evidence

- [AthleteImportMatcher](../../backend/src/main/java/com/flyingangels/backend/importing/AthleteImportMatcher.java)
- [AthleteImportUpdateIntegrationTest](../../backend/src/test/java/com/flyingangels/backend/importing/AthleteImportUpdateIntegrationTest.java)
- [Postman identity workflows](../postman/README.md#identity-matching-walkthrough)

## Review and supersession

Record team review/date and linked PR when ratified. Preserve this record and link a replacement ADR if matching policy changes again.
