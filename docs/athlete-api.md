# Athlete and Meet API

Start with [Backend workflows: which endpoint to use and when](backend-workflows.md) for business-task mapping, preview/commit behavior and real-client CSV testing.

The complete backend architecture, structure, endpoint contracts, payload examples, import rules, setup, security, Hibernate schema management, and testing guide is [backend/README.md](../backend/README.md). The focused explanation of recent changes is in [backend-changes.md](backend-changes.md).

The backend supports:

- Athlete CRUD and profiles with gender, date of birth, team statuses, event/meet context, results, and participation.
- Meet date ranges and meet event retrieval.
- Reusable event categories with measurement units; bulk event creation and selected copying.
- Independent athlete participation and multiple result attempts.
- Timestamped athlete notes with optional event context.
- Header-based CSV/TSV/XLSX parsing, required valid DOB for imported rows, create/update/unchanged matching, blank optional-field preservation, persistent previews, selected-row commit, and outcome summaries. Race results use seconds; jump/throw results use metres.

Import identity rule: known different DOBs represent separate people even when names match. An existing name/DOB match without a supplied registration identifier is blocked for review. See [ADR-0009](adr/0009-name-dob-and-confirmed-identifiers.md).

Editable code diagrams and standalone SVGs are available in [diagrams](diagrams/README.md).

For executable requests and synthetic upload files, use the [Postman test pack](postman/README.md). Design rationale is recorded in the [ADRs](adr/README.md).
