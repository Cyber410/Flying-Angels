# ADR-0010: Deploy main releases to an existing ECS service

- Recorded: 2026-10-29
- Status: Implemented in repository; awaiting team ratification and live deployment verification
- Scope: CI/CD
- Reviewer / approval date: Pending
- Related use case / Trello card / PR: Sprint release deployment; link pending

## Context

GitHub Actions builds, tests and publishes application images to ECR. The team accepts one-time manual AWS infrastructure setup. Subsequent merges into main must deploy automatically to that existing environment. Infrastructure creation and custom AWS orchestration code are outside the requested pipeline scope. The existing RDS database and athlete records must persist.

## Decision

Keep CI and CD in different workflow files. `ci.yml` builds/tests/publishes on pushes to development and main. `ce.yaml` uses workflow_run to deploy only after successful CI from a same-repository push to main, gated by AWS_DEPLOY_ENABLED=true. CD downloads the release manifest from the exact CI run/attempt and verifies its commit and digest references. The CD workflow must exist on the repository default branch. Read the task revision attached to the configured existing ECS service, use the official AWS render actions to replace the backend/frontend image references with that run's immutable digests, and use the official AWS deploy action to register/update/wait for stability.

Verify that the expected task revision remains the primary completed deployment so a rollback is not reported as a successful release. Preserve roles, environment, secret references, health checks, logging, networking and service desired count. Do not create infrastructure, start/stop RDS, import data, or automatically merge PRs.

## Alternatives considered

- Repeated manual task-definition updates: rejected because the requested release should deploy automatically after merging into main.
- CloudFormation provisioning and custom Python deployment/start/stop scripts: unnecessary for the clarified requirement; the team accepts configuring infrastructure once.
- Deploy every development push: not selected; development publishes images, and main is the release boundary.
- Deploy a latest task-definition-family revision: not selected; reading the revision attached to the target service avoids unintentionally copying an unrelated un-deployed revision.

## Consequences and trade-offs

- Three deployment repository variables are needed: AWS_DEPLOY_ENABLED, ECS_CLUSTER and ECS_SERVICE. Existing publishing credentials/configuration are reused.
- Temporary lab credentials still require renewal. A running deployment requires RDS to be available; presentation start/stop remains an operational action.
- The existing Build, test and verify containers required-check name remains unchanged.
- CD does not implement frontend login/CSRF flows, HTTPS, production account management or database migration tooling.
- ECS circuit breaker/rollback should be enabled in the one-time setup. Database-affecting changes still require release review.

## Verification

Actionlint validates the workflow. Event conditions are checked for pull requests, development pushes, main pushes, manual publishing and disabled deployment. Local container tests have verified the existing production environment names, TLS database connection and authentication behavior. Live AWS registration/update/wait permissions and service startup require the configured GitHub main release run; local checks do not claim that deployment has occurred.

See [ECS continuous delivery](../aws-deployment.md) for setup and operations.

## Supersession

No prior ADR is superseded. ADR-0006 and ADR-0007 remain applicable.
