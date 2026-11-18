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
- `.github/workflows/cd.yaml` — **CD - Deploy to ECS**: deploy the exact successful main CI release to the existing service.

Keep the required PR status check **Build, test and verify containers**. CD uses a `workflow_run` completion trigger and accepts only successful CI runs from a push to `main` in this same repository. It does not run for failed checks, PRs, development pushes or manual publishing.

## One-time AWS setup

Reuse the existing RDS database, ECR repositories, ECS cluster/service, log group and Secrets Manager secrets in **us-east-1**. Keep `AWS_DEPLOY_ENABLED=false` and the service desired count at zero while preparing the database and public entry point. This guide uses **https://app.flyingangels.live** for the browser application and same-origin API.

1. **Certificate:** in ACM, request a public, non-exportable certificate for `app.flyingangels.live` in us-east-1. Choose DNS validation. Add ACM's generated CNAME at Name.com and wait for Issued. Keep the existing SendGrid CNAME/TXT records. There is no need to move DNS to Route 53 or buy another domain/certificate.
2. **Public load balancer:** create an internet-facing Application Load Balancer in the same VPC as RDS. Select two public subnets in different Availability Zones with internet-gateway routes. Record their IPv4 CIDRs from VPC/EC2 Subnets; these become `NGINX_TRUSTED_PROXY_CIDRS` below. Choose an IPv4 ALB for this initial setup.
3. **Security groups:** use the separate groups and rules in the table below. Remove direct public/My-IP HTTP access from the ECS application group after attaching the ALB. Keep port 8080 private and RDS inaccessible from the internet.
4. **Target group:** create an HTTP target group with target type **IP**, port **80**, the application VPC, and health check path **/login**, success code **200**. ECS registers task IPs; do not manually register the task's public IP. Use this target group for the `frontend` container's port 80.
5. **Listeners and DNS:** attach the ACM certificate to an HTTPS/443 ALB listener forwarding to the target group. Set HTTP/80 to redirect to HTTPS/443. At Name.com, add CNAME host `app`, answer the ALB DNS name (no `https://` or path), with the default TTL. Existing SendGrid records remain unchanged.
6. **ECS task definition:** create a revision of the existing Linux/X86_64 Fargate task with the two containers below. Retain the existing 0.5 vCPU / 2 GiB allocation, logging, and **LabRole** for both task and execution roles. Verify the execution role can retrieve all four existing secrets. Keep the persistent outbox encryption key unchanged.
7. **ECS service:** attach the task revision and ALB target group to the existing rolling-update service, enable the deployment circuit breaker/rollback, and set health-check grace period to 120 seconds. Keep desired count zero until the database is ready. With no NAT Gateway, retain a public subnet and assigned public IP for the task's outbound ECR/Secrets Manager/SendGrid access; the task security group still permits inbound HTTP only from the ALB.
8. **Database and release:** configure the one-time database baseline below, release the revised images through main, then set desired count to one and verify the public browser flows. Start with one task because login sessions are currently held in that backend's memory. Restarts require sign-in again; multiple simultaneous serving tasks need session-affinity/shared-session planning.

| Security group | Inbound rule | Purpose |
| --- | --- | --- |
| ALB | TCP 443 and 80 from `0.0.0.0/0` | Public HTTPS and HTTP-to-HTTPS redirect |
| ECS application | TCP 80 from **the ALB security group only** | ALB to Nginx; no direct public application access |
| RDS | TCP 3306 from **the ECS application security group only** | Backend to existing MySQL |

Allow the ALB outbound TCP/80 to the ECS application group. Retain task outbound connectivity for MySQL/3306, SMTP/587, DNS and AWS APIs/HTTPS. Neither port 8080 nor 3306 needs a public inbound rule.

### Attach the load balancer to the existing service

AWS does not support changing an existing service's load-balancer configuration through the ECS console. For the existing ECS rolling deployment controller, use CloudShell/AWS CLI. Define these non-secret variables using the actual existing resource names and the newly created ARNs:

```sh
export AWS_REGION=us-east-1
export ECS_CLUSTER=YOUR_EXISTING_CLUSTER
export ECS_SERVICE=YOUR_EXISTING_SERVICE
export TASK_DEFINITION_ARN=YOUR_NEW_TASK_REVISION_ARN
export TARGET_GROUP_ARN=YOUR_NEW_TARGET_GROUP_ARN

aws ecs update-service --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" --service "$ECS_SERVICE" \
  --task-definition "$TASK_DEFINITION_ARN" \
  --load-balancers "targetGroupArn=$TARGET_GROUP_ARN,containerName=frontend,containerPort=80" \
  --health-check-grace-period-seconds 120 --desired-count 0 \
  --query 'service.{TaskDefinition:taskDefinition,DesiredCount:desiredCount,LoadBalancers:loadBalancers}'
```

This replaces the service's load-balancer list with this target group. Use it for the previously discussed service without an existing load balancer. If the service has other target groups, review its existing configuration first. The command preserves a stopped service while attaching the configuration that CD will copy. Merely registering a task revision does not make it the service's active configuration.

Backend container:

| Setting | Value |
| --- | --- |
| Container name | `backend` (exactly) |
| Image | Published backend digest reference |
| Port | 8080 |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:mysql://YOUR_RDS_ENDPOINT:3306/flying_angels?sslMode=REQUIRED&serverTimezone=UTC` |
| `DATABASE_USERNAME` | Your configured database user |
| `APP_ADMIN_EMAIL` | Staff email used to initialize the first Admin account |
| `APP_LOGIN_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128` for this same-task Nginx-to-backend topology only; backend port 8080 must remain private |
| `APP_LOGIN_THROTTLE_ACCOUNT_LIMIT`, `APP_LOGIN_THROTTLE_IP_LIMIT` | Failed-login limits (defaults 5 per account / 100 per source IP) |
| `APP_LOGIN_THROTTLE_WINDOW_SECONDS`, `APP_LOGIN_THROTTLE_BLOCK_SECONDS` | Observation window and block length (defaults 900 / 900 seconds) |
| `APP_CORS_ALLOWED_ORIGINS` | Empty for the same-origin frontend and `/api/`; do not use `*` |
| `SESSION_COOKIE_SECURE` | `true`; browser access uses HTTPS through the ALB |
| `APP_ACTIVATION_URL` | `https://app.flyingangels.live/activate` |
| `APP_MAIL_OUTBOX_KEY` | Secret reference / ValueFrom: a persistent Base64-encoded 32-byte encryption key; required by the prod profile |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` | `smtp.sendgrid.net`, `587`, `noreply@flyingangels.live` |
| `SMTP_USERNAME` | Plain value `apikey` (the literal word) |
| `SMTP_PASSWORD` | ValueFrom: full ARN of the existing plaintext SendGrid API-key secret |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Both `true` |
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
| `NGINX_TRUSTED_PROXY_CIDRS` | Comma-separated actual IPv4 CIDRs of the two **ALB subnets**, e.g. `ALB_SUBNET_1_CIDR,ALB_SUBNET_2_CIDR`; replace both placeholders. Never use `0.0.0.0/0`, and do not copy an example VPC range. |
| Startup dependency | backend is HEALTHY |
| Logging | Same log group, stream prefix `frontend` |

Frontend ECS health-check command:

```json
["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1/"]
```

Use interval 10 seconds, timeout 5 seconds, retries 5, start period 30 seconds. Make both containers essential. Check ECS Events and CloudWatch Logs to verify initial startup.

The `prod` profile enforces session authentication and CSRF protection. The frontend now includes `/login`, `/activate`, `/forgot-password`, role-protected navigation, and the cookie/CSRF API client. The initial Admin is created only when `staff_users` is empty. Set `APP_ADMIN_EMAIL` to an actual email, replacing the earlier `APP_ADMIN_USERNAME=admin`; the password must have at least 12 characters (at most 72 UTF-8 bytes). It is BCrypt-hashed in MySQL. Later environment changes do not reset existing account credentials.

Production cookies are Secure by default. Keep them Secure for this public deployment; TLS terminates at the ALB, and its private HTTP hop to Nginx does not require disabling cookie security. The containers do not provision an ALB/certificate, start RDS, or scale ECS up.

Login throttling persists counters in MySQL across task replacements. Excessive failures return 429 with Retry-After; successful login clears only the account failure count. Configure backend `APP_LOGIN_TRUSTED_PROXIES` for same-task Nginx and frontend `NGINX_TRUSTED_PROXY_CIDRS` for the ALB subnets. Otherwise requests may share a proxy address and trigger each other's IP limits. See [backend throttling policy and tradeoffs](../backend/README.md#login-throttling).

New UI invitations use `/api/users/pending-invitations`: the invitation and encrypted email job commit together, and account creation follows email-code verification and password setup on `/activate`. The pending-invitations API lists invitation state; use the actual inbox and SendGrid Activity to confirm new enrollment-email delivery. The existing `/api/users/{id}/invitation/delivery` endpoint is scoped to legacy user-linked invitation jobs, not the new pending invitation IDs. Password recovery uses an emailed code and `/forgot-password`. All use the existing SendGrid SMTP settings and retained `APP_MAIL_OUTBOX_KEY`. Missing/invalid encryption keys prevent production startup. SENT means SMTP acceptance, not confirmed inbox delivery. No provider is provisioned automatically.

### Existing RDS database: complete before the first release

The first Flyway release now matches the existing RDS schema: V1 is the ten tables supplied on 2026-11-18, V2 adds the missing Sprint 3 schema, V3 adds verified account enrollment and V4 adds password recovery. The owner reports no existing application data and has a snapshot. No manual conversion SQL is needed.

Keep the service stopped until the updated images are ready. For its first updated backend startup, add `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and `SPRING_FLYWAY_BASELINE_VERSION=1`; retain target `latest` and Hibernate `validate`. Flyway skips the existing V1 tables, records the baseline, and executes V2 through V4. Verify BASELINE 1 plus successful SQL 2, 3 and 4, then remove the temporary baseline override or set it to false. See [database migration details](database-migrations.md#first-startup-against-the-reviewed-rds-database).

Do not apply these instructions to local databases with the earlier unreleased Flyway checksums/history; those need a separate transition. No RDS changes or local data resets have been executed by this workspace.

### Public verification

After RDS is ready and the new service has one healthy task, confirm:

1. `https://app.flyingangels.live/login` loads from another network, and HTTP redirects to HTTPS.
2. Admin login works with a Secure/HttpOnly session cookie, and normal authorized writes pass CSRF validation.
3. Existing athletes, meets, results and notes are retained.
4. Invite one controlled real recipient; the email link opens `/activate`, the code verifies, password setup succeeds, and the new account can sign in with its role restrictions.
5. Password recovery delivers its code and replaces the password; the previous session becomes invalid.
6. Unknown/unauthorized API calls return JSON errors, and ports 8080/3306 are inaccessible publicly.

The automated test suites use synthetic records and email addresses. Do not point full Postman collections at a real SendGrid-backed deployment.

## Container builds and API reverse proxy

The deployment Dockerfiles build from source in separate builder stages. Only the compiled JAR enters the non-root Java 17 JRE backend runtime; only built static assets enter the Nginx runtime. Maven/JDK tooling and Node/npm dependencies stay in the builder stages. `.dockerignore` excludes local artifacts and private environment files. CI tests and publishes the same resulting images.

Nginx already provides the public reverse-proxy layer. `/api/` requests retain their paths and are forwarded to the backend configured by `BACKEND_HOST` and `BACKEND_PORT`; other paths serve the existing frontend. The CI stack exposes only Nginx, and the AWS application security group should expose port 80 (or the configured TLS entry point), not backend port 8080 or MySQL. API routing, deployment destinations, CORS policy and invitation URLs are configuration concerns, with no controller-specific CORS overrides.

`deploy/19-trusted-proxies.sh` generates Nginx's real-IP allowlist from `NGINX_TRUSTED_PROXY_CIDRS` at container startup. Its default is empty: direct-client forwarding headers remain untrusted. With ALB subnet CIDRs configured, Nginx uses the final client address appended by the single ALB to X-Forwarded-For, then supplies a single resolved X-Real-IP/X-Forwarded-For value to Spring. Recursive traversal is disabled so even a client inside an ALB subnet cannot make Nginx select an earlier forged address. It accepts only `http`/`https` protocol values from a trusted original socket peer, reconstructs the forwarded host from Host, and clears standardized Forwarded metadata. Invalid CIDRs fail startup, and all-address trust is rejected.

The task security group accepting port 80 only from the ALB is part of this trust boundary; do not use the allowlist as a substitute for that rule. Keep the ALB X-Forwarded-For processing mode at **append**. Spring continues to trust only same-task Nginx for client-address resolution. Its generic forwarded-header strategy remains disabled. Cookies, CSRF headers, request bodies and backend responses pass normally. `tests/integration/test_nginx_proxy.py` exercises the built image with trusted and untrusted peers; CI runs it before the full stack checks.

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
3. After the database and public setup above are ready, set `AWS_DEPLOY_ENABLED=true` and merge after reviews and required checks pass. PR merging remains a team action; CD does not enable GitHub auto-merge.
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
- Public users use the application domain and ALB DNS routing rather than the task public IP. An ALB continues charging while it exists even with zero ECS tasks. Delete the demo ALB when no longer needed (and recreate listeners/DNS as necessary for a later demo); do not delete RDS or email-authentication DNS records as part of compute shutdown.

A new main release does not override a deliberately stopped service or start the database. For an actual live release verification, have RDS available and desired task count one.

## Failure handling

| Failure | Action |
| --- | --- |
| Deployment skipped or absent | Check that `.github/workflows/cd.yaml` exists on the default branch, CI completed successfully for a same-repository push to `main`, and `AWS_DEPLOY_ENABLED=true`. PR/development/manual runs do not deploy. |
| Missing variable | Configure the name reported by the deployment settings step. |
| Expired credentials | Refresh all three AWS secrets, then rerun the failed CD workflow. It reuses the exact CI release artifact while it remains available. |
| Release artifact expired/missing | The release metadata is retained for seven days. Rerun the original main CI run to publish and upload a new attempt, which triggers CD after success. |
| Service missing or inactive | Finish the one-time ECS setup and verify cluster/service names and region. CD intentionally does not create a replacement service. |
| Container name mismatch | The existing task must contain exactly one `backend` and one `frontend` container. |
| Unsupported deployment controller | Use the ECS rolling controller. This workflow does not configure CodeDeploy blue/green or external deployment controllers. |
| Startup/health failure | Inspect ECS Events and CloudWatch Logs; verify database availability, secret values, LabRole permissions and networking. |
| ALB returns 503 | Verify target group type IP, frontend port 80, task/subnet Availability Zones, health check /login, and security-group rules. |
| Users share an IP limit | Check the ALB subnet CIDRs in frontend NGINX_TRUSTED_PROXY_CIDRS and backend loopback APP_LOGIN_TRUSTED_PROXIES. |
| Email link opens the wrong host | Check APP_ACTIVATION_URL in the task revision attached to the service; newly issued links use the updated URL. |
| Rollback detected | Fix the failing release and merge a correction, or explicitly restore a known-good task revision in ECS. |

Enable the ECS circuit breaker with rollback during setup. Rolling deployments can briefly run two tasks. The backend now runs Flyway migrations and Hibernate validation. For the reviewed ten-table RDS schema, retain the snapshot and configure the one-time V1 baseline before starting the updated image. A schema without Flyway history blocks startup by default, even when its tables contain no rows. See [Database migrations](database-migrations.md). Later SQL changes must remain compatible with older tasks during rolling deployment.

## References

- [AWS render task-definition action](https://github.com/aws-actions/amazon-ecs-render-task-definition)
- [AWS deploy task-definition action](https://github.com/aws-actions/amazon-ecs-deploy-task-definition)
- [Publishing and CI instructions](ecr-publishing.md)
