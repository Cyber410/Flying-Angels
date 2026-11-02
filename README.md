# Flying Angels Management System

For the implemented backend's architecture, project structure, API reference, database design, setup, and testing, see the [complete backend guide](backend/README.md).

For which endpoint to use and when, including preview/commit behavior with the real client CSV, start with [Backend workflows](docs/backend-workflows.md).

Backend review assets: [Postman tests](docs/postman/README.md) and [Architecture Decision Records](docs/adr/README.md).

## Overview
The Flying Angels Management System is a centralized platform designed to support the operations of Flying Angels, a youth sports development organization serving athletes and families across the GTA and Southwestern Ontario.

The system will support both the **Network Level** and **Local Chapter Level** operations by streamlining athlete registration, program management, coach scheduling, communication, fee collection, and reporting.

## Repository Rules and Workflow

### Branching Strategy
We will use a structured Git workflow to keep the repository organized and safe for collaboration.

- `main` is the production-ready branch.
- `development` is the main integration branch for active work.
- Feature branches are created from `development`, not directly from `main`.
- A release branch or direct release merge from `development` to `main` is used when a version is ready for production.

Recommended branch naming convention:
- `feature/<short-description>`
- `bugfix/<short-description>`
- `hotfix/<short-description>`
- `release/<version>`

### Rules for Working in the Repo
1. Never push directly to `main` or `development`.
2. Create a feature branch for every task or story.
3. Work only on the feature branch related to your task.
4. Open a Pull Request (PR) before merging code.
5. At least one code review is required before merge.
6. GitHub Actions checks must pass before the PR can be merged.
7. Merge into `development` only after review and successful checks.
8. Only merge from `development` into `main` during a release-ready milestone.
9. Keep commits clear, small, and focused.
10. Delete feature branches after they are merged.

### Pull Request Process
- Create a feature branch from `development`.
- Commit changes with clear messages.
- Push the branch to GitHub.
- Open a Pull Request targeting `development`.
- Request review from a teammate.
- Ensure all required GitHub Actions checks pass.
- Merge only after approval and validation.

### Code Review Expectations
- Review for correctness, readability, and maintainability.
- Check for testing coverage and potential edge cases.
- Comment respectfully and provide clear suggestions.
- Do not approve code that has failing checks or unclear logic.

### GitHub Actions Checks
All PRs must pass the required workflow checks before merging, including:
- linting
- unit tests
- build validation
- any other project quality gates defined for the repo

### GitHub Actions CI/CD Setup
The `CI and ECR publishing` workflow builds the application, runs unit tests, builds runtime images, starts an isolated Docker Compose stack, and runs database and HTTP integration tests. After successful checks, pushes to `development` or `main` automatically publish the tested images to Amazon ECR. Pull requests run checks only. Sprint work merges into `development`; release-ready work merges into `main` at the end of the sprint.

Follow [ECR publishing instructions](docs/ecr-publishing.md) for the exact GitHub secrets and variables, first-run steps, image references, and troubleshooting. ECS deployment is a later setup step; this workflow does not start AWS compute.

#### Required checks for every pull request
For the frontend (React + TypeScript):
- install dependencies
- run linting
- run the TypeScript compiler check
- run unit tests
- run production build

For the backend (Spring Boot):
- set up Java
- run unit/integration tests
- run the Maven or Gradle build
- fail the check if the project does not compile

The workflow should run on:
- pull requests targeting `main` and `development`
- pushes to `main` and `development`, automatically publishing images after successful checks
- manual runs, with optional ECR publishing on `development` or `main`

#### Merge protection rule
To make sure failed checks block merges:
1. Go to the GitHub repository.
2. Open Settings > Branches.
3. Add a branch protection rule for `main`.
4. Add another rule for `development`.
5. Enable:
   - Require a pull request before merging
   - Require at least 1 approval
   - Require status checks to pass before merging
   - Select `Build, test and verify containers` after its first run; remove requirements for the old frontend/backend checks
   - Require branches to be up to date before merging
   - Optionally require conversation resolution

This ensures that if a CI check fails, the merge button is disabled until the problem is fixed.

#### CI workflow structure
- One required job runs frontend and backend checks before building images.
- Backend unit and database integration tests run in separate Maven profiles.
- HTTP integration checks exercise the built frontend, Nginx, backend, and disposable MySQL database.
- Logs and backend test reports are uploaded even if checks fail; the isolated Compose stack is always removed.

#### Deployment step
Automatic publishing on pushes to `development` and `main` sends versioned images to the existing private ECR repositories after every check passes. Manual publishing remains available for reruns. The run summary and `published-images.json` artifact provide image digest references for configuring ECS/Fargate. Automatic ECS updates are not part of this workflow yet.

## Testing Strategy and File Structure
The MVP is complete only after Flying Angels approves it.

### Test Folder Structure
Unit and database integration tests live with their applications. The root `tests/` folder contains project-level QA assets and `tests/integration/test_deployed_stack.py`, which CI runs against the built Docker images.

```text
tests/
  acceptance/
    sprint-checklist.md
    regression-checklist.md
    uat-checklist.md
  manual/
    login-flow.md
    athlete-import-flow.md
    pb-ranking-check.md
  fixtures/
    sample-athletes.csv
    sample-results.csv
    invalid-csv-example.csv
  reports/
    sprint-summary.md
```

The actual automated tests for the app should live inside the application folders:

```text
frontend/
  src/
    __tests__/
      auth/
      athletes/
      rankings/
      import/
    components/
    features/
    pages/
    services/
    utils/

backend/
  src/
    test/
      java/
        com/flyingangels/
          controller/
          service/
          repository/
          integration/
```

### What goes in the root test folder
The root `tests/` folder should contain:
- acceptance test checklists
- sprint validation files
- regression testing notes
- manual QA scripts
- CSV test fixtures for imports
- sample payloads and datasets
- release verification documentation
- non-code QA records for Flying Angels sign-off

This folder includes human-facing QA documentation and automated deployed-stack checks under `tests/integration/`.

### What goes in frontend test folders
Frontend test files should live alongside the feature or component they validate.

Examples:
- `src/__tests__/auth/LoginForm.test.tsx`
- `src/__tests__/athletes/AthleteForm.test.tsx`
- `src/__tests__/rankings/RankingTable.test.tsx`
- `src/__tests__/import/CsvImport.test.tsx`

These tests should cover:
- login validation
- athlete create/update flows
- search behavior
- PB and ranking logic
- CSV parsing and validation
- form validation feedback
- accessibility checks

### What goes in backend test folders
Backend tests should live under the Spring Boot test package and be grouped by layer.

Examples:
- `src/test/java/com/flyingangels/service/AthleteServiceTest.java`
- `src/test/java/com/flyingangels/controller/AthleteControllerTest.java`
- `src/test/java/com/flyingangels/repository/AthleteRepositoryTest.java`
- `src/test/java/com/flyingangels/integration/CsvImportIntegrationTest.java`

These tests should cover:
- service validation and business rules
- repository persistence and query behavior
- controller request/response handling
- CSV import processing
- PB and rankings calculation
- invalid data rejection and error handling

### CI runs automated application and deployed-stack tests
GitHub Actions executes the tests in the application folders and the HTTP checks in `tests/integration/`.

This means:
- frontend checks run lint, type checks, build and unit tests
- backend checks run build, unit tests and database integration tests
- deployed-stack checks validate runtime images through Nginx using an isolated Compose database

This separation keeps development clean and ensures the repo has both automated engineering validation and project-level QA tracking.

### Testing Levels

#### Unit testing
Unit tests validate the smallest logic units in isolation.

Frontend unit tests should cover:
- login validation and password checks
- athlete create and update logic
- athlete search filtering
- PB calculation logic
- ranking calculations
- CSV validation logic in forms or uploaded data parsers
- edge cases and negative inputs

Backend unit tests should cover:
- service layer validation for athlete registration
- password validation rules
- ranking and PB calculations
- CSV import parsing and data validation
- repository query behavior in isolation
- error handling and validation exceptions

Example frontend test cases:
- invalid login with wrong credentials
- duplicate athlete entry detection
- club name and athlete ID validation
- PB calculation after import

Example backend test cases:
- create athlete with valid data
- reject athlete with missing required fields
- calculate PB after result insertion
- reject invalid CSV row format

#### Integration testing
Integration tests validate that React, Spring Boot, and MySQL work together correctly.

Examples:
- save athlete from frontend and verify record persists in database
- import athlete results and confirm PB updates
- retrieve athlete profile and history from API
- search athlete records and verify ranking response
- validate end-to-end data flow from form submission to database and UI refresh

#### System testing
System tests validate the complete workflow from user interaction to backend processing.

Examples:
- login and search athlete
- import results file
- confirm PB and ranking recomputation
- view athlete history and ranking results
- verify full workflow executes without broken state

#### Acceptance testing
Acceptance tests ensure the product matches Flying Angels business requirements.

Examples:
- athletes are correctly created and updated
- ranking values match approved calculations
- imports load expected records
- PB values are accurate after each submission
- user flows match operational expectations from the organization

#### Negative testing
Negative tests confirm the system handles invalid data and security issues safely.

Examples:
- wrong password
- missing required fields
- invalid CSV format
- duplicate athlete records
- unauthorized access attempts
- malformed API payloads

#### Regression testing
Regression tests ensure previously working features still work after changes.

Examples:
- athlete profiles still display correctly
- history remains accurate
- PB and rankings remain valid after updates
- sprint review features remain working after code changes

#### Accessibility testing
Accessibility testing ensures the UI is usable for all users.

Examples:
- form labels are present and readable
- keyboard navigation works correctly
- focus states are visible
- color contrast is usable
- error messages are accessible and understandable
- screen-reader-friendly controls and labels are present

### Frontend Testing Standards
Frontend tests should be written with:
- React Testing Library
- Vitest or Jest
- user behavior testing rather than implementation details

Recommended frontend test naming:
- `LoginForm.test.tsx`
- `AthleteForm.test.tsx`
- `RankingTable.test.tsx`
- `CsvImport.test.tsx`

Important frontend checks:
- validate user inputs before submit
- verify API error handling
- assert displayed messages for success and failure
- validate accessibility attributes on forms and buttons

Example commands:
```bash
cd frontend
npm install
npm run test -- --run
npm run lint
npm run typecheck
npm run build
```

### Backend Testing Standards
Backend tests should be written with:
- JUnit 5
- Spring Boot Test
- Mockito for mocking dependencies

Recommended backend test naming:
- `AthleteServiceTest.java`
- `AthleteControllerTest.java`
- `CsvImportServiceTest.java`
- `RankingServiceTest.java`
- `AthleteIntegrationTest.java`

Important backend checks:
- service validation logic
- repository queries and persistence
- exception handling
- transaction behavior
- API response validation
- CSV import correctness

Example commands:
```bash
cd backend
./mvnw test
# or
./gradlew test
./mvnw package
# or
./gradlew build
```

### CI Enforcement for Testing
The GitHub Actions workflow must treat the following as required checks:
- frontend lint
- frontend typecheck
- frontend unit tests
- frontend build
- backend tests
- backend build

If any required check fails, the pull request cannot be merged.

### Release Workflow
1. Merge completed features into `development`.
2. Validate the integration branch.
3. Prepare the release branch or release merge.
4. Merge the release into `main`.
5. Tag the release version if needed.
6. Keep `development` updated with any production fixes.

### Recommended Default Setup
For this project, the best practice is:
- create `main` first
- create `development` from `main`
- create all feature branches from `development`

This keeps the main branch stable while allowing the team to integrate new work safely before release.
