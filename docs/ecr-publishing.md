# Build, test and publish images to ECR

The `CI and ECR publishing` workflow in `.github/workflows/ci.yml` verifies the application and automatically publishes the tested Docker images after successful pushes to `development` or `main`. It does not deploy ECS, start RDS, create AWS resources, or import athlete data.

## What runs and when

| Trigger | Behavior |
| --- | --- |
| Pull request targeting `main` or `development` | Build and test; no AWS credentials or image publishing |
| Push to `main` or `development` (including PR merges) | Build and test, then automatically publish to ECR after all checks pass |
| Manual run with publishing unchecked | Build and test only |
| Manual run on `development` or `main` with publishing checked | Build and test, then publish to the existing ECR repositories |

The required GitHub status check is **Build, test and verify containers**. If branch protection requires the previous `Frontend checks` and `Backend checks`, replace those requirements with this check after its first run.

## One-time GitHub configuration

1. Open the repository on GitHub.
2. Select **Settings → Secrets and variables → Actions → Secrets**.
3. Open **Learner Lab → Start Lab → AWS Details → AWS CLI → Show**.
4. Add the following **repository secrets**, copying only each value after `=`:

   | GitHub secret name | Learner Lab credential |
   | --- | --- |
   | `AWS_ACCESS_KEY_ID` | `aws_access_key_id` |
   | `AWS_SECRET_ACCESS_KEY` | `aws_secret_access_key` |
   | `AWS_SESSION_TOKEN` | `aws_session_token` |

5. Select **Variables → New repository variable** and add:

   | Variable | Example value |
   | --- | --- |
   | `AWS_REGION` | `us-east-1` |
   | `ECR_BACKEND_REPOSITORY` | `flying-angels-backend` |
   | `ECR_FRONTEND_REPOSITORY` | `flying-angels-frontend` |
   | `AWS_PUBLISH_ENABLED` | `true` |

Repository values are names, not full ECR URLs. Both private repositories must already exist in the selected region and account. The workflow discovers the registry address after authentication. Learner Lab supports `us-east-1` and `us-west-2` according to its Readme.

ECS variables and `AWS_DEPLOY_ENABLED` are not read by this publishing workflow. Keep deployment disabled while setting up ECS. The database password belongs in AWS Secrets Manager for the future ECS task, not in these GitHub secrets.

The lab's temporary user credentials provide publishing access. The existing `LabRole` is intended for ECS task and execution roles; the lab documents its ECR access as read-only, so the workflow does not assume that role to push images.

## Publish automatically

1. Ensure the four repository variables and three AWS secrets above are configured. `AWS_PUBLISH_ENABLED` must be `true`; otherwise the publishing run fails with a configuration error.
2. Start the Learner Lab and update expired credentials **before merging**. They must remain valid when the publishing steps execute after tests.
3. Review the workflow and supporting runtime/test files in a pull request targeting `development`. The pull request runs checks without AWS authentication or publishing.
4. Merge the pull request into `development`. The resulting push automatically starts **CI and ECR publishing**, including publication after successful checks. No manual button or default-branch change is required.
5. Open **GitHub → Actions → CI and ECR publishing** and select the run whose event is **push**, branch is **development**, and commit matches the merge. The earlier pull-request run does not publish.
6. Wait for **Build, test and verify containers** to pass.
7. Open the run's **Summary**. Under **Published images**, copy the backend and frontend image references.
8. In AWS, open **ECR → Private repositories → flying-angels-backend → Images**. Confirm the tag from the summary appears. Repeat for the frontend repository.
9. At the end-of-sprint release milestone, merge into `main`. That push also builds, tests, and publishes images for the release commit.

If credentials expire or publishing fails, refresh all three secrets and use **Re-run all jobs** on the failed push run. It will test and publish automatically again. A failed publishing step marks that push run as failed, even if application tests passed; its logs identify whether the failure is a test or AWS configuration problem.

## Optional manual run

Manual runs remain available for verification or republishing. Select **Actions → CI and ECR publishing → Run workflow**, choose `development` or `main`, and check **Publish tested images to ECR** to publish. Leave the checkbox unchecked for checks only.

GitHub requires a `workflow_dispatch` workflow on the repository's default branch for manual runs. If this updated workflow exists only on `development` while the default branch is `main`, manual runs may not be available yet. Automatic publishing after a push to `development` works regardless; changing the default branch is unnecessary for the automatic path.

Tags have the form `sha-<commit>-<run-id>-<attempt>`, so a rerun does not overwrite a previous release. The summary also provides immutable references such as `ACCOUNT.dkr.ecr.REGION.amazonaws.com/flying-angels-backend@sha256:...`. Use those full digest references as the images for the `backend` and `frontend` containers when creating the ECS task definition.

The run's `verification-<run-id>-<attempt>` artifact contains `published-images.json`, backend test reports, HTTP test output, and Compose logs. The JSON is generated only after both pushes and digest lookups succeed. ECR pushes are separate operations: if the second push fails, the first image can still exist. A failed run must not be treated as a completed release; fix the error and rerun.

## Verification sequence

1. Install frontend dependencies using the committed lockfile; run lint and type checks.
2. Build the backend JAR and frontend production assets.
3. Run backend unit tests (`-Punit-tests`) and frontend unit tests.
4. Build Linux/amd64 runtime images using `deploy/backend.Dockerfile` and `deploy/frontend.Dockerfile`. Application dependencies are already compiled; these Dockerfiles package the outputs.
5. Start `deploy/compose.ci.yml` under a unique Compose project name. MySQL uses a temporary in-memory filesystem. Only the frontend is exposed on the runner's loopback port 18080.
6. Run database integration tests (`-Pintegration-tests`), which also use disposable Testcontainers MySQL databases.
7. Run `tests/integration/test_deployed_stack.py` against the built images. It checks React routes, athlete create/read/search/update/delete through Nginx, and rejection of invalid athlete data.
8. On pushes to `development` or `main`, or an explicitly requested manual publication on either branch, authenticate to AWS, verify both repositories, and push the same images that passed the HTTP tests.
9. Collect logs, remove the isolated Compose stack, and upload reports even when a step fails.

The test stack uses synthetic data and the default development security configuration, bound to loopback. `ProductionSecurityTest` separately checks authorization and CSRF behavior. Publishing does not configure production authentication, database access, or HTTPS. The existing `application-prod.properties` expects `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `APP_ADMIN_USERNAME`, and `APP_ADMIN_PASSWORD` when that profile is enabled; supply those through the ECS task and secret references during deployment setup.

## Run the same checks locally

Prerequisites: Java 17, Node 22, Python 3, Docker with Compose v2, and network access to dependency/image registries. From the repository root:

```bash
(cd frontend && npm ci && npm run lint -- --max-warnings=0 && npm run typecheck && npm run build && npm test)
(cd backend && bash mvnw -B -DskipTests clean package && bash mvnw -B -Punit-tests test)

# Use a fresh, isolated project; never point these tests at the club's database.
export COMPOSE_PROJECT_NAME="fa-ci-local-$(date +%s)"
export BACKEND_IMAGE=flying-angels-backend:ci-local
export FRONTEND_IMAGE=flying-angels-frontend:ci-local
export CI_HTTP_PORT=18080
export TEST_BASE_URL=http://127.0.0.1:18080
trap 'docker compose -f deploy/compose.ci.yml down --volumes --remove-orphans' EXIT

docker build -f deploy/backend.Dockerfile -t "$BACKEND_IMAGE" .
docker build -f deploy/frontend.Dockerfile -t "$FRONTEND_IMAGE" .
docker pull mysql:8.4
docker compose -f deploy/compose.ci.yml up -d --no-build --pull never --wait --wait-timeout 240
(cd backend && bash mvnw -B -Pintegration-tests -Dapi.version=1.44 test)
python3 tests/integration/test_deployed_stack.py

docker compose -f deploy/compose.ci.yml down --volumes --remove-orphans
trap - EXIT
```

Local builds use the machine's native architecture. GitHub's Ubuntu runner explicitly builds Linux/amd64 for the planned ECS X86_64 task. No local AWS credentials are needed for these checks.

## Troubleshooting

| Failure | Action |
| --- | --- |
| No **Run workflow** button | Manual runs require the workflow on the default branch. Automatic push publishing does not need this button. |
| Publishing rejected on another branch | Select `development` or `main`. To test another branch, leave publishing unchecked. |
| Missing publishing setting | Add the exact secret or variable named in the error. Never print its value to logs. |
| Expired/invalid security token | Start the lab and replace all three GitHub AWS secrets with current values; rerun. |
| Repository not found | Check repository names, account and region. The workflow does not create repositories. |
| ECR access denied | Use lab user credentials with write access. `LabRole` has read-only ECR access in this lab. |
| Unit/integration/HTTP tests fail | Read the failed step and download the verification artifact. Publishing is skipped. |
| Docker image pull/TLS timeout | Check Docker Hub/network status and retry; no code change is normally required. |
| Compose startup timeout | Inspect `compose.log`, especially backend database connection errors. |

Before each new publication, verify the lab session and credentials. This workflow starts no AWS compute; retained ECR image storage can still use lab budget. Add an ECR lifecycle policy to retain only the image versions you need.

## References

- [AWS credentials action](https://github.com/aws-actions/configure-aws-credentials)
- [ECR login action](https://github.com/aws-actions/amazon-ecr-login)
- [GitHub manual workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
