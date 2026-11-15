# Local database backup and restore

This rehearsal uses the existing local fa-postman stack as its source. Its separate destination is fa-restore-rehearsal. AWS and the original database receive no test writes.

## What was verified

- The restored schema and every table's data matched the backup byte-for-byte before application startup.
- Flyway retained the successful V1 baseline and Hibernate validation passed.
- The existing Admin password worked after signing in again.
- The API returned 9 athletes, 9 performances, 2 notes and 37 accounts.
- A synthetic pending invitation created only in the copy survived a second backup/restore. With the same encryption key, it was captured once in the separate Mailpit inbox and its activation token validated.
- Delivery was paused again after the test. The source had 28 SENT emails and no pending emails; no pending-message test account was created there.

## Inspect the copy in Postman

The restored backend is running at http://127.0.0.1:18081 and its separate Mailpit inbox is http://127.0.0.1:18026. The original backend remains at http://localhost:18080.

Create a separate Postman environment named Flying Angels – Restore rehearsal. Set baseUrl to http://127.0.0.1:18081. Keep the original environment unchanged. Cookies are scoped by host/path, not port: using 127.0.0.1 for the copy avoids replacing the original localhost session cookie.

1. GET {{baseUrl}}/api/csrf. Retain the cookie and the returned headerName/token.
2. POST {{baseUrl}}/api/auth/login with the existing local Admin email/password as JSON and the CSRF header. Expect HTTP 200 and role ADMIN. A restored password hash preserves the password; changing bootstrap configuration does not reset it.
3. GET {{baseUrl}}/api/csrf again after login to obtain fresh CSRF state.
4. GET {{baseUrl}}/api/auth/me. Expect the restored Admin account.
5. GET {{baseUrl}}/api/athletes?archive=all. Select a returned ID rather than guessing one.
6. GET {{baseUrl}}/api/athletes/{id}. Check profile information and performances.
7. GET {{baseUrl}}/api/athletes/{id}/history. Check historical results.
8. GET {{baseUrl}}/api/athletes/{id}/notes. Select an athlete known to have notes and check their content/timestamps.
9. GET {{baseUrl}}/api/users. Check retained accounts and roles.

Sessions are held by the backend and are not part of this database backup. Sign in again after restarting the backend.

## Reproduce the exercise

Run from the repository root. The local images must already exist. The original private .env.postman.local file must still contain POSTMAN_MAIL_OUTBOX_KEY. Do not generate a replacement key: pending invitation payloads require the original key for decryption.

If a rehearsal is already running, inspect it or remove only that project using Cleanup before starting a new one. Never stop/recreate the original fa-postman MySQL container; its tmpfs data is disposable.

### 1. Make a private SQL backup

~~~sh
umask 077
RESTORE_WORKDIR="$(mktemp -d "$TMPDIR/fa-restore.XXXXXX")"

docker exec fa-postman-mysql-1 sh -c '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u "$MYSQL_USER" \
    --single-transaction --quick --no-tablespaces --skip-comments \
    --skip-add-locks --skip-lock-tables --hex-blob --order-by-primary \
    --set-gtid-purged=OFF "$MYSQL_DATABASE"
' > "$RESTORE_WORKDIR/original.sql"
~~~

On this Mac, TMPDIR is set by the system. Use /tmp in the directory template if it is unset on another machine. Keep this terminal open so RESTORE_WORKDIR remains available.

The backup includes tables, relationships, indexes, records, account hashes, Flyway history and the outbox. It does not include the externally configured encryption key or MySQL server accounts. The restore instance creates its own database user through Docker configuration.

The [MySQL backup documentation](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html) describes these logical backups. Single-transaction provides a consistent snapshot for the application's InnoDB tables; avoid schema changes while dumping. This temporary local SQL file is a rehearsal artifact, not durable off-machine recovery storage.

### 2. Create an isolated MySQL instance

Save the Compose configuration at the end of this guide to $RESTORE_WORKDIR/compose.yml. Then define a terminal helper for this project only:

~~~sh
restore_stack() {
  docker compose --env-file .env.postman.local \
    -p fa-restore-rehearsal -f "$RESTORE_WORKDIR/compose.yml" "$@"
}
restore_stack up -d --no-build --wait --wait-timeout 240 mysql
~~~

The project name separates the containers and networks from fa-postman. MySQL has no published port and keeps the copy in temporary storage.

### 3. Restore into that separate instance

~~~sh
docker exec -i fa-restore-rehearsal-mysql-1 sh -c '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"
' < "$RESTORE_WORKDIR/original.sql"
~~~

The destination is explicitly fa-restore-rehearsal-mysql-1. The SQL contains table recreation statements; use only the disposable destination.

To verify the complete restore before application startup, repeat the dump command using fa-restore-rehearsal-mysql-1 as its source and save to restored.sql. Run:

~~~sh
cmp "$RESTORE_WORKDIR/original.sql" "$RESTORE_WORKDIR/restored.sql"
~~~

No output and exit code 0 mean the exports are identical. This is stronger than checking row counts alone.

### 4. Start the backend and inspect history

~~~sh
restore_stack up -d --no-build --wait --wait-timeout 240 backend mailpit

docker exec fa-restore-rehearsal-mysql-1 sh -c '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" \
    -e "SELECT installed_rank, version, type, success FROM flyway_schema_history;"
'
~~~

The history must match the source. In this database it is a successful V1 BASELINE row. A database originally created by V1 SQL instead retains a V1 SQL row. Do not baseline again: the history was restored too. Flyway should report the schema is up to date, and Hibernate validates entity compatibility.

Use the Postman steps above to verify actual application access. Email delivery starts disabled, so restored pending messages remain available for inspection.

### 5. Test pending invitation recovery

1. With delivery paused, POST /api/users/invitations as Admin with fresh CSRF state. Use a unique synthetic example.test email address, a name and role COACH. Save the returned user ID.
2. GET /api/users/{id}/invitation/delivery. Expect PENDING with attempts 0.
3. Stop only this copy's backend with restore_stack stop backend.
4. Dump this copy using the backup command, substituting fa-restore-rehearsal-mysql-1 as source and with-pending.sql as the output filename.
5. Import with-pending.sql into fa-restore-rehearsal-mysql-1 using the restore command. This recreates only the disposable copy's backed-up tables.
6. Start its backend again, sign in again, and inspect the same delivery endpoint. It should still be PENDING with attempts 0.
7. Enable delivery to the separate local Mailpit inbox:

~~~sh
RESTORE_SEND_EMAILS=true restore_stack up -d --no-build --no-deps \
  --wait --wait-timeout 240 backend
~~~

8. Open http://127.0.0.1:18026. After the worker polls, the message should appear and delivery status should become SENT. The same key is required to recover the encrypted payload.
9. Copy the captured token and POST /api/auth/invitations/validate with JSON containing the token field and fresh CSRF state. Expect a successful invitation response. Do not put real activation tokens in shared logs.
10. Pause automatic delivery again:

~~~sh
RESTORE_SEND_EMAILS=false restore_stack up -d --no-build --no-deps \
  --wait --wait-timeout 240 backend
~~~

The worker clears encrypted payloads after successful delivery. Compare backup contents before allowing delivery. One captured message was observed in this rehearsal; the application's at-least-once delivery can still duplicate a message after a crash between SMTP acceptance and database acknowledgment.

## Cleanup

When finished inspecting this copy:

~~~sh
restore_stack down --volumes
~~~

This removes only the restore-rehearsal project. Host backup files remain; retain or securely dispose of them separately. Keep real recovery backups and the retained encryption key outside application containers.

## Disposable Compose configuration

These are synthetic, local-only credentials. Backend and Mailpit are published only on loopback; SMTP is configured exclusively for the separate Mailpit service. The database uses an internal network. There are no frontend changes.

~~~yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: flying_angels_ci
      MYSQL_ROOT_PASSWORD: restore-root-password
      MYSQL_USER: restore_user
      MYSQL_PASSWORD: restore-app-password
    tmpfs:
      - /var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", 'mysqladmin ping -h 127.0.0.1 -u root -p"$$MYSQL_ROOT_PASSWORD" --silent']
      interval: 3s
      timeout: 3s
      retries: 60
  backend:
    image: flying-angels-backend:postman
    networks: [default, access]
    depends_on:
      mysql:
        condition: service_healthy
    ports:
      - "127.0.0.1:18081:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:mysql://mysql:3306/flying_angels_ci?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
      DATABASE_USERNAME: restore_user
      DATABASE_PASSWORD: restore-app-password
      APP_REQUIRE_AUTHENTICATION: "true"
      APP_ADMIN_EMAIL: postman-admin@example.test
      APP_ADMIN_PASSWORD: Postman-only-password-123!
      SESSION_COOKIE_SECURE: "false"
      APP_ACTIVATION_URL: http://localhost:18081/activate
      APP_MAIL_OUTBOX_KEY: ${POSTMAN_MAIL_OUTBOX_KEY:?Retain the original outbox key}
      APP_MAIL_OUTBOX_ENABLED: ${RESTORE_SEND_EMAILS:-false}
      APP_LOGIN_THROTTLE_CLEANUP_ENABLED: "false"
      SMTP_HOST: mailpit
      SMTP_PORT: "1025"
      SMTP_FROM: noreply@flyingangels.local
      SMTP_AUTH: "false"
      SMTP_STARTTLS: "false"
  mailpit:
    image: axllent/mailpit:v1.31.5
    networks: [default, access]
    ports:
      - "127.0.0.1:18026:8025"
networks:
  default:
    internal: true
  access: {}
~~~
