# Local Docker environment and networks

Local database configuration is read from a private `.env` in the repository root. Dockerfiles contain no passwords. Compose substitutes the environment values into MySQL and the backend at startup.

## Local startup

On a new checkout:

```sh
cp .env.example .env
```

Edit `.env` and fill the database passwords and initial Admin credentials. Its keys are:

```dotenv
MYSQL_DATABASE=flying_angels
MYSQL_USER=appuser
MYSQL_PASSWORD=
MYSQL_ROOT_PASSWORD=
APP_ADMIN_EMAIL=
APP_ADMIN_PASSWORD=
# Generate once with openssl rand -base64 32; keep private and retain across backend restarts.
APP_MAIL_OUTBOX_KEY=
```

Use an email address for APP_ADMIN_EMAIL and at least 12 characters for APP_ADMIN_PASSWORD. These initialize a persisted Admin only when the staff table is empty; subsequent account/password changes happen through the Admin-only staff APIs. Then start the stack:

```sh
docker compose up --build
```

Use Postman against `http://localhost:8080`: retrieve `/api/csrf`, sign in through POST `/api/auth/login`, retain the session cookie, refresh the CSRF token, then create Coach accounts through POST `/api/users`. A Coach can view athlete information and manage events/assignments/results, while basic athlete/meet edits and account management remains Admin-only. Coach imports can add performances to confirmed existing athletes; rows creating athletes or changing basic profile details require an Admin. The existing frontend is unchanged and has no login/CSRF integration yet; use the [security API collection](postman/README.md#7-secured-mode-testing) to exercise this backend-only functionality.

For backend-only work, use `docker compose up --build mysql backend`. MySQL and backend use the same `MYSQL_USER` / `MYSQL_PASSWORD`. The health check reads the root password from the running container environment. Required values have no password fallback: Compose reports an error if either password or the username is missing/empty.

The private `.env` is Git-ignored and excluded from repository-root image build contexts. Commit only `.env.example`, with blank password fields. On the current checkout, `.env` was prepared privately using the generated values from the reverted secrets setup; no setup script is needed.

This is environment-variable configuration. It does not provide Docker Secrets: the local file is plaintext and the values are present in configured container environments. CI's disposable test fixture and AWS Secrets Manager configuration are separate.

## Database upgrades

New databases initialize through one Flyway V1 containing all 16 application tables. Existing unversioned databases require a backup, schema review and explicit one-time baseline; see [Database migrations](database-migrations.md). Root Compose forwards `SPRING_FLYWAY_BASELINE_ON_MIGRATE` (false), `SPRING_FLYWAY_BASELINE_VERSION` (1) and `SPRING_FLYWAY_TARGET` (`latest`). Keep baseline-on-migrate false after initial setup. Lowering the target does not reverse schema changes. Hibernate remains in validate mode.

## Current networking

The root Compose file declares two project-scoped bridge networks:

| Service | `application` | `database` |
| --- | --- | --- |
| Frontend | Yes | No |
| Backend | Yes | Yes |
| MySQL | No | Yes |

Backend connects to the database by service name, `mysql:3306`. Frontend and MySQL have no shared network. Compose creates the networks automatically with names such as `flying-angels_application` and `flying-angels_database`.

The applied layout is:

```yaml
services:
  frontend:
    networks:
      - application
  backend:
    networks:
      - application
      - database
  mysql:
    networks:
      - database

networks:
  application:
    driver: bridge
  database:
    driver: bridge
```

The frontend cannot resolve MySQL by its Compose service name through the application network. Published host ports remain a separate access path. The backend host port binds to loopback (`127.0.0.1:8080`) for local Postman access; set `BACKEND_HTTP_PORT` to change that host port. MySQL remains published on port 3306 for host SQL tools.

For a fully container-based stack with no host SQL client, remove MySQL's `ports` entry and add `internal: true` to the database network. An internal-only database network does not provide host port forwarding. If retaining host SQL access, keep the database bridge non-internal and bind its port only to `127.0.0.1:3306:3306`. Application host ports can similarly bind to `127.0.0.1` for local-only access.

Frontend source and Vite configuration are unchanged. The existing development proxy targets `http://backend:8080` for the Compose network. The production Nginx API proxy reads `BACKEND_HOST` and `BACKEND_PORT` at container startup. The deployment frontend Dockerfile retains its multi-stage build; frontend development configuration remains outside this backend work.

`APP_CORS_ALLOWED_ORIGINS` is a comma-separated list of exact origins for clients that call the API across origins. Leave it empty for same-origin requests through either proxy. CORS does not replace session authorization or CSRF tokens; Postman is not subject to browser CORS enforcement.

To activate the network configuration, recreate the containers and inspect the result:

```sh
docker compose config --quiet
docker compose up -d --build --force-recreate
docker network ls --filter label=com.docker.compose.project
docker compose ps
```

MySQL stores its data in the named `mysql_data` Docker volume. Stopping/recreating containers keeps that volume and its database records. Initial database credentials and the first Admin account are not reset by changing `.env` after those records already exist.

For the updated account-invitation workflow, use `docker compose --profile mail up --build mysql backend mailpit`. The optional local Mailpit inbox is at `http://localhost:8025`; its SMTP listener stays inside the application network. SMTP_HOST defaults to mailpit and SMTP_PORT to 1025 in Compose. Invitations queue atomically in MySQL and are sent asynchronously; configure a private `APP_MAIL_OUTBOX_KEY` first. SMTP outages leave the account/invitation committed and schedule retries. The inbox captures invitations for local testing; external email delivery requires real provider SMTP settings. APP_ACTIVATION_URL identifies the future client activation route, while Postman can copy the captured token and invoke the backend activation APIs directly. No activation frontend is added.

APP_AVAILABILITY_TIME_ZONE defaults to America/Toronto. Optional meet startTime controls the availability cutoff; date-only meets close at midnight on startDate. APP_AGE_GROUP_BANDS is deliberately empty until club categories are supplied. Once configured, provide ageAsOf on ranking/CSV requests so group attribution uses an explicit date.

References: [Compose environment interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/), [Compose networks](https://docs.docker.com/reference/compose-file/networks/).

Login throttling is enabled for secured local runs: five failures per email and 100 per socket IP in 15 minutes, with a 15-minute block and Retry-After header. Success clears the account failure count. The root Compose file forwards the APP_LOGIN_THROTTLE_* policy values and APP_LOGIN_TRUSTED_PROXIES. The trusted-proxy allowlist is empty by default; forwarding headers from a direct client are ignored. See [backend throttling](../backend/README.md#login-throttling).
