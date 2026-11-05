# Continuous delivery to an existing ECS service

Create AWS infrastructure once. After that, merge application changes into `main` and GitHub Actions updates the existing ECS service automatically. The pipeline does not merge pull requests, create infrastructure, recreate the database, or import CSV data.

| Event | Behavior |
| --- | --- |
| PR into `development` or `main` | Build and test only |
| Merge/push to `development` | Build, test and publish images to ECR |
| Merge/push to `main` | Build, test, publish images, then deploy to ECS when enabled |
| Manual run with publishing selected | Build, test and publish; does not deploy ECS |

The workflows are separate:

- `.github/workflows/ci.yml` — **CI and ECR publishing**: build, test, container verification and image publishing.
- `.github/workflows/ce.yaml` — **CD - Deploy to ECS**: deploy the exact successful main CI release to the existing service.

Keep the required PR status check **Build, test and verify containers**. CD uses a `workflow_run` completion trigger and accepts only successful CI runs from a push to `main` in this same repository. It does not run for failed checks, PRs, development pushes or manual publishing.

## One-time AWS setup

Your existing RDS MySQL database, password secret and ECR repositories are reused. In the same region/account, finish the following once:

1. **Networking:** use the same VPC as RDS. Create an application security group allowing HTTP/80 from your public IP/32, and configure the database's security group to allow MySQL/3306 from the application group. The ECS task needs a public subnet with an internet-gateway route and an assigned public IP for this low-cost setup without a NAT Gateway.
2. **Logs:** create `/ecs/flying-angels` in CloudWatch Logs with seven-day retention.
3. **Application administrator password:** store a separate plaintext password in Secrets Manager, e.g. `flying-angels/admin-password`. The database secret must also contain only the database password, not an entire JSON object.
4. **Cluster:** create an ECS Fargate cluster, e.g. `flying-angels-lab`.
5. **Task definition:** create a Linux/X86_64 task, e.g. family `flying-angels-app`, with 0.5 vCPU and 2 GiB memory. Select existing **LabRole** for both task and execution roles. Add the two containers below using the already published image digests from GitHub's run summary.
6. **Service:** create an existing rolling-update **ECS** service, e.g. `flying-angels-app`, using this task definition and the networking above. Enable the deployment circuit breaker and rollback. Start with one desired task for initial verification while RDS is available. No load balancer is required for this initial IP-restricted setup.

Backend container:

| Setting | Value |
| --- | --- |
| Container name | `backend` (exactly) |
| Image | Published backend digest reference |
| Port | 8080 |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:mysql://YOUR_RDS_ENDPOINT:3306/flying_angels?sslMode=REQUIRED&serverTimezone=UTC` |
| `DATABASE_USERNAME` | Your configured database user |
| `APP_ADMIN_USERNAME` | `admin` or your chosen username |
| `DATABASE_PASSWORD` | Secret reference / ValueFrom: database password secret ARN |
| `APP_ADMIN_PASSWORD` | Secret reference / ValueFrom: administrator password secret ARN |
| Logging | `awslogs`, existing group `/ecs/flying-angels`, correct region, stream prefix `backend` |

Use your actual SQL database name in the URL. Define an ECS container health check; ECS does not automatically use the Dockerfile's health check. The backend health-check command array is:

```json
["CMD-SHELL", "bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'"]
```

Use interval 10 seconds, timeout 5 seconds, retries 5, and start period 120 seconds. If the console uses a comma-separated command field, enter `CMD-SHELL, bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'`.

Frontend container:

| Setting | Value |
| --- | --- |
| Container name | `frontend` (exactly) |
| Image | Published frontend digest reference |
| Port | 80 |
| `BACKEND_HOST` | `127.0.0.1` |
| `BACKEND_PORT` | `8080` |
| Startup dependency | backend is HEALTHY |
| Logging | Same log group, stream prefix `frontend` |

Frontend ECS health-check command:

```json
["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1/"]
```

Use interval 10 seconds, timeout 5 seconds, retries 5, start period 30 seconds. Make both containers essential. Check ECS Events and CloudWatch Logs to verify initial startup.

The existing `prod` profile enforces authentication and CSRF protection. The frontend login/CSRF flow and HTTPS still need integration; the deployment pipeline does not implement those features. Do not send Basic credentials over plain HTTP or assume a successful infrastructure/startup check means all authenticated UI flows work. Keep initial access restricted to your IP and use synthetic demonstration data during setup.

## One-time GitHub configuration

Keep the existing publishing secrets and variables:

| Kind | Names |
| --- | --- |
| Repository secrets | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` |
| Repository variables | `AWS_REGION`, `ECR_BACKEND_REPOSITORY`, `ECR_FRONTEND_REPOSITORY`, `AWS_PUBLISH_ENABLED=true` |

Add/update these **repository variables** under **Settings → Secrets and variables → Actions → Variables**:

| Variable | Value |
| --- | --- |
| `AWS_DEPLOY_ENABLED` | `true` after the existing service is configured |
| `ECS_CLUSTER` | Your actual existing cluster name, e.g. `flying-angels-lab` |
| `ECS_SERVICE` | Your actual existing service name, e.g. `flying-angels-app` |

A task-definition-family variable is unnecessary: the workflow reads the exact revision attached to the configured service. Database endpoints, usernames, secrets, roles, logging, health checks and networking stay configured in AWS. They do not need to be copied into GitHub.

The earlier proposed infrastructure variables (`AWS_STACK_NAME`, `RDS_INSTANCE_IDENTIFIER`, `DATABASE_SECRET_ARN`, `DEMO_ALLOWED_CIDR`, etc.) are not read by this workflow. Do not add them for deployment. No CloudFormation stack or custom Python AWS deployment code is required.

Learner Lab credentials expire. Refresh all three GitHub secrets from the active lab's **AWS Details** before the release. No IAM user or long-lived access key needs to be created.

## Normal release process

1. Merge feature work into `development`. CI verifies and publishes it without deploying.
2. At the sprint release milestone, open a PR from `development` into `main`.
3. Merge it after reviews and required checks pass. PR merging remains a team action; CD does not enable GitHub auto-merge.
4. The resulting `main` push builds, tests and publishes both images.
5. After CI succeeds, the separate **CD - Deploy to ECS** workflow downloads `published-images.json` from that exact CI run and attempt. It verifies that the metadata commit matches the successful CI commit and both images use digest references. It then authenticates, reads the existing service task definition, replaces only the `backend` and `frontend` images, registers a revision and updates the service.
6. The job waits for service stability and checks that the expected revision is still the primary completed deployment. A rollback to a previous revision is reported as a failed release rather than a successful new deployment.
7. Open **Actions → CD - Deploy to ECS** and inspect that run's **ECS deployment completed** summary for the cluster, service, revision and desired task count. If it fails, download `deployment-diagnostics-*` and inspect ECS Events/CloudWatch Logs.

The pipeline preserves the service desired count. If it is one, the application is replaced using rolling deployment. If it is zero, the new version is configured but no tasks are started. The database is neither started nor stopped by CD. A running application requires RDS to be available.

No manual image copying, task-definition editing or console redeployment is required after the initial setup. The CD `workflow_run` file must exist on the repository's default branch, normally `main`. Merging both workflow files into `main` satisfies that requirement when `main` is the default. No manual run button is needed. The CD run consumes only JSON metadata from the trusted successful main CI run; it does not execute downloaded artifact code.

## Presentation start/stop and cost control

These are operational controls, separate from releasing code:

- Before presenting: start Learner Lab, start existing RDS and wait for Available, then set ECS desired tasks to one.
- After presenting: set ECS desired tasks to zero, wait for running/pending tasks to reach zero, then stop RDS.
- Keep the existing resources and data. RDS storage, images, secrets and retained logs still incur charges while compute is stopped.
- RDS automatically restarts after seven days stopped; monitor it and the lab budget. Ending a lab session is not a complete shutdown mechanism.
- The task public IP can change on restart or deployment. Find it under ECS → running task → Networking.

A new main release does not override a deliberately stopped service or start the database. For an actual live release verification, have RDS available and desired task count one.

## Failure handling

| Failure | Action |
| --- | --- |
| Deployment skipped or absent | Check that `ce.yaml` exists on the default branch, CI completed successfully for a same-repository push to `main`, and `AWS_DEPLOY_ENABLED=true`. PR/development/manual runs do not deploy. |
| Missing variable | Configure the name reported by the deployment settings step. |
| Expired credentials | Refresh all three AWS secrets, then rerun the failed CD workflow. It reuses the exact CI release artifact while it remains available. |
| Release artifact expired/missing | The release metadata is retained for seven days. Rerun the original main CI run to publish and upload a new attempt, which triggers CD after success. |
| Service missing or inactive | Finish the one-time ECS setup and verify cluster/service names and region. CD intentionally does not create a replacement service. |
| Container name mismatch | The existing task must contain exactly one `backend` and one `frontend` container. |
| Unsupported deployment controller | Use the ECS rolling controller. This workflow does not configure CodeDeploy blue/green or external deployment controllers. |
| Startup/health failure | Inspect ECS Events and CloudWatch Logs; verify database availability, secret values, LabRole permissions and networking. |
| Rollback detected | Fix the failing release and merge a correction, or explicitly restore a known-good task revision in ECS. |

Enable the ECS circuit breaker with rollback during setup. Rolling deployments can briefly run two tasks. CD does not reset the database or change the existing Hibernate schema strategy; review database-affecting code changes as part of the release.

## References

- [AWS render task-definition action](https://github.com/aws-actions/amazon-ecs-render-task-definition)
- [AWS deploy task-definition action](https://github.com/aws-actions/amazon-ecs-deploy-task-definition)
- [Publishing and CI instructions](ecr-publishing.md)
