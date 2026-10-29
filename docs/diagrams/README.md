# Implemented use-case code diagrams

Open [use-case-code-diagrams.drawio](use-case-code-diagrams.drawio) in diagrams.net. It contains seven editable pages with frontend/API, controller, service, repository, entity/database and response layers. The footnote boxes have been removed. HTTP API consumer boxes identify workflows callable through the backend; dedicated meet/import/notes frontend screens are not yet implemented.

| Use case | Image |
| --- | --- |
| Meet Management | [SVG](01-meet-management.svg) |
| Event Management | [SVG](02-event-management.svg) |
| Athlete Event Participation | [SVG](03-athlete-participation.svg) |
| Results Management | [SVG](04-results-management.svg) |
| Athlete Notes | [SVG](05-athlete-notes.svg) |
| CSV Athlete Import | [SVG](06-athlete-import.svg) |
| Existing Athlete API | [SVG](07-athlete-api.svg) |

The complete backend structure, architecture, API contracts, setup, and operational guide are in [backend/README.md](../../backend/README.md).

See [backend changes and rationale](../backend-changes.md) for behavior, API examples, database setup/deployment instructions, and the supplied spreadsheet's handling rules.

Regenerate from the repository root with `python3 docs/diagrams/generate.py`.
